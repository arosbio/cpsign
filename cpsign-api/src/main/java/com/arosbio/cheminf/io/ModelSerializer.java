/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cheminf.io;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cheminf.ChemCPClassifier;
import com.arosbio.cheminf.ChemCPRegressor;
import com.arosbio.cheminf.ChemClassifier;
import com.arosbio.cheminf.ChemPredictor;
import com.arosbio.cheminf.ChemVAPClassifier;
import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.descriptors.ChemDescriptor;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.GlobalConfig.Defaults.PredictorType;
import com.arosbio.commons.TypeUtils;
import com.arosbio.data.NamedLabels;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.DataSink;
import com.arosbio.io.DataSource;
import com.arosbio.io.IOSettings;
import com.arosbio.io.JarDataSink;
import com.arosbio.ml.cp.ConformalClassifier;
import com.arosbio.ml.cp.ConformalPredictor;
import com.arosbio.ml.cp.acp.ACP;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.tcp.TCP;
import com.arosbio.ml.cp.tcp.TCPClassifier;
import com.arosbio.ml.interfaces.AggregatedPredictor;
import com.arosbio.ml.interfaces.ClassificationPredictor;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.interfaces.RegressionPredictor;
import com.arosbio.ml.io.ConfAISerializer;
import com.arosbio.ml.io.MetaFileUtils;
import com.arosbio.ml.io.ModelIO;
import com.arosbio.ml.io.ModelIO.ModelJarProperties;
import com.arosbio.ml.io.ModelIO.ModelJarProperties.Directories;
import com.arosbio.ml.io.ModelIO.ModelType;
import com.arosbio.ml.io.ModelIO.URIUnpacker;
import com.arosbio.ml.io.ModelInfo;
import com.arosbio.ml.io.MountData;
import com.arosbio.ml.io.impl.PropertyFileStructure;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.arosbio.ml.vap.VennABERSPredictor;
import com.arosbio.ml.vap.avap.AVAP;
import com.arosbio.ml.vap.avap.AVAPClassifier;
import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * Handles serialization, i.e. saving/loading objects to/from file. The default save format is a Java Archive (JAR) which is a specialized form of ZIP with some additional
 * meta data. Thus compression of saved data is handled automatically by the format itself. When saving as a JAR, a class file is saved in the archive which enables information
 * to be found using the CLI using, e.g., {@code java -jar <model-file>.jar}, making it possible to print out the version of CPSign that was used for building the model.
 * 
 * <p>
 * To associate additional meta data with a data set or model, the concept of {@link com.arosbio.ml.io.MountData MountData} is introduced in versions of the {@code saveDataset(..)} and {@code saveModel(..)} methods 
 * where custom files can be saved in the JAR archive and queried using the {@code ModelIO.listMountedData(URI)} and the {@code getMountedDataAsXX(..)} methods.
 *  
 *    
 * @author staffan
 *
 */
public class ModelSerializer extends ConfAISerializer {

	private static final Logger LOGGER = LoggerFactory.getLogger(ModelSerializer.class);
	
	// ============================================================
	// ============================================================
	//   UTILITY METHODS 
	// ============================================================
	// ============================================================

	private static Map<Integer, String> convertToLabelsMapping(Object obj){
		Map<String,Object> loadedLabels = (JsonObject) obj;

		Map<Integer, String> correctedLabels = new HashMap<>();
		for (Map.Entry<String, Object> label: loadedLabels.entrySet()){
			correctedLabels.put(TypeUtils.asInt(label.getValue()), label.getKey());
		}
		return correctedLabels;
	}

	// ============================================================
	// ============================================================
	//   SAVING 
	// ============================================================
	// ============================================================

	
	// ============================================================
	//   PUBLIC METHODS
	// ============================================================

	public static void saveDataset(ChemPredictor predictor, File target, EncryptionSpecification spec,
			MountData... extraData) 
					throws InvalidKeyException, IllegalArgumentException, IOException{
		savePrecomputedData(predictor.getDataset(), predictor.getModelInfo(), target, spec, predictor.getProperties(), extraData);
	}

	public static void saveDataset(ChemDataset data, ModelInfo info, File target, EncryptionSpecification spec,
			MountData... extraData) 
					throws InvalidKeyException, IllegalArgumentException, IOException {
		savePrecomputedData(data, info, target, spec, data.getProperties(), extraData); 
	}


	public static void saveModel(
			ChemPredictor predictor, 
			File target, 
			EncryptionSpecification spec,
			MountData... extraData)
					throws IOException, IllegalArgumentException, InvalidKeyException {
		LOGGER.debug("Saving ChemPredictor to model JAR");
		long build_ts = System.currentTimeMillis();

		verifyPredictorCanBeSaved(predictor.getPredictor());
		ModelInfo info = predictor.getModelInfo();
		if (!info.isValid())
			throw new IllegalArgumentException("Must supply valid model information to write it to file");

		ModelIO.verifyEmptyFinalJAR(target);

		// Collect all properties
		Map<String,Object> predictorProps = predictor.getProperties();
		Map<String,Object> resourceProps = new HashMap<>();
		// Map<String,Object> testProps = ModelIO.getTestSection(info, predictor, build_ts);
		Map<String,Object> infoProps = ModelIO.getInfoSection(info, ModelType.CHEM_PREDICTOR, build_ts);
		Map<String,Object> allProps = new HashMap<>();
		// allProps.put(PropertyFileStructure.TestSection.NESTING_KEY, testProps);
		allProps.put(PropertyFileStructure.ResourceSection.NESTING_KEY, resourceProps);
		allProps.put(PropertyFileStructure.ParameterSection.NESTING_KEY, predictorProps);
		allProps.put(PropertyFileStructure.InfoSection.NESTING_KEY, infoProps);
		
		PredictorType mlType = getMLType(predictor.getPredictor());

		// Get the manifest
		Manifest mf = ModelIO.getManifest(predictor.getModelInfo());
		// Open up the output jar!
		try(
				FileOutputStream fos = new FileOutputStream(target);
				BufferedOutputStream buffered = new BufferedOutputStream(fos);
				JarOutputStream jarStream = new JarOutputStream(buffered, mf);
				DataSink sink = new JarDataSink(jarStream);
				){
			// Set up directory structure
			sink.createDirectory(Directories.MODEL_DIRECTORY);
			sink.createDirectory(Directories.DATA_DIRECTORY);
			sink.createDirectory(Directories.HELP_DIRECTORY);
			sink.createDirectory(Directories.ICONS_DIRECTORY);

			// write models / data
			LOGGER.debug("Saving predictor of type {}", predictor.getDefaultModelName());
			predictor.getPredictor().saveToDataSink(sink, Directories.MODEL_DIRECTORY, spec);

			resourceProps.put(PropertyFileStructure.ResourceSection.MODELS_DIR, Directories.MODEL_DIRECTORY);
			LOGGER.debug("wrote model(s)");

			// write dataset (descriptors, transformers, meta info)
			predictor.getDataset().saveStateExceptRecords(sink, Directories.DATA_DIRECTORY, spec);
			resourceProps.put(PropertyFileStructure.ResourceSection.DATA_DIR, Directories.DATA_DIRECTORY);
			LOGGER.debug("wrote data set info");

			// add icon
			ModelIO.addIcon(sink);

			// write the help-page
			HelpFile.writeHelpFile(predictor, mlType, sink);

			// write eclipse.project 
			LOGGER.debug("Skipped writing eclipse project file");

			// add RunMe.class
			ModelIO.addRunmeFile(sink);

			// Write extra data
			if (extraData != null && extraData.length>0) {
				ModelIO.mountExtraData(jarStream, extraData, resourceProps);
			}

			// write cpsign.json
			try (OutputStream jsonPropsStream = sink.getOutputStream(ModelJarProperties.JSON_PROPERTY_FILE);){
				MetaFileUtils.writePropertiesToStream(jsonPropsStream, allProps);
			}
			LOGGER.debug("written cpsign property file");
		}

		LOGGER.debug("Finished writing trained model jar");

	}

	// ============================================================
	//   IMPLEMENTING METHODS
	// ============================================================

	private static void savePrecomputedData(ChemDataset problem, 
			ModelInfo info, 
			File target, 
			EncryptionSpecification spec, 
			Map<String, Object> props,
			MountData... extraData)
					throws IOException, IllegalArgumentException, InvalidKeyException {

		LOGGER.debug("Saving ChemDataset to precomputed JAR");
		long build_ts = System.currentTimeMillis();

		if (problem.isEmpty())
			throw new IllegalArgumentException("ChemDataset is empty - cannot be saved to JAR");
		if (info==null || ! info.isValid())
			throw new IllegalArgumentException("Must supply valid model information to write it to file");

		ModelIO.verifyEmptyFinalJAR(target);

		// Remove predictor parameters (if any)
		if (props.containsKey(PropertyNameSettings.PREDICTOR_NESTING_KEY))
			props.remove(PropertyNameSettings.PREDICTOR_NESTING_KEY);

		// Set up the properties to save
		Map<String,Object> resourceProps = new HashMap<>();
		Map<String,Object> infoProps = ModelIO.getInfoSection(info, ModelType.PRECOMPUTED_DATA, build_ts);
		Map<String,Object> allProps = new HashMap<>();
		allProps.put(PropertyFileStructure.ResourceSection.NESTING_KEY, resourceProps);
		allProps.put(PropertyFileStructure.ParameterSection.NESTING_KEY, props);
		allProps.put(PropertyFileStructure.InfoSection.NESTING_KEY, infoProps);

		// Get the manifest
		Manifest mf = ModelIO.getManifest(info);
		// Open up the output jar!
		try(
				FileOutputStream fos = new FileOutputStream(target);
				BufferedOutputStream buffered = new BufferedOutputStream(fos);
				JarOutputStream jarStream = new JarOutputStream(buffered, mf);
				DataSink sink = new JarDataSink(jarStream);
				){
			// Set up directory structure
			sink.createDirectory(Directories.DATA_DIRECTORY);
			sink.createDirectory(Directories.HELP_DIRECTORY);
			sink.createDirectory(Directories.ICONS_DIRECTORY);

			problem.saveToDataSink(sink, Directories.DATA_DIRECTORY, spec);
			resourceProps.put(PropertyFileStructure.ResourceSection.DATA_DIR, Directories.DATA_DIRECTORY);
			LOGGER.debug("wrote precomputed data");

			// copy icon
			ModelIO.addIcon(sink);

			// write the help-page
			HelpFile.writeHelpFilePrecomp(problem,(boolean)props.get(PropertyNameSettings.IS_CLASSIFICATION_KEY),info, sink);

			ModelIO.addRunmeFile(sink);

			// Write extra data
			if (extraData != null ) {
				ModelIO.mountExtraData(jarStream, extraData, resourceProps);
			}

			// write cpsign.json
			try (OutputStream jsonPropsStream = sink.getOutputStream(ModelJarProperties.JSON_PROPERTY_FILE);){
				MetaFileUtils.writePropertiesToStream(jsonPropsStream, allProps);
				LOGGER.debug("written cpsign property file");
			}

		}

		LOGGER.debug("Finished writing precomputed data to jar");
	}

	/**
	 * Checks if the {@code location} is valid for a user to add {@link MountData} in, i.e. saving
	 * some custom information in. 
	 * @param location the location to validate
	 * @throws IllegalArgumentException exception in case {@code location} is invalid 
	 */
	public static void verifyLocationForCustomData(String location) throws IllegalArgumentException {
		if (location.equals(Directories.HELP_DIRECTORY+'/')) {
			// we don't care about help-directory - user can specify their own help files
		} else if (location.startsWith(Directories.MODEL_DIRECTORY+'/')) {
			throw new IllegalArgumentException("Not allowed mounting data in the 'data' directory");
		} else if (location.startsWith(Directories.ICONS_DIRECTORY)) {
			// don't care about icons either
		} else if (location.equals(ModelJarProperties.JSON_PROPERTY_FILE)) {
			throw new IllegalArgumentException("Not allowed mounting data in '"+ModelJarProperties.JSON_PROPERTY_FILE+"' location, this is reserved for internal usage");
		} 
	}


	// ============================================================
	//   UTILITY METHODS
	// ============================================================

	private static void verifyPredictorCanBeSaved(Predictor predictor) 
			throws IllegalArgumentException {
		if (predictor instanceof AggregatedPredictor && 
				((AggregatedPredictor) predictor).isPartiallyTrained()) {
			return; // this is fine!
		} else if (predictor instanceof TCP) {
			return; // TCP does not need to be trained!
		} else if (predictor.isTrained())
			return; // also fine
		else
			throw new IllegalArgumentException("Predictor is not trained, cannot save as trained JAR model");
	}

	private static PredictorType getMLType(Predictor predictor){

		if (predictor instanceof ClassificationPredictor){
			// Classification
			if (predictor instanceof ACP){
				return PredictorType.ACP_CLASSIFICATION;
			} else if (predictor instanceof AVAP){
				return PredictorType.VAP_CLASSIFICATION;
			} else if (predictor instanceof TCP){
				return PredictorType.TCP_CLASSIFICATION;
			}

		} else if (predictor instanceof RegressionPredictor){
			// Regression
			if (predictor instanceof ACP){
				return PredictorType.ACP_REGRESSION;
			}
		}

		throw new IllegalArgumentException("PredictionModel of unknown MLType="+predictor.getClass());
	}

	private static class HelpFile {

		private static final String TRAINED_MODEL_HELP_TEMPLATE_MARKDOWN = "resources/help_model_template.md";
		private static final String PRECOMPUTED_HELP_TEMPLATE_MARKDOWN = "resources/help_dataset_template.md"; 

		// Lookups
		private static final String PREDICTOR_TYPE = "PREDICTOR_TYPE";
		private static final String UNDERLYING_SCORER_TYPE = "SCORER_TYPE";
		private static final String MODEL_NAME = "MODEL_NAME";
		private static final String NUM_TOTAL_FEATURES = "NUM_FEATURES";
		private static final String NUM_SIGNATURES_FEATURES = "NUM_SIGNATURES";
		private static final String NUM_OBSERVATIONS = "NUM_OBSERVATIONS";
		private static final String DATA_TYPE = "DATA_MODEL_TYPE";
		private static final String START_H = "START_H";
		private static final String END_H = "END_H";

		private static void writeHelpFile(ChemPredictor predictor, PredictorType mlType, DataSink target) throws IOException {

			// Get the template
			String helpTemplate=null;
			try (InputStream helptemplateStream = ModelSerializer.class.getClassLoader().getResourceAsStream(TRAINED_MODEL_HELP_TEMPLATE_MARKDOWN);){
				helpTemplate = IOUtils.toString(helptemplateStream,IOSettings.CHARSET);
			}

			// Get the replacement mapping
			Map<String,String> valueMap = new HashMap<>();
			valueMap.put(PREDICTOR_TYPE, mlType.getFancyName());
			valueMap.put(UNDERLYING_SCORER_TYPE, getScorerTypeTxt(predictor.getPredictor()));
			valueMap.put(MODEL_NAME, predictor.getModelInfo().getName());
			valueMap.putAll(getFeaturesInfo(predictor.getDataset()));
			valueMap.put(NUM_OBSERVATIONS, ""+predictor.getPredictor().getNumObservationsUsed());

			// Perform string-substitution and save file
			try(
					OutputStream ostream = target.getOutputStream(Directories.HELP_DIRECTORY+'/'+ModelJarProperties.HELP_FILE_NAME);
					){

				// Perform replacement
				String helpTxtContent = new StringSubstitutor(valueMap)
						.setEnableUndefinedVariableException(true)
						.replace(helpTemplate);

				IOUtils.copy(IOUtils.toInputStream(helpTxtContent, IOSettings.CHARSET), ostream);
				LOGGER.debug("written help-file");
			} catch (Exception e) {
				LOGGER.error("Failed writing help-file",e);
			}
		}

		private static void writeHelpFilePrecomp(ChemDataset data, boolean isClf, ModelInfo info, DataSink target) 
				throws IOException {
			String helpTemplate=null;
			try (InputStream helptemplateStream = ModelSerializer.class.getClassLoader().getResourceAsStream(PRECOMPUTED_HELP_TEMPLATE_MARKDOWN);){
				helpTemplate = IOUtils.toString(helptemplateStream, IOSettings.CHARSET);
			}

			// Get the replacement mapping
			Map<String,String> valueMap = new HashMap<>();
			valueMap.put(MODEL_NAME, info.getName());
			valueMap.put(DATA_TYPE, (isClf ? "Classification" : "Regression"));
			valueMap.putAll(getFeaturesInfo(data));
			valueMap.put(NUM_OBSERVATIONS, ""+data.getNumRecords());

			try(
					OutputStream ostream = target.getOutputStream(Directories.HELP_DIRECTORY+'/'+ModelJarProperties.HELP_FILE_NAME);
					){

				// Perform replacement
				String helpTxtContent = new StringSubstitutor(valueMap)
						.setEnableUndefinedVariableException(true)
						.replace(helpTemplate);

				IOUtils.copy(IOUtils.toInputStream(helpTxtContent, IOSettings.CHARSET), ostream);

				LOGGER.debug("Saved help-file");
			} catch (Exception e) {
				LOGGER.error("Failed saving help-file", e);
			}
		}

		private static Map<String,String> getFeaturesInfo(ChemDataset data){
			Map<String,String> valueMap = new HashMap<>();

			int totalNumAttr = data.getNumAttributes();
			int signStartInd = data.getSignaturesDescriptorStartIndex();
			int numSign = 0;
			if (signStartInd>=0) {
				numSign = totalNumAttr - signStartInd;
				for (ChemDescriptor d : data.getDescriptors()) {
					if (d instanceof SignaturesDescriptor) {
						valueMap.put(START_H, ""+((SignaturesDescriptor) d).getStartHeight());
						valueMap.put(END_H, ""+((SignaturesDescriptor) d).getEndHeight());
						break;
					}
				}
			}

			valueMap.put(NUM_TOTAL_FEATURES, ""+totalNumAttr);
			valueMap.put(NUM_SIGNATURES_FEATURES, ""+numSign);
			return valueMap;
		}

		private static String getScorerTypeTxt(Predictor pred) {
			if (pred instanceof ConformalPredictor) {
				if (pred instanceof ConformalClassifier) {
					if (pred instanceof TCPClassifier) {
						return ((TCPClassifier) pred).getNCM().getModel().getName();
					} else {
						// ACP
						return ((ACPClassifier)pred).getICPImplementation().getNCM().getModel().getName();
					}
				} else {
					return ((ACPRegressor) pred).getICPImplementation().getNCM().getModel().getName();
				}
			} else if (pred instanceof VennABERSPredictor) {
				return ((AVAPClassifier) pred).getScoringAlgorithm().getName();
			} 
			return "Undefined";
		}

	}




	public static ChemDataset loadDataset(URI uri, EncryptionSpecification spec) 
			throws InvalidKeyException, IOException {
		try (
				URIUnpacker unpacker = new URIUnpacker(uri);
				){
			return loadDataset(unpacker.getSrc(), spec);
		}
	}
	
	public static ChemDataset loadDataset(DataSource src, EncryptionSpecification spec) 
			throws InvalidKeyException, IOException {
		return loadPrecomputedData(src, 
				ModelIO.validatePropertiesAndFlatten(ModelIO.getCPSignProperties(src)), 
				spec);
	}


	public static ChemPredictor loadChemPredictor(URI uri, EncryptionSpecification spec) 
			throws InvalidKeyException, IOException {
		try (
				URIUnpacker unpacker = new URIUnpacker(uri);
				){
			return loadChemPredictor(unpacker.getSrc(), spec);
		}
	}

	public static ChemPredictor loadChemPredictor(DataSource src, EncryptionSpecification spec) 
			throws InvalidKeyException, IOException {
		return loadChemPredictor(src, 
				ModelIO.validatePropertiesAndFlatten(ModelIO.getCPSignProperties(src)), 
				spec);
	}


	@SuppressWarnings("unchecked")
	private static ChemPredictor loadChemPredictor(DataSource source, Map<String,Object> props, EncryptionSpecification spec) 
			throws InvalidKeyException, IOException {

		// Predictor section
		Map<String,Object> predictorProps = null;
		if (props.containsKey(PropertyNameSettings.PREDICTOR_NESTING_KEY))
			predictorProps = (Map<String, Object>) props.get(PropertyNameSettings.PREDICTOR_NESTING_KEY);
		else
			predictorProps = props;

		// Load model and initiate Predictor
		ChemPredictor predictor=null;
		ChemDataset problem = new ChemDataset();
		PredictorType mlType = PredictorType.getPredictorType(TypeUtils.asInt(predictorProps.get(PropertyNameSettings.ML_TYPE_KEY)));

		switch (mlType) {
		case ACP_CLASSIFICATION:
			predictor = new ChemCPClassifier(new ACPClassifier());
			break;
		case ACP_REGRESSION:
			predictor = new ChemCPRegressor(new ACPRegressor());
			break;
		case TCP_CLASSIFICATION:
			predictor = new ChemCPClassifier(new TCPClassifier());
			break;
		case VAP_CLASSIFICATION:
			predictor = new ChemVAPClassifier(new AVAPClassifier());
			break;
		default:
			throw new IllegalArgumentException("ML type {"+mlType+"} not supported in ModelSerializer.load(..)");
		}

		predictor.getPredictor().loadFromDataSource(source, (String) props.get(PropertyFileStructure.ResourceSection.MODELS_DIR), spec);

		LOGGER.debug("Loaded prediction model");

		// Load the data set state
		if (props.containsKey(PropertyFileStructure.ResourceSection.DATA_DIR)){
			// Updated property structure
			problem.loadStateExceptRecords(source, (String) props.get(PropertyFileStructure.ResourceSection.DATA_DIR), spec);
		} else if (props.containsKey(PropertyFileStructure.ResourceSection.LEGACY_DATA_LOC)){
			problem.loadStateExceptRecords(source, (String) props.get(PropertyFileStructure.ResourceSection.LEGACY_DATA_LOC), spec);
		} else {
			LOGGER.debug("Failed finding data location from properties: {}", props);
			throw new IOException("Unsupported serialization format of model, is this an old model?");
		}
		

		predictor.setDataset(problem);
		LOGGER.debug("Loaded problem: {}", problem);

		// Set percentiles
		Object highPercentileOrNull = CollectionUtils.getArbitratyDepth(props, PropertyNameSettings.HIGH_PERCENTILE_KEY);
		Object lowPercentileOrNull = CollectionUtils.getArbitratyDepth(props, PropertyNameSettings.LOW_PERCENTILE_KEY);

		if (highPercentileOrNull != null) {
			predictor.setHighPercentile(TypeUtils.asDouble(highPercentileOrNull));
			LOGGER.debug("Setting high percentile={}", highPercentileOrNull);
		} if (lowPercentileOrNull != null) {
			predictor.setLowPercentile(TypeUtils.asDouble(lowPercentileOrNull));
			LOGGER.debug("Setting low percentile={}",lowPercentileOrNull);
		}

		// Load labels (if classification)
		if (predictor instanceof ChemClassifier){
			((ChemClassifier) predictor).setLabels(ModelSerializer.convertToLabelsMapping(CollectionUtils.getArbitratyDepth(props, PropertyNameSettings.CLASS_LABELS_LIST_KEY)));
			LOGGER.debug("Loaded labels={}",((ChemClassifier) predictor).getLabels());
		}

		// Set the model info
		ModelInfo info = ModelInfo.fromProperties(props);
		predictor.withModelInfo(info);
		LOGGER.debug("Loaded model info: {}", info);

		LOGGER.debug("Finished loading ChemPredictor");
		return predictor;
	}

	private static ChemDataset loadPrecomputedData(DataSource source, Map<String,Object> props, EncryptionSpecification spec) 
			throws InvalidKeyException, IOException{
		
		ChemDataset data = new ChemDataset();
		data.loadFromDataSource(source,(String)props.get(PropertyFileStructure.ResourceSection.DATA_DIR), spec);

		if (data.getTextualLabels() != null){
			// Labels already loaded from meta data 
			return data;
		}
		// Here we do not know if it's regression or classification data, if it is - set labels
		if ((boolean)props.getOrDefault(PropertyNameSettings.IS_CLASSIFICATION_KEY, false)){
			data.setTextualLabels(new NamedLabels(ModelSerializer.convertToLabelsMapping(CollectionUtils.getArbitratyDepth(props, PropertyNameSettings.CLASS_LABELS_LIST_KEY))));			
		} 
		
		return data;
	}

	

}

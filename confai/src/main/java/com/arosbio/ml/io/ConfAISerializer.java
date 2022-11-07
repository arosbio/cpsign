/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.io;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.GlobalConfig.Defaults.PredictorType;
import com.arosbio.commons.TypeUtils;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.DataSink;
import com.arosbio.io.DataSource;
import com.arosbio.io.JarDataSink;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.tcp.TCPClassifier;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.io.ModelIO.ModelJarProperties.Directories;
import com.arosbio.ml.io.ModelIO.ModelType;
import com.arosbio.ml.io.ModelIO.URIUnpacker;
import com.arosbio.ml.io.impl.PropertyFileStructure;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.arosbio.ml.vap.avap.AVAPClassifier;

/**
 * Handles serialization, i.e. saving/loading objects to/from file. When saving as a JAR, a class file is saved in the archive which enables information
 * to be found using the CLI using, e.g., {@code java -jar <model-file>.jar}, making it possible to print out the version of CPSign that was used for building the model.
 * 
 * <p>
 * To associate additional meta data with a data set or model, the concept of {@link com.arosbio.ml.io.MountData MountData} is introduced in versions of the {@code saveDataset(..)} and {@code saveModel(..)} methods 
 * where custom files can be saved in the JAR archive and queried using the {@link com.arosbio.ml.io.ModelIO#listMountLocations(URI)} and the {@code getMountedDataAsXX(..)} methods.
 *  
 *    
 * @author staffan
 *
 */
public class ConfAISerializer {

	static final Logger LOGGER = LoggerFactory.getLogger(ConfAISerializer.class);



	// ============================================================
	//   PUBLIC METHODS
	// ============================================================

	public static ModelType getType(URI uri) throws IOException {
		return getType(ModelIO.getCPSignProperties(uri));
	}

	public static<K,V> ModelType getType(Map<K,V> properties) throws IOException {
		return ModelIO.ModelType.getType(properties);
	}

	/**
	 * Save a (trained) {@link Predictor} model to a JAR file
	 * @param predictor The model
	 * @param target The target File, must be empty/non existing
	 * @param spec an {@link EncryptionSpecification} or {@code null}
	 * @param extraData Extra files/data that should be added to the JAR
	 * @throws InvalidKeyException Any problems related to encryption 
	 * @throws IllegalArgumentException Any illegal arguments, or if {@code predictor} not trained
	 * @throws IOException Issues with writing to disc
	 */
	public static void saveModel(
			Predictor predictor, 
			File target, 
			EncryptionSpecification spec,
			MountData... extraData) 
					throws InvalidKeyException, IllegalArgumentException, IOException {
		LOGGER.debug("Saving Predictor to JAR");
		long build_ts = System.currentTimeMillis();

		ModelIO.verifyPredictorCanBeSaved(predictor);
		ModelInfo info = ModelIO.getInfo(predictor);
		if (!info.isValid())
			throw new IllegalArgumentException("Must supply valid model information to write it to file");

		ModelIO.verifyEmptyFinalJAR(target);

		// Collect all properties
		Map<String,Object> parametersSection = new HashMap<>();
		parametersSection.put(PropertyNameSettings.PREDICTOR_NESTING_KEY, predictor.getProperties());

		Map<String,Object> resourceProps = new HashMap<>();

		Map<String,Object> infoProps = ModelIO.getInfoSection(info, ModelIO.ModelType.PLAIN_PREDICTOR, build_ts);
		Map<String,Object> allProps = new HashMap<>();
		allProps.put(PropertyFileStructure.ResourceSection.NESTING_KEY, resourceProps);
		allProps.put(PropertyFileStructure.ParameterSection.NESTING_KEY, parametersSection);
		allProps.put(PropertyFileStructure.InfoSection.NESTING_KEY, infoProps);

		// Get the manifest
		Manifest mf = ModelIO.getManifest(info);
		// Open up the output jar!
		try(
				FileOutputStream fos = new FileOutputStream(target);
				BufferedOutputStream buffered = new BufferedOutputStream(fos);
				JarOutputStream jarStream = new JarOutputStream(buffered, mf);
				DataSink jarSink = new JarDataSink(jarStream);
				){
			// Set up directory structure
			jarSink.createDirectory(Directories.MODEL_DIRECTORY);
			jarSink.createDirectory(Directories.HELP_DIRECTORY);
			jarSink.createDirectory(Directories.ICONS_DIRECTORY);

			// write models
			predictor.saveToDataSink(jarSink, Directories.MODEL_DIRECTORY, spec);
			resourceProps.put(PropertyFileStructure.ResourceSection.MODELS_DIR, Directories.MODEL_DIRECTORY);
			LOGGER.debug("wrote model(s)");

			// add icon
			ModelIO.addIcon(jarSink);
			
			// Write extra data
			if (extraData != null && extraData.length>0) {
				ModelIO.mountExtraData(jarStream, extraData, resourceProps);
			}

			// write cpsign.json
			try (OutputStream jsonPropsStream = jarSink.getOutputStream(ModelIO.ModelJarProperties.JSON_PROPERTY_FILE);){
				MetaFileUtils.writePropertiesToStream(jsonPropsStream, allProps);
			}
			LOGGER.debug("written cpsign property file");

		}

		LOGGER.debug("Finished writing trained sparse predictor to jar");

	}


	public static Predictor loadPredictor(URI uri, EncryptionSpecification spec) 
			throws InvalidKeyException, IOException {
		try (
				URIUnpacker unpacker = new URIUnpacker(uri);
				){
			return loadPredictor(unpacker.getSrc(), spec);
		}
	}

	public static Predictor loadPredictor(DataSource src, EncryptionSpecification spec) 
			throws InvalidKeyException, IOException {
		return loadSparsePredictor(src, 
			ModelIO.validatePropertiesAndFlatten(ModelIO.getCPSignProperties(src)), 
				spec);
	}


	@SuppressWarnings("unchecked")
	private static Predictor loadSparsePredictor(DataSource source, Map<String,Object> props, EncryptionSpecification spec) 
			throws InvalidKeyException, IllegalArgumentException, IOException{
		// Load model and initiate Predictor

		// Predictor section
		Map<String,Object> predictorProps = null;
		if (props.containsKey(PropertyNameSettings.PREDICTOR_NESTING_KEY))
			predictorProps = (Map<String, Object>) props.get(PropertyNameSettings.PREDICTOR_NESTING_KEY);
		else
			predictorProps = props;

		Predictor predictor=null;
		PredictorType mlType = PredictorType.getPredictorType(TypeUtils.asInt(predictorProps.get(PropertyNameSettings.ML_TYPE_KEY)));

		switch (mlType) {
		case ACP_CLASSIFICATION:
			predictor = new ACPClassifier();
			break;
		case ACP_REGRESSION:
			predictor = new ACPRegressor();
			break;
		case TCP_CLASSIFICATION:
			predictor = new TCPClassifier();
			break;
		case VAP_CLASSIFICATION:
			predictor = new AVAPClassifier();
			break;
		default:
			throw new IllegalArgumentException("ML type {"+mlType+"} not supported in ModelLoader");
		}

		predictor.loadFromDataSource(source, (String) props.get(PropertyFileStructure.ResourceSection.MODELS_DIR), spec);

		LOGGER.debug("Loaded prediction model");

		// Set the model info
		predictor.setModelInfo(ModelInfo.fromProperties(props));
		LOGGER.debug("Loaded model info");

		LOGGER.debug("Finished loading sparse Predictor");
		return predictor;
	}


}

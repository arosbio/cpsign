/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp.icp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.config.ImplementationConfig;
import com.arosbio.commons.mixins.ResourceAllocator;
import com.arosbio.data.DataRecord;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.FeatureVector.Feature;
import com.arosbio.data.SparseFeature;
import com.arosbio.data.SparseFeatureImpl;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.DataSink;
import com.arosbio.io.DataSource;
import com.arosbio.ml.TrainingsetValidator;
import com.arosbio.ml.algorithms.Classifier;
import com.arosbio.ml.algorithms.MLAlgorithm;
import com.arosbio.ml.algorithms.impl.DefaultMLParameterSettings;
import com.arosbio.ml.cp.ConformalPredictor;
import com.arosbio.ml.cp.nonconf.NCM;
import com.arosbio.ml.cp.nonconf.calc.PValueCalculator;
import com.arosbio.ml.cp.nonconf.calc.SmoothedPValue;
import com.arosbio.ml.cp.nonconf.calc.StandardPValue;
import com.arosbio.ml.cp.nonconf.classification.NCMMondrianClassification;
import com.arosbio.ml.cp.nonconf.utils.NCMUtils;
import com.arosbio.ml.interfaces.ClassificationPredictor;
import com.arosbio.ml.io.MetaFileUtils;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.arosbio.ml.sampling.TrainSplit;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.Jsoner;


/**
 * Mondrian Inductive Conformal Prediction
 * @author staffan
 *
 */
public class ICPClassifier implements ICP, ClassificationPredictor {

	private static final Logger LOGGER = LoggerFactory.getLogger(ICPClassifier.class);
	private final static String ICP_META_INFO_PATH = ".meta.json";
	private final static String NCS_PATH = ".ncs";

	//	private MLAlgorithm model;
	private NCMMondrianClassification ncm;
	private PValueCalculator pValueCalculator = new StandardPValue();

	// Once trained
	private Map<Integer,PValueCalculator> fittedNCSEstimators;

	// The nonconformity lists for all classes
	private Map<Integer,List<Double>> nonconfLists;
	private int numTrainingObservations;

	/* 
	 * =================================================
	 * 			CONSTRUCTORS
	 * =================================================
	 */

	/**
	 * Creates an empy instance, that will require loading or setting of all parameters individually 
	 */
	public ICPClassifier() {
	}

	public ICPClassifier(NCMMondrianClassification ncm){
		super();
		this.ncm = ncm;
	}

	public ICPClassifier clone() {
		ICPClassifier clone = new ICPClassifier();
		if (this.ncm != null)
			clone.ncm = ncm.clone();
		if (pValueCalculator != null)
			clone.pValueCalculator = pValueCalculator.clone();
		return clone;
	}

	/* 
	 * =================================================
	 * 			GETTERS / SETTERS
	 * =================================================
	 */

	@Override
	public void setPValueCalculator(PValueCalculator estimator) {
		this.pValueCalculator = estimator; 
		if (fittedNCSEstimators != null && !fittedNCSEstimators.isEmpty())
			fittedNCSEstimators.clear(); // invalidate the fitted estimators!
	}

	@Override
	public PValueCalculator getPValueCalculator() {
		return pValueCalculator;
	}

	public Map<Integer,List<Double>> getNCS(){
		return nonconfLists;
	}

	public void setNCS(Map<Integer,List<Double>> nonconf) {
		this.nonconfLists = nonconf; 
	}

	@Override
	public Set<Integer> getLabels(){
		Classifier model = ncm.getModel();
		if (model!=null && model.isFitted()){
			return new HashSet<>(model.getLabels());
		}
		return new HashSet<>();
	}

	@Override
	public boolean isTrained() {
		MLAlgorithm model = ncm.getModel();
		return model.isFitted() && nonconfLists !=null && !nonconfLists.isEmpty();
	}

	public void setSeed(long seed) {
		ncm.getModel().setSeed(seed);
		pValueCalculator.setRNGSeed(seed);
	}

	public Long getSeed() {
		return ncm.getModel().getSeed();
	}

	public NCMMondrianClassification getNCM(){
		return ncm;
	}

	public void setNCM(NCMMondrianClassification nonconfMeasure){
		this.ncm = nonconfMeasure;
	}

	public int getNumObservationsUsed() {
		if (! ncm.isFitted())
			return 0;
		return numTrainingObservations;
	}

	public int getNumClasses() {
		Classifier model = ncm.getModel();
		if (model == null)
			return 0;
		return model.getLabels().size();
	}

	@Override
	public boolean releaseResources() {
		if (ncm != null && ncm.getModel() instanceof ResourceAllocator){
			((ResourceAllocator) ncm.getModel()).releaseResources();
			return true;
		}
		return false;
	}

	@Override
	public boolean holdsResources() {
		return isTrained() && 
			(ncm.getModel() instanceof ResourceAllocator) && 
			((ResourceAllocator) ncm.getModel()).holdsResources();
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String,Object> props = new HashMap<>();
		props.putAll(ncm.getProperties());
		props.put(PropertyNameSettings.ML_TYPE_KEY, "ICP Classification");
		props.put(PropertyNameSettings.IS_CLASSIFICATION_KEY, true);
		props.put(PropertyNameSettings.NUM_OBSERVATIONS_KEY, numTrainingObservations);
		props.put(PValueCalculator.PVALUE_CALCULATOR_NAME_KEY, pValueCalculator.getName());
		props.put(PValueCalculator.PVALUE_CALCULATOR_ID_KEY, pValueCalculator.getID());
		if (pValueCalculator.getRNGSeed() != null)
			props.put(PValueCalculator.PVALUE_CALCULATOR_SEED_KEY, pValueCalculator.getRNGSeed());
		return props;
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		List<ConfigParameter> params = new ArrayList<>();
		params.addAll(ncm.getConfigParameters());

		// ncm estimator
		params.add(new ImplementationConfig.Builder<>(ConformalPredictor.CONFIG_PVALUE_CALC_PARAM_NAMES, PValueCalculator.class)
			.defaultValue(new SmoothedPValue()).build());
		return params;
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalArgumentException {
		for (Map.Entry<String, Object> kv : params.entrySet()) {
			try {
			if (CollectionUtils.containsIgnoreCase(ConformalPredictor.CONFIG_PVALUE_CALC_PARAM_NAMES, kv.getKey())) {
				if (kv.getValue() instanceof PValueCalculator) {
					pValueCalculator = (PValueCalculator) kv.getValue();
				} else {
					pValueCalculator = FuzzyServiceLoader.load(PValueCalculator.class, kv.getValue().toString());
				}
			}
			} catch (Exception e) {
				LOGGER.debug("Got invalid config argument: {}", kv);
				throw Configurable.getInvalidArgsExcept(kv.getKey(), kv.getValue()); 
			}
		}
		// pass on to underlying ncm
		ncm.setConfigParameters(params);
	}


	/* 
	 * =================================================
	 * 			TRAINING
	 * =================================================
	 */
	@Override
	public void train(TrainSplit icpdataset) 
			throws IllegalArgumentException {
		if (ncm == null)
			throw new IllegalStateException("No NCM set in the ICP");

		TrainingsetValidator.getInstance().validateClassification(icpdataset);
		LOGGER.debug("Training ICP classification model");

		//Train the Nonconf Measure
		ncm.trainNCM(icpdataset.getProperTrainingSet());
		LOGGER.debug("Finished training the NCM");

		// Init lists for nonconformity scores
		nonconfLists = new HashMap<>();
		Classifier model = ncm.getModel();
		for (int l : model.getLabels())
			nonconfLists.put(l, 
					new ArrayList<Double>((int)(icpdataset.getCalibrationSet().size()*2/3))); // pre-set some size 

		// Calculate nonconformity scores for the calibration set
		try {
			for (DataRecord rec: icpdataset.getCalibrationSet()){
				Map<Integer, Double> ncScores = ncm.calculateNCS(rec.getFeatures());
				int label = (int) rec.getLabel();
				nonconfLists.get(label).add(ncScores.get(label));
			}
		} catch (IllegalStateException e){
			LOGGER.debug("exception when calculating the nonconformity scores",e);
			throw new IllegalArgumentException(e.getMessage(),e);
		}

		fittedNCSEstimators = null; // make sure to remove old fitted extractors after re-training

		for (List<Double> nonconf: nonconfLists.values()) {
			Collections.sort(nonconf);
		}

		LOGGER.debug("nonconf scores computed");
		numTrainingObservations = icpdataset.getTotalNumTrainingRecords();

	}

	private void fitNCSEstimators() {
		fittedNCSEstimators = new HashMap<>();

		for (Map.Entry<Integer,List<Double>> nonconf: nonconfLists.entrySet()) {
			if (nonconf.getValue().isEmpty())
				throw new IllegalArgumentException("No nonconformity scores for class {" + nonconf.getKey()+'}');
			Collections.sort(nonconf.getValue());
			PValueCalculator forLabel = pValueCalculator.clone();
			forLabel.build(nonconf.getValue());
			fittedNCSEstimators.put(nonconf.getKey(), forLabel);
		}
	}

	/* 
	 * =================================================
	 * 			PREDICTION
	 * =================================================
	 */

	private void assertIsTrained(){
		if (! isTrained())
			throw new IllegalStateException("Predictor not trained");
	}

	/**
	 * Predict a test instance
	 * @param instance the instance to predict
	 * @return the p-values 
	 */
	public Map<Integer, Double> predict(FeatureVector instance) throws IllegalStateException {
		assertIsTrained();
		if (ncm == null)
			throw new IllegalStateException("No nonconformity measure set");
		if (instance == null)
			throw new IllegalArgumentException("example to predict was null");

		if (fittedNCSEstimators == null || fittedNCSEstimators.isEmpty()) {
			fitNCSEstimators();
			LOGGER.trace("Fitted PvalueExtractors of type {}", pValueCalculator.getClass());
		}

		Map<Integer,Double> ncScores = ncm.calculateNCS(instance);

		//Calculate p-values
		Map<Integer, Double> prediction = new HashMap<>();
		for (Map.Entry<Integer,Double> nc : ncScores.entrySet()) {
			prediction.put(nc.getKey(), fittedNCSEstimators.get(nc.getKey()).getPvalue(nc.getValue()));
		}

		LOGGER.trace("p-values={}",prediction);

		return prediction;
	}

	public List<SparseFeature> calculateGradient(FeatureVector example, int label) 
			throws IllegalStateException {
		return calculateGradient(example, DefaultMLParameterSettings.DEFAULT_STEPSIZE, label);
	}

	public List<SparseFeature> calculateGradient(FeatureVector example, double stepsize, int label) 
			throws IllegalStateException {

		//The gradient to return, same size as the example to predict
		List<SparseFeature> gradient = new ArrayList<>(example.getNumExplicitFeatures());

		//First do a normal prediction
		Map<Integer, Double> result = predict(example);
		double originalPvalue = result.get(label);

		LOGGER.trace("========\nOriginal features: {}", example);
		LOGGER.debug("Computing gradient for label={}, original pvalue={}",label,originalPvalue);

		// Loop over all features and make a prediction in each case

		for (Feature f : example) {	

			// Save instance 
			Feature oldInstance = f.clone();

			// Set the new updated value
			example.setFeature(oldInstance.getIndex(), oldInstance.getValue()+stepsize);

			//predict and extract the p-value for the class
			Map<Integer, Double> fresult = predict(example);
			double pval = fresult.get(label);

			//Set diff as value in gradient array
			double diff = (pval-originalPvalue)/stepsize;
			gradient.add(new SparseFeatureImpl(f.getIndex(), diff));
			LOGGER.trace("Normal={}, altered={}, diff={}",originalPvalue,pval,diff);

			// Set the value in example feature back
			example.setFeature(f.getIndex(), oldInstance.getValue());
		}

		return gradient;
	}


	/* 
	 * =================================================
	 * 			SAVE / LOAD
	 * =================================================
	 */

	@Override
	public void saveToDataSink(DataSink sink, String path, EncryptionSpecification spec)
			throws IOException, InvalidKeyException, IllegalStateException {
		if (!isTrained())
			throw new IllegalStateException("Model is not trained");
		// Save the NCM to the sink - i.e. all the models
		ncm.saveToDataSink(sink, path+NCMUtils.NCM_BASE_PATH, spec);

		// Save the NCS to sink (alphas)
		String nonconfPath = path+NCS_PATH;
		if (spec != null){
			try(
					OutputStream alphasStream = sink.getOutputStream(nonconfPath);
					OutputStream encryptedStream = spec.encryptStream(alphasStream);){
				writeNCS2Stream(encryptedStream);
			}
		} else {
			try(OutputStream alphasStream = sink.getOutputStream(nonconfPath);){
				writeNCS2Stream(alphasStream);
			}
		}
		LOGGER.debug("Saved NCS to Sink in ICPClassifier, location={}",nonconfPath);

		// Save the ICP meta-info (for loading the correct NCM etc)
		try (OutputStream metaStream = sink.getOutputStream(path + ICP_META_INFO_PATH)){
			Map<String, Object> props = getProperties();
			props.put(PropertyNameSettings.IS_ENCRYPTED, spec != null);
			MetaFileUtils.writePropertiesToStream(metaStream, props);
			LOGGER.debug("Saved icp info to location={}{}", path,ICP_META_INFO_PATH);
		} catch (Exception e) {
			LOGGER.debug("Failed saving meta info to stream",e);
		}
	}

	@Override
	public void loadFromDataSource(DataSource src, String path, EncryptionSpecification spec)
			throws IOException, InvalidKeyException {
		if (! src.hasEntry(path+ICP_META_INFO_PATH) || 
				! src.hasEntry(path+NCS_PATH))
			throw new IOException("No ICP model in source under modelName=" + path);

		// Load properties 
		Map<String, Object> props = null;
		try (InputStream metaStream = src.getInputStream(path+ICP_META_INFO_PATH)){
			props = MetaFileUtils.readPropertiesFromStream(metaStream);
		} catch (Exception e) {
			LOGGER.debug("Failed reading the ICP properties file",e);
			throw new IOException("Failed reading the ICP model");
		}

		if (props.containsKey(PropertyNameSettings.NUM_OBSERVATIONS_KEY)) {
			try {
				numTrainingObservations = TypeUtils.asInt(props.get(PropertyNameSettings.NUM_OBSERVATIONS_KEY));
			} catch (NumberFormatException e) {
				LOGGER.debug("Could not parse the number of training instances correctly");
			}
		}

		// p-value calculator
		if (props.containsKey(PValueCalculator.PVALUE_CALCULATOR_ID_KEY)) {
			int id = TypeUtils.asInt(props.get(PValueCalculator.PVALUE_CALCULATOR_ID_KEY));
			LOGGER.debug("Retreiving pvalue-calculator based on ID: {}", id);
			pValueCalculator = FuzzyServiceLoader.load(PValueCalculator.class, id);
		} else if (props.containsKey(PValueCalculator.PVALUE_CALCULATOR_NAME_KEY)) {
			String name = props.get(PValueCalculator.PVALUE_CALCULATOR_NAME_KEY).toString();
			LOGGER.debug("Retreiving pvalue-calculator based on name: {}", name);
			pValueCalculator = FuzzyServiceLoader.load(PValueCalculator.class, name);
		} else {
			LOGGER.debug("No pvalue-calculator info saved in model-file, using the default one");
		}
		if (props.containsKey(PValueCalculator.PVALUE_CALCULATOR_SEED_KEY)) {
			long seed = TypeUtils.asLong(props.get(PValueCalculator.PVALUE_CALCULATOR_SEED_KEY));
			pValueCalculator.setRNGSeed(seed);
			LOGGER.debug("Set the p-value calculator seed to: {}", seed);
		}


		// Check if encrypted
		Object isEncr = props.get(PropertyNameSettings.IS_ENCRYPTED);
		if (isEncr == null) {
			LOGGER.debug("ICP Property file was not correct (missing IS_ENCRYPTED value), got: {}", props);
			throw new IOException("Property file not correct");
		}

		if ((boolean) isEncr && spec == null) {
			throw new InvalidKeyException("Model is encrypted - no encryption key sent to decrypt it");
		}
		if (!(boolean)isEncr) {
			spec = null; // Remove it
		}

		// Instantiate the NCM and let it load the models 
		Object ncmID = props.get(PropertyNameSettings.NCM_ID);
		if (ncmID != null) {
			NCM theNCM = FuzzyServiceLoader.load(NCM.class, ncmID.toString());
			if (theNCM instanceof NCMMondrianClassification) {
				ncm = (NCMMondrianClassification) theNCM;
				ncm.loadFromDataSource(src, path + NCMUtils.NCM_BASE_PATH, spec);
			}
		}
		else {
			throw new IOException("No NCM name saved in the ICP meta-info: cannot load it properly");
		}

		// Load the ncs
		try (InputStream ncsStream = src.getInputStream(path+NCS_PATH);){
			if(spec != null)
				loadNCSFromStream(spec.decryptStream(ncsStream));
			else
				loadNCSFromStream(ncsStream);
			LOGGER.debug("loaded ncs");
		} catch (IOException e){
			LOGGER.debug("Could not load ncs from stream, this might not be a classification model?");
			throw new IOException("Could not load ncs from stream, this might not be a classification model?");
		}

		LOGGER.debug("Finished loading ICP");

	}

	private void writeNCS2Stream(OutputStream stream) throws IOException {
		try(BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));){
			List<Integer> labels = new ArrayList<>(getLabels());
			Collections.sort(labels);
			for (int label: labels) {
				writer.write(Jsoner.serialize(nonconfLists.get(label)));
				writer.newLine();
			}
		}
		LOGGER.debug("Wrote nonconformities to stream");
	}

	private void loadNCSFromStream(InputStream stream) throws IOException {
		nonconfLists = new HashMap<>();

		try(BufferedReader reader = new BufferedReader(new InputStreamReader(stream));){
			List<Integer> labels = new ArrayList<>(getLabels());
			Collections.sort(labels);
			for (int label: labels) {
				String rawNonconfString=reader.readLine();
				try {
					JsonArray ncsArr = (JsonArray) Jsoner.deserialize(rawNonconfString);
					List<Double> ncs = new ArrayList<>();
					for (Object n : ncsArr) {
						ncs.add(TypeUtils.asDouble(n));
					}
					nonconfLists.put(label, ncs);
				} catch (JsonException e) {
					LOGGER.debug("Failed converting stored NCS in the model to list of double",e);
					throw new IOException("Failed loading nonconformity scores");
				}
			}

		}
		LOGGER.debug("Loaded nonconformities from stream");
	}

}

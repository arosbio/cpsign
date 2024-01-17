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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.arosbio.ml.algorithms.MLAlgorithm;
import com.arosbio.ml.algorithms.impl.DefaultMLParameterSettings;
import com.arosbio.ml.cp.CPRegressionPrediction;
import com.arosbio.ml.cp.CPRegressionPrediction.PredictedInterval;
import com.arosbio.ml.cp.ConformalPredictor;
import com.arosbio.ml.cp.nonconf.NCM;
import com.arosbio.ml.cp.nonconf.calc.PValueCalculator;
import com.arosbio.ml.cp.nonconf.calc.SmoothedPValue;
import com.arosbio.ml.cp.nonconf.regression.NCMRegression;
import com.arosbio.ml.cp.nonconf.utils.NCMUtils;
import com.arosbio.ml.interfaces.RegressionPredictor;
import com.arosbio.ml.io.MetaFileUtils;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.arosbio.ml.sampling.TrainSplit;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.Jsoner;

public class ICPRegressor implements ICP, RegressionPredictor {

	private static final Logger LOGGER = LoggerFactory.getLogger(ICPRegressor.class);
	//	private static final String NCS_ESTIMATOR_PARAMETER = "PVALUE_CALCULATION";
	private static final String NCS_PATH = ".ncs";
	private static final String ICP_META_INFO_PATH = ".meta.json";

	private NCMRegression ncm;
	private List<Double> ncs;
	private PValueCalculator pValueCalculator = new SmoothedPValue();

	private int numTrainingObservations;

	//The minimum and maximum values observed in training. Used to truncate ranges.
	private double minObservation;
	private double maxObservation;

	/* 
	 * =================================================
	 * 			CONSTRUCTORS
	 * =================================================
	 */

	public ICPRegressor(){
	}

	public ICPRegressor(NCMRegression ncm) {
		super();
		this.ncm = ncm;
	}


	/* (non-Javadoc)
	 * @see com.arosbio.ml.cp.icp.ICPRegressor#clone()
	 */
	@Override
	public ICPRegressor clone() {
		ICPRegressor clone = new ICPRegressor();
		if (ncm != null)
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

	public MLAlgorithm getScoringAlgorithm(){
		if (ncm != null)
			return ncm.getModel();
		return null;
	}

	public void setPValueCalculator(PValueCalculator estimator) {
		this.pValueCalculator = estimator;
	}

	public PValueCalculator getPValueCalculator() {
		return pValueCalculator;
	}

	/**
	 * Get the Nonconformity scores (NCS)
	 * @return the list of nonconformity scores
	 */
	public List<Double> getNCS(){
		return ncs;
	}

	/**
	 * Set the nonconformity scores (alphas)
	 * @param alphas nonconformity scores
	 */
	public void setNCS(List<Double> alphas) {
		this.ncs = alphas;
	}

	public void setMinMaxObservations(double minObs, double maxObs) {
		this.minObservation = minObs;
		this.maxObservation = maxObs;
	}

	public void setSeed(long seed) {
		if (ncm != null) {
			ncm.getModel().setSeed(seed);
			if (ncm.requiresErrorModel())
				ncm.getErrorModel().setSeed(seed);
		}
		pValueCalculator.setRNGSeed(seed);
	}

	public Long getSeed() {
		if (ncm != null)
			return ncm.getModel().getSeed();
		return null;
	}

	@Override
	public boolean isTrained() {
		if (ncm != null)
			return ncm.isFitted() & ncs != null && ! ncs.isEmpty();
		return false;
	}

	@Override
	public NCMRegression getNCM(){
		return ncm;
	}

	public void setNCM(NCMRegression nonconfMeasure){
		this.ncm = nonconfMeasure;
	}

	public int getNumObservationsUsed() {
		if (isTrained())
			return numTrainingObservations;
		return 0;
	}

	@Override
	public boolean releaseResources() {
		boolean state = false;
		if (ncm != null && ncm.getModel() instanceof ResourceAllocator){
			((ResourceAllocator) ncm.getModel()).releaseResources();
			state = true;
		}
		if (ncm != null && ncm.getErrorModel() instanceof ResourceAllocator){
			((ResourceAllocator)ncm.getErrorModel()).releaseResources();
			state = true;
		}
		return state;
	}

	@Override
	public boolean holdsResources() {
		return isTrained() && 
			(ncm.getModel() instanceof ResourceAllocator) && 
			((ResourceAllocator) ncm.getModel()).holdsResources();
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String,Object> props = ncm.getProperties();
		props.put(PropertyNameSettings.ML_TYPE_KEY, "ICP Regression");
		props.put(PropertyNameSettings.IS_CLASSIFICATION_KEY, false);
		props.put(PropertyNameSettings.NUM_OBSERVATIONS_KEY, numTrainingObservations);
		props.put(PValueCalculator.PVALUE_CALCULATOR_NAME_KEY, pValueCalculator.getName());
		props.put(PValueCalculator.PVALUE_CALCULATOR_ID_KEY, pValueCalculator.getID());
		if (pValueCalculator.getRNGSeed() != null)
			props.put(PValueCalculator.PVALUE_CALCULATOR_SEED_KEY, pValueCalculator.getRNGSeed());
		return props;
	}

	public List<ConfigParameter> getConfigParameters() {
		List<ConfigParameter> params = new ArrayList<>();
		params.addAll(ncm.getConfigParameters());

		// ncm estimator
		params.add(new ImplementationConfig.Builder<>(ConformalPredictor.CONFIG_PVALUE_CALC_PARAM_NAMES, PValueCalculator.class).defaultValue(new SmoothedPValue()).build());
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
				LOGGER.debug("Got invalid config argument: {}={}",kv.getKey(),kv.getValue());
				throw Configurable.getInvalidArgsExcept(kv.getKey(), kv.getValue()); 
			}
		}
		// pass on to underlying ncm
		ncm.setConfigParameters(params);
	}

	/* 
	 * =================================================
	 * 			TRAIN
	 * =================================================
	 */
	@Override
	public void train(TrainSplit icpdataset) 
			throws IllegalArgumentException, IllegalStateException {
		if (icpdataset==null)
			throw new IllegalArgumentException("Training data cannot be null");
		if (ncm == null)
			throw new IllegalStateException("No NCM set in the ICP");
		TrainingsetValidator.getInstance().validateRegression(icpdataset);

		LOGGER.debug("Training ICP regression model");

		// Train the NCM
		ncm.trainNCM(icpdataset.getProperTrainingSet());
		LOGGER.debug("Finished training the NCM using {} records", icpdataset.getProperTrainingSet().size());

		//Calculate ncs
		ncs = new ArrayList<>(icpdataset.getCalibrationSet().size());
		try{
			for (DataRecord calibRec: icpdataset.getCalibrationSet()){
				ncs.add(ncm.calcNCS(calibRec));
			}
		} catch (IllegalStateException e){
			LOGGER.debug("failed calculating the ncs",e);
			throw new IllegalArgumentException(e);
		}

		Collections.sort(ncs);
		LOGGER.debug("Calculated all ncs scores for calibration points ({} records)", ncs.size());

		// Get the capping-values
		minObservation=icpdataset.getObservedLabelSpace().lowerEndpoint();
		maxObservation=icpdataset.getObservedLabelSpace().upperEndpoint();

		LOGGER.trace("MinObservation: {} ; MaxObservation: {}",minObservation, maxObservation);
		numTrainingObservations = icpdataset.getTotalNumTrainingRecords();
		LOGGER.debug("ICP training finished");

	}


	/* 
	 * =================================================
	 * 			PREDICTION
	 * =================================================
	 */

	public double predictMidpoint(FeatureVector instance) throws IllegalStateException {
		return ncm.predictMidpoint(instance);
	}

	/**
	 * Predict a new instance, without specifying a confidence, only yielding a midpoint prediction
	 * @param instance a test instance to predict
	 * @return a prediction
	 */
	public CPRegressionPrediction predict(FeatureVector instance) throws IllegalStateException {
		return predict(instance, null);
	}

	/**
	 * Predict a new instance given a list of confidences
	 * @param instance a test instance to predict
	 * @param confidences a list of confidences, may be empty
	 * @return a prediction
	 */
	public CPRegressionPrediction predict(FeatureVector instance, Collection<Double> confidences) 
			throws IllegalStateException {
		if (! isTrained())
			throw new IllegalStateException("Model not trained");
		if (instance == null)
			throw new IllegalArgumentException("example to predict was null");
		if (pValueCalculator == null)
			throw new IllegalStateException("No NCS estimator set");
		if (!pValueCalculator.isReady()) {
			pValueCalculator.build(ncs);
			LOGGER.debug("Fitted NCS estimator");
		}

		double y_hat = ncm.predictMidpoint(instance);
		double intervalScaling = ncm.calcIntervalScaling(instance);
		if (intervalScaling < 0) {
			LOGGER.debug("Interval scaling was found to be negative, something is incorrect, was: {}", intervalScaling);
			throw new IllegalArgumentException("Error when predicting, got a negative interval-scaling for prediction - something in the setup was incorrect");
		}


		CPRegressionPrediction prediction = new CPRegressionPrediction(y_hat, intervalScaling, minObservation, maxObservation);

		// If no intervals should be predicted
		if (confidences == null || confidences.isEmpty()) {
			return prediction;
		}

		// get the interval sizes
		calculatePredictionIntervals(prediction, intervalScaling, confidences);

		return prediction;
	}

	private void calculatePredictionIntervals(CPRegressionPrediction prediction, 
			double scaling, Collection<Double> confidences) {

		Map<Double,PredictedInterval> list = new HashMap<>();

		if (confidences==null) 
			return;

		for (double conf : confidences){
			double ncs = pValueCalculator.getNCScore(conf);
			if (ncs < 0) {
				LOGGER.debug("Failing prediction due to encountering a negative NCS ({}), using pvalue-calc: {}",ncs, pValueCalculator);
				throw new IllegalStateException("p-value calculator of type " + pValueCalculator.getName() + " returned a negative NCS, perhaps it is not suitable for this dataset");
			}

			list.put(conf,prediction.new PredictedInterval(conf, scaling*ncs));
		}

		prediction.setPredictedIntervals(list);

	}

	public List<SparseFeature> calculateGradient(FeatureVector example)
			throws IllegalStateException {
		return calculateGradient(example, DefaultMLParameterSettings.DEFAULT_STEPSIZE);
	}

	public List<SparseFeature> calculateGradient(FeatureVector instance, double stepsize)
			throws IllegalStateException {
		//The gradient to return, same size as the example to predict
		List<SparseFeature> gradient = new ArrayList<>(instance.getNumExplicitFeatures());

		//First do a normal prediction
		CPRegressionPrediction result = predict(instance);

		//Get prediction midpoint
		double midpoint = result.getY_hat();

		for (Feature f : instance) {

			Feature oldInstance = f.clone();

			// Set the new updated value
			instance.withFeature(f.getIndex(), f.getValue()+stepsize);

			//Predict modified feature vector
			double mpHat = predictMidpoint(instance);
			if (LOGGER.isTraceEnabled())
				LOGGER.trace("index: {}, mpHat: {}",f.getIndex(), mpHat);

			double derivate = (mpHat-midpoint)/stepsize;
			gradient.add(new SparseFeatureImpl(oldInstance.getIndex(), derivate));

			//Set the value in example feature back
			instance.withFeature(f.getIndex(), oldInstance.getValue());

		}
		if (LOGGER.isTraceEnabled())
			LOGGER.trace("ICPRegressor gradient: {}", gradient);

		return gradient;
	}

	/* 
	 * =================================================
	 * 			SAVE / LOAD
	 * =================================================
	 */

	@Override
	public void saveToDataSink(DataSink sink, String modelbasePath, EncryptionSpecification spec)
			throws IOException, InvalidKeyException, IllegalStateException {
		if (!isTrained())
			throw new IllegalStateException("Model is not trained");

		// Save the NCM to the sink - i.e. all the models
		ncm.saveToDataSink(sink, modelbasePath+NCMUtils.NCM_BASE_PATH, spec);

		// Save the NCS to sink (ncs)
		if (spec != null){
			try(
					OutputStream alphasStream = sink.getOutputStream(modelbasePath+NCS_PATH);
					OutputStream encryptedStream = spec.encryptStream(alphasStream);){
				writeNCSToStream(encryptedStream);
			}
		} else {
			try(OutputStream alphasStream = sink.getOutputStream(modelbasePath+NCS_PATH);){
				writeNCSToStream(alphasStream);
			}
		}
		LOGGER.debug("Saved ncs to Sink in ICPRegressor, location={}",modelbasePath+NCS_PATH);

		// Save the ICP meta-info (for loading the correct NCM etc)
		try (OutputStream metaStream = sink.getOutputStream(modelbasePath + ICP_META_INFO_PATH)){
			Map<String, Object> props = getProperties();
			props.put(PropertyNameSettings.IS_ENCRYPTED, spec != null);
			MetaFileUtils.writePropertiesToStream(metaStream, props);
			LOGGER.debug("Saved icp info to stream");
		} catch (Exception e) {
			LOGGER.debug("Failed saving meta info to stream",e);
		}
	}




	@Override
	public void loadFromDataSource(DataSource src, String path, EncryptionSpecification encryptSpec)
			throws IOException, InvalidKeyException {

		if (! src.hasEntry(path+ICP_META_INFO_PATH) ||
				! src.hasEntry(path+NCS_PATH)) {
			throw new IOException("No ICP model in source under location=" + path);
		}

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
		if (!(boolean)isEncr) {
			encryptSpec = null; // Remove it
		}

		if ((boolean) isEncr && encryptSpec == null) {
			LOGGER.debug("Model encrypted but no encryption specification sent to decrypt it");
			throw new InvalidKeyException("Model encrypted");
		}

		// Instantiate the NCM and let it load the models 
		Object ncmID = props.get(PropertyNameSettings.NCM_ID);
		if (ncmID != null) {
			NCM theNCM = FuzzyServiceLoader.load(NCM.class, ncmID.toString());
			if (theNCM instanceof NCMRegression) {
				ncm = (NCMRegression) theNCM;
				ncm.loadFromDataSource(src, path + NCMUtils.NCM_BASE_PATH, encryptSpec);
			} else {
				LOGGER.debug("Failed loading NCM from serialized model, was of incorrect type: {}",ncm.getName());
				throw new IOException("Invalid NCM type in serialized model");
			}
		}
		else {
			throw new IOException("No NCM name saved in the ICP meta-info: cannot load it properly");
		}

		// Load the ncs
		try (InputStream ncsStream = src.getInputStream(path+NCS_PATH);){
			if(encryptSpec != null)
				loadNCSFromStream(encryptSpec.decryptStream(ncsStream));
			else
				loadNCSFromStream(ncsStream);
			LOGGER.debug("loaded ncs");
		} catch(IOException e){
			LOGGER.debug("Could not load ncs from stream, this might not be a regression problem?");
			throw new IOException("Could not load ncs from stream, this might not be a regression problem?");
		}

		LOGGER.debug("Finished loading ICP");

	}

	private void writeNCSToStream(OutputStream stream) throws IOException {
		try(
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
				){
			writer.write(Jsoner.serialize(ncs));
			writer.write("\n"+minObservation);
			writer.write("\n"+maxObservation);
			writer.newLine();
		}
		LOGGER.debug("Wrote NCS to stream");
	}

	private void loadNCSFromStream(InputStream stream) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream));){
			String alphas_raw = reader.readLine();
			String min = reader.readLine();
			String max = reader.readLine();

			try {
				JsonArray ncsAsObject = (JsonArray) Jsoner.deserialize(alphas_raw);
				// Convert all values into double!
				ncs = new ArrayList<>();
				for (Object n : ncsAsObject) {
					ncs.add(TypeUtils.asDouble(n));
				}
				minObservation=Double.parseDouble(min);
				maxObservation=Double.parseDouble(max);
			} catch (JsonException e) {
				LOGGER.debug("Failed loading NCS from model file",e);
				throw new IOException("Failed loading nonconformity scores from model");
			}
		}

		LOGGER.debug("Loaded NCS from stream");
	}

}

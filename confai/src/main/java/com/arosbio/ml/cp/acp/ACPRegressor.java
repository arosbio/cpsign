/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp.acp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.GlobalConfig.Defaults.PredictorType;
import com.arosbio.commons.MathUtils;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.ImplementationConfig;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.SparseFeature;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.DataIOUtils;
import com.arosbio.io.DataSink;
import com.arosbio.io.DataSource;
import com.arosbio.ml.PredictorBase;
import com.arosbio.ml.algorithms.impl.DefaultMLParameterSettings;
import com.arosbio.ml.cp.CPRegressionPrediction;
import com.arosbio.ml.cp.CPRegressionPrediction.PredictedInterval;
import com.arosbio.ml.cp.ConformalRegressor;
import com.arosbio.ml.cp.icp.ICPRegressor;
import com.arosbio.ml.cp.nonconf.calc.PValueCalculator;
import com.arosbio.ml.cp.nonconf.regression.NCMRegression;
import com.arosbio.ml.io.MetaFileUtils;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.cp.regression.MedianPredictionIntervalWidth;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.sampling.SamplingStrategy;
import com.arosbio.ml.sampling.SamplingStrategyUtils;
import com.arosbio.ml.sampling.TrainSplit;
import com.arosbio.ml.sampling.TrainSplitGenerator;

public final class ACPRegressor extends PredictorBase implements ACP, ConformalRegressor {

	public static final String PREDICTOR_TYPE = "ACP Regression";
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ACPRegressor.class);
	private static final String ACP_DIRECTORY_NAME = "acp";
	private static final String ACP_META_FILE_NAME = "meta.json";
	private static final String ICP_BASE_FILE_NAME = "model";

	private Map<Integer,ICPRegressor> predictors = new HashMap<>();
	private ICPRegressor icpImplementation;
	private SamplingStrategy strategy;
	private AggregationType aggregation = AggregationType.MEDIAN;

	/* 
	 * =================================================
	 * 			INTERFACES
	 * =================================================
	 */

	/* 
	 * =================================================
	 * 			CONSTRUCTORS
	 * =================================================
	 */

	public ACPRegressor() {
		super();
	}
	
	public ACPRegressor(NCMRegression ncm, SamplingStrategy strategy) {
		super();
		this.icpImplementation = new ICPRegressor(ncm);
		setStrategy(strategy);
	}

	public ACPRegressor(ICPRegressor icp, SamplingStrategy strategy) {
		super();
		this.icpImplementation = icp;
		this.strategy = strategy;
	}

	public ACPRegressor(ICPRegressor icp, SamplingStrategy strategy, long seed) {
		this(icp, strategy);
		this.seed = seed;
	}

	public ACPRegressor clone(){
		ACPRegressor clone = new ACPRegressor();
		if (icpImplementation != null)
			clone.icpImplementation = icpImplementation.clone();
		if (strategy != null)
			clone.strategy = this.strategy.clone();
		clone.seed=seed;

		// Copy all ICPs 
		if (predictors != null)
			for (Integer i : predictors.keySet())
				clone.predictors.put(i, predictors.get(i).clone());

		return clone;
	}
	
	public String toString() {
		return "ACP regression with " + strategy.toString();
	}


	/* 
	 * =================================================
	 * 			GETTERS / SETTERS
	 * =================================================
	 */

	public void setAggregation(AggregationType type){
		this.aggregation = type;
	}
	
    public AggregationType getAggregation(){
		return aggregation;
	}

	@Override
	public SingleValuedMetric getDefaultOptimizationMetric() {
		return new MedianPredictionIntervalWidth();
	}

	public ICPRegressor getICPImplementation() {
		return icpImplementation;
	}

	public void setICPImplementation(ICPRegressor icp) {
		this.icpImplementation = icp;
	}

	public Map<Integer,ICPRegressor> getPredictors(){
		return predictors;
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		List<ConfigParameter> params = new ArrayList<>();
		params.add(new ImplementationConfig.Builder<>(Arrays.asList(CONFIG_SAMPLING_STRATEGY_PARAM_NAME), SamplingStrategy.class).build());
		if (strategy != null)
			params.addAll(strategy.getConfigParameters());
		if (icpImplementation != null)
			params.addAll(icpImplementation.getConfigParameters());
		return params;
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalArgumentException {
		// SAMPLING STRATEGY
		if (params.containsKey(CONFIG_SAMPLING_STRATEGY_PARAM_NAME)) {
			if (params.get(CONFIG_SAMPLING_STRATEGY_PARAM_NAME) instanceof SamplingStrategy) {
				this.strategy = (SamplingStrategy) params.get(CONFIG_SAMPLING_STRATEGY_PARAM_NAME);
			} else {
				throw new IllegalArgumentException("Parameter " + CONFIG_SAMPLING_STRATEGY_PARAM_NAME + " cannot take value: " + params.get(CONFIG_SAMPLING_STRATEGY_PARAM_NAME));
			}
		}

		// pass on to ICP
		icpImplementation.setConfigParameters(params);
	}

	/**
	 * Add an ICP to an Aggregated Conformal Predictor, using random sampling strategy
	 * @param icp The ICP to add
	 * @throws IllegalAccessException if sampling strategy is <b>folded</b> - then must specify which fold the ICP belongs to
	 */
	public void addICP(ICPRegressor icp) throws IllegalAccessException {
		if (strategy.isFolded())
			throw new IllegalAccessException("For folded sampling strategy, a fold must be specified");
		if (predictors==null)
			predictors = new HashMap<>();

		// add to first empty spot
		for(int i=0; i<strategy.getNumSamples();i++) {
			if (!predictors.containsKey((Integer) i)){
				// empty spot - add it!
				predictors.put(i, icp);
				LOGGER.debug("Added ICP in index={}",i);
				return;
			}
		}
		LOGGER.debug("ACP already 'full' - updating sampling strategy to add more icp-predictors");
		// if we're get here - update the strategy and add the predictor to next index
		if (strategy instanceof RandomSampling)
			((RandomSampling) strategy).withNumSamples(strategy.getNumSamples());
		else {
			LOGGER.debug("Attempted to add an ICP to a non RandomSampling aggregation - not supported");
			throw new IllegalAccessException("Invalid access - cannot add an ICP when not using RandomSampling");
		}
		predictors.put(strategy.getNumSamples(), icp);
		LOGGER.debug("Added ICP in index={}",strategy.getNumSamples());
	}

	/**
	 * Add an ICP to an ACP
	 * @param icp The ICP to add
	 * @param index The index the ICP belongs to
	 * @throws IllegalArgumentException If <code>index</code> is outside the number of folds set in the strategy of the ACP
	 */
	public void addICP(ICPRegressor icp, int index) throws IllegalArgumentException {
		if (predictors==null)
			predictors = new HashMap<>();
		LOGGER.debug("Attempting to add new ICP to index={}",index);

		if (index >= strategy.getNumSamples() || index < 0)
			throw new IllegalArgumentException("index must be within range [0,"+(strategy.getNumSamples()-1)+"]");
		predictors.put(index, icp);
		LOGGER.debug("added ICP");
	}

	@Override
	public int getNumTrainedPredictors(){
		if (predictors!=null)
			return predictors.size();
		return 0;
	}

	public boolean holdsResources(){
		return ! predictors.isEmpty();
	}

	public boolean releaseResources(){
		if (predictors == null || predictors.isEmpty())
			return false;

		// Release all ICPs memory
		boolean state = true;
		for (ICPRegressor icp : predictors.values()){
			state = MathUtils.keepFalse(state, icp.releaseResources());
		}
		// Drop references
		predictors.clear();
		return state;
	}

	@Override
	public boolean isTrained() {
		return predictors!=null && predictors.size() == strategy.getNumSamples();
	}

	@Override
	public boolean isPartiallyTrained() {
		return predictors!=null && !predictors.isEmpty();
	}

	@Override
	public SamplingStrategy getStrategy() {
		return strategy;
	}

	public void setStrategy(SamplingStrategy strategy) {
		if (strategy.isStratified())
			throw new IllegalArgumentException("Stratified sampling not allowed for regression");
		this.strategy = strategy;
	}

	@Override
	public String getPredictorType() {
		return PREDICTOR_TYPE;
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String,Object> params = new HashMap<>();
		params.putAll(strategy.getProperties());
		if (icpImplementation != null && icpImplementation.getNCM() != null)
			params.putAll(icpImplementation.getNCM().getProperties());
		params.put(PropertyNameSettings.ML_SEED_VALUE_KEY, seed);
		params.put(PropertyNameSettings.ML_TYPE_KEY, PredictorType.ACP_REGRESSION.getId());
		params.put(PropertyNameSettings.ML_TYPE_NAME_KEY, PredictorType.ACP_REGRESSION.getName());
		params.put(PropertyNameSettings.IS_CLASSIFICATION_KEY, false);
		return params;
	}

	@Override
	public int getNumObservationsUsed() {
		if (predictors.isEmpty())
			return 0;
		else
			return predictors.values().iterator().next().getNumObservationsUsed();
	}

	/* 
	 * =================================================
	 * 			TRAIN
	 * =================================================
	 */
	@Override
	public void train(Dataset data) 
			throws IllegalArgumentException {
		Iterator<TrainSplit> splits = strategy.getIterator(data, seed);

		predictors=new HashMap<>();

		//Train the models
		int i=0, nrModels=strategy.getNumSamples();
		LOGGER.debug("Training ACP Predictor with {} models", nrModels);

		while (splits.hasNext()){
			ICPRegressor icp = icpImplementation.clone();
			TrainSplit nextDataset = splits.next();
			icp.train(nextDataset);
			predictors.put(i, icp);
			LOGGER.debug(" - Trained model {}/{}",(i+1),nrModels);
			i++;
		}

	}

	/**
	 * Train only a specific ICP model (at a given index)
	 * @param data The {@link com.arosbio.data.Dataset Dataset} that should be trained
	 * @param index the index, counting starts at 0!! [0,nrFolds-1]
	 * @throws IllegalArgumentException Invalid index argument
	 */
	public void train(Dataset data, int index) 
			throws IllegalArgumentException {

		if (predictors == null){
			predictors = new HashMap<>();
		}
		SamplingStrategyUtils.validateTrainSplitIndex(strategy, index);

		TrainSplitGenerator generator = strategy.getIterator(data,seed);
		LOGGER.debug("Set up new splits-iterator for training");

		ICPRegressor icp = icpImplementation.clone();
		TrainSplit split=null;
		try {
			split = generator.get(index);
		} catch (NoSuchElementException e) {
			LOGGER.debug("Tried to get a non-existing index split",e);
			throw new IllegalArgumentException("Cannot train index " + index + ", only allowed indexes are [0,"+(strategy.getNumSamples()-1)+"]");
		}
		icp.train(split);
		predictors.put(index, icp);
		LOGGER.debug(" - Trained model {}/{}",(index+1),getStrategy().getNumSamples());

	}

	/* 
	 * =================================================
	 * 			PREDICT
	 * =================================================
	 */

	private void assertIsTrained() throws IllegalStateException {
		if (! isTrained())
			throw new IllegalStateException("Predictor not trained!");
	}

	public double predictMidpoint(FeatureVector instance) throws IllegalStateException {
		assertIsTrained();
		List<Double> midPs = new ArrayList<>();
		for (ICPRegressor icp : predictors.values()){
			midPs.add(icp.predictMidpoint(instance));
		}
		if (aggregation == AggregationType.MEDIAN){
			return MathUtils.median(midPs);
		} else if (aggregation == AggregationType.MEAN){
			return MathUtils.mean(midPs);
		} else {
			throw new IllegalStateException("Invalid aggregation type");
		}
	}

	/**
	 * Predict the confidence required for yielding a given distance 
	 * @param example to predict
	 * @param width prediction interval width to use
	 * @return the {@link com.arosbio.ml.cp.CPRegressionPrediction CPRegressionPrediction} with the result
	 * @throws IllegalStateException If the predictor was not trained
	 */
	public CPRegressionPrediction predictConfidence(FeatureVector example, double width) 
			throws IllegalStateException {
		return predictConfidence(example, Arrays.asList(width));
	}

	/**
	 * Predict the confidences required for yielding a set of given prediction interval widths 
	 * @param example to predict
	 * @param widths prediction interval widths to use
	 * @return the {@link com.arosbio.ml.cp.CPRegressionPrediction CPRegressionPrediction} with results
	 * @throws IllegalStateException If the predictor was not trained
	 */
	public CPRegressionPrediction predictConfidence(FeatureVector example, Collection<Double> widths)
			throws IllegalStateException {
		// Ensure that we have models
		assertIsTrained();

		// Start with a prediction to get midpoint
		List<Double> Y_hats = new ArrayList<>();
		List<Double> intervalScalings = new ArrayList<>();
		Map<Double,List<Double>> width2conf = new HashMap<>();

		// Min and Max observations
		double minObs = Double.MAX_VALUE, maxObs = -Double.MAX_VALUE;

		// loop over all models to predict MP without confidence
		for (ICPRegressor icp : predictors.values()) {
			CPRegressionPrediction result = icp.predict(example);
			Y_hats.add(result.getY_hat());
			intervalScalings.add(result.getIntervalScaling());

			// Update min and max values
			minObs = Math.min(result.getMinObs(), minObs);
			maxObs = Math.max(result.getMaxObs(), maxObs);


			for (double width : widths) {
				if (!width2conf.containsKey(width)) {
					width2conf.put(width, new ArrayList<>());
				}
				PValueCalculator estimator = icp.getPValueCalculator();
				double ncs2searchFor = width/(2*result.getIntervalScaling()); // divide by 2 to get the "half width" or old "distance" 
				double conf = 1 - estimator.getPvalue(ncs2searchFor); // 1 - pValue = conf!
				width2conf.get(width).add(conf);
			}
		}

		// Midpoint: take median for Yhat
		double y_hat = ACP.aggregate(aggregation, Y_hats);
		double scaling = ACP.aggregate(aggregation, intervalScalings);
		CPRegressionPrediction result = new CPRegressionPrediction(y_hat, scaling, minObs, maxObs);
		Map<Double,PredictedInterval> intervals = new HashMap<>();

		// calculate median confidence for ICPs (and corresponding widths)
		for (double width : widths) {
			double conf = MathUtils.median(width2conf.get(width));
			PredictedInterval interval = result.new PredictedInterval(conf, width/2);
			intervals.put(width, interval);
		}

		result.setWidthBasedIntervals(intervals);

		return result;
	}

	/**
	 * Predict an List of {@link com.arosbio.data.SparseFeature SparseFeature} with a single confidence
	 * 
	 * @param example feature vector to predict
	 * @param confidence desired confidence
	 * @return a {@link com.arosbio.ml.cp.CPRegressionPrediction CPRegressionPrediction} instance with the result
	 * @throws IllegalStateException Model not trained
	 */
	public CPRegressionPrediction predict(final FeatureVector example, double confidence) 
			throws IllegalStateException {
		return predict(example, Arrays.asList(confidence));
	}

	/**
	 * Predict an List of {@link com.arosbio.data.SparseFeature SparseFeature} with a list of confidences
	 * @param example feature vector to predict
	 * @param confidences a list of desired confidence(s)
	 * @return a {@link com.arosbio.ml.cp.CPRegressionPrediction CPRegressionPrediction} instance with the result(s)
	 * @throws IllegalStateException Model not trained
	 */
	public CPRegressionPrediction predict(final FeatureVector example, Collection<Double> confidences)
			throws IllegalStateException {

		// Ensure that we have models
		assertIsTrained();

		// for saving results
		List<Double> Y_hats = new ArrayList<>(), scalings = new ArrayList<>();
		Map<Double,List<Double>> intervalHalfWidths = new HashMap<>();

		// Min and Max observations
		double minObs = Double.MAX_VALUE, maxObs = -Double.MAX_VALUE;

		// make predictions using all ICPs
		for (ICPRegressor icp : predictors.values()) {
			CPRegressionPrediction result = icp.predict(example, confidences);
			// All y-hats will be the same for each model
			Y_hats.add(result.getY_hat());
			scalings.add(result.getIntervalScaling());

			// Update min and max values
			minObs = Math.min(result.getMinObs(), minObs);
			maxObs = Math.max(result.getMaxObs(), maxObs);

			// Map of confidence to distance
			for (double conf: confidences) {
				if (!intervalHalfWidths.containsKey(conf)) {
					intervalHalfWidths.put(conf, new ArrayList<>());
				}
				intervalHalfWidths.get(conf).add(result.getInterval(conf).getIntervalHalfWidth());
			}

		}

		// KeepMedianLabel for MidPoint
		double y_hat = ACP.aggregate(aggregation, Y_hats);
		double scaling = ACP.aggregate(aggregation, scalings);
		CPRegressionPrediction predictionResult = new CPRegressionPrediction(y_hat, scaling, minObs, maxObs);

		Map<Double,PredictedInterval> intervals = new LinkedHashMap<>();

		// calculate medians and range
		for (double conf : confidences) {
			// Get aggregation over all models for this confidence
			double medianHalfWidth = ACP.aggregate(aggregation, intervalHalfWidths.get(conf));
			PredictedInterval interval = predictionResult.new PredictedInterval(conf, medianHalfWidth);
			intervals.put(conf, interval);
		}

		predictionResult.setPredictedIntervals(intervals);

		return predictionResult;
	}

	@Override
	public List<SparseFeature> calculateGradient(final FeatureVector example)
			throws IllegalStateException {
		return calculateGradient(example, DefaultMLParameterSettings.DEFAULT_STEPSIZE);
	}

	@Override
	public List<SparseFeature> calculateGradient(final FeatureVector example, double stepsize)
			throws IllegalStateException {
		assertIsTrained();

		LOGGER.debug("calculating gradient in ACP using stepsize={}",stepsize);

		List<List<SparseFeature>> gradients = new ArrayList<>();

		for (ICPRegressor icp : predictors.values()) {
			gradients.add(icp.calculateGradient(example, stepsize));
		}
		return DataUtils.averageIdenticalIndices(gradients);
	}

	/* 
	 * =================================================
	 * 			SAVE / LOAD
	 * =================================================
	 */

	@Override
	public void saveToDataSink(DataSink sink, String basePath, EncryptionSpecification spec) 
			throws IOException, InvalidKeyException, IllegalStateException {
		// create the directory
		String acpDir = DataIOUtils.createBaseDirectory(sink, basePath, ACP_DIRECTORY_NAME+"/");
		
		// write meta.json
		Map<String, Object> params = getProperties();
		try (OutputStream metaStream = sink.getOutputStream(acpDir+ACP_META_FILE_NAME)){
			MetaFileUtils.writePropertiesToStream(metaStream, params);
		}
		LOGGER.debug("Written ACP Properties to jar: {}", params);

		// for each ICP - write it
		int i=0;
		for (Entry<Integer, ICPRegressor> icp: predictors.entrySet()){
			icp.getValue().saveToDataSink(sink, acpDir+ICP_BASE_FILE_NAME+'.'+icp.getKey(), spec);
			i++;
		}
		LOGGER.debug("Written {} ICP models to jar",i);

	}

	@Override
	public void loadFromDataSource(DataSource source, String basePath, EncryptionSpecification encryptSpec) 
			throws InvalidKeyException, IOException {
		String acpDir = DataIOUtils.locateBasePath(source, basePath, ACP_DIRECTORY_NAME+'/'); 
		LOGGER.debug("acp directory={}",acpDir);

		if (icpImplementation == null) {
			LOGGER.debug("No ICPImplementation set - falling back to the default ICPRegressor implementation");
			icpImplementation = new ICPRegressor();
		}

		// Load meta.params
		try(
				InputStream metaDataStream = source.getInputStream(acpDir+ACP_META_FILE_NAME);
				){

			Map<String,Object> properties = MetaFileUtils.readPropertiesFromStream(metaDataStream);
			LOGGER.debug("acp properties from meta-file: {}",properties);

			// Sampling strategy
			strategy = SamplingStrategyUtils.fromProperties(properties);
			seed = TypeUtils.asLong(properties.get(PropertyNameSettings.ML_SEED_VALUE_KEY));

		} catch (IOException e){
			LOGGER.debug("Could not read the acp meta-file",e);
			throw new IOException(e);
		}
		LOGGER.debug("Loaded ACP meta-file");

		// Load the ICPs
		int nrModels = strategy.getNumSamples();
		predictors = new HashMap<>(nrModels);
		for (int i=0; i<nrModels; i++){
			ICPRegressor icp = icpImplementation.clone();
			try {
				icp.loadFromDataSource(source, acpDir+ICP_BASE_FILE_NAME+'.'+i, encryptSpec);
				predictors.put(i, icp);
				LOGGER.debug("ACP Regression loaded model {}/{}", (i+1),nrModels);
			} catch (IOException e) {
				LOGGER.debug("Could not load model {} from src",i);
			} catch(InvalidKeyException e) {
				LOGGER.debug("Failing ACPregression loading, ICP encrypted",e);
				throw new InvalidKeyException(e.getMessage());
			} catch (Exception e){ 
				LOGGER.debug("Could not load model {} from src",i,e);
			}
		}

		// get the ICP impl from the first ICP
		if (predictors.isEmpty()) {
			LOGGER.debug("No models successfully loaded - failing");
			throw new IOException("No ICP models could be loaded from file");
		}
		icpImplementation = predictors.values().iterator().next().clone();

		LOGGER.debug("Finished loading ACPRegressor");
	}

	public void loadFromDataSource(DataSource source, EncryptionSpecification encryptSpec) 
			throws IOException, InvalidKeyException, IllegalArgumentException {
		loadFromDataSource(source, null, encryptSpec);
	}
}

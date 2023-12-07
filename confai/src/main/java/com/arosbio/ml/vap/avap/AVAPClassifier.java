/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.vap.avap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
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
import com.arosbio.ml.ClassificationUtils;
import com.arosbio.ml.PredictorBase;
import com.arosbio.ml.algorithms.ScoringClassifier;
import com.arosbio.ml.algorithms.impl.DefaultMLParameterSettings;
import com.arosbio.ml.algorithms.svm.SVC;
import com.arosbio.ml.interfaces.ClassificationPredictor;
import com.arosbio.ml.io.MetaFileUtils;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.classification.LogLoss;
import com.arosbio.ml.sampling.SamplingStrategy;
import com.arosbio.ml.sampling.SamplingStrategyUtils;
import com.arosbio.ml.sampling.TrainSplit;
import com.arosbio.ml.sampling.TrainSplitGenerator;
import com.arosbio.ml.vap.ivap.IVAPClassifier;

public final class AVAPClassifier extends PredictorBase implements AVAP, ClassificationPredictor {

	private static final Logger LOGGER = LoggerFactory.getLogger(AVAPClassifier.class);
	private static final String CVAP_DIRECTORY_NAME = "cvap";
	private static final String CVAP_META_FILE_NAME = "meta.json";
	private static final String IVAP_BASE_FILE_NAME = "model";
	private final static String FEATURE_SCALING_FILE_NAME = "model.scale";
	public final static String PREDICTOR_TYPE = "CVAP Classification";

	private Map<Integer,IVAPClassifier> predictors = new HashMap<>();
	private ScoringClassifier scoringAlgorithm;
	private SamplingStrategy strategy;

	/* 
	 * =================================================
	 * 			CONSTRUCTORS
	 * =================================================
	 */

	public AVAPClassifier(){
		super();
	}

	public AVAPClassifier(ScoringClassifier mlImpl, SamplingStrategy strategy){
		super();
		this.scoringAlgorithm = mlImpl;
		this.strategy = strategy;
	}

	public AVAPClassifier(ScoringClassifier mlImpl, SamplingStrategy strategy, long seed){
		this(mlImpl, strategy);
		this.seed = seed;
	}

	@Override
	public AVAPClassifier clone(){
		AVAPClassifier clone = new AVAPClassifier();
		clone.strategy = strategy.clone();
		clone.scoringAlgorithm = this.scoringAlgorithm.clone();
		clone.seed=seed;

		// Copy all IVAPs 
		if (predictors != null)
			for (Integer i : predictors.keySet())
				clone.predictors.put(i, predictors.get(i).clone());

		return clone;		
	}

	/* 
	 * =================================================
	 * 			GETTERS AND SETTERS
	 * =================================================
	 */

	@Override
	public SingleValuedMetric getDefaultOptimizationMetric() {
		return new LogLoss();
	}

	@Override
	public String getPredictorType() {
		return PREDICTOR_TYPE;
	}

	/**
	 * Returns the number of trained models, which might be <code>nrModels &ne; SamplingStrategy.nrModels</code>
	 * @return the number of trained models 
	 */
	public int getNumTrainedPredictors(){
		if (predictors!=null)
			return predictors.size();
		return 0;
	}

	public Set<Integer> getLabels(){
		if (predictors==null || predictors.isEmpty())
			return new HashSet<>();
		return predictors.values().iterator().next().getLabels();
	}


	public int getNumClasses() {
		return isTrained() ? 2 : -1;
	}

	@Override
	public SamplingStrategy getStrategy(){
		return strategy;
	}
	
	public ScoringClassifier getScoringAlgorithm() {
		return scoringAlgorithm;
	}

	@Override
	public boolean isPartiallyTrained() {
		return predictors!=null && !predictors.isEmpty();
	}

	@Override
	public boolean isTrained() {
		return predictors!=null && predictors.size() == strategy.getNumSamples();
	}

	public boolean holdsResources(){
		return ! predictors.isEmpty();
	}

	public boolean releaseResources(){
		if (predictors == null || predictors.isEmpty())
			return false;

		// Release all ICPs memory
		boolean state = true;
		for (IVAPClassifier icp : predictors.values()){
			state = MathUtils.keepFalse(state, icp.releaseResources());
		}
		// Drop references
		predictors.clear();
		return state;
	}

	/**
	 * Get which models have been trained (used for parallel training)
	 * @return The set of models that have been trained. Numbers can be in the range [1, total-number-of-models]
	 */
	public Set<Integer> getModelsTrained(){
		return new HashSet<>(predictors.keySet());
	}

	public Map<Integer, IVAPClassifier> getModels(){
		return predictors;
	}


	@Override
	public Map<String,Object> getProperties() {
		Map<String,Object> params = new HashMap<>();
		params.putAll(strategy.getProperties());
		params.put(PropertyNameSettings.ML_SEED_VALUE_KEY, seed);
		params.put(PropertyNameSettings.PREDICTOR_ML_ALG_INFO_KEY, scoringAlgorithm.getProperties());
		params.put(PropertyNameSettings.ML_TYPE_KEY, PredictorType.VAP_CLASSIFICATION.getId());
		params.put(PropertyNameSettings.ML_TYPE_NAME_KEY, PredictorType.VAP_CLASSIFICATION.getName());
		params.put(PropertyNameSettings.IS_CLASSIFICATION_KEY, true);
		return params;
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		List<ConfigParameter> params = new ArrayList<>();
		params.add(new ImplementationConfig.Builder<>(Arrays.asList(CONFIG_SAMPLING_STRATEGY_PARAM_NAME), SamplingStrategy.class).build());
		if (strategy != null)
			params.addAll(strategy.getConfigParameters());
		if (scoringAlgorithm != null)
			params.addAll(scoringAlgorithm.getConfigParameters());
		return params;
	}

	@Override
	public void setConfigParameters(Map<String,Object> params) throws IllegalArgumentException {
		// SAMPLING STRATEGY
		if (params.containsKey(CONFIG_SAMPLING_STRATEGY_PARAM_NAME)) {
			if (params.get(CONFIG_SAMPLING_STRATEGY_PARAM_NAME) instanceof SamplingStrategy) {
				this.strategy = (SamplingStrategy) params.get(CONFIG_SAMPLING_STRATEGY_PARAM_NAME);
			} else {
				throw new IllegalArgumentException("Parameter " + CONFIG_SAMPLING_STRATEGY_PARAM_NAME + " cannot take value: " + params.get(CONFIG_SAMPLING_STRATEGY_PARAM_NAME));
			}
		}

		// pass on to underlying classifier
		scoringAlgorithm.setConfigParameters(params);
	}

	@Override
	public int getNumObservationsUsed() {
		if(!isTrained())
			return 0;
		return predictors.values().iterator().next().getNumObservationsUsed();
	}

	/* 
	 * =================================================
	 * 			TRAIN
	 * =================================================
	 */
	/**
	 * Train the complete CVAP
	 * @throws IllegalArgumentException Too small dataset or invalid arguments 
	 */
	@Override
	public void train(Dataset problem) throws IllegalArgumentException {

		Iterator<TrainSplit> splits = strategy.getIterator(problem, seed);

		predictors=new HashMap<>();

		//Train the models
		int i=0, nrModels=strategy.getNumSamples();
		LOGGER.debug("Training CVAP Predictor with {} models", nrModels);

		while (splits.hasNext()){
			IVAPClassifier ivap = new IVAPClassifier(scoringAlgorithm.clone());
			TrainSplit nextDataset = splits.next();
			ivap.train(nextDataset);
			predictors.put(i, ivap);
			LOGGER.debug(" - Trained model {}/{}",(i+1), nrModels);
			i++;
		}

	}

	public void train(Dataset problem, int index) throws IllegalArgumentException {

		if (predictors == null)
			predictors = new HashMap<>();
		SamplingStrategyUtils.validateTrainSplitIndex(strategy, index);
		TrainSplitGenerator generator = strategy.getIterator(problem, seed);

		IVAPClassifier ivap = new IVAPClassifier(scoringAlgorithm.clone());
		TrainSplit split=null;
		try {
			split = generator.get(index);
		} catch(NoSuchElementException e) {
			LOGGER.debug("Tried to get a non-existing index split",e);
			throw new IllegalArgumentException("Cannot train index " + index + ", only allowed indexes are [0,"+(strategy.getNumSamples()-1)+"]");
		}
		ivap.train(split);
		predictors.put(index, ivap);

	}

	/* 
	 * =================================================
	 * 			PREDICT
	 * =================================================
	 */

	private void assertIsTrained(){
		if (! isTrained())
			throw new IllegalStateException("Predictor not trained");
	}

	public CVAPPrediction<Integer> predict(FeatureVector example) 
			throws IllegalStateException {
		assertIsTrained();
		
		Integer label0=null, label1=null;
		final List<Double> p0s = new ArrayList<>(predictors.size()),
			p1s = new ArrayList<>(predictors.size()),
			intervalWidths = new ArrayList<>(predictors.size()),
			oneMinusP0 = new ArrayList<>(predictors.size());

		boolean firstIteration=true; // Check if initial stuff needs to be set

		for (IVAPClassifier ivap : predictors.values()){
			Map<Integer, Pair<Double,Double>> interval = ivap.predict(example);

			if (firstIteration){
				// Chose label to compute for
				Set<Integer> labels = interval.keySet();
				Iterator<Integer> labelsIt = labels.iterator();
				label0 = labelsIt.next();
				label1 = labelsIt.next();
			}
			// Collect all info
			Pair<Double,Double> p0p1 = interval.get(label0);
			final double p0 = p0p1.getLeft();
			final double p1 = p0p1.getRight();
			p0s.add(p0);
			p1s.add(p1);
			intervalWidths.add(p1-p0);
			oneMinusP0.add(1-p0);

			// Make sure init of params is not done again
			firstIteration=false;
		}

		// After all IVAPs
		double gmP1 = MathUtils.geometricMean(p1s);
		double gm1mP0 = MathUtils.geometricMean(oneMinusP0);
		double probability = gmP1/(gm1mP0+gmP1);
		if (Double.isNaN(probability) || Double.isNaN(gm1mP0) || Double.isNaN(gmP1)) {
			LOGGER.debug("CVAP probability calculation: gmP1={}, gm1mP0={}, prob={}",
					gmP1,gm1mP0,probability);
			if (gmP1 == 0 && gm1mP0 == 0)
				LOGGER.debug("gmP1 and gm1mP0 are both == 0!!, the full lists p1s={} and oneMinusP0={}",p1s,oneMinusP0);
		}

		Map<Integer,Double> probabilities = new HashMap<>();
		probabilities.put(label0, probability);
		probabilities.put(label1, 1-probability);

		final double meanIntervalWidth = MathUtils.mean(intervalWidths);
		final double medianIntervalWidth = MathUtils.median(intervalWidths);


		return new CVAPPrediction<Integer>(p0s, p1s, label0, label1, probabilities, meanIntervalWidth,medianIntervalWidth);
	}

	@Override
	public List<SparseFeature> calculateGradient(FeatureVector example)
			throws IllegalStateException {
		return calculateGradient(example, DefaultMLParameterSettings.DEFAULT_STEPSIZE);
	}

	public List<SparseFeature> calculateGradient(FeatureVector example, int label)
			throws IllegalStateException {
		return calculateGradient(example, DefaultMLParameterSettings.DEFAULT_STEPSIZE, label);
	}

	@Override
	public List<SparseFeature> calculateGradient(FeatureVector example, double stepsize)
			throws IllegalStateException {

		// Find the most likely class - use that for computing gradient
		CVAPPrediction<Integer> res = predict(example);
		int label = ClassificationUtils.getPredictedClass(res.getProbabilities());
		return calculateGradient(example, stepsize, label, res);
	}

	public List<SparseFeature> calculateGradient(FeatureVector example, double stepsize, int label)
			throws IllegalStateException {

		return calculateGradient(example, stepsize, label, predict(example));
	}

	private List<SparseFeature> calculateGradient(FeatureVector example, double stepsize, int label, CVAPPrediction<Integer> prediction)
			throws IllegalStateException {
		if (! isTrained())
			throw new IllegalStateException("Predictor not trained yet");
		List<List<SparseFeature>> gradients = new ArrayList<>();

		for (IVAPClassifier model : predictors.values()){
			gradients.add(model.calculateGradient(example, stepsize, label));
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
		String cvapDir = DataIOUtils.createBaseDirectory(sink, basePath, CVAP_DIRECTORY_NAME+"/");
		LOGGER.debug("Saving AVAPClassifier to jar, loc={}", cvapDir);

		// write meta.json
		Map<String, Object> params = getProperties();
		try (OutputStream metaStream = sink.getOutputStream(cvapDir+CVAP_META_FILE_NAME)){
			MetaFileUtils.writePropertiesToStream(metaStream,getProperties());
		} catch (Exception e) {
			LOGGER.debug("Failed saving AVAP properties to stream", e);
			throw new IOException("Failed saving AVAPClassifier");
		}
		LOGGER.debug("Written CVAP Properties to jar: {}", params);

		// for each IVAP - write it
		int i=0;
		for (Entry<Integer, IVAPClassifier> ivap: predictors.entrySet()){
			LOGGER.debug("Attempting to write IVAP with id={} to dataSink",ivap.getKey());
			ivap.getValue().saveToDataSink(sink, cvapDir+IVAP_BASE_FILE_NAME+'.'+ivap.getKey(), spec);
			i++;
		}
		LOGGER.debug("Written {} IVAP models to jar",i);

	}

	public void loadFromDataSource(DataSource source, EncryptionSpecification spec) throws IOException, IllegalArgumentException, InvalidKeyException {
		loadFromDataSource(source, null, spec);
	}

	@Override
	public void loadFromDataSource(DataSource source, String basePath, EncryptionSpecification spec) 
			throws IOException, IllegalArgumentException, InvalidKeyException {

		String cvapDir = DataIOUtils.locateBasePath(source, basePath, CVAP_DIRECTORY_NAME+ "/");
		LOGGER.debug("loading AVAP from source, location={}",cvapDir);

		// Load meta.params
		try(
				InputStream metaDataStream = source.getInputStream(cvapDir+CVAP_META_FILE_NAME);
				){
			Map<String,Object> properties = MetaFileUtils.readPropertiesFromStream(metaDataStream);
			LOGGER.debug("cvap properties from meta-file: {}",properties);

			// Sampling strategy
			strategy = SamplingStrategyUtils.fromProperties(properties);
			seed = TypeUtils.asLong(properties.get(PropertyNameSettings.ML_SEED_VALUE_KEY));

		} catch (IOException e){
			LOGGER.debug("Could not read the cvap meta-file",e);
			throw new IOException(e);
		}
		LOGGER.debug("Loaded CVAP meta-file");

		// Load the IVAPS
		int nrModels = strategy.getNumSamples();
		predictors = new HashMap<>(nrModels);
		for (int i=0; i<nrModels; i++){
			IVAPClassifier ivap = new IVAPClassifier();
			try {
				ivap.loadFromDataSource(source, cvapDir+IVAP_BASE_FILE_NAME+'.'+i, spec);
				predictors.put(i, ivap);
				LOGGER.debug(" - Loaded model {}/{}", (i+1),nrModels);
			} catch (IOException e) {
				LOGGER.debug("There was no {}:t model in the jar",i);
			} catch(InvalidKeyException e) {
				LOGGER.debug("Failing ACPregression loading, ICP encrypted",e);
				throw new InvalidKeyException(e.getMessage());
			} catch (Exception e){ 
				LOGGER.debug("Could not load the {}:t model in the jar",i,e);
			}
		}

		if (predictors.isEmpty()) {
			LOGGER.debug("No IVAPs successfully loaded, failing!");
			throw new IOException("No IVAPs could be loaded from file");
		}

		// get the underlying algorithm from the first IVAP
		scoringAlgorithm = (SVC) predictors.values().iterator().next().getScoringAlgorithm().clone();

		// load scaling factors
		if (source.hasEntry(cvapDir + FEATURE_SCALING_FILE_NAME)) {
			throw new IOException("The model has scaling factors - this is no longer supported - please use an older version of CPSign if you wish to use this model");
		}
		LOGGER.debug("Finished loading AVAP Classification");
	}

}

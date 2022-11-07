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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;

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
import com.arosbio.ml.algorithms.impl.DefaultMLParameterSettings;
import com.arosbio.ml.cp.ConformalClassifier;
import com.arosbio.ml.cp.icp.ICPClassifier;
import com.arosbio.ml.cp.nonconf.classification.NCMMondrianClassification;
import com.arosbio.ml.io.MetaFileUtils;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.cp.classification.ProportionSingleLabelPredictions;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.sampling.SamplingStrategy;
import com.arosbio.ml.sampling.SamplingStrategyUtils;
import com.arosbio.ml.sampling.TrainSplit;
import com.arosbio.ml.sampling.TrainSplitIterator;

public final class ACPClassifier extends PredictorBase implements ACP, ConformalClassifier {

	public static final String PREDICTOR_TYPE = "ACP Classification";

	private static final Logger LOGGER = LoggerFactory.getLogger(ACPClassifier.class);
	private static final String ACP_DIRECTORY_NAME = "acp";
	private static final String ACP_META_FILE_NAME = "meta.json";
	private static final String ICP_BASE_FILE_NAME = "model";

	private Map<Integer,ICPClassifier> predictors = new HashMap<>();
	private SamplingStrategy strategy;
	private ICPClassifier icpImplementation;
	private TrainSplitIterator splitsIterator;
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

	public ACPClassifier() {
		super();
	}
	
	public ACPClassifier(NCMMondrianClassification ncm, SamplingStrategy strategy) {
		super();
		this.icpImplementation = new ICPClassifier(ncm);
		this.strategy = strategy;
	}
	
	public ACPClassifier(ICPClassifier icpImpl, SamplingStrategy strategy) {
		super();
		this.icpImplementation = icpImpl;
		this.strategy = strategy;
	}

	@Override
	public ACPClassifier clone(){
		ACPClassifier clone = new ACPClassifier();
		clone.strategy = strategy.clone();
		if (icpImplementation != null)
			clone.icpImplementation = icpImplementation.clone();
		clone.seed = this.seed; 
		// Copy all ICPs
		if (predictors != null)
			for (Integer i : predictors.keySet())
				clone.predictors.put(i, predictors.get(i).clone());
		
		return clone;
	}
	
	public String toString() {
		return "ACP classification with " + strategy.toString();
	}


	/* 
	 * =================================================
	 * 			GETTERS AND SETTERS
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
		return new ProportionSingleLabelPredictions();
	}

	public Map<Integer,ICPClassifier> getPredictors(){
		return predictors;
	}

	public boolean holdsResources(){
		return ! predictors.isEmpty();
	}

	public boolean releaseResources(){
		if (predictors == null || predictors.isEmpty())
			return false;

		// Release all ICPs memory
		boolean state = true;
		for (ICPClassifier icp : predictors.values()){
			state = MathUtils.keepFalse(state, icp.releaseResources());
		}
		// Drop references
		predictors.clear();
		return state;
	}



	@Override
	public String getPredictorType() {
		return PREDICTOR_TYPE;
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

	public Set<Integer> getLabels(){
		if (predictors!=null && !predictors.isEmpty())
			return predictors.values().iterator().next().getLabels();
		return new HashSet<>();
	}

	/**
	 * Add an ICP to an Aggregated Conformal Predictor, using random sampling strategy
	 * @param icp The ICP to add
	 * @throws IllegalAccessException if sampling strategy is <b>folded</b> - then must specify which fold the ICP belongs to
	 */
	public void addICP(ICPClassifier icp) throws IllegalAccessException {
		if(strategy.isFolded())
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
		LOGGER.debug("ACP already 'full' - updating sampling strategy to add more icp-models");
		// if we're get here - update the strategy and add the model to next index
		((RandomSampling) strategy).setNumSamples(strategy.getNumSamples());
		predictors.put(strategy.getNumSamples(), icp);
		LOGGER.debug("Added ICP in index={}",strategy.getNumSamples());
	}

	/**
	 * Add an ICP to an ACP that has folded sampling strategy
	 * @param icp The ICP to add
	 * @param index The fold the ICP belongs to
	 * @throws IllegalArgumentException If <code>index</code> is outside the number of folds set in the strategy of the ACP
	 */
	public void addICP(ICPClassifier icp, int index) throws IllegalArgumentException {
		if (predictors==null)
			predictors = new HashMap<>();
		LOGGER.debug("Attempting to add new ICP to fold={}",index);

		if (index >= strategy.getNumSamples() || index < 0)
			throw new IllegalArgumentException("index must be within range [0,"+(strategy.getNumSamples()-1)+"]");
		predictors.put(index, icp);
		LOGGER.debug("added ICP");
	}

	@Override
	public int getNumClasses() {
		if (predictors.isEmpty())
			return 0;
		return predictors.values().iterator().next().getNumClasses();
	}

	public ICPClassifier getICPImplementation() {
		return icpImplementation;
	}
	
	public void setICPImplementation(ICPClassifier impl) {
		this.icpImplementation = impl;
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

	@Override
	public SamplingStrategy getStrategy(){
		return strategy;
	}

	public void setStrategy(SamplingStrategy strategy) {
		this.strategy = strategy;
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
	public Map<String, Object> getProperties() {
		Map<String,Object> props = new HashMap<>();
		props.putAll(strategy.getProperties());
		if (icpImplementation != null)
			props.putAll(icpImplementation.getProperties());
		props.put(PropertyNameSettings.ML_SEED_VALUE_KEY, seed);
		props.put(PropertyNameSettings.ML_TYPE_KEY, PredictorType.ACP_CLASSIFICATION.getId());
		props.put(PropertyNameSettings.ML_TYPE_NAME_KEY, PredictorType.ACP_CLASSIFICATION.getName());
		props.put(PropertyNameSettings.IS_CLASSIFICATION_KEY, true);
		return props;
	}

	@Override
	public int getNumObservationsUsed() {
		if(predictors.isEmpty())
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
	public void train(Dataset problem) 
			throws IllegalArgumentException {
		Iterator<TrainSplit> splits = strategy.getIterator(problem, seed);

		predictors=new HashMap<>();

		//Train the models
		int i=0, nrModels=strategy.getNumSamples();
		LOGGER.debug("Training ACP Predictor with {} models", nrModels);

		while (splits.hasNext()){
			ICPClassifier icp = icpImplementation.clone();
			TrainSplit nextDataset = splits.next();
			icp.train(nextDataset);
			nextDataset.clear(); //explicitly clear all memory
			predictors.put(i, icp);
			LOGGER.debug(" - Trained model {}/{}",(i+1), nrModels);
			i++;
		}

	}

	public void train(Dataset problem, int index) throws IllegalArgumentException {

		if (icpImplementation == null)
			throw new IllegalStateException("No ICP implementation given to train");
		if (predictors == null)
			predictors = new HashMap<>();

		if (splitsIterator==null || splitsIterator.getProblem() != problem) {
			// Set up the splits 
			splitsIterator = strategy.getIterator(problem, seed);
			LOGGER.debug("Set up new splits-iterator for training");
		}

		ICPClassifier icp = icpImplementation.clone();
		TrainSplit split=null;
		try {
			split = splitsIterator.get(index);
		} catch (NoSuchElementException e) {
			LOGGER.debug("Tried to get a non-existing index split",e);
			throw new IllegalArgumentException("Cannot train index " + index + ", only allowed indexes are [0,"+(strategy.getNumSamples()-1)+"]");
		}
		icp.train(split);
		split.clear(); //explicitly clear all memory
		predictors.put(index, icp);

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

	public Map<Integer, Double> predict(final FeatureVector example) 
			throws IllegalStateException {

		//Ensure that we have models
		assertIsTrained();

		// class--> pvalues from all ICPs
		Map<Integer, List<Double>> icpResults = new HashMap<>();

		for (Entry<Integer, ICPClassifier> model : predictors.entrySet()){
			Map<Integer,Double> results = model.getValue().predict(example);
			LOGGER.trace("ACP prediction: {} classification P-values: {}", model.getKey(), results);

			if (icpResults.isEmpty()) {
				for(Map.Entry<Integer, Double> pval: results.entrySet())
					icpResults.put(pval.getKey(), new ArrayList<>());
			}

			for (Map.Entry<Integer, Double> pval : results.entrySet())
				icpResults.get(pval.getKey()).add(pval.getValue());

		}

		// Aggregate predictions
		Map<Integer, Double> acpResult = new HashMap<>();
		for (Map.Entry<Integer, List<Double>> clazz: icpResults.entrySet()){
			acpResult.put(clazz.getKey(), ACP.aggregate(aggregation, clazz.getValue()));
		}
		LOGGER.trace("ACP result: {}", acpResult);
		return acpResult;
	}

	@Override
	public List<SparseFeature> calculateGradient(FeatureVector example) 
			throws IllegalStateException {
		return calculateGradient(example, DefaultMLParameterSettings.DEFAULT_STEPSIZE);
	}

	@Override
	public List<SparseFeature> calculateGradient(FeatureVector example, double stepsize) 
			throws IllegalStateException {
		assertIsTrained();

		// Find the highest P-value and use as the label to calculate gradient of
		Map<Integer, Double> pVals = predict(example);
		int label = ClassificationUtils.getPredictedClass(pVals);

		return calculateGradient(example, stepsize, label);
	}

	public List<SparseFeature> calculateGradient(FeatureVector feature, int label) 
			throws IllegalStateException {
		return calculateGradient(feature, DefaultMLParameterSettings.DEFAULT_STEPSIZE, label);
	}

	public List<SparseFeature> calculateGradient(FeatureVector example, double stepsize, int label) 
			throws IllegalStateException {
		assertIsTrained();

		List<List<SparseFeature>> gradients = new ArrayList<>();

		for (ICPClassifier model : predictors.values()){
			List<SparseFeature> gradient = model.calculateGradient(example, stepsize, label);
			gradients.add(gradient);
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
		String acpDir = DataIOUtils.createBaseDirectory(sink, basePath, ACP_DIRECTORY_NAME + '/');

		// write meta.json
		Map<String, Object> params = getProperties();
		try (OutputStream metaStream = sink.getOutputStream(acpDir+ACP_META_FILE_NAME)) {
			MetaFileUtils.writePropertiesToStream(metaStream, params);
		}
		LOGGER.debug("Written ACP Properties to jar: {}", params);

		// for each ICP - write it
		int i=0;
		for (Entry<Integer, ICPClassifier> ivap: predictors.entrySet()){
			ivap.getValue().saveToDataSink(sink, acpDir+ICP_BASE_FILE_NAME+'.'+ivap.getKey(), spec);
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
			LOGGER.debug("No ICPImplementation set - falling back to the default ICPClassifier implementation");
			icpImplementation = new ICPClassifier();
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
		for(int i=0; i<nrModels; i++){
			ICPClassifier icp = icpImplementation.clone();
			try{
				icp.loadFromDataSource(source, acpDir+ICP_BASE_FILE_NAME+'.'+i, encryptSpec);
				predictors.put(i, icp);
				LOGGER.debug("ACP Classification loaded model {}/{}", (i+1),nrModels);
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
			LOGGER.debug("No models successfully loaded - failing");
			throw new IOException("No ICP models could be loaded from file");
		}
		// get the ICP impl from the first ICP
		icpImplementation = predictors.values().iterator().next().clone();

		LOGGER.debug("Finished loading ACPClassifier model");
	}

	public void loadFromDataSource(DataSource source, EncryptionSpecification encryptSpec) 
			throws IOException, InvalidKeyException, IllegalArgumentException {
		loadFromDataSource(source, null, encryptSpec);
	}

}

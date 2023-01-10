/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp.tcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.commons.GlobalConfig.Defaults.PredictorType;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.config.ImplementationConfig;
import com.arosbio.commons.mixins.ResourceAllocator;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.FeatureVector.Feature;
import com.arosbio.data.MissingDataException;
import com.arosbio.data.SparseFeature;
import com.arosbio.data.SparseFeatureImpl;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.DataIOUtils;
import com.arosbio.io.DataSink;
import com.arosbio.io.DataSource;
import com.arosbio.ml.PredictorBase;
import com.arosbio.ml.TrainingsetValidator;
import com.arosbio.ml.algorithms.impl.DefaultMLParameterSettings;
import com.arosbio.ml.cp.ConformalClassifier;
import com.arosbio.ml.cp.nonconf.NCM;
import com.arosbio.ml.cp.nonconf.calc.PValueCalculator;
import com.arosbio.ml.cp.nonconf.calc.SmoothedPValue;
import com.arosbio.ml.cp.nonconf.classification.NCMMondrianClassification;
import com.arosbio.ml.io.MetaFileUtils;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.cp.classification.ProportionSingleLabelPredictions;

public final class TCPClassifier extends PredictorBase implements TCP, ConformalClassifier {

	public static final String PREDICTOR_TYPE = "TCP Classification";

	private static final Logger LOGGER = LoggerFactory.getLogger(TCPClassifier.class);

	// SAVING
	private static final String TCP_DIRECTORY_NAME = "tcp";
	private static final String TCP_META_INFO = "tcp.meta.json";
	private static final String NCM_BASE = "ncm";

	private NCMMondrianClassification ncm;
	private PValueCalculator pValueCalculator = new SmoothedPValue();
	private Dataset originalData;

	private SubSet trainingData;
	private Set<Integer> labels;

	/* 
	 * =================================================
	 * 			CONSTRUCTORS
	 * =================================================
	 */

	public TCPClassifier() {
		super();
	}

	public TCPClassifier(NCMMondrianClassification ncm) {
		super();
		this.ncm = ncm;
	}

	public TCPClassifier(NCMMondrianClassification ncm, long seed) {
		this(ncm);
		this.seed = seed;
	}

	@Override
	public TCPClassifier clone() {
		TCPClassifier clone = new TCPClassifier(this.ncm.clone());
		clone.seed=seed;
		if (originalData != null)
			clone.originalData = originalData.clone();
		clone.pValueCalculator = pValueCalculator.clone();
		return clone;
	}

	/* 
	 * =================================================
	 * 			GETTERS AND SETTERS
	 * =================================================
	 */

	@Override
	public SingleValuedMetric getDefaultOptimizationMetric() {
		return new ProportionSingleLabelPredictions();
	}

	@Override
	public Dataset getDataset() {
		return originalData;
	}

	public PValueCalculator getPValueCalculator() {
		return pValueCalculator;
	}

	public void setPValueCalculator(PValueCalculator ncsEstimator) {
		this.pValueCalculator = ncsEstimator;
	}

	public NCMMondrianClassification getNCM() {
		return ncm;
	}

	public void setNCM(NCMMondrianClassification ncm) {
		this.ncm = ncm;
	}

	@Override
	public Long getSeed() {
		return super.getSeed();
	}

	@Override
	public void setSeed(long seed) {
		super.setSeed(seed);
		if (ncm!=null && ncm.getModel() != null)
			ncm.getModel().setSeed(seed);
		if (pValueCalculator != null)
			pValueCalculator.setRNGSeed(seed);
	}

	@Override
	public boolean isTrained() {
		return originalData != null && !originalData.isEmpty() && ncm != null && pValueCalculator != null;
	}

	@Override
	public int getNumObservationsUsed() {
		return originalData.getNumRecords();
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String, Object> props = new HashMap<>();
		props.putAll(ncm.getProperties()); // this should include everything to recreate the NCM implementation
		props.put(PropertyNameSettings.ML_TYPE_KEY, PredictorType.TCP_CLASSIFICATION.getId());
		props.put(PropertyNameSettings.ML_TYPE_NAME_KEY, PredictorType.TCP_CLASSIFICATION.getName());
		props.put(PropertyNameSettings.IS_CLASSIFICATION_KEY, true);
		props.put(PropertyNameSettings.ML_SEED_VALUE_KEY, seed);
		props.put(PValueCalculator.PVALUE_CALCULATOR_NAME_KEY, pValueCalculator.getName());
		props.put(PValueCalculator.PVALUE_CALCULATOR_ID_KEY, pValueCalculator.getID());
		if (pValueCalculator.getRNGSeed() != null)
			props.put(PValueCalculator.PVALUE_CALCULATOR_SEED_KEY, pValueCalculator.getRNGSeed());
		return props;
	}

	@Override
	public String getPredictorType() {
		return PREDICTOR_TYPE;
	}

	@Override
	public int getNumClasses() {
		if (!isTrained())
			return 0;
		return originalData.getLabels().size();
	}

	@Override
	public Set<Integer> getLabels(){
		if (! isTrained())
			return new HashSet<>();
		Set<Integer> set = new HashSet<>();
		for (double l : originalData.getLabels()){
			set.add((int)l);
		}
		return set;
	}

	@Override
	public boolean releaseResources() {
		if (holdsResources()){
			((ResourceAllocator)ncm.getModel()).releaseResources();
			return true;
		}
		return false;
	}

	@Override
	public boolean holdsResources() {
		return ncm != null && (ncm.getModel() instanceof ResourceAllocator);
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		List<ConfigParameter> params = new ArrayList<>();
		if (ncm!=null)
			params.addAll(ncm.getConfigParameters());

		// ncm estimator
		params.add(new ImplementationConfig.Builder<>(CONFIG_PVALUE_CALC_PARAM_NAMES, PValueCalculator.class).defaultValue(new SmoothedPValue()).build());
		return params;
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalArgumentException {
		for (Map.Entry<String, Object> kv : params.entrySet()) {
			try {
			if (CollectionUtils.containsIgnoreCase(CONFIG_PVALUE_CALC_PARAM_NAMES, kv.getKey())) {
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
	 * 			TRAIN
	 * =================================================
	 */

	@Override
	public void train(Dataset problem) throws MissingDataException, IllegalArgumentException {
		if (problem == null || problem.getNumRecords() <1)
			throw new IllegalArgumentException("No records sent");

		originalData = problem;

		List<DataRecord> trainingset = new ArrayList<>(problem.getNumRecords()+1);
		trainingset.addAll(problem.getDataset());
		trainingset.addAll(problem.getModelingExclusiveDataset());
		trainingset.addAll(problem.getCalibrationExclusiveDataset());

		// Validate to make sure it's big enough
		TrainingsetValidator.getInstance().validateClassification(trainingset);

		// Shuffle data
		trainingData = new SubSet(trainingset);
		if (trainingData.containsMissingFeatures())
			throw new MissingDataException("Training data contain missing feature values");

		LOGGER.debug("shuffling records with seed: {}",seed);
		trainingData.shuffle(seed);

		// Set the labels
		this.labels = new HashSet<>();
		for (double l: originalData.getLabels()) {
			this.labels.add((int)l);
		}

		LOGGER.debug("Finished 'training' TCP predictor - i.e. setting the training data");
	}


	/* 
	 * =================================================
	 * 			PREDICT
	 * =================================================
	 */

	private void assertIsTrained(){
		if (!isTrained())
			throw new IllegalStateException("Predictor not trained");
	}

	@Override
	public Map<Integer, Double> predict(final FeatureVector example)
			throws IllegalStateException {
		assertIsTrained();

		Map<Integer, Double> prediction = new HashMap<>();

		int beforeSize = trainingData.size(); 
		for (int label: labels) {
			prediction.put(label, predictPValueForClass(label, example));
		}

		int afterSize = trainingData.size();

		if (beforeSize != afterSize) {
			LOGGER.debug("The before and after sizes doesn't match! something is wrong in the code!");
			throw new RuntimeException("Coding error in TCP Classification");
		}


		return prediction;
	}

	private double predictPValueForClass(int label, FeatureVector example) {
		// Add the record, with the assumed label
		trainingData.add(new DataRecord((double)label, example));

		// train the NCM_INFO_FILE
		ncm.trainNCM(trainingData);

		// Predict all alphas (NCS) for the assumed label
		List<Double> ncs = new ArrayList<>((int)(trainingData.size()/2d));
		for (DataRecord r : trainingData) {
			if (label == (int)r.getLabel()) {
				// get the NCS for the label of interest
				ncs.add(ncm.calculateNCS(r.getFeatures()).get(label));
			}
		}

		// The last example is the example to predict!
		double ncsForTestEx = ncs.remove(ncs.size()-1);

		// Fit the ncs estimator
		pValueCalculator.build(ncs);

		// calculate the p-value for the test example
		double pValue = pValueCalculator.getPvalue(ncsForTestEx);

		// remove the example from the training examples!
		trainingData.remove(trainingData.size()-1);

		return pValue;
	}

	@Override
	public List<SparseFeature> calculateGradient(FeatureVector example)
			throws IllegalStateException {
		return calculateGradient(example, DefaultMLParameterSettings.DEFAULT_STEPSIZE);
	}

	@Override
	public List<SparseFeature> calculateGradient(FeatureVector example, double stepsize)
			throws IllegalStateException {

		//First do a normal prediction
		Map<Integer, Double> result = predict(example);

		//Pick class with largest pValue
		int selectedClass = 0;
		double highestPValue = -1d;
		for(Map.Entry<Integer, Double> pVales : result.entrySet()){
			if (pVales.getValue()>highestPValue){
				selectedClass=pVales.getKey();
				highestPValue = pVales.getValue();
			}
		}
		return doCalc(example, stepsize, selectedClass,result);
	}

	@Override
	public List<SparseFeature> calculateGradient(FeatureVector example, int label)
			throws IllegalStateException {
		return calculateGradient(example, DefaultMLParameterSettings.DEFAULT_STEPSIZE, label);
	}

	@Override
	public List<SparseFeature> calculateGradient(FeatureVector example, double stepsize, int label)
			throws IllegalStateException {
		return doCalc(example, stepsize, label, predict(example));
	}

	private List<SparseFeature> doCalc(FeatureVector example, double stepsize, int label, Map<Integer,Double> pvals){

	
		//The gradient to return, same size as the example to predict
		List<SparseFeature> gradient = new ArrayList<>(example.getNumExplicitFeatures());

		// First do a normal prediction
		// Map<Integer, Double> pvals = predict(example);
		double normalPValue = pvals.get(label);

		for (Feature f : example) {
			// Get the old value
			double oldValue = f.getValue();

			// Update it to the new
			example.withFeature(f.getIndex(), f.getValue()+stepsize);

			// Predict it and store in the gradient
			double diff = (predictPValueForClass(label, example)-normalPValue)/stepsize;
			gradient.add(new SparseFeatureImpl(f.getIndex(), diff));

			// Change it back to what it was!
			example.withFeature(f.getIndex(), oldValue);
		}

		return gradient;
	}

	@Override
	public void saveToDataSink(DataSink sink, String path, EncryptionSpecification encryptSpec)
			throws IOException, InvalidKeyException, IllegalStateException {

		// create the directory
		String tcpDir = DataIOUtils.createBaseDirectory(sink, path, TCP_DIRECTORY_NAME+'/');

		LOGGER.debug("Saving TCP Classifier to sink, location= {}", tcpDir);
		originalData.saveToDataSink(sink, tcpDir, encryptSpec);
		LOGGER.debug("Saved classifier data");

		LOGGER.debug("Saving TCP NCM");
		ncm.saveToDataSink(sink, tcpDir + NCM_BASE, encryptSpec);

		LOGGER.debug("Saving tcp meta info");
		try (OutputStream ncmPropertyStream = sink.getOutputStream(tcpDir+ TCP_META_INFO)){
			MetaFileUtils.writePropertiesToStream(ncmPropertyStream, getProperties());
		} catch(Exception e) {
			LOGGER.debug("Failed saving TCP properties to stream", e);
			throw new IOException("Failed saving TCP");
		}
	}

	@Override
	public void loadFromDataSource(DataSource source, String path, EncryptionSpecification encryptSpec)
			throws IOException, InvalidKeyException {

		String tcpDir = DataIOUtils.locateBasePath(source, path, TCP_DIRECTORY_NAME+'/');

		LOGGER.debug("Trying to load TCP classifier from src, location={}",tcpDir);
		Dataset p = new Dataset();
		p.loadFromDataSource(source, tcpDir, encryptSpec);
		LOGGER.debug("Loaded {} records",p.getNumRecords());

		LOGGER.debug("Trying to load TCP Meta info");
		Map<String,Object> props = null;
		try (InputStream tcpPropStream = source.getInputStream(tcpDir + TCP_META_INFO)){
			props = MetaFileUtils.readPropertiesFromStream(tcpPropStream);
			seed = TypeUtils.asLong(props.get(PropertyNameSettings.ML_SEED_VALUE_KEY));
		} catch(Exception e) {
			LOGGER.debug("Failed loading properties", e);
			throw new IOException("Could not load properties from source");
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

		NCM ncmLoaded = FuzzyServiceLoader.load(NCM.class, props.get(PropertyNameSettings.NCM_ID).toString());
		if (!(ncmLoaded instanceof NCMMondrianClassification)) {
			LOGGER.debug("TCP meta pointed to a faulty NCM implementation of non-correct type: {}", ncmLoaded.getName()); 
			throw new IOException("Failed initiatlizing the NCM for TCP");
		}
		ncm = (NCMMondrianClassification) ncmLoaded;

		ncm.loadFromDataSource(source, tcpDir + NCM_BASE, encryptSpec);

		// Set the properties saved in the model - hopefully this will set the model-specific things correctly!
		ncm.getModel().setConfigParameters(props);

		// Train it
		train(p);

		// set the seed
		setSeed(TypeUtils.asLong(props.get(PropertyNameSettings.ML_SEED_VALUE_KEY)));
	}

}

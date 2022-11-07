/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.vap.ivap;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.commons.MathUtils;
import com.arosbio.commons.TypeUtils;
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
import com.arosbio.ml.algorithms.ScoringClassifier;
import com.arosbio.ml.algorithms.impl.AlgorithmUtils;
import com.arosbio.ml.algorithms.impl.DefaultMLParameterSettings;
import com.arosbio.ml.algorithms.svm.SVC;
import com.arosbio.ml.interfaces.ClassificationPredictor;
import com.arosbio.ml.io.MetaFileUtils;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.arosbio.ml.sampling.TrainSplit;
import com.github.sanity.pav.PairAdjacentViolators;
import com.github.sanity.pav.Point;

import kotlin.jvm.functions.Function1;

public final class IVAPClassifier implements IVAP, ClassificationPredictor {

	private static final Logger LOGGER = LoggerFactory.getLogger(IVAPClassifier.class);

	// Setting nice paths in the output
	private static final String SCORING_MODEL_FILE_ENDING = ".model";
	private static final String SCORING_MODEL_PARAMS_FILE_ENDING = ".model.params";
	private static final String CALIBRATION_POINTS_FILE_ENDING = ".calibration";
	private static final String META_PARAMS_FILE_ENDING = ".meta.json";

	// Locating the actual stuff from the params-file
	private static final String SCORING_MODEL_LOCATION = "ivapScoringModelLocation";
	private static final String SCORING_PARAMS_LOCATION = "ivapScoringModelParamsLocation";
	private static final String CALIBRATION_POINTS_LOCATION = "ivapCalibPointsLocation";

	private ScoringClassifier model;
	/**
	 * Calibration points for calibrating the results, having a point with x=score, y={0, 1}
	 * where y=1 if of the same label as the first label of the ML algorithm, 0 otherwise
	 */
	private List<Point> calibrationPoints;
	private int numTrainingObservations;

	/* 
	 * =================================================
	 * 			CONSTRUCTORS
	 * =================================================
	 */
	public IVAPClassifier(){}

	public IVAPClassifier(ScoringClassifier algorithm) {
		model = algorithm;
	}

	/* 
	 * =================================================
	 * 			GETTERS AND SETTERS
	 * =================================================
	 */

	@Override
	public Map<String,Object> getProperties() {
		Map<String,Object> params = new HashMap<>();
		params.put(PropertyNameSettings.ML_SEED_VALUE_KEY, getSeed());
		params.put(PropertyNameSettings.NCM_SCORING_MODEL_ID, model.getID());
		params.put(PropertyNameSettings.NCM_SCORING_MODEL_NAME, model.getName());
		params.put(PropertyNameSettings.NUM_OBSERVATIONS_KEY, numTrainingObservations);
		return params;
	}

	@Override
	public boolean isTrained() {
		return model != null && calibrationPoints !=null && ! calibrationPoints.isEmpty();
	}

	public List<Point> getCalibrationPoints(){
		return calibrationPoints;
	}

	public void setCalibrationPoints(List<Point> points){
		this.calibrationPoints = new ArrayList<>(points);
	}

	public ScoringClassifier getScoringAlgorithm(){
		return model;
	}

	public void setScoringAlgorithm(ScoringClassifier model){
		this.model = model;
	}

	@Override
	public int getNumObservationsUsed() {
		if(!isTrained())
			return 0;
		return numTrainingObservations;
	}

	public Set<Integer> getLabels(){
		if (model!=null && model.isFitted()){
			return new HashSet<>(model.getLabels());
		}
		return new HashSet<>();
	}

	/**
	 * Copy of the underlying algorithm and the svm-parameters.
	 * Does not copy the trained models or calibration points!
	 * @return A copy of the current object
	 */
	@Override
	public IVAPClassifier clone(){
		return new IVAPClassifier(model.clone());
	}

	@Override
	public void setSeed(long seed) {
		model.setSeed(seed);
	}

	@Override
	public Long getSeed() {
		return model.getSeed();
	}

	@Override
	public int getNumClasses() {
		return 2;
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		return model.getConfigParameters();
	}

	@Override
	public void setConfigParameters(Map<String,Object> params) throws IllegalArgumentException {
		// pass on to underlying classifier
		model.setConfigParameters(params);
	}

	@Override
	public boolean releaseResources() {
		if (model != null && model instanceof ResourceAllocator){
			((ResourceAllocator)model).releaseResources();
			return true;
		}
		return false;
	}

	@Override
	public boolean holdsResources() {
		return model != null && model instanceof ResourceAllocator;
	}

	/* 
	 * =================================================
	 * 			TRAINING / PREDICTING
	 * =================================================
	 */

	public void train(TrainSplit dataset) 
			throws IllegalArgumentException {
		TrainingsetValidator.getInstance().validateClassification(dataset);
		// Train the underlying algorithm
		model.train(dataset.getProperTrainingSet());

		if (model.getLabels() == null || model.getLabels().size() <2)
			throw new IllegalArgumentException("Not enough classes given");
		LOGGER.debug("Trained IVAP classification underlying model with labels: {}, using {} records", 
				model.getLabels(), dataset.getProperTrainingSet().size());

		// Predict all scores in the calibration set
		int labelToPredictFor = model.getLabels().get(0);
		try {
			calibrationPoints = new ArrayList<>(dataset.getCalibrationSet().size()+1);
			for (DataRecord rec: dataset.getCalibrationSet()) {
				Map<Integer,Double> pred = model.predictScores(rec.getFeatures());
				// decision value = x, either 0 or 1 for y 
				calibrationPoints.add(new Point(pred.get(labelToPredictFor), 
						(labelToPredictFor == (int)rec.getLabel()? 1 : 0)));
			}
		} catch (IllegalStateException e){
			LOGGER.debug("IllegalState when predicting calibration set to build calibration-points for isotonic regression",e);
			throw new RuntimeException(e.getMessage());
		}
		LOGGER.debug("Finished computing scores for all ({}) records in the calibration set - finished fitting full IVAP", calibrationPoints.size());

		numTrainingObservations = dataset.getTotalNumTrainingRecords();
	}

	public Map<Integer,Pair<Double, Double>> predict(FeatureVector example) 
			throws IllegalStateException {
		if (! isTrained())
			throw new IllegalStateException("The IVAP has not been trained yet");
		if (example == null)
			throw new IllegalArgumentException("example to predict was null");
		int numCalibPoints = calibrationPoints.size();
		Map<Integer,Double> pred = model.predictScores(example);

		List<Integer> labels = model.getLabels();
		double score = pred.get(labels.get(0));

		// Fit isotonic regression using first hypothetical label
		calibrationPoints.add(new Point(score, 0d));
		PairAdjacentViolators pavLabel0 = new PairAdjacentViolators(calibrationPoints);
		final Function1<Double, Double> interpolatorLabel0 = pavLabel0.interpolator();
		double p0 = MathUtils.truncate(interpolatorLabel0.invoke(score), 0d, 1d);
		calibrationPoints.remove(calibrationPoints.size()-1); // remove the added example
		//			printCalibPoints();

		// Fit isotonic regression using second hypothetical label
		calibrationPoints.add(new Point(score, 1d)); 
		PairAdjacentViolators pavLabel1 = new PairAdjacentViolators(calibrationPoints);
		final Function1<Double, Double> interpolatorLabel1 = pavLabel1.interpolator();
		double p1 = MathUtils.truncate(interpolatorLabel1.invoke(score), 0d, 1d);
		calibrationPoints.remove(calibrationPoints.size()-1); // remove the added example

		if (calibrationPoints.size() != numCalibPoints)
			throw new RuntimeException("Something went wrong in the IVAP algorithm");

		Map<Integer, Pair<Double,Double>> result = new HashMap<>();
		result.put(labels.get(0), ImmutablePair.of(p0, p1));
		result.put(labels.get(1), ImmutablePair.of(1-p1, 1-p0));

		return result;

	}

	public List<SparseFeature> calculateGradient(FeatureVector example, int label)
			throws IllegalStateException, IllegalArgumentException {
		return calculateGradient(example, DefaultMLParameterSettings.DEFAULT_STEPSIZE, label);
	}

	public List<SparseFeature> calculateGradient(FeatureVector example, double stepsize, int label)
			throws IllegalStateException, IllegalArgumentException {
		if (!isTrained())
			throw new IllegalStateException("Predictor not trained");
		List<Integer> labels = model.getLabels();
		if (!labels.contains((Integer) label)){
			throw new IllegalArgumentException("Given label " + label + ", which is not what the model was trained for");
		}
			
		//The gradient to return, same size as the example to predict
		List<SparseFeature> gradient = new ArrayList<>(example.getNumExplicitFeatures());

		//First do a normal prediction
		Map<Integer, Pair<Double,Double>> result = predict(example);
		double originalPred = MathUtils.mean(result.get(label).getLeft(), result.get(label).getRight());

		// LOGGER.trace("========\nOriginal features: " + example);
		LOGGER.debug("Computing gradient for label={}, original probability={}",
				label,originalPred);

		//Loop over all features and make a prediction in each case
		for (Feature f : example) {

			// Save instance 
			Feature oldInstance = f.clone();

			// Set the new updated value
			example.setFeature(f.getIndex(), f.getValue()+stepsize);

			//predict and extract the p-value for the class
			Map<Integer, Pair<Double,Double>> fresult = predict(example);
			double alteredPred = MathUtils.mean(fresult.get(label).getLeft(), fresult.get(label).getRight());

			//Set diff as value in gradient array
			double diff = (alteredPred-originalPred)/stepsize;
			gradient.add(new SparseFeatureImpl(f.getIndex(), diff));
			LOGGER.trace("Normal={}, altered={}, diff={}",
					originalPred,alteredPred,diff);

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
	public void saveToDataSink(DataSink sink, String modelbasePath, EncryptionSpecification spec) 
			throws IOException, InvalidKeyException, IllegalStateException {
		String scoreLocation = modelbasePath+SCORING_MODEL_FILE_ENDING, 
				scoreParamsLocation = modelbasePath+SCORING_MODEL_PARAMS_FILE_ENDING,
				calibScoresLocation = modelbasePath + CALIBRATION_POINTS_FILE_ENDING,
				metaLocation = modelbasePath + META_PARAMS_FILE_ENDING;
		// ML-model
		if (spec != null){
			try(
					OutputStream modelStream = sink.getOutputStream(scoreLocation);
					OutputStream encryptedStream = spec.encryptStream(modelStream);){
				model.saveToStream(encryptedStream);
			}
		} else {
			try(OutputStream modelStream = sink.getOutputStream(scoreLocation);){
				model.saveToStream(modelStream);
			}
		}
		LOGGER.debug("Saved model file in IVAPClassifier, location={}",scoreLocation);

		// calibration points
		if (spec!=null) {
			try(
					OutputStream calibStream = sink.getOutputStream(calibScoresLocation);
					OutputStream encryptedStream = spec.encryptStream(calibStream);
					BufferedWriter calibWriter = new BufferedWriter(new OutputStreamWriter(encryptedStream));
					){
				writeCalibrationPoints(calibWriter);
			}
		} else {
			try(
					OutputStream calibStream = sink.getOutputStream(calibScoresLocation);
					BufferedWriter calibWriter = new BufferedWriter(new OutputStreamWriter(calibStream));
					){
				writeCalibrationPoints(calibWriter);
			}
		}
		LOGGER.debug("Saved calibration points in IVAPClassifier, location={}",
				calibScoresLocation);

		// Meta file
		try (OutputStream paramStream = sink.getOutputStream(metaLocation);){
			Map<String,Object> prop = getProperties();
			prop.put(PropertyNameSettings.IS_ENCRYPTED, (spec!= null));
			prop.put(SCORING_MODEL_LOCATION, scoreLocation);
			prop.put(SCORING_PARAMS_LOCATION, scoreParamsLocation);
			prop.put(CALIBRATION_POINTS_LOCATION, calibScoresLocation);
			MetaFileUtils.writePropertiesToStream(paramStream, prop);
		}
		LOGGER.debug("Saved meta-params in IVAPClassifier, location={}",
				metaLocation);

	}



	private void writeCalibrationPoints(BufferedWriter writer) throws IOException{
		for (Point p: calibrationPoints) {
			writer.write(""+p.getX());
			writer.write(',');
			writer.write(""+p.getY());
			writer.write(',');
			writer.write(""+p.getWeight());
			writer.write(',');
			writer.newLine();
		}
		writer.flush();
		LOGGER.debug("Saved calibration points to writer");
	}

	@Override
	public void loadFromDataSource(DataSource src, String modelName, EncryptionSpecification spec)
			throws IOException, IllegalArgumentException, InvalidKeyException {

		if (! src.hasEntry(modelName+META_PARAMS_FILE_ENDING))
			throw new IllegalArgumentException("No IVAP model in source under modelName=" + modelName);

		// Load params
		Map<String,Object> props = null;
		try (InputStream istream = src.getInputStream(modelName+META_PARAMS_FILE_ENDING)){
			props = MetaFileUtils.readPropertiesFromStream(istream);
		}

		if ((boolean)props.get(PropertyNameSettings.IS_ENCRYPTED) && spec == null) {
			throw new InvalidKeyException("Model encrypted - no encryption key sent");
		} else if (! (boolean) props.get(PropertyNameSettings.IS_ENCRYPTED)) {
			spec = null; 
		}

		// Load model
		MLAlgorithm alg =  FuzzyServiceLoader.load(MLAlgorithm.class, props.get(PropertyNameSettings.NCM_SCORING_MODEL_ID).toString());
		if (alg instanceof SVC) {
			model = (SVC) alg;
			LOGGER.debug("Instantiated IVAP underlying model of type: {}", model.getName());
		} else {
			LOGGER.debug("ML algorithm not a classification model, but of class: {}", alg.getClass());
			throw new IllegalArgumentException("ML algorithm not of supported class for Venn Prediction");
		}

		if (props.get(SCORING_MODEL_LOCATION) != null) {
			LOGGER.debug("Trying to load scoring model from location={}",props.get(SCORING_MODEL_LOCATION));
			AlgorithmUtils.loadAlgorithm(src, (String)props.get(SCORING_MODEL_LOCATION), alg, spec);
			LOGGER.debug("Loaded ML model successfully");
		} else {
			LOGGER.debug("No Scoring model saving location found in properties, failing loading");
			throw new IOException("Could not load scoring model for IVAP");
		}

		// Further properties
		if (props.containsKey(PropertyNameSettings.NUM_OBSERVATIONS_KEY)) {
			numTrainingObservations = TypeUtils.asInt(props.get(PropertyNameSettings.NUM_OBSERVATIONS_KEY));
			LOGGER.debug("Loaded the number of training examples used: {}", numTrainingObservations);
		}

		// Load calibration points
		if (props.containsKey(CALIBRATION_POINTS_LOCATION)) {
			LOGGER.debug("Trying to load calibration points, loc={}", props.get(CALIBRATION_POINTS_LOCATION));
			if (spec != null) {
				try (InputStream rawStream = src.getInputStream(props.get(CALIBRATION_POINTS_LOCATION).toString());
						InputStream istream = spec.decryptStream(rawStream);){
					loadCalibrationPoints(istream);
				}
			} else {
				try (InputStream istream = src.getInputStream(props.get(CALIBRATION_POINTS_LOCATION).toString());){
					loadCalibrationPoints(istream);
				}
			}
			LOGGER.debug("Loaded calibration points, successully loaded complete IVAP");
		} else {
			LOGGER.debug("No location saved for calibration points, IVAP not loaded successfully");
		}
		
	}

	private void loadCalibrationPoints(InputStream calibrationStream) throws IOException{
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(calibrationStream))){
			calibrationPoints = new ArrayList<>();
			String line =null;
			while ( (line=reader.readLine())!=null) {
				String[] splits = line.split(",");
				calibrationPoints.add(new Point(
						Double.parseDouble(splits[0]), 
						Double.parseDouble(splits[1]), 
						Double.parseDouble(splits[2])));
			}
		} catch (Exception e){
			LOGGER.debug("Failed loading calibration points",e);
			throw e;
		}
		LOGGER.debug("Loaded calibration points from stream");
	}

}

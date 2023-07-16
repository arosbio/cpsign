/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.testing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.StringUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.DataUtils.DataType;
import com.arosbio.data.Dataset;
import com.arosbio.ml.algorithms.Classifier;
import com.arosbio.ml.algorithms.MLAlgorithm;
import com.arosbio.ml.algorithms.PseudoProbabilisticClassifier;
import com.arosbio.ml.algorithms.Regressor;
import com.arosbio.ml.algorithms.ScoringClassifier;
import com.arosbio.ml.cp.ConformalClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.MetricAggregation;
import com.arosbio.ml.metrics.MetricFactory;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.classification.PointClassifierMetric;
import com.arosbio.ml.metrics.classification.ProbabilisticMetric;
import com.arosbio.ml.metrics.classification.ScoringClassifierMetric;
import com.arosbio.ml.metrics.plots.PlotMetric;
import com.arosbio.ml.metrics.plots.PlotMetricAggregation;
import com.arosbio.ml.metrics.regression.PointPredictionMetric;
import com.arosbio.ml.testing.utils.EvaluationUtils;
import com.arosbio.ml.vap.avap.AVAPClassifier;

/**
 * TestRunner - runs tests given a testing strategy.
 * 
 * @author staffan
 * @author Aros bio
 */
public class TestRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(TestRunner.class);

	private static final List<Double> DEFAULT_EVAL_POINTS = Arrays.asList(0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,.95);

	private final TestingStrategy strategy;
	private final List<Double> evalPointsToInject;
	private final boolean calculateMeanAndStd;
	private final double allowedFailureRatio;

	private TestRunner(Builder b){
		this.strategy = b.strat.clone();
		this.evalPointsToInject = (b.evalPointsToInject != null ? new ArrayList<>(b.evalPointsToInject) : null);
		this.calculateMeanAndStd = b.calculateMeanAndStd;
		this.allowedFailureRatio = b.allowedFailureRatio;
	}

	public static class Builder {
		private TestingStrategy strat;
		private List<Double> evalPointsToInject;
		private boolean calculateMeanAndStd = true;
		private double allowedFailureRatio = 0.05;

		public Builder(TestingStrategy strategy){
			this.strat = strategy;
		}

		public Builder strategy(TestingStrategy strategy){
			this.strat = strategy;
			return this;
		}

		public Builder evalPoints(List<Double> points){
			this.evalPointsToInject = points;
			return this;
		}

		public Builder evalPoints(double... points){
			if (points==null || points.length == 0)
				this.evalPointsToInject = null;
			else {
				this.evalPointsToInject = new ArrayList<>(points.length); 
				for (double p : points){
					this.evalPointsToInject.add(p);
				}
			}
			return this;
		}

		/**
		 * Set if a test-strategy with more than one split should calculate the mean and standard deviation 
		 * from the folds (<code>true</code>), or if a single metric should be calculated using all test splits (<code>false</code>).
		 * Note that if this is true (default) and several test-splits are used, then {@link com.arosbio.ml.metrics.SingleValuedMetric SingleValuedMetric} instances will be wrapped
		 * in {@link com.arosbio.ml.metrics.MetricAggregation MetricAggregation} instances in the returned metrics lists. 
		 * @param calc calculate mean and standard deviations of multiple test-splits (true) or calculate a single metric for all test-splits
		 * @return The TestRunner.Builder instance
		 */
		public Builder calcMeanAndStd(boolean calc){
			this.calculateMeanAndStd = calc;
			return this;
		}

		public Builder allowedFailureRatio(double ratio){
			this.allowedFailureRatio = ratio;
			return this;
		}

		public TestRunner build(){
			return new TestRunner(this);
		}
	}

	/**
	 * Runs the testing, using all {@link com.arosbio.ml.metrics.Metric Metrics} 
	 * defined for the given {@link com.arosbio.ml.interfaces.Predictor Predictor}.
	 * @param data The Dataset with data to be used for training and testing
	 * @param predictor The predictor to be used
	 * @return A list of {@link com.arosbio.ml.metrics.Metric Metrics}
	 * @throws IllegalArgumentException Faulty arguments
	 * @throws UnsupportedPredictorException If cross validation is not supported for the given {@link com.arosbio.ml.interfaces.Predictor Predictor}
	 */
	public List<Metric> evaluate(Dataset data, Predictor predictor)
			throws IllegalArgumentException, UnsupportedPredictorException {
		return evaluate(data, predictor, MetricFactory.getMetrics(predictor, DataUtils.checkDataType(data)==DataType.MULTI_CLASS));
	}

	/**
	 * Runs the cross validation, using given {@link com.arosbio.ml.metrics.Metric Metrics}.
	 * Note that an exception will be thrown in case a {@link com.arosbio.ml.metrics.Metric Metrics} 
	 * does not implement the correct interface for the given {@link com.arosbio.ml.interfaces.Predictor}.
	 * @param data The Dataset with data to be used for training and testing
	 * @param predictor The predictor to be used
	 * @param metrics A list of {@link com.arosbio.ml.metrics.Metric Metrics}
	 * @return A list of {@link com.arosbio.ml.metrics.Metric Metrics} (same as was given as parameter)
	 * @throws UnsupportedPredictorException If cross validation is not supported for the given {@link com.arosbio.ml.interfaces.Predictor} 
	 * @throws IllegalArgumentException Faulty arguments
	 * 
	 */
	public List<Metric> evaluate(Dataset data, Predictor predictor, List<? extends Metric> metrics)
			throws IllegalArgumentException, UnsupportedPredictorException  {
		
		if (metrics == null || metrics.isEmpty())
			throw new IllegalArgumentException("No metrics given");
		int numTestSplits = strategy.getNumberOfSplitsAndValidate(data);
		if (data.getNumRecords() < numTestSplits)
			throw new IllegalArgumentException("Number of test-train splits cannot be larger than number of records");
		if (predictor == null)
			throw new IllegalArgumentException("Predictor cannot be null");
		if (! supports(predictor))
			throw new UnsupportedPredictorException("Testing not supported for predictor of class " + predictor.getClass());

		Iterator<TestTrainSplit> splitsIterator = strategy.getSplits(data);

		// For each fold, train and predict dataset and collect metrics
		int split=1;

		// Check if proper mean +/- std should be calculated
		boolean useAggregation = calculateMeanAndStd && numTestSplits>1;
		List<Metric> usedMetrics = updateMetricsAndWrap(useAggregation, metrics);
		LOGGER.debug("Running evaluation of {} using mean +/- std: {}", predictor.getPredictorType(), useAggregation);


		while (splitsIterator.hasNext()) {

			TestTrainSplit currentSplit = splitsIterator.next();

			LOGGER.debug("Doing split {}/{} examples for validation={}, examples for training={}",
					split,numTestSplits,currentSplit.getTestSet().size(), currentSplit.getTrainingSet().getNumRecords());

			// Create inner problem for this fold to use in ACP
			Dataset innerProblem = currentSplit.getTrainingSet();

			Predictor foldPredictor = predictor.clone();
			foldPredictor.setSeed(strategy.getSeed());
			foldPredictor.train(innerProblem);

			if (useAggregation) {
				// Build up a list of metrics for the given test-split
				List<Metric> splitMetrics = getTestSplitMetrics(usedMetrics);
				
				// Run the test
				evaluateSplit(foldPredictor, currentSplit.getTestSet(), splitMetrics);
				// Pull out the results for the ones needing aggregation
				updateAggregatedMetrics(usedMetrics, splitMetrics);

			} else {
				evaluateSplit(foldPredictor, currentSplit.getTestSet(), metrics);
			}

			split++;
		}

		return usedMetrics;

	}

	private void evaluateSplit(Predictor predictor, Collection<DataRecord> testSet, List<? extends Metric> metrics) {
		int numSuccess=0,numFail=0;
		for (DataRecord ex: testSet) {
			try {
				if (predictor instanceof ConformalClassifier) {
					EvaluationUtils.evaluateExample((ConformalClassifier) predictor, ex, metrics);
				} else if (predictor instanceof ACPRegressor) {
					EvaluationUtils.evaluateExample((ACPRegressor) predictor, ex, metrics);
				} else if (predictor instanceof AVAPClassifier) {
					EvaluationUtils.evaluateExample((AVAPClassifier) predictor, ex, metrics);
				} else {
					throw new IllegalStateException("Got invalid predictor type {"+predictor.getPredictorType()+"} in TestRunner evaluateSplit");
				}
				numSuccess++;
			} catch (IllegalStateException e) {
				numFail++;
				LOGGER.debug("IllegalStateException running evaluateFold - should not happen",e);
				throw new RuntimeException("");
			} catch (Exception e) {
				numFail++;
				LOGGER.error("Failed to predict one example, current status for evaluateSplit #success={}, #fails={}",
						numSuccess,numFail,e);
				if (allowedFailureRatio <=0) {
					LOGGER.debug("Allowed failure ratio=0, failing directly");
					throw e;
				}
			}

		}
		double failRatio = ((double)numFail)/(numFail+numSuccess);
		if (failRatio>allowedFailureRatio) {
			LOGGER.error("Number of failures exceeded allowed failure ratio of {}, was={}",
					allowedFailureRatio,failRatio);
			throw new IllegalStateException("Failed predicting "+numFail + " out of "+(numFail+numSuccess) + " examples");
			
		}

	}

	private List<Metric> updateMetricsAndWrap(boolean useAgg, List<? extends Metric> input){

		// First update evaluation points (if applicable)
		if (evalPointsToInject != null && !evalPointsToInject.isEmpty()){
			MetricFactory.setEvaluationPoints(input, evalPointsToInject);
		} else {
			for (Metric m : input) {
				if (m instanceof PlotMetric) {
					if (((PlotMetric) m).getEvaluationPoints() == null || 
							((PlotMetric) m).getEvaluationPoints().isEmpty()) {
						((PlotMetric) m).setEvaluationPoints(DEFAULT_EVAL_POINTS);
					}
				}
			}
		}

		// handle wrapping of metrics into aggregation wrapper classes
		if (useAgg) {
			List<Metric> wrappedMetrics = new ArrayList<>();
			for (Metric m : input) {
				if (m instanceof SingleValuedMetric) {
					wrappedMetrics.add(new MetricAggregation<>((SingleValuedMetric)m));
				} else if (m instanceof PlotMetric){
					wrappedMetrics.add(new PlotMetricAggregation((PlotMetric)m));
				} else {
					// This will likely never occur - in case of some custom stuff?
					wrappedMetrics.add(m);
				}
			}
			return wrappedMetrics;
		} else {
			return new ArrayList<>(input);
		}
	}

	private List<Metric> getTestSplitMetrics(List<? extends Metric> metrics){
		List<Metric> testSplitMetrics = new ArrayList<>();
		for (Metric m : metrics){
			if (m instanceof MetricAggregation){
				testSplitMetrics.add(((MetricAggregation<?>) m).spawnNewMetricInstance());
			} else if (m instanceof PlotMetricAggregation){
				testSplitMetrics.add(((PlotMetricAggregation) m).spawnNewMetricInstance());
			} else {
				testSplitMetrics.add(m); 
			}
		}
		return testSplitMetrics;
	}

	private void updateAggregatedMetrics(List<Metric> aggMetrics, List<Metric> splitMetrics){
		

		for (int i=0; i<aggMetrics.size(); i++) {
			if (aggMetrics.get(i) instanceof MetricAggregation){
				// Single metric
				((MetricAggregation<?>)aggMetrics.get(i)).addSplitEval((SingleValuedMetric) splitMetrics.get(i));
			} else if (aggMetrics.get(i)instanceof PlotMetricAggregation){
				// Plot metric
				((PlotMetricAggregation)aggMetrics.get(i)).addSplitEval((PlotMetric)splitMetrics.get(i));
			} else {
				LOGGER.error("Tried to update aggregated metrics, but the metric was not of that type! instead it was: {}", aggMetrics.get(i));
				throw new RuntimeException("Invalid type of metric encountered during test-evaluation: " + aggMetrics.get(i).getName());
				// Do nothing
			}
		}
	}

	public static <T extends Metric> boolean metricSupportedByAlgorithm(T metric, MLAlgorithm alg) {
		if (alg instanceof Regressor) {
			return metric instanceof PointPredictionMetric;
		} else if (alg instanceof Classifier) {
			if (metric instanceof PointClassifierMetric) {
				return true;
			}
			if (alg instanceof PseudoProbabilisticClassifier) {
				return metric instanceof ProbabilisticMetric || metric instanceof ScoringClassifierMetric; 
			}
			if (alg instanceof ScoringClassifier) {
				return metric instanceof ScoringClassifierMetric; 
			}
			throw new IllegalArgumentException("Metric "+metric.getName() +" not supported by the algorithm");
		}
		throw new IllegalArgumentException("Algorithm "+alg.getName() +" not supported by testing framework");
	}

	public static boolean supports(Predictor predictor) {
		boolean supported = predictor instanceof ConformalClassifier ||predictor instanceof ACPRegressor|| predictor instanceof AVAPClassifier;
		
		LOGGER.debug("Was called with predictor of type: {} which is{} supported for testing",
				predictor.getClass(), supported? "" : " not");
		return supported;
	}

	public static class UnsupportedPredictorException extends RuntimeException {
		/**
		 * 
		 */
		private static final long serialVersionUID = -6778050838993355716L;

		public UnsupportedPredictorException(String msg) {
			super(msg);
		}

	}

	public List<Metric> evaluateRegressor(Dataset data, 
		Regressor algorithm) throws IllegalArgumentException {
			return evaluateRegressor(data, algorithm, MetricFactory.getRegressorMetrics());
		}

	/**
	 * Evaluate an {@link Regressor} algorithm using the specified {@link TestingStrategy}, a given {@link Dataset} and
	 * a list of {@link Metric Metrics}. 
	 * @param data A {@link Dataset} to use in the evaluation
	 * @param algorithm A {@link Regressor} algorithm to be evaluated
	 * @param metrics Metrics that should be of type {@link PointPredictionMetric}
	 * @return Either the same list of metrics given to {@code metrics} or those metrics wrapped in {@link com.arosbio.ml.metrics.MetricAggregation MetricAggregation } and {@link com.arosbio.ml.metrics.plots.PlotMetricAggregation PlotMetricAggregation} 
	 * instances, depending on the test-strategy and if mean+/- std should be returned
	 * @throws IllegalArgumentException In case of invalid arguments
	 */
	public List<Metric> evaluateRegressor(Dataset data, 
		Regressor algorithm, 
		List<? extends Metric> metrics) throws IllegalArgumentException {
		
		if (algorithm == null)
			throw new IllegalArgumentException("Algorithm cannot be null");

		// Validate metrics
		if (metrics == null || metrics.isEmpty())
			throw new IllegalArgumentException("No Metrics given");
		for (Metric m : metrics){
			List<String> invalidMetrics = new ArrayList<>();
			if (! (m instanceof PointPredictionMetric)){
				invalidMetrics.add(m.getName());
			}
			if (!invalidMetrics.isEmpty()){
				LOGGER.error("Failed evaluateRegressor due to invalid metrics: {}", invalidMetrics);
				throw new IllegalArgumentException("Invalid metrics for regressor algorithm: " + StringUtils.join(", ", invalidMetrics));
			}
		}

		Iterator<TestTrainSplit> testSplits = strategy.getSplits(data);

		// Evaluate the testing strategy
		int numTestSplits = strategy.getNumberOfSplitsAndValidate(data);
		int split=1;
		// Check if proper mean +/- std should be calculated
		boolean useAggregation = calculateMeanAndStd && numTestSplits>1;
		List<Metric> usedMetrics = updateMetricsAndWrap(useAggregation, metrics);

		//For each test-split, train and predict data set and collect metrics
		while (testSplits.hasNext()){
			try {
				TestTrainSplit currentSplit = testSplits.next();
				LOGGER.debug("Doing split {}/{} examples for validation={}, examples for training={}",
						split,numTestSplits,currentSplit.getTestSet().size(), currentSplit.getTrainingSet().getNumRecords());

				//Create inner problem for this fold to use in ACP
				List<DataRecord> foldDataset = currentSplit.getTrainingSet().getDataset();

				Regressor foldAlgorithm = algorithm.clone();
				foldAlgorithm.setSeed(strategy.getSeed());
				foldAlgorithm.train(foldDataset);

				List<Metric> testSplitMetrics = (useAggregation ? getTestSplitMetrics(usedMetrics) : usedMetrics);

				for (DataRecord test : currentSplit.getTestSet()) {
					double yHat = foldAlgorithm.predictValue(test.getFeatures());
					for (Metric m : testSplitMetrics) {
						if (m instanceof PointPredictionMetric)
							((PointPredictionMetric) m).addPrediction(test.getLabel(),yHat);
					}
				}
				if (useAggregation){
					updateAggregatedMetrics(usedMetrics, testSplitMetrics);
				}


				split++;

			} catch (IllegalStateException e) {
				// should only be thrown in case 
				LOGGER.debug("Failed fold {} in CV for simple MLAlgorithm",split, e);
				throw new RuntimeException(e.getMessage());
			}

		}

		return usedMetrics;
	}

	public List<Metric> evaluateClassifier(Dataset data, 
		Classifier algorithm) throws IllegalArgumentException {
			return evaluateClassifier(data, algorithm, MetricFactory.getClassifierMetrics(DataUtils.checkDataType(data)==DataType.MULTI_CLASS));
		}

	/**
	 * Evaluate a {@link Classifier} algorithm using the specified {@link TestingStrategy}, a given {@link Dataset} and
	 * a list of {@link Metric Metrics}. 
	 * @param data A {@link Dataset} to use in the evaluation
	 * @param algorithm A {@link Classifier} algorithm to be evaluated
	 * @param metrics Metrics that should be calculated
	 * @return Either the same list of metrics given to {@code metrics} or those metrics wrapped in {@link MetricAggregation} and {@link PlotMetricAggregation} instances, depending on the test-strategy and if mean+/- std should be returned
	 * @throws IllegalArgumentException In case of invalid arguments
	 */
	public List<Metric> evaluateClassifier(Dataset data, 
		Classifier algorithm, List<? extends Metric> metrics) throws IllegalArgumentException {
		
		if (algorithm == null)
			throw new IllegalArgumentException("Algorithm cannot be null");
		
			// Evaluate the testing strategy
		strategy.getNumberOfSplitsAndValidate(data);

		// Validate the metrics
		if (metrics == null || metrics.isEmpty())
			throw new IllegalArgumentException("No Metrics given");
		boolean requireProbabilities=false, requireStdPred=false, requireScores=false;
		for (Metric m : metrics) {
			if (m instanceof PointClassifierMetric)
				requireStdPred=true;
			else if (m instanceof ProbabilisticMetric && algorithm instanceof PseudoProbabilisticClassifier)
				requireProbabilities = true;
			else if (m instanceof ScoringClassifierMetric && algorithm instanceof ScoringClassifier)
				requireScores = true;
			else
				throw new IllegalArgumentException("Metric " + m.getName() + " not supported classifier of type " + algorithm.getName());
		}

		Iterator<TestTrainSplit> testSplits = strategy.getSplits(data);
		//For each fold, train and predict dataset and collect metrics
		int numTestSplits = strategy.getNumberOfSplitsAndValidate(data);
		int split=1;
		// Check if proper mean +/- std should be calculated
		boolean useAggregation = calculateMeanAndStd && numTestSplits>1;
		List<Metric> usedMetrics = updateMetricsAndWrap(useAggregation, metrics);

		while (testSplits.hasNext()){
			try {
				TestTrainSplit currentSplit = testSplits.next();
				LOGGER.debug("Doing split {}/{} examples for validation={}, examples for training={}",
						split,numTestSplits,currentSplit.getTestSet().size(), currentSplit.getTrainingSet().getNumRecords());

				//Create inner problem for this fold to use in ACP
				List<DataRecord> foldDataset = currentSplit.getTrainingSet().getDataset();

				Classifier foldAlgorithm = algorithm.clone();
				foldAlgorithm.train(foldDataset);

				List<Metric> testSplitMetrics = (useAggregation ? getTestSplitMetrics(usedMetrics) : usedMetrics);

				for (DataRecord test: currentSplit.getTestSet()) {
					int observedLabel = (int) test.getLabel();
					// Predict the stuff
					Map<Integer, Double> probabilities = null;
					Map<Integer, Double> scores = null;
					int predictedLabel = -Integer.MAX_VALUE;

					if (requireProbabilities) {
						probabilities = ((PseudoProbabilisticClassifier)foldAlgorithm).predictProbabilities(test.getFeatures());
					} 
					if (requireScores) {
						scores = ((ScoringClassifier) foldAlgorithm).predictScores(test.getFeatures());
					}
					if (requireStdPred) {
						predictedLabel = foldAlgorithm.predictClass(test.getFeatures());
					}

					for (Metric builder : testSplitMetrics) {
						if (builder instanceof PointClassifierMetric)
							((PointClassifierMetric) builder).addPrediction(observedLabel, predictedLabel);
						else if (builder instanceof ProbabilisticMetric && algorithm instanceof PseudoProbabilisticClassifier)
							((ProbabilisticMetric)builder).addPrediction(observedLabel, probabilities);
						else 
							((ScoringClassifierMetric)builder).addPrediction(observedLabel, scores);
					}
				}

				if (useAggregation){
					updateAggregatedMetrics(usedMetrics, testSplitMetrics);
				}

				split++;
			} catch (IllegalStateException e) {
				// should only be thrown in case 
				LOGGER.debug("Failed fold {} in CV for Classifier algorithm",split, e);
				throw new RuntimeException(e.getMessage());
			}

		}

		return usedMetrics;
	}

}

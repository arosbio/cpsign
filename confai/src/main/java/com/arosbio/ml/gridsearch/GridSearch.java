/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.gridsearch;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.LazyListsPermutationIterator;
import com.arosbio.commons.MathUtils;
import com.arosbio.commons.Stopwatch;
import com.arosbio.commons.StringUtils;
import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.commons.mixins.ResourceAllocator;
import com.arosbio.data.DataUtils;
import com.arosbio.data.DataUtils.DataType;
import com.arosbio.data.Dataset;
import com.arosbio.data.MissingDataException;
import com.arosbio.io.CollectionsWriter;
import com.arosbio.io.DebugWriter;
import com.arosbio.io.IOUtils;
import com.arosbio.ml.algorithms.Classifier;
import com.arosbio.ml.algorithms.MLAlgorithm;
import com.arosbio.ml.algorithms.Regressor;
import com.arosbio.ml.cp.ConformalPredictor;
import com.arosbio.ml.gridsearch.utils.GSResComparator;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.MetricAggregation;
import com.arosbio.ml.metrics.MetricFactory;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.cp.CPAccuracy;
import com.arosbio.ml.metrics.cp.ConfidenceDependentMetric;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.ml.testing.TestingStrategy;
import com.arosbio.ml.vap.VennABERSPredictor;

/**
 * <code>GridSearch</code> takes a <code>Dataset</code> and the chosen
 * {@link com.arosbio.ml.interfaces.Predictor Predictor}
 * and performs an exhaustive search for the
 * {@link com.arosbio.commons.config.Configurable.ConfigParameter
 * ConfigParameter}s
 * (available parameters depend on the predictor/NCM/scoring algorithm that is
 * used). For instance some
 * NCMs in ICPRegressor uses an error-model, and the parameters can be set
 * independently from
 * the scoring model. Thus the number of possible combinations can be very
 * large!
 * <p>
 * The {@link com.arosbio.ml.interfaces.Predictor Predictor} has a default
 * {@link com.arosbio.ml.metrics.SingleValuedMetric SingleValuedMetric}
 * that is used if not specified.
 * Note that only a single confidence can be used for the evaluation, so it
 * might be good to consider metrics that
 * do not depend on the confidence.
 * 
 * Another important parameter of {@link GridSearch} is the
 * {@code tolerance} (Conformal Prediction only). The tolerance controls
 * how much the accuracy is tolerated to differ compared 
 * to the specified {@code confidence}. Conformal Predictors
 * are meant to give the correct results in e.g. 70 % of the predictions if
 * confidence is set to 0.7, but that is only guaranteed on average for a
 * large test set, meaning that some times the output might be lower or higher
 * than set confidence level. The tolerance will allow for a discrepancy
 * in the accuracy of the Conformal Predictor (estimated by the
 * {@link com.arosbio.ml.testing.TestingStrategy TestingStrategy}).
 * The default {@code tolerance} is set to 0.05, e.g.
 * if confidence is set to 0.7, the Grid Search will accept results with
 * accuracy of 0.7 - 0.05 = 0.65 at worst. For Venn-ABERS predictors the
 * accuracy is not checked so the {@code tolerance} parameter is not used.
 * Note that {@code tolerance} can only be in the range [0..1].
 * 
 * 
 * @author Aros Bio AB
 * @author Staffan Arvidsson McShane
 *
 */
public class GridSearch {

	private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(GridSearch.class);

	public static final double MIN_ALLOWED_TOLERANCE = 0.0, MAX_ALLOWED_TOLERANCE = 1.0;

	public static enum EvalStatus {
		IN_PROGRESS("in progress"), VALID("valid"), NOT_VALID("not valid"), FAILED("failed");

		private final String textRep;

		private EvalStatus(String textRep) {
			this.textRep = textRep;
		}

		public String toString() {
			return textRep;
		}
	}

	public static class GSResult { 

		private final Map<String, Object> parameters;
		private final double result;
		private final SingleValuedMetric optimizationType;
		private final long runtimeMS;
		private final List<SingleValuedMetric> secondaryMetrics;
		private final EvalStatus status;
		private final String errorMessage;

		private GSResult(Builder b){
			this.parameters = Objects.requireNonNull(b.parameters);
			this.result = b.result;
			this.optimizationType = Objects.requireNonNull(b.optimizationType);
			this.runtimeMS = b.runtimeMS;
			this.secondaryMetrics = b.secondaryMetrics;
			this.status = b.status;
			this.errorMessage = b.errorMessage;
		}

		static class Builder {
			private Map<String, Object> parameters;
			private double result;
			private SingleValuedMetric optimizationType;
			private long runtimeMS;
			private List<SingleValuedMetric> secondaryMetrics;
			private EvalStatus status;
			private String errorMessage;

			public static Builder success(Map<String, Object> params, 
					double optimizationResult, 
					SingleValuedMetric type,
					long runtime) {
					
				Builder b = new Builder();
				b.parameters = params;
				b.result = optimizationResult;
				b.optimizationType = type;
				b.runtimeMS = runtime;
				b.status = EvalStatus.VALID;
				return b;
			}

			public static Builder failed(Map<String, Object> params, 
					SingleValuedMetric optMetric, 
					EvalStatus status,
					String error) {
				Builder b = new Builder();
				b.parameters = params;
				b.optimizationType = optMetric;
				b.status = status;
				b.errorMessage = error;
				return b;
			}

			public Builder secondary(List<SingleValuedMetric> metrics){
				this.secondaryMetrics = metrics;
				return this;
			}

			public GSResult build(){
				return new GSResult(this);
			}

		}

		public EvalStatus getStatus() {
			return status;
		}

		public SingleValuedMetric getOptimizationMetric() {
			return optimizationType;
		}

		public List<SingleValuedMetric> getSecondaryMetrics() {
			return secondaryMetrics;
		}

		public Map<String, Object> getParams() {
			return parameters;
		}

		public double getResult() {
			return result;
		}

		/**
		 * Runtime in milliseconds
		 * 
		 * @return the runtime
		 */
		public long getRuntime() {
			return runtimeMS;
		}

		public String toString() {
			return String.format("GSResult using metric %s: %s, runtime: %sms, params: %s",
					optimizationType.getName(),
					(status == EvalStatus.VALID ? result : status.textRep),
					runtimeMS,
					parameters);
		}

		/**
		 * Returns error message (if any) that was encountered during the run. Empty
		 * string if no exceptions were thrown
		 * 
		 * @return Error message or empty String
		 */
		public String getErrorMessage() {
			return errorMessage != null ? errorMessage : "";
		}
	}

	private final Writer customResultsWriter;

	// testing settings
	private final TestingStrategy testStrategy;
	private final boolean calcMeanAndSD;
	private final SingleValuedMetric explicitMetric;
	private final List<SingleValuedMetric> secondaryMetrics;

	private final double confidence;
	private final double tolerance;
	private final int maxNumGSresults;

	private Stopwatch timer = new Stopwatch();

	private GridSearch(Builder builder) {
		customResultsWriter = builder.customWriter;
		if (builder.testStrategy == null)
			throw new IllegalArgumentException("Must specify a testing strategy");
		testStrategy = builder.testStrategy;
		calcMeanAndSD = builder.computeMeanAndSD;
		explicitMetric = builder.optMetric;
		secondaryMetrics = builder.secondaryMetrics;

		confidence = builder.confidence;
		tolerance = builder.tolerance;
		maxNumGSresults = builder.maxNumGSresults;
	}

	/**
	 * A mutable builder object. Calls will return the reference to the same
	 * instance, with a fluid API facilitating chaining of method calls. Uses 10-fold CV 
	 * as the default {@link TestingStrategy}.
	 */
	public static class Builder {
		private TestingStrategy testStrategy = new KFoldCV(); 
		private boolean computeMeanAndSD = true;
		private SingleValuedMetric optMetric;
		private List<SingleValuedMetric> secondaryMetrics;
		private Writer customWriter;
		private double confidence = ConfidenceDependentMetric.DEFAULT_CONFIDENCE;
		private double tolerance = 0.05;
		private int maxNumGSresults = 10;

		public Builder testStrategy(TestingStrategy strategy) {
			this.testStrategy = strategy;
			return this;
		}

		public TestingStrategy testStrategy() {
			return testStrategy;
		}

		/**
		 * Set the metric that should be used for determining the best model
		 * 
		 * @param metric the metric
		 * @return the same Builder object
		 */
		public Builder optimizationMetric(SingleValuedMetric metric) {
			this.optMetric = metric;
			return this;
		}

		/**
		 * Set the metric that should be used for determining the best model
		 * 
		 * @param metric the metric
		 * @return the same Builder object
		 */
		public Builder optMetric(SingleValuedMetric metric) {
			this.optMetric = metric;
			return this;
		}

		/**
		 * Set the metric that should be used for determining the best model
		 * 
		 * @param metric the metric
		 * @return the same Builder object
		 */
		public Builder evaluationMetric(SingleValuedMetric metric) {
			this.optMetric = metric;
			return this;
		}

		/**
		 * Allows to evaluate the parameters using additional metrics, not only the
		 * evaluation metric that is used for picking the best evaluation strategy
		 * 
		 * @param metrics a list of metrics
		 * @return the reference of the calling instance (fluid API)
		 */
		public Builder secondaryMetrics(List<SingleValuedMetric> metrics) {
			this.secondaryMetrics = new ArrayList<>(metrics);
			return this;
		}

		public List<SingleValuedMetric> secondaryMetrics() {
			return this.secondaryMetrics;
		}

		/**
		 * Allows to evaluate the parameters using additional metrics, not only the
		 * evaluation metric that is used for picking the best evaluation strategy
		 * 
		 * @param metrics a list of metrics
		 * @return the same Builder object
		 */
		public Builder secondaryMetrics(SingleValuedMetric... metrics) {
			this.secondaryMetrics = new ArrayList<>(Arrays.asList(metrics));
			return this;
		}

		/**
		 * Set the desired confidence of the internal cross-validation (not always
		 * applicable)
		 * 
		 * @param confidence the confidence, should be in range [0..1]
		 * @return the same Builder object
		 * @throws IllegalArgumentException If the confidence given is not allowed
		 */
		public Builder confidence(double confidence) {
			if (confidence < 0 || confidence > 1)
				throw new IllegalArgumentException("Confidence must be within the range [0..1]");
			this.confidence = confidence;
			return this;
		}

		/**
		 * Setter for the tolerance for the validity of the model
		 * 
		 * @param tol Allowed tolerance for validity of the model range [0..1]
		 * @return the reference of the calling instance (fluid API)
		 */
		public Builder tolerance(double tol) {
			if (tolerance < MIN_ALLOWED_TOLERANCE || tolerance > MAX_ALLOWED_TOLERANCE)
				throw new IllegalArgumentException(String.format("Parameter tolerance must be in range [%s..%s]",
						MIN_ALLOWED_TOLERANCE, MAX_ALLOWED_TOLERANCE));
			this.tolerance = tol;
			return this;
		}

		public Builder maxNumResults(int max) {
			this.maxNumGSresults = max;
			return this;
		}

		public Builder loggingWriter(Writer output) {
			this.customWriter = output;
			return this;
		}

		public Builder computeMeanSD(boolean compute) {
			this.computeMeanAndSD = compute;
			return this;
		}

		public GridSearch build() {
			return new GridSearch(this);
		}

	}

	public SingleValuedMetric getEvaluationMetric() {
		return explicitMetric;
	}

	public SingleValuedMetric getOptimizationMetric() {
		return explicitMetric;
	}

	public List<SingleValuedMetric> getSecondaryMetrics() {
		return secondaryMetrics;
	}

	public TestingStrategy getTestingStrategy() {
		return this.testStrategy;
	}

	/**
	 * Get the confidence used for internal cross validation
	 * 
	 * @return the confidence
	 */
	public double getConfidence() {
		return confidence;
	}

	/**
	 * Getter for the tolerance for the validity of the model
	 * 
	 * @return the tolerance
	 */
	public double getTolerance() {
		return tolerance;
	}

	private static void verifyGridParameters(Map<String, List<?>> grid, Configurable predictor) {
		List<ConfigParameter> paramList = predictor.getConfigParameters();
		List<String> allowedParamNames = new ArrayList<>();
		for (ConfigParameter p : paramList) {
			allowedParamNames.addAll(p.getNames());
		}
		LOGGER.debug("All possible parameters: {}, given parameters: {}", allowedParamNames, grid.keySet());

		Set<String> nonOkParams = new HashSet<>();
		for (String givenParamName : grid.keySet()) {
			if (!CollectionUtils.containsIgnoreCase(allowedParamNames, givenParamName)) {
				nonOkParams.add(givenParamName);
			}
		}

		if (!nonOkParams.isEmpty()) {
			LOGGER.debug("Found extra parameters that is not valid: {}", nonOkParams);
			throw new IllegalArgumentException("Following parameters are not used/recognized: " + nonOkParams);
		}

	}

	private final static String WARNING_MESSAGE = "WARNING: Optimal parameters found at border of the grid, true optimal parameters might be outside the search grid. Parameters affected: ";
	private final static String WARNING_NO_VALID_RESULTS = "WARNING: no parameter combinations produced valid models";

	private String getWarning(Map<String, Object> optimalParams, Map<String, List<?>> grid) {
		StringBuilder warningBuilder = new StringBuilder();

		for (String p : optimalParams.keySet()) {
			// Cannot be on the boarder unless 3 points given
			if (grid.get(p).size() < 3)
				continue;

			// If not numeric value - skip (how to do it for enum/sampling etc?
			if (!(optimalParams.get(p) instanceof Number))
				continue;

			Pair<List<Number>, List<Object>> lists = getOrderedListAndErronious(grid.get(p));

			if (!lists.getRight().isEmpty()) {
				// If the list contains invalid parameters - write that as error
				if (warningBuilder.length() == 0) {
					warningBuilder.append(WARNING_MESSAGE);
				} else {
					warningBuilder.append(", ");
				}
				warningBuilder.append(p);
				warningBuilder.append(" (parameter list contained invalid values: ");
				warningBuilder.append(StringUtils.toStringNoBrackets(lists.getRight()));
				warningBuilder.append(')');
			} else {
				// All valid values, check if parameter on the boarder
				Object optimalVal = optimalParams.get(p);
				int index = lists.getLeft().indexOf(optimalVal);
				if (index == 0 || index == (grid.get(p).size() - 1)) {
					// optimal value was on the boarder!
					if (warningBuilder.length() == 0) {
						warningBuilder.append(WARNING_MESSAGE);
					} else {
						warningBuilder.append(", ");
					}
					warningBuilder.append(p);
				}
			}

		}
		return (warningBuilder.length() > 0 ? warningBuilder.toString() : null);
	}

	protected static Pair<List<Number>, List<Object>> getOrderedListAndErronious(List<?> unchecked) {
		List<Number> valid = new ArrayList<>();
		List<Object> illegal = new ArrayList<>();
		for (Object o : unchecked) {
			if (o instanceof Number) {
				valid.add((Number) o);
			} else {
				illegal.add(o);
			}
		}
		Collections.sort(valid, new NumberComparator());
		return ImmutablePair.of(valid, illegal);
	}

	protected static class NumberComparator implements Comparator<Number> {

		@Override
		public int compare(Number o1, Number o2) {
			// if both are integers (not using short
			if (isInteger(o1) && isInteger(o2))
				return Long.compare(o1.longValue(), o2.longValue());
			// Default is to use Double
			return Double.compare(o1.doubleValue(), o2.doubleValue());

		}

		private static boolean isInteger(Number n) {
			return n instanceof Short || n instanceof Integer || n instanceof Long;
		}

	}

	private static Map<String, List<?>> getDefaultParamGrid(Configurable predictor) {
		List<ConfigParameter> params = predictor.getConfigParameters();
		Map<String, List<?>> paramGrid = new HashMap<>();

		for (ConfigParameter p : params) {
			if (p.getDefaultGrid() != null && !p.getDefaultGrid().isEmpty()) {
				paramGrid.put(p.getNames().get(0), p.getDefaultGrid());
			}
		}
		LOGGER.debug("Setting up default parameter grid: {}", paramGrid);
		return paramGrid;
	}

	private Iterator<Map<String, Object>> getParametersIterator(
			Map<String, List<? extends Object>> parameterGrid,
			Configurable predictor) {

		if (parameterGrid == null || parameterGrid.isEmpty()) {
			LOGGER.debug("No explicit grid was given - compiling the default grid!");
			parameterGrid = getDefaultParamGrid(predictor);
		}

		return new ParameterCombinationsIterator(parameterGrid);
	}

	private class ParameterCombinationsIterator implements Iterator<Map<String, Object>> {

		private Map<String, List<?>> grid;
		private List<String> paramOrder;

		private LazyListsPermutationIterator permutationIterator;

		public ParameterCombinationsIterator(Map<String, List<?>> grid) {
			this.grid = grid;
			this.paramOrder = new ArrayList<>(grid.keySet());
			List<Integer> paramSizes = new ArrayList<>();
			for (String p : paramOrder) {
				paramSizes.add(grid.get(p).size());
			}
			this.permutationIterator = new LazyListsPermutationIterator(paramSizes);
		}

		@Override
		public boolean hasNext() {
			return permutationIterator.hasNext();
		}

		@Override
		public Map<String, Object> next() {
			List<Integer> combo = permutationIterator.next();

			// Get the current parameters
			Map<String, Object> params = new HashMap<>();
			for (int i = 0; i < paramOrder.size(); i++) {
				String paramName = paramOrder.get(i);
				params.put(paramName, grid.get(paramName).get(combo.get(i)));
			}

			return params;
		}

	}

	@SuppressWarnings("resource")
	private Writer configAndGetOutput() {
		if (customResultsWriter != null) {
			LOGGER.debug("GridSearch will write results to custom writer + debug");
			return new CollectionsWriter(Arrays.asList(customResultsWriter, new DebugWriter(this.getClass())));
		} else {
			LOGGER.debug("No custom writer set, printing results to debug");
			return new DebugWriter(this.getClass());
		}
	}

	/*
	 * =============================================================================
	 * DO GRID SEARCH
	 * =============================================================================
	 */

	public GridSearchResult search(Dataset problem,
			Predictor predictor)
			throws IllegalArgumentException, IOException, GridSearchException {
		return search(problem, predictor,
				getDefaultParamGrid(predictor));
	}

	public GridSearchResult search(Dataset problem,
			Predictor predictor,
			Map<String, List<?>> parameterGrid)
			throws IllegalArgumentException, IOException, GridSearchException {

		// Validation
		if (testStrategy == null)
			throw new IllegalArgumentException("TestingStrategy must be set");
		testStrategy.getNumberOfSplitsAndValidate(problem);

		SingleValuedMetric optimizationMetric = explicitMetric != null ? explicitMetric
				: predictor.getDefaultOptimizationMetric();
		if (parameterGrid == null)
			throw new IllegalArgumentException("Parameter grid was empty!");
		verifyGridParameters(parameterGrid, predictor);

		LOGGER.debug(
				"Running grid search with predictor of type: {} with optimization metric: {} and the following param grid: {}",
				predictor.getClass(), optimizationMetric.getName(), parameterGrid);

		boolean foundValidResult = false;

		// Metrics
		List<SingleValuedMetric> metrics = getAllMetrics(optimizationMetric);

		// Add accuracy-metric if CP & Add confidence as field
		if (predictor instanceof ConformalPredictor) {
			// Check so it's not added already
			boolean isPresent = false;
			for (Metric m : metrics) {
				if (m instanceof CPAccuracy) {
					isPresent = true;
					break;
				}
			}
			if (!isPresent) {
				metrics.add(new CPAccuracy(confidence));
			}
		}

		verifyMetricsOfCorrectType(metrics, predictor);

		Iterator<Map<String, Object>> paramsIterator = getParametersIterator(parameterGrid, predictor);
		GSResComparator sorter = new GSResComparator(predictor);

		List<GSResult> results = null;
		if (maxNumGSresults > 0)
			results = new ArrayList<>(maxNumGSresults * 2);
		else
			results = new ArrayList<>((int) Math.pow(10, parameterGrid.size())); // Assume 10 values for each parameter

		TestRunner runner = new TestRunner.Builder(testStrategy).calcMeanAndStd(calcMeanAndSD).build();

		// Configure the output logging
		Writer resWriter = configAndGetOutput();

		try (
				GridResultCSVWriter resultPrinter = new GridResultCSVWriter.Builder()
						.rank(false)
						.confidence((predictor instanceof ConformalPredictor ? confidence : null))
						.params(parameterGrid.keySet())
						.log(resWriter).build()) {

			EvalStatus currentStatus = EvalStatus.IN_PROGRESS;
			Map<String, Object> currentParams = new HashMap<>();
			List<? extends Metric> paramResult = null;

			while (paramsIterator.hasNext()) {
				currentParams = paramsIterator.next();
				String errorMsg = "-";
				currentStatus = EvalStatus.IN_PROGRESS;

				try {
					LOGGER.debug("Running with parameters: {}", currentParams);
					// Clone the metrics for this run
					paramResult = cloneMetrics(metrics);
					// Start timer, before things can fail

					timer.start();
					// Set the new parameters to tune
					predictor.setConfigParameters(currentParams);

					paramResult = runner.evaluate(problem, predictor, paramResult);

					currentStatus = getStatus(predictor, paramResult);

					if (currentStatus == EvalStatus.VALID) {
						foundValidResult = true;
					}
				} catch (MissingDataException e) {
					LOGGER.debug("Got MissingDataException in GridSearch - failing!");
					throw new MissingDataException(
							"Failed performing grid search of parameter values - input data contains missing features - please revise the pre-processing of data");
				} catch (Exception | Error e) {
					currentStatus = EvalStatus.FAILED;
					errorMsg = e.getMessage();
					if (errorMsg == null)
						errorMsg = e.getClass().getName() + " exception";
					LOGGER.debug("Failed running GS with the following parameters: {}, exception: {}",
							currentParams, LoggerUtils.getShortExceptionMsg(e));
				} finally {
					// Stop the timer for this param combo
					timer.stop();
					// Update results
					if (paramResult != null){
						GSResult.Builder builder = (currentStatus != EvalStatus.FAILED
								? GSResult.Builder.success(currentParams, ((SingleValuedMetric) paramResult.get(0)).getScore(),
										((SingleValuedMetric) paramResult.get(0)), timer.elapsedTimeMillis())
								: GSResult.Builder.failed(currentParams, ((SingleValuedMetric) paramResult.get(0)), currentStatus,
										errorMsg));
						if (paramResult.size() > 1) {
							builder.secondary(getSecondaryMetrics(paramResult));
						}
						GSResult r = builder.build();
						results.add(r);
					
						// sort and remove results that are of no interest
						updateResults(results,sorter);

						// Print the current parameters and the metrics
						resultPrinter.printRecord(r);
					}

					// clear allocations from current model
					predictor.releaseResources();

				}
			}
		}

		IOUtils.closeQuietly(resWriter);

		// Fix the results - sort and get correct size
		if (foundValidResult) {
			Collections.sort(results,sorter);
			if (maxNumGSresults > 0 && results.size() > maxNumGSresults) {
				results.subList(maxNumGSresults, results.size()).clear();
			}

			// Set the optimal results for the predictor implementation
			predictor.setConfigParameters(results.get(0).getParams());

			GridSearchResult.Builder res = new GridSearchResult.Builder(results, optimizationMetric.clone());

			// Set warnings
			String warning = getWarning(results.get(0).parameters, parameterGrid);

			if (warning != null) {
				LOGGER.debug(warning);
				res.warning(warning);
			}

			return res.build();
		} else {
			LOGGER.debug("No valid parameter combinations found in grid-search!");
			return new GridSearchResult.Builder(results, optimizationMetric.clone())
				.warning(WARNING_NO_VALID_RESULTS).build();
		}

	}

	public GridSearchResult search(Dataset data,
			MLAlgorithm alg,
			Map<String, List<?>> parameterGrid)
			throws IllegalArgumentException, IOException, GridSearchException {

		if (!(alg instanceof Regressor || alg instanceof Classifier))
			throw new IllegalArgumentException("Algorithm " + alg.getName() + " not supported by GridSearch");
		// Validation
		if (testStrategy == null)
			throw new IllegalArgumentException("TestingStrategy must be set");
		testStrategy.getNumberOfSplitsAndValidate(data);

		SingleValuedMetric optimizationMetric = null;
		if (explicitMetric != null) {
			optimizationMetric = explicitMetric;
		} else {
			DataType type = DataUtils.checkDataType(data);
			for (Metric m : MetricFactory.getMetrics(alg, type == DataType.MULTI_CLASS ? true : false)) {
				if (m instanceof SingleValuedMetric) {
					optimizationMetric = (SingleValuedMetric) m;
					break;
				}
			}
		}
		if (optimizationMetric == null){
			throw new IllegalArgumentException("No metric given to optimize hyperparameters for");
		}
		if (parameterGrid == null){
			throw new IllegalArgumentException("Parameter grid was empty!");
		}
			
		verifyGridParameters(parameterGrid, alg);

		LOGGER.debug("Running grid search with predictor of type: {} with optimization metric: {} and grid: {}",
				alg.getClass(), optimizationMetric.getName(), parameterGrid);

		// Metrics
		List<SingleValuedMetric> metrics = getAllMetrics(optimizationMetric);
		boolean foundValid = false;

		verifyMetricsOfCorrectType(metrics, alg);

		Iterator<Map<String, Object>> paramsIterator = getParametersIterator(parameterGrid, alg);
		GSResComparator sorter = new GSResComparator(alg);

		List<GSResult> results = null;
		if (maxNumGSresults > 0)
			results = new ArrayList<>(maxNumGSresults * 2);
		else
			results = new ArrayList<>((int) Math.pow(10, parameterGrid.size())); // Assume 10 values for each parameter

		TestRunner runner = new TestRunner.Builder(testStrategy).calcMeanAndStd(calcMeanAndSD).build();

		Writer resWriter = configAndGetOutput();

		try (
				GridResultCSVWriter resultPrinter = new GridResultCSVWriter.Builder()
						.rank(false)
						.skipConfidence()
						.params(parameterGrid.keySet())
						.log(resWriter).build();) {

			Map<String, Object> currentParams = new HashMap<>();
			List<Metric> paramResult = null;
			List<SingleValuedMetric> inputMetrics = null;
			String errorMsg = null;

			while (paramsIterator.hasNext()) {
				currentParams = paramsIterator.next();
				paramResult = null;
				errorMsg = null;

				try {
					LOGGER.debug("Running with parameters: {}", currentParams);
					// Clone the metrics for this run
					inputMetrics = cloneMetrics(metrics);

					// Start timer, before things can fail
					timer.start();
					// Set the new parameters to tune
					MLAlgorithm pAlg = alg.clone();
					pAlg.setConfigParameters(currentParams);

					if (alg instanceof Regressor)
						paramResult = runner.evaluateRegressor(data, (Regressor) pAlg, inputMetrics);
					else
						paramResult = runner.evalulateClassifier(data, (Classifier) pAlg, inputMetrics);

					foundValid = true;
				} catch (MissingDataException e) {
					LOGGER.debug("Got MissingDataException in GridSearch - failing!");
					errorMsg = e.getMessage();
					throw new MissingDataException(
							"Failed performing grid search of parameter values - input data contains missing features - please revise the pre-processing of data");
				} catch (Exception | Error e) {
					LOGGER.debug("Failed running GS with the following parameters: {}, exception: {}", currentParams,
							LoggerUtils.getShortExceptionMsg(e));
					errorMsg = e.getMessage();
				} finally {
					timer.stop(); // Stop current timer!

					// Update results
					GSResult.Builder builder = errorMsg == null && paramResult != null
							? GSResult.Builder.success(currentParams,
									((SingleValuedMetric) paramResult.get(0)).getScore(),
									((SingleValuedMetric) paramResult.get(0)),
									timer.elapsedTimeMillis())
							: GSResult.Builder.failed(currentParams, optimizationMetric, EvalStatus.FAILED, errorMsg);

					// Set secondary metrics
					if (paramResult != null && paramResult.size() > 1) {
						List<SingleValuedMetric> secondary = new ArrayList<>();
						for (Metric m : paramResult.subList(1, paramResult.size())) {
							if (m instanceof SingleValuedMetric)
								secondary.add((SingleValuedMetric) m);
						}
						builder.secondary(secondary);
					} else if (inputMetrics!= null && inputMetrics.size() > 1) {
						// If the run failed, the paramResult == null
						builder.secondary(inputMetrics.subList(1, inputMetrics.size()));
					}
					GSResult r = builder.build();
					results.add(r);

					updateResults(results,sorter);

					// Print the current parameters and the metrics
					resultPrinter.printRecord(r);

					// Release resources
					if (alg instanceof ResourceAllocator) {
						((ResourceAllocator) alg).releaseResources();
						LOGGER.debug("released resources from ML model");
					}
				}
			}
		}

		IOUtils.closeQuietly(resWriter);

		// Fix the results - sort and get correct size
		if (foundValid) {
			Collections.sort(results, sorter);
			if (maxNumGSresults > 0 && results.size() > maxNumGSresults) {
				results.subList(maxNumGSresults, results.size()).clear();
			}

			// Set the optimal results for the predictor implementation
			alg.setConfigParameters(results.get(0).getParams());

			GridSearchResult.Builder res = new GridSearchResult.Builder(results, optimizationMetric.clone());

			// Set warnings
			String warning = getWarning(results.get(0).parameters, parameterGrid);

			if (warning != null) {
				LOGGER.debug(warning);
				res.warning(warning);
			}

			return res.build();
		} else {
			LOGGER.debug("No valid parameter combinations found in grid-search!");
			return new GridSearchResult.Builder(results, optimizationMetric.clone()).warning(WARNING_NO_VALID_RESULTS).build();
		}

	}

	private void updateResults(List<GSResult> results,Comparator<GSResult> sorter) {
		if (maxNumGSresults > 0 && results.size() >= 2 * maxNumGSresults) {
			Collections.sort(results,sorter);
			results.subList(maxNumGSresults, results.size()).clear();
		}
	}

	private List<SingleValuedMetric> getSecondaryMetrics(List<?> ms) {
		if (ms == null || ms.size() < 2)
			return new ArrayList<>();
		List<SingleValuedMetric> s = new ArrayList<>();
		for (int i = 1; i < ms.size(); i++) {
			if (ms.get(i) instanceof SingleValuedMetric) {
				s.add((SingleValuedMetric) ms.get(i));
			}
		}
		return s;
	}

	private EvalStatus getStatus(Predictor predictor, List<? extends Metric> paramResult) {
		if (predictor instanceof VennABERSPredictor) {
			// Always valid - no way of telling it's not
			return EvalStatus.VALID;
		} else if (getAccuracy(paramResult) - confidence > -tolerance) {
			return EvalStatus.VALID;
		} 
		// Otherwise it was invalid
		return EvalStatus.NOT_VALID;
	}

	private static void verifyMetricsOfCorrectType(List<SingleValuedMetric> metrics, Predictor predictor)
			throws IllegalArgumentException {
		List<String> notOKmetrics = new ArrayList<>();

		for (SingleValuedMetric m : metrics) {
			if (!TestRunner.metricSupportedByPredictor(m, predictor)) {
				notOKmetrics.add(m.getName());
			}
		}

		if (!notOKmetrics.isEmpty()) {
			LOGGER.debug("Grid search for predictor of type {} not OK metrics: {}", 
					predictor.getPredictorType(),
					notOKmetrics);
			throw new IllegalArgumentException(
					"Metrics not supported by " + predictor.getPredictorType() + ": " + notOKmetrics);
		}
	}

	private static void verifyMetricsOfCorrectType(List<SingleValuedMetric> metrics, MLAlgorithm alg)
			throws IllegalArgumentException {
		List<String> notOKmetrics = new ArrayList<>();

		for (SingleValuedMetric m : metrics) {
			if (!TestRunner.metricSupportedByAlgorithm(m, alg)) {
				notOKmetrics.add(m.getName());
			}
		}

		if (!notOKmetrics.isEmpty()) {
			LOGGER.debug("Grid search for algorithm of type {} not OK metrics: {}", alg.getName(), notOKmetrics);
			throw new IllegalArgumentException(
					String.format("Metrics not supported by %s: %s", alg.getName(), notOKmetrics));
		}
	}

	private static double getAccuracy(List<? extends Metric> metrics) {
		for (Metric m : metrics) {
			if (m instanceof CPAccuracy) {
				return ((CPAccuracy) m).getScore();
			} else if (m instanceof MetricAggregation) {
				SingleValuedMetric underlyingMetric = ((MetricAggregation) m).spawnNewMetricInstance();
				if (underlyingMetric instanceof CPAccuracy) {
					return ((MetricAggregation) m).getScore();
				}
			}
		}
		return -1;
	}

	// @SuppressWarnings("unchecked")
	private static List<SingleValuedMetric> cloneMetrics(List<SingleValuedMetric> list) {
		// <M extends Metric> (M)
		List<SingleValuedMetric> clones = new ArrayList<>(list.size());
		for (SingleValuedMetric m : list)
			clones.add(m.clone());
		return clones;
	}

	

	private List<SingleValuedMetric> getAllMetrics(SingleValuedMetric opt) {
		List<SingleValuedMetric> l = new ArrayList<>();
		l.add(opt);
		if (secondaryMetrics != null) {
			for (SingleValuedMetric m : secondaryMetrics) {
				if (m.getClass().equals(opt.getClass())) {
					// If same class - check confidence is different - otherwise skip it
					if (m instanceof ConfidenceDependentMetric) {
						double c1 = ((ConfidenceDependentMetric) m).getConfidence();
						double c2 = ((ConfidenceDependentMetric) opt).getConfidence();

						if (!MathUtils.equals(c1, c2)) {
							l.add(m);
						}
					}
				} else {
					l.add(m);
				}
			}
		}
		return l;
	}

}

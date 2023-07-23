/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Formatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.MathUtils;
import com.arosbio.commons.Stopwatch;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.BooleanConfig;
import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.commons.config.DescribedConfig;
import com.arosbio.commons.config.EnumConfig;
import com.arosbio.commons.config.ImplementationConfig;
import com.arosbio.commons.config.IntegerConfig;
import com.arosbio.commons.config.NumericConfig;
import com.arosbio.commons.config.StringConfig;
import com.arosbio.commons.mixins.HasID;
import com.arosbio.commons.mixins.Named;
import com.arosbio.cpsign.app.TuneScorer;
import com.arosbio.cpsign.app.params.CLIParameters;
import com.arosbio.cpsign.app.params.CLIParameters.TextOutputType;
import com.arosbio.cpsign.app.params.converters.ConfigUtils;
import com.arosbio.cpsign.app.params.converters.IntegerListOrRangeConverter;
import com.arosbio.cpsign.app.params.converters.ListOrRangeConverter;
import com.arosbio.cpsign.app.params.mixins.TestingStrategyMixin;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.out.OutputNamingSettings.ProgressInfoTexts;
import com.arosbio.ml.gridsearch.GridResultCSVWriter;
import com.arosbio.ml.gridsearch.GridSearch;
import com.arosbio.ml.gridsearch.GridSearch.EvalStatus;
import com.arosbio.ml.gridsearch.GridSearch.GSResult;
import com.arosbio.ml.gridsearch.GridSearch.ProgressCallback;
import com.arosbio.ml.gridsearch.GridSearch.ProgressInfo;
import com.arosbio.ml.gridsearch.GridSearchResult;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.cp.ConfidenceDependentMetric;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

public class TuneUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(TuneScorer.class);
	private static final int NUM_SIGN_DIGITS_IN_RESULT = 3;

	private static final int BACKUP_TXT_FORMAT_WIDTH = 20;

	static class TuneProgress implements ProgressCallback {

		final CLIConsole console;
		final int printInterval;

		public TuneProgress(CLIConsole console, int totalNumPoints){
			this.console = console;
			this.printInterval = CLIProgramUtils.getProgressInterval(totalNumPoints,10);
		}
		@Override
		public void updatedInfo(ProgressInfo info) {
			if (info.getNumProcessedGridPoints() % printInterval == 0){
				console.println(" - Finished %d/%d grid points", PrintMode.NORMAL, info.getNumProcessedGridPoints(), info.getTotalNumGridPoints());
			}
		}
	}

	public static void printTuneResults(TextOutputType format, GridSearchResult gsRes, Double confidence,
			File resultFile) {
		CLIConsole console = CLIConsole.getInstance();

		String txt = null;
		try {
			switch (format) {
				case CSV:
					LOGGER.debug("Printing results as CSV");
					txt = compileResultsAsCSV(gsRes, CSVFormat.DEFAULT.builder().setRecordSeparator(System.lineSeparator()).build(), console,
							confidence);
					break;
				case JSON:
					LOGGER.debug("Printing results as JSON");
					txt = compileResultsAsJSON(gsRes, confidence);
					break;
				case TEXT:
					LOGGER.debug("Printing results as TEXT");
					txt = compileResultsAsText(gsRes);
					break;
				case TSV:
					LOGGER.debug("Printing results as TSV");
					txt = compileResultsAsCSV(gsRes, CSVFormat.TDF.builder().setRecordSeparator(System.lineSeparator()).build(), console, confidence);
					break;
				default:
					LOGGER.debug("Output format not recognized in tune printing: {}", format);
					console.failWithInternalError("Output format for grid results not recognized");
					break;
			}
		} catch (Exception e) {
			LOGGER.debug("Failed compiling results", e);
			console.failWithInternalError("Failed compiling results due to: " + e.getMessage());
		}

		doPrint(format, txt, console, resultFile);
	}

	// JSON property keys
	private static final String RESULTS_KEY = "results";
	private static final String OPTIMIZATION_TYPE = "optimizationMetric";
	private static final String METRICS = "metrics";
	private static final String OPTIMAL_VALUE = "result";
	private static final String WARNING = "warning";
	private static final String PARAMS = "params";
	private static final String RUNTIME = "runtime(milliseconds)";
	private static final String COMMENT = "comment";
	private static final String STATUS = "status";
	private static final String CONF_SETTING = "chosenConfidence";
	private static final String RANK = "Rank";
	private static final String RANK_NA = "NA";

	private static String compileResultsAsJSON(GridSearchResult results, Double conf) {
		JsonObject topLevel = new JsonObject();
		topLevel.put(OPTIMIZATION_TYPE, results.getOptimizationType().getName());
		if (results.hasWarning()) {
			topLevel.put(WARNING, results.getWarning());
		}

		if (checkIfUsesConf(listAllMetrics(results)) && conf != null) {
			topLevel.put(CONF_SETTING, conf);
		}

		// Add array with parameter values
		double lastResult = MathUtils.roundToNSignificantFigures(results.getBestParameters().get(0).getResult(),
				NUM_SIGN_DIGITS_IN_RESULT);
		int rank = 1;
		JsonArray array = new JsonArray();
		for (GSResult res : results.getBestParameters()) {
			Map<String, Object> paramRes = new HashMap<>();
			double roundedVal = MathUtils.roundToNSignificantFigures(res.getResult(), NUM_SIGN_DIGITS_IN_RESULT);
			paramRes.put(OPTIMAL_VALUE, roundedVal);
			paramRes.put(RUNTIME, res.getRuntime());
			paramRes.put(PARAMS, res.getParams());
			paramRes.put(STATUS, res.getStatus().toString());
			if (res.getErrorMessage() != null && !res.getErrorMessage().isEmpty())
				paramRes.put(COMMENT, res.getErrorMessage());
			// Metrics
			Map<String, Object> metrics = new LinkedHashMap<>();
			// Optimization one first
			metrics.putAll(res.getOptimizationMetric().asMap());
			// Secondary metrics
			metrics.putAll(getSecondaryMetricsMapping(res));
			// Round all values and add to JSON mapping
			paramRes.put(METRICS, MathUtils.roundAllValues(metrics));
			// End metrics

			if (Double.isFinite(roundedVal)) {
				if (lastResult != roundedVal) {
					// If new score - update the rank
					lastResult = roundedVal;
					rank++;
				}
				paramRes.put(RANK, rank);
			} else {
				paramRes.put(RANK, RANK_NA);
			}
			array.add(paramRes);
		}

		topLevel.put(RESULTS_KEY, array);

		return Jsoner.prettyPrint(topLevel.toJson());

	}

	private static String compileResultsAsCSV(GridSearchResult results, CSVFormat format, CLIConsole console,
			Double conf) {

		List<SingleValuedMetric> mets = listAllMetrics(results);
		boolean usesConfidence = checkIfUsesConf(mets);

		StringBuilder resultsBuilder = new StringBuilder();

		try (
				GridResultCSVWriter resultPrinter = new GridResultCSVWriter.Builder()
					.rank(false)
					.confidence((conf != null && usesConfidence ? conf : null))
					.params(results.getBestParameters().get(0).getParams().keySet())
					.log(resultsBuilder)
					.format(format.builder())
					.build();
				) {

			for (GSResult res : results.getBestParameters()) {
				resultPrinter.printRecord(res);
			}

		} catch (Exception e) {
			LOGGER.debug("Failed writing results to CSV", e);
			console.failWithInternalError("Failed compiling results in CSV format: " + e.getMessage());
		}

		resultsBuilder.append("%n");

		return resultsBuilder.toString();
	}

	private static int findLongestMetricName(Collection<SingleValuedMetric> metrics) {
		int widest = BACKUP_TXT_FORMAT_WIDTH;
		for (SingleValuedMetric m : metrics) {
			for (String n : m.asMap().keySet()) {
				widest = Math.max(widest, n.length());
			}
		}
		return widest;
	}

	private static String compileResultsAsText(GridSearchResult results) {

		// Width from map of optimization type
		List<SingleValuedMetric> allMetrics = new ArrayList<>();
		allMetrics.add(results.getBestParameters().get(0).getOptimizationMetric());
		if (results.getBestParameters().get(0).getSecondaryMetrics() != null) {
			allMetrics.addAll(results.getBestParameters().get(0).getSecondaryMetrics());
		}
		int widestParamName = findLongestMetricName(allMetrics);

		final int w = Math.max(widestParamName + 1, BACKUP_TXT_FORMAT_WIDTH); // width to splitting ':'
		final String lineFormat = " - %-" + w + "s : %s%n";

		StringBuilder resultBilder = new StringBuilder();
		// Write info text to start with
		resultBilder.append("Optimal parameters found by tune using ");
		resultBilder.append(results.getOptimizationType().getName());
		resultBilder.append(" as optimization metric:%n");
		resultBilder.append(StringUtils.repeat('-', resultBilder.length() - 2));
		resultBilder.append("%n");

		Formatter f = new Formatter(resultBilder);

		// Print result for every result
		for (GSResult res : results.getBestParameters()) {

			if (res.getStatus() == EvalStatus.VALID) {
				// Valid result!

				// Optimization metric
				for (Map.Entry<String, ?> kv : res.getOptimizationMetric().asMap().entrySet()) {
					if (kv.getValue() instanceof Double || kv.getValue() instanceof Float) {
						f.format(lineFormat, kv.getKey(), MathUtils.roundToNSignificantFigures(
								TypeUtils.asDouble(kv.getValue()), NUM_SIGN_DIGITS_IN_RESULT));
					} else {
						f.format(lineFormat, kv.getKey(), kv.getValue());
					}
				}
				// f.format(lineFormat, results.getOptimizationType().getName(),
				// MathUtils.roundToNSignificantFigures(res.getResult(),NUM_SIGN_DIGITS_IN_RESULT));
				// Parameters
				for (Map.Entry<String, Object> param : res.getParams().entrySet()) {
					f.format(lineFormat, "Param " + param.getKey(), param.getValue());
				}
				// Secondary metrics
				if (res.getSecondaryMetrics() != null) {
					for (Map.Entry<String, Object> kv : getSecondaryMetricsMapping(res).entrySet()) {
						if (kv.getValue() instanceof Float || kv.getValue() instanceof Double)
							f.format(lineFormat, kv.getKey(), MathUtils.roundToNSignificantFigures(
									TypeUtils.asDouble(kv.getValue()), NUM_SIGN_DIGITS_IN_RESULT));
						else
							f.format(lineFormat, kv.getKey(), kv.getValue());
					}
				}
				f.format(lineFormat, "Runtime", Stopwatch.toNiceString(res.getRuntime()));
			} else {
				// Invalid - we can skip some irrelevant stuff
				f.format(lineFormat, "Status", res.getStatus().toString());
				// Parameters
				for (Map.Entry<String, Object> param : res.getParams().entrySet()) {
					f.format(lineFormat, "Param " + param.getKey(), param.getValue());
				}
			}

			// Finish both cases with comments (if any)
			if (res.getErrorMessage() != null && !res.getErrorMessage().isEmpty())
				f.format(lineFormat, "Comment", res.getErrorMessage());

			// Write a blank rows between results
			resultBilder.append("%n");
		}

		f.close();

		return resultBilder.toString();

	}

	private static Map<String, Object> getSecondaryMetricsMapping(GSResult res) {
		Map<String, Object> mapping = new LinkedHashMap<>();
		if (res.getSecondaryMetrics() != null) {
			for (SingleValuedMetric svm : res.getSecondaryMetrics()) {
				mapping.putAll(svm.asMap());
			}
		}
		return mapping;
	}

	private static void doPrint(TextOutputType format, String txt, CLIConsole console, File overallStatsFile) {
		boolean printed = false;
		if (overallStatsFile != null) {
			try (
					FileWriter fw = new FileWriter(overallStatsFile);
					PrintWriter printer = new PrintWriter(fw);) {
				console.print(
						WordUtils.wrap("Writing results to file: " + overallStatsFile + ProgressInfoTexts.SPACE_ELLIPSES,
								console.getTextWidth()),
						PrintMode.NORMAL);
				printer.printf(txt);
				printer.flush();
				printed = true;
				console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
			} catch (Exception e) {
				LOGGER.debug("Failed printing results to file: {} resorting to printing to screen instead",
						overallStatsFile, e);
				console.println(ProgressInfoTexts.FAILED_TAG, PrintMode.NORMAL);
			}
		}
		if (!printed) {
			if (format == TextOutputType.TEXT)
				console.println("", PrintMode.NORMAL);
			else
				console.println("%nResults:", PrintMode.NORMAL);
			console.println(txt, PrintMode.SILENT);
			console.println("", PrintMode.SILENT);
		}
	}

	private static List<SingleValuedMetric> listAllMetrics(GridSearchResult results) {
		List<SingleValuedMetric> mets = new ArrayList<>();
		mets.add(results.getOptimizationType());
		List<SingleValuedMetric> secondaryMetrics = results.getBestParameters().get(0).getSecondaryMetrics();
		if (secondaryMetrics != null)
			mets.addAll(secondaryMetrics);
		return mets;
	}

	private static boolean checkIfUsesConf(List<? extends Metric> mets) {
		for (Metric m : mets) {
			if (m instanceof ConfidenceDependentMetric) {
				return true;
			}
		}
		return false;
	}

	public static Map<String, List<?>> setupParamGrid(Configurable predictor, Map<String, String> paramGrid) {
		CLIConsole console = CLIConsole.getInstance();
		List<ConfigParameter> possibleParams = predictor.getConfigParameters();
		Map<String, List<? extends Object>> finalGrid = new LinkedHashMap<>();

		List<String> unUsedParams = new ArrayList<>();
		if (paramGrid != null && !paramGrid.isEmpty()) {
			LOGGER.debug("Setting up param grid out of --grid arguments: {}", paramGrid);
			int numFailed = 0;

			for (Map.Entry<String, String> kv : paramGrid.entrySet()) {
				boolean added = false;
				for (ConfigParameter p : possibleParams) {
					if (CollectionUtils.containsIgnoreCase(p.getNames(), kv.getKey())) {
						try {
							finalGrid.put(p.getNames().get(0), convertToParamValues(kv.getValue(), p));
							added = true;
							break;
						} catch (Exception e) {
							console.printlnStdErr(
									String.format("Grid values for parameter '%s' could not be converted properly: %s",
											kv.getKey(), e.getMessage()),
									PrintMode.SILENT);
							numFailed++;
						}
					}
				}
				if (!added) {
					unUsedParams.add(kv.getKey());
				}
			}

			if (numFailed > 0) {
				LOGGER.debug("Failed converting {} parameters", numFailed);
				console.failWithArgError("Failed converting %s parameter(s), please check your syntax", numFailed);
			}
		}

		// Write out unused params
		if (!unUsedParams.isEmpty()) {
			LOGGER.debug("Unused params: {}", unUsedParams);
			console.printlnStdErr("Unused parameter(s) in the evaluation grid: " + StringUtils.join(unUsedParams, ", "),
					PrintMode.NORMAL);
		}

		// Fail if no grid-search possible to run
		if (finalGrid.isEmpty()) {
			LOGGER.error("No grid was possible to set up, have {} non-recognized/un-used params - failing",
					unUsedParams.size());
			console.failWithArgError("No parameters sucessfully added to search-grid");
		}

		return finalGrid;
	}

	public final static String GRID_USE_DEFAULT_FALLBACK = "default";

	public static List<Object> convertToParamValues(String input, ConfigParameter p) {
		LOGGER.debug("Trying to convert input '{}' to a list of values for the grid", input);
		if (p instanceof DescribedConfig) {
			return convertToParamValues(input, ((DescribedConfig) p).getOriginalConfig());
		}
		// special case for 'default'
		if (input.equalsIgnoreCase(GRID_USE_DEFAULT_FALLBACK)) {
			if (p.getDefaultGrid() != null && !p.getDefaultGrid().isEmpty()) {
				return p.getDefaultGrid();
			} else {
				LOGGER.debug("User requested to use default values for parameter {} but there are none", p.toString());
				throw new IllegalArgumentException("No default evaluation points exists");
			}

		}

		// Split input to a list of values
		List<String> splittedInput = MultiArgumentSplitter.split(input);

		// trying boolean
		if (p instanceof BooleanConfig) {
			try {
				List<Boolean> vals = new ArrayList<>();

				for (String v : splittedInput) {
					vals.add(TypeUtils.asBoolean(v));
				}
				return new ArrayList<>(vals);
			} catch (Exception e) {
				LOGGER.debug("Could not parse input '{}' as a list of boolean values", input);
				throw new IllegalArgumentException('\'' + input + "' cannot be parsed into a list of boolean vales");
			}

		}

		// enum
		if (p instanceof EnumConfig<?>) {
			Object[] values = ((EnumConfig<?>) p).getEnumValues();
			List<Object> convertedValues = new ArrayList<>();

			for (String s : splittedInput) {
				boolean added = false;
				for (Object o : values) {
					LOGGER.debug("matching {} to obj: {}", s, o);
					if (o instanceof HasID) {
						try {
							if (TypeUtils.asInt(s) == ((HasID) o).getID()) {
								convertedValues.add(o);
								added = true;
								break;
							}
						} catch (Exception e) {
						}
					} else {
						try {
							if (TypeUtils.asInt(s) == ((Enum<?>) o).ordinal()) {
								convertedValues.add(o);
								added = true;
								break;
							}
						} catch (NumberFormatException e) {
						}
					}
					if (o instanceof Named) {
						if (((Named) o).getName().equalsIgnoreCase(s)) {
							convertedValues.add(o);
							added = true;
							break;
						}
					}
					// try using the name
					if (s.equalsIgnoreCase(((Enum<?>) o).name())) {
						convertedValues.add(o);
						added = true;
						break;
					}
				}
				if (!added) {
					LOGGER.debug("Could not find a match for input '{}' for parameter: {}",s, p.getNames());
					throw new IllegalArgumentException(input + " does not match any of the allowed values: "
							+ StringUtils.join(((EnumConfig<?>) p).getEnumValues()));
				}
			}
			return convertedValues;
		}

		// implementation
		if (p instanceof ImplementationConfig<?>) {
			ImplementationConfig<?> impl = (ImplementationConfig<?>) p;
			Class<?> cls = impl.getClassOrInterface();
			FuzzyServiceLoader<?> loader = new FuzzyServiceLoader<>(cls);
			List<Object> list = new ArrayList<>();

			for (String s : splittedInput) {
				if (s.contains(":")) {
					String[] confs = s.split(":");
					Object o = loader.load(confs[0]);
					// Configure it
					List<String> args = new ArrayList<>(Arrays.asList(confs));
					args.remove(0);
					if (!(o instanceof Configurable)) {
						LOGGER.debug("Tried configure object of type " + cls
								+ " but it does not implement Configurable (ImplementationConfig<T>) in tune");
						throw new IllegalArgumentException("Object " + cls.getSimpleName() + " is not configurable");
					}
					ConfigUtils.setConfigs((Configurable) o, args, input);

					list.add(o);
				} else {
					Object o = loader.load(s);
					list.add(o);
				}

			}
			return list;
		}

		// integer
		if (p instanceof IntegerConfig) {
			try {
				List<Object> values = new ArrayList<>();
				for (String s : splittedInput) {
					values.addAll(new IntegerListOrRangeConverter().convert(s));
				}
				return values;
			} catch (Exception e) {
				LOGGER.debug("Could not parse as integer input", e);
				throw new IllegalArgumentException(
						"Input '" + input + "' could not be converted to a list of integer values");
			}
		}

		// numeric
		if (p instanceof NumericConfig) {
			try {
				List<Object> values = new ArrayList<>();
				for (String s : splittedInput) {
					values.addAll(new ListOrRangeConverter().convert(s));
				}
				return values;
			} catch (Exception e) {
				LOGGER.debug("Could not parse as number input", e);
				throw new IllegalArgumentException(
						String.format("Input '%s' could not be converted to a list of numeric values", input));
			}
		}

		// string
		if (p instanceof StringConfig) {
			return new ArrayList<>(MultiArgumentSplitter.split(input));
		}

		LOGGER.error("Config type of unregognizable type: {}, class: {}", p.getType(), p.getClass());
		throw new RuntimeException(String.format("Parameter %s of unrecognized/unsupported type", p.getNames().get(0)));
	}

	/**
	 * For Tune-scorer, i.e. not using tolerance and confidence
	 * @param testing given testing arguments
	 * @param optMetric optimization metric
	 * @param secondaryMetrics any secondary metrics
	 * @param numResultsToPrint how many of the top results that should be returned from grid-searching
	 * @param console the console instance
	 * @return the configured {@link GridSearch} instance
	 */
	public static GridSearch initAndConfigGS(TestingStrategyMixin testing, SingleValuedMetric optMetric, List<SingleValuedMetric> secondaryMetrics,
			int numResultsToPrint, CLIConsole console, int numGridPoints) {
		return initAndConfigGS(testing, optMetric, secondaryMetrics, numResultsToPrint, CLIParameters.DEFAULT_CONFIDENCE, 1d, console, numGridPoints);
	}

	public static GridSearch initAndConfigGS(TestingStrategyMixin testing, SingleValuedMetric optMetric, List<SingleValuedMetric> secondaryMetrics,
			int numResultsToPrint, double cvConf, double cvTol, CLIConsole console, int numGridPoints) {

		testing.testStrategy.setSeed(GlobalConfig.getInstance().getRNGSeed());

		// Init grid search to make sure parameters are OK
		GridSearch.Builder gridSearch = new GridSearch.Builder();
		try {
			gridSearch.confidence(cvConf)
				.tolerance(cvTol)
				.evaluationMetric(optMetric)
				.testStrategy(testing.testStrategy)
				.register(new TuneProgress(console, numGridPoints))
				.maxNumResults(numResultsToPrint);

			if (secondaryMetrics != null && !secondaryMetrics.isEmpty()) {
				gridSearch.secondaryMetrics(secondaryMetrics);
			}
			LOGGER.debug("Running Grid search using testing strategy {}", testing.testStrategy.toString());
		} catch (Exception e) {
			LOGGER.debug("Failed initializing GridSearch", e);
			console.failWithArgError("Faulty parameters for %s: %s", console.getRunningCmd(), e.getMessage());
		}

		return gridSearch.build();
	}

	public static int calcNumGridPoints(Map<String, List<?>> grid) {
		int prod = 1;
		for (List<?> l : grid.values()) {
			prod *= l.size();
		}
		return prod;
	}

	/**
	 * Should only be called in case no valid parameters were found
	 * @param result The result from a grid search
	 * @return An error message
	 */
	public static String compileErrorMessagesForFailedRuns(GridSearchResult result) {

		Set<String> errorMessages = new LinkedHashSet<>();
		for (GSResult res : result.getBestParameters()) {
			String m = res.getErrorMessage();
			if (m != null && !m.isEmpty())
				errorMessages.add(m);
		}
		// return if no good messages found
		if (errorMessages.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		final String LINE_START = "  - ";
		final String NEW_LINE = "%n";
		int maxNumMessages = 5;
		int counter = 0;
		for (String m : errorMessages) {
			if (counter >= maxNumMessages)
				break;
			sb.append(LINE_START).append(m).append(NEW_LINE);
			counter++;
		}
		return sb.toString();
	}

}

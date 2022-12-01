/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app;

import java.io.File;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.output.FileWriterWithEncoding;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.cpsign.app.params.converters.EmptyFileConverter;
import com.arosbio.cpsign.app.params.converters.MetricConverter;
import com.arosbio.cpsign.app.params.mixins.ConsoleVerbosityMixin;
import com.arosbio.cpsign.app.params.mixins.EchoMixin;
import com.arosbio.cpsign.app.params.mixins.EncryptionMixin;
import com.arosbio.cpsign.app.params.mixins.LogfileMixin;
import com.arosbio.cpsign.app.params.mixins.OverallStatsMixinClasses;
import com.arosbio.cpsign.app.params.mixins.PrecomputedDatasetMixin;
import com.arosbio.cpsign.app.params.mixins.PredictorMixinClasses;
import com.arosbio.cpsign.app.params.mixins.ProgramProgressMixin;
import com.arosbio.cpsign.app.params.mixins.RNGSeedMixin;
import com.arosbio.cpsign.app.params.mixins.TestingStrategyMixin;
import com.arosbio.cpsign.app.params.mixins.TransformerMixin;
import com.arosbio.cpsign.app.params.mixins.TuneGridMixin;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIProgramUtils;
import com.arosbio.cpsign.app.utils.CLIProgressBar;
import com.arosbio.cpsign.app.utils.NullProgress;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ProgramTimer;
import com.arosbio.cpsign.app.utils.TuneUtils;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.CLIProgressBar.SupportsProgressBar;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.cpsign.out.OutputNamingSettings;
import com.arosbio.cpsign.out.OutputNamingSettings.PB;
import com.arosbio.cpsign.out.OutputNamingSettings.ProgressInfoTexts;
import com.arosbio.data.DataUtils;
import com.arosbio.data.DataUtils.DataType;
import com.arosbio.data.NamedLabels;
import com.arosbio.io.IOSettings;
import com.arosbio.ml.algorithms.Classifier;
import com.arosbio.ml.algorithms.MLAlgorithm;
import com.arosbio.ml.gridsearch.GridSearch;
import com.arosbio.ml.gridsearch.GridSearchResult;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.MetricFactory;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.classification.BalancedAccuracy;
import com.arosbio.ml.metrics.classification.LabelDependent;
import com.arosbio.ml.metrics.regression.RMSE;
import com.arosbio.ml.testing.TestRunner;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/*
 * PB.VALIDATE_PARAMS_PROGRESS
 * PB.LOADING_FILE_OR_MODEL_PROGRESS
 * PB.CROSSVALIDATING_PROGRESS
 * PB.RESULTS_WRITING_PROGRESS
 */
@Command(
		name = TuneScorer.CMD_NAME, 
		description = TuneScorer.CMD_DESCRIPTION, 
		descriptionHeading = CPSignApp.DESCRIPTION_HEADER, 
		header = TuneScorer.CMD_HEADER,
		aliases = TuneScorer.CMD_ALIAS
		)
public class TuneScorer implements RunnableCmd, SupportsProgressBar {

	private static final Logger LOGGER = LoggerFactory.getLogger(TuneScorer.class);
	//	private static final int NUM_SIGN_DIGITS_IN_RESULT = 3;

	public final static String CMD_NAME = "tune-scorer";
	public final static String CMD_ALIAS = "tune-algorithm";
	public final static String CMD_HEADER = "Tune algorithm parameters using a grid-search";
	public final static String CMD_DESCRIPTION = 
			"Perform an exhaustive grid search to find optimal model parameters for the underlying scoring algorithms. "
					+ "What parameters that can be tuned depends on which algorithm is used and can be queried using, e.g., "
					+ ParameterUtils.RUN_EXPLAIN_ANSI_ON+"explain scorer "+ParameterUtils.ANSI_OFF;

	/*****************************************
	 * INTERNAL STATE
	 *****************************************/
	@Spec private CommandSpec spec;
	private CLIConsole console = CLIConsole.getInstance();
	private CLIProgressBar pb = new NullProgress();
	private ProgramTimer timer = new ProgramTimer(false, console);

	private MLAlgorithm predictor;
	private ChemDataset data;
	private boolean isClassification;


	/*****************************************
	 * OPTIONS
	 *****************************************/

	// Input
	@Mixin
	private PrecomputedDatasetMixin inputSection;

	// Predictor params
	@Mixin
	private PredictorMixinClasses.ScorerModelParams algorithmSection;

	// Grid search params
	@Option(names = {"-op", "--opt-metric"}, 
			description = "The metric that should be used for optimizing the parameters. Only 'standard' metrics for non-conformal and Venn-predictors are valid. Balanced Accuracy is the default for classifers and RMSE for regression algorithms. All available metrics can be queried using "+
					ParameterUtils.RUN_EXPLAIN_ANSI_ON+"explain metrics"+ParameterUtils.ANSI_OFF+". But note that some are not valid here.",
					converter = MetricConverter.class,
					paramLabel = ArgumentType.TEXT_MATCH_ONE_OF
			)
	private Metric inputOptMetric;

	@Mixin
	private TuneGridMixin gridMixin;

	// CV - params
	@Mixin
	private TestingStrategyMixin testStrat = new TestingStrategyMixin();

	// Transformer section
	@Mixin
	private TransformerMixin transformerSection;

	@Option(
			names = {"-ro", "--result-output"},
			description = "File to print the best parameter combinations to, number of results is regulated by the --num-results parameter (default is otherwise to print in the terminal)",
			converter = EmptyFileConverter.class,
			paramLabel = ArgumentType.FILE_PATH
			)
	private File overallStatsFile;

	@Mixin
	private OverallStatsMixinClasses.JSONTextCSV_TSV resultOutputFormat; 

	@Option(
			names= {"--num-results"},
			description = "Number of parameter-results to print (e.g. print the top 5 best parameters). A value smaller than 1 will return all results.%n"+
					ParameterUtils.DEFAULT_VALUE_LINE,
					defaultValue = "5",
					paramLabel = ArgumentType.INTEGER
			)
	private int numResultsToPrint = 5;

	@Option(names= {"--calc-all", "--calc-all-metrics"},
			description = "Calculate all possible metrics available for the given ML scorer type")
	private boolean calculateSecondaryMetrics = false;

	@Option(
			names= {"--generate@file"},
			description = "Generate a file that can be specified with the @file-syntax with the parameters that resulted in the best score of this run. "+
					"This way it is easier to automate a workflow where tuning is performed prior to model training / evaluation on a withheld dataset.",
					converter = EmptyFileConverter.class,
					paramLabel = ArgumentType.FILE_PATH
			)
	private File generateAtFile;

	// Encrypted model
	@Mixin
	private EncryptionMixin encryptSection = new EncryptionMixin();

	// General

	@Mixin
	private ProgramProgressMixin pbArgs;

	@Mixin
	private ConsoleVerbosityMixin consoleArgs;

	@Mixin
	private RNGSeedMixin seedArgs = new RNGSeedMixin(); 

	@Mixin
	private LogfileMixin loggingArgs;

	@Mixin
	private EchoMixin echo;

	/*****************************************
	 * END OF OPTIONS
	 *****************************************/


	@Override
	public String getName() {
		return CMD_NAME;
	}

	@Override
	public int getNumSteps() {
		return 4;
	}

	@Override
	public void setProgressBar(CLIProgressBar pb) {
		this.pb = pb;
	}

	@Override
	public Integer call() {

		CLIProgramUtils.doFullProgramConfig(this);

		console.print(OutputNamingSettings.ProgressInfoTexts.VALIDATING_ARGS, PrintMode.NORMAL);
		validateParams();
		console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
		pb.stepProgress();
		timer.endSection();

		// Load data first - so we know the type of data (regression / classification)
		loadData();

		// Get the ML Algorithm 
		predictor = algorithmSection.getMLAlg(isClassification, console);

		// Set up the optimization metric and (optionally) secondary metrics
		Pair<SingleValuedMetric, List<SingleValuedMetric>> metrics = setupMetrics();

		// INIT GRID-SEARCH
		GridSearch grid = TuneUtils.initAndConfigGS(testStrat, metrics.getLeft(), metrics.getRight(), numResultsToPrint,console);
		
		// Run tune!
		tuneAndPrintResults(grid, predictor);

		// FINISH PROGRAM
		pb.finish();
		timer.endProgram();
		console.println("", PrintMode.NORMAL);
		return ExitStatus.SUCCESS.code;
	}


	private void validateParams(){

		// If output should be written to file
		CLIProgramUtils.setupOverallStatsFile(overallStatsFile, console);

		console.println("Using RNG seed: %s", 
			PrintMode.VERBOSE,GlobalConfig.getInstance().getRNGSeed());
	}

	@SuppressWarnings("null")
	private Pair<SingleValuedMetric, List<SingleValuedMetric>> setupMetrics() {
		LOGGER.debug("Setting up metric for ML Alg: {}",predictor.getClass().getName());
		SingleValuedMetric optMetric = null;

		if (inputOptMetric == null) {
			LOGGER.debug("No explicit metric given - falling back to default metric for the given algorithm");

			if (predictor instanceof Classifier)
				optMetric = new BalancedAccuracy();
			else 
				optMetric = new RMSE();
			
		} else {
			LOGGER.debug("Given an explicit metric: {}",inputOptMetric.getName());
			
			if (! (inputOptMetric instanceof SingleValuedMetric)) {
				LOGGER.debug("Got opt-metric of class {} which is not a single-valued-metric - failing!",inputOptMetric.getClass().getName());
				console.failWithArgError("Parameter "+CLIProgramUtils.getParamName(this, "inputOptMetric", "--opt-metric") + 
						" must be a single valued metric, please pick a different metric");
			} else {
				// Correct 'base type' 
				optMetric = (SingleValuedMetric) inputOptMetric;

				if (!TestRunner.metricSupportedByAlgorithm(optMetric, predictor)) {
					LOGGER.debug("Metric not supported by ml algorithm {} vs {}",optMetric.getClass().getName(), predictor.getClass().getName());

					// Generate error message with allowed metrics
					List<SingleValuedMetric> allowed = MetricFactory.filterToSingleValuedMetrics(MetricFactory.getMetrics(predictor, true));
					StringBuilder sb = new StringBuilder("Invalid metric (").append(optMetric.getName()).append(") specified for algorithm ").append(predictor.getName()).append(", allowed values:%n");

					for (SingleValuedMetric m : allowed) {
						sb.append('\t').append(m.getName()).append("%n");
					}
					console.failWithArgError(sb.toString());
				}
			}
		}
		LOGGER.debug("Using {} as optimization metric",optMetric.getName());

		
		List<SingleValuedMetric> secondaryMetrics = null;
		if (calculateSecondaryMetrics) {
			// Setup secondary metrics
			secondaryMetrics = new ArrayList<>();
			boolean isMulticlass = DataUtils.checkDataType(data) == DataType.MULTI_CLASS;
			// Only use SingleValuedMetrics
			List<SingleValuedMetric> mets = MetricFactory.filterToSingleValuedMetrics(MetricFactory.getMetrics(predictor, isMulticlass));
			// Remove the optimization metric - so no duplicates
			for (SingleValuedMetric m : mets) {
				if (optMetric.getClass() != m.getClass()) {
					secondaryMetrics.add((SingleValuedMetric) m);
				}
			}
		}

		if (isClassification) {
			NamedLabels nl = data.getTextualLabels();
			
			if (optMetric instanceof LabelDependent)
				MetricFactory.setClassificationLabels(nl, optMetric);
			
			if (secondaryMetrics != null)
				MetricFactory.setClassificationLabels(nl, secondaryMetrics);
			
		}

		LOGGER.debug("Set up optimization metric: {}, and secondary metrics: {}",optMetric, secondaryMetrics);
		return Pair.of(optMetric, secondaryMetrics);
	}

	private void loadData() {

		console.println(ProgressInfoTexts.LOADING_PRECOMP_DATA, PrintMode.NORMAL);

		try {
			data = ModelSerializer.loadDataset(inputSection.getAsFile().toURI(), encryptSection.exclusive.encryptSpec);
			// loaded = ModelSerializer.load(inputSection.modelFile, encryptSection.exclusive.encryptSpec);
		} catch (Exception e) {
			LOGGER.debug("Failed loading input that should be a precomputed data set",e);
			console.failWithArgError("Failed loading precomputed data set due to: " + e.getMessage());
		}

		isClassification = data.getTextualLabels() != null; 

		CLIProgramUtils.printInfoAboutDataset(data, console);
		console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
		pb.stepProgress();
		timer.endSection();


		// Do transformations
		CLIProgramUtils.applyTransformations(data, isClassification, transformerSection.transformers, this, console);

		// Check for missing features - that will fail later in the run
		if (DataUtils.containsMissingFeatures(data)) {
			LOGGER.debug("Missing data encountered before running tune");
			console.failWithArgError("Training data contains missing data for one or multiple features, please revise your pre-processing prior to training/tuning");
		}
	}

	@SuppressWarnings("null")
	private void tuneAndPrintResults(GridSearch searcher, MLAlgorithm algorithm) {
		Map<String,List<?>> grid = TuneUtils.setupParamGrid(algorithm, gridMixin.paramGrid);
		console.printlnWrapped("Parameter grid contains %s combinations to be evaluated", PrintMode.VERBOSE,
			TuneUtils.calcNumGridPoints(grid));

		GridSearchResult gsRes = null;

		// Run the tuning/grid-search
		console.print("Running tune grid search using %s strategy... ", PrintMode.NORMAL, testStrat.testStrategy);
		pb.setCurrentTask(PB.CROSSVALIDATING_PROGRESS);

		try {
			// searcher.setEvaluationMetric(optMetric);
			gsRes = searcher.search(data,algorithm, grid);
		} catch (Exception e){
			LOGGER.debug("failed gridsearch with exception",e);
			console.failWithArgError(e.getMessage());
		}
		if (gsRes.getHasValidParamCombo()) {
			console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
		} else {
			console.println(ProgressInfoTexts.FAILED_TAG, PrintMode.NORMAL);
			String errMessages = TuneUtils.compileErrorMessagesForFailedRuns(gsRes);
			LOGGER.debug("No valid parameter combinations found, all failed - thus failing execution, messages={}",errMessages);
			
			StringBuilder errTxt = new StringBuilder("Found no valid parameter combinations - all models were invalid or failed during training/evaluation.");
			if (errMessages != null && !errMessages.isEmpty()){
				errTxt.append(" Error messages encountered during evaluation:%n").append(errMessages);

			}
			
			console.failWithArgError(errTxt.toString());
		}
		timer.endSection();
		pb.stepProgress();

		// PRINTING RESULTS
		pb.setCurrentTask(PB.RESULTS_WRITING_PROGRESS);
		List<Metric> allMetrics = new ArrayList<>(); 
		allMetrics.add(searcher.getEvaluationMetric());
		if (searcher.getSecondaryMetrics()!=null)
			allMetrics.addAll(searcher.getSecondaryMetrics());
		CLIProgramUtils.printNoteOnPosNegLabels(allMetrics, data.getTextualLabels(), console);
		TuneUtils.printTuneResults(resultOutputFormat.outputFormat, gsRes, null, overallStatsFile);
		LOGGER.debug("Finished writing results");

		if (gsRes.hasWarning()) {
			LOGGER.debug("had warning in grid-search: {}", gsRes.getWarning());
			console.printlnWrappedStdErr(String.format("%n%s%n",gsRes.getWarning()), PrintMode.NORMAL);
		}

		if (generateAtFile != null) {
			LOGGER.debug("Writing @parameters file with optimal parameters");
			if (gsRes.getHasValidParamCombo())
				writeAtParameterFile(gsRes);
			else 
				console.printlnStdErr("No valid parameters - no @parameter file will be written", PrintMode.SILENT);
		}

		pb.stepProgress();
	}


	private void writeAtParameterFile(GridSearchResult results) {

		// The predictor should have all the optimal parameters set after the GridSearch finalized


		try (
				Writer fos = new FileWriterWithEncoding(generateAtFile, IOSettings.CHARSET);
				PrintWriter pw = new PrintWriter(fos);
				){

			console.print(WordUtils.wrap("Writing optimal @parameter file: " + generateAtFile + ProgressInfoTexts.SPACE_ELLIPSES, console.getTextWidth()), 
					PrintMode.NORMAL);

			// The predictor should have all the optimal parameters set after the GridSearch finalized
			compileBestParamsAtFile(predictor, pw);
			console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);

		} catch (Exception e) {
			LOGGER.debug("Failed generating @file with optimal parameters", e);
			console.println(ProgressInfoTexts.FAILED_TAG, PrintMode.NORMAL);
			console.println("Failed generating @file with optimal parameters - these will need to be specified manually", PrintMode.VERBOSE);
		}

	}

	private static void compileBestParamsAtFile(MLAlgorithm pred, PrintWriter writer) {

		Tune.addScoring(pred, "--scorer", writer);

	}



}
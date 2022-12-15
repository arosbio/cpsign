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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.output.FileWriterWithEncoding;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cheminf.ChemClassifier;
import com.arosbio.cheminf.ChemPredictor;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.cpsign.app.params.CLIParameters;
import com.arosbio.cpsign.app.params.converters.EmptyFileConverter;
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
import com.arosbio.cpsign.app.params.mixins.ValidationPointsMixin;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.CLIProgramUtils;
import com.arosbio.cpsign.app.utils.CLIProgressBar;
import com.arosbio.cpsign.app.utils.CLIProgressBar.SupportsProgressBar;
import com.arosbio.cpsign.app.utils.MissingParam;
import com.arosbio.cpsign.app.utils.NullProgress;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.cpsign.app.utils.ProgramTimer;
import com.arosbio.cpsign.app.utils.TuneUtils;
import com.arosbio.cpsign.out.OutputNamingSettings;
import com.arosbio.cpsign.out.OutputNamingSettings.PB;
import com.arosbio.cpsign.out.OutputNamingSettings.ProgressInfoTexts;
import com.arosbio.data.NamedLabels;
import com.arosbio.io.IOSettings;
import com.arosbio.ml.algorithms.MLAlgorithm;
import com.arosbio.ml.cp.ConformalClassifier;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.icp.ICPClassifier;
import com.arosbio.ml.cp.icp.ICPRegressor;
import com.arosbio.ml.cp.nonconf.NCM;
import com.arosbio.ml.cp.nonconf.calc.PValueCalculator;
import com.arosbio.ml.cp.nonconf.classification.NCMMondrianClassification;
import com.arosbio.ml.cp.nonconf.regression.NCMRegression;
import com.arosbio.ml.cp.nonconf.utils.NCMUtils;
import com.arosbio.ml.cp.tcp.TCPClassifier;
import com.arosbio.ml.gridsearch.GridSearch;
import com.arosbio.ml.gridsearch.GridSearchResult;
import com.arosbio.ml.interfaces.ClassificationPredictor;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.MetricFactory;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.classification.BrierScore;
import com.arosbio.ml.metrics.classification.LabelDependent;
import com.arosbio.ml.metrics.classification.LogLoss;
import com.arosbio.ml.metrics.classification.ROC_AUC;
import com.arosbio.ml.metrics.cp.ConfidenceDependentMetric;
import com.arosbio.ml.metrics.cp.classification.AverageC;
import com.arosbio.ml.metrics.cp.classification.ObservedFuzziness;
import com.arosbio.ml.metrics.cp.classification.ProportionMultiLabelPredictions;
import com.arosbio.ml.metrics.cp.classification.ProportionSingleLabelPredictions;
import com.arosbio.ml.metrics.cp.regression.CIWidthBasedMetric;
import com.arosbio.ml.metrics.cp.regression.MeanPredictionIntervalWidth;
import com.arosbio.ml.metrics.cp.regression.MedianPredictionIntervalWidth;
import com.arosbio.ml.metrics.regression.RMSE;
import com.arosbio.ml.sampling.SamplingStrategy;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.ml.vap.avap.AVAPClassifier;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.TypeConversionException;

/*
 * PB.VALIDATE_PARAMS_PROGRESS
 * PB.LOADING_FILE_OR_MODEL_PROGRESS
 * PB.CROSSVALIDATING_PROGRESS
 * PB.RESULTS_WRITING_PROGRESS
 */
@Command(
		name=Tune.CMD_NAME, 
		description = Tune.CMD_DESCRIPTION, 
		descriptionHeading = CPSignApp.DESCRIPTION_HEADER, 
		header = Tune.CMD_HEADER,
		aliases = Tune.CMD_ALIAS
		)
public class Tune implements RunnableCmd, SupportsProgressBar {

	private static final Logger LOGGER = LoggerFactory.getLogger(Tune.class);

	public final static String CMD_NAME = "tune";
	public final static String CMD_ALIAS = "gridsearch";
	public final static String CMD_HEADER = "Tune modeling parameters using a grid-search";
	public final static String CMD_DESCRIPTION = "Perform an exhaustive grid search to find optimal SVM parameter values for Cost, Gamma and Epsilon (both tolerance criteria and epsilon used in loss function of EpsilonSVR). "
			+ "For regression problems using the log-normalized "+
			"nonconformity measure, it is also possible to grid search for optimizing the beta-parameter of the nonconformity measure.";

	/*****************************************
	 * INTERNAL STATE
	 *****************************************/
	@Spec private CommandSpec spec;
	private CLIConsole console = CLIConsole.getInstance();
	private CLIProgressBar pb = new NullProgress();
	private ProgramTimer timer = new ProgramTimer(false, console);

	private ChemPredictor predictor;


	/*****************************************
	 * OPTIONS
	 *****************************************/

	// Input
	@Mixin
	private PrecomputedDatasetMixin inputSection;

	// Predictor params
	@Mixin
	private PredictorMixinClasses.AllPTMixin predictorSection;

	// Grid search params
	@Option(names = {"-op", "--opt-metric"}, 
			description = 
			"The metric that should be used for optimizing the parameters. Some metrics are confidence-dependent and are marked with an '*'. Apart from these 'default' ones, all 'single-valued-metrics' from "+
					ParameterUtils.RUN_EXPLAIN_ANSI_ON+"explain metrics"+ParameterUtils.ANSI_OFF+" can be used as well. Options:%n"+
					// ACP Regression
					"CP Regression:%n" +
					ParameterUtils.MULTIPLE_OPTIONS_INDENTATION_C_BEFORE + "(1) "+MedianPredictionIntervalWidth.METRIC_ALIAS + "%n"+
					ParameterUtils.MULTIPLE_OPTIONS_INDENTATION_C_BEFORE + "(2) "+MeanPredictionIntervalWidth.METRIC_ALIAS+"%n"+
					ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(3) "+RMSE.METRIC_NAME+"%n"+
					// CP Classification
					"CP Classification:%n" +
					ParameterUtils.MULTIPLE_OPTIONS_INDENTATION_C_BEFORE + "(1) "+ProportionSingleLabelPredictions.METRIC_ALIAS + "%n"+
					ParameterUtils.MULTIPLE_OPTIONS_INDENTATION_C_BEFORE + "(2) "+ProportionMultiLabelPredictions.METRIC_ALIAS + "%n"+
					ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(3) "+ObservedFuzziness.METRIC_ALIAS+"%n"+
					ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(4) "+AverageC.METRIC_NAME+"%n"+
					// VAP Classification:
					"VAP Classification:%n" +
					ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(1) "+LogLoss.METRIC_ALIAS+"%n"+
					ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(2) "+BrierScore.METRIC_ALIAS +"%n"+
					ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(3) "+ROC_AUC.METRIC_ALIAS+"%n"+
					ParameterUtils.DEFAULT_VALUE_LINE,
					defaultValue="1",
					paramLabel = ArgumentType.ID_OR_TEXT
			)
	private String optimizationString = "1";

	@Mixin
	private TuneGridMixin gridMixin;

	// CV - params
	@Mixin
	private TestingStrategyMixin testStrat = new TestingStrategyMixin();

	@Option(names = { "-co", "--confidence" }, 
			description = "Confidence used in the model evaluation, allowed range [0..1]%n"+
					ParameterUtils.DEFAULT_VALUE_LINE,
					defaultValue="0.8",
					paramLabel = ArgumentType.NUMBER)
	public void setCVconf(double conf) {
		if (conf < 0 || conf >1)
			throw new TypeConversionException("confidence must be in the range [0..1]");
		cvConfidence = conf;
	}
	private double cvConfidence = 0.8;

	@Option(names={"--tolerance"}, 
			description = "Allowed tolerance for validity of the generated models, allowed range [0..1]%n"+
					ParameterUtils.DEFAULT_VALUE_LINE,
					defaultValue = "0.1",
					paramLabel = ArgumentType.NUMBER
			)
	public void setTolerance(double tol) {
		if (tol < 0 || tol >1)
			throw new TypeConversionException("tolerance must be in the range [0..1]");
		cvTolerance = tol;
	}
	private double cvTolerance = 0.1;

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
			description = "Calculate all possible metrics available for the given predictor type")
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

		try {
			predictor = CLIProgramUtils.getSignaturesPredictor(
					predictorSection.getPredictor(console), console);
		} catch (Exception e) {
			LOGGER.debug("Failed init the predictor",e);
			console.failWithArgError("Failed setting up predictor with given parameters: " + e.getMessage());
		}
		LOGGER.debug("Init ChemPredictor of class {}, using predictor of class: {}",predictor.getClass(),predictor.getPredictor().getClass());
		
		console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);		
		pb.stepProgress();
		timer.endSection();
		// Full validation finished

		// LOAD DATA
		pb.setCurrentTask(PB.LOADING_FILE_OR_MODEL_PROGRESS);
		loadData(predictor);
		
		// Set up the optimization metric and (optionally) secondary metrics
		Pair<SingleValuedMetric, List<SingleValuedMetric>> metrics = setupMetrics(predictor);
		// INIT GRID-SEARCH
		GridSearch grid = TuneUtils.initAndConfigGS(testStrat,metrics.getLeft(),metrics.getRight(),numResultsToPrint,cvConfidence, cvTolerance,console);
		
		pb.stepProgress();
		timer.endSection();


		tuneAndPrintResults(grid, predictor);

		// FINISH PROGRAM
		pb.finish();
		timer.endProgram();
		console.println("", PrintMode.NORMAL);
		return ExitStatus.SUCCESS.code;
	}


	private void validateParams(){

		if (optimizationString == null)
			console.failDueToMissingParameters(new MissingParam("optimizationString","OPTIMIZATION_TYPE",Tune.class));

		// If output should be written to file
		CLIProgramUtils.setupOverallStatsFile(overallStatsFile, console);

		console.println("Using RNG seed: %s", PrintMode.VERBOSE,
			GlobalConfig.getInstance().getRNGSeed());
	}

	private Pair<SingleValuedMetric, List<SingleValuedMetric>> setupMetrics(ChemPredictor chemPred) {
		LOGGER.debug("Converting metric string into implementation: {}", optimizationString);

		Predictor predictor = chemPred.getPredictor();
		SingleValuedMetric optMetric = null;

		// If input was an integer
		try {
			int id = Integer.parseInt(optimizationString);
			if (predictor instanceof ACPRegressor) {
				if (id == 1) {
					optMetric = new MedianPredictionIntervalWidth(cvConfidence);
				} else if (id == 2) {
					optMetric = new MeanPredictionIntervalWidth(cvConfidence);
				} else if (id == 3) {
					optMetric = new RMSE();
				}
			} else if (predictor instanceof ConformalClassifier) {
				if (id == 1) {
					optMetric = new ProportionSingleLabelPredictions(cvConfidence);
				} else if (id == 2) {
					optMetric = new ProportionMultiLabelPredictions(cvConfidence);
				} else if (id == 3) {
					optMetric = new ObservedFuzziness();
				} else if (id == 4){
					optMetric = new AverageC();
				}
			} else if (predictor instanceof AVAPClassifier) {
				if (id == 1) {
					optMetric = new LogLoss();
				} else if (id == 2) {
					optMetric = new BrierScore();
				} else if (id == 3) {
					optMetric = new ROC_AUC();
				}
			} else {
				LOGGER.debug("Predictor of non-recognized type: {}", predictor.getClass());
				console.failWithInternalError();
			}

			if (optMetric == null) {
				LOGGER.debug("Failed setting up metric for predictor of class {} using ID: {}",predictor.getClass(), id);
				if (id <1 || id> 3) {
					console.failWithArgError("Invalid argument for " + CLIProgramUtils.getParamName(this, "optimizationString", "--opt-metric") +
							", numerical value only allowed in range [1..3]");
				}

			}

		} catch (Exception e) {
			LOGGER.debug("Was not an integer ID");
		}
		// END if metric was Integer

		if (optMetric == null) {
			// If not a integer-value, try assuming it's the name of a metric

			Metric loaded = FuzzyServiceLoader.load(Metric.class, optimizationString);

			if ( ! (loaded instanceof SingleValuedMetric)) {
				LOGGER.debug("Got opt-metric of class {} which is not a single-valued-metric - failing!",loaded.getClass());
				console.failWithArgError("Parameter "+CLIProgramUtils.getParamName(this, "optimizationString", "--opt-metric") + 
						" must be a single valued metrics, please pick a different metric");
			}

			if (loaded instanceof ConfidenceDependentMetric) {
				((ConfidenceDependentMetric) loaded).setConfidence(cvConfidence);
			}

			optMetric = (SingleValuedMetric) loaded;
		}

		// Here check if tune allows given criteria for given predictor type
		if (! TestRunner.metricSupportedByPredictor(optMetric, predictor))
			console.failWithArgError("Optimization metric '%s' not allowed for given predictor type", optMetric.getName());

		List<SingleValuedMetric> secondaryMetrics = null;
		if (calculateSecondaryMetrics) {
			// Setup secondary metrics
			ValidationPointsMixin fake = new ValidationPointsMixin();
			fake.calibrationPoints = new ArrayList<>();
			fake.calibrationPoints.add(cvConfidence);
			List<Metric> mets = CLIProgramUtils.setupMetrics(chemPred, fake);
			secondaryMetrics = new ArrayList<>();
			for (Metric m : mets) {
				if (m instanceof SingleValuedMetric && optMetric.getClass() != m.getClass()) {
					if (m instanceof CIWidthBasedMetric)
						continue; // Skip the width-based ones in tune
					secondaryMetrics.add((SingleValuedMetric) m);
				}
			}
		}
		
		// Update labels in metrics if needed
		if (chemPred instanceof ChemClassifier) {
			NamedLabels nl = ((ChemClassifier) chemPred).getNamedLabels();
			
			if (optMetric instanceof LabelDependent)
				MetricFactory.setClassificationLabels(nl, optMetric);
			if (secondaryMetrics != null) 
				MetricFactory.setClassificationLabels(nl,secondaryMetrics);
			
		}
		LOGGER.debug("Set up optimization metric: {}, and secondary metrics: {}", optMetric , secondaryMetrics);
		return Pair.of(optMetric, secondaryMetrics);
	}

	private void loadData(ChemPredictor predictor) {

		// Generate the numeric dataset
		try {
			CLIProgramUtils.loadPrecomputedData(predictor, inputSection, encryptSection.exclusive.encryptSpec, console);
		} catch (Exception | Error e) {
			LOGGER.debug("Failed loading data in 'tune'",e);
			console.failWithInternalError();
		}

		// Do transformations
		CLIProgramUtils.applyTransformations(predictor.getDataset(),predictor.getPredictor() instanceof ClassificationPredictor, transformerSection.transformers, this, console);

		if (predictor.checkIfDatasetsContainMissingFeatures()) {
			LOGGER.debug("Missing data encountered before running tune");
			console.failWithArgError("Training data contains missing data for one or multiple features, please revise your pre-processing prior to training");
		}
	}

	
	@SuppressWarnings("null")
	private void tuneAndPrintResults(GridSearch searcher, ChemPredictor chemPredictor) {
		Map<String,List<?>> grid = TuneUtils.setupParamGrid(chemPredictor.getPredictor(), gridMixin.paramGrid);
		console.printlnWrapped("Parameter grid contains " + TuneUtils.calcNumGridPoints(grid) + " combinations to be evaluated" , PrintMode.VERBOSE);

		GridSearchResult gsRes = null;

		// Run the tuning/grid-search
		console.print(String.format("Running tune grid search using %s strategy... ",testStrat.testStrategy), PrintMode.NORMAL);
		pb.setCurrentTask(PB.CROSSVALIDATING_PROGRESS);

		try {
			gsRes = searcher.search(chemPredictor.getDataset(), chemPredictor.getPredictor(), grid);
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
		CLIProgramUtils.printNoteOnPosNegLabels(allMetrics, chemPredictor.getDataset().getTextualLabels(), console);
		CLIProgramUtils.printNoteOnForcedPredAndMidpoint(allMetrics, console);
		TuneUtils.printTuneResults(resultOutputFormat.outputFormat, gsRes, cvConfidence, overallStatsFile);
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
			compileBestParamsAtFile(predictor.getPredictor(), pw);
			console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);

		} catch (Exception e) {
			LOGGER.debug("Failed generating @file with optimal parameters", e);
			console.println(ProgressInfoTexts.FAILED_TAG, PrintMode.NORMAL);
			console.println("Failed generating @file with optimal parameters - these will need to be specified manually", PrintMode.VERBOSE);
		}

	}

	private static void compileBestParamsAtFile(Predictor pred, PrintWriter writer) {

		// Predictor type
		writer.println("--predictor-type");
		writer.println(CLIParameters.PredictorType.getPredictorType(pred).getName());

		if (pred instanceof ACPClassifier) {
			ACPClassifier acp = (ACPClassifier) pred;
			ICPClassifier icp = acp.getICPImplementation();
			NCMMondrianClassification ncm = (NCMMondrianClassification) icp.getNCM();

			addSS(acp.getStrategy(), writer);
			addNCM(ncm, writer);
			addScoring(ncm.getModel(), "--scorer", writer);
			addPvalCalc(icp.getPValueCalculator(), writer);

		} else if (pred instanceof ACPRegressor) {
			ACPRegressor acp = (ACPRegressor) pred;
			ICPRegressor icp = acp.getICPImplementation();
			NCMRegression ncm = icp.getNCM();

			addSS(acp.getStrategy(),writer);
			addNCM(ncm,writer);
			addScoring(ncm.getModel(), "--scorer", writer);
			if (ncm.requiresErrorModel()) {
				addScoring(ncm.getErrorModel(), "--error-scorer",writer);
			}
			addPvalCalc(icp.getPValueCalculator(),writer);

		} else if (pred instanceof TCPClassifier) {
			TCPClassifier tcp = (TCPClassifier) pred;
			NCMMondrianClassification ncm = tcp.getNCM();

			addNCM(ncm, writer);
			addScoring(ncm.getModel(), "--scorer", writer);
			addPvalCalc(tcp.getPValueCalculator(), writer);

		} else if (pred instanceof AVAPClassifier) {
			AVAPClassifier avap = (AVAPClassifier) pred;

			addSS(avap.getStrategy(), writer);
			addScoring(avap.getScoringAlgorithm(), "--scorer", writer);
		}

	}

	private static void addNCM(NCM ncm, PrintWriter writer) {
		writer.println("--ncm");

		StringBuilder sb = new StringBuilder();
		sb.append(ncm.getID());
		if (ncm instanceof NCMMondrianClassification) {
			Map<String,Object> props = ncm.getProperties();
			Set<String> keys = filter(props.keySet(), getConfigNames(ncm.getConfigParameters()), ncm.getModel().getProperties().keySet());
			appendKeyVal(sb, keys, props);
		} else if (ncm instanceof NCMRegression) {
			Map<String,Object> props = ncm.getProperties();

			Set<String> toExcl = new HashSet<>(ncm.getModel().getProperties().keySet());
			if (((NCMRegression) ncm).requiresErrorModel()) {
				toExcl.addAll(NCMUtils.addErrorModelPrefix((((NCMRegression) ncm).getErrorModel().getProperties())).keySet());
			}

			Set<String> keys = filter(props.keySet(), getConfigNames(ncm.getConfigParameters()), toExcl);

			appendKeyVal(sb, keys, props);
		}

		writer.println(sb.toString());
	}

	private static void appendKeyVal(StringBuilder sb, Set<String> keys, Map<String,Object> map) {
		for (String key : keys) {
			if (map.containsKey(key))
				sb.append(':').append(key).append('=').append(map.get(key));
		}
	}

	private static void addPvalCalc(PValueCalculator pCalc,PrintWriter writer) {
		writer.println("--pvalue-calc");
		writer.println(pCalc.getID());
	}

	static void addScoring(MLAlgorithm alg, String flag, PrintWriter writer) {
		writer.println(flag);
		StringBuilder sb = new StringBuilder();
		sb.append(alg.getID());
		Map<String, Object> props = alg.getProperties();

		appendKeyVal(sb, filter(props.keySet(), getConfigNames(alg.getConfigParameters()), new HashSet<>()), props);

		writer.println(sb.toString());
	}

	private static void addSS(SamplingStrategy ss, PrintWriter writer) {
		writer.println("--sampling-strategy");
		StringBuilder sb = new StringBuilder();
		sb.append(ss.getID());
		Map<String, Object> props = ss.getProperties();
		appendKeyVal(sb, filter(props.keySet(), getConfigNames(ss.getConfigParameters()), new HashSet<>()), props);
		writer.println(sb.toString());
	}

	static Set<String> getConfigNames(Collection<ConfigParameter> params){
		Set<String> pNames = new HashSet<>();
		for(ConfigParameter p : params) {
			pNames.addAll(p.getNames());
		}
		return pNames;
	}

	static Set<String> filter(Set<String> allKeys,Set<String> toInclude, Set<String> toExclude){
		Set<String> returnSet = new HashSet<>();
		for (String key : allKeys) {
			if (CollectionUtils.containsIgnoreCase(toInclude, key)) {
				if (!CollectionUtils.containsIgnoreCase(toExclude, key)) {
					returnSet.add(key);
				}
			}
		}
		return returnSet;
	}


}
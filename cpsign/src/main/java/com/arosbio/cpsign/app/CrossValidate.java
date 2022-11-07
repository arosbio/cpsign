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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cheminf.ChemClassifier;
import com.arosbio.cheminf.ChemPredictor;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.cpsign.app.params.mixins.ConsoleVerbosityMixin;
import com.arosbio.cpsign.app.params.mixins.EchoMixin;
import com.arosbio.cpsign.app.params.mixins.LogfileMixin;
import com.arosbio.cpsign.app.params.mixins.OverallStatsMixinClasses;
import com.arosbio.cpsign.app.params.mixins.PrecomputedDatasetMixin;
import com.arosbio.cpsign.app.params.mixins.PredictorMixinClasses;
import com.arosbio.cpsign.app.params.mixins.ProgramProgressMixin;
import com.arosbio.cpsign.app.params.mixins.RNGSeedMixin;
import com.arosbio.cpsign.app.params.mixins.TestingStrategyMixin;
import com.arosbio.cpsign.app.params.mixins.TransformerMixin;
import com.arosbio.cpsign.app.params.mixins.ValidationPointsMixin;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIProgramUtils;
import com.arosbio.cpsign.app.utils.CLIProgressBar;
import com.arosbio.cpsign.app.utils.NullProgress;
import com.arosbio.cpsign.app.utils.ProgramTimer;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.CLIProgressBar.SupportsProgressBar;
import com.arosbio.cpsign.out.OutputNamingSettings;
import com.arosbio.cpsign.out.OutputNamingSettings.PB;
import com.arosbio.cpsign.out.OutputNamingSettings.ProgressInfoTexts;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.ml.interfaces.ClassificationPredictor;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.ml.testing.TestRunner.UnsupportedPredictorException;

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
		name=CrossValidate.CMD_NAME,
		aliases=CrossValidate.CMD_ALIAS,
		description = CrossValidate.CMD_DESCRIPTION, 
		descriptionHeading = CPSignApp.DESCRIPTION_HEADER, 
		header = CrossValidate.CMD_HEADER
		)
public class CrossValidate implements RunnableCmd, SupportsProgressBar {

	private static final Logger LOGGER = LoggerFactory.getLogger(CrossValidate.class);

	public static final String CMD_NAME = "crossvalidate";
	public final static String CMD_ALIAS = "cv";
	public final static String CMD_HEADER = "Internal testing of a given data set";
	public final static String CMD_DESCRIPTION = "Runs an evaluation of the dataset and the given parameters, using a test-strategy (supports k-fold cross-validation, leave-one-out cross-validation and " + 
			"a single test-train split). This will give an estimate of the efficiency and validity, given this dataset and these settings. Input to @|bold crossvalidate|@ is a precomputed data set from @|bold " + Precompute.CMD_NAME+"|@.";

	/*****************************************
	 * INTERNAL STATE
	 *****************************************/
	
	@Spec private CommandSpec spec;
	private CLIConsole console = CLIConsole.getInstance();
	private CLIProgressBar pb = new NullProgress();
	private ProgramTimer timer = new ProgramTimer(false, console);
	private EncryptionSpecification encryptSpec = null;

	/*****************************************
	 * OPTIONS
	 *****************************************/
	
	// Input
	@Mixin
	private PrecomputedDatasetMixin input;

	// Predictor (ACP/CCP/TCP, strats,....)
	@Mixin
	private PredictorMixinClasses.AllPTMixin predictorSection;

	// Cross validation 
	@Mixin
	private TestingStrategyMixin testStrat = new TestingStrategyMixin();

	@Mixin
	private ValidationPointsMixin validation = new ValidationPointsMixin();

	// Transformer section
	@Mixin
	private TransformerMixin transformersSection;

	@Mixin
	private OverallStatsMixinClasses.StatsFile statsOutputFile = new OverallStatsMixinClasses.StatsFile();

	@Mixin
	private OverallStatsMixinClasses.JSONTextCSV_TSV resultOutputFormat = new OverallStatsMixinClasses.JSONTextCSV_TSV();

	@Option(names = {"--roc"},
			description = "Output the ROC curve (VAP only), the ROC curve has many points and lead to verbose output. Default is to only print the AUC score")
	private boolean outputROC = false;

	@Mixin
	private ProgramProgressMixin pbArgs;

	@Mixin
	private ConsoleVerbosityMixin consoleArgs;

	@Mixin
	private RNGSeedMixin seedArgs; 

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
		LOGGER.debug("Validated arguments");
		console.println("Using RNG seed: %s", PrintMode.VERBOSE, GlobalConfig.getInstance().getRNGSeed());
		pb.stepProgress();
		timer.endSection();


		// Init predictor + load data
		pb.setCurrentTask(PB.LOADING_FILE_OR_MODEL_PROGRESS);
		ChemPredictor predictor = initPredictor();
		loadData(predictor);
		pb.stepProgress();
		timer.endSection();

		// Verify that there is no empty features
		if (predictor.checkIfDatasetsContainMissingFeatures()) {
			LOGGER.debug("SubSet contain missing features - cannot continue");
			console.failWithArgError("SubSet contains missing data - please revise your pre-processing");
		}


		// Do testing 
		pb.setCurrentTask(PB.CROSSVALIDATING_PROGRESS);
		List<Metric> cvResult = crossvalidate(predictor);
		pb.stepProgress();
		timer.endSection();

		// PRINT STATS
		pb.setCurrentTask(PB.RESULTS_WRITING_PROGRESS);
		CLIProgramUtils.printResults(cvResult, statsOutputFile.overallStatsFile, 
				resultOutputFormat.outputFormat, outputROC, (predictor instanceof ChemClassifier ? ((ChemClassifier)predictor).getNamedLabels() : null), console);
		pb.stepProgress();
		timer.endSection();

		// FINISH PROGAM
		pb.finish();
		timer.endProgram();
		console.println("", PrintMode.NORMAL);
		return ExitStatus.SUCCESS.code;

	}

	@SuppressWarnings("null")
	private ChemPredictor initPredictor() {
		ChemPredictor predictor = null;

		try {
			predictor = CLIProgramUtils.getSignaturesPredictor(
					predictorSection.getPredictor(console), console);
		} catch (Exception e) {
			LOGGER.debug("Failed init the predictor",e);
			console.failWithArgError("Failed setting up predictor with given parameters: " + e.getMessage());
		}
		LOGGER.debug("Init ChemPredictor of class {}, using predictor of class: {}", predictor.getClass(), predictor.getPredictor().getClass());
		return predictor;
	}

	private void loadData(ChemPredictor predictor) {
		// Load precomputed data set
		try {
			CLIProgramUtils.loadPrecomputedData(predictor, 
					input, encryptSpec, console);
		} catch (IllegalArgumentException e){
			LOGGER.debug("Error parsing as ChemFile: ", e);
			console.failWithArgError(e.getMessage());
		} catch (Exception e){
			LOGGER.debug("Error parsing as ChemFile: ", e);
			console.failWithArgError(e.getMessage());
		}

		// Do transformations
		CLIProgramUtils.applyTransformations(predictor.getDataset(), predictor instanceof ClassificationPredictor, transformersSection.transformers, this, console);

		if (predictor.checkIfDatasetsContainMissingFeatures()) {
			LOGGER.debug("Missing data encountered before running crossvalidations");
			console.failWithArgError("Training data contains missing data for one or multiple features, please revise your pre-processing prior to training");
		}
	}

	private void validateParams() {

		CLIProgramUtils.setupOverallStatsFile(statsOutputFile.overallStatsFile, console);

	}

	private List<Metric> crossvalidate(ChemPredictor signPred) {

		console.print("Starting the validation using %s strategy... ", PrintMode.NORMAL,testStrat.testStrategy);

		List<Metric> cvResult=null;
		try {
			LOGGER.debug("Starting crossvalidate with testing strategy={}, conf={}",
					testStrat.testStrategy.toString(), validation.calibrationPoints);

			TestRunner runner = new TestRunner.Builder(testStrat.testStrategy).build();

			List<Metric> metrics = CLIProgramUtils.setupMetrics(signPred, validation);

			LOGGER.debug("Running CV with metrics: {}", metrics);

			cvResult = runner.evaluate(signPred.getDataset(), signPred.getPredictor(), metrics);

		} catch (IllegalArgumentException e ) {
			LOGGER.debug("Failed due to faulty parameters", e);
			console.failWithArgError(e.getMessage());
		} catch (UnsupportedPredictorException e) {
			LOGGER.debug("Called crossvalidate with predictor of unsupported class: {}", signPred.getPredictor().getClass());
			console.failWithArgError("Crossvalidation not supported for this predictor type");
		} catch (Exception e) {
			LOGGER.debug("Failed execution of cross-validate due to exception",e);
			console.failWithInternalError("Failed performing test-evaluation, reason: "+e.getMessage());
		}

		console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);

		return cvResult;
	}

}

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

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.chem.io.in.ProgressTracker;
import com.arosbio.cheminf.ChemCPClassifier;
import com.arosbio.cheminf.ChemPredictor;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.cpsign.app.params.mixins.CompoundsToPredictMixin;
import com.arosbio.cpsign.app.params.mixins.ConfidencesListMixin;
import com.arosbio.cpsign.app.params.mixins.ConsoleVerbosityMixin;
import com.arosbio.cpsign.app.params.mixins.EarlyTerminationMixin;
import com.arosbio.cpsign.app.params.mixins.EchoMixin;
import com.arosbio.cpsign.app.params.mixins.EncryptionMixin;
import com.arosbio.cpsign.app.params.mixins.ListFailedRecordsMixin;
import com.arosbio.cpsign.app.params.mixins.LogfileMixin;
import com.arosbio.cpsign.app.params.mixins.OutputChemMixin;
import com.arosbio.cpsign.app.params.mixins.PercentilesMixin;
import com.arosbio.cpsign.app.params.mixins.PrecomputedDatasetMixin;
import com.arosbio.cpsign.app.params.mixins.PredictorMixinClasses;
import com.arosbio.cpsign.app.params.mixins.ProgramProgressMixin;
import com.arosbio.cpsign.app.params.mixins.RNGSeedMixin;
import com.arosbio.cpsign.app.params.mixins.TransformerMixin;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.CLIConsole.VerbosityLvl;
import com.arosbio.cpsign.app.utils.CLIProgramUtils;
import com.arosbio.cpsign.app.utils.CLIProgressBar;
import com.arosbio.cpsign.app.utils.CLIProgressBar.SupportsProgressBar;
import com.arosbio.cpsign.app.utils.NullProgress;
import com.arosbio.cpsign.app.utils.PredictRunner;
import com.arosbio.cpsign.app.utils.ProgramTimer;
import com.arosbio.cpsign.out.OutputNamingSettings;
import com.arosbio.cpsign.out.OutputNamingSettings.PB;
import com.arosbio.cpsign.out.OutputNamingSettings.ProgressInfoTexts;
import com.arosbio.cpsign.out.PredictionImageHandler.GradientImageOpts;
import com.arosbio.cpsign.out.PredictionImageHandler.SignificantSignatureImageOpts;
import com.arosbio.data.MissingDataException;
import com.arosbio.io.UriUtils;
import com.arosbio.ml.cp.ConformalClassifier;
import com.arosbio.ml.cp.tcp.TCPClassifier;
import com.arosbio.ml.interfaces.Predictor;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/*
 * PB.VALIDATE_PARAMS_PROGRESS
 * PB.LOADING_FILE_OR_MODEL_PROGRESS
 * PB.TRAINING_PROGRESS
 * PB.COMPUTING_PERCENTILES_PROGRESS
 * PB.PREDICTING_PROGRESS
 * @author staffan
 *
 */
@Command(
		name=PredictOnline.CMD_NAME,
		aliases=PredictOnline.CMD_ALIAS,
		description = PredictOnline.CMD_DESCRIPTION, 
		descriptionHeading = CPSignApp.DESCRIPTION_HEADER, 
		header = PredictOnline.CMD_HEADER
		)
public class PredictOnline implements RunnableCmd, SupportsProgressBar {

	private static final Logger LOGGER = LoggerFactory.getLogger(PredictOnline.class);
	public final static String CMD_NAME = "predict-online";
	public final static String CMD_ALIAS = "online-predict";
	public final static String CMD_HEADER = "Train and predict new compounds online";
	public final static String CMD_DESCRIPTION = "Train and predict new examples on the fly. Will not save the model. Currently only available for TCP.";

	/*****************************************
	 * INTERNAL STATE
	 *****************************************/
	@Spec private CommandSpec spec;
	private CLIConsole console = CLIConsole.getInstance();
	private CLIProgressBar pb = new NullProgress();
	private ProgramTimer timer = new ProgramTimer(false, console);

	/*****************************************
	 * OPTIONS
	 *****************************************/

	// Input
	@Mixin
	private PrecomputedDatasetMixin inputSection;

	// To predict
	@Mixin
	private CompoundsToPredictMixin toPredict;

	@Mixin
	private EarlyTerminationMixin earlyTermination = new EarlyTerminationMixin();

	@Mixin
	private ListFailedRecordsMixin listFailedRecordsMixin = new ListFailedRecordsMixin();

	// Input data to predict
	@Mixin
	private ConfidencesListMixin confs;

	@Option(
			names = {"-cg", "--calculate-gradient"}, 
			description = "Calculate the gradient and the most significant signature of the molecules")
	private boolean calcGradient = false;


	@Mixin
	private PredictorMixinClasses.TCPMixin modelingParams; 

	@Mixin
	private PercentilesMixin percentilesArgs = new PercentilesMixin();

	// Transformers section
	@Mixin
	private TransformerMixin transformerArgs;

	@Mixin
	private OutputChemMixin outputSection; 

	@Mixin
	private EncryptionMixin encryptSection = new EncryptionMixin();

	@Mixin
	private GradientImageOpts gradientImageSection;

	@Mixin
	private SignificantSignatureImageOpts signatureImageSection;

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
		return 5;
	}

	@Override
	public void setProgressBar(CLIProgressBar pb) {
		this.pb = pb;
	}

	@Override
	public Integer call() {

		CLIProgramUtils.doFullProgramConfig(this);

		console.print(OutputNamingSettings.ProgressInfoTexts.VALIDATING_ARGS, PrintMode.NORMAL);
		pb.setCurrentTask(PB.VALIDATE_PARAMS_PROGRESS);
		verifyGeneralParams();
		LOGGER.debug("Validated arguments");

		// FINISHED SETUP
		console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
		console.println("Using RNG seed: %d", PrintMode.VERBOSE, GlobalConfig.getInstance().getRNGSeed());
		pb.stepProgress();
		timer.endSection();

		// LOAD DATA
		pb.setCurrentTask(PB.LOADING_FILE_OR_MODEL_PROGRESS);
		ChemCPClassifier predictor = initAndLoad();
		pb.stepProgress();
		timer.endSection();

		// TRAIN PREDICTOR
		pb.setCurrentTask(PB.TRAINING_PROGRESS);
		train(predictor);
		pb.stepProgress();
		timer.endSection();

		// COMPUTE PERCENTILES
		computePercentiles(predictor);
		pb.stepProgress();

		// PREDICT
		pb.setCurrentTask(PB.PREDICTING_PROGRESS);
		console.println(OutputNamingSettings.ProgressInfoTexts.COMPUTING_PREDICTIONS, PrintMode.NORMAL);
		new PredictRunner(this, console, pb, predictor, toPredict, ProgressTracker.createStopAfter(earlyTermination.maxFailuresAllowed), 
			confs, null, outputSection, gradientImageSection, signatureImageSection, calcGradient, listFailedRecordsMixin.listFailedRecords).runPredict();
		pb.stepProgress();
		timer.endSection();

		// FINISH PROGRAM 
		pb.finish();
		timer.endProgram();
		console.println("", PrintMode.NORMAL);
		return ExitStatus.SUCCESS.code;

	}


	private ChemCPClassifier initAndLoad() {
		LOGGER.debug("Initializing Predictor");
		// Initiate ChemPredictor
		ChemCPClassifier chemPredictor = initChemCPClassificationTCP();

		// Load data
		try {
			CLIProgramUtils.loadPrecomputedData(chemPredictor, inputSection, encryptSection.exclusive.encryptSpec, console);
		} catch (Exception e) {
			LOGGER.debug("Failed loading data to 'train' online predictor",e);
			throw e;
		}

		// Do transformations
		CLIProgramUtils.applyTransformations(chemPredictor.getDataset(), true, transformerArgs.transformers, this, console);

		// Verify no missing data
		CLIProgramUtils.verifyNoMissingDataAndPrintErr(chemPredictor.getDataset(), true, console);

		return chemPredictor;
	}

	private void train(ChemPredictor predictor) {
		try {
			console.print("Setting up TCP predictor... ", PrintMode.NORMAL);
			predictor.train();
			console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
		} catch (MissingDataException mde) {
			LOGGER.debug("Missing data exception running train",mde);
			console.failWithArgError("Failed training due to missing data for one or multiple features, please revise your pre-processing prior to training");
		} catch(IllegalArgumentException e) {
			LOGGER.debug("Failed training predictor", e);
			console.failWithArgError(e.getMessage());
		} catch (Exception e) {
			LOGGER.debug("Could not 'train' Signatures TCP model",e);
			console.failWithInternalError();
		}
	}

	private void computePercentiles(ChemPredictor predictor) {

		if (percentilesArgs.maxNumMolsForPercentiles <= 0) {
			LOGGER.debug("No computation of percentiles - set to 0");
		}

		else if (!calcGradient) {
			LOGGER.debug("No computation of percentiles - calc gradient not set to true");
		}

		else {

			console.print(OutputNamingSettings.ProgressInfoTexts.COMPUTING_PERCENTILES, PrintMode.NORMAL);
			pb.setCurrentTask(PB.COMPUTING_PERCENTILES_PROGRESS);
			try {
				predictor.computePercentiles(percentilesArgs.percentilesFile.getIterator(), percentilesArgs.maxNumMolsForPercentiles);
			} catch(Exception e) {
				LOGGER.debug("Failed computing percentiles",e);
				console.failWithInternalError("Failed computing percentiles due to: " + e.getMessage());
			}
			console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
			timer.endSection();

			if (calcGradient && !predictor.hasValidPercentiles()) {
				LOGGER.error("set to calculate the gradient, but the predictor does not have valid percentiles ({},{})",
						predictor.getLowPercentile(), predictor.getHighPercentile());
				console.failWithArgError(CLIProgramUtils.INVALID_PERCENTILES_ERROR);
			}
		} 
	}



	private ChemCPClassifier initChemCPClassificationTCP() {

		Predictor tcp = modelingParams.getPredictor(console);
		if (! (tcp instanceof TCPClassifier)) {
			LOGGER.error("Something went wrong in getPredictor for TCPMixin, got predictor of class: {}", tcp.getClass());
			console.failWithInternalError("Internal error - " + CMD_NAME + " only possible with TCP currently");
		}

		ChemCPClassifier tcpModel = new ChemCPClassifier((ConformalClassifier) tcp);

		LOGGER.debug("Finished initiating ChemCPClassifier object for TCP");
		return tcpModel;
	}

	private void verifyGeneralParams(){

		// if predicting to std-out and compress - must be silentMode to get correct results
		if (outputSection.outputFile==null && 
				outputSection.compress && 
				console.getVerbosity() != VerbosityLvl.SILENT) {
			console.failWithArgError("Need to run with flag "+ 
					CLIProgramUtils.getParamName(this, "silentMode", "SILENT_MODE")+
					" when printing results to standard out in compressed format");
		}

		// If we're writing images, we must predict the significant signature!
		if (gradientImageSection.createImgs || 
				signatureImageSection.createImgs)
			calcGradient = true;

		// Verify that parent-folders exists of output file (create them otherwise)
		if (outputSection.outputFile != null){
			try{
				UriUtils.createParentOfFile(outputSection.outputFile);
			} catch (IOException e){
				LOGGER.debug("failed when trying to create parent of output-file",e);
				console.failWithArgError("Could not create parent directory of output file:%n" + outputSection.outputFile);
			}
		}

		// Set up inchi-factory
		if (outputSection.printInChI) {
			try{
				CPSignMolProperties.setupInChIGenerator();
			} catch(Exception e){
				console.failWithArgError("InChI cannot be generated, please contact Aros Bio or run without the " + 
						CLIProgramUtils.getParamName(this, "printInChI", "PRINT_INCHI") + " flag");
			}
		}

	}


}
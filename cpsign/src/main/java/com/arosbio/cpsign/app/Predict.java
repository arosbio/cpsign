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
import java.net.URI;
import java.security.InvalidKeyException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.chem.io.in.ProgressTracker;
import com.arosbio.cheminf.ChemPredictor;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.cpsign.app.params.mixins.CompoundsToPredictMixin;
import com.arosbio.cpsign.app.params.mixins.ConfidencesListMixin;
import com.arosbio.cpsign.app.params.mixins.ConsoleVerbosityMixin;
import com.arosbio.cpsign.app.params.mixins.EarlyTerminationMixin;
import com.arosbio.cpsign.app.params.mixins.EchoMixin;
import com.arosbio.cpsign.app.params.mixins.EncryptionMixin;
import com.arosbio.cpsign.app.params.mixins.ListFailedRecordsMixin;
import com.arosbio.cpsign.app.params.mixins.LogfileMixin;
import com.arosbio.cpsign.app.params.mixins.OutputChemMixin;
import com.arosbio.cpsign.app.params.mixins.ProgramProgressMixin;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.CLIConsole.VerbosityLvl;
import com.arosbio.cpsign.app.utils.CLIProgramUtils;
import com.arosbio.cpsign.app.utils.CLIProgressBar;
import com.arosbio.cpsign.app.utils.CLIProgressBar.SupportsProgressBar;
import com.arosbio.cpsign.app.utils.MissingParam;
import com.arosbio.cpsign.app.utils.NullProgress;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.cpsign.app.utils.PredictRunner;
import com.arosbio.cpsign.app.utils.ProgramTimer;
import com.arosbio.cpsign.out.OutputNamingSettings;
import com.arosbio.cpsign.out.OutputNamingSettings.PB;
import com.arosbio.cpsign.out.OutputNamingSettings.ProgressInfoTexts;
import com.arosbio.cpsign.out.PredictionImageHandler.GradientImageOpts;
import com.arosbio.cpsign.out.PredictionImageHandler.SignificantSignatureImageOpts;
import com.arosbio.io.UriUtils;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/*
 * The sections are the following (3):
 * PB.VALIDATE_PARAMS_PROGRESS 
 * PB.LOADING_FILE_OR_MODEL_PROGRESS
 * PB.PREDICTING_PROGRESS
 * @author staffan
 *
 */
@Command(
		name = Predict.CMD_NAME, 
		description = Predict.CMD_DESCRIPTION, 
		descriptionHeading = CPSignApp.DESCRIPTION_HEADER, 
		header = Predict.CMD_HEADER
		)
public class Predict implements RunnableCmd, SupportsProgressBar{

	private static final Logger LOGGER = LoggerFactory.getLogger(Predict.class);
	public final static String CMD_NAME = "predict";
	public final static String CMD_HEADER = "Predict new compounds given a model";
	public final static String CMD_DESCRIPTION = "Predict new compounds given a trained model (derived from the @|bold train|@ program)."+
			" For TCP there is also the @|bold "+PredictOnline.CMD_NAME+"|@ program that can train models on the fly,"+
			"given a precomputed data set";

	
	/*****************************************
	 * INTERNAL STATE
	 *****************************************/
	
	@Spec private CommandSpec spec;
	private CLIConsole console = CLIConsole.getInstance();
	private CLIProgressBar pb = new NullProgress();
	private ProgramTimer timer = new ProgramTimer(false, console);

	// private PredictionImageHandler imageHandler;

	/*****************************************
	 * OPTIONS
	 *****************************************/

	// Input
	@Option(
			names = { "-m", "--model" }, 
			description = "Trained CPSign predictor model",
			required = true,
			paramLabel = ArgumentType.URI_OR_PATH
			)
	private URI modelFile;

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
			names = { "-pw", "--prediction-widths" }, 
			description = "(ACP regression only) Set prediction interval widths, and calculate the confidence needed to generate that prediction width (e.g. '0.5,2,5' or '0.5 2 5')",
			arity = ParameterUtils.LIST_TYPE_ARITY,
			paramLabel = ArgumentType.NUMBER,
			split = ParameterUtils.SPLIT_WS_COMMA_REGEXP
			)
	private List<Double> predictionWidths;

	@Option(
			names = {"-cg", "--calculate-gradient"}, 
			description = "Calculate the gradient and the most significant signature of the molecules")
	private boolean calcGradient = false;

	@Mixin
	private OutputChemMixin outputSection = new OutputChemMixin(); 

	@Mixin
	private EncryptionMixin encryptSection = new EncryptionMixin();

	@Mixin
	private GradientImageOpts gradientImageSection;

	@Mixin
	private SignificantSignatureImageOpts signatureImageSection;

	// General

	@Mixin
	private ProgramProgressMixin pbArgs;

	@Mixin
	private ConsoleVerbosityMixin consoleArgs;

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
		return 3;
	}

	@Override
	public void setProgressBar(CLIProgressBar pb) {
		this.pb = pb;
	}

	@Override
	public Integer call() {

		CLIProgramUtils.doFullProgramConfig(this);

		pb.setCurrentTask(PB.VALIDATE_PARAMS_PROGRESS);
		console.print(OutputNamingSettings.ProgressInfoTexts.VALIDATING_ARGS, PrintMode.NORMAL);

		verifyGenericParams();
		LOGGER.debug("Validated arguments");

		console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
		pb.stepProgress();
		timer.endSection();
		// -- FINISHED VALIDATION SECTION


		// LOAD MODEL
		pb.setCurrentTask(PB.LOADING_FILE_OR_MODEL_PROGRESS);
		ChemPredictor predictor = loadModel();
		pb.stepProgress();
		timer.endSection();

		// PREDICT
		pb.setCurrentTask(PB.PREDICTING_PROGRESS);
		console.println(OutputNamingSettings.ProgressInfoTexts.COMPUTING_PREDICTIONS, PrintMode.NORMAL);
		new PredictRunner(this, console, pb, predictor, toPredict, ProgressTracker.createStopAfter(earlyTermination.maxFailuresAllowed), 
			confs, predictionWidths, outputSection, gradientImageSection, signatureImageSection, calcGradient, listFailedRecordsMixin.listFailedRecords).runPredict();
		pb.stepProgress();
		timer.endSection();

		// FINISH PROGRAM
		pb.finish();
		timer.endProgram();
		console.println("", PrintMode.NORMAL);

		return ExitStatus.SUCCESS.code;

	}

	private ChemPredictor loadModel() {
		console.print(OutputNamingSettings.ProgressInfoTexts.LOADING_MODEL, PrintMode.NORMAL);

		ChemPredictor model=null;
		try {
			model = ModelSerializer.loadChemPredictor(modelFile, encryptSection.exclusive.encryptSpec);
		} catch (InvalidKeyException e) {
			LOGGER.debug("Failed loading model due to invalid-key-exception",e);
			if (encryptSection.exclusive.encryptSpec != null){
				console.failWithArgError("Model is encrypted, wrong key was given");
			} else {
				console.failWithArgError("Model is encrypted, must supply the key that can decrypt it!");
			}
			
		} catch (Exception e) {
			LOGGER.debug("Failed loading model",e);
			console.failWithArgError("Could not load model from: "+modelFile + "%nMessage: " + e.getMessage());
		}

		if (!model.getPredictor().isTrained()) {
			console.println(ProgressInfoTexts.FAILED_TAG, PrintMode.NORMAL);
			LOGGER.debug("Predictor was not trained - failing");
			console.failWithArgError("Given model was not trained - only a trained model can be used in predict");
		}
		console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);

		// Brief info about loaded model
		CLIProgramUtils.printInfoAboutLoadedModel(model, console);

		if (calcGradient && !model.hasValidPercentiles()) {
			LOGGER.error("set to calculate the gradient, but the predictor does not have valid percentiles ({},{})",
				model.getLowPercentile(), model.getHighPercentile());
			console.failWithArgError(CLIProgramUtils.INVALID_PERCENTILES_ERROR);
		}

		return model;
	}


	private void verifyGenericParams(){

		// modelfile must be given
		if (modelFile == null) {
			LOGGER.debug("Missing parameter modelFile");
			console.failDueToMissingParameters(new MissingParam("modelFile", "MODEL", Predict.class));
		}
		else if (! UriUtils.canReadFromURI(modelFile)) {
			LOGGER.debug("Cannot read from model-URI: {}", modelFile);
			console.failWithArgError("Parameter "+ CLIProgramUtils.getParamName(this, "modelFile", "MODEL_FILE") + 
					" cannot be read, given: "+modelFile);
		}

		// if predicting to std-out and compress - must be silentMode to get correct results
		if (outputSection.outputFile==null && 
				outputSection.compress && 
				console.getVerbosity() != VerbosityLvl.SILENT) {
			console.failWithArgError("Need to run with flag "+ 
					CLIProgramUtils.getParamName(this, "silentMode", "SILENT_MODE")+
					" when printing results to standard out in compressed format");
		}

		// Verify that parent-folders exists (create them otherwise)
		if (outputSection.outputFile != null){
			try{
				UriUtils.createParentOfFile(outputSection.outputFile);
			} catch (IOException e){
				LOGGER.debug("failed when trying to creat parent of output-file",e);
				console.failWithArgError("Could not create parent directory of output file:%n" + outputSection.outputFile);
			}
		}

		// If we're writing images, we must predict the significant signature!
		if (gradientImageSection.createImgs || 
				signatureImageSection.createImgs)
			calcGradient = true;

		// Set up inchi-factory
		if (outputSection.printInChI) {
			try {
				CPSignMolProperties.setupInChIGenerator();
			} catch (Exception e){
				console.failWithArgError("InChI cannot be generated, please contact Aros Bio or run without the " + 
						CLIProgramUtils.getParamName(this, "printInChI", "PRINT_INCHI") + " flag");
			}
		}
	}

}
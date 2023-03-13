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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.chem.io.in.ChemFileParserUtils;
import com.arosbio.chem.io.in.FailedRecord;
import com.arosbio.cheminf.ChemCPClassifier;
import com.arosbio.cheminf.ChemCPRegressor;
import com.arosbio.cheminf.ChemPredictor;
import com.arosbio.cheminf.ChemVAPClassifier;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.commons.MathUtils;
import com.arosbio.commons.TypeUtils;
import com.arosbio.cpsign.app.params.mixins.CompoundsToPredictMixin;
import com.arosbio.cpsign.app.params.mixins.ConfidencesListMixin;
import com.arosbio.cpsign.app.params.mixins.ConsoleVerbosityMixin;
import com.arosbio.cpsign.app.params.mixins.EchoMixin;
import com.arosbio.cpsign.app.params.mixins.EncryptionMixin;
import com.arosbio.cpsign.app.params.mixins.LogfileMixin;
import com.arosbio.cpsign.app.params.mixins.OutputChemMixin;
import com.arosbio.cpsign.app.params.mixins.ProgramProgressMixin;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIConsole.ParamComb;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.CLIConsole.VerbosityLvl;
import com.arosbio.cpsign.app.utils.CLIProgramUtils;
import com.arosbio.cpsign.app.utils.CLIProgressBar;
import com.arosbio.cpsign.app.utils.CLIProgressBar.SupportsProgressBar;
import com.arosbio.cpsign.app.utils.MissingParam;
import com.arosbio.cpsign.app.utils.NullProgress;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.cpsign.app.utils.ProgramTimer;
import com.arosbio.cpsign.out.OutputNamingSettings;
import com.arosbio.cpsign.out.OutputNamingSettings.PB;
import com.arosbio.cpsign.out.OutputNamingSettings.ProgressInfoTexts;
import com.arosbio.cpsign.out.PredictionImageHandler;
import com.arosbio.cpsign.out.PredictionImageHandler.GradientImageOpts;
import com.arosbio.cpsign.out.PredictionImageHandler.SignificantSignatureImageOpts;
import com.arosbio.cpsign.out.PredictionResultsWriter;
import com.arosbio.cpsign.out.ResultsHandler;
import com.arosbio.data.MissingDataException;
import com.arosbio.io.UriUtils;
import com.arosbio.ml.ClassificationUtils;
import com.arosbio.ml.vap.avap.CVAPPrediction;

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

	private PredictionImageHandler imageHandler;

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
	
	@Option(names = {"--list-failed"},
			description = "List @|bold all|@ failed molecules, such as invalid records, molecules removed due to Heavy Atom Count or failures at descriptor calculation. "+
			"The default is otherwise to only list the summary of the number of failed records.")
	private boolean listFailedMolecules = false;

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

	// State of the current execution
	private int molIterationCounter = 0; // for images/output text
	private int numSuccessfulPreds = 0;
	private int numMissingDataFails = 0;
	private List<FailedRecord> failedRecords = new ArrayList<>();
	private int progressInterval = 0;
	private int totalNumMolsToPredict = 0;
	

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


		// if Predict from an input file
		if (toPredict.toPredict.predictFile!=null){

			try {
				totalNumMolsToPredict = toPredict.toPredict.predictFile.countNumRecords();
			} catch (IOException e){
				LOGGER.debug("Could not parse the predictFile", e);
				console.failWithArgError("Could not read molecules from "+
						toPredict.toPredict.predictFile.getURI());
			}

			if (totalNumMolsToPredict < 1) {
				console.failWithArgError("Could not read molecules from "+
						toPredict.toPredict.predictFile.getURI());
			}

			totalNumMolsToPredict += (toPredict.toPredict.smilesToPredict!=null? 1 : 0);

			progressInterval = CLIProgramUtils.getProgressInterval(totalNumMolsToPredict, 10);
			int numSteps = totalNumMolsToPredict/progressInterval;
			pb.addAdditionalSteps(numSteps); 
		}

		

		console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
		pb.stepProgress();
		timer.endSection();


		// -- FINISHED VALIDATION SECTION


		// LOAD MODEL
		pb.setCurrentTask(PB.LOADING_FILE_OR_MODEL_PROGRESS);
		ChemPredictor predictor = loadModel();
		// Setup prediction depictions
		imageHandler = new PredictionImageHandler(
				gradientImageSection, 
				signatureImageSection,
				predictor);
		if (imageHandler.isUsed() && !predictor.usesSignaturesDescriptor()) {
			LOGGER.debug("Loaded model without signatures descriptor and got argument for generating prediction images - failing!");
			console.failWithArgError("Generating prediction images is only available when using the signatures descriptor");
		}
		pb.stepProgress();
		timer.endSection();

		// PREDICT
		pb.setCurrentTask(PB.PREDICTING_PROGRESS);
		console.println(OutputNamingSettings.ProgressInfoTexts.COMPUTING_PREDICTIONS, PrintMode.NORMAL);
		predict(predictor, totalNumMolsToPredict, progressInterval);
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
			
		} catch(IllegalArgumentException | IOException e) {
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

	private void predict(ChemPredictor sp, int molsInPredFile, int progressInterval) {

		// Set up the ResultsOutputter
		try(
			PredictionResultsWriter predictionsWriter = CLIProgramUtils.setupResultsOutputter(this, 
				outputSection.outputFormat, 
				outputSection.outputFile,
				toPredict.toPredict.predictFile,
				outputSection.printInChI, 
				outputSection.compress);){

			doPredict(sp, predictionsWriter, molsInPredFile, progressInterval);

		} catch (IOException e) {
			LOGGER.debug("Failed closing the results outputter",e);
			console.printlnWrappedStdErr("Could not properly close the result file, not all predictions might have successfully have been writen to file", 
					PrintMode.NORMAL);
		}

		// We are done with all predictions
		String molSingularOrPlural=(numSuccessfulPreds>1 || numSuccessfulPreds==0?"s":"");
		console.println("%nSuccessfully predicted %s molecule%s", PrintMode.NORMAL,numSuccessfulPreds,molSingularOrPlural);
		
		// If we had some failing records
		if (!failedRecords.isEmpty()) {
			console.println("Failed predicting %s record%s", PrintMode.NORMAL,
					failedRecords.size(),(failedRecords.size()>1 ? "s":""));
			
			if (numMissingDataFails >0) {
				console.println("%s record(s) failed due to missing features - please make sure your data pre-processing is correct, consider using e.g. removal of poor descriptors (DropMissingDataFeatures) or impute missing features (SingleFeatImputer)", 
						PrintMode.VERBOSE, numMissingDataFails);
			}
			
			if (listFailedMolecules) {
				StringBuilder failedStr = new StringBuilder("Failed the following record(s):%n");
				for (int i=0; i< failedRecords.size()-1; i++) {
					failedStr.append(failedRecords.get(i));
					failedStr.append("%n");
				}
				failedStr.append(failedRecords.get(failedRecords.size()-1));
				console.println(failedStr.toString(), PrintMode.NORMAL);
			}
		}

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

		// If we're writing images, we must predict the significant signature!
		if (gradientImageSection.createImgs || 
				signatureImageSection.createImgs)
			calcGradient = true;

		// Verify that parent-folders exists (create them otherwise)
		if (outputSection.outputFile != null){
			try{
				UriUtils.createParentOfFile(outputSection.outputFile);
			} catch (IOException e){
				LOGGER.debug("failed when trying to creat parent of output-file",e);
				console.failWithArgError("Could not create parent directory of output file: " + outputSection.outputFile);
			}
		}

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

	@SuppressWarnings("null")
	private void doPredict(ChemPredictor predictor, PredictionResultsWriter predWriter, int numMolsInPredFile, int progressInterval) {

		// Verify confidence / predictionWidths for regression are given
		if (predictor instanceof ChemCPRegressor) {
			if ((confs.confidences == null || confs.confidences.isEmpty()) &&
					(predictionWidths == null || predictionWidths.isEmpty())) {
				LOGGER.debug("No confidences or predictionWidths given when predicting regression - fail!");
				console.failDueToMissingParameters(ParamComb.AND_OR, "must be given when doing CP regression", 
						new MissingParam("setConfidences","CONFIDENCES", Predict.class),
						new MissingParam("predictionWidths","PREDICTION_WIDTHS", Predict.class));
			}
		}
		
		// Predict single SMILES
		if (toPredict.toPredict.smilesToPredict != null) {
			LOGGER.debug("Predicting single --smiles molecule");

			IAtomContainer mol;
			final String smilesID = "cmd-line-input";
			try {
				mol = ChemFileParserUtils.parseSMILES(toPredict.toPredict.smilesToPredict); // we do CDKConfigureAtomContainer.applyAromaticitySuppressHydrogens within this method
				CPSignMolProperties.setRecordIndex(mol,smilesID);

				predictMolecule(predictor,predWriter, mol);
				predWriter.flush();
				numSuccessfulPreds++;

			} catch (InvalidSmilesException e) { 
				LOGGER.debug("Invalid smiles sent as input: {}", toPredict.toPredict.smilesToPredict);
				console.failWithArgError("Input to parameter --smiles '"+toPredict.toPredict.smilesToPredict+"' could not be parsed as a valid SMILES");
				failedRecords.add(new FailedRecord(-1,smilesID).setReason(e.getMessage()));
			} catch (CDKException e){ 
				LOGGER.debug("Got a CDKException predicting the --smiles molecule",e);
				LOGGER.error("Dataset when predicting the --smiles molecule");
				failedRecords.add(new FailedRecord(-1,smilesID).setReason(e.getMessage()));
				numMissingDataFails++;
			} catch (Exception e) {
				LOGGER.debug("Exception trying to predict SMILES, stacktrace: ", e);
				LOGGER.error("Error predicting SMILES: {}", e.getMessage());
				failedRecords.add(new FailedRecord(-1,smilesID).setReason(e.getMessage()));
			} finally {
				// Step Progress and print out
				printPredictionProgressAndStep();
			}
		}

		// Predict from a SMILES- or SDF-file
		if (numMolsInPredFile > 0){
			LOGGER.debug("Predicting from an input file: {}",toPredict.toPredict.predictFile.getURI());

			Iterator<IAtomContainer> molIterator=null;
			try {
				molIterator = toPredict.toPredict.predictFile.getIterator();
			} catch (IOException e) {
				LOGGER.debug("Failed reading from predictFile",e);
				console.failWithArgError("Could not read any molecules from parameter " + 
						CLIProgramUtils.getParamName(this, "predictFile", "PREDICT_FILE"));
			}

			// DO THE PREDICTIONS

			boolean predictionFromFileDone = false;
			IAtomContainer mol=null;

			while (molIterator.hasNext()) {
				mol = molIterator.next();
				if (mol.getProperty(CDKConstants.REMARK) == null)
					mol.removeProperty(CDKConstants.REMARK);
				int index = TypeUtils.asInt(CPSignMolProperties.getRecordIndex(mol));
				String id = (CPSignMolProperties.hasMolTitle(mol)? CPSignMolProperties.getMolTitle(mol) : null);
				
				try {

					predictMolecule(predictor, predWriter, mol);
					predWriter.flush();
					
					predictionFromFileDone=true;
					numSuccessfulPreds++;

				} catch (IllegalAccessException e) {
					// IllegalAccess is due to model-issues and not specific for the molecule - then fail complete run
					LOGGER.debug("IllegalAccessException", e);
					console.failWithInternalError("Failed predicting molecule due to: " + e.getMessage());
				} catch (MissingDataException e) {
					LOGGER.debug("Got a missing data exception - something wrong i pre-processing?");
					failedRecords.add(new FailedRecord(index,id).setReason(e.getMessage()));
					numMissingDataFails++;
				} catch (Exception e) {
					LOGGER.debug("Failed molecule due to generic exception", e);
					failedRecords.add(new FailedRecord(index,id).setReason(e.getMessage()));
				} finally {
					molIterationCounter++;
					
					// Step Progress and print out
					printPredictionProgressAndStep();
				}
			}

			if (!predictionFromFileDone)
				console.failWithNoMoleculesCouldBeLoaded(toPredict.toPredict.predictFile);

		}

	}

	private void printPredictionProgressAndStep() {
		if (progressInterval > 0 && molIterationCounter % progressInterval == 0) {
			console.println(" - Processed %s/%s molecules", 
					PrintMode.NORMAL,molIterationCounter,totalNumMolsToPredict);
			pb.stepProgress();
		}
	}

	private void predictMolecule(ChemPredictor signpred, PredictionResultsWriter predWriter, IAtomContainer mol) 
			throws IllegalAccessException, CDKException, IOException {

		// Perform prediction 
		if (signpred instanceof ChemCPRegressor)
			predictMolecule((ChemCPRegressor)signpred, predWriter, mol);
		else if (signpred instanceof ChemCPClassifier)
			predictMolecule((ChemCPClassifier)signpred, predWriter, mol);
		else if (signpred instanceof ChemVAPClassifier)
			predictMolecule((ChemVAPClassifier)signpred, predWriter, mol);
		else {
			LOGGER.debug("ChemPredictor of a non-supported class: {}", signpred.getClass());
			console.failWithInternalError("Internal problem predicting molecules, please contact Aros Bio and kindly send include the cpsign logfile");
		}
	}

	private void predictMolecule(ChemVAPClassifier predictor, PredictionResultsWriter predWriter,
			IAtomContainer mol) throws CDKException, IllegalAccessException, IOException {
		// Perform prediction
		ResultsHandler resHandler = new ResultsHandler();
		try {
			CVAPPrediction<String> res =  predictor.predict(mol);
			resHandler.setProbabilities(MathUtils.roundAll(res.getProbabilities()));

			// Prediction sets
			resHandler.setP0P1Interval(MathUtils.roundTo3significantFigures(res.getMeanP0P1Width()), 
					MathUtils.roundTo3significantFigures(res.getMedianP0P1Width()));
		} catch (CDKException e){
			LOGGER.debug("Exception running predictProbabilities", e);
			LOGGER.error("Dataset handling a molecule when predicting, skipping it");
			return;
		} 
		
		// Find Significant Signature 
		if (calcGradient){
			try{
				resHandler.signSign = predictor.predictSignificantSignature(mol);
				LOGGER.trace("SignSign from predictSignificantSignature: {}", resHandler.signSign);
			} catch (IllegalStateException | CDKException e){
				LOGGER.debug("Exception running predictSignificantSignature", e);
			}
		}

		// Print results
		predWriter.write(mol, resHandler); 
		predWriter.flush();

		generateImgs(mol, resHandler);

	}

	private void predictMolecule(ChemCPClassifier signClass,PredictionResultsWriter predWriter,
			IAtomContainer mol) throws IllegalStateException, IOException {

		// Perform prediction
		ResultsHandler resHandler = new ResultsHandler();
		try{
			resHandler.pValues = MathUtils.roundAll(signClass.predict(mol));

			LOGGER.trace("Pvalues from predictMondrianTwoClasses={}", resHandler.pValues);

			if (confs.confidences!=null){
				for (Double conf: confs.confidences)
					resHandler.addPredictedLabels(conf, ClassificationUtils.getPredictedLabels(resHandler.pValues, conf));
			}

		} catch (CDKException e){
			LOGGER.debug("Exception running predictMondrianTwoClasses", e);
			LOGGER.error("Dataset handling a molecule when predicting p-values, skipping it");
			return;
		} 

		// Find Significant Signature 
		if (calcGradient){
			try{
				resHandler.setSignificantSignatureResult(signClass.predictSignificantSignature(mol));
				LOGGER.trace("SignSign from predictSignificantSignature: {}", resHandler.signSign);
			} catch (IllegalStateException | CDKException e){
				LOGGER.debug("Exception running predictSignificantSignature", e);
			}
		}

		// Print results
		predWriter.write(mol, resHandler); 
		predWriter.flush();

		generateImgs(mol, resHandler);

	}

	private void predictMolecule(ChemCPRegressor signReg, PredictionResultsWriter predWriter,
			IAtomContainer ac) throws IllegalAccessException, IOException, CDKException {


		ResultsHandler resHandler = new ResultsHandler();


		if (confs.confidences!=null){
			//predict for confidences
			resHandler.addRegressionResultConfBased(signReg.predict(ac, confs.confidences));
		}

		if (predictionWidths!=null){
			//predict for distance
			resHandler.addRegressionResultPredWidthBased(signReg.predictConfidence(ac, predictionWidths));
		}

		if (calcGradient){
			try {
				resHandler.signSign = signReg.predictSignificantSignature(ac);
				LOGGER.trace("SignSign from predictSignificantSignature: {}", resHandler.signSign);
			} catch (IllegalStateException | CDKException e){
				LOGGER.debug("Exception running predictSignificantSignature (ACP)", e);
				LOGGER.error("Could not predict significance signatures for molecule: {}",e.getMessage());
			}
		}

		// Print results
		predWriter.write(ac, resHandler);
		predWriter.flush();

		generateImgs(ac, resHandler);

	}

	private void generateImgs(IAtomContainer mol, ResultsHandler res) {
		if (imageHandler.isPrintingSignatureImgs()){
			try {
				// String label = null;
				// if (res.pValues != null) {
				// 	label = ClassificationUtils.getPredictedClass(res.pValues);
				// } else if (res.probabilities != null) {
				// 	label = ClassificationUtils.getPredictedClass(res.probabilities);
				// }

				imageHandler.writeSignificantSignatureImage(res.toRenderInfo(mol)); // mol, res.signSign.getAtoms(), res.pValues, res.getProbabilities(), label);
			} catch (Exception e) {
				LOGGER.debug("Exception writing significant signature depiction", e);
				LOGGER.error("Error writing significant signature depiction: {}", e.getMessage());
			}
		}
		if (imageHandler.isPrintingGradientImgs()) {
			try {
				imageHandler.writeGradientImage(res.toRenderInfo(mol)); //mol, res.signSign.getAtomContributions(), res.pValues, res.probabilities);
			} catch (Exception e) {
				LOGGER.debug("Exception writing gradient depiction", e);
				LOGGER.error("Error writing gradient depiction: {}", e.getMessage());
			}
		}
	}

}
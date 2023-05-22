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
import com.arosbio.chem.io.in.FailedRecord.Cause;
import com.arosbio.cheminf.ChemCPClassifier;
import com.arosbio.cheminf.ChemPredictor;
import com.arosbio.cheminf.descriptors.DescriptorCalcException;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.MathUtils;
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
	private PredictionImageHandler imageHandler;

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

		// if Predict from a CSV- or SDF-file - count total number of molecules
		int totalNumMolsToPredict = 0, progressInterval = 0; 
		if (toPredict.toPredict.predictFile != null){

			try {
				totalNumMolsToPredict = CollectionUtils.count(toPredict.toPredict.predictFile.getIterator());
			} catch (IllegalArgumentException | IOException e){
				LOGGER.debug("Could not parse the predictFile", e);
				console.failWithArgError("Could not read molecules from "+
						toPredict.toPredict.predictFile.getURI());
			}

			if (totalNumMolsToPredict < 1) {
				LOGGER.debug("No valid records found in file: "+toPredict.toPredict.predictFile.getURI());
				console.failWithArgError("Could not read molecules from "+
						toPredict.toPredict.predictFile.getURI());
			}

			progressInterval = CLIProgramUtils.getProgressInterval(totalNumMolsToPredict, 10);
			int numSteps = totalNumMolsToPredict/progressInterval;
			pb.addAdditionalSteps(numSteps); // add the additional steps 
		}

		// FINISHED SETUP
		console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
		console.println("Using RNG seed: "+ GlobalConfig.getInstance().getRNGSeed(), PrintMode.VERBOSE);
		pb.stepProgress();
		timer.endSection();

		// LOAD DATA
		pb.setCurrentTask(PB.LOADING_FILE_OR_MODEL_PROGRESS);
		ChemCPClassifier predictor = initAndLoad();
		// Set up prediction depictions
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
		predict(predictor, totalNumMolsToPredict, progressInterval);
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
		ChemCPClassifier signTCP = initChemCPClassificationTCP();

		// Load data
		try {
			CLIProgramUtils.loadPrecomputedData(signTCP, inputSection, encryptSection.exclusive.encryptSpec, console);
		} catch (Exception e) {
			LOGGER.debug("Failed loading data to 'train' online predictor",e);
			throw e;
		}

		// Do transformations
		CLIProgramUtils.applyTransformations(signTCP.getDataset(), true, transformerArgs.transformers, this, console);

		// Verify no missing data
		CLIProgramUtils.verifyNoMissingDataAndPrintErr(signTCP.getDataset(), true, console);

		return signTCP;
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
				console.failWithArgError("Could not create parent directory of output file: " + outputSection.outputFile);
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

	

	private void predict(ChemCPClassifier predictor, int molsInPredFile, int progressInterval) {

		// Some state
		int numSuccessFulPredictions = 0;
		// int numPredictedCount = 0; // for images/output text
		List<FailedRecord> failedRecords = new ArrayList<>();

		// Set up the ResultsOutputter
		try(
			PredictionResultsWriter predictionWriter = CLIProgramUtils.setupResultsOutputter(this,
				outputSection.outputFormat, 
				outputSection.outputFile,
				toPredict.toPredict.predictFile,
				outputSection.printInChI, 
				outputSection.compress);){

			// Predict single SMILES
			if (toPredict.toPredict.smilesToPredict != null) {

				IAtomContainer mol;
				try {
					mol = ChemFileParserUtils.parseSMILES(toPredict.toPredict.smilesToPredict); 
					CPSignMolProperties.setRecordIndex(mol, "cmd-line-input");

					predictMolecule(predictor, predictionWriter, mol);

					numSuccessFulPredictions++;

				} catch (InvalidSmilesException e){
					LOGGER.error("Invalid smiles: {}", toPredict.toPredict.smilesToPredict);
					failedRecords.add(new FailedRecord.Builder(-1, FailedRecord.Cause.INVALID_STRUCTURE)
						.withReason("invalid SMILES: " + toPredict.toPredict.smilesToPredict).build());
				} catch (CDKException e){ 
					LOGGER.error("CDKException when predicting the --smiles molecule",e);

				} catch (Exception e) {
					LOGGER.error("Error predicting SMILES: {}", e.getMessage(),e);

				}
			}

			// Predict from a chemical-file
			if (molsInPredFile > 0){

				int currentMoleculeIndex = 0;
				Iterator<IAtomContainer> molIterator=null;
				try {
					molIterator = toPredict.toPredict.predictFile.getIterator();
				} catch (IOException e) {
					LOGGER.debug("Failed reading from predictFile",e);
					console.failWithArgError("Could not read any molecules from parameter " + 
							CLIProgramUtils.getParamName(this, "predictFile", "PREDICT_FILE"));
				}

				// DO THE PREDICTIONS

				boolean predictionDone = false;
				IAtomContainer mol=null;

				while (molIterator.hasNext()) {
					mol = molIterator.next();
					if (mol.getProperty(CDKConstants.REMARK) == null)
						mol.removeProperty(CDKConstants.REMARK);
					try {

						predictMolecule(predictor, predictionWriter, mol);

						predictionDone=true;
						numSuccessFulPredictions++;

					} catch (DescriptorCalcException e){
						LOGGER.debug("Failed calculating descriptors for molecule", e);
						failedRecords.add(new FailedRecord.Builder(currentMoleculeIndex, Cause.DESCRIPTOR_CALC_ERROR).build());
					} catch (CDKException e) {
						LOGGER.debug("CDKException",e);
						LOGGER.info("Error predicting molecule: {}", e.getMessage());
						failedRecords.add(new FailedRecord.Builder(currentMoleculeIndex, Cause.INVALID_STRUCTURE).build());
					} catch (IOException e) {
						LOGGER.debug("Failed writing to output", e);
					} finally {
						currentMoleculeIndex++;
						// Step Progress and print out
						if ((1+currentMoleculeIndex) % progressInterval == 0) {
							pb.stepProgress();
							// Print progress to stdout
							console.println(" - Predicted %d/%d molecules", PrintMode.NORMAL,
								currentMoleculeIndex,molsInPredFile);
						}
					}
				}

				if (!predictionDone)
					console.failWithArgError("No valid molecules in "+
							CLIProgramUtils.getParamName(this, "predictFile", "PREDICT_FILE"));

			}

		} catch (IOException e) {
			LOGGER.debug("Failed closing the results outputter",e);
			console.printlnWrappedStdErr("Could not properly close the result file, not all predictions might have successfully have been written to file", PrintMode.NORMAL);
		}

		// ===================================
		// We are done with all predictions

		// Summarize how many was successfully and failed predictions

		console.println("%nSuccessfully predicted %d molecule%s", PrintMode.NORMAL,
				numSuccessFulPredictions,(numSuccessFulPredictions>1 || numSuccessFulPredictions==0?"s":""));
		// Only write failed ones if there were any
		if (!failedRecords.isEmpty()) {
			console.println("Failed predicting %s molecule(s)%s", PrintMode.NORMAL,
				failedRecords.size(), (failedRecords.size()>1 ? "s" :""));
			// If detailed info should be written per record
			if (listFailedRecordsMixin.listFailedRecords){
				StringBuilder sb = new StringBuilder("List of failed records and their causes:%n");
				CLIProgramUtils.appendFailedMolsInfo(sb,failedRecords);
				console.println(sb.toString(), PrintMode.NORMAL);
			}
		}

	}

	private void predictMolecule(ChemCPClassifier predictor, PredictionResultsWriter predWriter, IAtomContainer mol) 
		throws IOException, CDKException {

		// Perform prediction 
		ResultsHandler resHandler = new ResultsHandler();
		try {
			resHandler.pValues = MathUtils.roundAll(predictor.predict(mol));

			LOGGER.debug("Pvalues from predict={}", resHandler.pValues);

			if (confs.confidences!=null){
				for (Double conf: confs.confidences)
					resHandler.addPredictedLabels(conf, ClassificationUtils.getPredictedLabels(resHandler.pValues, conf));
			}

		} catch (IllegalStateException e){
			LOGGER.debug("Exception running predict", e);
			console.failWithInternalError("Could not predict p-values for molecule due to: " + e.getMessage());
		}

		// Find Significant Signature 
		if (calcGradient){
			try{
				resHandler.signSign = predictor.predictSignificantSignature(mol);
			} catch (IllegalStateException | CDKException e){
				LOGGER.debug("Exception running predictSignificantSignature", e);
			}
		}

		// Print results
		predWriter.write(mol, resHandler); 
		predWriter.flush();

		if (imageHandler.isPrintingSignatureImgs()){
			try {
				imageHandler.writeSignificantSignatureImage(resHandler.toRenderInfo(mol)); 
			} catch (Exception e) {
				LOGGER.debug("Exception writing significant signature depiction", e);
			}
		}
		if (imageHandler.isPrintingGradientImgs()) {
			try {
				imageHandler.writeGradientImage(resHandler.toRenderInfo(mol));
			} catch (Exception e) {
				LOGGER.debug("Exception writing gradient depiction", e);
			}
		}

	}


}
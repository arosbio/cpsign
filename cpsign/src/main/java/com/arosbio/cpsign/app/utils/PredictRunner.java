package com.arosbio.cpsign.app.utils;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.chem.io.in.ChemFileParserUtils;
import com.arosbio.chem.io.in.EarlyLoadingStopException;
import com.arosbio.chem.io.in.FailedRecord;
import com.arosbio.chem.io.in.FailedRecord.Cause;
import com.arosbio.chem.io.in.ProgressTracker;
import com.arosbio.cheminf.ChemCPClassifier;
import com.arosbio.cheminf.ChemCPRegressor;
import com.arosbio.cheminf.ChemPredictor;
import com.arosbio.cheminf.ChemVAPClassifier;
import com.arosbio.cheminf.descriptors.DescriptorCalcException;
import com.arosbio.commons.MathUtils;
import com.arosbio.commons.TypeUtils;
import com.arosbio.cpsign.app.Predict;
import com.arosbio.cpsign.app.RunnableCmd;
import com.arosbio.cpsign.app.params.mixins.CompoundsToPredictMixin;
import com.arosbio.cpsign.app.params.mixins.ConfidencesListMixin;
import com.arosbio.cpsign.app.params.mixins.OutputChemMixin;
import com.arosbio.cpsign.app.utils.CLIConsole.ParamComb;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.CLIConsole.VerbosityLvl;
import com.arosbio.cpsign.out.PredictionImageHandler;
import com.arosbio.cpsign.out.PredictionImageHandler.GradientImageOpts;
import com.arosbio.cpsign.out.PredictionImageHandler.SignificantSignatureImageOpts;
import com.arosbio.cpsign.out.PredictionResultsWriter;
import com.arosbio.cpsign.out.ResultsHandler;
import com.arosbio.data.MissingDataException;
import com.arosbio.io.UriUtils;
import com.arosbio.ml.ClassificationUtils;
import com.arosbio.ml.vap.avap.CVAPPrediction;

public class PredictRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(PredictRunner.class);

	private final RunnableCmd cmd; 
	private final CLIConsole console;
	private final CLIProgressBar pb;
	
	private final ChemPredictor predictor;
	
	private final CompoundsToPredictMixin toPredict;
	private final ProgressTracker tracker;
	private final ConfidencesListMixin confs;
	private final List<Double> predictionWidths;

	private final OutputChemMixin outputSection;
	private final boolean calcGradient;
	private final boolean listFailed;


	// Set up from input when running predict
	private PredictionImageHandler imageHandler;



	public PredictRunner(final RunnableCmd cmd,
	final CLIConsole console,
	final CLIProgressBar pb,
	final ChemPredictor predictor,
	final CompoundsToPredictMixin toPredict,
	final ProgressTracker progressTracker,
	final ConfidencesListMixin confs,
	final List<Double> predictionWidths,
	final OutputChemMixin outputSection,
	final GradientImageOpts gradientImageSection,
	final SignificantSignatureImageOpts signatureImageSection,
	final boolean calcGradient,
	final boolean listFailed){
		this.cmd = cmd;
		this.console = console;
		this.pb = pb;
		this.predictor = predictor;
		this.toPredict = toPredict;
		this.tracker = progressTracker;
		this.confs = confs;
		this.predictionWidths = predictionWidths;

		this.outputSection = outputSection;
		this.listFailed = listFailed;
		// If we're writing images, we must predict the significant signature!
		if (gradientImageSection.createImgs || 
				signatureImageSection.createImgs){
			this.calcGradient = true;
		} else {
			this.calcGradient = calcGradient;
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

		// Verify confidence / predictionWidths for regression are given
		if (predictor instanceof ChemCPRegressor) {
			if ((confs.confidences == null || confs.confidences.isEmpty()) &&
					(predictionWidths == null || predictionWidths.isEmpty())) {
				LOGGER.debug("No confidences or predictionWidths given when predicting regression - fail!");
				console.failDueToMissingParameters(ParamComb.AND_OR, "must be given when doing CP regression", 
						new MissingParam("setConfidences","CONFIDENCES", ConfidencesListMixin.class),
						new MissingParam("predictionWidths","PREDICTION_WIDTHS", Predict.class));
			}
		}

		// Setup prediction depictions
		imageHandler = new PredictionImageHandler(
				gradientImageSection, 
				signatureImageSection,
				predictor);
		if (imageHandler.isUsed() && !predictor.usesSignaturesDescriptor()) {
			LOGGER.debug("Loaded model without signatures descriptor and got argument for generating prediction images - failing!");
			console.failWithArgError("Generating prediction images is only available when using the signatures descriptor");
		}
			
	}

	// Iteration info
	int numMolsToPredict = 0, numMolsInPredictFile = 0, progressInterval = -1, molIterationCounter = 0, numMissingDataFails = 0, numSuccessfulPreds = 0;

	public void runPredict() {
		// Calculate the number of compounds to predict
		// From predict-file
		if (toPredict.toPredict.predictFile!=null){

			try {
				numMolsInPredictFile = toPredict.toPredict.predictFile.countNumRecords();
				numMolsToPredict += numMolsInPredictFile;
			} catch (IOException e){
				LOGGER.debug("Could not parse the predictFile", e);
				console.failWithArgError("Could not read molecules from%n"+
						toPredict.toPredict.predictFile.getURI());
			}

			if (numMolsToPredict < 1) {
				console.failWithArgError("Could not read molecules from%n"+
						toPredict.toPredict.predictFile.getURI());
			}
		}
		numMolsToPredict += (toPredict.toPredict.smilesToPredict!=null? 1 : 0);

		progressInterval = CLIProgramUtils.getProgressInterval(numMolsToPredict, 10);
		int numSteps = numMolsToPredict/progressInterval;
		pb.addAdditionalSteps(numSteps); 

		// Set up the ResultsOutputter + run doPredict
		try(
			PredictionResultsWriter predictionsWriter = CLIProgramUtils.setupResultsOutputter(cmd, 
				outputSection.outputFormat, 
				outputSection.outputFile,
				toPredict.toPredict.predictFile,
				outputSection.printInChI, 
				outputSection.compress);){

			doPredict(predictionsWriter);

		} catch (IOException e) {
			LOGGER.debug("Failed closing the results outputter",e); 
			console.printlnWrappedStdErr("Could not properly close the result file, not all predictions might have successfully have been writen to file", 
					PrintMode.NORMAL);
		} catch (EarlyLoadingStopException e){
			// we have failed enough times and should exit the program
			LOGGER.debug("Encountered enough failed records to stop execution", e);
			new CLIProgramUtils.UserInputErrorResolver(console, numSuccessfulPreds, toPredict.toPredict.predictFile, 
				tracker.getFailures(), predictor.getDataset(), predictor.getDataset().getProperty(), null, tracker.getMaxAllowedFailures(), listFailed)
				.failWithError();
		}

		// We are done with all predictions
		String molSingularOrPlural=(numSuccessfulPreds>1 || numSuccessfulPreds==0?"s":"");
		StringBuilder resultInfo = new StringBuilder(String.format(Locale.ENGLISH, "%nSuccessfully predicted %d molecule%s.",numSuccessfulPreds,molSingularOrPlural));
		
		
		// If we had some failing records
		if (tracker.getNumFailures()>0) {
			List<FailedRecord> failedRecs = tracker.getFailures();
			resultInfo.append(String.format(Locale.ENGLISH, " Failed predicting %d record%s.", failedRecs.size(),(failedRecs.size()>1 ? "s":"")));
			
			if (numMissingDataFails > 0) {
				String missingDataInfo = numMissingDataFails + " record(s) failed due to missing features - please make sure your data pre-processing is correct, consider using e.g. removal of poor descriptors (DropMissingDataFeatures) or impute missing features (SingleFeatImputer)%n";
				if (console.getVerbosity() == VerbosityLvl.VERBOSE){
					resultInfo.append(missingDataInfo);
				} else {
					LOGGER.debug(missingDataInfo);
				}
			}
			
			if (listFailed) {
				resultInfo.append("%n");
				CLIProgramUtils.appendFailedMolsInfo(resultInfo, failedRecs);
			}
		}
		// Print info
		console.printlnWrapped(resultInfo.toString(), PrintMode.NORMAL);
	}
	
    private void doPredict(PredictionResultsWriter predWriter) throws EarlyLoadingStopException {

		// Predict single SMILES
		if (toPredict.toPredict.smilesToPredict != null) {
			LOGGER.debug("Predicting single --smiles molecule");

			// Parse input to Mol
			IAtomContainer mol = null;
			final String smilesID = "cmd-line-input";
			try {
				mol = ChemFileParserUtils.parseSMILES(toPredict.toPredict.smilesToPredict); // we do CDKConfigureAtomContainer.applyAromaticitySuppressHydrogens within this method
				CPSignMolProperties.setRecordIndex(mol,smilesID);
			} catch (InvalidSmilesException e){
				trackError(-1, smilesID, e, toPredict.toPredict.smilesToPredict);
				console.failWithArgError("Input to parameter --smiles: '"+toPredict.toPredict.smilesToPredict+"' could not be parsed as a valid SMILES");
			}

			// Predict it
			try {
				predictMolecule(predictor, predWriter, mol);
				predWriter.flush();
				numSuccessfulPreds++;
			} catch (Exception e){
				trackError(-1, smilesID, e, toPredict.toPredict.smilesToPredict);
			} finally {
				// Step Progress and print out
				printPredictionProgressAndStep();
			}
		}

		// Predict from a predict-file
		if (numMolsInPredictFile > 0){
			LOGGER.debug("Predicting from an input file: {}",toPredict.toPredict.predictFile.getURI());

			Iterator<IAtomContainer> molIterator=null;
			try {
				molIterator = toPredict.toPredict.predictFile.getIterator(tracker);
			} catch (IOException e) {
				LOGGER.debug("Failed reading from predictFile",e);
				console.failWithArgError("Could not read any molecules from parameter " + 
						CLIProgramUtils.getParamName(CompoundsToPredictMixin.class, "predictFile", "PREDICT_FILE"));
			}

			// DO THE PREDICTIONS

			boolean predictionFromFileDone = false;
			IAtomContainer mol=null;
			int index = -1;
			String id = "null";

			while (molIterator.hasNext()) {
				mol = molIterator.next(); // may throw EarlyLoadingStopException 
				if (mol.getProperty(CDKConstants.REMARK) == null)
					mol.removeProperty(CDKConstants.REMARK);
				index = TypeUtils.asInt(CPSignMolProperties.getRecordIndex(mol));
				id = (CPSignMolProperties.hasMolTitle(mol)? CPSignMolProperties.getMolTitle(mol) : null);
				
				try {

					predictMolecule(predictor, predWriter, mol);
					predWriter.flush();
					
					predictionFromFileDone=true;
					numSuccessfulPreds++;
				} catch (Exception e){
					trackError(index, id, e, id);
				} finally {
					molIterationCounter++;
					
					// Step Progress and print out
					printPredictionProgressAndStep();

					// Check if continue or not
					tracker.assertCanContinueParsing();
				}
			}

			if (!predictionFromFileDone){
				console.failWithNoMoleculesCouldBeLoaded(toPredict.toPredict.predictFile);
			}
		}

	}

	private void printPredictionProgressAndStep() {
		if (progressInterval > 0 && molIterationCounter % progressInterval == 0) {
			console.println(" - Processed %d/%d molecules", 
					PrintMode.NORMAL,molIterationCounter,numMolsToPredict);
			pb.stepProgress();
		}
	}

	private void trackError(int index, String id, Exception e, String smilesOrNull){
		if (e instanceof InvalidSmilesException){
			// Invalid structure
			LOGGER.debug("Invalid smiles given: {}", smilesOrNull);
			tracker.register(new FailedRecord.Builder(index, Cause.INVALID_STRUCTURE).withID(id).withReason(e.getMessage()).build());
		} else if (e instanceof DescriptorCalcException){
			LOGGER.debug("Failed calculating descriptors for molecule", e);
			tracker.register(new FailedRecord.Builder(index, Cause.DESCRIPTOR_CALC_ERROR).withReason(e.getMessage()).build());
		} else if (e instanceof CDKException){
			LOGGER.debug("Failed configuring the input molecule properly",e);
			tracker.register(new FailedRecord.Builder(index, Cause.INVALID_STRUCTURE).withReason("Failed configuring input structure: " + e.getMessage()).build());
		} else if (e instanceof MissingDataException){
			LOGGER.debug("Got a missing data exception - something wrong i pre-processing?");
			tracker.register(new FailedRecord.Builder(index, Cause.DESCRIPTOR_CALC_ERROR).withID(id).withReason(e.getMessage()).build());
			numMissingDataFails ++;
		} else if (e instanceof IllegalAccessException){
			// IllegalAccess is due to model-issues and not specific for the molecule - then fail complete run
			LOGGER.debug("IllegalAccessException - model is not accessible for some reason", e);
			console.failWithInternalError("Failed predicting molecule due to: " + e.getMessage());	
		} else if (e instanceof IllegalStateException){
			// Predictor not trained - should not happen really
			LOGGER.debug("Failed due to IllegalState except, meaning model not trained", e);
			console.failWithArgError("Predictor model not trained or internal error");
		} else {
			LOGGER.debug("Failed molecule due to generic exception", e);
			tracker.register(new FailedRecord.Builder(index, Cause.UNKNOWN).withID(id).withReason(e.getMessage()).build());
		}
	}

	private void predictMolecule(ChemPredictor chemPredictor, PredictionResultsWriter predWriter, IAtomContainer mol) 
			throws IllegalAccessException, CDKException, IOException {

		// Perform prediction 
		if (chemPredictor instanceof ChemCPRegressor)
			predictMolecule((ChemCPRegressor)chemPredictor, predWriter, mol);
		else if (chemPredictor instanceof ChemCPClassifier)
			predictMolecule((ChemCPClassifier)chemPredictor, predWriter, mol);
		else if (chemPredictor instanceof ChemVAPClassifier)
			predictMolecule((ChemVAPClassifier)chemPredictor, predWriter, mol);
		else {
			LOGGER.debug("ChemPredictor of a non-supported class: {}", chemPredictor.getClass());
			console.failWithInternalError("Internal problem predicting molecules, please contact Aros Bio and kindly send include the cpsign logfile");
		}
	}

	private void predictMolecule(ChemVAPClassifier predictor, PredictionResultsWriter predWriter,
			IAtomContainer mol) throws CDKException, IllegalAccessException, IOException {
		// Perform prediction
		ResultsHandler resHandler = new ResultsHandler();

		CVAPPrediction<String> res =  predictor.predict(mol);
		resHandler.setProbabilities(MathUtils.roundAll(res.getProbabilities()));

		// Prediction sets
		resHandler.setP0P1Interval(MathUtils.roundTo3significantFigures(res.getMeanP0P1Width()), 
				MathUtils.roundTo3significantFigures(res.getMedianP0P1Width()));

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

	

	private void predictMolecule(ChemCPClassifier chemPredictor, PredictionResultsWriter predWriter,
			IAtomContainer mol) throws IllegalStateException, IOException, CDKException {

		// Perform prediction
		ResultsHandler resHandler = new ResultsHandler();

		resHandler.pValues = MathUtils.roundAll(chemPredictor.predict(mol));

		LOGGER.trace("Pvalues from predict={}", resHandler.pValues);

		if (confs.confidences!=null){
			for (Double conf : confs.confidences)
				resHandler.addPredictedLabels(conf, ClassificationUtils.getPredictedLabels(resHandler.pValues, conf));
		}

		// Find Significant Signature 
		if (calcGradient){
			try{
				resHandler.setSignificantSignatureResult(chemPredictor.predictSignificantSignature(mol));
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
				imageHandler.writeSignificantSignatureImage(res.toRenderInfo(mol));
			} catch (Exception e) {
				LOGGER.debug("Exception writing significant signature depiction", e);
				LOGGER.error("Error writing significant signature depiction: {}", e.getMessage());
			}
		}
		if (imageHandler.isPrintingGradientImgs()) {
			try {
				imageHandler.writeGradientImage(res.toRenderInfo(mol));
			} catch (Exception e) {
				LOGGER.debug("Exception writing gradient depiction", e);
				LOGGER.error("Error writing gradient depiction: {}", e.getMessage());
			}
		}
	}
    
}

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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.chem.io.in.ChemFile;
import com.arosbio.chem.io.in.EarlyLoadingStopException;
import com.arosbio.chem.io.in.FailedRecord;
import com.arosbio.chem.io.in.FailedRecord.Cause;
import com.arosbio.chem.io.in.MolAndActivityConverter;
import com.arosbio.chem.io.in.ProgressTracker;
import com.arosbio.cheminf.ChemCPClassifier;
import com.arosbio.cheminf.ChemCPRegressor;
import com.arosbio.cheminf.ChemClassifier;
import com.arosbio.cheminf.ChemPredictor;
import com.arosbio.cheminf.ChemVAPClassifier;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.commons.MathUtils;
import com.arosbio.commons.TypeUtils;
import com.arosbio.cpsign.app.params.converters.ChemFileConverter;
import com.arosbio.cpsign.app.params.mixins.ConsoleVerbosityMixin;
import com.arosbio.cpsign.app.params.mixins.EarlyTerminationMixin;
import com.arosbio.cpsign.app.params.mixins.EchoMixin;
import com.arosbio.cpsign.app.params.mixins.EncryptionMixin;
import com.arosbio.cpsign.app.params.mixins.ListFailedRecordsMixin;
import com.arosbio.cpsign.app.params.mixins.LogfileMixin;
import com.arosbio.cpsign.app.params.mixins.OutputChemMixin;
import com.arosbio.cpsign.app.params.mixins.OverallStatsMixinClasses;
import com.arosbio.cpsign.app.params.mixins.ProgramProgressMixin;
import com.arosbio.cpsign.app.params.mixins.ValidationPointsMixin;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.CLIConsole.VerbosityLvl;
import com.arosbio.cpsign.app.utils.CLIProgramUtils;
import com.arosbio.cpsign.app.utils.CLIProgressBar;
import com.arosbio.cpsign.app.utils.CLIProgressBar.SupportsProgressBar;
import com.arosbio.cpsign.app.utils.NullProgress;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.cpsign.app.utils.ProgramTimer;
import com.arosbio.cpsign.out.NOPResultsWriter;
import com.arosbio.cpsign.out.OutputNamingSettings;
import com.arosbio.cpsign.out.OutputNamingSettings.PB;
import com.arosbio.cpsign.out.OutputNamingSettings.ProgressInfoTexts;
import com.arosbio.cpsign.out.PredictionResultsWriter;
import com.arosbio.cpsign.out.ResultsHandler;
import com.arosbio.data.MissingDataException;
import com.arosbio.data.NamedLabels;
import com.arosbio.io.IOUtils;
import com.arosbio.ml.ClassificationUtils;
import com.arosbio.ml.cp.CPRegressionPrediction;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.classification.PointClassifierMetric;
import com.arosbio.ml.metrics.classification.ProbabilisticMetric;
import com.arosbio.ml.metrics.classification.ScoringClassifierMetric;
import com.arosbio.ml.metrics.cp.classification.CPClassifierMetric;
import com.arosbio.ml.metrics.cp.regression.CPRegressionMultiMetric;
import com.arosbio.ml.metrics.regression.PointPredictionMetric;
import com.arosbio.ml.metrics.vap.VAPMetric;
import com.arosbio.ml.vap.avap.CVAPPrediction;
import com.google.common.collect.Range;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/*
 * PB.VALIDATE_PARAMS_PROGRESS
 * PB.LOADING_FILE_OR_MODEL_PROGRESS
 * PB.PREDICTING_PROGRESS
 * PB.RESULTS_WRITING_PROGRESS
 */
@Command(
		name = Validate.CMD_NAME, 
		description = Validate.CMD_DESCRIPTION, 
		descriptionHeading = CPSignApp.DESCRIPTION_HEADER, 
		header = Validate.CMD_HEADER
		)
public class Validate implements RunnableCmd, SupportsProgressBar {

	private static final Logger LOGGER = LoggerFactory.getLogger(Validate.class);

	public static final String CMD_NAME = "validate";
	public final static String CMD_HEADER = "Predict a validation set with known labels";
	public static final String CMD_DESCRIPTION = "Use a test-file with existing (true) labels to validate a Predictor."
			+ " The normal execution will only report overall statistics, but it is possible to print all"
			+ " predicted result to json, CSV or sdf file format.";

	/*****************************************
	 * INTERNAL STATE
	 *****************************************/

	@Spec private CommandSpec spec;
	private CLIConsole console = CLIConsole.getInstance();
	private CLIProgressBar pb = new NullProgress();
	private ProgramTimer timer = new ProgramTimer(false, console);

	private List<Metric> validationMetrics = new ArrayList<>(); 

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

	@Option(
			names = { "-p", "--predict-file" }, 
			description = "File to use for validation. Accepted formats are CSV, SDFile or JSON.",
			parameterConsumer = ChemFileConverter.PredictFileConverter.class,
			paramLabel = ArgumentType.CHEM_FILE_ARGS,
			required=true)
	private ChemFile predictFile;

	@Option(
			names = { "-vp", "--validation-property" },
			description = {"Endpoint that should be validated on. Can be omitted if identical to the one in used in training.",
					"(SDFile) Name of field with correct label, should match a property in the predict file",
					"(CSV) Name of the column to use for validation, should match header of that column",
					"(JSON) JSON-key for the property with the true response value"},
					paramLabel = ArgumentType.TEXT)
	private String validationEndpoint;

	@Mixin
	private EarlyTerminationMixin earlyTermination = new EarlyTerminationMixin();
	
	@Mixin
	private ListFailedRecordsMixin listFailedRecordsMixin = new ListFailedRecordsMixin();

	// Validation opts
	@Mixin
	private ValidationPointsMixin validationSection = new ValidationPointsMixin();

	// Output
	@Option(
			names = {"--print-predictions"}, 
			description="Print the prediction output in json/csv/sdf format (default is only printing overall statistics). Use together with the --output parameter to print the predictions to a file")
	private boolean printPredictions = false;
	@Mixin
	private OutputChemMixin outputSection = new OutputChemMixin();  

	@Mixin
	private OverallStatsMixinClasses.StatsFile statsOutputFile = new OverallStatsMixinClasses.StatsFile();

	@Mixin
	private OverallStatsMixinClasses.JSONTextCSV_TSV overallStatsFormat = new OverallStatsMixinClasses.JSONTextCSV_TSV();

	@Option(
			names = {"--roc"},
			description = "Output the ROC curve (VAP only), the ROC curve has many points and lead to verbose output. Default is to only print the AUC score")
	private boolean outputROC = false;


	// Encrypted model
	@Mixin
	private EncryptionMixin encryptSection = new EncryptionMixin();

	// General Section
	@Mixin
	private ProgramProgressMixin pbArgs;

	@Mixin
	private ConsoleVerbosityMixin consoleArgs = new ConsoleVerbosityMixin();

	@Mixin
	private LogfileMixin loggingArgs = new LogfileMixin();

	@Mixin
	private EchoMixin echo = new EchoMixin();

	/*****************************************
	 * END OF OPTIONS
	 *****************************************/

	private ProgressTracker progressTracker = null;
	private int numMissingDataFails = 0, numSuccessfulPreds = 0;

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

		try {
			console.print(OutputNamingSettings.ProgressInfoTexts.VALIDATING_ARGS, PrintMode.NORMAL);
			validateParams();
			LOGGER.debug("Validated arguments");
			console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
			pb.stepProgress();
			timer.endSection();

			// LOAD MODEL
			pb.setCurrentTask(PB.LOADING_FILE_OR_MODEL_PROGRESS);
			ChemPredictor predictor = loadModel();
			pb.stepProgress();
			timer.endSection();

			// VALIDATE
			pb.setCurrentTask(PB.PREDICTING_PROGRESS);
			validate(predictor);
			pb.stepProgress();
			timer.endSection();

			// PRINT STATS
			pb.setCurrentTask(PB.RESULTS_WRITING_PROGRESS);
			CLIProgramUtils.printResults(validationMetrics, statsOutputFile.overallStatsFile, 
					overallStatsFormat.outputFormat, outputROC,(predictor instanceof ChemClassifier ? ((ChemClassifier)predictor).getNamedLabels() : null), console);

			// Finish
			pb.finish();
			timer.endProgram();
			console.println("", PrintMode.NORMAL);
			return ExitStatus.SUCCESS.code;
		} finally {			
			CLIProgramUtils.closeQuietly(pb);
		}
	}

	@SuppressWarnings("null")
	private ChemPredictor loadModel() {
		console.print(OutputNamingSettings.ProgressInfoTexts.LOADING_MODEL, PrintMode.NORMAL);
		// Load the model and make sure it's a trained Signatures Predictor model
		ChemPredictor predictor = null;
		try {
			predictor =  ModelSerializer.loadChemPredictor(modelFile, encryptSection.exclusive.encryptSpec);
			if (! predictor.getPredictor().isTrained()) {
				LOGGER.debug("The signatures predictor was not trained");
				console.failWithArgError("Only trained Signatures Predictor models can be used in program "+ CMD_NAME);
			}

			console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);

			CLIProgramUtils.printInfoAboutLoadedModel(predictor, console);

		} catch (InvalidKeyException e) {
			LOGGER.debug("Failed loading model",e);
			console.failWithArgError("The model could not be decrypted with the given license");
		} catch (IllegalArgumentException e) {
			LOGGER.debug("Failed loading model",e);
			console.failWithArgError(e.getMessage());
		} catch (Exception e) {
			LOGGER.debug("Failed loading model",e);
			console.failWithArgError("Failed loading model due to: " +e.getMessage());
		}

		// If validation endpoint is not set
		if (validationEndpoint == null || validationEndpoint.isEmpty()) {
			validationEndpoint = predictor.getProperty();
			LOGGER.debug("The validation endpoint was not given explicitly, using the one in the trained model: {}", validationEndpoint);
		}

		return predictor;
	}

	private void validate(ChemPredictor predictor){

		// Set up Validation Results
		setUpValidationResults(predictor);

		console.println(OutputNamingSettings.ProgressInfoTexts.COMPUTING_PREDICTIONS, PrintMode.NORMAL);

		// Set up the ResultsOutputter
		try(
			PredictionResultsWriter predictionWriter = printPredictions ? 
			CLIProgramUtils.setupResultsOutputter(
				this, 
				outputSection.outputFormat,
				outputSection.outputFile,
				predictFile,
				outputSection.printInChI, 
				outputSection.compress) :
			new NOPResultsWriter(); ){


			// Do the validation
			doValidate(predictor, predictionWriter);

		} catch (IllegalAccessException | IOException e) {
			LOGGER.debug("Failed in doValidate",e);
			console.failWithArgError("Could not parse predict-file");
		} catch (EarlyLoadingStopException e){
			// we have failed enough times and should exit the program
			LOGGER.debug("Encountered enough failed records to stop execution", e);
			new CLIProgramUtils.UserInputErrorResolver(console, numSuccessfulPreds, predictFile, 
				progressTracker.getFailures(), predictor.getDataset(), predictor.getDataset().getProperty(), 
				(predictor instanceof ChemClassifier ? ((ChemClassifier)predictor).getNamedLabels() : null), 
				progressTracker.getMaxAllowedFailures(), listFailedRecordsMixin.listFailedRecords).failWithError();
		}

		// We are done with all predictions
		String molSingularOrPlural=(numSuccessfulPreds>1 || numSuccessfulPreds==0?"s":"");
		StringBuilder resultInfo = new StringBuilder(String.format(Locale.ENGLISH, "%nSuccessfully predicted %d molecule%s.",numSuccessfulPreds,molSingularOrPlural));
		
		
		// If we had some failing records
		if (progressTracker.getNumFailures()>0) {
			List<FailedRecord> failedRecs = progressTracker.getFailures();
			resultInfo.append(String.format(Locale.ENGLISH, " Failed predicting %d record%s.", failedRecs.size(),(failedRecs.size()>1 ? "s":"")));
			
			if (numMissingDataFails > 0) {
				String missingDataInfo = numMissingDataFails + " record(s) failed due to missing features - please make sure your data pre-processing is correct, consider using e.g. removal of poor descriptors (DropMissingDataFeatures) or impute missing features (SingleFeatImputer)%n";
				if (console.getVerbosity() == VerbosityLvl.VERBOSE){
					resultInfo.append(missingDataInfo);
				} else {
					LOGGER.debug(missingDataInfo);
				}
			}
			
			if (listFailedRecordsMixin.listFailedRecords) {
				resultInfo.append("%n");
				CLIProgramUtils.appendFailedMolsInfo(resultInfo, failedRecs);
			}
		}
		// Print info
		console.printlnWrapped(resultInfo.toString(), PrintMode.NORMAL);
	}


	private void setUpValidationResults(ChemPredictor predictor) {
		validationMetrics = CLIProgramUtils.setupMetrics(predictor, validationSection);

		// Make sure we've added some metrics
		if (validationMetrics.isEmpty())
			throw new IllegalArgumentException("validation not implemented for predictor " + predictor.getPredictor().getClass());
	}

	private void validateParams(){
		CLIProgramUtils.setupOverallStatsFile(statsOutputFile.overallStatsFile, console);

		// Set up the failures tracker
		progressTracker = ProgressTracker.createStopAfter(earlyTermination.maxFailuresAllowed);
	}

	private void doValidate(ChemPredictor predictor, PredictionResultsWriter predWriter)
			throws IllegalAccessException, FileNotFoundException, IOException {

		// Config the progress-interval
		int totalNumMolsToPredict = 0, iterationCount = 0;
		try {
			totalNumMolsToPredict = predictFile.countNumRecords();
			LOGGER.debug("Found {} molecules in the predictFile (to be predicted)",
					totalNumMolsToPredict);
		} catch (IllegalArgumentException | IOException e){
			LOGGER.debug("Could not parse the predictFile", e);
			console.failWithArgError("Could not read from "+CLIProgramUtils.getParamName(this, "predictFile", "PREDICT_FILE"));
		}
		int progressInterval = CLIProgramUtils.getProgressInterval(totalNumMolsToPredict, 10);
		int numSteps = totalNumMolsToPredict/progressInterval;
		pb.addAdditionalSteps(numSteps);


		// Predict from a CSV- or SDF-file
		NamedLabels labels = null; 
		if (predictor instanceof ChemClassifier) {
			labels = new NamedLabels(((ChemClassifier) predictor).getLabels());
			LOGGER.debug("Using labels from model-file: {}", labels);
		}


		// DO THE PREDICTIONS
		boolean predictionDone = false;

		try (
			MolAndActivityConverter molIterator = (labels!=null?
				// If classification 
				MolAndActivityConverter.Builder.classificationConverter(
					predictFile.getIterator(progressTracker), 
					validationEndpoint, 
					labels).progressTracker(progressTracker).build() :  
				// If regression
				MolAndActivityConverter.Builder.regressionConverter(
					predictFile.getIterator(progressTracker), 
					validationEndpoint).progressTracker(progressTracker).build())
				){

			try {
				molIterator.initialize();
				 // pre-fetch first one in order to check parameters are OK
				LOGGER.debug("Initialized MolAndAcivityConv, result of hasNext(): {}",molIterator.hasNext());
			} catch (Exception e) {
				if (molIterator.getNumOKMols() == 0 || progressTracker.getNumFailures()>0) {
					LOGGER.debug("Invalid arguments for reading the prediction file",e);
				}
				IOUtils.closeQuietly(predWriter); // have to close it (in case Sys-out is controlled)
				new CLIProgramUtils.UserInputErrorResolver(console, molIterator.getNumOKMols(), 
					predictFile, progressTracker.getFailures(), predictor.getDataset(), validationEndpoint, 
					labels, earlyTermination.maxFailuresAllowed, listFailedRecordsMixin.listFailedRecords).failWithError();
			}

			Pair<IAtomContainer, Double> molRecord=null;
			IAtomContainer mol=null;
			double trueValue;
			int index=-1;
			String id="";

			while (molIterator.hasNext()) {
				
				molRecord = molIterator.next();
				
				mol = molRecord.getLeft();
				trueValue = molRecord.getRight();
				if (mol.getProperty(CDKConstants.REMARK) == null)
					mol.removeProperty(CDKConstants.REMARK);
				index = TypeUtils.asInt(CPSignMolProperties.getRecordIndex(mol));
				id = (CPSignMolProperties.hasMolTitle(mol)? CPSignMolProperties.getMolTitle(mol) : null);

				try {
					predictMolecule(predictor, predWriter, mol, trueValue);

					predictionDone=true;
					numSuccessfulPreds++;

				} catch (MissingDataException e) {
					LOGGER.debug("Got a missing data exception - something wrong i pre-processing?");
					progressTracker.register(new FailedRecord.Builder(index, Cause.DESCRIPTOR_CALC_ERROR).withID(id).withReason(e.getMessage()).build());
					numMissingDataFails++;
				} catch (Exception e) {
					LOGGER.debug("Failed molecule due to generic exception", e);
					progressTracker.register(new FailedRecord.Builder(index,Cause.UNKNOWN).withID(id).withReason(e.getMessage()).build());
				} finally {
					iterationCount++;
					if (progressInterval>0 && iterationCount % progressInterval == 0 ){
						// Print progress to stdout
						console.println(" - Processed %d/%d molecules", PrintMode.NORMAL,iterationCount,totalNumMolsToPredict);
						pb.stepProgress();
					}
				}
			}

		} 

		if (!predictionDone){
			LOGGER.debug("No molecules were predicted, with {} record failures: {}", progressTracker.getNumFailures(), progressTracker.getFailures());
			IOUtils.closeQuietly(predWriter); // have to close it (in case Sys-out is controlled)
			new CLIProgramUtils.UserInputErrorResolver(console, 0, predictFile, progressTracker.getFailures(), 
				predictor.getDataset(), validationEndpoint, labels, earlyTermination.maxFailuresAllowed, listFailedRecordsMixin.listFailedRecords).failWithError();;
		}
			
	}

	private void predictMolecule(ChemPredictor chemPredictor, PredictionResultsWriter predictionWriter, IAtomContainer mol, double trueLabel) 
			throws IOException {
		if(chemPredictor instanceof ChemCPRegressor)
			predictMolecule((ChemCPRegressor)chemPredictor, predictionWriter, mol, trueLabel);
		else if (chemPredictor instanceof ChemCPClassifier)
			predictMolecule((ChemCPClassifier)chemPredictor, predictionWriter, mol, (int)trueLabel);
		else if (chemPredictor instanceof ChemVAPClassifier) 
			predictMolecule((ChemVAPClassifier)chemPredictor, predictionWriter, mol, (int) trueLabel);
		else {
			LOGGER.debug("ChemPredictor of a non-supported class: {}", chemPredictor.getClass());
			console.failWithInternalError("Internal problem predicting molecules, please contact Aros Bio and kindly send us the cpsign logfile");
		}
	}

	private void predictMolecule(ChemCPClassifier chemPredictor, PredictionResultsWriter predictionWriter,
			IAtomContainer mol, int trueLabel) throws IOException {

		// Perform prediction
		try {
			Map<String,Double> pvals = MathUtils.roundAll(chemPredictor.predict(mol));
			ResultsHandler resHandler = new ResultsHandler();

			resHandler.setPvalues(pvals);

			// Update metrics
			Map<Integer, Double> numericPvals = chemPredictor.getNamedLabels().reverse(pvals);
			int forcedPredLabel = ClassificationUtils.getPredictedClass(numericPvals);
			for (Metric m : validationMetrics) {
				if (m instanceof CPClassifierMetric) {
					((CPClassifierMetric) m).addPrediction(trueLabel, numericPvals);
				} else if (m instanceof ScoringClassifierMetric) {
					((ScoringClassifierMetric) m).addPrediction(trueLabel, numericPvals);
				} else if (m instanceof PointClassifierMetric) {
					((PointClassifierMetric) m).addPrediction(trueLabel, forcedPredLabel);
				}
			}

			// Print results
			predictionWriter.write(mol, resHandler);

		} catch(CDKException e){
			LOGGER.error("Problem handling a molecule when predicting p-values, skipping it",e);
			return;
		} catch (IllegalStateException e){
			LOGGER.debug("Exception running predictMondrian", e);
			console.failWithInternalError("Could not predict p-values for molecule due to: %s", e.getMessage());
		}

	}

	private void predictMolecule(ChemVAPClassifier chemPredictor, PredictionResultsWriter predictionWriter,
			IAtomContainer ac, int trueLabel) throws IOException {

		// Perform prediction
		try {
			ResultsHandler resHandler = new ResultsHandler();
			CVAPPrediction<String> res = chemPredictor.predict(ac);

			Map<String, Double> probs = MathUtils.roundAll(res.getProbabilities());
			resHandler.setProbabilities(probs);
			resHandler.setP0P1Interval(res.getMeanP0P1Width(), res.getMedianP0P1Width());

			// Update metrics
			Map<Integer, Double> numericProbs = chemPredictor.getNamedLabels().reverse(probs);
			int predictedClass = ClassificationUtils.getPredictedClass(numericProbs);
			for (Metric m : validationMetrics) {
				if (m instanceof VAPMetric){
					((VAPMetric) m).addPrediction((int)trueLabel, numericProbs, res.getMeanP0P1Width(), res.getMedianP0P1Width());
				} else if (m instanceof ProbabilisticMetric) {
					((ProbabilisticMetric) m).addPrediction((int)trueLabel, numericProbs);
				} else if (m instanceof ScoringClassifierMetric) {
					((ScoringClassifierMetric) m).addPrediction((int)trueLabel, numericProbs);
				} else if (m instanceof PointClassifierMetric) {
					((PointClassifierMetric) m).addPrediction(trueLabel, predictedClass);
				}
			}

			// Print results
			predictionWriter.write(ac, resHandler);

		} catch (CDKException e){
			LOGGER.error("Problem handling a molecule when predicting p-values, skipping it",e);
			return;
		} catch (IllegalStateException e){
			LOGGER.debug("Exception running predict", e);
			console.failWithInternalError("Could not predict p-values for molecule due to: %s", e.getMessage());
		}

	}

	private void predictMolecule(ChemCPRegressor signPredictor, PredictionResultsWriter predictionWriter,
			IAtomContainer ac, double trueValue) throws IOException {

		try {
			//predict for confidences
			CPRegressionPrediction resultList = signPredictor.predict(ac, 
					validationSection.calibrationPoints);
			ResultsHandler resHandler = new ResultsHandler();
			resHandler.addRegressionResultConfBased(resultList);

			Map<Double, Range<Double>> intervals = new HashMap<>();
			for (double c : validationSection.calibrationPoints) {
				intervals.put(c, resultList.getInterval(c).getInterval());
			}

			// Update metrics
			for (Metric m : validationMetrics) {
				if (m instanceof PointPredictionMetric) {
					((PointPredictionMetric) m).addPrediction(trueValue, resultList.getY_hat());
				} else if (m instanceof CPRegressionMultiMetric) {
					((CPRegressionMultiMetric) m).addPrediction(trueValue, intervals);
				}
			}

			// Print results
			predictionWriter.write(ac, resHandler);

		} catch (CDKException e){
			LOGGER.error("Problem when handling a molecule when predicting p-values, skipping it",e);
			return;
		} catch (IllegalStateException e){
			LOGGER.debug("Exception running predict", e);
			console.failWithInternalError("Could not predict molecule due to: " + e.getMessage());
		}
	}

}

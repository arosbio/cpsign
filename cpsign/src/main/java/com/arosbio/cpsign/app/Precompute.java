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

import org.apache.commons.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.io.in.ChemFile;
import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.cpsign.app.params.CLIParameters.ClassOrRegType;
import com.arosbio.cpsign.app.params.converters.ChemFileConverter;
import com.arosbio.cpsign.app.params.converters.MLTypeConverters.ClassRegConverter;
import com.arosbio.cpsign.app.params.mixins.ClassificationLabelsMixin;
import com.arosbio.cpsign.app.params.mixins.ConsoleVerbosityMixin;
import com.arosbio.cpsign.app.params.mixins.DescriptorsMixin;
import com.arosbio.cpsign.app.params.mixins.EarlyTerminationMixin;
import com.arosbio.cpsign.app.params.mixins.EchoMixin;
import com.arosbio.cpsign.app.params.mixins.EncryptionMixin;
import com.arosbio.cpsign.app.params.mixins.ListFailedRecordsMixin;
import com.arosbio.cpsign.app.params.mixins.LogfileMixin;
import com.arosbio.cpsign.app.params.mixins.ModelingPropertyMixin;
import com.arosbio.cpsign.app.params.mixins.OutputJARMixin;
import com.arosbio.cpsign.app.params.mixins.ProgramProgressMixin;
import com.arosbio.cpsign.app.params.mixins.TransformerMixin;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIProgramUtils;
import com.arosbio.cpsign.app.utils.CLIProgressBar;
import com.arosbio.cpsign.app.utils.MissingParam;
import com.arosbio.cpsign.app.utils.NullProgress;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ProgramTimer;
import com.arosbio.cpsign.app.utils.CLIConsole.ParamComb;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.CLIProgressBar.SupportsProgressBar;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.cpsign.out.OutputNamingSettings;
import com.arosbio.cpsign.out.OutputNamingSettings.PB;
import com.arosbio.cpsign.out.OutputNamingSettings.ProgressInfoTexts;
import com.arosbio.io.UriUtils;
import com.arosbio.ml.io.ModelInfo;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/*
 * Sections:
 * PB.VALIDATE_PARAMS_PROGRESS
 * PB.LOADING_FILE_OR_MODEL_PROGRESS
 * PB.PARSING_FILE_OR_MODEL_PROGRESS
 * PB.RESULTS_WRITING_PROGRESS
 * @author staffan
 *
 */
@Command(
		name = Precompute.CMD_NAME,
		header = Precompute.CMD_HEADER,
		description = Precompute.CMD_DESCRIPTION,
		descriptionHeading = CPSignApp.DESCRIPTION_HEADER
		)
public class Precompute implements RunnableCmd, SupportsProgressBar {

	private static final Logger LOGGER = LoggerFactory.getLogger(Precompute.class);
	public final static String CMD_NAME = "precompute";
	public final static String CMD_HEADER = "Compute descriptors for a data set";
	public final static String CMD_DESCRIPTION = "The precompute command computes descriptors for chemical data, producing numerical data files in LIBSVM sparse format and meta data files. "+
			"The precomputed data sets are then used by most of the other programs in CPSign.";

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
	
	// Input Section

	//MODELING TYPE
	@Option(names = { "-mt", "--model-type" }, 
			description = "Modeling type:%n"+
					ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(1) classification%n"+
					ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(2) regression%n"+
					ParameterUtils.DEFAULT_VALUE_LINE, 
					converter = ClassRegConverter.class,
					defaultValue= "1",
					paramLabel = ArgumentType.ID_OR_TEXT
			)
	private ClassOrRegType modeltype = ClassOrRegType.CLASSIFICATION;

	// Data input
	@Option(names = { "-td", "--train-data" }, 
			description = "File with molecules in CSV, SDF or JSON format. run "+ParameterUtils.RUN_EXPLAIN_ANSI_ON+"explain chem-formats|@ to get further info.",
			paramLabel = ArgumentType.CHEM_FILE_ARGS,
			parameterConsumer = ChemFileConverter.class)
	private ChemFile trainFile;

	@Option(names = { "-md", "--model-data" }, 
			description = "File with molecules that exclusively should be used for training the scoring algorithm. In CSV, SDF or JSON format",
			hidden=true,
			paramLabel = ArgumentType.CHEM_FILE_ARGS,
			parameterConsumer = ChemFileConverter.class)
	private ChemFile properTrainExclusiveFile;

	@Option(names = { "-cd", "--calibration-data" },
			description = "File with molecules that exclusively should be used for calibrating predictions. In CSV, SDF or JSON format",
			hidden=true,
			paramLabel = ArgumentType.CHEM_FILE_ARGS,
			parameterConsumer = ChemFileConverter.class)
	private ChemFile calibrationExclusiveTrainFile;

	@Mixin
	private ModelingPropertyMixin propertyMix;

	@Mixin
	private ClassificationLabelsMixin labelsMix;

	@Mixin
	private EarlyTerminationMixin earlyTermination = new EarlyTerminationMixin();

	@Option(names = "--min-hac",
			description="Specify the minimum allowed Heavy Atom Count (HAC) allowed for the records. This serves as a sanity check that parsing from file has been OK.",
			paramLabel = ArgumentType.INTEGER,
			defaultValue = "5")
	private int minHAC = 5;

	@Mixin
	private ListFailedRecordsMixin listFailedRecordsMixin = new ListFailedRecordsMixin();

	// Descriptors Section
	@Mixin
	private DescriptorsMixin descriptorSection = new DescriptorsMixin();

	// Transformer section
	@Mixin
	private TransformerMixin transformSection = new TransformerMixin();

	// Output
	@Mixin
	private OutputJARMixin outputSection = new OutputJARMixin();

	// Encryption
	@Mixin
	private EncryptionMixin encryptSection = new EncryptionMixin();

	// General Section

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


		// INIT PROBLEM 
		ChemDataset sp = new ChemDataset(descriptorSection.descriptors);
		pb.stepProgress();

		// DO PRECOMPUTE
		pb.setCurrentTask(PB.PARSING_FILE_OR_MODEL_PROGRESS);
		precompute(sp);

		//pb.stepProgress(); // Stepped internally in method


		// SAVE
		pb.setCurrentTask(PB.SAVING_JAR_PROGRESS);
		saveData(sp);
		pb.stepProgress();
		timer.endSection();

		// FINISH
		pb.finish();
		timer.endProgram();
		console.println("", PrintMode.NORMAL);

		return ExitStatus.SUCCESS.code;
	}

	private void precompute(ChemDataset problem) {
		boolean isClassification = modeltype.equals(ClassOrRegType.CLASSIFICATION);
		CLIProgramUtils.loadData(problem,
				isClassification, 
				trainFile,
				properTrainExclusiveFile,
				calibrationExclusiveTrainFile,
				propertyMix.endpoint,
				labelsMix.labels,
				this, 
				console,
				listFailedRecordsMixin.listFailedRecords,
				minHAC,
				earlyTermination.maxFailuresAllowed);
		timer.endSection();

		if (transformSection.transformers!=null && !transformSection.transformers.isEmpty()) {
			CLIProgramUtils.applyTransformations(problem, isClassification, transformSection.transformers, this, console);
			timer.endSection();
		}

		// Do some validation and give useful output if there is NaNs that need to be taken into account
		CLIProgramUtils.verifyNoMissingDataAndPrintErr(problem, false, console);

	}

	private void saveData(ChemDataset chemData) {
		
		console.print(WordUtils.wrap("Saving precomputed data set to file: " + outputSection.modelFile + ProgressInfoTexts.SPACE_ELLIPSES, console.getTextWidth()).trim(), 
				PrintMode.NORMAL);

		// Write precomputed data to file
		try {
			ModelSerializer.saveDataset(chemData,
					new ModelInfo(getModelName(), outputSection.modelVersion, outputSection.modelCategory),
					outputSection.modelFile, 
					encryptSection.exclusive.encryptSpec,
					outputSection.dataToMount); 
		} catch (IOException e) {
			LOGGER.debug("failed saving precomputed model with IOException",e);
			console.failWithInternalError("Failed saving model to file due to: " + e.getMessage());
		} catch (Exception e) {
			LOGGER.debug("failed saving precomputed model with Exception",e);
			console.failWithInternalError("Failed saving model to file due to: " + e.getMessage());
		}
		console.println(' '+ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);

	}

	private String getModelName(){
		// Take the given name if there was one
		if (outputSection.modelName != null && !outputSection.modelName.isBlank()){
			return outputSection.modelName;
		}
		// Otherwise we take the name of the input file as model name
		if (trainFile != null){
			try {
				return UriUtils.getResourceNameStripFileSuffix(trainFile.getURI());
			} catch (Exception e){
				LOGGER.debug("failed getting the resource name from train-data",e);
			}
		} 
		if (properTrainExclusiveFile != null){
			try {
				return UriUtils.getResourceNameStripFileSuffix(properTrainExclusiveFile.getURI());
			} catch (Exception e){
				LOGGER.debug("failed getting the resource name from proper-train-data",e);
			}
		}

		if (calibrationExclusiveTrainFile != null){
			try {
				return UriUtils.getResourceNameStripFileSuffix(calibrationExclusiveTrainFile.getURI());
			} catch (Exception e){
				LOGGER.debug("failed getting the resource name from calib-excl-data",e);
			}
		}

		// Resort to using the output model name as model-name
		return UriUtils.getResourceNameStripFileSuffix(outputSection.modelFile.toURI());

	}

	private void validateParams() {
		
		if (descriptorSection.descriptors == null || descriptorSection.descriptors.isEmpty()) {
			console.failWithArgError("No descriptors given");
		} 

		if ( trainFile == null && 
				calibrationExclusiveTrainFile==null &&
				properTrainExclusiveFile == null)
			console.failDueToMissingParameters(ParamComb.AND_OR,"",
					new MissingParam("inputFile", "TRAIN_DATA", Precompute.class),
					new MissingParam("calibrationExclusiveTrainFile", "EXCLUSIVE_CALIBRATION_DATA", Precompute.class),
					new MissingParam("properTrainExclusiveFile", "EXCLUSIVE_MODELING_DATA", Precompute.class));

		// If classification - verify labels set
		if (modeltype.equals(ClassOrRegType.CLASSIFICATION)) {
			if ((labelsMix == null || labelsMix.labels == null || labelsMix.labels.isEmpty()))
				console.failDueToMissingParameters(new MissingParam("labels", "--labels", ClassificationLabelsMixin.class));
			if (labelsMix.labels.size()<2)
				console.failWithArgError("Parameter --labels must be at least of length 2");
		} else {
			// Regression 
			if (labelsMix != null && labelsMix.labels != null && !labelsMix.labels.isEmpty()){
				console.failWithArgError("Parameter --labels cannot be given in regression mode");
			}
		}

		/////////
		// JAR //
		/////////
		try {
			CLIProgramUtils.setupOutputModelFile( outputSection.modelFile, 
					this);
		} catch (IOException e) {
			LOGGER.debug("Failed setting up the output JAR-model file",e);
			console.failWithArgError("Could not setup the output model");
		}

		

	}

}

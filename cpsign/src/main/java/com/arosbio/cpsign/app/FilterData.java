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
import com.arosbio.cheminf.data.DataRecordWithRef;
import com.arosbio.cpsign.app.params.CLIParameters.ClassOrRegType;
import com.arosbio.cpsign.app.params.converters.ChemFileConverter;
import com.arosbio.cpsign.app.params.converters.MLTypeConverters.ClassRegConverter;
import com.arosbio.cpsign.app.params.mixins.ClassificationLabelsMixin;
import com.arosbio.cpsign.app.params.mixins.ConsoleVerbosityMixin;
import com.arosbio.cpsign.app.params.mixins.DescriptorsMixin;
import com.arosbio.cpsign.app.params.mixins.EchoMixin;
import com.arosbio.cpsign.app.params.mixins.EncryptionMixin;
import com.arosbio.cpsign.app.params.mixins.LogfileMixin;
import com.arosbio.cpsign.app.params.mixins.ModelingPropertyMixin;
import com.arosbio.cpsign.app.params.mixins.OutputChemMixin;
import com.arosbio.cpsign.app.params.mixins.ProgramProgressMixin;
import com.arosbio.cpsign.app.params.mixins.TransformerMixin;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIProgramUtils;
import com.arosbio.cpsign.app.utils.CLIProgressBar;
import com.arosbio.cpsign.app.utils.MissingParam;
import com.arosbio.cpsign.app.utils.NullProgress;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ProgramTimer;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.CLIConsole.VerbosityLvl;
import com.arosbio.cpsign.app.utils.CLIProgressBar.SupportsProgressBar;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.cpsign.out.OutputNamingSettings;
import com.arosbio.cpsign.out.PredictionResultsWriter;
import com.arosbio.cpsign.out.ResultsHandler;
import com.arosbio.cpsign.out.OutputNamingSettings.PB;
import com.arosbio.cpsign.out.OutputNamingSettings.ProgressInfoTexts;
import com.arosbio.data.DataRecord;
import com.arosbio.io.UriUtils;

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
		name = FilterData.CMD_NAME,
		header = FilterData.CMD_HEADER,
		description = FilterData.CMD_DESCRIPTION,
		descriptionHeading = CPSignApp.DESCRIPTION_HEADER,
		hidden = true
		)
public class FilterData implements RunnableCmd, SupportsProgressBar {

	private static final Logger LOGGER = LoggerFactory.getLogger(FilterData.class);
	public final static String CMD_NAME = "filter-data";
	public final static String CMD_HEADER = "Applies transformations on a data set";
	public final static String CMD_DESCRIPTION = "Computes descriptors, applies transformations and outputs the remaining data in CSV format";

	/*****************************************
	 * INTERNAL STATE
	 *****************************************/

	@Spec private CommandSpec spec;
	private CLIConsole console = CLIConsole.getInstance();
	private CLIProgressBar pb = new NullProgress();
	private ProgramTimer timer = new ProgramTimer(false, console);
//	private EncryptionSpecification encryptSpec = null;


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
//	@Option(names = { "-mi", "--model-in" },
//			description = "Model file with precomputed data (old signatures will be used as starting point, records will be ignored)",
//			paramLabel = ArgumentType.URI_OR_PATH
//			)
//	private URI modelFile;

	@Option(names = { "-td", "--train-data" }, 
			description = "File with molecules in CSV, SDF or JSON format. run @|bold explain chem-formats|@ to get further info.",
			paramLabel = ArgumentType.CHEM_FILE_ARGS,
			parameterConsumer = ChemFileConverter.class)
	private ChemFile trainFile;

	@Mixin
	private ModelingPropertyMixin propertyMix;

	@Mixin
	private ClassificationLabelsMixin labelsMix;

	@Option(names= {"--early-termination-after"}, 
			description = "Early termination stops loading records once passing this number of failed records and fails execution of the program. "
					+ "Specifying a value less than 0 means there is no early termination and loading will continue until finished. "
					+ "This number of failures are the threshold applied to each of the three levels of processing (i.e. reading molecules from file, "
					+ "getting the endpoint activity from the records and CDK-configuration/HeavyAtomCount/descriptor-calculation)%n"+
					ParameterUtils.DEFAULT_VALUE_LINE,
					paramLabel = ArgumentType.INTEGER,
					defaultValue = "-1")
	private int maxFailuresAllowed = -1;

	@Option(names = "--min-hac",
			description="Specify the minimum allowed Heavy Atom Count (HAC) allowed for the records. This serves a s sanity check that parsing from file has been OK.",
			paramLabel = ArgumentType.INTEGER,
			defaultValue = "5")
	private int minHAC = 5;

	@Option(names = {"--list-failed"},
			description = "List @|bold all|@ failed molecules, such as invalid records, molecules removed due to Heavy Atom Count or failures at descriptor calculation. "+
			"The default is otherwise to only list the summary of the number of failed records.")
	private boolean listFailedMolecules = false;


	// Descriptors Section
	@Mixin
	private DescriptorsMixin descriptorSection = new DescriptorsMixin();

	// Transformer section
	@Mixin
	private TransformerMixin filterSection = new TransformerMixin();

	// Output
	@Mixin
	private OutputChemMixin outputSection = new OutputChemMixin();

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

	private PredictionResultsWriter resultsOutputter;

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


		// INIT PROBLEM - (Load previous signatures if any)
		ChemDataset sp = new ChemDataset(descriptorSection.descriptors).setKeepMolRef(true); 
		pb.stepProgress();

		// DO PRECOMPUTE
		pb.setCurrentTask(PB.PARSING_FILE_OR_MODEL_PROGRESS);
		precompute(sp);
		//pb.stepProgress(); // Stepped internally in method

		// SAVE
		pb.setCurrentTask(PB.RESULTS_WRITING_PROGRESS);
		saveDataAgain(sp);
		pb.stepProgress();
		timer.endSection();

		// FINISH
		pb.finish();
		timer.endProgram();
		console.println("", PrintMode.NORMAL);

		return ExitStatus.SUCCESS.code;
	}

	private void precompute(ChemDataset dataset) {
		boolean isClassification = modeltype.equals(ClassOrRegType.CLASSIFICATION);
		CLIProgramUtils.loadData(dataset,
				isClassification, 
				trainFile,
				null, null,
				propertyMix.endpoint,
				labelsMix.labels,
				this, 
				console,
				listFailedMolecules,
				minHAC,
				maxFailuresAllowed);
		timer.endSection();

		if (filterSection.transformers!=null && !filterSection.transformers.isEmpty()) {
			CLIProgramUtils.applyTransformations(dataset, isClassification, filterSection.transformers, this, console);
			timer.endSection();
		}

	}

	private void saveDataAgain(ChemDataset data) {
		// Print results
		console.print(WordUtils.wrap("Saving filtered data set to file: " + outputSection.outputFile + ProgressInfoTexts.SPACE_ELLIPSES, console.getTextWidth()).trim(), 
				PrintMode.NORMAL);
		try {
			for (DataRecord r : data.getDataset()) {

				resultsOutputter.write(((DataRecordWithRef) r).getMolRef(), new ResultsHandler());
				resultsOutputter.flush();
			}
		} catch (IOException e) {
			LOGGER.debug("Failed saving data set",e);
			console.failWithArgError("Failed saving model to file du to: " + e.getMessage());
		} catch (Exception e) {
			LOGGER.debug("failed saving precomputed model with Exception",e);
			console.failWithInternalError("Failed saving model to file due to: " + e.getMessage());
		}
		console.println(' '+ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);


	}


	private void validateParams() {

		if ( trainFile == null )
			console.failDueToMissingParameters(
					new MissingParam("inputFile", "TRAIN_DATA", FilterData.class)
					);

		// Has to check that the labelsMix are set
		if (modeltype.equals(ClassOrRegType.CLASSIFICATION) && 
				(labelsMix == null || labelsMix.labels == null || labelsMix.labels.isEmpty()))
			console.failDueToMissingParameters(new MissingParam("labels", "--labels", ClassificationLabelsMixin.class));

		if (labelsMix!=null && labelsMix.labels!=null && 
				labelsMix.labels.size()<2)
			console.failWithArgError("Parameter --labels must be at least of length 2");
		// If regression - no labelsMix should be given!
		if (modeltype.equals(ClassOrRegType.REGRESSION) &&
				labelsMix != null && labelsMix.labels != null && !labelsMix.labels.isEmpty())
			console.failWithArgError("Parameter --labels cannot be given in regression mode");

		////////////
		// Output //
		////////////

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
				console.failWithArgError("Could not create parent directory of output file: " + outputSection.outputFile);
			}
		}

		// Set up the ResultsOutputter
		resultsOutputter = CLIProgramUtils.setupResultsOutputter(this, 
				outputSection.outputFormat, 
				outputSection.outputFile,
				trainFile,
				outputSection.printInChI, 
				outputSection.compress);

	}

}

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
import java.util.List;
import java.util.Map;

import org.apache.commons.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.cpsign.app.params.mixins.ConsoleVerbosityMixin;
import com.arosbio.cpsign.app.params.mixins.EchoMixin;
import com.arosbio.cpsign.app.params.mixins.EncryptionMixin;
import com.arosbio.cpsign.app.params.mixins.LogfileMixin;
import com.arosbio.cpsign.app.params.mixins.OutputJARMixin;
import com.arosbio.cpsign.app.params.mixins.PrecomputedDatasetMixin;
import com.arosbio.cpsign.app.params.mixins.ProgramProgressMixin;
import com.arosbio.cpsign.app.params.mixins.TransformerMixin.TransformerParamConsumer;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.CLIProgramUtils;
import com.arosbio.cpsign.app.utils.CLIProgressBar;
import com.arosbio.cpsign.app.utils.CLIProgressBar.SupportsProgressBar;
import com.arosbio.cpsign.app.utils.NullProgress;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.cpsign.app.utils.ProgramTimer;
import com.arosbio.cpsign.out.OutputNamingSettings.PB;
import com.arosbio.cpsign.out.OutputNamingSettings.ProgressInfoTexts;
import com.arosbio.data.transform.Transformer;
import com.arosbio.ml.io.ModelIO;
import com.arosbio.ml.io.ModelInfo;
import com.arosbio.ml.io.impl.PropertyFileStructure;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/*
 * The init-step
 * PB.LOADING_FILE_OR_MODEL_PROGRESS
 * PB.TRANSFORMATIONS (but this is added in the CLIProgramUtils.applyTransformations method) - so do not count it here
 * PB.SAVING_JAR_PROGRESS
 * 
 * total "3" steps (but then added an additional one during processing)
 */
@Command(
		name = Transform.CMD_NAME, 
		description = Transform.CMD_DESCRIPTION, 
		descriptionHeading = CPSignApp.DESCRIPTION_HEADER, 
		header = Transform.CMD_HEADER
		)
public class Transform implements RunnableCmd, SupportsProgressBar {

	private static final Logger LOGGER = LoggerFactory.getLogger(Aggregate.class);

	public final static String CMD_NAME = "transform";
	public final static String CMD_HEADER = "Apply transformations on the features of a precomputed dataset";
	public static final String CMD_DESCRIPTION = "Optionally separate the @|bold precompute|@ and feature transformations into separate steps. "+
		"In this way several different transformation approaches can be compared but "+
		"possibly saving computational resources by performing the descriptor calculation once.";
	
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

	// Transformer section
	@Option(names = {"--transform", "--transformations" }, 
			description = "Transformations that filter, solves duplicate entries, feature selection and more. Run "+ParameterUtils.RUN_EXPLAIN_ANSI_ON+"explain transform"+ParameterUtils.ANSI_OFF+
			" to get a list of all available transformations",
			parameterConsumer = TransformerParamConsumer.class,
			split = ParameterUtils.SPLIT_WS_REGEXP,
			arity = "0..*",
			paramLabel = ArgumentType.TEXT,
			required = true
			)
	public List<Transformer> transformers = new ArrayList<>();

	// Model Output options
	@Mixin
	private OutputJARMixin outputSection = new OutputJARMixin();

	// Encryption
	@Mixin
	private EncryptionMixin encryptSection = new EncryptionMixin();

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

	@SuppressWarnings("null")
	@Override
	public Integer call() {

		CLIProgramUtils.doFullProgramConfig(this);
		pb.stepProgress(); // Finished program init

		// LOAD
		pb.setCurrentTask(PB.PARSING_FILE_OR_MODEL_PROGRESS);
		ChemDataset data = null;
		String oldModelName = null;
		try {
			data = CLIProgramUtils.loadPrecomputedData(inputSection, encryptSection.exclusive.encryptSpec, console, true);
			Map<String,Object> props = ModelIO.getCPSignProperties(inputSection.getAsFile().toURI());
			try {
				oldModelName = CollectionUtils.getArbitratyDepth(props, PropertyFileStructure.InfoSection.MODEL_NAME_KEY).toString();
			} catch (Exception e){
				LOGGER.debug("Failed getting the input model name", e);
			}
			LOGGER.debug("Loaded precomputed data set with old model name={}", oldModelName);
		} catch (Exception e) {
			LOGGER.debug("Failed loading data in 'transform'",e);
			console.failWithInternalError();
		}
		pb.stepProgress();
		timer.endSection();


		// Do transformations - bumping progress bar and setting current task is done within the method
		CLIProgramUtils.applyTransformations(data,
			data.getTextualLabels()!=null,
			transformers, this, console);
		
		// SAVE
		pb.setCurrentTask(PB.SAVING_JAR_PROGRESS);
		saveData(data, oldModelName);
		pb.stepProgress();
		timer.endSection();

		// FINISH PROGRAM
		pb.finish();
		timer.endProgram();
		console.println("", PrintMode.NORMAL);
		return ExitStatus.SUCCESS.code;
	}

	private void saveData(ChemDataset chemData, String inputModelName) {
		
		console.print(WordUtils.wrap("Saving precomputed data set to file: " + outputSection.modelFile + ProgressInfoTexts.SPACE_ELLIPSES, console.getTextWidth()).trim(), 
				PrintMode.NORMAL);

		// Write precomputed data to file
		try {
			ModelSerializer.saveDataset(chemData,
					new ModelInfo(
						outputSection.modelName != null ? outputSection.modelName : inputModelName, // use new name if given - or re-use the one given
						outputSection.modelVersion, outputSection.modelCategory),
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


}

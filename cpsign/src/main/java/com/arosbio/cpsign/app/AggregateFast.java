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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.GlobalConfig.Defaults.PredictorType;
import com.arosbio.commons.MathUtils;
import com.arosbio.commons.TypeUtils;
import com.arosbio.cpsign.app.params.mixins.ConsoleVerbosityMixin;
import com.arosbio.cpsign.app.params.mixins.EchoMixin;
import com.arosbio.cpsign.app.params.mixins.LogfileMixin;
import com.arosbio.cpsign.app.params.mixins.ProgramProgressMixin;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIProgramUtils;
import com.arosbio.cpsign.app.utils.CLIProgressBar;
import com.arosbio.cpsign.app.utils.MissingParam;
import com.arosbio.cpsign.app.utils.NullProgress;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ProgramTimer;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.CLIProgressBar.SupportsProgressBar;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.cpsign.out.OutputNamingSettings;
import com.arosbio.cpsign.out.OutputNamingSettings.PB;
import com.arosbio.cpsign.out.OutputNamingSettings.ProgressInfoTexts;
import com.arosbio.io.IOSettings;
import com.arosbio.io.UriUtils;
import com.arosbio.ml.io.ModelIO.ModelJarProperties;
import com.arosbio.ml.io.ModelIO.ModelJarProperties.Directories;
import com.arosbio.ml.io.ModelIO.ModelType;
import com.arosbio.ml.io.impl.PropertyFileStructure;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/*
 * PB.VALIDATE_PARAMS_PROGRESS
 * PB.AGGREGATING_MODELS_PROGRESS
 * PB.SAVING_JAR_PROGRESS
 */
@Command(
		name = AggregateFast.CMD_NAME, 
		description = AggregateFast.CMD_DESCRIPTION, 
		descriptionHeading = CPSignApp.DESCRIPTION_HEADER, 
		header = AggregateFast.CMD_HEADER
		)
public class AggregateFast implements RunnableCmd, SupportsProgressBar {

	private static final Logger LOGGER = LoggerFactory.getLogger(AggregateFast.class);

	public static final String CMD_NAME = "fast-aggregate";
	public static final String CMD_HEADER = "Aggregate data from multiple models, @|bold without validation|@";
	public static final String CMD_DESCRIPTION = "The fast-aggregate program joins partially trained predictors into the final aggregated one. "+
			"This program facilitate a high level way of distributing the training step for ACP and VAP predictors. "+
			"Use the --splits flag in the @|bold train|@ program to train a partial predictor, and then use fast-aggregate "+ 
			"to merge everything together.";

	/*****************************************
	 * INTERNAL STATE
	 *****************************************/

	@Spec private CommandSpec spec;
	private CLIConsole console = CLIConsole.getInstance();
	private CLIProgressBar pb = new NullProgress();
	private ProgramTimer timer = new ProgramTimer(false, console);
	private JarOutputStream outputJar;
	private int numFailedModels=0, numSuccessfulModels=0;
	private List<Double> lowPercentiles=new ArrayList<>(), highPercentiles=new ArrayList<>();
	private JsonObject jsonProperties, parametersSection;
	private byte[] buffer = new byte[4096];
	private List<URI> modelsToAggregate=null;
	private PredictorType predictorType;


	/*****************************************
	 * OPTIONS
	 *****************************************/

	@Option(names ={"-m","--model-files"}, 
			description="A list (space or comma-separated) of models that should be aggregated. "+
					"Valid input is directories, glob patterns (with wildcard character '*'), explicit files or URIs. "+
					"Note that models can be a mix of non-encrypted and encrypted models.", 
					required = true,
					arity = ParameterUtils.LIST_TYPE_ARITY,
					split = ParameterUtils.SPLIT_WS_REGEXP,
					paramLabel = ArgumentType.URI_OR_PATH
			)
	private List<String> inputModels;

	@Option(names = {"-af", "--accept-fail"},
			description="Accept failure if a model cannot be added to the aggregated model (i.e. if model is of wrong type, does not have matching signatures, cannot be decrypted etc.). Default is to fail execution")
	private boolean acceptFail=false;

	// Output model

	@Option(names = {"-mo", "--model-out"}, 
			description = "Path to where the new model should be saved",
			required = true,
			paramLabel = ArgumentType.FILE_PATH
			)
	private File modelFile;

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

		try {

			console.print(OutputNamingSettings.ProgressInfoTexts.VALIDATING_ARGS, PrintMode.NORMAL);
			assertAndSetupAggregate();
			console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
			timer.endSection();
			pb.stepProgress();

			pb.setCurrentTask(PB.AGGREGATING_MODELS_PROGRESS);
			aggregate();
			timer.endSection();
			pb.stepProgress();

			// FINISH PROGRAM
			pb.finish();
			timer.endProgram();
			console.println("", PrintMode.NORMAL);
			return ExitStatus.SUCCESS.code;

		} finally {
			com.arosbio.io.IOUtils.closeQuietly(outputJar);
		}
	}

	private void assertAndSetupAggregate() {

		// Check models were given and resolve to proper uri's
		if (inputModels == null || 
				inputModels.isEmpty())
			console.failDueToMissingParameters(new MissingParam("inputModels", "MODELS", AggregateFast.class));

		// Parse URIs, paths, directories etc into a list of URIs
		try {
			modelsToAggregate = UriUtils.getResources(inputModels);
		} catch (IOException e) {
			LOGGER.debug("Failed setting up the resources to aggregate",e);
			console.failWithArgError("Invalid model input: " + e.getMessage());
		}
		if (modelsToAggregate.isEmpty())
			console.failDueToMissingParameters(new MissingParam("inputModels", "MODELS", AggregateFast.class));

		// Set up the final output-model including parent folders
		if (modelFile==null)
			console.failDueToMissingParameters(new MissingParam("modelFile", "MODEL_OUTPUT", AggregateFast.class));

		// Update the progress-bar max num steps
		pb.addAdditionalSteps(modelsToAggregate.size());

		//////////
		// JAR //
		//////////
		try {
			CLIProgramUtils.setupOutputModelFile(
					modelFile, 
					this);
		} catch (IOException e) {
			LOGGER.debug("Failed setting up the output JAR-model file",e);
			console.failWithArgError("Could not setup the output model");
		}

	}

	@SuppressWarnings("null")
	private void aggregate() {

		LOGGER.debug("Models to aggregate: {}", modelsToAggregate.toString());

		console.println("Starting to aggregate models...", PrintMode.NORMAL);
		initOutputJar();

		ModelType type = null;
		try {
			type = ModelSerializer.getType(parametersSection); 
		} catch (Exception e){
			LOGGER.debug("Failed getting the model type from parameter section: {}",parametersSection);
			com.arosbio.io.IOUtils.closeQuietly(outputJar);
			console.failWithArgError("Model of unrecognized/unsupported type, is it an old model?");
		}

		if (type == ModelType.CHEM_PREDICTOR){
			doAggregateTrained();
		} else {
			// Close the jar-file
			com.arosbio.io.IOUtils.closeQuietly(outputJar);

			if (type == ModelType.PLAIN_PREDICTOR){
				console.failWithArgError(CMD_NAME + " not possible with numeric models");
			} else if (type == ModelType.PRECOMPUTED_DATA) {
				console.failWithArgError(CMD_NAME + " not possible with precomputed data");
			} else {
				console.failWithArgError(CMD_NAME + " not possible with model of type " + type.name);
			}
				
		}

		try {
			outputJar.close();
		} catch(IOException e) {
			LOGGER.debug("failed closing the jaroutputstream",e);
			console.failWithInternalError("Failed closing the final jar: " + e.getMessage());
		}

		StringBuilder sb = new StringBuilder(200);
		sb.append("Successfully aggregated ");
		sb.append(numSuccessfulModels);
		sb.append(" models");
		if(numFailedModels>0){
			sb.append(", failed to add ");
			sb.append(numFailedModels);
			if (numFailedModels>1)
				sb.append(" models");
			else
				sb.append(" model");
		}
		sb.append(".%n");
		console.printlnWrapped(sb.toString(), PrintMode.NORMAL);
		console.println("Aggregated model saved at:%n" + modelFile, PrintMode.NORMAL);

	}

	/**
	 * init JarOutputStream, add MANIFEST, plugin.xml, help-folder and icons-folder
	 */
	private void initOutputJar(){

		URI originalModelName = modelsToAggregate.get(0);
		LOGGER.debug("Trying to init aggregated model from {}", modelsToAggregate.get(0));
		try{
			File localFile = UriUtils.getFile(originalModelName);

			try(
					JarFile currentJar = new JarFile(localFile);
					){

				// Init the JarOutputStream
				Manifest manifest = currentJar.getManifest();
				outputJar = new JarOutputStream(new FileOutputStream(modelFile), manifest);
				LOGGER.debug("Initiated aggregated model {}", modelFile);


				// Read the cpsign.json
				try(
						InputStream propertyFile = currentJar.getInputStream(currentJar.getJarEntry(ModelJarProperties.JSON_PROPERTY_FILE));
						Reader propertyReader = new InputStreamReader(propertyFile); 
						){

					jsonProperties = (JsonObject) Jsoner.deserialize(propertyReader); 
					parametersSection = (JsonObject) jsonProperties.get(PropertyFileStructure.ParameterSection.NESTING_KEY);
					if (jsonProperties.containsKey(PropertyFileStructure.InfoSection.NESTING_KEY)){
						parametersSection.putAll((JsonObject) jsonProperties.get(PropertyFileStructure.InfoSection.NESTING_KEY));
					}
					LOGGER.debug("Parsed properties: {}", jsonProperties);
				}

				// Make sure this is an AggregatedPredictor model and is trained! Precomputed stuff is not OK
				Object mlType = CollectionUtils.getArbitratyDepth(parametersSection, PropertyNameSettings.ML_TYPE_KEY);
				if (mlType == null)
					throw new IllegalArgumentException("Cannot aggregate precomputed data with fast-aggregate");
				predictorType = PredictorType.getPredictorType(TypeUtils.asInt(mlType));
				if (predictorType.isTCP())
					throw new IllegalArgumentException("Cannot aggregate TCP models with fast-aggregate");
				else if (!predictorType.isAggregatedPredictor())
					throw new IllegalArgumentException("Cannot aggregate non-aggregated models with fast-aggregate");

				// Get percentiles
				lowPercentiles.add((Double)CollectionUtils.getArbitratyDepth(parametersSection,PropertyNameSettings.LOW_PERCENTILE_KEY));
				highPercentiles.add((Double)CollectionUtils.getArbitratyDepth(parametersSection,PropertyNameSettings.HIGH_PERCENTILE_KEY));

				// copy icons
				copyEverythingInDirectory(currentJar, Directories.ICONS_DIRECTORY+'/');
				LOGGER.debug("copied icons-directory");

				// copy help
				copyEverythingInDirectory(currentJar, Directories.HELP_DIRECTORY+'/');
				LOGGER.debug("copied help-directory");

				// copy signatures and models!
				copyEverythingInDirectory(currentJar, Directories.DATA_DIRECTORY+'/');
				LOGGER.debug("copied data-directory");
				copyEverythingInDirectory(currentJar, Directories.MODEL_DIRECTORY+'/');
				LOGGER.debug("copied models-directory");

				// now we've successfully added one of the models 
				numSuccessfulModels++;
			}

			LOGGER.debug("Copied everything successfuly from the first model");
			modelsToAggregate.remove(0);

			if (! UriUtils.isLocalFile(originalModelName) && localFile != null) {
				LOGGER.debug("attempting to delete local copy of remote uri");
				// need to remove the local copied file
				FileUtils.forceDelete(localFile);
				LOGGER.debug("deleted local copy of remote uri: {}", originalModelName);
			}

		} catch (Exception e){
			failModel(originalModelName, e);
			// If we accept fail => do init again but remove the failing model
			modelsToAggregate.remove(0);
			initOutputJar();
		}
	}

	private void failModel(URI model, Exception reason) {
		numFailedModels++;
		LOGGER.debug("Failed to add model: {}, reason: {}", model, reason);
		if (! acceptFail) {
			console.failWithArgError("Failed to aggregate model:%n%s%nDue to reason:%s",model,reason.getMessage());
		}
	}

	private void copyEverythingInDirectory(JarFile jf, String directory) throws IOException {

		// Enumerate all entries - copy the ones part of this folder
		Enumeration<JarEntry> entries = jf.entries();
		while (entries.hasMoreElements()){

			JarEntry je = entries.nextElement();
			if (je.getName().startsWith(directory)) {
				try {
					copyEntryToJar(jf, je);
					LOGGER.debug("copied entry: {}", je.getName());
				} catch (ZipException e) {
					if (e.getMessage().contains("duplicate")) {
						LOGGER.debug("entry already existed: {}",je.getName());
					} else {
						console.failWithArgError("Failed aggregating model due to: " + e.getMessage());
					}
				}
			}
		}
	}

	private void copyEntryToJar(JarFile jf, JarEntry entry, String destination) throws IOException{
		try(

				InputStream is = jf.getInputStream(entry);
				){
			outputJar.putNextEntry(new JarEntry(destination));
			int bytesRead = 0;
			while ((bytesRead = is.read(buffer)) != -1) {
				outputJar.write(buffer, 0, bytesRead);
			}
			outputJar.flush();
			outputJar.closeEntry();
		}
	}

	private void copyEntryToJar(JarFile jf, JarEntry entry) throws IOException{
		copyEntryToJar(jf, entry, entry.getName());
	}


	private void doAggregateTrained(){

		for (URI currentModel: modelsToAggregate) {
			LOGGER.debug("Aggregating model={}",currentModel);
			File localFile=null;
			try {
				localFile = UriUtils.getFile(currentModel);
			} catch (IllegalArgumentException | IOException e) {
				LOGGER.debug("Failed copy uri to local file system",e);
				console.failWithInternalError("Failed to copy non-local URI to local filesystem: "+e.getMessage());
			}

			try (
					JarFile currentJar = new JarFile(localFile);
					){

				// Read the cpsign.json
				try (
						InputStream propertyFile = currentJar.getInputStream(currentJar.getJarEntry(ModelJarProperties.JSON_PROPERTY_FILE));
						Reader propertyReader = new InputStreamReader(propertyFile); 
						){

					JsonObject props = (JsonObject) Jsoner.deserialize(propertyReader);
					JsonObject paramProps = (JsonObject) props.get(PropertyFileStructure.ParameterSection.NESTING_KEY);

					Object mlTypeArg = CollectionUtils.getArbitratyDepth(paramProps, PropertyNameSettings.ML_TYPE_KEY);
					if (mlTypeArg == null)
						throw new IllegalArgumentException("Cannot aggregate precomputed data with fast-aggregate");
					PredictorType loadedType = PredictorType.getPredictorType(TypeUtils.asInt(mlTypeArg));
					if (loadedType != predictorType)
						throw new IllegalArgumentException("Predictors of different types cannot be aggregated");

					// Copy everything in the data and model directories
					copyEverythingInDirectory(currentJar, Directories.MODEL_DIRECTORY+'/');
					copyEverythingInDirectory(currentJar, Directories.DATA_DIRECTORY+'/');

					// Get percentiles
					lowPercentiles.add((Double)CollectionUtils.getArbitratyDepth(paramProps,PropertyNameSettings.LOW_PERCENTILE_KEY));
					highPercentiles.add((Double)CollectionUtils.getArbitratyDepth(paramProps,PropertyNameSettings.HIGH_PERCENTILE_KEY));
				}

				numSuccessfulModels++;

			} catch (Exception e){
				failModel(currentModel, e);
			}

			if (! UriUtils.isLocalFile(currentModel) && localFile != null) {
				LOGGER.debug("attempting to delete local copied file");
				// need to remove the local copied file
				try {
					FileUtils.forceDelete(localFile);
				} catch(IOException e) {
					LOGGER.debug("failed deleting local copied uri",e);
					console.printlnWrappedStdErr("Could not delete local copy of file: " + localFile, PrintMode.NORMAL);
				}
				LOGGER.debug("deleted local copy of remote uri: {}", currentModel);
			}

			// Step progress in the end
			pb.stepProgress();
		}

		pb.setCurrentTask(PB.SAVING_JAR_PROGRESS);

		// Use mean value for percentiles (assume equal number of ICP's in each model) - only if there are percentiles computed!
		lowPercentiles = MathUtils.filterNull(lowPercentiles);
		highPercentiles = MathUtils.filterNull(highPercentiles);
		if (!lowPercentiles.isEmpty() && !highPercentiles.isEmpty()){
			parametersSection.put(PropertyNameSettings.LOW_PERCENTILE_KEY, MathUtils.mean(lowPercentiles));
			parametersSection.put(PropertyNameSettings.HIGH_PERCENTILE_KEY, MathUtils.mean(highPercentiles));
		}

		// Write the property-file (cpsign.json)
		try {
			outputJar.putNextEntry(new JarEntry(ModelJarProperties.JSON_PROPERTY_FILE));
			IOUtils.write(jsonProperties.toJson(), outputJar, IOSettings.CHARSET);
			outputJar.closeEntry();
		} catch(Exception e) {
			LOGGER.debug("Failed writing cpsign.json property file",e);
			console.failWithInternalError("Could not write the model property file due to: " + e.getMessage());
		}
		LOGGER.debug("Written cpsign.json property file");

		pb.stepProgress();

	}

}

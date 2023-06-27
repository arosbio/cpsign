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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cheminf.ChemPredictor;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.StringUtils;
import com.arosbio.cpsign.app.params.mixins.ConsoleVerbosityMixin;
import com.arosbio.cpsign.app.params.mixins.EchoMixin;
import com.arosbio.cpsign.app.params.mixins.EncryptionMixin;
import com.arosbio.cpsign.app.params.mixins.LogfileMixin;
import com.arosbio.cpsign.app.params.mixins.OutputJARMixin;
import com.arosbio.cpsign.app.params.mixins.PercentilesMixin;
import com.arosbio.cpsign.app.params.mixins.PrecomputedDatasetMixin;
import com.arosbio.cpsign.app.params.mixins.PredictorMixinClasses;
import com.arosbio.cpsign.app.params.mixins.ProgramProgressMixin;
import com.arosbio.cpsign.app.params.mixins.RNGSeedMixin;
import com.arosbio.cpsign.app.params.mixins.TransformerMixin;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.CLIProgramUtils;
import com.arosbio.cpsign.app.utils.CLIProgressBar;
import com.arosbio.cpsign.app.utils.CLIProgressBar.SupportsProgressBar;
import com.arosbio.cpsign.app.utils.NullProgress;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.cpsign.app.utils.ProgramTimer;
import com.arosbio.cpsign.out.OutputNamingSettings;
import com.arosbio.cpsign.out.OutputNamingSettings.PB;
import com.arosbio.cpsign.out.OutputNamingSettings.ProgressInfoTexts;
import com.arosbio.data.Dataset;
import com.arosbio.data.MissingDataException;
import com.arosbio.ml.algorithms.MLAlgorithm;
import com.arosbio.ml.cp.ConformalPredictor;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.nonconf.classification.NCMMondrianClassification;
import com.arosbio.ml.cp.nonconf.regression.NCMRegression;
import com.arosbio.ml.cp.tcp.TCPClassifier;
import com.arosbio.ml.interfaces.AggregatedPredictor;
import com.arosbio.ml.interfaces.ClassificationPredictor;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.io.ModelIO;
import com.arosbio.ml.io.ModelInfo;
import com.arosbio.ml.io.MountData;
import com.arosbio.ml.io.impl.PropertyFileStructure;
import com.arosbio.ml.sampling.SamplingStrategy;
import com.arosbio.ml.vap.avap.AVAPClassifier;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/*
 * Sections:
 * PB.VALIDATE_PARAMS_PROGRESS
 * PB.PARSING_FILE_OR_MODEL_PROGRESS
 * PB.TRAINING_PROGRESS
 * PB.COMPUTING_PERCENTILES_PROGRESS
 * PB.SAVING_JAR_PROGRESS
 * @author staffan
 *
 */
@Command(
		name = Train.CMD_NAME, 
		description = Train.CMD_DESCRIPTION, 
		descriptionHeading = CPSignApp.DESCRIPTION_HEADER, 
		header = Train.CMD_HEADER
		)
public class Train implements RunnableCmd, SupportsProgressBar {

	private static final Logger LOGGER = LoggerFactory.getLogger(Train.class);
	public final static String CMD_NAME = "train";
	public final static String CMD_HEADER = "Train a predictor model";
	public final static String CMD_DESCRIPTION = "Train an Aggragated Conformal Predictor (ACP), Venn-ABERS Predictor (VAP) "+ 
			"or Transductive Conformal Predictor (TCP). The trained models can later be used "+
			"in predictions. For ACP and VAP, the underlying scoring algorihtm is trained " + 
			"and can be reused for all further predictions, whereas the TCP predictor only "+ 
			"precomputes the descriptors and sets parameters for future training+predictions (as the "+ 
			"underlying scoring algorithm needs to be trained for each new prediction). Input to "+
			"@|bold train|@ must be a precomputed data set from @|bold precompute|@.";

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

	@Mixin
	private PrecomputedDatasetMixin inputSection;

	@Mixin
	private PredictorMixinClasses.AllPTMixin predOpts;

	@Mixin
	private PercentilesMixin percentilesArgs = new PercentilesMixin();

	@Option(names = {"--splits"},
			description= "(ACP/VAP) Run only a specific set of training splits. A means of parallelizing the training step. "+
					"If a folded sampling strategy is used, the random seed is @|bold required|@! Indexing starts at 1, i.e. allowed "+
					"indices are [1,#num_models]",
					split = ParameterUtils.SPLIT_WS_COMMA_REGEXP,
					arity = "1..*",
					paramLabel = ArgumentType.INTEGER
			)
	private List<Integer> runOnlySplits;

	// Transformers section
	@Mixin
	private TransformerMixin transformerArgs;

	// JAR options
	@Mixin
	private OutputJARMixin outputSection; 

	// Encryption
	@Mixin
	private EncryptionMixin encryptSection = new EncryptionMixin();

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
	@SuppressWarnings("null")
	public Integer call() {

		CLIProgramUtils.doFullProgramConfig(this);

		console.print(OutputNamingSettings.ProgressInfoTexts.VALIDATING_ARGS, PrintMode.NORMAL);
		validateGeneralParams();
		LOGGER.debug("Validated general arguments");

		ChemPredictor predictor = null;

		try {
			predictor = CLIProgramUtils.getChemPredictor(
					predOpts.getPredictor(console), console);
		} catch (Exception e) {
			LOGGER.debug("Failed init the predictor",e);
			console.failWithArgError("Failed setting up predictor with given parameters: " + e.getMessage());
		}
		LOGGER.debug("Init ChemPredictor of class {}, using predictor of class: {}",
			predictor.getClass(), predictor.getPredictor().getClass());

		// Update the number of total steps in case of aggregated predictor
		if (predictor.getPredictor() instanceof AggregatedPredictor) {
			if ( runOnlySplits != null && ! runOnlySplits.isEmpty()) {
				pb.addAdditionalSteps( runOnlySplits.size());
			} else {
				pb.addAdditionalSteps(((AggregatedPredictor)predictor.getPredictor()).getStrategy().getNumSamples());
			}
		}

		if (! (predictor.getPredictor() instanceof AggregatedPredictor))
			assertTrainParamsAggregatedPredictor(predictor);


		console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
		// Display the seed used for this run
		console.println("Using RNG seed: %s" , PrintMode.VERBOSE,GlobalConfig.getInstance().getRNGSeed());
		pb.stepProgress();
		timer.endSection();

		// ---- VALIDATION DONE


		// LOAD
		pb.setCurrentTask(PB.PARSING_FILE_OR_MODEL_PROGRESS);
		loadData(predictor);
		pb.stepProgress();
		timer.endSection();

		// TRAIN 
		pb.setCurrentTask(PB.TRAINING_PROGRESS);
		train(predictor);
		pb.stepProgress();
		timer.endSection();

		// COMPUTE PERCENTILE
		pb.setCurrentTask(PB.COMPUTING_PERCENTILES_PROGRESS);
		computePercentiles(predictor);
		pb.stepProgress();

		// SAVE
		pb.setCurrentTask(PB.SAVING_JAR_PROGRESS);
		saveModel(predictor);
		timer.endSection();

		// FINISH PROGRAM
		pb.finish();
		timer.endProgram();

		console.println("", PrintMode.NORMAL);

		return ExitStatus.SUCCESS.code;
	}

	private void validateGeneralParams() {

		/////////
		// JAR //
		/////////
		try {
			CLIProgramUtils.setupOutputModelFile(outputSection.modelFile, 
					this);
		} catch (IOException e) {
			LOGGER.debug("Failed setting up the output JAR-model file",e);
			console.failWithArgError("Could not setup the output model");
		}

		// Make sure we have a valid model name
		if ( outputSection.modelName == null ||  outputSection.modelName.isEmpty()) {

			try {
				Map<String,Object> props = ModelIO.getCPSignProperties(inputSection.getAsFile().toURI());
				outputSection.modelName = CollectionUtils.getArbitratyDepth(props, PropertyFileStructure.InfoSection.MODEL_NAME_KEY).toString();
				LOGGER.debug("No modelName given, using the one from the precomputed dataset: {}",outputSection.modelName);
			} catch (Exception e){
				LOGGER.debug("Failed getting the input model name (to set for the output model as no explicit one was given)", e);
			}

			if (outputSection.modelName == null){
				outputSection.modelName = CLIProgramUtils.getModelNameFromFileName( outputSection.modelFile);
				LOGGER.debug("Failed taking the model name from the precomputed data, using the output model file-name instead: {}",  outputSection.modelName);
			}

		}

		// Try to load potential old mounted data if specified
		if ( outputSection.keepMounts ) {
			try {
				List<String> oldMounts = ModelIO.listMountLocations( inputSection.getAsFile().toURI());
				LOGGER.debug("Found old mounted data that should be copied into final model, locations: {}", oldMounts);
				if (oldMounts!=null && ! oldMounts.isEmpty()) {

					List<MountData> tmp = new ArrayList<>();
					if (outputSection.dataToMount != null && outputSection.dataToMount.length>0){
						for (MountData md : outputSection.dataToMount){
							tmp.add(md);
						}
					} 

					for (String loc : oldMounts) {
						LOGGER.debug("Trying to copy old mounted data at location={}",loc);
						MountData md = new MountData(loc, ModelIO.getMountedDataAsTmpFile( inputSection.getAsFile().toURI(), loc).toURI());
						tmp.add(md);
					}
					if (! tmp.isEmpty()){
						outputSection.dataToMount = tmp.toArray(new MountData[]{});
					}
				}
			} catch (IllegalArgumentException | IOException e) {
				LOGGER.debug("Failed copying old mounted data",e);
				console.failWithArgError("Failed reading previous mount-data: " + e.getMessage());
			}
		}

	}

	

	private void assertTrainParamsAggregatedPredictor(ChemPredictor predictor) {
		

		if ( runOnlySplits != null && 
				!  runOnlySplits.isEmpty()) {
			
			if (! (predictor.getPredictor() instanceof AggregatedPredictor)) {
				console.failWithArgError("Parameter " +CLIProgramUtils.getParamName(this, "runOnlySplits", "SPLITS") + " can only be given for an aggregated predictor type");
			}
			
			SamplingStrategy ss = ((AggregatedPredictor) predictor.getPredictor()).getStrategy();
			for (int split:  runOnlySplits){
				if (split < 1 || split >  ss.getNumSamples()) {
					console.failWithArgError("Parameter " +CLIProgramUtils.getParamName(this, "runOnlySplits", "SPLITS")+
							" must be in the range [1.."+ ss.getNumSamples()+"] for each index");
				}
			}
		}

	}

	private void loadData(ChemPredictor predictor) {
		// Load precomputed data and training data 
		try {
			CLIProgramUtils.loadPrecomputedData(predictor, inputSection, encryptSection.exclusive.encryptSpec, console);
		} catch (Exception e) {
			LOGGER.debug("Failed loading data in 'train'",e);
			console.failWithInternalError();
		}

		// Do transformations
		CLIProgramUtils.applyTransformations(predictor.getDataset(), predictor.getPredictor() instanceof ClassificationPredictor,  transformerArgs.transformers, this, console);

		// Verify no missing data
		CLIProgramUtils.verifyNoMissingDataAndPrintErr(predictor.getDataset(), true, console);
	}


	private void train(ChemPredictor signPredictor) {

		// Train
		try {
			LOGGER.debug("Starting train");
			trainPredictor(signPredictor.getPredictor(), signPredictor.getDataset());
		} catch (MissingDataException mde) {
			LOGGER.debug("Missing data exception running train",mde);
			console.failWithArgError("Failed training due to missing data for one or multiple features, please revise your pre-processing prior to training");
		} catch (IllegalArgumentException e){
			LOGGER.debug("Exception running train ", e);
			console.failWithArgError(e.getMessage());
		} 

	}

	private void trainPredictor(Predictor predictor, Dataset problem) {
		if(predictor instanceof TCPClassifier) {
			trainPredictor((TCPClassifier)predictor, 
					problem);
		} else if (predictor instanceof ACPClassifier) {
			NCMMondrianClassification ncm = (NCMMondrianClassification) ((ACPClassifier) predictor).getICPImplementation().getNCM();
			trainPredictor((AggregatedPredictor)predictor, 
					problem,
					"ACP Classification",
					" using NCM " + ncm.getName() + " with scorer " + formatAlgInfo(ncm.getModel()));
		} else if (predictor instanceof ACPRegressor) {
			NCMRegression ncm = ((ACPRegressor)predictor).getICPImplementation().getNCM();

			String verboseInfo = " using NCM " + ncm.getName() + " with scorer " + formatAlgInfo(ncm.getModel());
			if (ncm.requiresErrorModel()) {
				verboseInfo += " and error model " + formatAlgInfo(ncm.getErrorModel());
			}

			trainPredictor((AggregatedPredictor)predictor, 
					problem,
					"ACP Regression",
					verboseInfo);

		} else if (predictor instanceof AVAPClassifier) {
			trainPredictor((AggregatedPredictor)predictor,
					problem,
					"Venn-ABERS Predictor",
					" using scoring algorithm " + formatAlgInfo(((AVAPClassifier)predictor).getScoringAlgorithm()));
		} else {
			LOGGER.debug("predictor of non-supported class: {}", predictor.getClass());
			console.failWithInternalError();
		}
	}

	private static String formatAlgInfo(MLAlgorithm alg) {

		Set<String> pNames = Tune.filter(alg.getProperties().keySet(), Tune.getConfigNames(alg.getConfigParameters()), new HashSet<>());

		StringBuilder sb = new StringBuilder();
		sb.append(alg.getName());
		Map<String,Object> params = alg.getProperties();
		if (params != null && !params.isEmpty()) {
			sb.append(" |");
			boolean first=true;
			for (String n : pNames) {
				if(!first)
					sb.append(',');
				sb.append(n);
				sb.append('=');
				sb.append(params.get(n));
				first=false;
			}
			sb.append('|');
		}

		return sb.toString();
	}

	private void trainPredictor(TCPClassifier tcpClass, Dataset problem) {
		LOGGER.debug("training tcp class");
		console.print("Setting up TCP Classification predictor using NCM " + 
				tcpClass.getNCM().getName() + " with scorer " + 
				formatAlgInfo(tcpClass.getNCM().getModel()) + ProgressInfoTexts.ELLIPSES, PrintMode.VERBOSE_ON_MATCH);
		console.print("Setting up TCP Classification predictor... ", PrintMode.NORMAL_ON_MATCH);
		tcpClass.train(problem);
		console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
	}

	private void trainPredictor(AggregatedPredictor predictor, Dataset problem, String predictorName, String verboseExtra) {

		int nrModels =  predictor.getStrategy().getNumSamples();

		if ( runOnlySplits != null && 
				! runOnlySplits.isEmpty()) {
			// Normal info 
			console.println("Training %s predictor with split(s) %s out of %d%s:", 
					PrintMode.NORMAL_ON_MATCH,predictorName,runOnlySplits,nrModels,StringUtils.handlePlural(" model", nrModels));
			// Verbose info  
			console.printlnWrapped("Training %s predictor with split(s) %s out of %d%s%s:", 
					PrintMode.VERBOSE_ON_MATCH,predictorName,runOnlySplits,nrModels,StringUtils.handlePlural(" model", nrModels),verboseExtra);
			LOGGER.debug("Running only a specific set of splits");
			for (int split : runOnlySplits) {
				console.print(" - Training model %d/%d... ", PrintMode.NORMAL,split,nrModels);
				predictor.train(problem, split-1); // Split index starts at 0 in java, but 1 at CLI
				console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
				pb.stepProgress();
			}
		} else if (nrModels == 1 && predictor instanceof ConformalPredictor){
			// ICP
			String icpPredName = predictorName.replace("ACP", "ICP");
			// Normal info
			console.println("Training %s predictor:", 
					PrintMode.NORMAL_ON_MATCH, icpPredName);
			// Verbose info
			console.printlnWrapped("Training %s predictor%s:", 
					PrintMode.VERBOSE_ON_MATCH,icpPredName,verboseExtra);
			LOGGER.debug("Training ICP model (a single) split");
			for (int i=0; i<nrModels; i++) {
				console.print(" - Training model... ", PrintMode.NORMAL);
				predictor.train(problem, i);
				console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
				pb.stepProgress();
			}

		} else {
			// Normal info
			console.println("Training %s predictor with %d%s:", 
					PrintMode.NORMAL_ON_MATCH, predictorName, nrModels,StringUtils.handlePlural(" model", nrModels));
			// Verbose info
			console.printlnWrapped("Training %s predictor with %d%s%s:", 
					PrintMode.VERBOSE_ON_MATCH,predictorName,nrModels,StringUtils.handlePlural(" model", nrModels),verboseExtra);
			LOGGER.debug("Training all splits");
			for (int i=0; i<nrModels; i++) {
				console.print(" - Training model %d/%d... ", PrintMode.NORMAL,(i+1),nrModels);
				predictor.train(problem, i);
				console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
				pb.stepProgress();
			}
		}
	}


	private void computePercentiles(ChemPredictor predictor) {
		console.print(OutputNamingSettings.ProgressInfoTexts.COMPUTING_PERCENTILES, PrintMode.NORMAL);

		if ( percentilesArgs.maxNumMolsForPercentiles <= 0) {
			LOGGER.debug("No percentiles will be calculated");
			console.println(OutputNamingSettings.ProgressInfoTexts.SKIPPED_TAG, PrintMode.NORMAL);
			return;
		}

		// Check that we have signatures descriptor - otherwise this is useless
		boolean hasSigDesc = predictor.usesSignaturesDescriptor();

		if (! hasSigDesc) {
			LOGGER.debug("Aborting calculation of percentiles - no signatures descriptor is used");
			console.println(ProgressInfoTexts.SKIPPED_TAG, PrintMode.NORMAL);
			return;
		}

		if ( percentilesArgs.percentilesFile == null) {
			LOGGER.error("Max num percentiles set to: {} but no percentiles file given!", percentilesArgs.maxNumMolsForPercentiles);
			console.failWithArgError("Percentiles set to compute, need to pass the " 
				+ CLIProgramUtils.getParamName(PercentilesMixin.class, "percentilesFile", "PERCENTILES_FILE") 
				+ " argument");
		}

		// Do compute percentiles
		pb.setCurrentTask(PB.COMPUTING_PERCENTILES_PROGRESS);
		LOGGER.debug("computing percentiles from uri: {}", percentilesArgs.percentilesFile.getURI());
		try {
			predictor.computePercentiles(percentilesArgs.percentilesFile.getIterator(), 
					percentilesArgs.maxNumMolsForPercentiles);
		} catch (Exception e) {
			LOGGER.debug("Failed computing percentiles",e);
			console.failWithInternalError("Failed computing percentiles due to: " + e.getMessage());
		}
		console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
		
		 // Only end section if actually done the computation 

	}


	private void saveModel(ChemPredictor predictor) {
		console.print(WordUtils.wrap("Saving model to file: " +  outputSection.modelFile + ProgressInfoTexts.SPACE_ELLIPSES, console.getTextWidth()).trim(), 
				PrintMode.NORMAL);

		predictor.withModelInfo(new ModelInfo( outputSection.modelName, 
				outputSection.modelVersion, 
				outputSection.modelCategory));
		try {
			ModelSerializer.saveModel(predictor, 
					outputSection.modelFile, 
					encryptSection.exclusive.encryptSpec,
					outputSection.dataToMount);
		} catch (Exception e){
			LOGGER.debug("Exception while generating JAR for trained predictor", e);
			console.failWithInternalError("Failed generating Model JAR: " + e.getMessage());
		}
		console.println(' '+ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
	}


}

/*
* Copyright (C) Aros Bio AB.
*
* CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
*
* 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
*
* 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
*/
package com.arosbio.cpsign.app.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.URI;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.openscience.cdk.interfaces.IAtomContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.chem.io.in.CSVChemFileReader;
import com.arosbio.chem.io.in.CSVFile;
import com.arosbio.chem.io.in.ChemFile;
import com.arosbio.chem.io.in.ChemFileIterator;
import com.arosbio.chem.io.in.ChemIOUtils;
import com.arosbio.chem.io.in.ChemIOUtils.ChemFormat;
import com.arosbio.chem.io.in.EarlyLoadingStopException;
import com.arosbio.chem.io.in.FailedRecord;
import com.arosbio.chem.io.in.FailedRecord.Cause;
import com.arosbio.chem.io.in.JSONFile;
import com.arosbio.chem.io.in.MolAndActivityConverter;
import com.arosbio.chem.io.in.ProgressTracker;
import com.arosbio.chem.io.in.SDFile;
import com.arosbio.cheminf.ChemCPClassifier;
import com.arosbio.cheminf.ChemCPRegressor;
import com.arosbio.cheminf.ChemClassifier;
import com.arosbio.cheminf.ChemPredictor;
import com.arosbio.cheminf.ChemVAPClassifier;
import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.data.ChemDataset.DescriptorCalcInfo;
import com.arosbio.cheminf.descriptors.ChemDescriptor;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.FuzzyMatcher;
import com.arosbio.commons.FuzzyMatcher.NoMatchException;
import com.arosbio.commons.StringUtils;
import com.arosbio.commons.mixins.Named;
import com.arosbio.cpsign.app.RunnableCmd;
import com.arosbio.cpsign.app.params.CLIParameters;
import com.arosbio.cpsign.app.params.CLIParameters.ChemOutputType;
import com.arosbio.cpsign.app.params.CLIParameters.TextOutputType;
import com.arosbio.cpsign.app.params.mixins.ClassificationLabelsMixin;
import com.arosbio.cpsign.app.params.mixins.ConsoleVerbosityMixin;
import com.arosbio.cpsign.app.params.mixins.DescriptorsMixin;
import com.arosbio.cpsign.app.params.mixins.InputChemFilesMixin;
import com.arosbio.cpsign.app.params.mixins.ModelingPropertyMixin;
import com.arosbio.cpsign.app.params.mixins.PrecomputedDatasetMixin;
import com.arosbio.cpsign.app.params.mixins.ProgramProgressMixin;
import com.arosbio.cpsign.app.params.mixins.ValidationPointsMixin;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.CLIConsole.VerbosityLvl;
import com.arosbio.cpsign.app.utils.CLIProgressBar.SupportsProgressBar;
import com.arosbio.cpsign.out.CSVResultsWriter;
import com.arosbio.cpsign.out.JSONResultsWriter;
import com.arosbio.cpsign.out.MetricsOutputFormatter;
import com.arosbio.cpsign.out.OutputNamingSettings.ProgressInfoTexts;
import com.arosbio.cpsign.out.PredictionResultsWriter;
import com.arosbio.cpsign.out.SDFResultsWriter;
import com.arosbio.cpsign.out.SplittedJSONResultsWriter;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.DataUtils.DataType;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.RecordType;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.MissingValueFeature;
import com.arosbio.data.NamedLabels;
import com.arosbio.data.transform.ColumnTransformer;
import com.arosbio.data.transform.Transformer;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.BufferedFileWriter;
import com.arosbio.io.ForcedSystemOutWriter;
import com.arosbio.io.IOUtils;
import com.arosbio.io.UriUtils;
import com.arosbio.ml.cp.ConformalClassifier;
import com.arosbio.ml.cp.ConformalPredictor;
import com.arosbio.ml.cp.ConformalRegressor;
import com.arosbio.ml.cp.acp.ACP;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.tcp.TCP;
import com.arosbio.ml.cp.tcp.TCPClassifier;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.io.ModelIO.ModelType;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.MetricFactory;
import com.arosbio.ml.metrics.classification.ClassifierMetric;
import com.arosbio.ml.metrics.classification.LabelDependent;
import com.arosbio.ml.metrics.classification.PointClassifierMetric;
import com.arosbio.ml.metrics.classification.ROC_AUC;
import com.arosbio.ml.metrics.cp.CPAccuracy;
import com.arosbio.ml.metrics.cp.CPMetric;
import com.arosbio.ml.metrics.cp.ConfidenceDependentMetric;
import com.arosbio.ml.metrics.cp.classification.ProportionMultiLabelPredictions;
import com.arosbio.ml.metrics.cp.classification.ProportionSingleLabelPredictions;
import com.arosbio.ml.metrics.cp.regression.CIWidthBasedMetric;
import com.arosbio.ml.metrics.cp.regression.MeanPredictionIntervalWidth;
import com.arosbio.ml.metrics.cp.regression.MedianPredictionIntervalWidth;
import com.arosbio.ml.metrics.plots.PlotMetric;
import com.arosbio.ml.metrics.regression.PointPredictionMetric;
import com.arosbio.ml.metrics.regression.RegressionMetric;
import com.arosbio.ml.metrics.vap.VAPCalibrationPlotBuilder;
import com.arosbio.ml.vap.avap.AVAPClassifier;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

public class CLIProgramUtils {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CLIProgramUtils.class);
	
	public static final String INVALID_PERCENTILES_ERROR = "Percentiles either not computed or not having valid values - gradients cannot be computed and prediction images cannot be created - please either train a new model, remove arguments for generating images or remove the --calc-gradient parameter";
	
	@SuppressWarnings("null")
	private static void configConsole(Object program) {
		
		Optional<ConsoleVerbosityMixin> consArgs = null;
		
		try {
			consArgs = getFieldByType(program, ConsoleVerbosityMixin.class);
		} catch (Exception e) {
			LOGGER.error("could not get argument, failed with exception", e);
		}
		
		// Config the console
		if (consArgs.isPresent() && consArgs.get() != null) {
			consArgs.get().configConsole();
		}
		
		if (program instanceof Named)
		CLIConsole.getInstance().setRunningCMD(((Named) program).getName());
	}
	
	
	/**
	* Performs a full configuration of a {@link RunnableCmd}, dynamically depending on the
	* Mixin classes using java reflection 
	* <ul>
	* <li>logging</li>
	* <li>console</li>
	* <li>configuring ProgramTimer (if applicable)</li>
	* <li>configuring ProgressBar (if applicable)</li>
	* <li>printing start of program-lines</li>
	* </ul>
	* 
	* @param program the program to run and configure
	*/
	public static void doFullProgramConfig(RunnableCmd program) {
		LOGGER.debug("Doing fullProgramConfig for program {}", program.getName());
		CLIProgramUtils.configConsole(program);
		LOGGER.debug("Running program {}", program.getName());
		
		CLIConsole console = CLIConsole.getInstance();
		
		// Check progress bar / timer
		try {
			Optional<ProgramProgressMixin> ppg = getFieldByType(program, ProgramProgressMixin.class);
			if (ppg != null && ppg.isPresent()) {
				// If time
				if (ppg.get().time) {
					Optional<Field> timerOp = getFieldFromObject(program, ProgramTimer.class);
					if (timerOp.isPresent()) {
						Field f = timerOp.get();
						f.setAccessible(true);
						ProgramTimer pt = new ProgramTimer(true, console);
						f.set(program, pt);
					} else {
						LOGGER.error(
						"Failed setting ProgramTimer when ProgramProgressMixin allowed to set --timer option, for program {}",
						program);
					}
				}
				
				// If progress bar
				if (ppg.get().progress) {
					if (program instanceof SupportsProgressBar) {
						((SupportsProgressBar) program).setProgressBar(new CLIProgressBarPrinter(program.getName(),
						((SupportsProgressBar) program).getNumSteps(), true));
					} else {
						LOGGER.error(
						"Failed setting ProgressBar when ProgramProgressMixin allowed to set --progress-bar option, for program {}"
						, program);
					}
				}
			}
		} catch (Exception e) {
			LOGGER.error("Failed getting ProgramProgressMixin from program command {} - no progress will be set", program,
			e);
		}
		
		
		// Start of program!
		printStartOfProgram(console);
		
		LOGGER.debug("Finished method doFullProgramConfig");
		
	}
	
	public static void printStartOfProgram(CLIConsole cons) {
		StringBuilder sb = new StringBuilder();
		sb.append("%n");
		StringUtils.center(sb, "-= CPSign - " + cons.getRunningCmd().toUpperCase()+" =-", cons.getTextWidth());
		sb.append("%n");
		cons.println(sb.toString(), PrintMode.NORMAL);
	}
	
	private static <T> Optional<Field> getFieldFromObject(Object cmd, Class<T> cls) {
		if (cmd == null)
		return Optional.empty();
		
		for (Field f : cmd.getClass().getDeclaredFields()) {
			if (cls.isAssignableFrom(f.getType())) {
				return Optional.of(f);
			}
			if (f.getAnnotation(Mixin.class) != null || f.getAnnotation(ArgGroup.class) != null) {
				Optional<Field> tmp = getFieldFromObject(f, cls);
				if (tmp.isPresent())
				return tmp;
			}
		}
		return Optional.empty();
	}
	
	public static String getParamName(Object optionClazz, String fieldName, String defaultName) {
		return getParamName(optionClazz.getClass(), fieldName, defaultName);
	}
	
	public static String getParamName(Class<?> optionClazz, String fieldName, String defaultName)
	throws IllegalArgumentException {
		Field theField = getField(optionClazz, fieldName);
		if (theField == null)
		return defaultName;
		
		try {
			Option paramAnnotation = theField.getAnnotation(Option.class);
			return getPrefName(paramAnnotation.names());
		} catch (Exception e) {
		}
		return defaultName;
		
	}
	
	public static String getPrefName(String[] names) {
		if (names==null)
		return null;
		String prefName = "";
		for (String name : names) {
			if (name.length() > prefName.length())
			prefName = name;
			if (prefName.length()>3)
			return prefName;
		}
		return prefName;
	}
	public static String getPrefName(List<String> names) {
		if (names==null)
		return null;
		String prefName = "";
		for (String name : names) {
			if (name.length() > prefName.length())
			prefName = name;
		}
		return prefName;
	}
	
	private static Field getField(Class<?> type, String fieldName) {
		return getField(type, fieldName, true, true);
	}
	
	private static Field getField(Class<?> type, String fieldName, boolean followDelegate, boolean searchSuperClasses) {
		if (type == null)
		return null;
		
		// Try to get the field from this 'level'
		try {
			return type.getDeclaredField(fieldName);
		} catch (Exception e) {
		}
		
		Field theField = null;
		
		// Try to go to super-class
		if (searchSuperClasses) {
			theField = getField(type.getSuperclass(), fieldName, true, true);
			if (theField != null)
			return theField;
		}
		
		// If failed - try to get from Mixin classes and ArgGroup
		if (followDelegate) {
			for (Field f : type.getDeclaredFields()) {
				if (f.getAnnotation(picocli.CommandLine.Mixin.class) != null
				|| f.getAnnotation(ArgGroup.class) != null) {
					
					Field tmp = getField(f.getType(), fieldName, true, true);
					if (tmp != null)
					return tmp;
				}
			}
			
		}
		
		return null;
	}
	
	
	/**
	* Get a Field from an object, given the type of the field. The following things can happen;
	* <ul>
	* <li>If the Field exists and is not <code>null</code>: the field/object is returned</li>
	* <li>If the Field exists, but is <code>null</code>: The newInstance is used of the class T (returned is then either a new T instance, or Optional.empty if newInstance method fails</li>
	* <li>If the Field does not exist: Optional.empty is returned</li>
	* </ul> 
	* 
	* @param obj the running program or any argument object
	* @param clazz the argument class we should look for
	* @param <T> the type of the class
	* @return and Optional in case {@code clazz} was found
	*/
	@SuppressWarnings("unchecked")
	public static <T extends Object> Optional<T> getFieldByType(Object obj, Class<T> clazz) {
		if (obj == null) {
			return Optional.empty();
		}
		
		if (clazz.isAssignableFrom(obj.getClass())) { 
			return Optional.of((T) obj);
		}
		
		for(Field f : obj.getClass().getDeclaredFields()) {	
			try {
				if (clazz.isAssignableFrom(f.getType())) {
					f.setAccessible(true);
					T t = (T) f.get(obj);
					if (t == null) {
						try {
							return Optional.of(clazz.getDeclaredConstructor().newInstance());
						} catch (Exception e) {return Optional.empty(); }
					}
					return Optional.of((T) f.get(obj));
				}
				
				if (f.getAnnotation(Mixin.class) != null || f.getAnnotation(ArgGroup.class) != null) {
					Optional<T> tmp = getFieldByType(f.get(obj), clazz);
					if (tmp.isPresent()) {
						return tmp;
					}
				}
			} catch (Exception e) {
				LOGGER.trace("Could not handle field {} - trying the next", f.getName());
			}
		}
		
		
		return Optional.empty();
	}
	
	public static void setupOutputModelFile(File explicitFile, Named program) throws IOException {
		setupOutputModelFile(explicitFile, program, "model");
	}
	
	@SuppressWarnings("null")
	public static void setupOutputModelFile(File explicitFile, Named program, String fileTypeName) throws IOException {
		
		LOGGER.debug("Setting up the output model file");
		CLIConsole cons = CLIConsole.getInstance();
		
		if (explicitFile == null)
		cons.failWithArgError("No output path was given for where the generated %s should be saved",
		fileTypeName);
		
		if (explicitFile.isDirectory())
		cons.failWithArgError("The specified path for output %s exists and is a directory: %s",
		fileTypeName, explicitFile.getAbsolutePath());
		
		if (explicitFile.exists() && explicitFile.length() > 0)
		cons.failWithArgError("The specified path for output %s already exists",
		fileTypeName);
		
		if (explicitFile.getName().length() < 1)
		cons.failWithArgError("The specified path for output %s has no file-name: %s",
		fileTypeName,explicitFile.getAbsolutePath());
		
		UriUtils.createParentOfFile(explicitFile);
		
		// Should now be possible to create the output model!
		
	}
	
	public static String getModelNameFromFileName(File modelOutputFile) {
		LOGGER.debug("Generating a default model-name based on output file: {}", 
		modelOutputFile);
		String name = modelOutputFile.getName();
		int index = name.lastIndexOf('.');
		if (index > 0)
		return name.substring(0, index);
		else
		return name;
	}
	
	public static void closeQuietly(CLIProgressBar pb) {
		try {
			pb.finish();
		} catch (Exception e) {}
	}
	
	public static void verifyClassLabelsGivenWhenNeeded(InputChemFilesMixin trainData, boolean precompModelGiven,
	boolean isClassification, CLIConsole console) {
		// If there's a precomputed model given - we don't need the labels!
		if (precompModelGiven) {
			return;
		}
		
		if (!isClassification) {
			if (trainData.labelsOpt.labels != null)
			console.failWithArgError(
			"Parameter " + CLIProgramUtils.getParamName(trainData, "labels", "CLASS_LABELS")
			+ " cannot be given in regression mode");
			return;
		}
		
		// Else we require them
		if (trainData.labelsOpt.labels == null)
		console.failDueToMissingParameters(new MissingParam("labels", "CLASS_LABELS", InputChemFilesMixin.class));
		if (trainData.labelsOpt.labels.size() < 2)
		console.failWithArgError("Parameter " + CLIProgramUtils.getParamName(trainData, "labels", "CLASS_LABELS")
		+ " must be at least of length 2");
		
	}
	
	public static void loadPrecomputedData(ChemPredictor predictor, 
	PrecomputedDatasetMixin data, EncryptionSpecification spec, CLIConsole console) {
		
		ChemDataset loaded = loadPrecomputedData(data, spec, console, false);
		
		if (predictor instanceof ChemClassifier && loaded.getTextualLabels() == null){
			LOGGER.debug("Training a classifier, but precomputed data set was not a classifier dataset: {}", loaded.toString());
			console.failWithArgError("File sent to --data-set was not a precomputed classification data set");
		}
		
		predictor.setDataset(loaded);
		printInfoAboutDataset(loaded, console);
		
	}
	
	public static ChemDataset loadPrecomputedData(PrecomputedDatasetMixin data, EncryptionSpecification spec, CLIConsole console, boolean printInfo) {
		
		// Verify can download the data as local file
		data.getAsFile();
		
		console.println(ProgressInfoTexts.LOADING_PRECOMP_DATA, PrintMode.NORMAL);
		try {
			ChemDataset loaded =  ModelSerializer.loadDataset(data.getAsFile().toURI(), spec);
			if (printInfo){
				printInfoAboutDataset(loaded, console);
			}
			return loaded;
		} catch (Exception e) {
			LOGGER.debug("Failed loading input that should be a precomputed data set",e);
			ModelType t = null;
			try {
				t = ModelSerializer.getType(data.getAsFile().toURI());
				LOGGER.debug("found type of input to be: {}", t);
			} catch (Exception | Error e2){
				LOGGER.debug("Input given as precomputed data set was not a cpsign model of any type");
			}
			if (t != null){
				console.failWithArgError("Failed loading precomputed data set due to: input not precomputed data set but a " +t + " model");
				
			} else {
				console.failWithArgError("Failed loading precomputed data set: input of unrecognized type");
			}
		}
		return null; // This should never happen, failing if not successfully loaded data
	}
	
	public static void printInfoAboutDataset(ChemDataset sp, CLIConsole cons) {
		StringBuilder sb = new StringBuilder("Loaded precomputed data set with ");
		SubSet ds = sp.getDataset();
		sb.append(ds.size());
		sb.append(" records and ");
		sb.append(sp.getNumAttributes());
		sb.append(" features.");
		if (sp.getCalibrationExclusiveDataset().size()>0) {
			LOGGER.debug("SubSet contains "+sp.getCalibrationExclusiveDataset().size()+" calibration-exclusive records");
			sb.append(" SubSet also includes ");
			sb.append(sp.getCalibrationExclusiveDataset().size());
			sb.append(" records marked exclusively for calibration.");
		}
		if (sp.getModelingExclusiveDataset().size()>0) {
			LOGGER.debug("SubSet contains "+sp.getModelingExclusiveDataset().size()+" modeling-exclusive records");
			sb.append(" SubSet also includes ");
			sb.append(sp.getModelingExclusiveDataset().size());
			sb.append(" records marked exclusively for scoring-model training.");
		}
		if (sp.getTextualLabels() != null && !sp.getTextualLabels().isEmpty()) {
			LOGGER.debug("labels=" + sp.getLabels());
			// classification - print info about how many of each label
			sb.append(" Occurrences of each class: ");
			Map<Double, Integer> dsFreq = ds.getLabelFrequencies();
			NamedLabels nl = sp.getTextualLabels();
			
			int i = 0;
			for (Map.Entry<Double, Integer> label : dsFreq.entrySet()) {
				sb.append("'");
				sb.append(nl.getLabel(label.getKey().intValue()));
				sb.append("'=");
				sb.append(label.getValue());
				
				if (i < dsFreq.size() - 1)
				sb.append(", ");
				i++;
			}
			sb.append('.');
		}
		
		cons.printlnWrapped(sb.toString(), PrintMode.NORMAL);
	}
	
	/**
	* Wrapper method of {@link #loadData(ChemPredictor, URI, ChemFile, ChemFile, ChemFile, String, List, EncryptionSpecification, RunnableCmd, CLIConsole, boolean, int, int)},
	* simply pulling out all required arguments
	* @param predictor
	* @param inputSection
	* @param spec
	* @param train
	* @param console
	* @param listFailed
	*/
	public static void loadData(ChemPredictor predictor, InputChemFilesMixin inputSection,
	EncryptionSpecification spec, RunnableCmd train, CLIConsole console, boolean listFailed) {
		
		loadData(predictor, null, inputSection.trainFile,
		inputSection.properTrainExclusiveFile, inputSection.calibrationExclusiveTrainFile,
		inputSection.endpointOpt.endpoint, inputSection.labelsOpt.labels, spec, train,
		console, listFailed, inputSection.minHAC, inputSection.maxFailuresAllowed);
	}
	
	public static void loadData(ChemPredictor predictor, URI precompModel, ChemFile trainFile,
	ChemFile modelExclusiveTrainFile, ChemFile calibExclusiveTrainFile, String endpoint, List<String> labels,
	EncryptionSpecification spec, RunnableCmd program, CLIConsole console, boolean listFailed, int minHAC,
	int maxNumAllowedFailures) {
		
		CLIProgressBar pb = getPB(program); 
		
		if (labels != null)
		LOGGER.debug("Loading data using labels: {}", labels);
		
		// PRECOMPUTED DATA
		if (precompModel != null) {
			try {
				console.println("Loading precomputed data set...", PrintMode.NORMAL);
				pb.addAdditionalStep();
				
				ChemDataset loadedData = ModelSerializer.loadDataset(precompModel, spec);
				
				if (predictor instanceof ChemClassifier && loadedData.getTextualLabels()==null)
				console.failWithArgError(
				"Precomputed data was of regression type - cannot be used for classification");
				else if (!(predictor instanceof ChemClassifier)
				&& (loadedData.getTextualLabels() != null))
				console.failWithArgError(
				"Precomputed data was of classification type - cannot be used for regression");
				
				predictor.setDataset(loadedData);
				
				if (predictor.getDataset().getNumRecords() <= 0)
				console.failWithArgError("No precomputed data set could be loaded");
				
			} catch (IllegalArgumentException e) {
				LOGGER.debug("Exception loading assumed precomputed data", e);
				console.failWithArgError(e.getMessage());
			} catch (InvalidKeyException e) {
				LOGGER.debug("Invalid key for reading precomputed data", e);
				console.failWithArgError(e.getMessage());
			} catch (Exception e) {
				LOGGER.debug("Failed with Exception", e);
				console.failWithArgError(e.getMessage());
			}
			console.printlnWrapped("Loaded precomputed data set with " + predictor.getDataset().getNumRecords()
			+ " records and " + predictor.getDataset().getNumAttributes() + " features.", PrintMode.NORMAL);
			
			pb.stepProgress();
		}
		
		// CHEMICAL DATA
		else if (trainFile != null || modelExclusiveTrainFile != null || calibExclusiveTrainFile != null) {
			loadData(predictor.getDataset(), (predictor instanceof ChemClassifier), trainFile,
			modelExclusiveTrainFile, calibExclusiveTrainFile, endpoint, labels, program, console, listFailed,
			minHAC, maxNumAllowedFailures);
		} else {
			// should never happen, should be handled before calling this method
			throw new IllegalArgumentException("Neither training data or precomputed model was given");
		}
	}
	
	public static void loadData(ChemDataset sp, boolean isClassification, InputChemFilesMixin input, Object program,
	CLIConsole console, boolean listFailed) {
		
		loadData(sp, isClassification, input.trainFile, input.properTrainExclusiveFile,
		input.calibrationExclusiveTrainFile, input.endpointOpt.endpoint, input.labelsOpt.labels, program,
		console, listFailed, input.minHAC, input.maxFailuresAllowed);
	}
	
	/**
	* Main method for precomputing data from chemical files 
	* @param sp
	* @param isClassification
	* @param trainFile
	* @param modelExclusiveTrainFile
	* @param calibExclusiveTrainFile
	* @param endpoint
	* @param labels
	* @param program
	* @param console
	* @param listFailed
	* @param minHAC
	* @param maxNumAllowedFailures
	*
	*/
	public static void loadData(ChemDataset sp, boolean isClassification, ChemFile trainFile,
	ChemFile modelExclusiveTrainFile, ChemFile calibExclusiveTrainFile, String endpoint, List<String> labels,
	Object program, 
	CLIConsole console, boolean listFailed, int minHAC, final int maxNumAllowedFailures) {
		
		CLIProgressBar pb = getPB(program);
		int numDatasetsUsed = 0;
		
		if (endpoint == null || endpoint.isEmpty()) {
			LOGGER.debug("No endpoint given though training data file was given - failing execution");
			console.failWithArgError(
			"Missing required parameter " + getParamName(new ModelingPropertyMixin(), "endpoint", "PROPERTY")
			+ ": needs to be specified with chemical data");
		}
		
		if (isClassification && (labels == null || labels.isEmpty())) {
			LOGGER.debug("No labels supplied even though classification training data was given - failing");
			console.failWithArgError(
			"Missing required parameter " + getParamName(new ClassificationLabelsMixin(), "labels", "LABELS")
			+ ": needed when running classification");
		}
		
		sp.setMinHAC(minHAC);
		boolean usingEarlyStopping = maxNumAllowedFailures >= 0;
		int numAllowedFailsUpdated = maxNumAllowedFailures;
		
		
		// Initialize the descriptors
		sp.initializeDescriptors();
		LOGGER.debug("Initialized descriptors");
		List<FailedRecord> allFailedRecords = new ArrayList<>();
		ChemFile currentFile = null;
		
		try {
			// Parse molecules
			if (trainFile != null) {
				currentFile = trainFile;
				console.println("Reading train file and calculating descriptors...", PrintMode.NORMAL);
				pb.addAdditionalStep();
				sp.setProgressTracker(usingEarlyStopping ? ProgressTracker.createStopAfter(numAllowedFailsUpdated) : ProgressTracker.createNoEarlyStopping());
				loadData(sp, trainFile, endpoint, labels, RecordType.NORMAL, console, listFailed);
				// update the remaining number of allowed failures
				numAllowedFailsUpdated -= Math.max(0,sp.getProgressTracker().getNumFailures());
				numDatasetsUsed++;
				if (sp.getProgressTracker().getNumFailures()>0){
					allFailedRecords.addAll(sp.getProgressTracker().getFailures());
				}
				pb.stepProgress();
			}
			
			// Calibration Exclusive dataset
			if (calibExclusiveTrainFile != null) {
				currentFile = calibExclusiveTrainFile;
				pb.addAdditionalStep();
				console.println("Reading calibration exclusive train file and calculating descriptors...",
				PrintMode.NORMAL);
				sp.setProgressTracker(usingEarlyStopping ? ProgressTracker.createStopAfter(numAllowedFailsUpdated) : ProgressTracker.createNoEarlyStopping());
				loadData(sp, calibExclusiveTrainFile, endpoint, labels, RecordType.CALIBRATION_EXCLUSIVE, console,
				listFailed);
				// update the remaining number of allowed failures
				numAllowedFailsUpdated -= Math.max(0,sp.getProgressTracker().getNumFailures());
				numDatasetsUsed++;
				if (sp.getProgressTracker().getNumFailures()>0){
					allFailedRecords.addAll(sp.getProgressTracker().getFailures());
				}
				pb.stepProgress();
			}
			
			// Modeling Exclusive dataset
			if (modelExclusiveTrainFile != null) {
				currentFile = modelExclusiveTrainFile;
				pb.addAdditionalStep();
				console.println("Reading modeling exclusive train file and calculating descriptors...", PrintMode.NORMAL);
				sp.setProgressTracker(usingEarlyStopping ? ProgressTracker.createStopAfter(numAllowedFailsUpdated) : ProgressTracker.createNoEarlyStopping());
				loadData(sp, modelExclusiveTrainFile, endpoint, labels, RecordType.MODELING_EXCLUSIVE, console, listFailed);
				
				numDatasetsUsed++;
				if (sp.getProgressTracker().getNumFailures()>0){
					allFailedRecords.addAll(sp.getProgressTracker().getFailures());
				}
				pb.stepProgress();
			}
		} catch (Exception e){
			allFailedRecords.addAll(sp.getProgressTracker().getFailures()); // Add failures from the current file
			LOGGER.debug("Failed parsing in chemical input data, will try to compile a good error message");
			failWithBadUserInputFile(console, sp.getNumRecords(), currentFile, allFailedRecords, sp, endpoint, labels, maxNumAllowedFailures);
		}
		
		if (labels != null && !labels.isEmpty()) {
			Map<Double, Integer> labelFreq = sp.getLabelFrequencies();
			List<String> nonFoundLabels = new ArrayList<>();
			for (String label : labels) {
				double labAsNumeric = (double) sp.getTextualLabels().getValue(label);
				if (!labelFreq.containsKey(labAsNumeric) || labelFreq.get(labAsNumeric) <= 0) {
					nonFoundLabels.add(label);
				}
			}
			// Fail in case some labels were not found in the input data (invalid input argument from user)
			if (!nonFoundLabels.isEmpty()) {
				console.failWithArgError("Could not detect molecules with label(s): %s%nwas the correct labels given?", 
				String.join(", ", nonFoundLabels));
			}
		}
		
		// If more than one dataset used - write total information
		if (numDatasetsUsed > 1) {
			StringBuilder sb = new StringBuilder();
			sb.append("Total number of molecules=");
			sb.append(sp.getNumRecords());
			int numSigs = getNumSignaturesPreTransformations(sp);
			if (numSigs > 0) {
				sb.append(", total number of signatures=");
				sb.append(numSigs);
			}
			int numAdditional = getNumNonSignaturesDescriptorsPreTrans(sp);
			if (numAdditional > 0) {
				sb.append(", total number of additional features=");
				sb.append(numAdditional);
			}
			
			console.printlnWrapped(sb.toString(), PrintMode.NORMAL);
		}
		
	}
	
	private static void loadData(ChemDataset problem, ChemFile file, String endpoint, List<String> labels,
	RecordType type, CLIConsole console, boolean listFailed) throws EarlyLoadingStopException {
		
		ChemFileIterator iterator = null;
		MolAndActivityConverter reader = null;
		int currentNumSignatures = getNumSignaturesPreTransformations(problem);
		
		// Set up file reader
		try {
			iterator = file.getIterator(problem.getProgressTracker());
		} catch (Exception e){
			// This is then likely to be issues with the file itself
			LOGGER.debug("failed reading from file, probably incorrectly given",e);
			throw new EarlyLoadingStopException("Could not parse input file: " + e.getMessage(), problem.getProgressTracker().getFailures());
			// failWithBadUserInputFile(console, problem.getNumRecords(), file, problem.getProgressTracker().getFailures(), endpoint, labels, problem.getProgressTracker().getMaxAllowedFailures());
			// console.failWithArgError("Could not parse input file: " + e.getMessage());
		}
		
		// Set up the converter
		try {
			
			if (labels != null && ! labels.isEmpty()){
				reader = MolAndActivityConverter.Builder.classificationConverter(iterator, endpoint, new NamedLabels(labels)).progressTracker(problem.getProgressTracker()).build();
			} else {
				reader = MolAndActivityConverter.Builder.regressionConverter(iterator, endpoint).progressTracker(problem.getProgressTracker()).build();
			}
			// pre-fetch first record to check that parameters are OK
			reader.initialize();
			
		} catch (EarlyLoadingStopException e){
			// Pass along
			throw e;
		} catch (Exception e) {
			LOGGER.debug("Could not initiate the MolAndActivityConverter for data type {}",type);
			LOGGER.error("failed with exception", e);
			// Wrap in early loading except
			throw new EarlyLoadingStopException("failed loading chemical file: " + e.getMessage(), problem.getProgressTracker().getFailures());
		} 
		
		// Do the descriptor calculations 
		DescriptorCalcInfo info = null;
		try {
			info = problem.add(reader, type);
		} catch (EarlyLoadingStopException e){
			LOGGER.debug("Stopped due to early loading stop",e);
			throw e;
		} catch (Exception e){
			LOGGER.debug("Stopped due to generic exception",e);
			throw new EarlyLoadingStopException(e.getMessage(), problem.getProgressTracker().getFailures());
		}
		
		// Write out info in the end
		StringBuilder extraInfoBuilder = new StringBuilder();
		
		if (listFailed) {
			
			appendFailedMolsInfo(extraInfoBuilder, info.getFailedRecords());
			
			if (extraInfoBuilder.length() > 0)
			extraInfoBuilder.append("%n");
			
		} else {
			// Count the number of failures
			int numFailed = info.getFailedRecords().size(); 
			if (numFailed > 0) {
				summarizeFailedRecords(extraInfoBuilder, info.getFailedRecords());
			}
		}
		
		IOUtils.closeQuietly(iterator);
		IOUtils.closeQuietly(reader);
		
		// Write data about the signatures generation
		console.printlnWrapped(
		getLoadedMoleculesInfo(problem.getDataset(type), problem, currentNumSignatures) + extraInfoBuilder.toString(),
		PrintMode.NORMAL);
		
	}

	private static Map<Cause,Integer> getCounts(Collection<FailedRecord> records){
		Map<Cause,Integer> counts = new HashMap<>();
		if (records.isEmpty())
			return counts;
		List<FailedRecord> sortedUnique = CollectionUtils.getUniqueAndSorted(records);
		for (FailedRecord r : sortedUnique){
			counts.put(r.getCause(), counts.getOrDefault(r.getCause(), 0)+1);
		}
		return counts;
	}
	
	private static void summarizeFailedRecords(StringBuilder sb, Collection<FailedRecord> records){
		List<FailedRecord> sortedUnique = CollectionUtils.getUniqueAndSorted(records);
		
		int numInvalidRec=0, numLowHAC=0, numMissingOrInvalidProperty=0, numUnknown=0, numDescriptorCalcError=0;
		for (FailedRecord r : sortedUnique){
			switch (r.getCause()){
				case DESCRIPTOR_CALC_ERROR:
					numDescriptorCalcError++;
					break;
				case LOW_HAC:
					numLowHAC++;
					break;
				case INVALID_PROPERTY:
				case MISSING_PROPERTY:
					numMissingOrInvalidProperty++;
					break;
				case INVALID_RECORD:
				case INVALID_STRUCTURE:
				case MISSING_STRUCTURE:
					numInvalidRec++;
					break;
				default:
					numUnknown++;
					break;
			}
		}
		
		if (numInvalidRec>0){
			sb.append("Failed ").append(numInvalidRec).append(" record(s) due to being invalid%n");
		}
		if (numMissingOrInvalidProperty>0){
			sb.append("Failed ").append(numInvalidRec).append(" record(s) due to missing/invalid endpoint activity%n");
		}
		if (numDescriptorCalcError>0){
			sb.append("Failed ").append(numInvalidRec).append(" record(s) during descriptor calculation%n");
		}
		if (numLowHAC>0){
			sb.append("Failed ").append(numInvalidRec).append(" record(s) due to Heavy Atom Count threshold%n");
		}
		if (numUnknown>0){
			sb.append("Failed ").append(numInvalidRec).append(" record(s) due to unknown error%n");
		}
		
	}
	
	public static void appendFailedMolsInfo(StringBuilder sb, Collection<FailedRecord> records) {
		List<FailedRecord> sortedUnique = CollectionUtils.getUniqueAndSorted(records);
		for (FailedRecord r : sortedUnique) {
			sb.append("%nRecord ");
			sb.append(r.getIndex());
			if (r.hasID()) {
				sb.append(" {").append(r.getID()).append('}');
			}
			// the reason (or cause)
			if (r.hasReason()) {
				sb.append(": ").append(r.getReason());
			} else {
				sb.append(": ").append(r.getCause().getMessage());
			}
		}
	}
	
	protected static String getLoadedMoleculesInfo(SubSet ds, ChemDataset sp, int prevNumSigs) {
		StringBuilder sb = new StringBuilder("Successfully parsed ");
		sb.append(ds.size());
		sb.append(" molecules.");
		if (sp.getTextualLabels() != null && !sp.getTextualLabels().isEmpty()) {
			LOGGER.debug("labels=" + sp.getLabels());
			// classification - print info about how many of each label
			sb.append(" Detected labels: ");
			Map<Double, Integer> dsFreq = ds.getLabelFrequencies();
			NamedLabels nl = sp.getTextualLabels();
			
			int i = 0;
			for (Map.Entry<Double, Integer> label : dsFreq.entrySet()) {
				sb.append("'");
				sb.append(nl.getLabel(label.getKey().intValue()));
				sb.append("'=");
				sb.append(label.getValue());
				
				if (i < dsFreq.size() - 1)
				sb.append(", ");
				i++;
			}
			sb.append('.');
		}
		int newSignatures = getNumSignaturesPreTransformations(sp) - prevNumSigs;
		int numAdditional = getNumNonSignaturesDescriptorsPreTrans(sp);
		if (newSignatures > 0)
		sb.append(" Generated " + newSignatures + " new signatures.");
		if (numAdditional > 0)
		sb.append(" Calculated/loaded " + numAdditional + " non-signatures descriptor features.");
		return sb.toString();
	}
	
	public static int getNumSignaturesPreTransformations(ChemDataset problem) {
		for (ChemDescriptor d : problem.getDescriptors()) {
			if (d instanceof SignaturesDescriptor) {
				return ((SignaturesDescriptor) d).getNumSignatures();
			}
		}
		return 0;
	}
	
	protected static int getNumNonSignaturesDescriptorsPreTrans(ChemDataset problem) {
		int numFeats = 0;
		for (ChemDescriptor d : problem.getDescriptors()) {
			if (!(d instanceof SignaturesDescriptor)) {
				numFeats += d.getLength();
			}
		}
		return numFeats;
	}
	
	public static PredictionResultsWriter setupResultsOutputter(RunnableCmd program, ChemOutputType outputFormat,
	File outputFile, ChemFile predictFile, boolean printInChI, boolean compress) {
		
		BufferedWriter writer = null;
		
		try {
			if (outputFile != null)
			writer = (compress ? BufferedFileWriter.getCompressedFileWriter(outputFile)
			: BufferedFileWriter.getFileWriter(outputFile));
			else
			writer = new ForcedSystemOutWriter(compress);
		} catch (IOException e) {
			LOGGER.debug("Failed setting up the writer to file/stream", e);
			CLIConsole.getInstance().failWithInternalError(
			"Could not initialize the results-printer");
		}
		
		PredictionResultsWriter resW = null;
		switch (outputFormat) {
			case CSV:
			resW = new CSVResultsWriter(predictFile, writer, printInChI, ',');
			break;
			case SPLITTED_JSON:
			resW = new SplittedJSONResultsWriter(writer, printInChI);
			break;
			case SDF_V2000:
			resW = new SDFResultsWriter(writer, false, printInChI);
			break;
			case SDF_V3000:
			resW = new SDFResultsWriter(writer, true, printInChI);
			break;
			case TSV:
			resW = new CSVResultsWriter(predictFile, writer, printInChI, '\t');
			break;
			case JSON:
			default:
			resW = new JSONResultsWriter(writer, printInChI);
			break;
		}
		// Register a shutdown hook so we finish the thing if needed
		Runtime.getRuntime().addShutdownHook(new ShutdownResultWriter(resW));
		
		return resW;
		
	}
	
	private static class ShutdownResultWriter extends Thread {
		private PredictionResultsWriter writer;
		
		public ShutdownResultWriter(PredictionResultsWriter writer) {
			this.writer = writer;
		}
		
		public void run() {
			if (writer != null) {
				try {
					writer.close();
				} catch (Exception e) {}
			}
		}
	}
	
	public static void printInfoAboutLoadedModel(ChemPredictor sp, CLIConsole console) {
		StringBuilder sb = new StringBuilder();
		sb.append("Loaded ");
		if (sp instanceof ChemVAPClassifier) {
			sb.append("a Venn-ABERS classification predictor with ");
			sb.append(((ChemVAPClassifier) sp).getPredictor().getNumTrainedPredictors());
			sb.append(" aggregated models. Model has been trained from ");
			sb.append(sp.getPredictor().getNumObservationsUsed());
			sb.append(" training examples.");
		} else if (sp instanceof ChemCPRegressor) {
			sb.append("an ACP regression predictor with ");
			sb.append(((ACPRegressor) ((ChemCPRegressor) sp).getPredictor()).getNumTrainedPredictors());
			sb.append(" aggregated models. Model has been trained from ");
			sb.append(sp.getPredictor().getNumObservationsUsed());
			sb.append(" training examples.");
		} else if (sp instanceof ChemCPClassifier) {
			if (sp.getPredictor() instanceof TCPClassifier) {
				// TCP
				sb.append("a TCP classification predictor, using ");
				sb.append(sp.getPredictor().getNumObservationsUsed());
				sb.append(" training examples.");
			} else if (sp.getPredictor() instanceof ACPClassifier) {
				// ACP
				sb.append("an ACP classification predictor with ");
				sb.append(((ACPClassifier) sp.getPredictor()).getNumTrainedPredictors());
				sb.append(" aggregated models. Model has been trained from ");
				sb.append(sp.getPredictor().getNumObservationsUsed());
				sb.append(" training examples.");
			}
		}
		
		// Endpoint
		String predEndpoint = sp.getProperty(); 
		if (predEndpoint != null) {
			sb.append(" The model endpoint is \'");
			sb.append(predEndpoint);
			sb.append("\'.");
		}
		
		// Class labels
		if (sp instanceof ChemClassifier) {
			ChemClassifier spC = (ChemClassifier) sp;
			int numLabels = spC.getLabelsSet().size();
			sb.append(" Class labels are ");
			
			int labelInd = 1;
			for (String label : ((ChemClassifier) sp).getLabelsSet()) {
				sb.append('\'');
				sb.append(label);
				sb.append('\'');
				labelInd++;
				if (labelInd == numLabels)
				sb.append(" and ");
				else if (labelInd < numLabels)
				sb.append(", ");
			}
			sb.append('.');
		}
		
		console.printlnWrapped(sb.toString(), PrintMode.VERBOSE);
	}
	
	public static ChemPredictor getChemPredictor(Predictor predictor, CLIConsole cons) {
		ChemPredictor sp = null;
		
		if (predictor instanceof ConformalClassifier)
		sp = new ChemCPClassifier((ConformalClassifier) predictor);
		else if (predictor instanceof ConformalRegressor)
		sp = new ChemCPRegressor((ConformalRegressor) predictor);
		else if (predictor instanceof AVAPClassifier)
		sp = new ChemVAPClassifier((AVAPClassifier) predictor);
		else
		cons.failWithArgError("Predictor of unsupported type: " + predictor);
		
		return sp;
	}
	
	public static ChemPredictor getSignaturesPredictor(Predictor predictor, DescriptorsMixin descriptorOpts,
	CLIConsole cons) {
		ChemPredictor sp = getChemPredictor(predictor, cons);
		
		if (descriptorOpts != null) {
			ChemDataset prob = sp.getDataset();
			prob.setDescriptors(descriptorOpts.descriptors);
		}
		
		return sp;
	}
	
	private static CLIProgressBar getPB(Object cmd) {
		CLIProgressBar pb = null;
		try {
			pb = getFieldByType(cmd, CLIProgressBar.class).get();
		} catch (Exception e) {
		}
		return pb == null ? new NullProgress() : pb;
	}
	
	public static void applyTransformations(ChemDataset sp, boolean isClassification,
	List<Transformer> transformers, Object program, CLIConsole console) {
		
		if (transformers == null || transformers.isEmpty()) {
			LOGGER.debug("No transformations applied");
			return; // No transformations
		}
		
		// we're doing transformations - add progress step
		CLIProgressBar pb = getPB(program);
		
		pb.addAdditionalStep();
		
		if (transformers.size() == 1) {
			console.println("Applying data transformation:", PrintMode.NORMAL);
		} else {
			console.println("Applying data transformations:", PrintMode.NORMAL);
		}
		
		for (Transformer t : transformers) {
			if (t instanceof ColumnTransformer && !((ColumnTransformer)t).getColumns().useAll())
			console.println(" - Applying %s to %s...", PrintMode.NORMAL,
			t.getName(),((ColumnTransformer) t).getColumns());
			else
			console.println(" - Applying %s...", PrintMode.NORMAL, t.getName());
			
			// Warnings if doing strange stuff
			if (isClassification && !t.applicableToClassificationData())
			console.printlnStdErr(
			"Note: Transformer %s is not applicable to classification data so the results might be unsatisfactory!",
			PrintMode.NORMAL,t.getName());
			else if (!isClassification && !t.applicableToRegressionData())
			console.printlnStdErr(
			"Note: Transformer %s is not applicable to regression data so the results might be unsatisfactory!",
			PrintMode.NORMAL,t.getName());
			
			// Apply it
			sp.apply(t);
			console.println("   " + t.getLastInfo().toString(), PrintMode.NORMAL);
		}
		console.println(
		"Final dataset contains %s records with %s features",
		PrintMode.NORMAL,sp.getNumRecords(),sp.getNumAttributes());
		
		pb.stepProgress();
	}
	
	/**
	* Check if there are missing value features, and list the features with those
	* @param data The ChemData to check
	* @param failIfPresent if {@code true} the program will exit, {@code false} there will only be error printed
	* @param console the console to print in
	*/
	public static void verifyNoMissingDataAndPrintErr(ChemDataset data, boolean failIfPresent, CLIConsole console){
		if (!data.containsMissingFeatures()){
			LOGGER.debug("No missing features in data - validation performed");
			return;
		}
		LOGGER.debug("Found missing data in precomputed data - compiling info to user");
		
		// Count the number of NaN/missing features for each feature index
		// Default data set
		Map<Integer,Integer> feature2numNan = countNaNFeatures(data.getDataset());
		// Model exclusive
		if (! data.getModelingExclusiveDataset().isEmpty()){
			Map<Integer,Integer> counts = countNaNFeatures(data.getModelingExclusiveDataset());
			// Merge counts
			for (Map.Entry<Integer,Integer> kv : counts.entrySet()){
				feature2numNan.put(kv.getKey(), kv.getValue() + feature2numNan.getOrDefault(kv.getKey(), 0));
			}
		}
		// Calibration exclusive
		if (! data.getCalibrationExclusiveDataset().isEmpty()){
			Map<Integer,Integer> counts = countNaNFeatures(data.getCalibrationExclusiveDataset());
			// Merge counts
			for (Map.Entry<Integer,Integer> kv : counts.entrySet()){
				feature2numNan.put(kv.getKey(), kv.getValue() + feature2numNan.getOrDefault(kv.getKey(), 0));
			}
		}
		
		if (feature2numNan.isEmpty()){
			LOGGER.debug("No features were NaN or missing value features, something went wrong either in CLIProgramUtils or ChemDataset");
			return;
		}
		List<String> featureNames = data.getFeatureNames(false);
		List<Integer> featIndices = new ArrayList<>(feature2numNan.keySet());
		Collections.sort(featIndices); // Sort indices so they are in the same order as was given
		
		StringBuilder sb = new StringBuilder("Found feature").append(featIndices.size()>1 ? "s" : "").append(" with missing values in the data:%n");
		for (int index : featIndices){
			sb.append(" - ").append(featureNames.get(index)). append(": ").append(feature2numNan.get(index)).append(" missing value(s)%n");
		}
		sb.append("Please remove/impute these before running modeling.");
		
		if (failIfPresent){
			console.failWithArgError(sb.toString());
		} else {
			console.printStdErr(sb.toString(), PrintMode.SILENT);
		}
		
		
	}
	
	private static Map<Integer,Integer> countNaNFeatures(SubSet data){
		Map<Integer,Integer> f2c = new HashMap<>();
		for (DataRecord r : data){
			for (FeatureVector.Feature f : r.getFeatures()){
				if (f instanceof MissingValueFeature || Double.isNaN(f.getValue())){
					int index = f.getIndex();
					f2c.put(index, 1 + f2c.getOrDefault(index,0));
				}
			}
		}
		return f2c;
	}
	
	public static boolean isMultiClassTask(ChemPredictor pred) {
		if (pred instanceof ChemCPClassifier) {
			
			if (pred.getPredictor().isTrained()) {
				return ((ConformalClassifier) pred.getPredictor()).getNumClasses()>2;
			}
			
			return ((ChemCPClassifier) pred).getLabels().size()>2;
		}
		return false;
	}
	
	public static boolean isMultiClassTask(ChemDataset data) {
		if (data.getTextualLabels()!=null) {
			return data.getTextualLabels().getNumLabels()>2;
		}
		return false;
	}
	
	public static boolean isMultiClassTask(Dataset data) {
		return DataUtils.checkDataType(data) == DataType.MULTI_CLASS;
	}
	
	/**
	* Uses the {@link #isMultiClassTask(ChemPredictor)} and calls the {@link #setupMetrics(ChemPredictor, ValidationPointsMixin, boolean)} with the deduced value.
	* @param predictor The Predictor to be used
	* @param points Evaluation points
	* @return A list of matching metrics
	*/
	public static List<Metric> setupMetrics(ChemPredictor predictor, ValidationPointsMixin points) {
		return setupMetrics(predictor, points, isMultiClassTask(predictor));
	}
	
	public static List<Metric> setupMetrics(ChemPredictor predictor, ValidationPointsMixin points, boolean isMultiClassTask) {
		
		List<Metric> metricBuilders = MetricFactory.getMetrics(predictor.getPredictor(),isMultiClassTask);
		List<Metric> metricsToRm = new ArrayList<>();
		List<Metric> metricsToAdd = new ArrayList<>();
		
		if (points.calibrationPoints == null  || points.calibrationPoints.isEmpty()){
			List<Double> newPoints = new ArrayList<>();
			if (predictor.getPredictor() instanceof ConformalPredictor){
				newPoints.add(CLIParameters.DEFAULT_CONFIDENCE);
			} else {
				newPoints.addAll(CLIParameters.DEFAULT_EXPECTED_PROBS);
			}
			LOGGER.debug("No explicit calibration points given, using default ones: {}",newPoints);
			points.calibrationPoints = newPoints;
		}
		
		if (predictor instanceof ChemClassifier){
			MetricFactory.setClassificationLabels(((ChemClassifier) predictor).getNamedLabels(), metricBuilders);
		}
		
		
		for (Metric m : metricBuilders) {
			
			if (m instanceof VAPCalibrationPlotBuilder && points.calibrationPointWidth != null){
				((VAPCalibrationPlotBuilder)m).setEvaluationPoints(points.calibrationPoints,points.calibrationPointWidth);
			} else if (m instanceof PlotMetric) {
				((PlotMetric) m).setEvaluationPoints(points.calibrationPoints);
			}
				
			// Width based metrics - special treat
			if (m instanceof CIWidthBasedMetric) {
				CIWidthBasedMetric met = (CIWidthBasedMetric) m;
				metricsToRm.add(m); // Always remove the actual metric - clone and set widths if set
				if (points.predictionWidths != null && !points.predictionWidths.isEmpty()) {
					for (double w : points.predictionWidths) {
						CIWidthBasedMetric clone = (CIWidthBasedMetric) met
						.clone();
						clone.setCIWidth(w);
						metricsToAdd.add(clone);
					}
				}
			}
				
			// These are already present in the plot-versions of the same metrics
			if (m instanceof CPAccuracy || m instanceof ProportionMultiLabelPredictions
			|| m instanceof ProportionSingleLabelPredictions || m instanceof MedianPredictionIntervalWidth
			|| m instanceof MeanPredictionIntervalWidth) {
				metricsToRm.add(m);
			}
			// Update single valued confidence-dependent metrics after the closest
			// calibration-point
			if (m instanceof ConfidenceDependentMetric) {
				((ConfidenceDependentMetric) m).setConfidence(
				getClosest(points.calibrationPoints, ((ConfidenceDependentMetric) m).getConfidence()));
			}
		}
		if (!metricsToRm.isEmpty()) {
			metricBuilders.removeAll(metricsToRm);
		}
		if (!metricsToAdd.isEmpty()) {
			metricBuilders.addAll(metricsToAdd);
		}
		
		// Some extra tweaking - put the CP metrics first - the simple metrics etc are 'forced' and not 
		// Only perform this if conformal predictor
		if (predictor.getPredictor() instanceof ConformalPredictor) {
			metricBuilders.sort(new Comparator<Metric>() {
				
				@Override
				public int compare(Metric o1, Metric o2) {
					if (o1 instanceof CPMetric && ! (o2 instanceof CPMetric)) {
						// o1 CP - not o2
						return -100 + getDefaultCmp(o1, o2);
					} else if (!(o1 instanceof CPMetric) && o2 instanceof CPMetric) {
						// o1 not CP - o1 CP
						return 100 + getDefaultCmp(o1, o2);
					} 
					// Both cp or not
					return getDefaultCmp(o1, o2);
				}
				
				private int getDefaultCmp(Metric o1, Metric o2) {
					return o1.getName().compareTo(o2.getName());
				}
			});
		}
		
		return metricBuilders;
	}
	
	public static void setupOverallStatsFile(File file, CLIConsole console) {
		if (file != null) {
			if (file.exists() && file.length() > 0)
			console.failWithArgError("The specified output file already exists");
			try {
				UriUtils.createParentOfFile(file);
			} catch (Exception e) {
				LOGGER.debug("Could not create the parent of output-file: {}", file);
				console.failWithArgError("Could not create parents to output-file: " + file);
			}
		}
	}
	
	/**
	* Check if any metrics are of LabelDependent (i.e. requires positive and negative labels to be set) 
	* and prints in case it is needed - otherwise not!
	* @param metrics list of metrics that are used
	* @param nl textual labels (or {@code null})
	* @param console the console instance
	*/
	public static void printNoteOnPosNegLabels(List<? extends Metric> metrics, NamedLabels nl, CLIConsole console) {
		if (nl == null)
		return;
		boolean haveToPrint = false;
		for (Metric m : metrics) {
			if (m instanceof LabelDependent) {
				haveToPrint = true;
				break;
			}
		}
		if (haveToPrint) {
			int pos = Collections.max(nl.getLabels().keySet());
			int neg = Collections.min(nl.getLabels().keySet());
			String txt = String.format("In the following results, the positive class is '%s' and negative is '%s'", nl.getLabel(pos),nl.getLabel(neg));
			console.printlnWrapped(txt, PrintMode.NORMAL);
		}
		
	}
	
	/**
	* Checks if note on forced predictions and mid-point is needed and prints using verbose mode
	* if is needed
	* @param metrics list of metrics that are used
	* @param console the console instance
	*/
	public static void printNoteOnForcedPredAndMidpoint(List<? extends Metric> metrics, CLIConsole console) {
		
		if (console.getVerbosity() == VerbosityLvl.VERBOSE) {
			boolean containsCP=false;
			int regCnt=0,clfCnt=0;
			List<String> forcedMetricNames = new ArrayList<>();
			for (Metric m : metrics) {
				if (m instanceof CPMetric) {
					containsCP = true;
				} else if (m instanceof PointClassifierMetric || m instanceof PointPredictionMetric) {
					forcedMetricNames.add(m.getName());
				}
				if (m instanceof RegressionMetric)
				regCnt++;
				if (m instanceof ClassifierMetric)
				clfCnt++;
			}
			if (containsCP && !forcedMetricNames.isEmpty()) {
				// Regression warning
				if (regCnt>clfCnt)
				console.printlnWrapped("Note that the following metrics are only computed based on the mid-point predictions of the predictor: "+StringUtils.toStringNoBrackets(forcedMetricNames), 
				PrintMode.VERBOSE);
				// Classifier warning
				else
				console.printlnWrapped("Note that the following metrics are computed based on 'forced predictions' - i.e. taking the class with the highest p-value as the predicted class: "+StringUtils.toStringNoBrackets(forcedMetricNames), 
				PrintMode.VERBOSE);
			}
		}
	}
	
	/**
	* Prints results found using crossvalidate and validate programs
	* @param metrics the metrics that was used
	* @param outputFile optional file to print to, otherwise print to console
	* @param format the output format
	* @param outputROC if the ROC (if applicable) should be written
	* @param nl NamedLabels if classification, or {@code null} for regression
	* @param console the console instance
	*/
	public static void printResults(List<? extends Metric> metrics, File outputFile, TextOutputType format,
	boolean outputROC, NamedLabels nl, CLIConsole console) {
		
		// Compile results String
		String outputString = null;
		try {
			outputString = compileResults(metrics, format, outputROC, console);
		} catch (IOException e) {
			LOGGER.debug("Failed compiling results", e);
			console.failWithArgError("Failed compiling overall statistics");
		}
		
		// Print a new line - then have a set of notes that can be printed
		console.println("", PrintMode.NORMAL);
		
		// Write a note on positive and negative class
		printNoteOnPosNegLabels(metrics, nl, console);
		// Write a note on forced predictions - if CPMetrics and standard ones
		printNoteOnForcedPredAndMidpoint(metrics, console);
		
		// Output the final results
		if (outputFile != null) {
			console.print("Writing overall statistics to file:%n%s ... ", PrintMode.NORMAL, outputFile);
			
			try (
			FileWriter fw = new FileWriter(outputFile);
			PrintWriter printer = new PrintWriter(fw);
			) {
				printer.printf(outputString);
				printer.flush();
				console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
			} catch (IOException e) {
				LOGGER.debug("Failed writing results to file", e);
				console.println(ProgressInfoTexts.FAILED_TAG, PrintMode.NORMAL);
				console.println("Falling back to write to console:", PrintMode.NORMAL);
				console.println(outputString, PrintMode.SILENT);
			}
		} else {
			console.println("%nOverall statistics:", PrintMode.NORMAL);
			console.println(outputString, PrintMode.SILENT);
		}
	}
	
	private static String compileResults(List<? extends Metric> metrics, TextOutputType format, boolean outputROC,
	CLIConsole console) throws IOException {
		
		MetricsOutputFormatter formatter = new MetricsOutputFormatter(metrics);
		
		// Special treat JSON as everything in the same line
		if (format == TextOutputType.JSON) {
			LOGGER.debug("printing results as json");
			return formatter.getJSON(outputROC);
		}
		
		StringBuilder sb = new StringBuilder();
		
		// Remaining output formats
		switch (format) {
			case CSV:
			LOGGER.debug("printing results as csv");
			sb.append(formatter.getCSV(','));
			break;
			
			case TSV:
			LOGGER.debug("printing results as tsv");
			sb.append(formatter.getCSV('\t'));
			break;
			
			case TEXT:
			LOGGER.debug("printing results as text");
			sb.append(formatter.getTextOnlySingleValueMetrics());
			sb.append("%nCalibration plot:%n");
			sb.append(formatter.getTextOnlyPlots(CLIConsole.DEFAULT_DELIMITER_CSV));
			break;
			
			default:
			LOGGER.debug("Got results output format thats not recognized: {}", format);
			console.printlnWrappedStdErr(
			"Unrecognized results format: " + format + "%nFalling back to printing as text..%n",
			PrintMode.NORMAL);
			
			sb.append(formatter.getTextOnlySingleValueMetrics());
			sb.append("%nCalibration plot:%n");
			sb.append(formatter.getTextOnlyPlots(CLIConsole.DEFAULT_DELIMITER_CSV));
			break;
		}
		
		sb.append("%n");
		
		// Always print roc curve as CSV in case asked for (JSON special treat above)
		if (outputROC) {
			for (Metric m : metrics) {
				if (m instanceof ROC_AUC) {
					sb.append("%nROC Curve:%n");
					if (format == TextOutputType.CSV) {
						sb.append(((ROC_AUC) m).rocAsCSV(','));
					} else if (format == TextOutputType.TSV) {
						sb.append(((ROC_AUC) m).rocAsCSV('\t'));
					} else
					sb.append(((ROC_AUC) m).rocAsCSV(CLIConsole.DEFAULT_DELIMITER_CSV));
				}
			}
		}
		
		return sb.toString();
	}
	
	private static double getClosest(List<Double> valPoints, double original) {
		double best = valPoints.get(0);
		for (double c : valPoints) {
			if (Math.abs(c - original) < Math.abs(best - original)) {
				best = c;
			}
		}
		return best;
	}
	
	public static int getProgressInterval(int totalNum, int numProgress) {
		// If we dont have 10 in total
		if (totalNum < numProgress)
		return 1;
		
		// If all adds up in a nice way
		if (totalNum % numProgress == 0)
		return totalNum/numProgress;
		
		// otherwise do maths
		return myCeil(totalNum/(numProgress-1));
	}
	
	private static int myCeil(int num) {
		int den = 1;
		int inc = 0;
		
		while (num > 10) {
			inc += num % 10;
			num /= 10;
			den *= 10;
		}
		
		return (num + (inc > 0? 1:0)) * den;
	}
	
	public static String getModelType(Object model) {
		if (model instanceof ChemCPClassifier) {
			if (((ChemCPClassifier) model).getPredictor() instanceof TCP)
			return "signatures tcp classification model";
			else if (((ChemCPClassifier) model).getPredictor() instanceof ACP){
				return "signatures " + ((ACP)((ChemCPClassifier) model).getPredictor()).getStrategy().toString().toLowerCase() + " classification model";
			} else
			return "signatures classification model";
		} else if (model instanceof ChemCPRegressor) {
			if (((ChemCPRegressor) model).getPredictor() instanceof ACP){
				return "signatures " + ((ACP)((ChemCPRegressor) model).getPredictor()).getStrategy().toString().toLowerCase() + " regression model";
			} else
			return "signatures regression model";
		} if (model instanceof Predictor){
			return "sparse predictor model";
		} else
		return "not recognized model type";
	}
	
	public static void failWithBadUserInputFile(CLIConsole console, int numOK, ChemFile inputFile, 
	List<FailedRecord> failedRecords, ChemDataset dataset, String property, List<String> givenLabels, int numMaxAllowedFailures) {
		StringBuilder errMessage = new StringBuilder();

		// If no valid molecules were found - this means something is definitely incorrect in the parameters
		if (numOK <= 0){
			// 1. Cannot read from the file itself
			if (!UriUtils.canReadFromURI(inputFile.getURI())){
				errMessage
					.append("Cannot read from file: ")
					.append(inputFile.getURI());
				console.failWithArgError(errMessage.toString());
			}

			// 2. Input file is empty
			if (!UriUtils.verifyURINonEmpty(inputFile.getURI())){
				LOGGER.debug("Found input file {} to be empty", inputFile.getURI());
				errMessage.append("Input file is empty: ").append(inputFile.getURI());
				console.failWithArgError(errMessage.toString());
			}

			// 3. Wrong chem-format given
			try {
				ChemFormat actualFormat = ChemIOUtils.deduceFormat(inputFile.getURI());
				LOGGER.debug("Tried to deduce the file format and found it to be {}, let's check if the given argument {} matches",
					actualFormat, inputFile.getClass().getSimpleName());
				boolean correctFormat = false;
				switch (actualFormat){
					case CSV:
						correctFormat = inputFile instanceof CSVFile;
						break;
					case JSON:
						correctFormat = inputFile instanceof JSONFile;
						break;
					case SDF:
						correctFormat = inputFile instanceof SDFile;
						break;
					case UNKNOWN:
					default:
						actualFormat = ChemFormat.UNKNOWN;
						correctFormat = false; // we do not actually know the format, 
						break;
				}

				if (!correctFormat){
					errMessage
						.append("The given input file: ")
						.append(inputFile.getURI())
						.append(" seems to be of type ")
						.append(actualFormat.toString())
						.append(" while given type was ")
						.append(inputFile.getFileFormat())
						.append(" please make sure you specify the correct file format and any extra sub-arguments, for more information please run ");
					appendExplainChemFormat(errMessage);
					console.failWithArgError(errMessage.toString());
				}
			} catch (Exception e){
				LOGGER.debug("Failed to deduce the file format",e);
			}

			// 4. Invalid argument for the given type

			// 5. There is a BOM (byte order mark) in the file, but not configured to having one
			try {
				boolean hasBOM = UriUtils.hasBOM(inputFile.getURI());
				if (hasBOM & inputFile instanceof CSVFile){
					if (! ((CSVFile)inputFile).getHasBOM()){
						LOGGER.debug("The file has a BOM and CSV-file not configured to parse the BOM");
						errMessage.append("The input file: ")
							.append(inputFile.getURI())
							.append(" has a Byte Order Mark (BOM) while the parameter for BOM was not set to true, see ");
						appendExplainChemFormat(errMessage);
						errMessage.append(" for further details on the available parameters.");
					}
				}
			} catch (IOException e){
				LOGGER.debug("Failed to check if the input file had a BOM, this means there's like likely something wrong with the file itself",e);
				errMessage.append("Could not read from the given input file:%n")
					.append(inputFile.getURI()).append("%nPlease verify that the file itself is valid");
			}
			failInCaseErrMessageBeenGenerated(console, errMessage);
			
		}




		Map<Cause,Integer> failureCounts = getCounts(failedRecords);
		// Sort the Map by values
        List<Map.Entry<Cause, Integer>> entries = new ArrayList<>(failureCounts.entrySet());
        entries.sort(Map.Entry.<Cause, Integer>comparingByValue()); //.reversed()

		// Check the most common issue
		Map.Entry<Cause,Integer> first = entries.get(0);

		switch(first.getKey()){
			case LOW_HAC:
				errMessage
					.append(first.getValue())
					.append(" records were discarded due to having too low Heavy atom count (now set to minimum ")
					.append(dataset.getMinHAC())
					.append(") - consider lowering the threshold using the ")
					.append(ParameterUtils.PARAM_FLAG_ANSI_ON)
					.append("--min-hac")
					.append(ParameterUtils.ANSI_OFF)
					.append(" parameter");
				break;
			case DESCRIPTOR_CALC_ERROR:
				errMessage
					.append(first.getValue())
					.append(" records were discarded due to descriptor calculation failures, perhaps there is a bug in one or several of the descriptors that was specified.");
				break;
			case INVALID_PROPERTY:
				if (givenLabels == null){
					// Regression setting
					errMessage
						.append("Running in regression mode, but ")
						.append(first.getValue())
						.append(" records had property values that could not be converted to numerical values");
				} else {
					// Classification setting
					errMessage
						.append("Running in classification mode, but ")
						.append(first.getValue())
						.append(" records had property values which did not match any of the given class labels (")
						.append(StringUtils.join(", ", givenLabels))
						.append("), where the correct class labels given to ")
						.append(ParameterUtils.PARAM_FLAG_ANSI_ON)
						.append("--labels")
						.append(ParameterUtils.ANSI_OFF)
						.append("?");
				}
				break;

			case INVALID_RECORD:
				errMessage
					.append(first.getValue())
					.append(" records were discarded due to descriptor calculation failures, perhaps there is a bug in one or several of the descriptors that was specified.");
				break;
			case INVALID_STRUCTURE:
				errMessage
					.append(first.getValue())
					.append(" records were discarded due to invalid chemical structures - please verify your input data");
				break;
			case MISSING_PROPERTY:
				if (numOK>0){
					errMessage
						.append(first.getValue())
						.append(" records were discarded due to missing property values");
				} else {
					// No valid records found, perhaps we can give more details
					errMessage.append("No records had the given property (").append(property).append(')');

					// check the first 10 records to find the properties that exists
					List<String> propsInFirst10 = null;
					
					try (ChemFileIterator reader = inputFile.getIterator();){
						Set<String> allProps = new LinkedHashSet<>();
						for (int i=0; i<10 && reader.hasNext(); i++){
							IAtomContainer mol = reader.next();
							allProps.addAll(mol.getProperties().keySet().stream().map(Object::toString).collect(Collectors.toList()));
						}
						propsInFirst10 = CPSignMolProperties.stripInteralProperties(allProps);

						String bestMatch = new FuzzyMatcher().match(propsInFirst10, property);
						// Found a best match
						errMessage.append(" did you mean: '").append(bestMatch).append("'?");
					} catch (NoMatchException e) {
						// No match found with fuzzy search - but the allProps still contain all properties from the first 10 records
						if (propsInFirst10 != null & !propsInFirst10.isEmpty() & propsInFirst10.size() < 11){
							errMessage
								.append(", here are the available properties found for the first 10 records: ")
								.append(StringUtils.join(", ", propsInFirst10));
						} else {
							errMessage
								.append(", please verify that you specify the correct property to model to the flag ")
								.append(ParameterUtils.PARAM_FLAG_ANSI_ON)
								.append("--property")
								.append(ParameterUtils.ANSI_OFF).append(" including correct font case.");
						}
					} catch (Exception e){
						// Failed - not sure why, give a more generic answer then
						LOGGER.debug("Tried to deduce all properties from the first 10 molecules - failed",e);
						errMessage.append(" did you specify it correctly, using the correct font case?");
					}

				}
				
				break;
			case MISSING_STRUCTURE:
				// this is likely due to having the wrong header in CSV to be regarded as the column with SMILES
				if (inputFile instanceof CSVFile){
					errMessage
						.append(first.getValue())
						.append(" records were discarded due to missing chemical structures. Please verify that the correct column in the CSV is used as structure");
					try {
						CSVChemFileReader csv = ((CSVFile)inputFile).getIterator();
						List<String> allHeaders = csv.getHeaders();
						String smilesCol = csv.getSmilesColumnHeader();
						errMessage
							.append(", currently '")
							.append(smilesCol)
							.append("' is used as the column containing SMILES out of ");
						if (allHeaders.size() < 11){
							// specify all of them
							errMessage.append("the following columns: ").append(StringUtils.join(", ", allHeaders)).append('.');
						} else {
							// just give the total number of headers
							errMessage.append(allHeaders.size()).append(" columns.");
						}
					} catch(Exception e){
						// Failed getting it, so there might be an issue with the CSV settings?
						errMessage.append('.');
					}
				} else if (inputFile instanceof JSONFile){
					errMessage
						.append(first.getValue())
						.append(" records were discarded due to missing chemical structures - please check the requirements for using JSON input data by running: ");
					appendExplainChemFormat(errMessage);
				} else {
					LOGGER.debug("invalid structures in SDF format (or custom reader) - this should never occur - why does it?");
					errMessage
						.append(first.getValue())
						.append(" records were discarded due to missing chemical structures - please verify your input data.");
				}
				
				break;
			case UNKNOWN:
			default:
				LOGGER.debug("Most records ({}/{}) failed due to unknown reasons - trying to deduce why..", first.getValue(), failedRecords.size());
				errMessage
					.append("Most records failed due to non-characterized errors, please revise your arguments for potential spelling error or similar. You may also get further information from using the ")
					.append(ParameterUtils.PARAM_FLAG_ANSI_ON)
					.append("--list-failed")
					.append(ParameterUtils.ANSI_OFF)
					.append(" flag to get info about each individual failed record.");
				break;
				
		}

		errMessage.append("%n%n");

		if (numMaxAllowedFailures>=0 & dataset.getNumRecords()>0){
			errMessage
				.append("If these issues are expected you may consider setting the parameter ")
				.append(ParameterUtils.PARAM_FLAG_ANSI_ON)
				.append("--early-termination-after")
				.append(ParameterUtils.ANSI_OFF)
				.append(" to either '-1' (to turn of early termination) or to a higher value than currently set to");
		}

		console.failWithArgError(errMessage.toString());
	}

	private static void failInCaseErrMessageBeenGenerated(CLIConsole console, StringBuilder errorMessageBuilder){
		if (errorMessageBuilder.length()>0){
			console.failWithArgError(errorMessageBuilder.toString());
		}
	}

	private static void appendExplainChemFormat(StringBuilder sb){
		sb.append(ParameterUtils.RUN_EXPLAIN_ANSI_ON)
			.append("explain chem-formats")
			.append(ParameterUtils.ANSI_OFF);
	}
	
}

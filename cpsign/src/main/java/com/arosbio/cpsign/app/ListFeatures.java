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
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.security.InvalidKeyException;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.data.ChemDataset.NamedFeatureInfo;
import com.arosbio.cheminf.descriptors.ChemDescriptor;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.commons.MathUtils;
import com.arosbio.cpsign.app.params.mixins.EchoMixin;
import com.arosbio.cpsign.app.params.mixins.EncryptionMixin;
import com.arosbio.cpsign.app.params.mixins.LogfileMixin;
import com.arosbio.cpsign.app.params.mixins.OverallStatsMixinClasses;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.CLIProgramUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.cpsign.out.OutputNamingSettings.ProgressInfoTexts;
import com.arosbio.io.BufferedFileWriter;
import com.arosbio.io.ForcedSystemOutWriter;
import com.arosbio.ml.io.ModelIO.ModelType;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.google.common.collect.ImmutableMap;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/*
 * PB.VALIDATE_PARAMS_PROGRESS
 * PB.LOADING_FILE_OR_MODEL_PROGRESS
 * PB.CROSSVALIDATING_PROGRESS
 * PB.RESULTS_WRITING_PROGRESS
 */
@Command(
		name = ListFeatures.CMD_NAME,
		aliases = ListFeatures.CMD_ALIAS,
		header = ListFeatures.CMD_HEADER,
		description = ListFeatures.CMD_DESCRIPTION,
		descriptionHeading = CPSignApp.DESCRIPTION_HEADER
		)
public class ListFeatures implements RunnableCmd {

	private static final Logger LOGGER = LoggerFactory.getLogger(ListFeatures.class);

	public static final String CMD_NAME = "list-features";
	public static final String CMD_ALIAS = "list-descriptors";
	public static final String CMD_HEADER = "List features from a model file";
	public static final String CMD_DESCRIPTION = "List features from a precomputed data set or trained model. Convenience function to list all features after e.g. feature-selection.";

	/*****************************************
	 * INTERNAL STATE
	 *****************************************/
	
	@Spec private CommandSpec spec;
	private CLIConsole console = CLIConsole.getInstance();
	// private EncryptionSpecification encryptSpec = null;

	/*****************************************
	 * OPTIONS
	 *****************************************/
	// Input
	@Option(names = { "-m", "--model" },
			description = "A CPSign predictor model or precomputed data set",
			required = true,
			paramLabel = ArgumentType.URI_OR_PATH)
	private URI modelFile;

	@Mixin
	private EncryptionMixin encryptMixin = new EncryptionMixin();

	// Output
	@Option(
			names = {"-ro", "--result-output"},
			description = "File to print the descriptors to (default is printing to terminal)",
			paramLabel = ArgumentType.FILE_PATH
			)
	private File overallStatsFile;

	@Mixin
	private OverallStatsMixinClasses.JSONTextCSV_TSV resultOutputFormat = new OverallStatsMixinClasses.JSONTextCSV_TSV();

	@Option(
			names = {"--include-signatures", "--incl-signs"},
			description="Include signatures (if present), which could potentially be >100k")
	private boolean includeSignatures = false;

	@Mixin
	private LogfileMixin loggingArgs;

	@Mixin
	private EchoMixin echo;

	@Option(names = {"-v", "--verbose"}, 
			description = "Verbose mode. Calculates descriptive stats about each feature as well as checks for missing features - only possible for precomputed dataset",
			required = false)
	public boolean verboseMode;

	/*****************************************
	 * END OF OPTIONS
	 *****************************************/
	
	@Override
	public String getName() {
		return CMD_NAME;
	}

	@Override
	public Integer call() {

		CLIProgramUtils.doFullProgramConfig(this);
		
		// Set up and init program
		validateParams();
		LOGGER.debug("Validated arguments");


		// load data
		ChemDataset cp = loadData();

		// Cannot run verbose mode when not having data 
		if (verboseMode && cp.getNumRecords() == 0){
			console.printStdErr("Cannot run in -v|--verbose mode for trained models, where training data is no longer saved - revering to non-verbose mode", 
				PrintMode.SILENT);
			verboseMode = false;
		}


		// Print features
		printFeatures(cp);


		console.println("", PrintMode.NORMAL);

		return ExitStatus.SUCCESS.code;
	}

	private ChemDataset loadData() {
		ModelType type = null;
		try {
			type = ModelSerializer.getType(modelFile);
		} catch (IOException e){
			LOGGER.debug("Failed getting model type",e);
			console.failWithArgError("Failed loading model: "+e.getMessage());
			return null; // This never happens
		}

		try {
			if (type == ModelType.PRECOMPUTED_DATA){
				LOGGER.debug("Loading precomputed data");
				return ModelSerializer.loadDataset(modelFile, encryptMixin.exclusive.encryptSpec);
			} else if (type == ModelType.CHEM_PREDICTOR){
				LOGGER.debug("Loading data from chem predictor");
				return ModelSerializer.loadChemPredictor(modelFile, encryptMixin.exclusive.encryptSpec).getDataset();
			} else if (type == ModelType.PLAIN_PREDICTOR) {
				LOGGER.debug("Found given model of 'plain predictor' type - not supported!");
				console.failWithArgError("Plain predictor models not supported by this program");
				return null; // This never happens
			} else {
				LOGGER.debug("Given data from incompatible model type: {}", type);
				console.failWithArgError("Model of unsupported type given");
				return null; // This never happens
			}
		} catch (IOException | InvalidKeyException e){
			LOGGER.debug("Failed loading model",e);
			console.failWithArgError("Failed loading model: %s", e.getMessage());
			return null; // This never happens
		}

	}

	private void validateParams() {

		CLIProgramUtils.setupOverallStatsFile(overallStatsFile, console);

	}

	private void printFeatures(ChemDataset data) {

		List<String> feats = data.getFeatureNames(includeSignatures);

		if (feats.isEmpty() && ! includeSignatures) {
			LOGGER.debug("No features could be loaded and was set to not print signatures, checking if signatures descriptor is used");
			List<ChemDescriptor> descriptors = data.getDescriptors();
			if (descriptors.get(descriptors.size()-1) instanceof SignaturesDescriptor){
				// Signatures were used
				if (descriptors.size()>1){
					LOGGER.debug("more than the signatures descriptor is used - probably due to transformations being applied prior to this step");
					console.printlnWrapped("%nNo features were loaded, perhaps data transformations prior to this step has removed other descriptors. If you wish to display the signatures, add the " + 
						CLIProgramUtils.getParamName(this, "includeSignatures", "INCLUDE_SIGNATURES") + " flag.", 
							PrintMode.SILENT);
				} else {
					// Only signatures were used
					console.printlnWrapped("%nNo features were loaded, only signatures descriptor was used for this data set, add the " + 
						CLIProgramUtils.getParamName(this, "includeSignatures", "INCLUDE_SIGNATURES") + " flag if you wish to display all generated signatures", 
							PrintMode.SILENT);
				}

			} else {
				LOGGER.debug("No features could be loaded - must be a bug or issue with software compatibility and serialization format");
				console.printlnWrapped("%nNo features could be loaded, this is likely a software bug or incompatibility of serialization format - was this an old model?", 
					PrintMode.SILENT);
			}
			
			return;
		} else if (feats.isEmpty()) {
			console.printlnWrapped("%nNo features could be loaded", PrintMode.NORMAL);
			return;
		}

		Writer resultsWriter = null;
		try {


			if (overallStatsFile != null) {
				console.print(WordUtils.wrap("Writing features to file: "+overallStatsFile + ProgressInfoTexts.SPACE_ELLIPSES, console.getTextWidth()), 
						PrintMode.NORMAL);
				resultsWriter = BufferedFileWriter.getFileWriter(overallStatsFile);
			} else {
				console.println("Features:", PrintMode.NORMAL);
				resultsWriter = new ForcedSystemOutWriter(false);
			}

			if (verboseMode){
				// Get the feature info first
				List<NamedFeatureInfo> featureInfos = data.getFeaturesInfo(includeSignatures);
				switch (resultOutputFormat.outputFormat) {
				case JSON:
					printVerboseAsJSON(featureInfos, resultsWriter);
					break;
				case CSV:
					printVerboseAsCSV(CSVFormat.DEFAULT.builder().setDelimiter(',').setRecordSeparator(System.lineSeparator()).build(), featureInfos, resultsWriter);
					break;
				case TEXT:
				case TSV:
				default:
					printVerboseAsCSV(CSVFormat.DEFAULT.builder().setDelimiter('\t').setRecordSeparator(System.lineSeparator()).build(), featureInfos, resultsWriter);
					break;
				}
			}
			else {
				switch (resultOutputFormat.outputFormat) {
				case JSON:
					printAsJSON(feats, resultsWriter);
					break;
				case CSV:
					printAsCSV(CSVFormat.DEFAULT.builder().setDelimiter(',').setRecordSeparator(System.lineSeparator()).build(), feats, resultsWriter);
					break;
				case TEXT:
				case TSV:
				default:
					printAsCSV(CSVFormat.DEFAULT.builder().setDelimiter('\t').setRecordSeparator(System.lineSeparator()).build(), feats, resultsWriter);
					break;
				}
			}

		} catch (IOException e) {
			LOGGER.debug("Failed printing features",e);
			console.failWithArgError("Failed printing to output: " +e.getMessage());
		} finally {
			try {
				resultsWriter.close();
			} catch (IOException e) {
				LOGGER.debug("Failed closing results-writer",e);
			}
		}

		if (overallStatsFile != null) {
			console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);
		}
	}

	private static final String FEATURE_INDEX_HEADER = "Index";
	private static final String FEATURE_NAME_HEADER = "Feature name";
	private static final String MIN_VALUE_HEADER = "Min value";
	private static final String MAX_VALUE_HEADER = "Max value";
	private static final String MEAN_VALUE_HEADER = "Mean value";
	private static final String MEDIAN_VALUE_HEADER = "Median value";
	private static final String CONTAIN_NAN_HEADER = "Contains missing values";


	private void printAsCSV(CSVFormat format, List<String> features, Writer writer) throws IOException {
		try (CSVPrinter printer = new CSVPrinter(writer, format)){

			// Header
			printer.printRecord(FEATURE_INDEX_HEADER, FEATURE_NAME_HEADER);

			// Features
			for (int i=0;i<features.size(); i++) {
				printer.printRecord(i, features.get(i));
			}
			printer.flush();
		}

	}

	private void printVerboseAsCSV(CSVFormat format, List<NamedFeatureInfo> features, Writer writer) throws IOException {
		try (CSVPrinter printer = new CSVPrinter(writer, format)){

			// Header
			printer.printRecord(FEATURE_INDEX_HEADER, FEATURE_NAME_HEADER, MIN_VALUE_HEADER,MAX_VALUE_HEADER,MEAN_VALUE_HEADER,MEDIAN_VALUE_HEADER,CONTAIN_NAN_HEADER);

			// Features
			for (NamedFeatureInfo info : features){
				printer.printRecord(info.index,info.featureName,
				MathUtils.roundTo3significantFigures(info.minValue),
				MathUtils.roundTo3significantFigures(info.maxValue),
				MathUtils.roundTo3significantFigures(info.meanValue),
				MathUtils.roundTo3significantFigures(info.medianValue)
				,info.containsNaN);
			}
			printer.flush();
		}

	}

	private void printAsJSON(List<String> features, Writer writer) throws IOException {
		writer.write(Jsoner.prettyPrint(new JsonArray(features).toJson()));
		writer.write(System.lineSeparator());
		writer.flush();
	}

	private void printVerboseAsJSON(List<NamedFeatureInfo> infoList, Writer writer) throws IOException {
		JsonArray array = new JsonArray();
		for (NamedFeatureInfo info : infoList){
			array.add(getMapping(info));
		}
		writer.write(Jsoner.prettyPrint(array.toJson()));
		writer.write(System.lineSeparator());
		writer.flush();
	}

	private static Map<String,Object> getMapping(final NamedFeatureInfo info){
		return ImmutableMap.of(FEATURE_INDEX_HEADER, info.index,
			FEATURE_NAME_HEADER,info.featureName,
			MIN_VALUE_HEADER,MathUtils.roundTo3significantFigures(info.minValue),
			MAX_VALUE_HEADER,MathUtils.roundTo3significantFigures(info.maxValue),
			MEAN_VALUE_HEADER,MathUtils.roundTo3significantFigures(info.meanValue),
			MEDIAN_VALUE_HEADER,MathUtils.roundTo3significantFigures(info.medianValue),
			CONTAIN_NAN_HEADER, info.containsNaN);

	}

}

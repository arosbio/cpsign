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

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.io.ModelSerializer;
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

	@SuppressWarnings("null")
	private void printFeatures(ChemDataset cp) {

		List<String> feats = cp.getFeatureNames(includeSignatures);

		if (feats.isEmpty() && ! includeSignatures) {
			console.printlnWrapped("%nNo features could be loaded, perhaps only signatures are used, try to run with the " + 
		CLIProgramUtils.getParamName(this, "includeSignatures", "INCLUDE_SIGNATURES") + " flag", PrintMode.NORMAL);
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


			switch (resultOutputFormat.outputFormat) {
			case JSON:
				printAsJSON(feats, resultsWriter);
				break;
			case CSV:
				printAsCSV(CSVFormat.DEFAULT.withDelimiter(',').withSystemRecordSeparator(), feats, resultsWriter);
				break;
			case TEXT:
			case TSV:
			default:
				printAsCSV(CSVFormat.DEFAULT.withDelimiter('\t').withSystemRecordSeparator(), feats, resultsWriter);
				break;
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
	private static final String FEATURE_NAME_HEADER = "Feature";

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

	private void printAsJSON(List<String> features, Writer writer) throws IOException {
		writer.write(Jsoner.prettyPrint(new JsonArray(features).toJson()));
		writer.write(System.lineSeparator());
		writer.flush();
	}

}

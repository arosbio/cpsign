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

import java.net.URI;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cpsign.app.params.CLIParameters.TextOutputType;
import com.arosbio.cpsign.app.params.mixins.ConsoleVerbosityMixin;
import com.arosbio.cpsign.app.params.mixins.OverallStatsMixinClasses;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIProgramUtils;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.CLIConsole.VerbosityLvl;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.ml.io.ModelIO;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;



@Command(
		name = ModelInfoCMD.CMD_NAME,
		aliases = ModelInfoCMD.CMD_ALIAS,
		header = ModelInfoCMD.CMD_HEADER,
		description = ModelInfoCMD.CMD_DESCRIPTION,
		descriptionHeading = CPSignApp.DESCRIPTION_HEADER
		)
public class ModelInfoCMD implements RunnableCmd {

	public static final String CMD_NAME = "model-info";
	public static final String CMD_ALIAS = "check-version";
	public static final String CMD_HEADER = "Get information about a CPSign model";
	public static final String CMD_DESCRIPTION = "Get information about a CPSign model, containing meta data such as build version of CPSign, the property that it predicts, the type of predictor etc.";

	private static final Logger LOGGER = LoggerFactory.getLogger(ModelInfoCMD.class);

	/*****************************************
	 * INTERNAL STATE
	 *****************************************/
	private CLIConsole console = CLIConsole.getInstance();
	@Spec private CommandSpec spec;

	/*****************************************
	 * OPTIONS
	 *****************************************/

	@Option(names = { "-m", "--model" }, 
			description = "A CPSign predictor model or precomputed data set",
			required = true,
			paramLabel = ArgumentType.URI_OR_PATH
			)
	private URI modelFile;

	@Mixin
	private OverallStatsMixinClasses.JSONOrText output = new OverallStatsMixinClasses.JSONOrText(); 

	@Mixin
	private ConsoleVerbosityMixin consoleArgs;

	/*****************************************
	 * END OF OPTIONS
	 *****************************************/

	@Override
	public String getName() {
		return CMD_NAME;
	}

	public Integer call(){

		CLIProgramUtils.doFullProgramConfig(this);

		Map<String,String> info = null;
		try {
			info = ModelIO.getModelInfo(modelFile, console.getVerbosity() == VerbosityLvl.VERBOSE);
		} catch (Exception e){
			LOGGER.debug("Failed compiling model info for input model");
			console.failWithArgError("Parameter " + CLIProgramUtils.getParamName(this, "modelFile", "MODEL_FILE") + " was not a CPSign model");
		}

		if (output.outputFormat == TextOutputType.JSON) {
			console.println(Jsoner.prettyPrint(new JsonObject(info).toJson()), PrintMode.NORMAL); 
		} else if (output.outputFormat== TextOutputType.TEXT){
			console.println("Model Info:%n-----------", PrintMode.NORMAL);
			console.println(getInfoAsText(info), PrintMode.NORMAL);
		} else {
			LOGGER.debug("Failed with not supported output format: {}", output.outputFormat);
			console.printlnWrappedStdErr("Non-supported output format: " + output.outputFormat + "%nfalling back to writing as text..", PrintMode.NORMAL);
			console.println("Model Info:%n-----------", PrintMode.NORMAL);
			console.println(getInfoAsText(info), PrintMode.NORMAL);
		}

		// FINISH PROGRAM
		console.println("", PrintMode.NORMAL);
		return ExitStatus.SUCCESS.code;
	}


	private static String getInfoAsText(Map<String,String> info) {
		// Get the widest column
		int widestKey = -1;
		for (String key: info.keySet()) {
			if (key.length() > widestKey)
				widestKey = key.length();
		}

		// Put together the output
		StringBuilder sb = new StringBuilder();
		for (String key: info.keySet()) {
			sb.append(String.format("%-"+widestKey+"s : %s%n", key, info.get(key)));
		}
		return sb.toString();
	}

}

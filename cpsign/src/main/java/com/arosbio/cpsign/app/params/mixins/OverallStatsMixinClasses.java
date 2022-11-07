/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app.params.mixins;

import java.io.File;

import com.arosbio.cpsign.app.params.CLIParameters.TextOutputType;
import com.arosbio.cpsign.app.params.converters.EmptyFileConverter;
import com.arosbio.cpsign.app.params.converters.TextOutputTypeConverter;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;

import picocli.CommandLine.Option;

public class OverallStatsMixinClasses {


	public static class JSONOrText {

		// @Option(
		// 		names = {"-rf","--result-format"}, 
		// 		description = 
		// 		"Output format, options:%n"+
		// 				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(1) json%n"+
		// 				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(2) text"+ParameterUtils.MULTIPLE_ARGUMENT_NAME_SPLITTER+"plain%n"+
		// 				ParameterUtils.DEFAULT_VALUE_LINE,
		// 				converter = TextOutputTypeConverter.class,
		// 				defaultValue="2",
		// 				paramLabel = ArgumentType.ID_OR_TEXT)
		@Option(
				names = {"-rf","--result-format"}, 
				description = 
				{"Output format, options:",
					ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(1) json",
					ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(2) text"+ParameterUtils.MULTIPLE_ARGUMENT_NAME_SPLITTER+"plain",
					ParameterUtils.DEFAULT_VALUE_LINE
				},
				converter = TextOutputTypeConverter.class,
				defaultValue="2",
				paramLabel = ArgumentType.ID_OR_TEXT)
		public TextOutputType outputFormat = TextOutputType.TEXT;
	}


	public static class JSONTextCSV_TSV {

		// @Option(
		// 		names = {"-rf","--result-format"}, 
		// 		description = 
		// 		"Output format, options:%n"+
		// 				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(1) json%n"+
		// 				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(2) text"+ParameterUtils.MULTIPLE_ARGUMENT_NAME_SPLITTER+"plain%n"+
		// 				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(3) CSV%n"+
		// 				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(4) TSV%n"+
		// 				ParameterUtils.DEFAULT_VALUE_LINE,
		// 				converter = TextOutputTypeConverter.class,
		// 				defaultValue = "2",
		// 				paramLabel = ArgumentType.ID_OR_TEXT)
		@Option(
				names = {"-rf","--result-format"}, 
				description = 
				{"Output format, options:",
					ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(1) json",
					ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(2) text"+ParameterUtils.MULTIPLE_ARGUMENT_NAME_SPLITTER+"plain",
					ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(3) CSV",
					ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(4) TSV",
					ParameterUtils.DEFAULT_VALUE_LINE
				},
				converter = TextOutputTypeConverter.class,
				defaultValue = "2",
				paramLabel = ArgumentType.ID_OR_TEXT)
		public TextOutputType outputFormat = TextOutputType.TEXT;
	}

	public static class StatsFile {
		
		@Option(
				names = {"-ro", "--result-output"},
				description = "File to print the overall statistics to (default is printing to terminal)",
				converter = EmptyFileConverter.class,
				paramLabel = ArgumentType.FILE_PATH
				)
		public File overallStatsFile;

	}

}

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

import com.arosbio.cpsign.app.params.CLIParameters.ChemOutputType;
import com.arosbio.cpsign.app.params.converters.ChemOutputTypeConverter;
import com.arosbio.cpsign.app.params.converters.EmptyFileConverter;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;

import picocli.CommandLine.Option;

public class OutputChemMixin {

	@Option(names = {"-of","--output-format"}, 
			description = {"Output format of predictions, options:",
			ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(1) json",
			ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(2) TSV  (tab-delimited CSV)",
			ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(3) sdf"+ParameterUtils.MULTIPLE_ARGUMENT_NAME_SPLITTER+"sdf-v2000",
			ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(4) sdf-v3000",
			ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(5) CSV  (comma-delimited CSV)",
			ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(6) splitted-json   (writes a new JSON object for each record, separated with a new line)",
			"Default: ${DEFAULT-VALUE}"},
			paramLabel = ArgumentType.ID_OR_TEXT,
			converter = ChemOutputTypeConverter.class
	)
	public ChemOutputType outputFormat = ChemOutputType.JSON;

	@Option(names = {"-o","--output"}, 
			description = "File to write output to (default is printing to screen)",
			converter = EmptyFileConverter.class,
			paramLabel = ArgumentType.FILE_PATH
	)
	public File outputFile;

	@Option(names = {"--output-inchi"}, 
			description = "Generate InChI and InChIKey in the output")
	public boolean printInChI=false;

	@Option(names = {"--compress"}, 
			description = "If the output should be compressed (only possible when writing to file or in silent mode)")
	public boolean compress = false;


}

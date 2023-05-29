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

import com.arosbio.chem.io.in.ChemFile;
import com.arosbio.cpsign.app.params.converters.ChemFileConverter;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;

public class CompoundsToPredictMixin {
	
	@ArgGroup(multiplicity = "1..*", exclusive = false)
	public AvailableInput toPredict;

	
	public static class AvailableInput {
		@Option(
				names = {"-s", "--smiles"}, 
				description = "SMILES to predict, can optionally include a blank space and a molecule name/identifier",
				paramLabel = ArgumentType.SMILES)
		public String smilesToPredict;

		@Option(
				names = { "-p", "--predict-file" }, 
				description = "File to predict. Accepted formats are CSV, SDF or JSON. Run "+ParameterUtils.RUN_EXPLAIN_ANSI_ON+"explain chem-formats"+ParameterUtils.ANSI_OFF+" to get further info.",
				parameterConsumer = ChemFileConverter.class,
				paramLabel = ArgumentType.CHEM_FILE_ARGS
				)
		public ChemFile predictFile;
	}
}

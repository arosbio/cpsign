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

import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

public class InputChemFilesMixin {

	@Option(names = { "-td", "--train-data" }, 
			parameterConsumer = ChemFileConverter.class,
			description = "File with molecules in CSV, SDF or JSON format. run "+ParameterUtils.RUN_EXPLAIN_ANSI_ON+"explain chem-formats"+ParameterUtils.ANSI_OFF+" to get further info.",
			paramLabel = ArgumentType.URI_OR_PATH)
	public ChemFile trainFile;

	@Option(names = { "-md", "--model-data" }, 
			parameterConsumer = ChemFileConverter.class,
			description = "File with molecules that exclusively should be used for training the scoring algorithm. In CSV, SDF or JSON format",
			hidden=true,
			paramLabel = ArgumentType.URI_OR_PATH)
	public ChemFile properTrainExclusiveFile;

	@Option(names = { "-cd", "--calibration-data" },
			parameterConsumer = ChemFileConverter.class,
			description = "File with molecules that exclusively should be used for calibrating predictions. In CSV, SDF or JSON format",
			hidden=true,
			paramLabel = ArgumentType.URI_OR_PATH)
	public ChemFile calibrationExclusiveTrainFile;

	@Mixin
	public ModelingPropertyMixin endpointOpt = new ModelingPropertyMixin();

	@Mixin
	public ClassificationLabelsMixin labelsOpt = new ClassificationLabelsMixin();

	@Option(names= {"--early-termination-after"}, 
			description = "Early termination stops loading records once passing this number of failed records and fails execution of the program. "
					+ "Specifying a value less than 0 means there is no early termination and loading will continue until finished. "
					+ "This number of failures are the threshold applied to each of the three levels of processing (i.e. reading molecules from file, "
					+ "getting the endpoint activity from the records and CDK-configuration/HeavyAtomCount/descriptor-calculation)")
	public int maxFailuresAllowed = -1;

	@Option(names = "--min-hac",
			description="Specify the minimum allowed Heavy Atom Count (HAC) allowed for the records. This serves a s sanity check that parsing from file has been OK.%nDefault: ${DEFAULT-VALUE}")
	public int minHAC = 5;

	@Option(names = {"--list-failed"},
			description = "List *all* failed molecules, ranging from invalid records, molecules removed due to Heavy Atom Count, to failures at descriptor calculation. "+
			"The default is otherwise to only list the summary of failed records.")
	public boolean listFailedMolecules = false;

}

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

import picocli.CommandLine.Option;

public class PercentilesMixin {

	@Option(names={"--percentiles"}, 
			description = "The maximum number of molecules used for calculating percentiles. Calculating percentiles is @|bold very time time-consuming|@ "+
					"is thus turned of by default. Turn on calculation if image rendering or gradients will be used.%n"+
					ParameterUtils.DEFAULT_VALUE_LINE,
			defaultValue = "0",
			paramLabel = ArgumentType.INTEGER
			)
	public int maxNumMolsForPercentiles = 0;

	@Option(names = { "--percentiles-data" },
			paramLabel = ArgumentType.CHEM_FILE_ARGS,
			parameterConsumer = ChemFileConverter.PercentilesDataConverter.class,
			description = "File with molecules that should be used for calculating percentiles (used when calculating gradients "
					+ "and generating images). Required if the --percentiles is specified with a number greater than 0.")
	public ChemFile percentilesFile;

}

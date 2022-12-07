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

import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;

/**
 * Note that this is only used for displaying these parameters 
 * on the usage texts, the actually used config is found in CPSignApp
 * and pre-validated before executing the command and validating remaining arguments
 */
public class LogfileMixin {
	

	@ArgGroup(exclusive = true, multiplicity = "0..1")
	public ExclusiveOptions exclusive = new ExclusiveOptions();

	public static class ExclusiveOptions {
		@Option(names = "--logfile", 
				description = "Path to a user-set logfile, specific for each run",
				paramLabel = ArgumentType.FILE_PATH,
				negatable = false)
		public String logfile;

		@Option(names= "--no-logfile",
				description = "Write no logfile",
				negatable = false)
		public boolean turnOfLogging = false;
	}

}

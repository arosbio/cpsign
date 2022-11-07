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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIConsole.VerbosityLvl;

import picocli.CommandLine.Option;

public class ConsoleVerbosityMixin {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleVerbosityMixin.class);

	@Option(names = { "-q","--quiet", "--silent" }, 
			description = "Silent/quiet mode. Only prints crucial results/warnings or output to logfile if specified",
			required = false
			)
	public boolean silentMode;

	@Option(names = {"-v","--verbose"}, 
			description = "Verbose mode",
			required = false)
	public boolean verboseMode;

	public void configConsole() {

		if (verboseMode && silentMode)
			throw new IllegalArgumentException("Options --quiet and --verbose cannot be given at the same time");

		CLIConsole cons = CLIConsole.getInstance();
		if (silentMode) {
			LOGGER.debug("running in SILENT_MODE");
			cons.setVerbosity(VerbosityLvl.SILENT);
		} else if (verboseMode) {
			LOGGER.debug("running in VERBOSE_MODE");
			cons.setVerbosity(VerbosityLvl.VERBOSE);
		} else {
			LOGGER.debug("running in DEFAULT_MODE (console verbosity)");
		}

	}

}

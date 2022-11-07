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

import java.util.List;

import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

public class EchoMixin {

	@Spec(Spec.Target.MIXEE) CommandSpec mixee;

	@Option(names = "--echo", 
			description = "Echo the input arguments given to CPSign"
			)
	public void echo(boolean b) {
		if (b) {
			List<String> args = mixee.commandLine().getParseResult().originalArgs();
			StringBuilder sb = new StringBuilder("%nRunning command:%n");
			for (String arg : args) {
				if (arg.split(" ").length > 1) {
					sb.append('"');
					sb.append(arg);
					sb.append('"');
				} else {
					sb.append(arg);
				}

				// print a space between each parameter
				sb.append(' ');
			}
			sb.append("%n");

			CLIConsole.getInstance().println(sb.toString(), PrintMode.NORMAL);
		}
	}

}

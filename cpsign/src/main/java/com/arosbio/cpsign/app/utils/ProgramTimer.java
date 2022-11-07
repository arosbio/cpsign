/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.Stopwatch;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;

public class ProgramTimer {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ProgramTimer.class);
	
	private Stopwatch fullTimeWatch = new Stopwatch();
	private Stopwatch sectionWatch = new Stopwatch();
	private CLIConsole console;
	private boolean printInConsole;
	
	public ProgramTimer(boolean printInConsole, CLIConsole console) {
		this.console = console;
		this.printInConsole = printInConsole;
		fullTimeWatch.start();
		sectionWatch.start();
	}
	
	public void setPrintInConsole(boolean print) {
		this.printInConsole = print;
	}
	
	public void endSection() {
		sectionWatch.stop();
		String t = sectionWatch.toString();
		
		if (printInConsole) {
			console.println("(%s)", PrintMode.NORMAL, t);
		} else {
			LOGGER.debug("Section finished in {}", t);
		}
		
		// Restart the timer
		sectionWatch.start();
	}
	
	public void endProgram() {
		fullTimeWatch.stop();
		String t = fullTimeWatch.toString();
		
		if (printInConsole) {
			console.println("Program finished in %s", PrintMode.NORMAL, t);
		} else {
			LOGGER.debug("Program finished in {}", t);
		}
	}
	
	

}

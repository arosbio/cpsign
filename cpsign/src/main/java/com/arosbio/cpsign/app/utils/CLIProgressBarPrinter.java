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

import com.arosbio.cpsign.out.OutputNamingSettings.PB;

import me.tongfei.progressbar.ConsoleProgressBarConsumer;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;

public class CLIProgressBarPrinter implements CLIProgressBar {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CLIProgressBarPrinter.class);
	
	private final ProgressBar progressBar;
	private final String sectionFormat = "%11s";
	private final int UPDATE_INTERVAL_MILLIS = 500;
	
	public CLIProgressBarPrinter(
			String programName, 
			int numSteps,
			boolean asUnicode) {
		java.util.logging.Logger logger = java.util.logging.Logger.getLogger("org.jline");
		logger.setLevel(java.util.logging.Level.OFF);

		progressBar = new ProgressBarBuilder()
				.setTaskName(programName)
				.setInitialMax(numSteps)
				.setUpdateIntervalMillis(UPDATE_INTERVAL_MILLIS)
				.setStyle((asUnicode? ProgressBarStyle.COLORFUL_UNICODE_BLOCK : ProgressBarStyle.ASCII))
				.setConsumer(new ConsoleProgressBarConsumer(System.err))
				.setMaxRenderedLength(CLIConsole.DEFAULT_CLI_CHARS_WIDTH * 2)
				.build();
		
		// Init the progress bar
		progressBar.setExtraMessage(String.format(sectionFormat, PB.INIT_PROGRAM_PROGRESS));

	}
	
	public void stepProgressAndSetTask(String task) {
		progressBar.step();
		setCurrentTask(task);
	}
	
	public void stepProgress() {
		progressBar.step();
	}
	@Override
	public void stepProgress(int numSteps) {
		progressBar.stepBy(numSteps);
	}

	public void setCurrentTask(String task) {
		progressBar.setExtraMessage(String.format(sectionFormat, task));
	}

	public void updateNumProgressSteps(int numSteps) {
		progressBar.maxHint(numSteps+1); // Always add 1 for the initial step to start at 1
	}

	@Override
	public void addAdditionalStep() {
		progressBar.maxHint(progressBar.getMax()+1);
	}

	@Override
	public void addAdditionalSteps(int extraSteps) {
		progressBar.maxHint(progressBar.getMax()+extraSteps);
	}
	
	public void finish() {
		progressBar.setExtraMessage(String.format(sectionFormat, PB.FINISHED_PROGRAM_PROGRESS));
		progressBar.stepTo(progressBar.getMax());
		progressBar.close();
	}

	public void fail() {
		progressBar.setExtraMessage(String.format(sectionFormat, PB.FAILED_PROGRAM_PROGRESS));
		progressBar.close(); // Just close - do not fill all of progress
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			LOGGER.debug("Tried to sleep to let progress printing finish", e);
		}
	}

}

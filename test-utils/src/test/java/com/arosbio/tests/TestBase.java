/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.tests;

import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.Before;
import org.junit.Rule;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

public abstract class TestBase {

	public static final String TEST_RESOURCE_BASE_DIR = "src/test/resources";

	@Rule public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
	@Rule public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

	public static final PrintStream SYS_OUT = System.out;
	public static final PrintStream SYS_ERR = System.err;
	public static final Charset STANDARD_CHARSET = StandardCharsets.UTF_8;

	public static final double DEFAULT_CALIBRATION_RATIO =.2;
	public static final int DEFUALT_NUM_CV_FOLDS = 10;
	public static final int DEFAULT_NUM_MODELS = 5;

	public static void resetOutputs(){
		System.setErr(SYS_ERR);
		System.setOut(SYS_OUT);
	}

	@Before
	public void betweenMethods() {
		systemErrRule.clearLog();
		systemOutRule.clearLog();	
	}

	public void printLogs() {
		SYS_OUT.println(systemOutRule.getLog());
		SYS_ERR.println(systemErrRule.getLog());
	}

	public void clearLogs() {
		systemErrRule.clearLog();
		systemOutRule.clearLog();
	}


}

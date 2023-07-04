/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.tests.suites.CLITest;

@Category(CLITest.class)
public class TestCLIExplainArgument extends CLIBaseTest {
	
	
	@BeforeClass
	public static void setCLIWidth() {
		System.setProperty("picocli.usage.width", "150");
		System.setProperty("jansi.mode", "force");
	}
	
	@Test
	public void TestPrintTopLevelUsage() throws Exception {
		System.out.println("TOP LEVEL USAGE");
		mockMain(new String[] {
				ExplainArgument.CMD_NAME,
		});
//		printLogs();
	}
	
//	@Test
	public void testAbbreviationChem() throws Exception {
//		LoggerUtils.setDebugMode(SYS_OUT);
		// This should work - but doesn't!
		mockMain("explain","chem");
		// printLogs();
	}
	
	@Test
	public void TestPrintChemFileFormats() throws Exception{
		System.out.println("CHEM-FILE ARGUMENT");
		mockMain(new String[] {
				ExplainArgument.CMD_NAME, "chemical-file"
		});
//		printLogs();
	}
	
	@Test
	public void TestPrintClassLabels() throws Exception{
		System.out.println("CLASS-LABELS ARGUMENT");
		mockMain(new String[] {
				ExplainArgument.CMD_NAME, "labels"
		});
//		printLogs();
	}
	
	@Test
	public void TestPrintExclusiveDS() throws Exception{
		System.out.println("EXCLUSIVE DATASETS ARGUMENT");
		mockMain(new String[] {
				ExplainArgument.CMD_NAME, "exclusive-data"
		});
//		printLogs();
	}
	
	@Test
	public void TestPrintNCM() throws Exception{
		System.out.println("NCM ARGUMENT");
		mockMain(new String[] {
				ExplainArgument.CMD_NAME, "ncm", "--info", "classification"
		});
//		printLogs();
	}
	
	@Test
	public void TestPrintNCM2() throws Exception{
		System.out.println("nonconf ARGUMENT");
		mockMain(new String[] {
				ExplainArgument.CMD_NAME, "nonconf-measure"
		});
//		printLogs();
	}
	
	@Test
	public void TestPrintDescriptors() throws Exception{
		System.out.println("explain: descriptors");
		
		mockMain(new String[] {
				ExplainArgument.CMD_NAME, "descriptor", "--list" ,"physchem"//"fps" //"--info"
		});
		// printLogs();
		Assert.assertTrue(systemErrRule.getLog().isEmpty());
	}
	
	@Test
	public void TestPrintTransformers() throws Exception{
		System.out.println("explain: transform");
		
		mockMain(new String[] {
				ExplainArgument.CMD_NAME, "transform", // "--list" , "feat_select"
		});
		
		Assert.assertTrue(systemErrRule.getLog().isEmpty());
		// printLogs();
	}
	
	@Test
	public void TestPrintMLAlgs() throws Exception {
		System.out.println("explain: scorer");
		
		mockMain(new String[] {
				ExplainArgument.CMD_NAME, "scorer", "--info","--list", "regression", "classification"
		});
		
		Assert.assertTrue(systemErrRule.getLog().isEmpty());
//		printLogs();
	}
	
	@Test
	public void TestPrintSyntax() throws Exception{
		System.out.println("explain: syntax");
		
		mockMain(new String[] {
				ExplainArgument.CMD_NAME, "syntax"
		});
		
		Assert.assertTrue(systemErrRule.getLog().isEmpty());
//		printLogs();
	}
	
	@Test
	public void TestPrintSyntax2() throws Exception{
		System.out.println("explain :-syntax");
		
		mockMain(new String[] {
				ExplainArgument.CMD_NAME, ":-syntax"
		});
		
		Assert.assertTrue(systemErrRule.getLog().isEmpty());
//		printLogs();
	}
	
	
	@Test
	public void testSamplingStrategy() throws Exception {
		System.out.println("explain samping-strategy");
		mockMain(new String[] {
				ExplainArgument.CMD_NAME, "sampling"
		});
		
		Assert.assertTrue(systemErrRule.getLog().isEmpty());
//		printLogs();
	}
	
	@Test
	public void testTestingStrategy() throws Exception {
		System.out.println("explain testing-strategy");
		mockMain(new String[] {
				ExplainArgument.CMD_NAME, "testing"
		});
		
		Assert.assertTrue(systemErrRule.getLog().isEmpty());
		// printLogs();
	}
	
	@Test
	public void testTuneParams() throws Exception {
		mockMain(new String[] {
				ExplainArgument.CMD_NAME, "tune-par", //"--help"
		});
		
		Assert.assertTrue(systemErrRule.getLog().isEmpty());
		// printLogs();
	}
	
	@Test
	public void testMetrics() {
		mockMain(new String[] {
				ExplainArgument.CMD_NAME, "metr", "--list", "cp-classific, venn-abers"
		});
		Assert.assertTrue(systemErrRule.getLog().isEmpty());
//		printLogs();
	}
	
	/*
	 * This test-case fails due to Jansi/Picocli problem when rendering the ANSI escape codes. perhaps giving it too much at the same time?
	 */
	@Test
	public void testListNumbersSyntax() {
		
		mockMain(ExplainArgument.CMD_NAME,"list-syntax"); //, "--test", "0:5:2:b=10"); //, "--test", "0.01:0.99:0.01");
		Assert.assertTrue(systemErrRule.getLog().isEmpty());
		String log = systemOutRule.getLog();
		String[] splits = log.split("LIST NUMBERS SYNTAX");
		Assert.assertEquals("Found the bug causing issues with CLI output - bug in both Jansi and Picocli",2, splits.length);
		// printLogs();
		clearLogs();
		mockMain(ExplainArgument.CMD_NAME,"list-syntax","--test","-1,b");
		String errTxt=systemErrRule.getLog();
		Assert.assertTrue(errTxt.length()>20);
		Assert.assertTrue(errTxt.toLowerCase().contains("invalid"));
		
	}

	@Test
	public void testChemFilters() {
		mockMain(ExplainArgument.CMD_NAME, "chem-filter");
		Assert.assertTrue(systemErrRule.getLog().isEmpty());
		// printLogs();
	}
}

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app.cli;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.cpsign.app.CLIBaseTest;
import com.arosbio.cpsign.app.CPSignApp;
import com.arosbio.cpsign.app.ExitStatus;
import com.arosbio.cpsign.app.Precompute;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.utils.TestUtils.StringOutputStream;

import picocli.CommandLine;

@Category(CLITest.class)
public class TestUsageTexts extends CLIBaseTest {

	@Test
	public void testAbbrevSubCommandTooShort() {
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("precompute","cpsign --help"));
		mockMain("pr");
	}
	
	@Test
	public void testAbbrevSubCommand() {
		mockMain("precomp");
//		printLogs();
	}
	@Test
	public void testNoArgs() {
		System.out.println("TEST: no arguments:");
		mockMain(new String[] {});
		// printLogs();
	}

	@Test
	public void testVersion() {
		// Test without sub-command
		StringOutputStream sos = new StringOutputStream();
		PrintStream original = System.out;
		try{
			System.setOut(new PrintStream(sos,true));
			mockMain("-V");

			// test using a sub-command and then -V
			StringOutputStream sos2 = new StringOutputStream();
			System.setOut(new PrintStream(sos2, true));
			mockMain(Precompute.CMD_NAME, "-V");

			
			Assert.assertEquals(sos.toString(), sos2.toString());
		} finally {
			System.setOut(original);
		}
	}

	@Test
	public void testSingleProgram() {
//		mockMain("explain",":-syntax");
		// mockMain("validate"); //"tune-scorer");
		mockMain("transform");
		// printLogs();
	}

	@Test
	public void testAllPrograms() {
		Map<String,CommandLine> cmds = new CommandLine(new CPSignApp()).getSubcommands();
		// System.out.println(cmds);

		Assert.assertTrue("There are 16 programs, some with aliases (one hidden from public CLI)",cmds.size()>16); 
		Assert.assertEquals("num unique subcommands is 17! 16 public ones and one hidden from public CLI",17, new HashSet<>(cmds.values()).size()); // get the number of unique subcommands

		for (String cmd : cmds.keySet()) {
			System.out.println("Test: " + cmd);
			mockMain(new String[] {cmd});
		}
		// printLogs();

	}

	@Test
	public void testFaultyCMD() {
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		System.out.println("TEST: blargh");
		mockMain(new String[] { "blargh"});
	}
	
	@Test
	public void printFilterDatasetUsage() {
		mockMain("filter-data","-h");
//		printLogs();
	}

	@Test
	public void testGenerateKey() {
		// mockMain("generate-key", "ls");
		mockMain("generate-key");
		// printLogs();
	}

}

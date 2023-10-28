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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.contrib.java.lang.system.Assertion;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.tools.ILoggingTool;
import org.openscience.cdk.tools.LoggingToolFactory;

import com.arosbio.chem.logging.DevNullLoggingTool;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.cpsign.app.params.mixins.ClassificationLabelsMixin;
import com.arosbio.cpsign.app.params.mixins.CompoundsToPredictMixin;
import com.arosbio.cpsign.app.params.mixins.ConfidencesListMixin;
import com.arosbio.cpsign.app.params.mixins.ValidationPointsMixin;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.utils.TestUtils;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Category(CLITest.class)
public class TestGenericAppFunctionality extends CLITestReqOutputDir {

	CSVCmpdData solu10 = TestResources.Reg.getSolubility_10();

	final String RUN_CONFIG_PATH = "/runconfigs/precomp_reg.txt";
	final String RUN_CONFIG_SPLIT1_PATH = "/runconfigs/precomp_reg_split1.txt";
	final String RUN_CONFIG_SPLIT2_PATH = "/runconfigs/precomp_reg_split2.txt";
	final static boolean PRINT_USAGE = false;


	@Before
	public void resetLogging() throws Exception{
		LoggerUtils.reloadLoggingConfig();
	}

	

	class RmFiles implements Assertion {

		Path [] files;
		public RmFiles(Path...files) {
			this.files = files;
		}

		@Override
		public void checkAssertion() throws Exception {
			if (files != null) {
				for (Path f: files) {
					if (f != null)
						java.nio.file.Files.deleteIfExists(f);
				}
			}
		}
	}



	@Test
	public void testSilentMode() throws Exception {

		//Create temp files for model and signatures
		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.jar"); 

		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_REGRESSION,
				"-td", solu10.format(), solu10.uri().toString(),
				"-pr", solu10.property(), 
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "dsf",
				"--silent"
		});

		String trainOutput = systemOutRule.getLog().toString();
		String trainErrOutput = systemErrRule.getLog().toString();
		//		original.println(trainOutput);
		Assert.assertTrue(trainOutput.isEmpty());
		Assert.assertTrue(trainErrOutput.isEmpty());

	}

	@Test
	public void testSilentVerboseExclusive() throws IOException {
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);

		mockMain( new String[] {
				Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_REGRESSION,
				"-td", solu10.format(), solu10.uri().toString(),
				"-pr", solu10.property(),
				"-mo", "somefile.jar",
				"--silent",
				"--verbose",
				"--echo"
		});
	}

	@Test
	public void testGetVersion() throws Exception {
		mockMain("-V");
		// printLogs();
	}

	@Test
	public void testECHO_command() throws Exception {
		File tmpFile = TestUtils.createTempFile("tmp", ".jar");
		String[] args = new String[] {
				Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_REGRESSION,
				"-td", solu10.format(), solu10.uri().toString(),
				"-pr", solu10.property(),
				"-mo", tmpFile.getAbsolutePath(),
				"--echo"
		};

		try{
			mockMain(args);
		} catch(Exception e){
			e.printStackTrace(SYS_ERR);
			Assert.fail();
		}
		String output = systemOutRule.getLog().toString();

		String echoLine = output.split("\n")[2];
		//		original.println(echoLine);

		for(int i=0; i<args.length; i++)
			Assert.assertTrue(echoLine.contains(args[i]));

	}

	@Test
	public void testECHO_command_Missing_input() throws Exception {
		//		printOutput();
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		File tmpFile = TestUtils.createTempFile("tmp", ".jar");

		String[] args = new String[] {
				Train.CMD_NAME,
				"-pt", ""+2,
				"-td", "no_data",
				"-sc", "linearSVR",
				"-mo", tmpFile.getAbsolutePath(),
				"-mn", "dsf",
				"--echo"
		};
		try{
			mockMain(args);
			Assert.fail("Should fail!");
		} catch(Exception e){
			//			e.printStackTrace(original_err);
		}
		String output = systemOutRule.getLog().toString();

		String echoLine = output.split("\n")[2];

		TestUtils.assertTextContainsIgnoreCase(echoLine, args);

		//		printLogs();
	}

	@Test
	public void testNoLogfile() throws Exception {
		File modelFile =TestUtils.createTempFile("acpmodel", ".svm.jar");

		// Get the standard logfile
		File logFile = new File(new File("").getAbsoluteFile(), "cpsign.log"); 
		int initLength = FileUtils.readFileToString(logFile, StandardCharsets.UTF_8).length();
		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_REGRESSION,
				"-td", solu10.format(), solu10.uri().toString(),
				"-pr", solu10.property(),
				"-mo", modelFile.getAbsolutePath(),
				"--no-logfile"
		});

		Assert.assertEquals("Standard logfile should have the same size as before the run",initLength, FileUtils.readFileToString(logFile, StandardCharsets.UTF_8).length());

	}

	@Test
	public void testExtraLogging()throws Exception{

		//Create temp files for model and signatures
		File modelFile =TestUtils.createTempFile("acpmodel", ".svm.jar");
		File tmpDir = java.nio.file.Files.createTempDirectory("log").toFile();
		String customLogFile = new File(tmpDir,"logs/mylog.log").getAbsolutePath();
		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_REGRESSION,
				"-td", solu10.format(), solu10.uri().toString(),
				"-pr", solu10.property(),
				"-mo", modelFile.getAbsolutePath(),
				"--logfile", customLogFile,
				"--echo",
		});

		// Make sure that stuff is written to the file!
		Assert.assertTrue(TestUtils.countLines(customLogFile)> 40); // Should be a lot of lines in the logfile!

	}


	@Test
	public void testExtraLoggingTrain() throws Exception{

		//Create temp files for model and signatures
		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.jar");
		// String customLogFile = "someFolder/train.log";
		File tmpDir = java.nio.file.Files.createTempDirectory("logging").toFile();
		String customLogFile = new File(tmpDir,"logs/mylog.log").getAbsolutePath();
		mockMain(new String[] {
				Train.CMD_NAME,
				"--seed", "961387310",
				"-ds", PrecomputedDatasets.Classification.getAmes123().getAbsolutePath(),
				"-mo", modelFile.getAbsolutePath(),
				"--logfile", customLogFile,
				"--pvalue-calc", "linear-interpolation",
				"--sampling-strategy","RandomStratified:numSamples=20",
				"--echo",
				"--time"
		});
		// customLogFile = new File(new File("").getAbsolutePath(),customLogFile).getAbsolutePath();
		// System.err.println("corrected logfile-path: " + customLogFile);
		// Make sure that stuff is written to the file!
		Assert.assertTrue(TestUtils.countLines(customLogFile)> 40); // Should be a lot of lines in the logfile!

		// printLogs();
	}



	@Test
	public void TestWithConfigsFromFileAbsolutePath() throws Exception{
		clearTestOutDir();
		exit.checkAssertionAfterwards(new PrintSysOutput(PRINT_USAGE));

		// Absolute path to config file
		mockMain(new String[] {
				"@"+TestUtils.getPath(RUN_CONFIG_PATH)
		});
	}

	//	@Test
	public void checkCurrentLocation() throws Exception {
		SYS_ERR.println(new File("").getAbsolutePath());
	}

	@Test
	public void TestWithConfigsFromFileRelativePath() throws Exception{
		clearTestOutDir();
		// Relative path (which is valid)
		String relativePath = "./"+TEST_RESOURCE_BASE_DIR+RUN_CONFIG_PATH;

		mockMain(new String[] {
				"@"+relativePath
		}); 
	}

	@Test
	public void TestWithConfigsFromFileRelativePathUsrHome() throws Exception{

		Path usrHome = null;
		// Check if there is a user-home on the system
		try {
			usrHome =  SystemUtils.getUserHome().toPath();
		} catch (NullPointerException npe){
			// if no usrHome - then do not run this test!
			return; 
		}

		// The absolute path for the run-config file
		Path absPath = new File(TEST_RESOURCE_BASE_DIR+RUN_CONFIG_PATH).getAbsoluteFile().toPath();
		// Make it relative to user-home and add the @ and ~/ for user-home 
		String pathRelToUserHome = "@~/"+usrHome.relativize(absPath).toString();

		clearTestOutDir();
		exit.checkAssertionAfterwards(new PrintSysOutput(PRINT_USAGE));

		// User-home absolute path
		systemErrRule.clearLog();
		mockMain(new String[] {
				pathRelToUserHome, 
				"--time"
		});
	
	}

	@Test
	public void TestWithConfigsFromFileRelativeJCommander() throws Exception{
		clearTestOutDir();
		// A JCommander-Faulty relative path
		String faultyRelativePath = TEST_RESOURCE_BASE_DIR+RUN_CONFIG_PATH;
		systemErrRule.clearLog();
		mockMain(new String[] {
				"@"+faultyRelativePath
		}); 
	}


	@Test
	public void TestWithConfigsFromFileDottedPath() throws Exception{

		clearTestOutDir();
		File childModuleDir = new File("").getAbsoluteFile();
		File repoDir = childModuleDir.getParentFile();

		// with double dots
		String faultyRelativePath = String.format("../../%s/%s/%s%s", 
			repoDir.getName(),
			childModuleDir.getName(),
			TEST_RESOURCE_BASE_DIR,RUN_CONFIG_PATH);
		systemErrRule.clearLog();
		mockMain(new String[] {
				"@"+faultyRelativePath
		}); 

	}

	@Test
	public void TestFailAtFaultyPath() throws Exception {
		final String faultyPath="/this/does/not/exist.txt";
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString(faultyPath));
		exit.checkAssertionAfterwards(new PrintSysOutput(PRINT_USAGE));

		// A truly faulty path
		systemErrRule.clearLog();
		mockMain(new String[] {
				"@"+faultyPath
		});

	}


	@Test
	public void TestWithSplittedConfigFile() throws Exception{
		clearTestOutDir();
		String path1 = "/runconfigs/precomp_reg_split1.txt";
		String path2 = "/runconfigs/precomp_reg_split2.txt";

		// Absolute path to config file
		String configFile1 = TestUtils.getFile(path1).getAbsolutePath();
		String configFile2 = TestUtils.getFile(path2).getAbsolutePath(); 
		mockMain(new String[] {
				"@"+configFile1,
				"@"+configFile2,
		});
	}

	@Test
	public void TestNegativeLabelValues() throws Exception {
		CmdWithLabels cmd = new CmdWithLabels();
		CommandLine cl = new CommandLine(cmd);
		cl.setTrimQuotes(true);
		cl.parseArgs("--labels", "-1, 1");
		//		SYS_ERR.println(cmd.labelsMix.labels);

		Assert.assertEquals("-1",cmd.labelsMix.labels.get(0));
		Assert.assertEquals("1",cmd.labelsMix.labels.get(1));


	}

	@Test
	public void TestNegativeLabelValues_2() throws Exception {
		CmdWithLabels cmd = new CmdWithLabels();
		CommandLine cl = new CommandLine(cmd);
		cl.setTrimQuotes(true);
		cl.parseArgs("--labels", "-1 1");
		//		SYS_ERR.println(cmd.labelsMix.labels);

		Assert.assertEquals("-1",cmd.labelsMix.labels.get(0));
		Assert.assertEquals("1",cmd.labelsMix.labels.get(1));


	}

	private static class CmdWithLabels{
		@Mixin
		ClassificationLabelsMixin labelsMix;
	}

	@Test
	public void testLabelsWithSpaces() throws Exception {
		CmdWithLabels cmd = new CmdWithLabels();
		CommandLine cl = new CommandLine(cmd);
		cl.setTrimQuotes(true);
		cl.parseArgs("--labels", "\"Label A\" \"Label B\"");

		Assert.assertEquals("Label A", cmd.labelsMix.labels.get(0));
		Assert.assertEquals("Label B", cmd.labelsMix.labels.get(1));
		//		for (String l : cmd.labelsMix.labels) {
		//			SYS_ERR.println(l);
		//		}
	}

	@Test
	public void testLoggingTool() {
		LoggingToolFactory.setLoggingToolClass(DevNullLoggingTool.class);
		//		SYS_ERR.println(LoggingToolFactory.getLoggingToolClass());
		ILoggingTool tool = LoggingToolFactory.createLoggingTool(CPSignApp.class);
		tool.debug("hej");
		// printLogs();
		//		LoggingToolFactory.set
	}

	@Command()
	private class TestArgs {

		@Option(names="--str-list", split=ParameterUtils.SPLIT_WS_COMMA_REGEXP, arity="1..*") //, splitter=MultiArgumentSplitter_OLD.class)
		public List<String> stringList;

		@Option(names="--float-list", split=ParameterUtils.SPLIT_WS_COMMA_REGEXP, arity="1..*") 
		//				listConverter = ListOrRangeConverter.class)
		public List<Double> doubles;

		@Option(names="--ints", split=ParameterUtils.SPLIT_WS_COMMA_REGEXP, arity="1..*")
		//		, splitter=MultiArgumentSplitter_OLD.class)
		public List<Integer> ints;
	}

	@Test
	public void testConfs() {
		ConfidencesListMixin obj = new ConfidencesListMixin();
		new CommandLine(obj).parseArgs("--confidences","0.5, 0.8, 0:1:0.05");
		Assert.assertEquals(.5, obj.confidences.get(0),0.0001);
		Assert.assertEquals(.8, obj.confidences.get(1),0.0001);
		Assert.assertEquals(0., obj.confidences.get(2),0.0001);
		Assert.assertEquals(1, obj.confidences.get(obj.confidences.size()-1),0.0001);
		Assert.assertEquals("first 2 value, then 0-1 with 0.05 increments (21 more)",23, obj.confidences.size());
		//		SYS_ERR.println(obj.confidences);
	}



	

	@Test
	public void testRangeInput() {
		//		LoggerUtils.setDebugMode();
		TestArgs a = new TestArgs();
		new CommandLine(a).parseArgs("--float-list", "1 2 3 4");

		//		System.err.println(a.doubles);
		Assert.assertEquals(Arrays.asList(1.0,2.,3.,4.), a.doubles);
	}

	@Test
	public void testPredWidths() throws Exception {
		ValidationPointsMixin vpm = new ValidationPointsMixin();
		new CommandLine(vpm).parseArgs("--prediction-widths","2.5", "1.5");
		Assert.assertEquals(Arrays.asList(2.5,1.5), vpm.predictionWidths);
	}

	@Test
	public void testSMILESInput() throws Exception {
		CompoundsToPredictMixin toPred = new CompoundsToPredictMixin();
		new CommandLine(toPred).parseArgs("--smiles", TEST_SMILES, "-p", "sdf", TestResources.Cls.getAMES_10().uri().toString());
		Assert.assertEquals(TEST_SMILES, toPred.toPredict.smilesToPredict);
		Assert.assertTrue(toPred.toPredict.predictFile != null);
	}


	@Test
	public void testIntsList() throws Exception {
		List<Integer> trueList = Arrays.asList(1,2,3,4);
		TestArgs a = new TestArgs();

		new CommandLine(a).parseArgs("--ints", "1 2 3 4");
		Assert.assertEquals(trueList, a.ints);

		a = new TestArgs();
		new CommandLine(a).parseArgs("--ints","1,2,3,4");
		Assert.assertEquals(trueList, a.ints);

		// Semicolons no longer allowed
		//		a = new TestArgs();
		//		new CommandLine(a).parseArgs("--ints","1;2;3;4");
		//		Assert.assertEquals(trueList, a.ints);

		a = new TestArgs();
		new CommandLine(a).parseArgs("--ints","1\n2\t3,4");
		Assert.assertEquals(trueList, a.ints);
	}

	@Test
	public void testNegativeInts() {
		List<Integer> trueList = Arrays.asList(-1,-2,3,-4);


		//		LoggerUtils.setDebugMode();
		TestArgs a = new TestArgs();
		new CommandLine(a).parseArgs("--ints","-1 -2 3\t-4");
		Assert.assertEquals(trueList, a.ints);

		// With quotes (old CPSignApp)
		a = new TestArgs();
		new CommandLine(a).parseArgs("--ints","-1 -2 3\t-4");
		Assert.assertEquals(trueList, a.ints);

		// Semicolons no longer allowed
		//		a = new TestArgs();
		//		new CommandLine(a).parseArgs("--ints","-1;-2;3;-4");
		//		Assert.assertEquals(trueList, a.ints);
	}


	@Test
	public void testPaths() {
		TestArgs a = new TestArgs();
		new CommandLine(a).parseArgs("--str-list","\"/some/bad path/file.txt\"");
		Assert.assertEquals(1, a.stringList.size());
		Assert.assertEquals("\"/some/bad path/file.txt\"", a.stringList.get(0));
	}

	@Test
	public void testCorrectFormatting() {
		CLIConsole console = CLIConsole.getInstance("cpsign");
		console.printlnWrappedStdErr(String.format("%n%s%n","This is my pretty long,\nand splitted text"), PrintMode.NORMAL);

		//		printLogs();
	}

}

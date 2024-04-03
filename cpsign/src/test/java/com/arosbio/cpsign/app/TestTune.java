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
import java.util.LinkedHashMap;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.cpsign.app.PrecomputedDatasets.Classification;
import com.arosbio.cpsign.app.PrecomputedDatasets.Regression;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.cp.nonconf.regression.LogNormalizedNCM;
import com.arosbio.ml.metrics.classification.BrierScore;
import com.arosbio.ml.metrics.cp.classification.BalancedObservedFuzziness;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.utils.TestUtils;

import picocli.CommandLine;
import picocli.CommandLine.Option;

@Category(CLITest.class)
public class TestTune extends CLIBaseTest {

	String smilesLabels = "-1,1";
	String smilesProperty = "class";
	boolean isLibLinear = true;
	double tolerance = 1;
	double conf = 0.75;

	boolean printOutput = false;

	@Test
	public void testNoArgs() throws Exception{
		mockMain(new String[]{
				Tune.CMD_NAME,
		});
	}

	@Test
	public void testWhenHavingMissingValues() throws Exception {
		expectExit(ExitStatus.USER_ERROR);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("missing", "data", "feature", "modeling"));


		try {
			mockMain(new String[] {
					Tune.CMD_NAME,
					"-ds", Classification.getMissingDataDS().getAbsolutePath(),
					"--time",
					"-g","C"
			}
					);
			Assert.fail();

		} catch(Exception e){
		}
		//		printLogs();
	}

	@Test
	public void testBadArgumentsNoSuccessfulCombo(){
		expectExit(ExitStatus.USER_ERROR);
		exit.checkAssertionAfterwards(
				new AssertSysErrContainsString("no", "valid", "parameter", "failed", "training"));
		// exit.checkAssertionAfterwards( new PrintSysOutput());

		try {
			mockMain(new String[] {
					Tune.CMD_NAME,
					"-ds", Classification.getAmes123().getAbsolutePath(),
					"--time",
					"-g=C=-1,-5,-10"
			});
			Assert.fail();

		} catch (Exception e) {
		}
		// printLogs();
	}


	@Test
	public void testFaultyParams() throws Exception{
		//		printOutput();
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("gamma"));

		// Non correct start:step:end
		mockMain(new String[]{
				Tune.CMD_ALIAS,
				"-pt", ACP_CLASSIFICATION_TYPE,
				"--cv-folds", "10",
				"-ds", Classification.getAmes123().getAbsolutePath(),
				//				"-td", SDFile.FORMAT_NAME, AmesBinaryClass.MINI_FILE_PATH,
				//				"-pr", AmesBinaryClass.PROPERTY,
				//				"-l", AmesBinaryClass.LABELS_STRING,
				"--grid=gamma=b2:-1:-2:1.5",
		}
				);

	}

	@Test
	public void TestCVAPClassification() throws Exception {

		File atFile = TestUtils.createTempFile("at-file", ".params");

		try {
			mockMain(
					new String[]{
							Tune.CMD_NAME,
							"-pt", CVAP_CLASSIFICATION_TYPE,
							"--cv-folds", "10",
							"-sc", (isLibLinear? LinearSVC.ALG_NAME : C_SVC.ALG_NAME),
							"-ds", Classification.getAmes123().getAbsolutePath(),
							"-g=GAMMA=0.5, 1.0, 2.0",
							"--grid=COST=-1:1:1",
							"--tolerance", ""+tolerance,
							"-op", "logloss",
							"-rf", "csv",
							"--generate@file", atFile.getAbsolutePath(), 
							"--confidence", ""+conf,
							"--verbose",
					}
					);
		} catch (Exception e) {
			//			e.printStackTrace();
			Assert.fail();
		}


		String contents = FileUtils.readFileToString(atFile, StandardCharsets.UTF_8);
		String[] lines = contents.split("\n");
		Assert.assertTrue(lines.length >= (isLibLinear? 2:4)); // cost or cost+gamma
		for (int i=0; i<lines.length; i++) {
			String l = lines[i];
			if (l.isEmpty())
				continue;
			// every other line should be a parameter-flag, and thus start with -- 
			if (i % 2 == 0) {
				Assert.assertTrue(l.startsWith("--"));
			} else if ( i % 2 == 1) {
				Assert.assertFalse(l.startsWith("--"));
			}
		}

		if (printOutput)
			printLogs();
	}

	@Test
	public void TestCVAPClassificationJSON() throws Exception {

		File atFile = TestUtils.createTempFile("at-file", ".params");

		try {
			mockMain(
					new String[]{
							Tune.CMD_NAME,
							"-pt", CVAP_CLASSIFICATION_TYPE,
							"--cv-folds", "5",
							"-ss", strategy(RANDOM_SAMPLING, 1),
							"-sc", C_SVC.ALG_NAME,
							"-ds", Classification.getAmes123().getAbsolutePath(),
							"-g","GAMMA=\"0.5, 1.0, 2.0\"",
							"-g","COST=b2:-1:1:1",
							"--tolerance", ""+tolerance,
							"-op", "logloss",
							"-rf", "json",
							"--progress-bar",
							"--calc-all",
							"--generate@file", atFile.getAbsolutePath(), 
							"--confidence", ""+conf,
					}
					);
		} catch (Exception e) {
			//			e.printStackTrace();
			Assert.fail();
		}

		String contents = FileUtils.readFileToString(atFile, StandardCharsets.UTF_8);
		String[] lines = contents.split("\n");
		Assert.assertTrue(lines.length >=4);
		for (int i=0; i<lines.length; i++) {
			String l = lines[i];
			if (l.isEmpty())
				continue;
			// every other line should be a parameter-flag, and thus start with -- 
			if (i % 2 == 0) {
				Assert.assertTrue(l.startsWith("--"));
			} else if ( i % 2 == 1) {
				Assert.assertFalse(l.startsWith("--"));
			}
		}
		System.out.println("@File contents: \n" + contents);
		if (printOutput)
			printLogs();
	}

	@Test
	public void TestACPClassification() throws Exception {
		File atFile = TestUtils.createTempFile("at-file", ".params");
		try {
			
			mockMain(
					new String[]{
							Tune.CMD_NAME,
							"-pt", ACP_CLASSIFICATION_TYPE,
							"--cv-folds", "10",
							"-sc", (isLibLinear? LinearSVC.ALG_NAME : C_SVC.ALG_NAME),
							"-ds", Classification.getAmes123().getAbsolutePath(),
							"--grid=GAMMA=0.5, 1.0, 2.0", //-1:1:1",
							"--grid=C=-1:1:1:base=2",
							"--tolerance", ""+tolerance,
							"--opt-metric", "Observed Fuzziness",
							//							"--debug",
							"-rf","csv",
							"--progress-bar",
							"--generate@file", atFile.getAbsolutePath(), 
							"--confidence", ""+conf,
							"-v",
//							"--calc-all"
					}
					);
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail();
		}

		String contents = FileUtils.readFileToString(atFile, StandardCharsets.UTF_8);
		//		SYS_ERR.println("At-FILE contents\n" + contents);
		String[] lines = contents.split("\n");
		Assert.assertTrue(lines.length >= (isLibLinear? 2:4)); // only cost or cost+gamma
		for (int i=0; i<lines.length; i++) {
			String l = lines[i];
			if (l.isEmpty())
				continue;
			// every other line should be a parameter-flag, and thus start with -- 
			if (i % 2 == 0) {
				Assert.assertTrue(l.startsWith("--"));
			} else if ( i % 2 == 1) {
				Assert.assertFalse(l.startsWith("--"));
			}
		}
		System.out.println("@File contents: \n" + contents);
		if (printOutput)
			printLogs();
	}


	@Test
	public void TestACPRegression() throws Exception {
		File atFile = TestUtils.createTempFile("at-file", ".params");
		try {
			mockMain(new String[]{
					Tune.CMD_NAME,
					"-pt", ACP_REGRESSION_TYPE,
					"--cv-folds", "10",
					"-ds", Regression.getSolu100().getAbsolutePath(),
					
					"-ss", strategy(RANDOM_SAMPLING, 1),
					"-sc", (isLibLinear? LinearSVR.ALG_NAME : EpsilonSVR.ALG_NAME),
					"-g=GAMMA=-1:1:1",
					"-g","C=-1:1:1",
					"-g=Epsilon=0.0001, 0.001, 0.01",
					"-g=PvalueCalc=standard,smooth",
					"--tolerance", ""+tolerance,
					"--confidence", ""+conf,
					"--num-results", "-1",
					"--progress-bar",
					"--time",
					"--generate@file", atFile.getAbsolutePath(), 
			}
					);
		} catch (Exception e) {e.printStackTrace();Assert.fail();}

		String contents = FileUtils.readFileToString(atFile, StandardCharsets.UTF_8);
		String[] lines = contents.split("\n");
		Assert.assertTrue(lines.length >=4);
		for (int i=0; i<lines.length; i++) {
			String l = lines[i];
			if (l.isEmpty())
				continue;
			// every other line should be a parameter-flag, and thus start with -- 
			if (i % 2 == 0) {
				Assert.assertTrue(l.startsWith("--"));
			} else if ( i % 2 == 1) {
				Assert.assertFalse(l.startsWith("--"));
			}
		}

		if (printOutput)
			printLogs();
	}

	@Test
	public void TestACPRegressionResultsToFile() throws Exception {
		File resFile = TestUtils.createTempFile("results-file", ".csv");
		try {
			mockMain(new String[]{
					Tune.CMD_NAME,
					"-pt", ACP_REGRESSION_TYPE,
					"--cv-folds", "10",
					"-ds", Regression.getSolu100().getAbsolutePath(),
					"-ss", strategy(RANDOM_SAMPLING, 1),
					"-sc", (isLibLinear? LinearSVR.ALG_NAME : EpsilonSVR.ALG_NAME),
					"--grid=GAMMA=b2:-1:1:1",
					"--grid=C=b2:-1:1:1",
					"--grid=Epsilon=0.0001,0.001,0.01",
					"-g=pvalueCalc=standard smooth",
					"--tolerance", ""+tolerance,
					"--confidence", ""+conf,
					"--num-results", "-1",
					"--result-format", "tsv",
					//					"--progress-bar",
					"--time",
					"--result-output", resFile.getAbsolutePath(), 
					"--verbose"
			}
					);
		} catch (Exception e) {e.printStackTrace();Assert.fail();}

		String contents = FileUtils.readFileToString(resFile, StandardCharsets.UTF_8);

		System.err.println("results:\n" + contents);


		String[] lines = contents.split("\n");

		Assert.assertEquals(3*3*2+1,lines.length); // 3cost x 3eps x2 p-vals + header

		if (printOutput)
			printLogs();
	}

	@Test
	public void TestACPRegressionSingleTestSplit() throws Exception {
		try {
			mockMain(new String[]{
					"tune",
					"-pt", ACP_REGRESSION_TYPE,
					"--cv-folds", "random-split:testFraction=0.3",
					"-ds", Regression.getSolu100().getAbsolutePath(),
					//					"-td", CSVFile.FORMAT_NAME, trainRegFile,
					//					"-pr", RegressionSolubility.PROPERTY,
					"-ss", strategy(RANDOM_SAMPLING, 1),
					"-sc", (isLibLinear? LinearSVR.ALG_NAME : EpsilonSVR.ALG_NAME),
					"-g=GAMMA=b2:-1:1:1",
					"--grid=COST=b2:-1:1:1",
					"--grid=EPSILON=0.0001, 0.001, 0.01",
					"-g=pvaluecalc=standard,smooth",
					"--tolerance", ""+tolerance,
					"--confidence", ""+conf,
					"--progress-bar",
					"--time",
			}
					);
		} catch (Exception e) {e.printStackTrace();Assert.fail();}

		if (printOutput)
			printLogs();
	}

	@Test
	public void TestACPRegressionForBeta() throws Exception {
		File atFile = TestUtils.createTempFile("at-file", ".params");
		try {
			mockMain(new String[]{
					Tune.CMD_NAME,
					"-pt", ACP_REGRESSION_TYPE,
					"--cv-folds", "10",
					"-ds", Regression.getSolu100().getAbsolutePath(),
					"-sc", (isLibLinear? LinearSVR.ALG_NAME : EpsilonSVR.ALG_NAME),
					"-g","GAMMA=b2:-1:1:1",
					"-g","COST=b2:-1:1:1",
					"--nonconf-measure", LogNormalizedNCM.IDENTIFIER,
					"--calc-all",
					"-g=BETA=0 0.3 0.1",
					"--tolerance", ""+tolerance,
					"--confidence", ""+conf,
					"--echo",
					"--generate@file", atFile.getAbsolutePath(), 
					"--verbose"
			}	
					);
		} catch (Exception e) {e.printStackTrace();Assert.fail();}
		String contents = FileUtils.readFileToString(atFile, StandardCharsets.UTF_8);
		String[] lines = contents.split("\n");
		Assert.assertTrue(lines.length >=4);
		for (int i=0; i<lines.length; i++) {
			String l = lines[i];
			if (l.isEmpty())
				continue;
			// every other line should be a parameter-flag, and thus start with -- 
			if (i % 2 == 0) {
				Assert.assertTrue(l.startsWith("--"));
			} else if ( i % 2 == 1) {
				Assert.assertFalse(l.startsWith("--"));
			}
		}

		System.out.println("@File contents: \n" + contents);
		if (printOutput)
			printLogs();
	}

	@Test
	public void TestACPRegressionForPvalueCalc() throws Exception {
		File atFile = TestUtils.createTempFile("at-file", ".params");
		try {
			mockMain(new String[]{
					Tune.CMD_NAME,
					"-pt", ACP_REGRESSION_TYPE,
					"--cv-folds", "10",
					"-ds", Regression.getSolu100().getAbsolutePath(),
					"-sc", (isLibLinear? LinearSVR.ALG_NAME : EpsilonSVR.ALG_NAME),
					"-g","GAMMA=-1:1:1:b2",
					"-g","COST=-1:1:1:b2",
					"--nonconf-measure", LogNormalizedNCM.IDENTIFIER,
					"-g","pvalues=2,3,4",
					"--tolerance", ""+tolerance,
					"--confidence", ""+conf,
					"--generate@file", atFile.getAbsolutePath(),
					"--opt-metric", "RMSE",
			}	
					);
		} catch (Exception e) {
			e.printStackTrace();Assert.fail();
		}
		if (printOutput)
			printLogs();

		String contents = FileUtils.readFileToString(atFile, StandardCharsets.UTF_8);
//				SYS_ERR.println("@File contents:\n" + contents);
		String[] lines = contents.split("\n");
		Assert.assertTrue(lines.length >=4);
		for (int i=0; i<lines.length; i++) {
			String l = lines[i];
			if (l.isEmpty())
				continue;
			// every other line should be a parameter-flag, and thus start with -- 
			if (i % 2 == 0) {
				Assert.assertTrue(l.startsWith("--"));
			} else if ( i % 2 == 1) {
				Assert.assertFalse(l.startsWith("--"));
			}
		}

		// Check that the @-file works!
		systemOutRule.clearLog();
		systemErrRule.clearLog();
		File model = TestUtils.createTempFile("sdfasd", ".txt");
		try {
			mockMain(new String[]{
					Train.CMD_NAME,
					//					"-pt", ACP_REGRESSION_TYPE,
					"-ds", Regression.getSolu100().getAbsolutePath(),
					//					"-td", CSVFile.FORMAT_NAME, trainRegFile,
					//					"-pr", RegressionSolubility.PROPERTY,
					//					"-sc", (isLibLinear? LinearSVR.ALG_NAME: EpsilonSVR.ALG_NAME),
					//					"--nonconf-measure", LogNormalizedNCM.IDENTIFIER,
					"@"+atFile.getAbsolutePath(),
					"-mn", "name",
					"-mo", model.getAbsolutePath(),
					"--echo"
			});
		} catch(Exception e) {
			Assert.fail();
		}

		Assert.assertTrue(systemErrRule.getLog().isEmpty());
		//		printLogs();
	}

	//	@Test
	//	public void getGetSolverTypeByID() {
	//		List<Integer> ids = Arrays.asList(0,1,2,3,4,5,6,7,11,12,13);
	//		for (int id: ids) {
	//			System.err.println("ID: " + id + ": solverType: " + SolverType.getById(TypeUtils.asInt(id)));
	//		}
	//		
	//		List<Integer> svrids = Arrays.asList(11,12,13);
	//		
	//		LinearSVR svr = new LinearSVR();
	//		for (int id: svrids) {
	//			Map<String,Object> params = new HashMap<>();
	//			params.put("solvertype", id);
	//			svr.setConfigParameters(params);
	//		}
	//	}


	@Test
	public void TestACPRegressionForBetaFaultyValues() throws Exception {
		// new implementation simply allows the parameters, but fails during execution
		// when setting illegal params

		File resFile = TestUtils.createTempFile("afdafas", ".csv");

		mockMain(new String[]{
				Tune.CMD_ALIAS,
				"--cv-folds", "5",
				"-ds", Regression.getSolu100().getAbsolutePath(),
				"-pt","acp-regression",
				//				"-td", CSVFile.FORMAT_NAME, trainRegFile,
				//				"-pr", RegressionSolubility.PROPERTY, 
				"-sc", LinearSVR.ALG_NAME,
				"--grid=COST=10,100",
				"--nonconf-measure", "log-normalized",
				//				"--beta-values", "-1 0 0.1 0.3",
				"--grid=BETA=-1 0.1 0.3",
				"--tolerance", ""+tolerance,
				"--confidence", ""+conf,
				"--num-results", "-1",
				"--result-output", resFile.getAbsolutePath(),
				"--result-format", "csv",
		}	
				);

		String contents = FileUtils.readFileToString(resFile, StandardCharsets.UTF_8);

		System.err.println("results:\n" + contents);


		String[] lines = contents.split("\n");

		Assert.assertEquals("header + 2cost x 3 beta (1 of which is failing)",1+2*3, lines.length);  

		//		printLogs();
	}

	@Test
	public void testTCP() throws Exception {
		File atFile = TestUtils.createTempFile("at-file", ".params");
		try {
			mockMain(
					new String[]{
							Tune.CMD_NAME,
							"-pt", TCP_CLASSIFICATION_TYPE,
							"--cv-folds", "RandomSplit",
							"-sc", LinearSVC.ALG_NAME,
							"-ds", Classification.getAmes123().getAbsolutePath(),
							"--grid=C=-1:1:1:base=2",
							"--tolerance", ""+tolerance,
							"--opt-metric", "Observed Fuzziness",
							"-rf","csv",
							"--progress-bar",
							"--generate@file", atFile.getAbsolutePath(), 
							"--confidence", ""+conf,
							"-v",
					}
					);
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail();
		}

		String contents = FileUtils.readFileToString(atFile, StandardCharsets.UTF_8);
		//		SYS_ERR.println("At-FILE contents\n" + contents);
		String[] lines = contents.split("\n");
		Assert.assertTrue(lines.length >= (isLibLinear? 2:4)); // only cost or cost+gamma
		for (int i=0; i<lines.length; i++) {
			String l = lines[i];
			if (l.isEmpty())
				continue;
			// every other line should be a parameter-flag, and thus start with -- 
			if (i % 2 == 0) {
				Assert.assertTrue(l.startsWith("--"));
			} else if ( i % 2 == 1) {
				Assert.assertFalse(l.startsWith("--"));
			}
		}
		System.out.println("@File contents: \n" + contents);
		if (printOutput)
			printLogs();
	}

	@Test
	public void TestFaultyConfidenceTolerance() throws Exception {
//				exit.checkAssertionAfterwards(new PrintSysOutput());
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("--tolerance", "Invalid", "option", "range [0..1]"));
		mockMain(new String[]{
				Tune.CMD_NAME,
				"-pt", ACP_REGRESSION_TYPE,
				"--cv-folds", "10",
				"-ds", Regression.getSolu100().getAbsolutePath(),
				"-sc", (isLibLinear? LinearSVR.ALG_NAME : EpsilonSVR.ALG_NAME),
				"-g","GAMMA=b2:-1:1:1",
				"-g","COST=-1:1:1:b2",
				"--nonconf-measure", "log-normalized",
				"-g","BETA=0 0.1 0.3",
				"--tolerance", ""+(-0.1),
				"--echo"
		}	
				);
	}
	
	@Test
	public void testCustomOptMetric() throws IOException {
		systemOutRule.clearLog();
		mockMain(
				new String[]{
						Tune.CMD_NAME,
						"-pt", ACP_CLASSIFICATION_TYPE,
						"--cv-folds", "5",
						"-sc", LinearSVC.ALG_NAME,
						"-ds", Classification.getAmes123().getAbsolutePath(),
						"--grid=C=10,50,100",
						"--tolerance", ""+tolerance,
						"--result-format", "tsv",
						//							"--debug",
						"--progress-bar",
						"--confidence", ""+conf,
						"-op","BalancedOF"
				}
				);
		String output = systemOutRule.getLog();
		Assert.assertTrue(output.contains(BalancedObservedFuzziness.METRIC_NAME));
//		printLogs();
	}
	
	@Test
	public void testInvalidMetricType() throws IOException {
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString(BrierScore.METRIC_NAME, "not", "allowed", "predictor", "metric"));
		
		mockMain(
				new String[]{
						Tune.CMD_NAME,
						"-pt", ACP_CLASSIFICATION_TYPE,
						"--cv-folds", "5",
						"-sc", LinearSVC.ALG_NAME,
						"-ds", Classification.getAmes123().getAbsolutePath(),
						"--grid=C=10,50,100",
						"--tolerance", ""+tolerance,
						"--result-format", "tsv",
						"--progress-bar",
						"--confidence", ""+conf,
						"-op","BrierScore"
				}
				);
	}

	@Test
	public void testMoreFoldsThanRecords() throws Exception {
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("records", "folds"));
		//		exit.checkAssertionAfterwards(new PrintSysOutput());
		mockMain(new String[]{
				Tune.CMD_NAME,
				"-pt", ACP_REGRESSION_TYPE,
				"--cv-folds", "101", // One more than number of records
				"-ds", Regression.getSolu100().getAbsolutePath(),
				"--scorer", (isLibLinear? LinearSVR.ALG_NAME : EpsilonSVR.ALG_NAME),
				"-g=gamma=b2:-1:1:1",
				"-g=COST=-1:1:1:b2",
				"--nonconf-measure", "log-normalized",
				"-g=BETA=0 0.1 0.3"
		}	
				);

	}

	static class TuneMock {
		@Option(names = {"-g", "--grid"},
				description = "Specify which parameters that should be part of the parameter grid, specified using syntax -g <KEY>=<VALUES>, e.g., -g COST=1,10,100 or --grid Gamma=b2:-8:-1:2. Run "+"explain tune-grid" + " for further details.",
				paramLabel = ArgumentType.TUNE_KEY_VALUE,
				mapFallbackValue = "default",
				arity = "1..*",
				required = true)
		private LinkedHashMap<String,String> paramGrid;
	}

	//	@Test
	public void testPicoCLIParamGrid() {
		TuneMock mock = new TuneMock();
		new CommandLine(mock).parseArgs("-gC","--grid=B","-gD=abs");
		SYS_ERR.println(mock.paramGrid);
	}
	
	public static void main(String[] args) {
		//  - %-33s : %s%n
		SYS_ERR.println("Median prediction-interval width_SD".length());
	}

}

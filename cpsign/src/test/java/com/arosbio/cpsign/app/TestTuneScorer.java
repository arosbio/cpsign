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
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.cpsign.app.PrecomputedDatasets.Classification;
import com.arosbio.cpsign.app.PrecomputedDatasets.Regression;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.ml.algorithms.Regressor;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.gridsearch.GridSearch;
import com.arosbio.ml.gridsearch.GridSearch.GSResult;
import com.arosbio.ml.gridsearch.GridSearchResult;
import com.arosbio.ml.metrics.classification.BrierScore;
import com.arosbio.ml.metrics.classification.F1Score;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.utils.TestUtils;

import picocli.CommandLine;
import picocli.CommandLine.Option;

@Category(CLITest.class)
public class TestTuneScorer extends CLIBaseTest {

	final static boolean isLibLinear = false;
	final static boolean printOutput = false;

	@Test
	public void testNoArgs() throws Exception {
		mockMain(new String[] {
				TuneScorer.CMD_NAME,
		});
	}

	@Test
	public void testWhenHavingMissingValues() throws Exception {
		expectExit(ExitStatus.USER_ERROR);
		exit.checkAssertionAfterwards(
				new AssertSysErrContainsString("missing", "data", "feature","remove"));

		try {
			mockMain(new String[] {
					TuneScorer.CMD_NAME,
					"-ds", Classification.getMissingDataDS().getAbsolutePath(),
					"--time",
					"-gC"
			});
			Assert.fail();

		} catch (Exception e) {
		}
		// printLogs();
	}

	@Test
	public void testBadArgumentsNoSuccessfulCombo(){
		expectExit(ExitStatus.USER_ERROR);
		exit.checkAssertionAfterwards(
				new AssertSysErrContainsString("no", "valid", "parameter", "failed", "training"));
		// exit.checkAssertionAfterwards(new PrintSysOutput());

		try {
			mockMain(new String[] {
					TuneScorer.CMD_NAME,
					"-ds", Classification.getAmes123().getAbsolutePath(),
					"--time",
					"-gC=-1,-5,-10"
			});
			Assert.fail();

		} catch (Exception e) {
		}
		// printLogs();
	}


	@Test
	public void testFaultyParams() throws Exception {
		// printOutput();
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("gamma"));

		// Non correct start:step:end
		mockMain(new String[] {
				TuneScorer.CMD_ALIAS,
				"--scorer", "LinearSVC",
				"--cv-folds", "10",
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"--grid=gamma=b2:-1:-2:1.5",
		});

	}

	@Test
	public void testSomeFailingParams() throws Exception {
		// Should still run OK, but should specify that some cost values failed
		mockMain(new String[] {
			TuneScorer.CMD_ALIAS,
			"--scorer", "LinearSVC",
			"--cv-folds", "10",
			"-ds", Classification.getAmes123().getAbsolutePath(),
			"--grid=cost=-50,0,50",
			"--num-results","-1",
			"-rf", "tsv",
		});
		
		TestUtils.assertTextContainsIgnoreCase(systemOutRule.getLog(), "failed","-50","Invalid argument for parameter 'cost':");
		// printLogs();
	}

	@Test
	public void TestClassification() throws Exception {

		String seed = "" + (int) (Math.random() * 10000);

		File atFile = TestUtils.createTempFile("at-file", ".params");
		File resFile1 = TestUtils.createTempFile("res-file", ".csv");
		File resFile2 = TestUtils.createTempFile("res-file", ".csv");
		try {
			mockMain(
					new String[] {
							TuneScorer.CMD_NAME,
							"--cv-folds", "10",
							"-sc", (isLibLinear ? LinearSVC.ALG_NAME : C_SVC.ALG_NAME),
							"-ds", Classification.getAmes123().getAbsolutePath(),
							"-gGAMMA=0.5, 1.0, 2.0",
							"--grid=COST=-1:1:1:b=10",
							"-rf", "csv",
							"--generate@file", atFile.getAbsolutePath(),
							"--verbose",
							"--calc-all-metrics",
							"--result-output", resFile1.getAbsolutePath(),
							"--seed", seed
					});
		} catch (Exception e) {
			// e.printStackTrace();
			Assert.fail();
		}

		String contents = FileUtils.readFileToString(atFile, StandardCharsets.UTF_8);
		String[] lines = contents.split("\n");
		Assert.assertEquals("@File should contain --scorer and the parameters for it", 2, lines.length); // cost or
																											// cost+gamma
		for (int i = 0; i < lines.length; i++) {
			String l = lines[i];
			if (l.isEmpty())
				continue;
			// every other line should be a parameter-flag, and thus start with --
			if (i % 2 == 0) {
				Assert.assertTrue(l.startsWith("--"));
			} else if (i % 2 == 1) {
				Assert.assertFalse(l.startsWith("--"));
			}
		}

		if (printOutput)
			printLogs();

		// DO SEARCH AGAIN - SAME SEED SHOULD GIVE IDENTICAL RESULTS
		try {
			mockMain(
					new String[] {
							TuneScorer.CMD_NAME,
							"--cv-folds", "10",
							"-sc", (isLibLinear ? LinearSVC.ALG_NAME : C_SVC.ALG_NAME),
							"-ds", Classification.getAmes123().getAbsolutePath(),
							"-gGAMMA=0.5, 1.0, 2.0",
							"--grid=COST=-1:1:1:b=10",
							"-rf", "csv",
							"--calc-all-metrics",
							"--result-output", resFile2.getAbsolutePath(),
							"--seed", seed
					});
		} catch (Exception e) {
			// e.printStackTrace();
			Assert.fail();
		}

		try (CSVParser p1 = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(new FileReader(resFile1));
			 CSVParser p2 = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(new FileReader(resFile1));) {
				Assert.assertEquals(p1.getHeaderMap(), p2.getHeaderMap());
				int lastIndexToCheck = 0;
				for (String h : p1.getHeaderNames()){
					if (h.toLowerCase().startsWith("runtime")){
						break;
					}
					lastIndexToCheck++;
				}

				List<CSVRecord> recs1 = p1.getRecords();
				List<CSVRecord> recs2 = p2.getRecords();

				Assert.assertEquals(recs1.size(), recs2.size());
				for (int i = 0; i<recs1.size(); i++){
					CSVRecord r1 = recs1.get(i);
					CSVRecord r2 = recs2.get(i);
					for (int col=0; col<lastIndexToCheck; col++){
						Assert.assertEquals(r1.get(col), r2.get(col));
					}
				}
		}
		// SYS_ERR.println(IOUtils.toString(resFile1.toURI(), StandardCharsets.UTF_8));
		// SYS_ERR.println("\n\n---\n" + IOUtils.toString(resFile2.toURI(), StandardCharsets.UTF_8));
	}

	@Test
	public void TestC_SVC_JSON_output() throws Exception {

		File atFile =TestUtils.createTempFile("at-file", ".params");

		try {
			mockMain(
					new String[] {
							TuneScorer.CMD_NAME,
							"--cv-folds", "5",
							"-sc", C_SVC.ALG_NAME,
							"-ds", Classification.getAmes123().getAbsolutePath(),
							"-gGAMMA=\"0.5, 1.0, 2.0\"",
							"-gCOST=\"b2:-1:1:1\"",
							"-rf", "json",
							"--progress-bar",
							"--generate@file", atFile.getAbsolutePath(),
					});
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail();
		}

		String contents = FileUtils.readFileToString(atFile, StandardCharsets.UTF_8);
		String[] lines = contents.split("\n");
		Assert.assertEquals(2, lines.length);
		for (int i = 0; i < lines.length; i++) {
			String l = lines[i];
			if (l.isEmpty())
				continue;
			// every other line should be a parameter-flag, and thus start with --
			if (i % 2 == 0) {
				Assert.assertTrue(l.startsWith("--"));
			} else if (i % 2 == 1) {
				Assert.assertFalse(l.startsWith("--"));
			}
		}
		System.out.println("@File contents: \n" + contents);
		if (printOutput)
			printLogs();
	}


	@Test
	public void testRegAPI() throws Exception{

		GridSearch gs = new GridSearch.Builder().testStrategy(new KFoldCV(10)).computeMeanSD(true).build();
		Regressor reg = new LinearSVR();

		// Load dataset
		ChemDataset ds = ModelSerializer.loadDataset(Regression.getSolu100().toURI(), null);
		Map<String,List<?>> grid = new HashMap<>();
		grid.put("C", CollectionUtils.listRange(-1, 1, 1));
		// grid.put("gamma", CollectionUtils.listRange(-1, 1,1,2));
		grid.put("epsilon", Arrays.asList(0.0001, 0.001, 0.01));




		GridSearchResult res = gs.search(ds, reg, grid);

		for (GSResult r : res.getBestParameters()){
			System.err.println(r);
		}
		// printLogs();

	}

	@Test
	public void TestRegression() throws Exception {
		File atFile =TestUtils.createTempFile("at-file", ".params");
		try {
			mockMain(new String[] {
					TuneScorer.CMD_ALIAS,
					"--cv-folds", "10",
					"-ds", Regression.getSolu100().getAbsolutePath(),
					"-sc", LinearSVR.ALG_NAME,
					"-rf", "json", // "csv", //
					"-g", "GAMMA=-1:1:1:b=2",
					"-gC=-1:1:1",
					"-gEpsilon=0.0001, 0.001, 0.01",
					"--num-results", "-1",
					"--progress-bar",
					"--time",
					"--generate@file", atFile.getAbsolutePath(),
			});
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail();
		}

		String contents = FileUtils.readFileToString(atFile, StandardCharsets.UTF_8);
		String[] lines = contents.split("\n");
		Assert.assertEquals(2, lines.length);
		for (int i = 0; i < lines.length; i++) {
			String l = lines[i];
			if (l.isEmpty())
				continue;
			// every other line should be a parameter-flag, and thus start with --
			if (i % 2 == 0) {
				Assert.assertTrue(l.startsWith("--"));
			} else if (i % 2 == 1) {
				Assert.assertFalse(l.startsWith("--"));
			}
		}

		if (printOutput)
			printLogs();
	}

	@Test
	public void testCustomOptMetric() throws IOException {
		systemOutRule.clearLog();
		mockMain(
				new String[] {
						TuneScorer.CMD_NAME,
						"--cv-folds", "5",
						"-sc", LinearSVC.ALG_NAME,
						"-ds", Classification.getAmes123().getAbsolutePath(),
						"--grid=C=10,50,100",
						"--result-format", "tsv",
						// "--debug",
						"--calc-all",
						"--progress-bar",
						"-op", "F1Score"
				});
		String output = systemOutRule.getLog();
		Assert.assertTrue(output.contains(F1Score.METRIC_NAME));
		// printLogs();
	}

	@Test
	public void testInvalidMetricType() throws IOException {
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(
				new AssertSysErrContainsString(BrierScore.METRIC_NAME, "invalid", "allowed", "metric"));

		mockMain(
				new String[] {
						TuneScorer.CMD_NAME,
						"--cv-folds", "5",
						"-sc", LinearSVC.ALG_NAME,
						"-ds", Classification.getAmes123().getAbsolutePath(),
						"--grid", "C=10,50,100",
						"--result-format", "tsv",
						"--progress-bar",
						"-op", "BrierScore"
				});
	}

	@Test
	public void testMoreFoldsThanRecords() throws Exception {
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("records", "folds"));
		// exit.checkAssertionAfterwards(new PrintSysOutput());
		mockMain(new String[] {
				TuneScorer.CMD_NAME,
				"--cv-folds", "101", // One more than number of records
				"-ds", Regression.getSolu100().getAbsolutePath(),
				"--scorer", (isLibLinear ? LinearSVR.ALG_NAME : EpsilonSVR.ALG_NAME),
				"-g", "gamma=b2:-1:1:1",
				"-g", "COST=-1:1:1:b2",
		});

	}

	static class TuneMock {
		@Option(names = { "-g",
				"--grid" }, description = "Specify which parameters that should be part of the parameter grid, specified using syntax -g<KEY>=<VALUES>, e.g., -gCOST=1,10,100 or --gridGamma=b2:-8:-1:2. Run "
						+ "explain tune-grid"
						+ " for further details.", paramLabel = ArgumentType.TUNE_KEY_VALUE, mapFallbackValue = "default", arity = "1..*", required = true)
		private LinkedHashMap<String, String> paramGrid;
	}

	// @Test
	public void testPicoCLIParamGrid() {
		TuneMock mock = new TuneMock();
		new CommandLine(mock).parseArgs("-gC", "--grid=B", "-gD=abs");
		SYS_ERR.println(mock.paramGrid);
	}

}

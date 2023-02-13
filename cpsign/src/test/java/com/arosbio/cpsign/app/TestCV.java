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
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.GlobalConfig.Defaults.PredictorType;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.cpsign.app.PrecomputedDatasets.Classification;
import com.arosbio.cpsign.app.PrecomputedDatasets.Regression;
import com.arosbio.cpsign.app.params.CLIParameters;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.tests.utils.TestUtils;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;

@Category(CLITest.class)
public class TestCV extends CLIBaseTest {
	
	static boolean DISPLAY_PROGRESS = false;

	@Before
	public void setup() throws Exception{
		LoggerUtils.reloadLoggingConfig();
	}

	@Test
	public void testCrossValidateRegression() throws Exception {
		//		original.println("======REGRESSION======");
		File precomFile = TestUtils.createTempFile("test", ".jar");
		CmpdData solu = TestResources.Reg.getLogS_1210(); //10k
		mockMain(Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_REGRESSION,
				"--property", solu.property(),
				"--train-data", solu.format(), solu.uri().toString(), 
				"--model-out", precomFile.getAbsolutePath());
		//		crossValidate(precompFile, type, expectedResult, isClassification, conf, seed)
		crossValidate(precomFile, EpsilonSVR.ALG_NAME, 1.5, PredictorType.ACP_REGRESSION,0.7, null);
		crossValidate(precomFile, LinearSVR.ALG_NAME, 1.6, PredictorType.ACP_REGRESSION,0.7, null);
		// printLogs();
	}

	@Test
	public void testCrossValidateClassification() throws Exception {
		//		original.println("======CLASSIFICATION======"); 
		File precomFile = TestUtils.createTempFile("test", ".jar");
		CmpdData cox2 = TestResources.Cls.getCox2();
		mockMain(Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_CLASSIFICATION,
				"--labels", "-1 1",
				"--property", "class",
				"--train-data", cox2.format(), cox2.uri().toString(), // CSVFile.FORMAT_NAME, classificationDataset,
				"--model-out", precomFile.getAbsolutePath());
		crossValidate(precomFile, C_SVC.ALG_NAME, 1000, PredictorType.ACP_CLASSIFICATION, 0.8, null);
//		printLogs();
		crossValidate(precomFile, LinearSVC.ALG_NAME, 1000, PredictorType.ACP_CLASSIFICATION, 0.8, null);
//		printLogs();
	}
	@Test
	public void testCVUsingStaticSeedACP_Class() throws Exception {
		String cv1 = crossValidate(Classification.getAmes123(), LinearSVC.ALG_NAME, 1000, PredictorType.ACP_CLASSIFICATION, 0.8, "10");
		String cv2 = crossValidate(Classification.getAmes123(), LinearSVC.ALG_NAME, 1000, PredictorType.ACP_CLASSIFICATION, 0.8, "10");
		Assert.assertEquals(cv2, cv1);
		Assert.assertTrue(cv1.length() > 10);
	}

	@Test
	public void testCVUsingStaticSeedReg() throws Exception {
		GlobalConfig.getInstance().setRNGSeed(987);
		String cv1 = crossValidate(Regression.getSolu100(), LinearSVR.ALG_NAME, 1000, PredictorType.ACP_REGRESSION, 0.8, "10");
		GlobalConfig.getInstance().setRNGSeed(678); // Set something else in-between 
		String cv2 = crossValidate(Regression.getSolu100(), LinearSVR.ALG_NAME, 1000, PredictorType.ACP_REGRESSION, 0.8, "10");
		Assert.assertEquals(cv2, cv1);
		Assert.assertTrue(cv1.length() > 10);
		// SYS_ERR.println(cv1 + "\n" + cv2);
	}

	@Test
	public void testCVUsingStaticSeedVennABERS() throws Exception {
		GlobalConfig.getInstance().setRNGSeed(987);
		String cv1 = crossValidate(Classification.getAmes123(), LinearSVC.ALG_NAME, 1000, PredictorType.VAP_CLASSIFICATION, 0.8, "10");
		GlobalConfig.getInstance().setRNGSeed(678); // Set something else in-between 
		String cv2 = crossValidate(Classification.getAmes123(), LinearSVC.ALG_NAME, 1000, PredictorType.VAP_CLASSIFICATION, 0.8, "10");
		//		String cv2 = crossValidate(amesSmall, "sdf",AmesBinaryClass.PROPERTY, LinearSVC.ALG_NAME, 1000, true, 0.8, AmesBinaryClass.LABELS_STRING, "10");
		Assert.assertEquals(cv2, cv1);
		Assert.assertTrue(cv1.length() > 10);
	}

	public String crossValidate(File precompFile, String scorerType, double expectedResult, PredictorType pType, double conf, String seed) throws Exception {

		String[] allArgs = new String[] {
				CrossValidate.CMD_NAME,
				"-pt", pType.toString(),
				"-ds", precompFile.getAbsolutePath(),
				"--cv-folds", "10",
				"-ss", strategy(RANDOM_SAMPLING, 1),
				"--calibration-points", ""+conf,
				"-sc", scorerType,
				(seed!=null && !seed.isEmpty()? "--seed":""), seed,
				(DISPLAY_PROGRESS? "--progress-bar":null),
				"-rf", "JSON",
		};
		// List<String> noEmpty = new ArrayList<>();
		// for(String arg: allArgs)
		// 	if(arg!=null && !arg.isEmpty())
		// 		noEmpty.add(arg);

		//		System.err.println(noEmpty);
		clearLogs();

		mockMain(allArgs); // noEmpty.toArray(new String[]{}));

		Assert.assertTrue((DISPLAY_PROGRESS? ! systemErrRule.getLog().isEmpty() : systemErrRule.getLog().isEmpty()));
		String trainOutput = systemOutRule.getLog();
		//		SYS_ERR.println("TRAIN_OUT:\n"+trainOutput+"\n----");
		//		if (DISPLAY_PROGRESS)
		//			obj.printLogs();

		// Check validity - parse JSON
		JsonObject json = getJSONFromLog(trainOutput);
		//		String jsonStr = trainOutput.substring(trainOutput.lastIndexOf("\n{"), trainOutput.lastIndexOf("}\n")+1);
		//		JsonObject json = (JsonObject) Jsoner.deserialize(jsonStr); //new JSONParser().parse(jsonStr);

		// Check for CP type predictors (not possible or Venn-ABERS)
		if (pType != PredictorType.VAP_CLASSIFICATION) {
			Map<String,Object> plot = (JsonObject)json.get("plot");
			double accuracy = TypeUtils.asDouble(
					((JsonArray)plot.get("Accuracy")).get(0)
					);
			Assert.assertTrue(accuracy >= conf-0.5);
		}


		if (pType == PredictorType.ACP_REGRESSION){
			double rmse = TypeUtils.asDouble(json.get("RMSE"));
			Assert.assertTrue(rmse<expectedResult);
		}

		return trainOutput;
	}

	@Test
	public void testF1ScoreLOOCV() throws Exception {
		// TODO - this works but the output is not really correct, F1 is not defined for e.g. when there's no TN for instance
		// The SD will be strange, as F1 flips between 0 and 1 depending on the predictions
		mockMain(
			CrossValidate.CMD_NAME,
			"-pt", "ACP_Classification",
			"-ds", Classification.getAmes123().getAbsolutePath(),
			"--cv-folds", "LOO-CV",
			"-ss", strategy(RANDOM_SAMPLING, 1),
			"--calibration-points", ".8",
			"-sc", "linearSVC",
			// (seed!=null && !seed.isEmpty()? "--seed":""), seed, // (seed!=null && !seed.isEmpty()? seed:""),
			(DISPLAY_PROGRESS? "--progress-bar":null),
			"-rf", "JSON");

		// printLogs();
	}

	@Test
	@Category(PerformanceTest.class)
	public void testRealCVClassCVAP() throws Exception{
		File precomFile = File.createTempFile("test", ".jar");
		CmpdData ames = TestResources.Cls.getAMES_4337();
		mockMain(Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_CLASSIFICATION,
				"-mo", precomFile.getAbsolutePath(),
				"-pr","Activity",
				"-l", "POS, NEG",
				"-td", ames.format(), ames.uri().toString() // SDFile.FORMAT_NAME, luciaDataset
				);

		mockMain( new String[]{
				CrossValidate.CMD_NAME,
				"-pt",CVAP_CLASSIFICATION_TYPE,
				"-ds", precomFile.getAbsolutePath(),
				"-k", "5",
				"-ss", strategy(RANDOM_SAMPLING, 10),
				"-rf", CLIParameters.TextOutputType.TEXT.name(),
				"--time",
				"--calibration-points", "0.05:1:0.1",
				"--calibration-points-width", "0.1",
				(DISPLAY_PROGRESS? "--progress-bar":""),
				//				"--roc",
		});
		//		SYS_OUT.println(systemOutRule.getLog());
		if (DISPLAY_PROGRESS)
			printLogs();

		//		printLogs();
	}

	@Test
	@Category(PerformanceTest.class)
	public void testRealCVClass() throws Exception{
		File precomFile = TestUtils.createTempFile("test", ".jar");
		CSVCmpdData ames = TestResources.Reg.getSolubility_10k();
		mockMain(Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_CLASSIFICATION,
				"-mo", precomFile.getAbsolutePath(),
				"-pr","Activity",
				"-l", "POS, NEG",
				"-td",ames.format(), ames.uri().toString() // SDFile.FORMAT_NAME, luciaDataset
				);

		mockMain( new String[]{
				CrossValidate.CMD_NAME,
				"-pt",ACP_CLASSIFICATION_TYPE,
				"-ds", precomFile.getAbsolutePath(),
				//				"-md", SDFile.FORMAT_NAME, luciaDataset,
				//				"-cd", SDFile.FORMAT_NAME, luciaDataset,
				"-k", "5",
				"-ss", strategy(RANDOM_SAMPLING, 10),
				"-cp", "0.2 0.5 0.7 0.9",
				"-rf", "json"
				//				"--nonconf-measure", "per-class",
		});
		//		printLogs();
	}

	@Test
	@Category(PerformanceTest.class)
	public void testRealCVReg() throws Exception{
		//		LoggerUtils.setDebugMode();
		File precomFile = TestUtils.createTempFile("test", ".jar");
		CSVCmpdData solu = TestResources.Reg.getSolubility_10k();
		mockMain(Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_REGRESSION,
				"-mo", precomFile.getAbsolutePath(),
				"-pr","ClogP",
				//				"-l", "POS, NEG",
				"-td", solu.format(), solu.uri().toString() // SDFile.FORMAT_NAME, solubilityDataset
				);
		mockMain(
				CrossValidate.CMD_NAME,
				"-pt",ACP_REGRESSION_TYPE,
				"-ds", precomFile.getAbsolutePath(),
				"-k", "5",
				"-ss", strategy(RANDOM_SAMPLING, 10),
				"--nonconf-measure", "lognormalizedNCM:.4",
				//				"--nonconf-beta", "0.4",
				"--time",
				"-cp", "0.2 0.5 0.7 0.9",
				"-rf", "tsv"
		);
		//		printLogs();
	}



}

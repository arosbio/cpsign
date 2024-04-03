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

import org.apache.commons.math3.util.MedianOf3PivotingStrategy;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.cpsign.app.PrecomputedDatasets.Classification;
import com.arosbio.cpsign.app.PrecomputedDatasets.Regression;
import com.arosbio.cpsign.app.params.converters.MLAlgorithmConverter;
import com.arosbio.cpsign.out.OutputNamingSettings;
import com.arosbio.cpsign.out.OutputNamingSettings.JSON;
import com.arosbio.ml.algorithms.MLAlgorithm;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.cp.nonconf.regression.LogNormalizedNCM;
import com.arosbio.ml.metrics.cp.regression.MedianPredictionIntervalWidth;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.utils.TestUtils;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;


@Category(CLITest.class)
public class TestSetSVMParams extends CLIBaseTest{


//	String trainfile = AmesBinaryClass.SMALL_FILE_PATH+GZIP_SUFFIX;
//	String trainfileRegression = RegressionChang.CHANG_FILE_PATH;

	final int nrModels=3;
	final double epsilon = 5, gamma=10, cost=0.001, epsilon_p=1.5;
	final int cvFolds=10;

//	@Test
	public void CSVC_params() {
		MLAlgorithm alg = new MLAlgorithmConverter().convert("CSVC:c=10:eps=10:svr-epsilon=1.5");
		SYS_ERR.println(alg);
		SYS_ERR.println(alg.getProperties());
	}

	@Test
	public void TestTrain() throws Exception {

		//Create temp files for model and signatures
		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.cpsign");

		try{
			mockMain(new String[] {
					Train.CMD_NAME,
					"-pt", ACP_CLASSIFICATION_TYPE,
					"-ds", Classification.getAmes123().getAbsolutePath(),
					"-ss", strategy(RANDOM_SAMPLING, nrModels),
					"-sc", C_SVC.ALG_NAME,
					"-mo", modelFile.getAbsolutePath(),
					"-mn", "dsfasf",
			});
		} catch(Exception e){
			e.printStackTrace(SYS_ERR);
			Assert.fail();
		}

		// Predict
		systemOutRule.clearLog();
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", "0.1,0.4,0.9",
		});

		String predictionOut = systemOutRule.getLog();
		JsonObject json = (JsonObject)getJSONArrayFromLog(predictionOut).get(0);

		//Get the P-values
		JsonObject pvals = getPvals(json);

		//		original.println(predictionOut);


		// NOW USE NEW PARAMETERS
		modelFile = TestUtils.createTempFile("newParams", ".cpsign");

		try{
			mockMain(new String[] {
					Train.CMD_NAME,
					"-pt", ACP_CLASSIFICATION_TYPE,
					"-ds", Classification.getAmes123().getAbsolutePath(),
					"-ss", strategy(RANDOM_SAMPLING, nrModels),
					"-mo", modelFile.getAbsolutePath(),
					"-mn", "lkjb",
					"--scorer", LinearSVC.ALG_NAME+":cost="+cost+":eps="+epsilon, // +":svr-epsilon="+epsilon_p,
//					"--epsilon", ""+epsilon,
//					"--gamma", ""+gamma,
//					"--cost", ""+cost,
//					"--epsilon-svr", ""+epsilon_p
			});
		} catch(Exception e){
			e.printStackTrace(SYS_ERR);
			Assert.fail();
		}
		
//		TestUtils.validateSecurity();
//		SYS_ERR.println(ModelLoader.getCPSignProperties(modelFile.toURI()));

		// Predict
		systemOutRule.clearLog();
		mockMain(new String[] {
				Predict.CMD_NAME,
				//				"-pt", "1",
				//				"-nr", ""+nrModels,
				"-m", modelFile.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", "0.1,0.4,0.9",
		});

		String predictionOutNewParams = systemOutRule.getLog();
		//Get the P-values
		JsonObject pvals2 = getPvals((JsonObject)getJSONArrayFromLog(predictionOutNewParams).get(0));
		System.out.println("pvals1=" + pvals + ", pvals2=" + pvals2);

		// Check that the pvalues are "significantly" different
		double totalDiff = 0; 
		for (String label: pvals.keySet())
			totalDiff += Math.abs(
					TypeUtils.asDouble(pvals.get(label))
					-TypeUtils.asDouble(pvals2.get(label))
					);

		Assert.assertTrue(totalDiff > 0.05);
		
//		Assert.assertTrue(systemErrRule.getLog().isEmpty());

		//		original.println(predictionOutNewParams);
	}

	@Test
	public void TestFautlySVMParams_EPS() throws Exception {
		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.cpsign");
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("epsilon"));
		mockMain(new String[] {
				Train.CMD_NAME,
				"-pt", ACP_CLASSIFICATION_TYPE,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "lkjb",
				"--epsilon", ""+-0.11,
		});

	}

//	@Test
	public void TestFautlySVMParams_GAMMA() throws Exception {
		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.cpsign");
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("gamma"));
		mockMain(new String[] {
				Train.CMD_NAME,
				"-pt", ACP_CLASSIFICATION_TYPE,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "lkjb",
				"--scorer", LinearSVC.ALG_NAME+":cost="+cost+":eps="+epsilon,
		});

	}
	@Test
	public void TestFautlySVMParams_COST() throws Exception {
		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.cpsign");		
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("cost"));
//		exit.checkAssertionAfterwards(new PrintSysOutput());
		mockMain(new String[] {
				Train.CMD_NAME,
				"-pt", ACP_CLASSIFICATION_TYPE,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "lkjb",
				"--scorer", C_SVC.ALG_NAME+":epsilon="+epsilon+":gamma="+gamma +":cost="+-200,
		});
//		printLogs();
	}

	@Test
	public void TestFautlySVMParams_EPS_SVR() throws Exception {
		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.cpsign");
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString(LinearSVR.ALG_NAME, "not", "0", "svrEpsilon"));

		CmpdData chang = TestResources.Reg.getChang();
		mockMain(new String[] {
				Train.CMD_NAME,
				"-pt", ACP_REGRESSION_TYPE,
				"-td", chang.format(), chang.uri().toString(), // SDFile.FORMAT_NAME, RegressionChang.CHANG_FILE_PATH,
				"-pr", chang.property(), // RegressionChang.PROPERTY,
//				"--labels", LABELS_STRING,
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "lkjb",
				"--scorer", LinearSVR.ALG_NAME+":cost="+cost+":eps="+epsilon+":svr-epsilon="+-1,
		});
		printLogs();

	}

	@Test
	// This sometimes fails
	public void TestPredictTCP() throws Exception{
		LoggerUtils.setDebugMode();
		mockMain(new String[] {
				PredictOnline.CMD_NAME,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"--smiles", TEST_SMILES
		});
		String predictOutput = systemOutRule.getLog().toString();
		// SYS_ERR.p´intln(predictOutput);
		JsonObject json = (JsonObject)getJSONArrayFromLog(predictOutput).get(0); 
		assertJSONPred(json, true, false, false, null, null);
		JsonObject pvals = getPvals(json);

		systemOutRule.clearLog();
		mockMain(new String[] {
				PredictOnline.CMD_NAME,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"--scorer", LinearSVC.ALG_NAME+":cost="+cost+":eps="+epsilon,
				"--smiles", TEST_SMILES,
		});

		String predictOutput2 = systemOutRule.getLog().toString();
		JsonObject pred2 = (JsonObject) getJSONArrayFromLog(predictOutput2).get(0);
		assertJSONPred(pred2, true, false, false, null, null); //getJSONArrayFromLog(predictOutput2).get(0)
		JsonObject pvals2 = getPvals(pred2);

//		SYS_ERR.println("pvals: " + pvals + "\npvals2: " + pvals2);
		// Check that the pvalues are "significantly" different
		double totalDiff = 0; 
		for (String label: pvals.keySet())
			totalDiff = Math.abs(TypeUtils.asDouble(pvals.get(label))
					-TypeUtils.asDouble(pvals2.get(label)));
		Assert.assertTrue(totalDiff >0.1);

	}

	@Test
	public void TestCrossValidate() throws JsonException {
		long seed = 1582138333713l;
		try{
			mockMain(new String[] {
					CrossValidate.CMD_NAME,
					"-ds", Regression.getChang().getAbsolutePath(),
					"-pt", "2",
					"--seed", ""+seed,
					"--nonconf-measure", ""+LogNormalizedNCM.ID+":.1",
//					"--nonconf-beta", "0.1",
					"--cv-folds", ""+cvFolds,
					"-rf", "json",
			});
		} catch(Exception e){
			e.printStackTrace();
			Assert.fail();
		}
		String predictOutput = systemOutRule.getLog().toString();

		systemOutRule.clearLog();

		try{
			mockMain(new String[] {
					CrossValidate.CMD_NAME,
					"-ds", Regression.getChang().getAbsolutePath(),
					"-pt", "2",
					"--scorer", LinearSVR.ALG_NAME +":cost="+cost + ":epsilon="+epsilon, 
					"--cv-folds", ""+cvFolds,
					"--seed", ""+seed,
					"--nonconf-measure", ""+LogNormalizedNCM.ID+":.1",
//					"--nonconf-beta", "0.1",
					"-rf", "json",
			});
		} catch(Exception e){
			e.printStackTrace();
			Assert.fail();
		}

		String predictOutput2 = systemOutRule.getLog().toString();
		
		JsonObject json1 = getJSONFromLog(predictOutput);
		JsonObject json2 = getJSONFromLog(predictOutput2);
		
		double rmse1 = TypeUtils.asDouble(json1.get("RMSE")); 
		double rmse2 = TypeUtils.asDouble(json2.get("RMSE")); 
		
		double efficiency1 = TypeUtils.asDouble(getFirstValueInPlot(json1, MedianPredictionIntervalWidth.Y_AXIS)); 
		double efficiency2 = TypeUtils.asDouble(getFirstValueInPlot(json2, MedianPredictionIntervalWidth.Y_AXIS)); 
		
		Assert.assertTrue(Math.abs(rmse1-rmse2)>0.12);
		Assert.assertTrue("eff1: " + efficiency1 + "   eff2: " + efficiency2, Math.abs(efficiency1-efficiency2)>.25);
		
//		printLogs();

	}
	
	private Object getFirstValueInPlot(JsonObject topLevel, String key) {
		JsonObject plotMap = (JsonObject)topLevel.get(OutputNamingSettings.JSON.PLOT_SECTION);
		return ((JsonArray) plotMap.get(key)).get(0);
	}
	
	private JsonObject getPvals(JsonObject json) {
		return (JsonObject) ((JsonObject)json.get(JSON.PREDICTING_SECTION_KEY)).get(JSON.CLASS_PVALS_KEY);
	}
}

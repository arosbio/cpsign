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
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.cheminf.ChemCPClassifier;
import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.descriptors.ChemDescriptor;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor.SignatureType;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.cpsign.app.PrecomputedDatasets.Classification;
import com.arosbio.cpsign.out.OutputNamingSettings.JSON;
import com.arosbio.ml.TrainingsetValidator;
import com.arosbio.ml.algorithms.Classifier;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.cp.tcp.TCPClassifier;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.tests.utils.TestUtils;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;

@Category(CLITest.class)
public class TestTCPClassification extends CLIBaseTest {

	CmpdData ames_126 = TestResources.Cls.getAMES_126();
	CSVCmpdData solu_10_to_pred = TestResources.Reg.getSolubility_10();
//	private static final String PREDICT_FILE_SDF = AmesBinaryClass.MINI_FILE_PATH;
//	private static final String PREDICT_FILE_SMILES = RegressionSolubility.SOLUBILITY_10_FILE_PATH;

	@Before
	public void resetLogging() throws Exception {
		LoggerUtils.reloadLoggingConfig();
	}

	@Test
	public void testMulticlassProbabilityNCM() throws Exception {
		File modelFile = TestUtils.createTempFile("tcpmodel", ".jar");
		
		mockMain(
				Train.CMD_NAME,
				"-pt", TCP_CLASSIFICATION_TYPE,
				"-ds", Classification.get3ClassLTKB().getAbsolutePath(), 
				"-sc", "platt-scaledC_svc", 
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "trained-tcp-class",
				"--nonconf-measure", "inverse-probability",
				"--percentiles", "0",
				"--time"
		);
		
//		systemOutRule.clearLog();
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", "0.1,0.4,0.7,0.8,0.9",
//				"-cg",
				"-of", "csv",

		});
		
		Assert.assertTrue(systemErrRule.getLog().isEmpty());
		
//		printLogs();
	}

	@Test
	public void testTooSmallDatasetShouldFail() throws Exception {
		TrainingsetValidator.setProductionEnv();
		File modelFile = TestUtils.createTempFile("tcpmodel", ".jar");
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("requires"));
		exit.checkAssertionAfterwards(new SetTestEnv());
		mockMain(
				Train.CMD_NAME,
				"-ds", Classification.getTooSmallDS().getAbsolutePath(),
				"-pt", TCP_CLASSIFICATION_TYPE,
				"-sc", LinearSVC.ALG_NAME, 
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "trained-acp-class",
				"--time"
		);
	}
	
	@Test
	public void testWhenHavingMissingValues() throws Exception {
		expectExit(ExitStatus.USER_ERROR);
//		exit.checkAssertionAfterwards(new PrintSysOutput());
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("missing", "data", "features", "training", "revise"));
		
		
		try {
			mockMain(new String[] {
					PredictOnline.CMD_NAME,
					"-ds", Classification.getMissingDataDS().getAbsolutePath(),

					"--smiles", TEST_SMILES,
//					"-mo", preFirst.getAbsolutePath(),
//					"-mn", "dasf",
					"--time"
			}
			);
			Assert.fail();
			
		} catch(Exception e){
		}
//		printLogs();
	}

	@Test
	public void testTooSmallDatasetShouldFailOnlinePred() throws Exception {
		TrainingsetValidator.setProductionEnv();
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("requires"));
		exit.checkAssertionAfterwards(new SetTestEnv());
			mockMain(new String[] {
					PredictOnline.CMD_NAME,
					"-ds", Classification.getTooSmallDS().getAbsolutePath(),
					"-sc", LinearSVC.ALG_NAME, 
					"--smiles", TEST_SMILES,
					"--time",
			});
	}

	@Test
	public void testPredictUsingSameSeed() throws Exception {

		String SEED = "" + System.currentTimeMillis();

		systemOutRule.clearLog();
		mockMain(new String[] {
				PredictOnline.CMD_NAME,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"--smiles", TEST_SMILES,
				"--seed", SEED,
		});

		String firstPrediction = systemOutRule.getLog();

		Assert.assertTrue(firstPrediction.contains(JSON.CLASS_PVALS_KEY));

		//Predict again
		systemOutRule.clearLog();
		mockMain(new String[] {
				PredictOnline.CMD_NAME,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"--smiles", TEST_SMILES,
				"--seed", SEED,
		});

		String secondPrediction = systemOutRule.getLog();

		Assert.assertTrue(secondPrediction.contains(JSON.CLASS_PVALS_KEY));

		Assert.assertEquals(firstPrediction, secondPrediction);
		Assert.assertTrue(systemErrRule.getLog().isEmpty());
	}


	@Test
	public void testPredictSDF() throws Exception {
		List<Double> confs = Arrays.asList(0.7,0.8,.9);

		CmpdData ames10 = TestResources.Cls.getAMES_10();
		mockMain(new String[] {
				PredictOnline.CMD_NAME,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"-p",ames10.format(), ames10.uri().toString(),
				"-co", TestUtils.toString(confs, ' '),
				"-cg",
				"--percentiles-data", ames10.format(), ames10.uri().toString(),
				"--percentiles", ""+5
		});

		// printLogs();
		JsonArray lines = getJSONArrayFromLog(systemOutRule.getLog());

		Assert.assertEquals(10, lines.size());

		for (Object rec : lines){
			assertJSONPred((JsonObject)rec, true, true, false, confs, null);
		}
	}

	@Test
	public void testPredictCSVFile() throws Exception {

		// CSVCmpdData solu10 = TestResources.Reg.getSolubility_10();
		mockMain(
				PredictOnline.CMD_NAME,
				"-ds", Classification.getAmes123().getAbsolutePath(),
//				"-td", SDFile.FORMAT_NAME, TRAIN_FILE,
//				"--endpoint", AmesBinaryClass.PROPERTY,
//				"-l",AmesBinaryClass.LABELS_STRING,
//				"--descriptors", "signatures:1:3",
				"-p", solu_10_to_pred.format(), solu_10_to_pred.uri().toString(), // CSVFile.FORMAT_NAME, RegressionSolubility.SOLUBILITY_10_FILE_PATH,
				"--percentiles-data", ames_126.format(), ames_126.uri().toString(), // AmesBinaryClass.FILE_FORMAT, AmesBinaryClass.MINI_FILE_PATH,
				"--percentiles", "5",
				"-v",
				"--time"
		);

		String output = systemOutRule.getLog();
		//		original.println(output);
		JsonArray resultLines = getJSONArrayFromLog(output);

		Assert.assertEquals(10, resultLines.size());

		for (Object rec : resultLines){
			assertJSONPred((JsonObject)rec, true, false, false, null, null);
		}

	}


	@Test
	public void testTrainAndPredict() throws Exception {

		File trainedTCPmodel = TestUtils.createTempFile("trained.tcp", ".cpsign");
		double cost=10d;
		double eps = 0.005;
		double gamma=0.01;

		//		LoggerUtils.setDebugMode();
		try{
			mockMain(new String[]{
					Train.CMD_NAME,
					"-pt", TCP_CLASSIFICATION_TYPE,
					"-ds", Classification.getAmesCDKDescAndTransformations().getAbsolutePath(),

					"-mo", trainedTCPmodel.getAbsolutePath(),
					"--model-name", "precomputed",
					"--scorer", LinearSVC.ALG_NAME+":C="+cost+":epsilon="+eps,
			});

		} catch(Exception e){
			e.printStackTrace();
			Assert.fail();
		}

		LoggerUtils.setDebugMode();
		ChemCPClassifier tcpLoaded = (ChemCPClassifier) ModelSerializer.loadChemPredictor(trainedTCPmodel.toURI(), null);
		Classifier clf = ((TCPClassifier)tcpLoaded.getPredictor()).getNCM().getModel();
		if (clf instanceof LinearSVC) {
			Assert.assertEquals(cost, ((LinearSVC) clf).getC(),0.0001);
			Assert.assertEquals(eps, ((LinearSVC) clf).getEpsilon(),0.0001);	
		} else if (clf instanceof C_SVC) {
			Assert.assertEquals(cost, ((C_SVC) clf).getC(),0.0001);
			Assert.assertEquals(eps, ((C_SVC) clf).getEpsilon(),0.0001);
			Assert.assertEquals(gamma, ((C_SVC) clf).getGamma(),0.0001);
		}

		ChemDataset sp = tcpLoaded.getDataset();
		List<ChemDescriptor> descriptors = sp.getDescriptors();
		Assert.assertTrue(descriptors.size() > 1);
		ChemDescriptor last = descriptors.get(descriptors.size()-1);
		Assert.assertTrue(last instanceof SignaturesDescriptor);
		SignaturesDescriptor desc = (SignaturesDescriptor) last;
		Assert.assertEquals(Classification.customEndH, desc.getEndHeight());
		Assert.assertEquals(Classification.customStartH, desc.getStartHeight());
		Assert.assertTrue(desc.getSignatureType() == SignatureType.STANDARD);

		try{
			mockMain(new String[]{
					Predict.CMD_NAME,
					"-m", trainedTCPmodel.toString(),      
					"--smiles", TEST_SMILES,
			});
		} catch(Exception e){
			e.printStackTrace();
			Assert.fail();
		}

	}

	@Test
	public void skipSignificantSignatureCalculation() throws Exception {


		mockMain(
				PredictOnline.CMD_NAME,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"-p", solu_10_to_pred.format(),solu_10_to_pred.uri().toString()
		);

		String output = systemOutRule.getLog();
		//		original.println(output);
		for(Object rec: getJSONArrayFromLog(output))
			assertJSONPred((JsonObject)rec, true, false, false, null, null);



		// CALC GRADIENT
		systemOutRule.clearLog();

		mockMain(new String[] {
				PredictOnline.CMD_NAME,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"-p", solu_10_to_pred.format(), solu_10_to_pred.uri().toString(), // CSVFile.FORMAT_NAME, RegressionSolubility.SOLUBILITY_10_FILE_PATH,
				"-cg",
				"--percentiles", ""+5,
				"--percentiles-data", solu_10_to_pred.format(), solu_10_to_pred.uri().toString()
		});


		String output2 = systemOutRule.getLog();
		//		original.println(output);
		for(Object rec: getJSONArrayFromLog(output2))
			assertJSONPred((JsonObject) rec, true, true, false, null, null);

	}


	@Test
	public void testPrecomputeEncryptedThenPredict() throws Exception {

		File encryptKey = generateEncryptionKeyFile();

//		//==============================
//		// Precompute to Encrypted
//		//==============================

		File modelFileEnc = TestUtils.createTempFile("modelfileEnc", ".enc.jar");

		mockMain(
				Precompute.CMD_NAME,
				"-td" , ames_126.format(), ames_126.uri().toString(), // AmesBinaryClass.FILE_FORMAT, AmesBinaryClass.SMALL_FILE_PATH,
				"--property",ames_126.property(), 
				"-l", getLabelsArg(ames_126.labels()),
				"--key-file", encryptKey.getAbsolutePath(),
				"-mo", modelFileEnc.toString(),
				"--model-name", "precomputed-encrypted"
		);


		// Predict on it!
		systemOutRule.clearLog();
		// LoggerUtils.setDebugMode(SYS_ERR);
		mockMain(new String[] {
				PredictOnline.CMD_NAME,
				"-ds",modelFileEnc.toString(),
				"--key-file",encryptKey.getAbsolutePath(),
				"--smiles", TEST_SMILES,
		});


		//Store for later assertion
		String resEnc = systemOutRule.getLog();
		JsonArray predLinesEnc = getJSONArrayFromLog(resEnc);
		Assert.assertEquals(1, predLinesEnc.size());
		assertJSONPred((JsonObject)predLinesEnc.get(0), true, false, false, null, null);


	}




//	@Test
	@Category(PerformanceTest.class)
	public void testTrainAndPredictLarge() throws Exception {

		String seed = "" + (int)(Math.random()*10000);


		/// FIRST ATTEMPT 

		//Create temp files for model and signatures
		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.jar"); 
		CSVCmpdData cox2 = TestResources.Cls.getCox2();

		mockMain(new String[] {
				Train.CMD_NAME,
				"-pt", TCP_CLASSIFICATION_TYPE,
				"-td", cox2.format(), cox2.uri().toString(),
				"-pr", "class",
				"--labels", "-1 1",
				"-sc", "linear-svc",
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "sdagas",
				"--percentiles", "5",
				"--seed", seed,
				"--progress-bar",
				"--time"
		});


		systemOutRule.clearLog();
		File predictions = TestUtils.createTempFile("preds", ".smi");
		mockMain(new String[] {
				PredictOnline.CMD_NAME,
				"-td", cox2.format(), cox2.uri().toString(),
				"-pr", "class",
				"--labels", "-1 1",
				"-p", ames_126.format(), ames_126.uri().toString(),
				"-o", predictions.getAbsolutePath(), 
				"--confidences", "0.1,0.4,0.9",
				"--progress-bar"
		});

		//		String pred1 = systemOutRule.getLog();

		//		printLogs();
	}


}

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
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.cheminf.ChemCPRegressor;
import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.descriptors.CDKPhysChemWrapper;
import com.arosbio.cheminf.descriptors.ChemDescriptor;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.cheminf.descriptors.fp.ECFP6;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.commons.TypeUtils;
import com.arosbio.cpsign.app.PrecomputedDatasets.Regression;
import com.arosbio.cpsign.out.OutputNamingSettings.JSON;
import com.arosbio.ml.TrainingsetValidator;
import com.arosbio.ml.algorithms.Regressor;
import com.arosbio.ml.algorithms.impl.DefaultMLParameterSettings;
import com.arosbio.ml.algorithms.impl.LibSvm;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.nonconf.NCM;
import com.arosbio.ml.cp.nonconf.regression.NCMRegression;
import com.arosbio.ml.io.ModelIO;
import com.arosbio.ml.io.impl.PropertyFileStructure;
import com.arosbio.ml.sampling.FoldedSampling;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.suites.NonSuiteTest;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.TestChemDataLoader.PreTrainedModels;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;

@Category(CLITest.class)
public class TestACPRegression extends CLIBaseTest {

	final int nrModels=5;
	final String nrFolds = "5";

	final static CSVCmpdData solu10 = TestResources.Reg.getSolubility_10();


	@Test
	public void testTooSmallDatasetShouldFail() throws Exception {
		TrainingsetValidator.setProductionEnv();
		File dataFile = TestUtils.createTempFile("acpmodel", ".jar");
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("requires"));
		exit.checkAssertionAfterwards(new SetTestEnv());
		
		
		mockMain(Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_REGRESSION,
				"-td", solu10.format(), solu10.uri().toString(), 
				"-pr", solu10.property(), 
				"-mo", dataFile.getAbsolutePath()
				);
		File modelFile = TestUtils.createTempFile("acpmodel", ".jar");
		
			mockMain(new String[] {
					Train.CMD_NAME,
					"-pt", ACP_REGRESSION_TYPE,
					"-ds", dataFile.getAbsolutePath(),
					"-ss", strategy(RANDOM_SAMPLING, nrModels),
					"-sc", LinearSVR.ALG_NAME, 
					"-mo", modelFile.getAbsolutePath(),
					"-mn", "trained-acp-reg",
					"--time"
			});
	}

	@Test
	public void testStratifiedSamplingNotForRegression() throws Exception {
		File modelFile = TestUtils.createTempFile("acpmodel", ".jar"); 
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("classification", "only", "Stratified sampling"));
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		try{
			mockMain(new String[] {
					Train.CMD_NAME,
					"-pt", ACP_REGRESSION_TYPE,
					"-ds", Regression.getChang().getAbsolutePath(),
					"-ss", strategy(RANDOM_STRATIFIED_SAMPLING, nrModels),
					"-sc", EpsilonSVR.ALG_NAME, 
					"-mo", modelFile.getAbsolutePath(),
					"-mn", "sdagas",
					"--percentiles", "0",
			});
		} catch(Exception e){}

	}

	@Test
	public void testPrecompThenTrainWithExplicitPercentilesFlag() throws Exception {

		File modelFile = TestUtils.createTempFile("acpmodel", ".jar");

		CmpdData chang = TestResources.Reg.getChang();

		try{
			mockMain(new String[] {
					Train.CMD_NAME,
					"-pt", ACP_REGRESSION_TYPE,
					"-ds", Regression.getSolu100().getAbsolutePath(),
					"-ss", strategy(RANDOM_SAMPLING, nrModels),
					"-sc", LinearSVR.ALG_NAME, //EpsilonSVR.ALG_NAME,
					"-mo", modelFile.getAbsolutePath(),
					"-mn", "trained-acp-reg",
					"--percentiles", "50",
					"--percentiles-data", chang.format(), chang.uri().toString(), // SDFile.FORMAT_NAME, RegressionChang.CHANG_FILE_PATH,
					"--time"
			});
		} catch(Exception e){
			e.printStackTrace(SYS_ERR);
			Assert.fail();
		}

		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", "0.1,0.4,0.9",
				"-cg",
		});

		Assert.assertTrue(systemErrRule.getLog().isEmpty());
	}


	@Test
	public void testTrainAndPredictUsingStaticSeed() throws Exception {

		String seed = "" + (int)(Math.random()*10000);
		

		//// 1

		File modelFile = TestUtils.createTempFile("acpmodel", ".jar"); 

		mockMain(
				Train.CMD_NAME,
				"-pt", ACP_REGRESSION_TYPE,
				"-ds", Regression.getSolu100().getAbsolutePath(),
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-sc", LinearSVR.ALG_NAME, 
				"-mo", modelFile.getAbsolutePath(),
				// "-mn", "trained-acp-reg",
				"--percentiles", "05",
				"--percentiles-data", solu10.format(), solu10.uri().toString(), 
				"--time",
				"--seed", seed
				
		);


		systemOutRule.clearLog();
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", "0.1,0.4,0.9",
				"-cg",
		});
		String pred1 = systemOutRule.getLog();

		//// 2

		File modelFile2 = TestUtils.createTempFile("acpmodel", ".jar"); 

		mockMain(new String[] {
				Train.CMD_NAME,
				"--seed", seed,
				"-pt", ACP_REGRESSION_TYPE,
				"-ds", Regression.getSolu100().getAbsolutePath(),
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-sc", LinearSVR.ALG_NAME, 
				"-mo", modelFile2.getAbsolutePath(),
				"--percentiles", "05",
				"--percentiles-data", solu10.format(), solu10.uri().toString(), 
				"--time",
				
		});
		Map<String,Object> props = ModelIO.getCPSignProperties(modelFile2.toURI());
		Assert.assertEquals("solubility_100", CollectionUtils.getArbitratyDepth(props, PropertyFileStructure.InfoSection.MODEL_NAME_KEY));

		systemOutRule.clearLog();
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", "0.1,0.4,0.9",
				"-cg",
		});

		Assert.assertEquals(pred1, systemOutRule.getLog());
		Assert.assertTrue(systemErrRule.getLog().isEmpty());
		// printLogs();
	}

	@Test
	public void testACPTrainAndPredictSMILES() throws Exception {

		double c=10.0, eps=0.005, gamma=0.004;

		boolean useLinear = true;

		File modelFile = TestUtils.createTempFile("acpmodel", ".jar"); 

		mockMain(new String[] {
				Train.CMD_NAME,
				"-pt", ACP_REGRESSION_TYPE,
				"-ds", Regression.getSolu100().getAbsolutePath(),
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-sc", (useLinear? LinearSVR.ALG_NAME : EpsilonSVR.ALG_NAME)+":c="+c+":epsilon="+eps+ (useLinear? "" : ":gamma="+gamma), 
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "trained-acp-reg",
		});

		Assert.assertTrue(systemErrRule.getLog().isEmpty());


		//Load ACP and predict a mol
		systemOutRule.clearLog();

		List<Double> confs = Arrays.asList(0.8, 0.1, 0.4, .9);
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", TestUtils.toString(confs, ','),
				//				"--output-format", "csv",
		});


		//Cut out prediction string

		JsonArray predictionResult = getJSONArrayFromLog(systemOutRule.getLog());

		Assert.assertEquals("only predicting a single smilesToPredict",1, predictionResult.size());

		assertJSONPred((JsonObject)predictionResult.get(0), false, false, false, confs, null);

		Assert.assertTrue(systemErrRule.getLog().isEmpty());

		// Check that svm and start/end height was correct in the trained model
		ChemCPRegressor sigACP = (ChemCPRegressor) ModelSerializer.loadChemPredictor(modelFile.toURI(), null);
		ChemDataset sp = sigACP.getDataset();
		Assert.assertEquals(1, sp.getDescriptors().size());
		ChemDescriptor d = sp.getDescriptors().get(0);
		Assert.assertTrue(d instanceof SignaturesDescriptor);

		Map<String,Object> props = ModelIO.getCPSignProperties(modelFile.toURI());

		Regressor reg = ((ACPRegressor)sigACP.getPredictor()).getICPImplementation().getNCM().getModel();

		if (reg instanceof LinearSVR) {
			Assert.assertTrue(useLinear);
			Assert.assertEquals(c, TypeUtils.asDouble(CollectionUtils.getArbitratyDepth(props, DefaultMLParameterSettings.COST_PARAM_NAMES.get(0))),0.0001);
			Assert.assertEquals(eps, TypeUtils.asDouble( CollectionUtils.getArbitratyDepth(props, DefaultMLParameterSettings.EPSILON_PARAM_NAMES.get(0))),0.0001);
		} else if (reg instanceof EpsilonSVR) {
			Assert.assertFalse(useLinear);
			Assert.assertEquals(c, TypeUtils.asDouble( CollectionUtils.getArbitratyDepth(props, DefaultMLParameterSettings.COST_PARAM_NAMES.get(0))),0.0001);
			Assert.assertEquals(eps, TypeUtils.asDouble( CollectionUtils.getArbitratyDepth(props, DefaultMLParameterSettings.EPSILON_PARAM_NAMES.get(0))),0.0001);
			Assert.assertEquals(gamma, TypeUtils.asDouble(CollectionUtils.getArbitratyDepth(props, LibSvm.GAMMA_PARAM_NAMES.get(0))),0.0001);

		}

		//		printLogs();
	}


	@Test
	public void testCCPTrainAndPredictSMILES() throws Exception {
		List<Double> confs = Arrays.asList(0.1, 0.4, 0.9);
		//Create temp files for model 
		File modelFile =TestUtils.createTempFile("acpmodel", ".jar");

		mockMain(new String[] {
				Train.CMD_NAME,
				"-pt", ACP_REGRESSION_TYPE,
				"-ds", Regression.getChangCustomDescrAndTransformers().getAbsolutePath(),
				"-ss", strategy(FOLDED_SAMPLING, nrModels),
				"-sc", EpsilonSVR.ALG_NAME,
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "model_name",
		});

		Assert.assertTrue(systemErrRule.getLog().split("\n").length < 2); // Can give an error for the percentiles, but should not give more errors than that
		//		Assert.assertTrue(systemErrRule.getLog().isEmpty());

		ChemCPRegressor acpReg = (ChemCPRegressor) ModelSerializer.loadChemPredictor(modelFile.toURI(), null);
//		Assert.assertTrue(acpReg.getProblem().getDescriptors().get(0) instanceof SignaturesDescriptor);

		List<ChemDescriptor> descriptors = acpReg.getDataset().getDescriptors();
		ChemDescriptor lastDesc = null;
		for (int i=0; i<descriptors.size();i++) {
			if (i<descriptors.size()-1)
				Assert.assertTrue(descriptors.get(i) instanceof CDKPhysChemWrapper);
			else
				lastDesc = descriptors.get(i);
		}
		Assert.assertTrue(lastDesc instanceof SignaturesDescriptor);
		
		Assert.assertEquals(((SignaturesDescriptor)lastDesc).getEndHeight(),Regression.customSignaturesEndH);
		
		Assert.assertTrue(acpReg.getPredictor() instanceof ACPRegressor);
		ACPRegressor model = (ACPRegressor) acpReg.getPredictor();
		Assert.assertEquals(nrModels, model.getPredictors().size());
		Assert.assertTrue(model.getStrategy().isFolded());
		Assert.assertTrue(model.getStrategy() instanceof FoldedSampling);
		Assert.assertFalse("should be at least one transformer saved",
				acpReg.getDataset().getTransformers().isEmpty());
		


		//Load ACP and predict a mol
		systemOutRule.clearLog();
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", TestUtils.toString(confs,' '),
				"--prediction-widths", "1.5",
				"--verbose",
				//				"--output-format", "csv"
		});

		assertContainsNumModels(systemOutRule.getLog(), nrModels, "acp", "regression", "predicted");

		//		printLogs();

		//Cut out predictions
		JsonArray lines = getJSONArrayFromLog(systemOutRule.getLog()); 

		Assert.assertEquals("only predicting a single smilesToPredict",1, lines.size());

		assertJSONPred((JsonObject)lines.get(0), false, false, false, confs, Arrays.asList(0.1));

		//		Assert.assertTrue(systemErrRule.getLog().isEmpty());
		Assert.assertTrue(systemErrRule.getLog().split("\n").length < 2); // Can give an error for the percentiles, but should not give more errors than that
		// printLogs();
	}


	@Test
	public void testPredictSDF() throws Exception {		

		CmpdData amesSDF = TestResources.Cls.getAMES_10();

		List<Double> confs = new ArrayList<>();
		confs.add(0.1);
		confs.add(0.3);
		confs.add(0.5);
		confs.add(0.7);
		confs.add(0.9);

		systemErrRule.clearLog();
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", PreTrainedModels.ACP_REG_LIBSVM.toString(),
				"-p", amesSDF.format(), amesSDF.uri().toString(),
				"-co", TestUtils.toString(confs,','),
								// "-of", "sdf",
				"--verbose",
		});
		Assert.assertTrue(systemErrRule.getLog().isEmpty());
		String predictionOut = systemOutRule.getLog();

		//		original.println(predictionOut);

		//Assert we load three models
		Assert.assertTrue(predictionOut.contains("regression") && predictionOut.contains("ACP") && predictionOut.contains("Loaded")&& predictionOut.contains(" aggregated models"));

		//Cut out predictions
		JsonArray records = getJSONArrayFromLog(predictionOut);

		Assert.assertEquals(10, records.size());

		for (Object line : records){
			assertJSONPred((JsonObject)line, false, false, false, confs, null);
		}
		
		//		printLogs();
	}

	@Test
	public void testPredictCSVFile() throws Exception {
		List<Double> confs = Arrays.asList(0.1,0.3,0.5,0.7,0.9);
		List<Double> dist = Arrays.asList(1.0, 2., 3.);

		//Load ACP and predict an SDF

		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", PreTrainedModels.ACP_REG_LIBSVM.toString(), //CHEM_ACP_REG_LIBSVM_PATH,
				"-p", solu10.format(), solu10.uri().toString(), // "csv", RegressionSolubility.SOLUBILITY_10_FILE_PATH,
				"-co", TestUtils.toString(confs,' '),
				"-pw", TestUtils.toString(dist,','),
				"--verbose"
		});

		String predictionOut = systemOutRule.getLog();

		//		original.println(predictionOut);

		//Assert we load models
		Assert.assertTrue(predictionOut.contains("regression") && predictionOut.contains("ACP") && predictionOut.contains("Loaded")&& predictionOut.contains(" aggregated models"));

		//Cut out predictions
		//		String predictionResult = predictionOut.substring(predictionOut.indexOf("{"), predictionOut.lastIndexOf("}")+1);
		JsonArray recs = getJSONArrayFromLog(predictionOut); //predictionResult.trim().split("\r\n|\r|\n");

		Assert.assertEquals(10, recs.size());

		for (Object line : recs){
			assertJSONPred((JsonObject) line, false, false, false, confs, dist);
		}
	}


	@Test
	public void skipSignificantSignatureCalculation() throws Exception {


		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", PreTrainedModels.ACP_REG_LIBSVM.toString(), // CHEM_ACP_REG_LIBLINEAR_PATH,
				"--smiles", TEST_SMILES,
				"-co", "0.1,0.3,0.5,0.7,0.9",
				//				"-of", "sdf",

		});

		String output = systemOutRule.getLog();
		//		SYS_OUT.println("no calcGradient:\n"+output);

		Assert.assertTrue(! output.contains(JSON.ATOM_VALS_KEY)); // no Atom values should be present! 
		Assert.assertTrue(output.contains(JSON.PREDICTING_SECTION_KEY)); // But should contain predictions


		// CALC GRADIENT
		systemOutRule.clearLog();
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", PreTrainedModels.ACP_REG_LIBSVM.toString(), // CHEM_ACP_REG_LIBLINEAR_PATH,
				"--smiles", TEST_SMILES,
				"-co", "0.1,0.3,0.5,0.7,0.9",
				"--calculate-gradient"
		});


		String output2 = systemOutRule.getLog();
		//		original.println("calcGradient:\n"+output2);

		Assert.assertTrue("Atom values should be present when calculating gradient",output2.contains(JSON.ATOM_VALS_KEY)); // no Atom values should be present! 
		Assert.assertTrue(output2.contains(JSON.PREDICTING_SECTION_KEY));

	}


	@Test
	public void testEncryptModelAndPredict() throws Exception{

		File encModels =TestUtils.createTempFile("encModels", ".gz.jar");

		// Create an encryption key, save to a file
		File encryptKeyFile = generateEncryptionKeyFile();

		//******
		// train and save encrypted
		//******

		mockMain(
				Train.CMD_NAME,
				"-pt", ACP_REGRESSION_TYPE,
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-sc", EpsilonSVR.ALG_NAME,
				"--key-file", encryptKeyFile.getAbsolutePath(),
				"-ds", Regression.getChang().getAbsolutePath(),
				"--model-out", encModels.getPath()
				);


		//**********
		// predict a mol from encrypted file
		//**********

		List<Double> confs = Arrays.asList(0.1,0.4,0.9);
		systemOutRule.clearLog();

		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", encModels.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", confs.toString().substring(1, confs.toString().length()-1),
				"--verbose",
				"--key-file",encryptKeyFile.getAbsolutePath(),
		});

		Assert.assertTrue(systemErrRule.getLog().split("\n").length < 2); // Can give an error for the percentiles, but should not give more errors than that
		assertContainsNumModels(systemOutRule.getLog(), nrModels, "regression");

		//Cut out prediction string
		JsonArray predictionResultEnc = getJSONArrayFromLog(systemOutRule.getLog());
		Assert.assertEquals(1,predictionResultEnc.size());
		assertJSONPred((JsonObject)predictionResultEnc.get(0), false, false, false, confs, null);

		//**********
		// Try without encryption key - should fail!
		//**********
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("encrypted", "must", "supply", "key", "decrypt"));
		// exit.checkAssertionAfterwards(new PrintSysOutput());
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", encModels.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", confs.toString().substring(1, confs.toString().length()-1),
				"--verbose"
		});
	}



	@Test
	public void testAllNCMs() throws Exception {
		Iterator<NCM> iter = FuzzyServiceLoader.iterator(NCM.class);
		
		while (iter.hasNext()) {
			NCM n = iter.next();
			if (n instanceof NCMRegression)
				trainPredict((NCMRegression) n);
		}
		
	}

	private  void trainPredict(NCMRegression ncm) throws IOException, InvalidKeyException, IllegalArgumentException, JsonException {
		List<Double> confs = Arrays.asList(0.6,0.7,.8);


		//Create temp files for the model
		File modelFile =TestUtils.createTempFile("acpmodel", ".jar"); 

		mockMain(new String[] {
				Train.CMD_NAME,
				"-pt", ACP_REGRESSION_TYPE,
				"-ds", Regression.getSolu100().getAbsolutePath(),
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-sc", EpsilonSVR.ALG_NAME,
				"-es", LinearSVR.ALG_NAME,
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "trained-acp-reg",
				"--nonconf-measure", ncm.getName(),
		});

		ChemCPRegressor loadedModel = (ChemCPRegressor) ModelSerializer.loadChemPredictor(modelFile.toURI(), null);
		Assert.assertTrue(loadedModel.getPredictor() instanceof ACPRegressor);
		ACPRegressor predictor = (ACPRegressor) loadedModel.getPredictor();
		Assert.assertEquals(nrModels, predictor.getPredictors().size());
		Assert.assertEquals(predictor.getICPImplementation().getNCM().getClass(),ncm.getClass());

		systemOutRule.clearLog();

		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", TestUtils.toString(confs,','),
				"--verbose",
		});

		assertContainsNumModels(systemOutRule.getLog(), nrModels, "acp", "regression", "predicted");


		JsonArray recs = getJSONArrayFromLog(systemOutRule.getLog()); 

		Assert.assertEquals("only predicting a single smiles",1, recs.size());
		assertJSONPred((JsonObject)recs.get(0), false, false, false, confs, null);
	}


	//	@Test
	@Category(NonSuiteTest.class)
	public void testTrainAndPredictLarge() throws Exception {

		boolean printRes = false;
		String seed = "" + (int)(Math.random()*10000);

		// Create temp files for precomputed data and final model
		File dataFile =TestUtils.createTempFile("precomp", ".jar"); 
		File modelFile =TestUtils.createTempFile("acpmodel", ".svm.jar"); 

		/// FIRST ATTEMPT 

		// Precompute data set
		CmpdData soluLogS = TestResources.Reg.getLogS_1210();
		mockMain(Precompute.CMD_NAME, 
			"-mt", PRECOMPUTE_REGRESSION,
			"-td", soluLogS.format(), soluLogS.uri().toString(),
			"-pr","ClogP"
		);

		

		mockMain(
				Train.CMD_NAME,
				"-pt", ACP_REGRESSION_TYPE,
				"-ds", dataFile.getAbsolutePath(),
				// "-td", SDFile.FORMAT_NAME, getURI("/resources/datasets/regression/solubility@PKKB_2009-reg.sdf").toString(),
				// "-pr", "ClogP",
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-sc", LinearSVR.ALG_NAME, 
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "sdagas",
				"--percentiles", "30",
				"--seed", seed,
				"--progress-bar",
				"--time"
		);

		if (printRes) {
			printLogs();
		}

		systemOutRule.clearLog();
		CSVCmpdData cox2 = TestResources.Cls.getCox2();
		File predictions = TestUtils.createTempFile("preds", ".smi");
		mockMain(
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"-p", cox2.format(), cox2.uri().toString(), // CSVFile.FORMAT_NAME, getURI("/resources/datasets/classification/cox2.smi").toString(), //cas_N6512 cox2
				"-o", predictions.getAbsolutePath(), 
				"--confidences", "0.1,0.4,0.9",
				"-cg",
				"-of", "json",
				"--progress-bar"
		);


		if (printRes) {
			printLogs();
		}
	}

	@Test
	public void testPredefSampling() throws Exception {
		File dataFile = TestUtils.createTempFile("data", ".jar");

		CmpdData calibData = TestResources.Reg.getChang();
		CmpdData properTData = TestResources.Reg.getChang_json();
		
		mockMain(Precompute.CMD_NAME,
				"-mt",PRECOMPUTE_REGRESSION,
				"--property", calibData.property(), // RegressionChang.PROPERTY,
				"--model-out", dataFile.getAbsolutePath(),
				"--model-name", "fabs",
				"--model-data", properTData.format(), properTData.uri().toString(), // RegressionChang.FILE_FORMAT, RegressionChang.CHANG_FILE_PATH, 
				"--calibration-data", calibData.format(), calibData.uri().toString() // RegressionChang.FILE_FORMAT, RegressionChang.CHANG_FILE_PATH
				);
		
		File modelFile =TestUtils.createTempFile("acpmodel", ".svm.jar");
		
		mockMain(
				Train.CMD_NAME,
				"--model-out", modelFile.getAbsolutePath(),
				"--model-name", "fabs",
				"--sampling-strategy", "PreDefined",
				"--data-set", dataFile.getAbsolutePath(), 
				"--predictor-type","ACP_Regression",
				"--ncm",
				"LogNormalized:ncm_beta=0.01",
				"--scorer",
				"epsilon-SVR:epsilon=0.001:cache=512.0:svr-epsilon=0.1:cost=22.627416997969522:coef0=0.0:shrink=false:kernel=2:degree=3:gamma=9.765625E-4",
				"--error-scorer",
				"epsilon-SVR:epsilon=0.001:cache=512.0:svr-epsilon=0.1:cost=22.627416997969522:coef0=0.0:shrink=false:kernel=2:degree=3:gamma=9.765625E-4",
				"--pvalue-calc",
				"Linear_interpolatio"
		);
	}
	
	@Test
	public void testECFP6() throws Exception {
		// Precompute 
		File dataFile =TestUtils.createTempFile("data", ".jar");
		
		CmpdData chang = TestResources.Reg.getChang_gzip();
		
		mockMain(Precompute.CMD_NAME,
				"-mt",PRECOMPUTE_REGRESSION,
				"--property", chang.property(), // RegressionChang.PROPERTY,
				"--model-out", dataFile.getAbsolutePath(),
				"--model-name", "fabs",
				"--train-data", chang.format(), chang.uri().toString(), // RegressionChang.FILE_FORMAT, RegressionChang.CHANG_FILE_PATH,
				"--descriptors", "ECFP6:len=1060"
//				"--calibration-data", RegressionChang.FILE_FORMAT, RegressionChang.CHANG_FILE_PATH
				);

		ChemDataset data =  ModelSerializer.loadDataset(dataFile.toURI(), null);
		List<ChemDescriptor> desc = data.getDescriptors();
		Assert.assertEquals(1, desc.size());
		Assert.assertTrue(desc.get(0) instanceof ECFP6);
		Assert.assertEquals(1060, ((ECFP6)desc.get(0)).getLength());
//		}
		
		// Train
		File modelFile =TestUtils.createTempFile("acpmodel", ".svm.jar");
		
		mockMain(
				Train.CMD_NAME,
				"--model-out", modelFile.getAbsolutePath(),
				"--model-name", "fabs",
				"--sampling-strategy", "random:numSamples=10",
				"--data-set", dataFile.getAbsolutePath(), 
				"--predictor-type","ACP_Regression",
				"--ncm",
				"LogNormalized:ncm_beta=0.01",
				"--scorer",
				"epsilon-SVR:epsilon=0.001:cache=512.0:svr-epsilon=0.1:cost=22.627416997969522:coef0=0.0:shrink=false:kernel=2:degree=3:gamma=9.765625E-4",
				"--error-scorer",
				"epsilon-SVR:epsilon=0.001:cache=512.0:svr-epsilon=0.1:cost=22.627416997969522:coef0=0.0:shrink=false:kernel=2:degree=3:gamma=9.765625E-4",
				"--pvalue-calc",
				"Linear_interpolatio"
		);
		
		// Predict
		mockMain(Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles", TEST_SMILES,
				"-co","0.8");
		
//		printLogs();
	}

}

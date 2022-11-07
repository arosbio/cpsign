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
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.cheminf.ChemCPClassifier;
import com.arosbio.cheminf.ChemCPRegressor;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.utils.GzipEncryption;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.TestChemDataLoader.PreTrainedModels;

@Category(CLITest.class)
public class TestFastAggregate extends CLIBaseTest {
	final static int NR_TO_AGGREGATE = 5;
	final static int NR_PER_TRAIN = 3;
	final static boolean FAIL_FAST = true;
	final static boolean USE_ENCRYPT = true;

	static File[] tempModelsReg, tempModelsClass;
	static String regModelsList, classModelList;

	static File encryptKeyFile = null;
	static EncryptionSpecification spec = null;

	@BeforeClass
	public static void init() throws Exception {
		
		if (USE_ENCRYPT){
			encryptKeyFile = generateEncryptionKeyFile();
			spec = new GzipEncryption();
			try (FileInputStream fis = new FileInputStream(encryptKeyFile);){
				byte[] key = IOUtils.toByteArray(fis);
				spec.init(key);
			}
		}


		tempModelsReg = new File[NR_TO_AGGREGATE];
		
		// REGRESSION 
		
		// precomp
		CSVCmpdData solu100 = TestResources.Reg.getSolubility_100();
		File precompReg = TestUtils.createTempFile("reg", ".jar");
		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_REGRESSION,
				"-td", solu100.format(), solu100.uri().toString(),
				"-pr", solu100.property(),
				"-mo", precompReg.getAbsolutePath(),
				"-mn", "solubility-regression",
				"--silent"}
		);

		for(int i=0; i< NR_TO_AGGREGATE; i++){
			// Set up the actual File
			tempModelsReg[i] = TestUtils.createTempFile("tmp", ".jar");

			StringBuilder splits = new StringBuilder();
			for (int j=1; j<NR_PER_TRAIN; j++) {
				splits.append(i*NR_PER_TRAIN+j);
				splits.append(',');
			}
			splits.append((i+1)*NR_PER_TRAIN);
			//			System.err.println("SPLITS="+splits.toString());

			// Do train
			try {
				mockMain(new String[] {
						Train.CMD_NAME,
						"-pt", ACP_REGRESSION_TYPE,
						"-ds", precompReg.getAbsolutePath(),
						"-ss", strategy(RANDOM_SAMPLING, NR_PER_TRAIN*NR_TO_AGGREGATE),
						"--scorer", LinearSVR.ALG_NAME,
						(USE_ENCRYPT? "--key-file":""),
						(USE_ENCRYPT? encryptKeyFile.getAbsolutePath() : ""),
						"-mo", tempModelsReg[i].getAbsolutePath(),
						"-mn", "solubility-regression",
						"--percentiles","0",
						"--splits",splits.toString(),
						"--silent"
				});
			} catch (Exception e) {
				SYS_ERR.println("Failed setting up splits in TestFastAggregate");
			}
		}
		StringBuilder sb = new StringBuilder(tempModelsReg[0].getPath().length()*NR_TO_AGGREGATE+25);
		for(int i=0; i< NR_TO_AGGREGATE; i++){
			sb.append(tempModelsReg[i].getPath());
			if( i< NR_TO_AGGREGATE-1)
				sb.append(' ');
		}
		regModelsList = sb.toString();


		// INIT CLASSIFICATION MODELS

		tempModelsClass = new File[NR_TO_AGGREGATE];
		
		File precompClas = TestUtils.createTempFile("class", ".jar");
		CmpdData ames = TestResources.Cls.getAMES_126();
		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_CLASSIFICATION,
				"-td", ames.format(), ames.uri().toString(),
				"-l", getLabelsArg(ames.labels()),
				"-pr", ames.property(),
				(USE_ENCRYPT? "--key-file":""),
				(USE_ENCRYPT? encryptKeyFile.getAbsolutePath() : ""),
				"-mo", precompClas.getAbsolutePath(),
				"-mn", "ames-classification",
				"--silent"
		});

		for(int i=0; i< NR_TO_AGGREGATE; i++){
			// Set up the actual File
			tempModelsClass[i] = TestUtils.createTempFile("tmp", ".jar");

			StringBuilder splits = new StringBuilder();
			for (int j=1; j<NR_PER_TRAIN; j++) {
				splits.append(i*NR_PER_TRAIN+j);
				splits.append(',');
			}
			splits.append((i+1)*NR_PER_TRAIN);
			//			System.err.println("SPLITS="+splits.toString());

			// Do train
			mockMain(new String[] {
					Train.CMD_NAME,
					"-pt", ACP_CLASSIFICATION_TYPE,
					"-ds", precompClas.getAbsolutePath(),
					"-ss", strategy(RANDOM_SAMPLING, NR_PER_TRAIN*NR_TO_AGGREGATE),
					"--scorer", LinearSVC.ALG_NAME,
					(USE_ENCRYPT? "--key-file":""),
					(USE_ENCRYPT? encryptKeyFile.getAbsolutePath() : ""),
					"-mo", tempModelsClass[i].getAbsolutePath(),
					"-mn", "ames-classification",
					"--percentiles","0",
					"--splits",splits.toString(),
					"--silent"
			});
		}

		sb = new StringBuilder(tempModelsClass[0].getPath().length()*NR_TO_AGGREGATE+25);
		for(int i=0; i< NR_TO_AGGREGATE; i++){
			sb.append(tempModelsClass[i].getPath());
			if( i< NR_TO_AGGREGATE-1)
				sb.append(' ');
		}
		classModelList = sb.toString();

	}

	@Test
	public void testManual() throws Exception{
		mockMain(new String[]{
				AggregateFast.CMD_NAME, "-h"
		});
	}


	@Test
	public void testAggregateRegression() throws Exception{
		//		exit.checkAssertionAfterwards(new PrintSysOutput());
		// OutputModel
		File modelOut = TestUtils.createTempFile("tmp", ".jar"); 

		// Do aggregate
		mockMain(new String[]{
				AggregateFast.CMD_NAME,
				(FAIL_FAST? "": "-af"),
				"-m", regModelsList,
				"-mo", modelOut.getAbsolutePath(),
				"--time",
				"--progress-bar",
		});
		Assert.assertTrue(systemOutRule.getLog().contains(modelOut.getAbsolutePath()));


		// Validate that the normal models are not deleted
		for (File trainedModel: tempModelsReg){
			Assert.assertTrue(trainedModel.exists());
			Assert.assertTrue("should not have deleted the content of the file",trainedModel.length()> 10000);
		}

		// new ChemCPRegressor(null);
		
		ChemCPRegressor acpReg = (ChemCPRegressor) ModelSerializer.loadChemPredictor(modelOut.toURI(), spec); 
		Assert.assertEquals(NR_TO_AGGREGATE*NR_PER_TRAIN, ((ACPRegressor) acpReg.getPredictor()).getNumTrainedPredictors());

		Assert.assertEquals(getSet(0, NR_TO_AGGREGATE*NR_PER_TRAIN-1),((ACPRegressor) acpReg.getPredictor()).getPredictors().keySet());

		acpReg.predict(getTestMol(), 0.8);

		if (USE_ENCRYPT){
			try {
				ModelSerializer.loadChemPredictor(modelOut.toURI(), null);
				Assert.fail(); // meaning that the model has successfully been encrypted
			} catch(Exception e){

			}
		}
		
		mockMain(Predict.CMD_NAME,
				"--model", modelOut.getAbsolutePath(),
				"--smiles",TEST_SMILES_2,
				"--confidences", ".7,.75,.8",
				(USE_ENCRYPT ? "--key-file":""),
				(USE_ENCRYPT ? encryptKeyFile.getAbsolutePath(): ""));
		//		printLogs();
	}

	private static Set<Integer> getSet(int start, int stop){
		Set<Integer> set = new HashSet<>();
		for (int i=start; i<=stop; i++) {
			set.add(i);
		}
		return set;
	}

	@Test
	public void testAggRegFailingForTCP() throws Exception {
		//		exit.checkAssertionAfterwards(new PrintSysOutput());
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);

		// OutputModel
		File modelOutWithTCP = TestUtils.createTempFile("tmp", ".jar");

		try{
			mockMain(new String[]{
					AggregateFast.CMD_NAME,
					(FAIL_FAST? "": "-af"),
					"-m", PreTrainedModels.TCP_CLF_LIBLINEAR+" "+regModelsList,
					"-mo", modelOutWithTCP.getAbsolutePath(),
					"--time"
			});
			if(FAIL_FAST)
				Assert.fail("tcp model not OK to add");
		} catch(Exception e){
			if(!FAIL_FAST)
				Assert.fail("Should not fail with a faulty model in there");
		}
	}

	@Test
	public void testAggRegFailNr2() throws Exception {
		//		exit.checkAssertionAfterwards(new PrintSysOutput());
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		// OutputModel
		File modelOutWithTCP2 = TestUtils.createTempFile("tmp", ".jar"); 

		try{
			mockMain(new String[]{
					"fast-aggregate",
					(FAIL_FAST? "": "-af"),
					"-m", regModelsList+", "+PreTrainedModels.TCP_CLF_LIBLINEAR.toString(),
					"-mo", modelOutWithTCP2.getAbsolutePath(),
					"--time"
			});
			if(FAIL_FAST)
				Assert.fail("tcp model not OK to add");
		} catch(Exception e){
			if(!FAIL_FAST)
				Assert.fail("Should not fail with a faulty model in there");
		}

	}


	@Test
	public void testAggregateClassification() throws Exception {
		//		exit.checkAssertionAfterwards(new PrintSysOutput());

		// OutputModel
		File modelOut = TestUtils.createTempFile("tmp", ".jar"); 

		// Do aggregate 
		mockMain(new String[]{
				AggregateFast.CMD_NAME,
				(FAIL_FAST? "": "-af"),
				"-m", classModelList,
				"-mo", modelOut.getAbsolutePath(),
				"--time",
				"--progress-bar",
		});

		Assert.assertTrue(systemOutRule.getLog().contains(modelOut.getAbsolutePath()));

		// Validate that the normal models are not deleted
		for(File trainedModel: tempModelsClass){
			Assert.assertTrue(trainedModel.exists());
			Assert.assertTrue("should not have deleted the content of the file",trainedModel.length()> 10000);
		}

		ChemCPClassifier acpClass = (ChemCPClassifier) ModelSerializer.loadChemPredictor(modelOut.toURI(), spec); 
		Assert.assertEquals(NR_TO_AGGREGATE*NR_PER_TRAIN, ((ACPClassifier) acpClass.getPredictor()).getNumTrainedPredictors());

		Assert.assertEquals(getSet(0, NR_TO_AGGREGATE*NR_PER_TRAIN-1),((ACPClassifier) acpClass.getPredictor()).getPredictors().keySet());
		acpClass.predict(getTestMol());

		if (USE_ENCRYPT){
			try{
				ModelSerializer.loadChemPredictor(modelOut.toURI(), null);
				//				acpClass.addModel(modelStream, null);
				Assert.fail(); // meaning that the model has successfully been encrypted
			} catch(Exception e){

			}
		}

		//		printLogs();
		mockMain(Predict.CMD_NAME,
				"--model", modelOut.getAbsolutePath(),
				"--smiles",TEST_SMILES_2,
				(USE_ENCRYPT ? "--key-file":""),
				(USE_ENCRYPT ? encryptKeyFile.getAbsolutePath(): ""));
	}


	@Test
	public void testAggClassFailNr1() throws Exception{
		betweenMethods();
		//		exit.checkAssertionAfterwards(new PrintSysOutput());
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);

		// OutputModel
		File modelOutWithTCP = TestUtils.createTempFile("tmp", ".jar"); // new File("/Users/staffan/Documents/cpsign_agg/agg_test/class_test.jar");

		try{
			mockMain(new String[]{
					AggregateFast.CMD_NAME,
					(FAIL_FAST? "": "-af"),
					"-m", PreTrainedModels.TCP_CLF_LIBLINEAR.toString()+" "+classModelList, //, SIGNATURES_TCP_CLASSIFICATION_LIBSVM_TRAINED_MODEL_PATH+", "+classModelList,
					"-mo", modelOutWithTCP.getAbsolutePath(),
					"--time",
					//					"--debug"
			});
			if (FAIL_FAST)
				Assert.fail("tcp model not OK to add");
		} catch (Exception e){
			if(!FAIL_FAST)
				Assert.fail("Should not fail with a faulty model in there");
		}

		//		original.println(systemOutRule.getLog());
		//		original_err.println(systemErrRule.getLog());

	}

	@Test
	public void testAggClassFailNr2() throws Exception {
		//		exit.checkAssertionAfterwards(new PrintSysOutput());
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		// OutputModel
		File modelOutWithTCP2 = TestUtils.createTempFile("tmp", ".jar"); // new File("/Users/staffan/Documents/cpsign_agg/agg_test/class_test.jar");

		try{
			mockMain(new String[]{
					"fast-aggregate",
					(FAIL_FAST? "": "-af"),
					"-m", classModelList+", "+PreTrainedModels.TCP_CLF_LIBLINEAR.toString(),
					"-mo", modelOutWithTCP2.getAbsolutePath(),
					"--time"
			});
			if (FAIL_FAST)
				Assert.fail("tcp model not OK to add");
		} catch(Exception e){
			if(!FAIL_FAST)
				Assert.fail("Should not fail with a faulty model in there");
		}

	}

}

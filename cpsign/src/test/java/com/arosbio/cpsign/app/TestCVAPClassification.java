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

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.cpsign.app.PrecomputedDatasets.Classification;
import com.arosbio.cpsign.app.params.CLIParameters.ChemOutputType;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.utils.TestUtils;

import ch.qos.logback.core.joran.spi.JoranException;


@Category(CLITest.class)
public class TestCVAPClassification extends CLIBaseTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(TestCVAPClassification.class);


	final static String outputFormat = ChemOutputType.SDF_V3000.toString();

	final static int nrModels=3;


	@AfterClass
	public static void teardown()throws JoranException{
		LoggerUtils.reloadLoggingConfig();
	}

	@Before
	public void betweenMethods() {
		super.betweenMethods();
		try {
			LoggerUtils.reloadLoggingConfig();
		} catch (JoranException e){
			Assert.fail("failed relogging the log-config");
		}
		
	}

	@Test
	public void testUsage() throws Exception {
		LOGGER.debug("Running CVAP train manual");
		mockMain(Train.CMD_NAME); //"inp",
	}

	@Test
	public void testTrainAndPredictUsingStaticSeed() throws Exception {
		LOGGER.debug("Running CVAP testTrainAndPredictUsingStaticSeed");

		String fmt = "json"; // e.g. SDF generates time-stamp in title - issue when comparing the predictions

		String seed = "" + (int)(Math.random()*10000);


		/// FIRST ATTEMPT 

		//Create temp files for model and signatures
		File modelFile = TestUtils.createTempFile("cvapmodel", ".svm.jar"); 

		mockMain(new String[] {
				Train.CMD_NAME,
				"-pt", CVAP_CLASSIFICATION_TYPE,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-sc", C_SVC.ALG_NAME, //LinearSVC.ALG_NAME,
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "sdagas",
				"--seed", seed,
				"-v",
				"--time",
				"--echo"
		});

		systemOutRule.clearLog();
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", "0.1,0.4,0.9",
				"-of", fmt,
		});

		String pred1 = systemOutRule.getLog();

		/// SECOND ATTEMPT

		//Create temp files for model and signatures
		File modelFile2 = TestUtils.createTempFile("cvapmodel", ".svm.jar"); 
		mockMain(
				Train.CMD_NAME,
				"-pt", CVAP_CLASSIFICATION_TYPE,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-sc", C_SVC.ALG_NAME, //LinearSVC.ALG_NAME,
				"-mo", modelFile2.getAbsolutePath(),
				"-mn", "sdagas",
				"--seed", seed
				);


		systemOutRule.clearLog();

		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", "0.1,0.4,0.9",
				"-of", fmt,
		});

		Assert.assertEquals(systemOutRule.getLog(), pred1);

		// SYS_OUT.println(systemOutRule.getLog());
	}

	@Test
	public void trainPartialPredictorAndAggregate() throws Exception {
		LOGGER.debug("Running CVAP trainPartialPredictorAndAggregate");

		String seed = "" + (int)(Math.random()*10000);

		File modelFile = TestUtils.createTempFile("cvapmodel1_2", ".svm.jar"); 
		try {
			mockMain(new String[] {
					Train.CMD_NAME,
					"-pt", CVAP_CLASSIFICATION_TYPE,
					"-ds", Classification.getAmes123().getAbsolutePath(),
					"-ss", strategy(FOLDED_STRATIFIED_SAMPLING, nrModels),
					"-sc", C_SVC.ALG_NAME, //LinearSVC.ALG_NAME,
					"-mo", modelFile.getAbsolutePath(),
					"-mn", "sdagas",
					"--seed", seed,
					"--splits", "1,2",
			});
		} catch (Exception e) {e.printStackTrace();}

		File modelFile3 = TestUtils.createTempFile("cvapmodel3", ".svm.jar");
		try {
			mockMain(new String[] {
					Train.CMD_NAME,
					"-pt", CVAP_CLASSIFICATION_TYPE,
					"-ds", Classification.getAmes123().getAbsolutePath(),
					"-ss", strategy(FOLDED_STRATIFIED_SAMPLING, nrModels),
					"-sc", C_SVC.ALG_NAME, //LinearSVC.ALG_NAME,
					"-mo", modelFile3.getAbsolutePath(),
					"-mn", "sdagas",
//					"--percentiles", "0",
					"--seed", seed,
					"--splits", "3",
			});
		} catch (Exception e) {e.printStackTrace();}


		// AGGREGATE
		File modelFileAggregated = TestUtils.createTempFile("VAPmodel_aggregated", ".svm.jar");
		try {
			mockMain(new String[] {
					AggregateFast.CMD_NAME,
					"-m", ""+modelFile.toString()+ " "+ modelFile3.toString(),
					"-mo", modelFileAggregated.getAbsolutePath(),
			});
		} catch (Exception e) {e.printStackTrace();}
		
		// BUILD WITHOUT SPLITS
		File modelFileNON_SPLITTED = TestUtils.createTempFile("VAPmodelNonSplit", ".svm.jar");
		try {
			mockMain(new String[] {
					Train.CMD_NAME,
					"-pt", CVAP_CLASSIFICATION_TYPE,
					"-ds", Classification.getAmes123().getAbsolutePath(),
					"-ss", strategy(FOLDED_STRATIFIED_SAMPLING, nrModels),
					"-sc", C_SVC.ALG_NAME, //LinearSVC.ALG_NAME,
					"-mo", modelFileNON_SPLITTED.getAbsolutePath(),
					"-mn", "sdagas",
					"--percentiles", "0",
					"--seed", seed,
			});
		} catch (Exception e) {e.printStackTrace();}

		// PRED WITH SPLITTED
		systemOutRule.clearLog();
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileAggregated.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", "0.1,0.4,0.9",
				"-of", outputFormat,
		});

		String predSplitted = systemOutRule.getLog();

		// PREDICT WITH NON-SPLITTED
		systemOutRule.clearLog();
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileNON_SPLITTED.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", "0.1,0.4,0.9",
				"-of", outputFormat,
		});

		String predNonSplitted = systemOutRule.getLog();

		Assert.assertEquals(predNonSplitted, predSplitted);

	}

	@Test
	public void testLinearSVCScorer() throws Exception {
		File modelFile = TestUtils.createTempFile("cvap", ".svm.jar");

		mockMain(new String[] {
				Train.CMD_NAME,
				"-pt", CVAP_CLASSIFICATION_TYPE,
				"-ds", Classification.getAmesCDKDescAndTransformations().getAbsolutePath(),
//				"-td", CSVFile.FORMAT_NAME, "delim:,", getURI("/resources/datasets/classification/BBB.csv").toString(),
//				"-pr", "Blood-Brain-Barrier Penetration",
//				"--labels", "penetrating non-penetrating",
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-sc", LinearSVC.ALG_NAME,
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "sdagas",
				"--percentiles", "0",
				//				"--progress-bar",
				"--time"
		});

		//		printLogs();

		mockMain(
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles", TEST_SMILES_2,
				"--time"
		);

		
		//		PrecomputedDataClassification data = (PrecomputedDataClassification) ModelLoader.loadModel(precompModelFile.toURI(), null);
		//		SYS_ERR.println("NUM RECS: " + data.getProblem().getNumRecords());
		//		
		//		printLogs();

	}

	//	@Test
	//	@Category(NonSuiteTest.class)
	public void testTrainAndPredictLarge() throws Exception {
		LOGGER.debug("Running CVAP testTrainAndPredictLarge");

		String seed = "" + (int)(Math.random()*10000);


		/// FIRST ATTEMPT 
		
		// Precompute
		File dataFile = TestUtils.createTempFile("data", ".jar");
		CSVCmpdData casData = TestResources.Cls.getCAS_N6512();
		mockMain(
				Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_CLASSIFICATION,
				"-td", casData.format(),casData.uri().toString(),
				"-pr", "class",
				"--labels", getLabelsArg(casData.labels()),
				"-mo", dataFile.getAbsolutePath(),
				"-mn", "sdagas",
				"--progress-bar",
				"--time"
		);

		//Create temp files for model and signatures
		File modelFile = TestUtils.createTempFile("cvap", ".svm.jar"); 

		mockMain(new String[] {
				Train.CMD_NAME,
				"-pt", CVAP_CLASSIFICATION_TYPE,
				"-ds", dataFile.getAbsolutePath(),
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-sc", C_SVC.ALG_NAME, //LinearSVC.ALG_NAME,
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "sdagas",
				"--percentiles", "30",
				"--seed", seed,
				"--progress-bar",
				"--time"
		});

		printLogs();

		systemOutRule.clearLog();
		File predictions = TestUtils.createTempFile("preds", ".smi");
		CSVCmpdData cox2 = TestResources.Cls.getCox2();
		mockMain(
				Predict.CMD_NAME,
				//				"-c", ACP_CLASSIFICATION_TYPE,
				"-ds", modelFile.getAbsolutePath(),
				"-p",cox2.format(), cox2.uri().toString(),
				"-o", predictions.getAbsolutePath(), 
				"--confidences", "0.1,0.4,0.9",
				"-cg",
				"-of", outputFormat,
				"--progress-bar"
		);

		//		String pred1 = systemOutRule.getLog();
		//		
		//		SYS_OUT.println(pred1);
		//		SYS_ERR.println(systemErrRule.getLog());
	}

}

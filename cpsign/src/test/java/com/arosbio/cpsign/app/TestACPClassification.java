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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.cheminf.ChemCPClassifier;
import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.descriptors.ChemDescriptor;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.cheminf.descriptors.fp.MACCS;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.cpsign.app.PrecomputedDatasets.Classification;
import com.arosbio.cpsign.app.params.CLIParameters.ChemOutputType;
import com.arosbio.ml.TrainingsetValidator;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.PlattScaledC_SVC;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.nonconf.NCM;
import com.arosbio.ml.cp.nonconf.calc.SmoothedPValue;
import com.arosbio.ml.cp.nonconf.classification.InverseProbabilityNCM;
import com.arosbio.ml.cp.nonconf.classification.NCMMondrianClassification;
import com.arosbio.ml.cp.nonconf.classification.ProbabilityMarginNCM;
import com.arosbio.ml.io.ModelIO;
import com.arosbio.ml.sampling.FoldedSampling;
import com.arosbio.ml.sampling.SingleSample;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.TestChemDataLoader;
import com.arosbio.testutils.TestChemDataLoader.PreTrainedModels;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;

import ch.qos.logback.core.joran.spi.JoranException;


@Category(CLITest.class)
public class TestACPClassification extends CLIBaseTest {

	final static String outputFormat = ChemOutputType.TSV.name();

	final static CmpdData percentilesData = TestResources.Cls.getAMES_126();

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
			systemErrRule.clearLog();
			systemOutRule.clearLog();
		} catch (JoranException e){
			Assert.fail();
		}
	}

	@Test
	public void testBadSMILES() throws IOException {
		String badSMILES = "Predict.csv";
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString(badSMILES, "parameter", "--smiles"));
		
		File modelFile = TestUtils.createTempFile("model", ".jar");
		mockMain(Train.CMD_NAME,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"-mo", modelFile.getAbsolutePath()
				);
		
		File predOutputFile = TestUtils.createTempFile("pred", ".csv");
		mockMain(Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles",badSMILES,
				"--output-format", "CSV",
				"--output", predOutputFile.getAbsolutePath()
				);
//		printLogs();
	}

	@Test
	public void testUsage() throws Exception {
		mockMain(Train.CMD_NAME);
//		printLogs();
	}

	@Test
	public void testFullWF_incl_transformers() throws Exception {

		CmpdData trainData = TestResources.Cls.getAMES_126_gzip();

		File preComDS = TestUtils.createTempFile("datafile", ".csr.jar");
		File bestParamsFile = TestUtils.createTempFile("params", ".txt");

		File modelFile = TestUtils.createTempFile("model", "jar");

		// Precompute using CDK descriptors
		mockMain(Precompute.CMD_NAME,
			"--train-data", trainData.format(), trainData.uri().toString(),
			"--labels", getLabelsArg(trainData.labels()),
			"--property", trainData.property(),
			"--descriptors", "all-cdk",
			"--transformations", "DropMissingDataFeatures","RobustScaler","L1SVCSelecter:C=10",
			"--model-out", preComDS.getAbsolutePath()
			);

		// Tune scorer
		mockMain(TuneScorer.CMD_NAME,
			"--data-set", preComDS.getAbsolutePath(),
			"-gC", // only use small grid of Cost-values
			"--generate@file", bestParamsFile.getAbsolutePath()
		);

		// Train
		mockMain(Train.CMD_NAME,
			"--data-set", preComDS.getAbsolutePath(),
			"@"+bestParamsFile.getAbsolutePath(),
			"--model-out", modelFile.getAbsolutePath()
		);

		// Predict
		mockMain(Predict.CMD_NAME,
			"-m", modelFile.getAbsolutePath(),
			"--smiles", TEST_SMILES
		);
		
			// printLogs();

	}


	@Test
	public void testUserSuppliedDescriptorsALL_EXCEPT_missing_vals() throws Exception {
		// File preComDS = TestUtils.createTempFile("datafile", ".csr.jar");
		File modelFile = TestUtils.createTempFile("model", "jar");
		
		// mockMain(
		// 	Precompute.CMD_NAME,
		// 	"-td", PeptideMulticlass.FILE_FORMAT, PeptideMulticlass.DELIM_ARG, PeptideMulticlass.FILE_PATH, 
		// 	"-pr", PeptideMulticlass.PROPERTY,
		// 	"--descriptors", "usersupplied:all,-Kp_binary_classification,Papp_class","signatures-descriptor", //
		// 	"--transformations", "drop-missing-feats", "standardizer:col_max_index=20",
		// 	"--labels",PeptideMulticlass.LABELS_STRING,
		// 	"-mo", preComDS.getAbsolutePath(),
		// 	"-mn", "dasf",
		// 	"--time");
		// TODO - is this ok?
		

		mockMain(
				Train.CMD_NAME,
				"--data-set", PrecomputedDatasets.Classification.getMissingDataDS().toString(), // preComDS.getAbsolutePath(),
//					"-pr", "Kp_binary_classification",
				"--transformations", "drop-missing-feats", "standardizer:col_max_index=20",
				"-mo", modelFile.getAbsolutePath(),
				"--time"
		);
		

		//		PrecomputedDataClassification precomp = (PrecomputedDataClassification) ModelLoader.loadModel(preFirst.toURI(), null);
		//		List<ChemDescriptor> descriptorsList = precomp.getProblem().getDescriptors();
		//		System.out.println(descriptorsList);
		//		Assert.assertTrue(descriptorsList.get(0) instanceof UserSuppliedDescriptor);
		//		Assert.assertTrue(((UserSuppliedDescriptor) descriptorsList.get(0)).getPropertyNames().contains("PSA"));
		//		Assert.assertTrue(((UserSuppliedDescriptor) descriptorsList.get(0)).getPropertyNames().size()>20); // A bunch of them in there!
		//				printLogs();
	}

	@Test
	public void testTrainWhenHavingMissingValues() throws Exception {
		File modelFile = TestUtils.createTempFile("model", ".jar");
		
		
		// Train - here it should fail!
		expectExit(ExitStatus.USER_ERROR);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("missing", "data", "feature"));
		try {
			mockMain(new String[] {
					Train.CMD_NAME,
					"-ds", PrecomputedDatasets.Classification.getMissingDataDS().toString(),
					"-mo", modelFile.getAbsolutePath(),
					"-mn", "dasf",
					"--time"
			}
					);
			Assert.fail();

		} catch(Exception e){
		}
	}

	@Test
	public void testSmoothedPValueCalc() throws Exception {

		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.jar");

		mockMain(
				Train.CMD_NAME,
				"-pt", ACP_CLASSIFICATION_TYPE,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"--pvalue-calc", "smooth",
				"-sc", C_SVC.ALG_NAME, 
				"-mo", modelFile.getAbsolutePath(),
//				"--percentiles", "10",
				"--progress-bar",
				"--echo"
	);

		ChemCPClassifier loaded = (ChemCPClassifier) ModelSerializer.loadChemPredictor(modelFile.toURI(), null);
		Assert.assertTrue(((ACPClassifier)loaded.getPredictor()).getICPImplementation().getPValueCalculator() instanceof SmoothedPValue);

		//		printLogs();
	}

	
	@Test
	public void testAllNCMs() throws Exception {
		Iterator<NCM> iter = FuzzyServiceLoader.iterator(NCM.class);
		
		while (iter.hasNext()) {
			NCM ncm = iter.next();
			if (ncm instanceof NCMMondrianClassification) {
				if (ncm instanceof ProbabilityMarginNCM || ncm instanceof InverseProbabilityNCM) {
					testNCMTrainAndPredict(ncm, PlattScaledC_SVC.ALG_NAME);
				} else {
					testNCMTrainAndPredict(ncm, LinearSVC.ALG_NAME);
				}
			}
		}
		
	}
	
	private void testNCMTrainAndPredict(NCM ncm, String scorerString) throws IOException {
		File modelFile = TestUtils.createTempFile("acpmodel", ".jar");
		
		systemErrRule.clearLog();
		
		// Train
		mockMain(new String[] {
				Train.CMD_NAME,
				"-pt", ACP_CLASSIFICATION_TYPE,
				"-ds", Classification.get3ClassLTKB().getAbsolutePath(),
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-sc", scorerString, // PlattScaledC_SVC.ALG_NAME, 
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "trained-acp-class",
				"--nonconf-measure", ncm.getName(),
				"--percentiles", "0",
				"--time"
		});

		// Predict
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", "0.1,0.4,0.9",
				"-of", outputFormat,
		});

		Assert.assertTrue(systemErrRule.getLog().isEmpty());
	}


	@Test
	public void testPredefinedSampling() throws Exception {
		File precompFile = TestUtils.createTempFile("acpmodel", ".jar");
		CmpdData calib = TestResources.Cls.getAMES_10();
		CmpdData proper = TestResources.Cls.getAMES_126();
		mockMain(
				Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_CLASSIFICATION,
				"-cd", calib.format(), calib.uri().toString(), 
				//  AmesBinaryClass.FILE_FORMAT, AmesBinaryClass.MINI_FILE_PATH,
				"-md", proper.format(), proper.uri().toString(), 
				// AmesBinaryClass.FILE_FORMAT, AmesBinaryClass.SMALL_FILE_PATH,
				"--labels", getLabelsArg(proper.labels()), // AmesBinaryClass.LABELS_STRING,
				"-pr", proper.property(), // AmesBinaryClass.PROPERTY,
				"-mo", precompFile.getAbsolutePath(),
				"--time"
		);
		
		
		File modelFile = TestUtils.createTempFile("acpmodel", ".jar");
		
		
		mockMain(
				Train.CMD_NAME,
				"-pt", ACP_CLASSIFICATION_TYPE,
				"--data-set", precompFile.getAbsolutePath(),
				"-ss", SingleSample.NAME,
				"-sc", ""+LinearSVC.ALG_ID, 
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "trained-acp-class",
				"--percentiles", "0",
				"--time"
		);

		mockMain(
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles",TEST_SMILES
		);

		//		printLogs();

	}

	@Test
	public void testTooSmallDatasetShouldFail() throws Exception {
		// File dataFile = TestUtils.createTempFile("data", ".jar");
		// mockMain(new String[] {
		// 		Precompute.CMD_NAME,
		// 		"-mt", PRECOMPUTE_CLASSIFICATION,
		// 		"-td", "sdf", getURI("/resources/mdl_formats/MDLv3000.sdf").toString(),
		// 		"--labels", "id1, id2",
		// 		"-pr", "Data",
		// 		"-mo", dataFile.getAbsolutePath(),
		// 		"--time"
		// });
		
		TrainingsetValidator.setProductionEnv();
		File modelFile = TestUtils.createTempFile("acpmodel", ".jar");
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("requires"));
		LoggerUtils.setDebugMode();
		try {
			mockMain(new String[] {
					Train.CMD_NAME,
					"-pt", ACP_CLASSIFICATION_TYPE,
					"-ds", PrecomputedDatasets.Classification.getTooSmallDS().toString(), // dataFile.getAbsolutePath(),
					"-ss", strategy(RANDOM_SAMPLING, nrModels),
					"-sc", ""+LinearSVC.ALG_ID, 
					"-mo", modelFile.getAbsolutePath(),
					"-mn", "trained-acp-class",
					"--percentiles", "0",
					"--time"
			});
		} finally {
			TrainingsetValidator.setTestingEnv();
		}
	}


//	@Test
//	public void testUsageTrainData() throws Exception {
//		//				exit.checkAssertionAfterwards(new PrintSysOutput());
//		//		exit.expectSystemExitWithStatus(ExitStatus.SUCCESS.code);
//		mockMain(new String[] {ExplainArgument.CMD_NAME, "chemistry"}); //, "in", "man"
//		//		printLogs();
//	}


//	@Test
//	public void testLabelsParsedCorrectly() throws Exception {
//		//				LoggerUtils.setDebugMode();
//		// From file!
//		File outFile = TestUtils.createTempFile("class.file", ".jar");
//		mockMain(new String[] {
//				//				"train",
//				"@"+ TEST_RESOURCE_BASE_DIR + "/resources/runconfigs/train_acp_class.txt",
//				"-mo", outFile.getAbsolutePath(),
//		});
//
//		//		original.println(systemOutRule.getLog());
//	}

	@Test
	public void testStratifiedVsNonStratified() throws Exception {
		int seed=12215;
		//Create temp file for the model
		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.jar"); 
		trainModelStratOrNot(modelFile, false, seed);

		ChemCPClassifier signACP = (ChemCPClassifier) ModelSerializer.loadChemPredictor(modelFile.toURI(), null);
		Assert.assertNull(signACP.getLowPercentile());
		Assert.assertNull(signACP.getHighPercentile());

		systemOutRule.clearLog();

		mockMain(
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", "0.1 0.4 0.9",
//				"-cg",
//				"--echo",
				"-q"
		);

		String sysOutPredictNotStratified=systemOutRule.getLog();

		//---------------------------------------------
		// STRATIFIED SHOULD GIVE A DIFFERENT OUTPUT
		//---------------------------------------------

		//Create temp file for the model
		File modelFileStrat = TestUtils.createTempFile("acpmodel", ".svm.jar"); 

		trainModelStratOrNot(modelFileStrat, true, seed);

		systemOutRule.clearLog();

		mockMain(
				Predict.CMD_NAME,
				"-m", modelFileStrat.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", "0.1,0.4,0.9",
				"-q"
		);

		// Make sure that the prediction is different in the two models!
		String stratOut = systemOutRule.getLog();
		//		original.println("Stratified: " + stratOut);

		Assert.assertNotEquals(sysOutPredictNotStratified, stratOut);
	}

	private static void trainModelStratOrNot(File outputFile, boolean strat, int seed) throws Exception {
		mockMain(
				Train.CMD_NAME,
				"-pt", ACP_CLASSIFICATION_TYPE,
				"--data-set", Classification.getAmes123().getAbsolutePath(),
				"-sc", C_SVC.ALG_NAME, 
				"-mo", outputFile.getAbsolutePath(),
				"-mn", "sdagas",
				"--seed", ""+seed,
				"--sampling-strategy", strategy((strat? RANDOM_STRATIFIED_SAMPLING: RANDOM_SAMPLING), nrModels) // (strat?  strategy(RANDOM_STRATIFIED_SAMPLING, nrModels)RANDOM_STRATIFIED_SAMPLING : RANDOM_SAMPLING),
		);
	}



	@Test
	public void testTrainAndPredictUsingStaticSeed() throws Exception {

		String seed = "" + (int)(Math.random()*10000);


		/// FIRST ATTEMPT 

		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.jar");

		mockMain(
				Train.CMD_NAME,
				"-pt", ACP_CLASSIFICATION_TYPE,
				"--data-set", Classification.getAmes123().getAbsolutePath(),
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-sc", C_SVC.ALG_NAME, 
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "sdagas",
//				"--percentiles", "0",
				"--seed", seed,
				"--progress-bar",
				"--echo"
		);

		//		printLogs();
		systemOutRule.clearLog();
		mockMain(
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", "0.1,0.4,0.9",
				"-of", outputFormat,
				"--progress-bar",
				"-q"
		);

		String pred1 = systemOutRule.getLog();

		/// SECOND ATTEMPT

		//Create temp files for model and signatures
		File modelFile2 = TestUtils.createTempFile("acpmodel", ".svm.jar"); 
		mockMain(
				Train.CMD_NAME,
				"-pt", ACP_CLASSIFICATION_TYPE,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-sc", C_SVC.ALG_NAME,
				"-mo", modelFile2.getAbsolutePath(),
				"-mn", "sdagas",
				"--seed", seed
		);

//		printLogs();

		systemOutRule.clearLog();

		mockMain(
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", "0.1,0.4,0.9",
				"-of", outputFormat,
				"-q"
		);

		Assert.assertEquals(systemOutRule.getLog(), pred1);
//		printLogs();
		//		SYS_ERR.println(systemErrRule.getLog());
	}

	@Test
	public void testInputColumnsStillInOutput() throws Exception {
		// Input file to predict
		CSVCmpdData solu_multi_col = TestResources.Reg.getSolubility_10_multicol();
		List<String> originalHeaders = null;
		try(InputStream is = solu_multi_col.url().openStream();
			InputStreamReader reader = new InputStreamReader(is);
			CSVParser p = CSVFormat.DEFAULT.builder().setDelimiter(solu_multi_col.delim()).setHeader().setSkipHeaderRecord(true).build().parse(reader); 
			){
				Iterator<CSVRecord> recs = p.iterator();
				recs.next(); // read first to get the header
				originalHeaders = p.getHeaderNames();
				
			}
		
		// Create a file for prediction output
		File predOut = TestUtils.createTempFile("pred", ".csv");

		mockMain(
				Predict.CMD_NAME,
				"-m", PreTrainedModels.ACP_CLF_LIBLINEAR.toString(), 
				"--predict-file", solu_multi_col.format(), solu_multi_col.uri().toString(), 
				"-of", "TSV",
				"-o", predOut.toString()
		);

		// Check that original headers are still there, and they have the same order!
		// SYS_ERR.println(FileUtils.readFileToString(predOut, StandardCharsets.UTF_8));
		try(InputStream is = new FileInputStream(predOut);
		InputStreamReader reader = new InputStreamReader(is);
		CSVParser p = CSVFormat.DEFAULT.builder().setDelimiter('\t').setHeader().setSkipHeaderRecord(
			true).build().parse(reader);){
			
			List<String> predHeaders = p.getHeaderNames();
			// System.err.println("predHeaders: " + predHeaders);
			// System.err.println("originalHeaders: " + originalHeaders);

			Assert.assertEquals(originalHeaders, predHeaders.subList(0, originalHeaders.size()));
			Assert.assertTrue(predHeaders.size() >= originalHeaders.size()+2);
		}

		//		printLogs();
	}

	@Test
	public void testACPTrainAndPredictSMILES() throws Exception {
		String modelCat = "ames-mutagen";
//		int startH=0, endH=2;
		double c=10.0, eps=0.005, gamma=0.004;
		List<Double> confs = Arrays.asList(0.6, 0.7, 0.8, 0.9);

		//Create temp files for model and signatures
		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.jar"); 
		mockMain(
				"train",
				"-pt", ACP_CLASSIFICATION_TYPE,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-sc", C_SVC.ALG_NAME+":cost="+c+":gamma="+gamma+":epsilon="+eps+":cache=1000:shrink=true",
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "sdagas sdfa",
				"--verbose",
				"--percentiles-data", percentilesData.format(), percentilesData.uri().toString(), // AmesBinaryClass.FILE_FORMAT, AmesBinaryClass.MINI_FILE_PATH,
				"--percentiles", "3",
				"--model-category", modelCat
		);

		systemOutRule.clearLog();
		mockMain(
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--confidences", confs.toString().substring(1,confs.toString().length()-1),
				"--verbose"
		);

		String predictOutput = systemOutRule.getLog();
		List<String> lines = Arrays.asList(predictOutput.split("\n"));

		boolean foundLoadingLine = false;
		for (String l: lines) {
			String line = l.toLowerCase();
			if (line.contains("load") && line.contains(""+nrModels))
				foundLoadingLine = true;
		}
		Assert.assertTrue(foundLoadingLine);


		//Cut out prediction string
		JsonArray predictionResult = getJSONArrayFromLog(systemOutRule.getLog()); 
		for (Object record: predictionResult)
			CLIBaseTest.assertJSONPred((JsonObject) record, true, false, false, confs, null);

		// Check that svm and start/end height was correct in the trained model
		ChemCPClassifier sigACP = (ChemCPClassifier) ModelSerializer.loadChemPredictor(modelFile.toURI(), null);
		ChemDataset sp = sigACP.getDataset();
		ChemDescriptor d = sp.getDescriptors().get(0);
		Assert.assertTrue(d instanceof SignaturesDescriptor);

//		Assert.assertEquals(startH, ((SignaturesDescriptor) d).getStartHeight());
//		Assert.assertEquals(endH, ((SignaturesDescriptor) d).getEndHeight());

		// No longer possible to test!
		Map<String,Object> props = ModelIO.getCPSignProperties(modelFile.toURI());
		Assert.assertEquals(TypeUtils.asDouble(CollectionUtils.getArbitratyDepth(props, "cost")), c, 0.0001);
		Assert.assertEquals(TypeUtils.asDouble(CollectionUtils.getArbitratyDepth(props, "epsilon")), eps, 0.0001);
		Assert.assertEquals(TypeUtils.asDouble(CollectionUtils.getArbitratyDepth(props, "gamma")), gamma, 0.0001);
		Assert.assertEquals(TypeUtils.asBoolean(CollectionUtils.getArbitratyDepth(props, "shrink")), true);
		Assert.assertEquals(TypeUtils.asDouble(CollectionUtils.getArbitratyDepth(props, "cache")), 1000d,0.0001);

		Assert.assertEquals(modelCat, sigACP.getModelInfo().getCategory());

		Assert.assertTrue(systemErrRule.getLog().isEmpty());
	}

	@Test
	public void testCCPTrainAndPredictSMILES() throws Exception {


		//Create temp files for model and signatures
		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.cpsign"); 

		mockMain(new String[] {
				Train.CMD_NAME,
				"-pt", ACP_CLASSIFICATION_TYPE,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"-sc", C_SVC.ALG_NAME,
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "model-name",
				"--percentiles", "0",
				"-ss", strategy(FOLDED_SAMPLING, nrModels),
		});

		Assert.assertTrue(systemErrRule.getLog().isEmpty());

		ChemCPClassifier trainedModel = (ChemCPClassifier) ModelSerializer.loadChemPredictor(modelFile.toURI(), null);
		Assert.assertTrue(trainedModel.getPredictor() instanceof ACPClassifier);
		ACPClassifier model = (ACPClassifier) trainedModel.getPredictor();
		Assert.assertEquals(nrModels, model.getPredictors().size());
		Assert.assertTrue("True strategy="+model.getStrategy(),model.getStrategy() instanceof FoldedSampling);


		//Load CCP and predict a mol

		systemOutRule.clearLog();

		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"--verbose"
		});

		String predictOutput = systemOutRule.getLog();
		List<String> lines = Arrays.asList(predictOutput.split("\n"));

		boolean foundLoadingLine = false;
		for (String l: lines) {
			String line = l.toLowerCase();
			if (line.contains("load") && line.contains(""+nrModels))
				foundLoadingLine = true;
		}
		Assert.assertTrue(foundLoadingLine);

		//Cut out prediction string
		JsonArray predictionResult = getJSONArrayFromLog(systemOutRule.getLog());
		for (Object record: predictionResult)
			CLIBaseTest.assertJSONPred((JsonObject) record, true, false, false, null, null);

		// Assert that no failures has happened

		Assert.assertTrue(systemErrRule.getLog().isEmpty());
	}


	@Test
	public void testCompressAndEncrypt() throws Exception{
		// setup output files
		File plainModels = TestUtils.createTempFile("plainModel.svm", ".cpsign"); //new File("/Users/staffan/Desktop/testCPSign/models"); //
		File encModels = TestUtils.createTempFile("encModels", ".cpsign"); //new File("/Users/staffan/Desktop/testCPSignEnc/models"); //

		int numPercentiles = 50;

		// Create an encryption key, save to a file
		File encryptKeyFile = generateEncryptionKeyFile();

		//******
		// train saving as PLAIN
		//******

		mockMain(Train.CMD_NAME,
				"-pt", ACP_CLASSIFICATION_TYPE,
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-sc", C_SVC.ALG_NAME,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"--percentiles-data", percentilesData.format(), percentilesData.uri().toString(), //AmesBinaryClass.FILE_FORMAT, AmesBinaryClass.MINI_FILE_PATH,
				"--model-out", plainModels.getPath(),
				"-mn", "model-name",
				"--seed", "42",
				"--percentiles", ""+numPercentiles
				);


		//******
		// train and save as encrypted
		//******

		mockMain(Train.CMD_NAME,
				"-pt", ACP_CLASSIFICATION_TYPE,
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-sc", C_SVC.ALG_NAME,
				"--key-file", encryptKeyFile.getAbsolutePath(),
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"--model-out", encModels.getPath(),
				"-mn", "model-name",
				"--seed", "42",
				"--percentiles", ""+numPercentiles,
				"--percentiles-data", percentilesData.format(), percentilesData.uri().toString() // AmesBinaryClass.FILE_FORMAT, AmesBinaryClass.MINI_FILE_PATH
				);


		//**********
		//Load ACP and predict a mol (PLAIN)
		//**********

		systemOutRule.clearLog();

		mockMain(
				Predict.CMD_NAME,
				"-m", plainModels.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"-cg",
				"--verbose"
		);
		String predictionOut = systemOutRule.getLog();

		//Assert we load nrModels models
		Assert.assertTrue(predictionOut.contains("Loaded") && predictionOut.contains(nrModels + " aggregated models"));

		//Cut out prediction string
		JsonArray predLines = getJSONArrayFromLog(predictionOut);
		Assert.assertEquals(1, predLines.size());
		assertJSONPred((JsonObject)predLines.get(0), true, true, false, null, null);


		//**********
		//Load ACP and predict a mol (ENCRYPTED)
		//**********

		systemOutRule.clearLog();
		mockMain(
				Predict.CMD_NAME,
				"--key-file", encryptKeyFile.getAbsolutePath(),
				//				"-c", ACP_CLASSIFICATION_TYPE,
				"-m", encModels.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"-cg"
		);

		String predictionOutEnc = systemOutRule.getLog();

		//Assert we load nrModels models
		Assert.assertTrue(predictionOut.contains("Loaded") && predictionOut.contains(nrModels + " aggregated models"));
		//		for (int i=0; i< nrModels; i++){
		//			Assert.assertTrue(predictionOutEnc.contains("Loaded model " + i +"/"+nrModels));
		//			predictionOutEnc = predictionOutEnc.substring(predictionOutEnc.indexOf("\n", predictionOutEnc.lastIndexOf("Loaded model " + i +"/"+nrModels))); // cut away what we just read
		//		}
		//		// do only load nrModels models!
		//		Assert.assertFalse(predictionOutEnc.contains("Loaded model"));

		//Cut out prediction string
		JsonArray predLinesEnc = getJSONArrayFromLog(predictionOutEnc);
		Assert.assertEquals(1, predLinesEnc.size());
		Assert.assertEquals(predLines.get(0), predLinesEnc.get(0));

		// Assert that no failures has happened
		Assert.assertTrue(systemErrRule.getLog().isEmpty());
		
		// Test cannot predict using wrong license when encrypted
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("encrypted"));
		mockMain(
				Predict.CMD_NAME,
				"-m", encModels.getAbsolutePath(),
				"--smiles",TEST_SMILES,
				"-cg",
				"--verbose"
		);

	}




	@Test
	public void testCalculateSignificantSignature() throws Exception {
		File dataFile = TestUtils.createTempFile("data", ".jar");
		CmpdData ames = TestResources.Cls.getAMES_126_gzip();

		mockMain(
				Precompute.CMD_NAME,
				"--echo",
				"-mt", PRECOMPUTE_CLASSIFICATION,
				"-mo",dataFile.getAbsolutePath(),
				"-td", ames.format(), ames.uri().toString(), // TRAIN_FORMAT, TRAIN_FILE,
				"-pr", ames.property(), // AmesBinaryClass.PROPERTY,
				"--labels", getLabelsArg(ames.labels()), //AmesBinaryClass.LABELS_STRING,
				"-mn", "sdagas",
				"--descriptors", "ALOGPDescriptor","VABCDescriptor","XLogPDescriptor","TPSADescriptor", "signatures-descriptor:1:1"
		);
		
		File trainedModelFile = TestUtils.createTempFile("trainedACP", ".jar");

		mockMain(
				Train.CMD_NAME,
				"--echo",
				"-pt", ACP_CLASSIFICATION_TYPE,
				"-ds", dataFile.getAbsolutePath(),
				"-mo",trainedModelFile.getAbsolutePath(),
				"-ss",strategy(RANDOM_SAMPLING, nrModels),
				"--pvalue-calc", "smooth",
				"-sc", LinearSVC.ALG_NAME, 
				"-mn", "sdagas",
				"--percentiles", "25",
				"--percentiles-data", percentilesData.format(), percentilesData.uri().toString() //TRAIN_FORMAT, TRAIN_FILE
		);

		systemOutRule.clearLog();
		List<Double> confs = Arrays.asList(0.1,0.5,0.7,.9);
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", trainedModelFile.getAbsolutePath(),
				"--smiles", TEST_SMILES,
				"-co", TestUtils.toString(confs, ' '), 
				"-cg",
				"-of", "json",
				//					"-p", "tsv", SOLUBILITY_10_FILE_PATH,
		});

		//			printLogs();
		String outputWithGradient = systemOutRule.getLog();
		//	
		//			//		original.println(outputWithGradient);
		//			Assert.assertTrue(systemErrRule.getLog().isEmpty());
		//	
		JsonArray predWithGrad = getJSONArrayFromLog(outputWithGradient.split("=-",2)[1]);
		Assert.assertEquals(1, predWithGrad.size());
		CLIBaseTest.assertJSONPred((JsonObject)predWithGrad.get(0), true, true, false, confs, null);

	}


	@Test
	public void testTrainFromTrainedModelClassificationModel() throws Exception{
		// CHECK THAT THIS WILL NOT WORK

		File trainedModel = TestUtils.createTempFile("trainedClass", ".cpsign");

		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("precomputed", "chem","predictor")); //,"loaded"
		mockMain(
				new String[]{
						Train.CMD_NAME,
						"-pt", ACP_CLASSIFICATION_TYPE,
						"--scorer", LinearSVC.ALG_NAME,
						"-ss", strategy(RANDOM_SAMPLING, nrModels),
						"-ds", TestChemDataLoader.PreTrainedModels.ACP_CLF_LIBLINEAR.toString(),
						"-mo", trainedModel.getAbsolutePath(),
						"-mn", "model_name",
				});

	}

	@Test
	public void testTrainFromTrainedModelRegressionModel() throws Exception{
		File trainedModel = TestUtils.createTempFile("trainedClass", ".cpsign");

		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("precomputed", "data", "due to"));
		mockMain(new String[]{
				Train.CMD_NAME,
				"-pt", ACP_CLASSIFICATION_TYPE,
				"--scorer", LinearSVC.ALG_NAME,
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-ds", TestChemDataLoader.PreTrainedModels.ACP_REG_LIBSVM.toString(),
				"-mo", trainedModel.getAbsolutePath(),
				"-mn", "model_name",
		});

	}


	@Test
	public void testNoModelOutGiven() throws Exception {

		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("Missing", "model-out", "required"));
		CmpdData ames = TestResources.Cls.getAMES_10();
		// PRECOMPUTE
		mockMain(
				Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_CLASSIFICATION,
				"-mn", "dsaf",
				"--train-data", ames.format(), ames.uri().toString()
		);
	}

	// /**
	//  * Helper method to count number of lines in a file.
	//  * 
	//  * @param filename
	//  * @return
	//  * @throws IOException
	//  */
	// public static int countLines(String filename) throws IOException {
	// 	InputStream is = new BufferedInputStream(new FileInputStream(filename));
	// 	try {
	// 		byte[] c = new byte[1024];
	// 		int count = 0;
	// 		int readChars = 0;
	// 		boolean empty = true;
	// 		while ((readChars = is.read(c)) != -1) {
	// 			empty = false;
	// 			for (int i = 0; i < readChars; ++i) {
	// 				if (c[i] == '\n') {
	// 					++count;
	// 				}
	// 			}
	// 		}
	// 		return (count == 0 && !empty) ? 1 : count;
	// 	} finally {
	// 		is.close();
	// 	}
	// }

	//	@Test
	public void testAppend() {
		String[] first = new String[] {"train", "input", "something"};

		for (String s : append(first, new String[] {"some", "other", "input"})) {
			SYS_OUT.println(s);
		}
	}

	@Category(PerformanceTest.class)
	//	@Test
	public void testUseSeveralTransformers() throws Exception {

		CmpdData ames126 = TestResources.Cls.getAMES_126();
		CmpdData ames1337 = TestResources.Cls.getAMES_1337();
		String labels = getLabelsArg(ames126.labelsStr());

		String[] validateArgs = new String[] {
				//				"--echo",
				"--predict-file", ames1337.format(), ames1337.uri().toString(),
				// Same endpoint and labels
				"-cp", "0.8, 0.9",
		};


		// Only signatures - vanilla! 

		System.out.println("Running vanilla signatures");
		runTests(
				new String[] {
						"-pt", ACP_CLASSIFICATION_TYPE,
						"-td", ames126.format(), ames126.uri().toString(), 
						"-pr", ames126.property(),
						"--labels", labels,
						"-ss", strategy(RANDOM_SAMPLING, nrModels),
						"--pvalue-calc", "smooth",
						"-sc", LinearSVC.ALG_NAME,
				}, 
				validateArgs,
				false);

		// Serveral transformers
		System.out.println("Running CDK + signatures");
		runTests(
				new String[] {
						"-pt", ACP_CLASSIFICATION_TYPE,
						"-td", ames126.format(), ames126.uri().toString(), 
						"-pr", ames126.property(),
						"--labels", labels,
						"-ss", strategy(RANDOM_SAMPLING, nrModels),
						"--pvalue-calc", "smooth",
						"-sc", LinearSVC.ALG_NAME,
						"-d", "all-cdk", "signatures",
						"--transformations", 
						"variancebasedselection", // Remove all 0-features from CDK
						"standardizer:colmaxindex=177", // roughly 160 non-zero features
						"zeroMaxScaler:colMinIndex=178" // just take something here..
				}, 
				validateArgs,
				true);


	}

	private void runTests(String[] trainArgs, String[] validateArgs, boolean printFeats) throws Exception {

		File modelFile = TestUtils.createTempFile("someTrainedFile", ".jar");

		// Train
		mockMain(
				append(
						new String[] {"train",
								"--percentiles", "0",
								"--model-out", modelFile.getAbsolutePath(),
								"-mn" , "blargh",
						},
						trainArgs
						));

		mockMain(new String[] {
				"list-features",
				"--model-in", modelFile.getAbsolutePath(),
				"--result-format", "csv",
		});

		// Predict
		mockMain(
				append(
						new String[] {"validate",
								"--model-in", modelFile.getAbsolutePath(), 
						},
						validateArgs
						));

	}


	//	@Test
	@Category(PerformanceTest.class)
	public void testTrainAndPredictLarge() throws Exception {

		String seed = "" + (int)(Math.random()*10000);


		/// FIRST ATTEMPT 

		//Create temp files for model and signatures
		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.jar"); 
		CSVCmpdData cas = TestResources.Cls.getCox2();
		mockMain(new String[] {
				Train.CMD_NAME,
				"-pt", ACP_CLASSIFICATION_TYPE, // TODO does this work??
				"-td", cas.format(), "delim="+cas.delim() ,cas.uri().toString(), // "delim:\\t" getURI("/resources/datasets/classification/cas_N6512.smi").toString(),
				"-pr", "class",
				"--labels", "0 1",
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-sc", "libsvm", //"liblinear",
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
				"-m", modelFile.getAbsolutePath(),
				"-p", cox2.format(), cox2.uri().toString(), // CSVFile.FORMAT_NAME, getURI("/resources/datasets/classification/cox2.smi").toString(),
				"-o", predictions.getAbsolutePath(), 
				"--confidences", "0.1,0.4,0.9",
				"-cg",
				"-of", outputFormat,
				"--progress-bar"
		);

		Assert.assertTrue(systemErrRule.getLog().isEmpty());

		//		String pred1 = systemOutRule.getLog();

		//		SYS_OUT.println(pred1);
		//		SYS_ERR.println(systemErrRule.getLog());
	}
	
	@Test
	public void testMACCS_FP() throws Exception {
		// Precompute 
		File dataFile = TestUtils.createTempFile("data", ".jar");

		CmpdData ames = TestResources.Cls.getAMES_126();
		
		mockMain(Precompute.CMD_NAME,
				"-mt",PRECOMPUTE_CLASSIFICATION,
				"--property", ames.property(),
				"--labels", getLabelsArg(ames.labels()),
				"--model-out", dataFile.getAbsolutePath(),
				"--model-name", "fabs",
				"--train-data", ames.format(), ames.uri().toString(),
				"--descriptors", "MACCS"
				);
		ChemDataset data =  ModelSerializer.loadDataset(dataFile.toURI(), null);
		List<ChemDescriptor> desc = data.getDescriptors();
		Assert.assertEquals(1, desc.size());
		Assert.assertTrue(desc.get(0) instanceof MACCS);
		
		// Train
		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.jar");
		
		mockMain(
				Train.CMD_NAME,
				"--model-out", modelFile.getAbsolutePath(),
				"--model-name", "fabs",
				"--sampling-strategy", "random:numSamples=10",
				"--data-set", dataFile.getAbsolutePath(), 
				"--predictor-type","ACP_classification",
				"--pvalue-calc",
				"Linear_interpolatio"
		);
		
		// Predict
//		LoggerUtils.setDebugMode(SYS_ERR);
		mockMain(Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles", TEST_SMILES 
				);
		
//		printLogs();
	}

}

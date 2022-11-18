/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.ConfAI;
import com.arosbio.commons.Version;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.transform.feature_selection.DropColumnSelector;
import com.arosbio.data.transform.feature_selection.SelectionCriterion;
import com.arosbio.data.transform.feature_selection.SelectionCriterion.Criterion;
import com.arosbio.data.transform.feature_selection.VarianceBasedSelector;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.IOSettings;
import com.arosbio.ml.TrainingsetValidator;
import com.arosbio.ml.algorithms.Classifier;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.algorithms.svm.PlattScaledC_SVC;
import com.arosbio.ml.algorithms.svm.SVC;
import com.arosbio.ml.algorithms.svm.SVR;
import com.arosbio.ml.cp.CPRegressionPrediction;
import com.arosbio.ml.cp.acp.ACP;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.icp.ICPClassifier;
import com.arosbio.ml.cp.icp.ICPRegressor;
import com.arosbio.ml.cp.nonconf.calc.SplineInterpolatedPValue;
import com.arosbio.ml.cp.nonconf.classification.InverseProbabilityNCM;
import com.arosbio.ml.cp.nonconf.classification.NCMMondrianClassification;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.cp.nonconf.regression.AbsDiffNCM;
import com.arosbio.ml.cp.nonconf.regression.NormalizedNCM;
import com.arosbio.ml.cp.tcp.TCP;
import com.arosbio.ml.cp.tcp.TCPClassifier;
import com.arosbio.ml.interfaces.AggregatedPredictor;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.sampling.FoldedSampling;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.sampling.SamplingStrategy;
import com.arosbio.ml.sampling.SingleSample;
import com.arosbio.ml.vap.avap.AVAPClassifier;
import com.arosbio.ml.vap.ivap.IVAPClassifier;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.GzipEncryption;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.ModelComparisonUtils;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.UnitTestInitializer;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;


@Category(UnitTest.class)
public class TestSaveLoadModel extends UnitTestInitializer {

	// final URI trainfileClassification = new File(AmesBinaryClass.SMALL_FILE_PATH+GZIP_SUFFIX).toURI();
	// final URI trainfileRegression = new File(RegressionSolubility.SOLUBILITY_10_FILE_PATH).toURI();

	// final File sparseClassificationProblem = getFile("/resources/numerical/classification/train.svm");
	// final File sparseRegressionProblem = new File(NumericalSVMLIGHTFiles.REGRESSION_HOUSING_SCALE_FILE_PATH);

	final boolean storeAsTmp=true, linearKernelSVM=true, CCP = false, encrypt=true, useStereoSignatures=false;
	final int ccpFolds = 6;
	final int nrModels = 5;
	final double calibSize = 0.2, confidence=0.8;

	static EncryptionSpecification spec = null;



	@BeforeClass
	public static void init() throws Exception {
		LoggerUtils.reloadLoggingConfig(); 
		TrainingsetValidator.setTestingEnv();
		spec = new GzipEncryption();
		spec.init(spec.generateRandomKey(16));
	}

	@AfterClass
	public static void taredown() throws Exception {
		TrainingsetValidator.setProductionEnv();
	}


	// @Test
	// public void testMountData() throws Exception {
		

	// 	File outputFile = createTempFile("someFile", "txt");

	// 	String text1 = "This is a metpred enabled model";
	// 	String text2 = "This is encrypted data";
	// 	String text3 = "Some more data";
	// 	List<MountData> data = new ArrayList<>();
	// 	data.add(new MountData("metpred.txt", text1));
	// 	data.add(new MountData("encrypted.txt", text2));
	// 	data.get(1).encryptData(spec);
	// 	data.add(new MountData("new_dir/file.txt", text3));
	// 	data.add(new MountData("spam.svmlight", getURIFromFullPath(NumericalSVMLIGHTFiles.CLASSIFICATION_2CLASS_20_PATH)));
	// 	data.get(3).encryptData(spec);


	// 	Assert.assertEquals(data.size(), ModelSerializer.listMountedData(outputFile.toURI()).size());
	// 	Assert.assertEquals(text1, ModelSerializer.getMountedDataAsString(outputFile.toURI(), "metpred.txt", spec));
	// 	Assert.assertEquals(text2, ModelSerializer.getMountedDataAsString(outputFile.toURI(), "encrypted.txt", spec));
	// 	Assert.assertEquals(text3, ModelSerializer.getMountedDataAsString(outputFile.toURI(), "new_dir/file.txt", spec));
	// 	File changLoaded = ModelSerializer.getMountedDataAsTmpFile(outputFile.toURI(), "chang.sdf", spec);
	// 	Assert.assertEquals(UriUtils.getFile(getURIFromFullPath(RegressionChang.CHANG_FILE_PATH)).length(), changLoaded.length());
	// }

	// @Test
	// public void testModelInfo() throws Exception {
	// 	Version buildVersion = ModelSerializer.getModelBuildVersion(getURIFromFullPath(PreTrainedModels.CHEM_ACP_CLF_LIBLINEAR_PATH));
	// 	System.out.println(buildVersion);
	// 	Map<String, String> info = ModelSerializer.getModelInfo(getURIFromFullPath(PreTrainedModels.CHEM_ACP_CLF_LIBLINEAR_PATH));
	// 	System.out.println(info);
	// }

//	@Test
//	public void ACP_class_scaled() throws Exception {
//		long seed = 52;
//		ACPClassifier modelimpl=CPSignFactor.createACPClassification(
//				CPSignFactor.createDistanceToHyperplaneNCM(CPSignFactor.createLinearSVC()), new RandomSampling(nrModels, calibSize));
//		modelimpl.getICPImplementation().setPValueCalculator(new SmoothedPValue(seed));
//		ChemCPClassifier signacp = new ChemCPClassifier(modelimpl);
//		signacp.setModelInfo(new ModelInfo("random_name"));
//
//		signacp.fromMolsIterator(new SDFile(new File(trainfileClassification).toURI()).getIterator(),PROPERTY, new NamedLabels(AMES_LABELS));
//		//		signacp.fromChemFile(trainfileClassification, PROPERTY, AMES_LABELS);
//		signacp.getProblem().rescale();
//		Assert.assertTrue(signacp.getProblem().isRescaled());
//
//		signacp.train();
//
//		Map<String,Double> originalPred = signacp.predictMondrian(getTestMol());
//
//		// Do only a single fold/model
//		File modelFile = createTempFile("one", ".cpsign");
//		ModelSerializer.generateTrainedModel(signacp, modelFile, null);
//		//		printCPSignJSON(modelFile);
//		ChemCPClassifier loaded = (ChemCPClassifier) ModelLoader.loadModel(modelFile.toURI(), null);
//
//		assertEquals(signacp, loaded);
//
//		Map<String,Double> loadedPred = loaded.predictMondrian(getTestMol());
//		Assert.assertEquals(originalPred,loadedPred);
//
//		ACPClassifier load = (ACPClassifier) loaded.getPredictor();
//		Assert.assertEquals(seed, (long) load.getICPImplementation().getPValueCalculator().getRNGSeed());
//		Assert.assertTrue(load.getICPImplementation().getPValueCalculator() instanceof SmoothedPValue);
//	}

	@SuppressWarnings({"unused" })
	private static void printCPSignJSON(File modelFile) throws Exception {
		try (final JarFile jarFile = new JarFile(modelFile)) {
			JarEntry je = jarFile.getJarEntry(ModelIO.ModelJarProperties.JSON_PROPERTY_FILE);
			if (je == null) {
				return;
			}

			// Read config file
			String config = null;
			try (InputStream stream = jarFile.getInputStream(je);){
				config = IOUtils.toString(stream, IOSettings.CHARSET);
			}

			// Parse as JSON
			if (config == null || config.isEmpty())
				return;

			try {
				JsonObject json = (JsonObject) Jsoner.deserialize(config);
				//				Map<Object,Object> json =  (Map<Object, Object>) new JSONParser().parse(config);
				SYS_OUT.println(json);
			} catch (JsonException e) {
				return;
			}
		}
	}

	@Test
	public void ACP_class() throws Exception {
		// LoggerUtils.setDebugMode();

		double newC = 0.5;
		File singleModelFile = TestUtils.createTempFile("one", ".cpsign");
		
		SVC impl = (linearKernelSVM ? new LinearSVC() : new C_SVC());
		if (impl instanceof LinearSVC)
			((LinearSVC)impl).setC(newC);
		else if (impl instanceof C_SVC)
			((C_SVC) impl).setC(newC);
		
		SamplingStrategy strat = (CCP ? new FoldedSampling(nrFolds) : new RandomSampling(nrModels, calibSize));
		ACPClassifier modelimpl = new ACPClassifier(new NegativeDistanceToHyperplaneNCM(impl),strat);
		
		modelimpl.setModelInfo(new ModelInfo("random_name",new Version(2,0,5,"+some-suffix"),"cat1"));
		
		Dataset data = TestDataLoader.getInstance().getDataset(true, true);

		// Should not be able to save when no trained models
		try{
			ConfAISerializer.saveModel(modelimpl, singleModelFile, null);
			Assert.fail();
		} catch(Exception e){
		}

		// ----------------------------------
		// Do only a single fold/model - ALSO CHECK MOUNTING OF DATA
		// ----------------------------------

		String text1 = "This is a metpred enabled model";
		String text2 = "This is encrypted data";
		String text3 = "Some more data";
		List<MountData> mData = new ArrayList<>();
		mData.add(new MountData("metpred.txt", text1));
		mData.add(new MountData("encrypted.txt", text2));
		mData.get(1).encryptData(spec);
		mData.add(new MountData("new_dir/file.txt", text3));
		mData.add(new MountData("spam.svmlight", TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_20.toURI()));
		mData.get(3).encryptData(spec);


		

		int trainIndex = 2;
		modelimpl.train(data, trainIndex);
		
		ConfAISerializer.saveModel(modelimpl, singleModelFile, null, mData.toArray(new MountData[]{}));
		ACPClassifier loadedSingle = (ACPClassifier) ConfAISerializer.loadPredictor(singleModelFile.toURI(), null);
		// ACPClassifier loaded = (ACPClassifier) loadedSingle;
		Assert.assertTrue(loadedSingle.isPartiallyTrained());
		Assert.assertFalse(loadedSingle.isTrained());
		Assert.assertEquals(1, loadedSingle.getNumTrainedPredictors());
		Map<Integer, ICPClassifier> allModels = loadedSingle.getPredictors();
		Assert.assertEquals(1, allModels.size());
		Assert.assertTrue(allModels.containsKey(trainIndex));

		Assert.assertEquals(mData.size(), ModelIO.listMountLocations(singleModelFile.toURI()).size());
		Assert.assertEquals(text1, ModelIO.getMountedDataAsString(singleModelFile.toURI(), "metpred.txt", spec));
		Assert.assertEquals(text2, ModelIO.getMountedDataAsString(singleModelFile.toURI(), "encrypted.txt", spec));
		Assert.assertEquals(text3, ModelIO.getMountedDataAsString(singleModelFile.toURI(), "new_dir/file.txt", spec));
		File spamFileLoaded = ModelIO.getMountedDataAsTmpFile(singleModelFile.toURI(), "spam.svmlight", spec);
		
		try(InputStream input = TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_20.openStream();
			InputStream unpackedSepFile = new FileInputStream(spamFileLoaded);){
			String original = IOUtils.toString(input, StandardCharsets.UTF_8);
			String unpacked = IOUtils.toString(unpackedSepFile, StandardCharsets.UTF_8);
			Assert.assertEquals(original, unpacked);
		}
			
		// ----------------------------------
		// Train full predictor
		// ----------------------------------
		DataRecord testRecord = data.getDataset().remove(0);

		modelimpl.train(data);
		
		System.out.println("training done");
		Map<Integer,Double> originalPred = modelimpl.predict(testRecord.getFeatures());

		File modelFile=null;
		if(storeAsTmp)
			modelFile = TestUtils.createTempFile("acp.class.svm.model", "");
		else {
			String path = "/tmp/models/acp_class_libsvm.osgi";
			modelFile = new File(path);
			modelFile.getParentFile().mkdirs();
			try{
				// delete it if it already exists
				FileUtils.forceDelete(modelFile);
			} catch(Exception e) {}
			modelFile = new File(path);
		}

		ConfAISerializer.saveModel(modelimpl, modelFile, (encrypt ? spec :null));
		System.out.println("model SAVED");

		System.out.println(modelimpl.getProperties());

		// load the model
		LoggerUtils.setDebugMode();
		ACPClassifier loaded = (ACPClassifier) ConfAISerializer.loadPredictor(modelFile.toURI(), spec);
		assertEquals(modelimpl, loaded);
//		SYS_ERR.println(loadedSignAcp.getProperties());

		Map<Integer,Double> loadedPred = loaded.predict(testRecord.getFeatures());

		Assert.assertEquals(originalPred,loadedPred);

		System.err.println(loaded.getModelInfo());
		ModelInfo loadedInfo = loaded.getModelInfo();
		Assert.assertEquals(new Version(2,0,5,"+some-suffix"), loadedInfo.getVersion());
		Assert.assertEquals("cat1", loadedInfo.getCategory());
		Assert.assertEquals("random_name", loadedInfo.getName());
		Assert.assertEquals(new ModelInfo("random_name",new Version(2,0,5,"+some-suffix"),"cat1"), loadedInfo);


	}



	@Test
	public void ACP_reg() throws Exception {
		double newC = 100;
		SVR alg = (linearKernelSVM? new LinearSVR() :  new EpsilonSVR());
		SamplingStrategy strat = (CCP? new FoldedSampling(ccpFolds) : new RandomSampling(nrModels, calibSize));
		ACPRegressor modelimpl = new ACPRegressor(new NormalizedNCM(alg), strat);
		modelimpl.getICPImplementation().setPValueCalculator(new SplineInterpolatedPValue());

		if(alg instanceof LinearSVR)
			((LinearSVR)alg).setC(newC);
		else if (alg instanceof EpsilonSVR)
			((EpsilonSVR)alg).setC(newC);

		Dataset data = TestDataLoader.getInstance().getDataset(false, true);

		// Should not be able to save as JAR if no trained models
		try{
			ConfAISerializer.saveModel(modelimpl, new File("/tmp/model.cpsign"), null);
			Assert.fail();
		} catch(Exception e){
			//			e.printStackTrace();
		}
		
		data.apply(new DropColumnSelector(1, 4, 5), new VarianceBasedSelector(new SelectionCriterion(Criterion.REMOVE_ZEROS)));

		// ----------------------------------
		// Do only a single fold/model
		// ----------------------------------

		int trainIndex = 2;
		modelimpl.train(data, trainIndex);
		File singleModelFile = TestUtils.createTempFile("one", ".cpsign");
		ConfAISerializer.saveModel(modelimpl, singleModelFile, null);
		ACPRegressor loadedSingle = (ACPRegressor) ConfAISerializer.loadPredictor(singleModelFile.toURI(), null);
		Assert.assertTrue(loadedSingle.isPartiallyTrained());
		Assert.assertFalse(loadedSingle.isTrained());
		Assert.assertEquals(1, loadedSingle.getNumTrainedPredictors());
		Map<Integer, ICPRegressor> allModels = loadedSingle.getPredictors();
		Assert.assertEquals(1, allModels.size());
		Assert.assertTrue(allModels.containsKey(trainIndex));
		// Assert.assertEquals(2, loadedSingle.getDataset().getTransformers().size());
		// Assert.assertTrue(loadedSingle.getDataset().getTransformers().get(0) instanceof DropColumnSelecter);
		// Assert.assertTrue(loadedSingle.getDataset().getTransformers().get(1) instanceof VarianceBasedSelecter);

		// ----------------------------------
		// Train full predictor
		// ----------------------------------
		modelimpl.train(data);
		System.out.println("training done");

		File modelFile=null;
		if(storeAsTmp)
			modelFile = TestUtils.createTempFile("acp.reg.svm.model", "");
		else{
			String path = "/tmp/models/acp_reg.cpsign";
			modelFile = new File(path);
		}

		ConfAISerializer.saveModel(modelimpl, modelFile, encrypt?spec:null);
		// if(encrypt)
		// 	signacp.saveEncrypted(modelFile, spec);
		// else
		// 	signacp.save(modelFile);
		System.out.println("model SAVED");

		// load the model
		ACPRegressor loaded = (ACPRegressor) ConfAISerializer.loadPredictor(modelFile.toURI(), spec);
		System.out.println("\n\n MODEL IS TRAINED="+loaded.isTrained()+"\n\n");
		assertEquals(modelimpl, loaded);

		ICPRegressor icpLoaded = loaded.getICPImplementation();
		Assert.assertTrue(icpLoaded.getPValueCalculator() instanceof SplineInterpolatedPValue);
		
	}
	
	@Test
	public void ACP_reg_predef_train_calib() throws Exception {
		SVR alg = (linearKernelSVM ? new LinearSVR() : new EpsilonSVR());
		SamplingStrategy strat = new SingleSample();
		ACPRegressor modelimpl = new ACPRegressor(new NormalizedNCM(alg), strat);
		modelimpl.getICPImplementation().setPValueCalculator(new SplineInterpolatedPValue());

		Dataset allData = TestDataLoader.getInstance().getDataset(false, false);
		SubSet[] splits = allData.getDataset().splitRandom(.3);
		FeatureVector testVector = splits[0].remove(1).getFeatures();

		Dataset data = new Dataset();
		// Prop train
		data.withModelingExclusiveDataset(splits[1]);
		
		// Calib
		data.withCalibrationExclusiveDataset(splits[0]);
		
		Assert.assertTrue(data.getDataset().isEmpty());
		
		modelimpl.train(data);
		CPRegressionPrediction predInital = modelimpl.predict(testVector, 0.8);
		
		File modelFile = TestUtils.createTempFile("acp.reg.svm.model", "");
		
		ConfAISerializer.saveModel(modelimpl, modelFile, null);
		
		ACPRegressor loaded = (ACPRegressor) ConfAISerializer.loadPredictor(modelFile.toURI(), null);

		
		assertEquals(modelimpl, loaded);


		CPRegressionPrediction predLoaded = loaded.predict(testVector, .8);
		Assert.assertEquals(predInital, predLoaded);
		
	}


	@Test
	public void CVAP_class() throws Exception {
		SVC impl = (linearKernelSVM ? new LinearSVC() : new C_SVC());
		double newC = 1;
		if (impl instanceof LinearSVC)
			((LinearSVC)impl).setC(newC);
		else if (impl instanceof C_SVC)
			((C_SVC) impl).setC(newC);
		
		SamplingStrategy strat = (CCP ? new FoldedSampling(nrFolds):new RandomSampling(nrModels, calibSize));
		
		Dataset data = TestDataLoader.getInstance().getDataset(true, true);
		AVAPClassifier cvap = new AVAPClassifier(impl, strat);

		// Should not be able to save model if no trained models
		try{
			ConfAISerializer.saveModel(cvap, new File("/tmp/plugin.jar"), null);
			Assert.fail();
		} catch(Exception e){}

		// Do only a single fold/model
		int trainIndex = 2;
		cvap.train(data, trainIndex);
		File singleModelFile = TestUtils.createTempFile("one", ".cpsign");
		LoggerUtils.setDebugMode();
		ConfAISerializer.saveModel(cvap, singleModelFile, null);
		AVAPClassifier loadedSingle = (AVAPClassifier) ConfAISerializer.loadPredictor(singleModelFile.toURI(), null);
		Assert.assertTrue(loadedSingle.isPartiallyTrained());
		Assert.assertFalse(loadedSingle.isTrained());
		Assert.assertEquals(1, loadedSingle.getNumTrainedPredictors());
		Map<Integer, IVAPClassifier> allModels = loadedSingle.getModels();
		Assert.assertEquals(1, allModels.size());
		Assert.assertTrue(allModels.containsKey(trainIndex));

		// train full 
		FeatureVector testVec = data.getDataset().remove(0).getFeatures();
		cvap.train(data);
		Map<Integer,Double> probs = cvap.predict(testVec).getProbabilities();

		File modelFile = TestUtils.createTempFile("acp.class.svm.model", "");

		ConfAISerializer.saveModel(cvap, modelFile, encrypt?spec:null);
		System.out.println("model SAVED");

		System.out.println(cvap.getProperties());

		AVAPClassifier loaded = (AVAPClassifier) ConfAISerializer.loadPredictor(modelFile.toURI(), spec);

		assertEquals(cvap, loaded);

		Map<Integer,Double> probsLoaded = loaded.predict(testVec).getProbabilities();
		Assert.assertEquals(probs, probsLoaded);
	}



	@Test
	public void TCP_class() throws Exception {
		SVC mlImpl = (linearKernelSVM? new LinearSVC() : new C_SVC());
		TCPClassifier tcp = new TCPClassifier(new NegativeDistanceToHyperplaneNCM(mlImpl));
		
		// LoggerUtils.setDebugMode();	

		// Set new SVM llParams, check that it works to load!
		Classifier clf = tcp.getNCM().getModel();
		if (clf instanceof LinearSVC) {
			((LinearSVC) clf).setC(10000);
			((LinearSVC) clf).setEpsilon(2d);
		} else if (clf instanceof C_SVC) {
			((C_SVC) clf).setC(10000);
			((C_SVC) clf).setEpsilon(2d);
			((C_SVC) clf).setGamma(2d);
		}

		Dataset data = TestDataLoader.getInstance().getDataset(true, true);
		tcp.train(data);
		
//		MLParameters params = tcp.getNCM().getModel().getParameters();
//		if(params instanceof LibLinearParameters){
//			((LibLinearParameters)params).setC(10000);
//			((LibLinearParameters)params).setEpsilon(2d);
//		} else if(params instanceof LibSvmParameters){
//			((LibSvmParameters)params).setC(10000);
//			((LibSvmParameters)params).setEpsilon(2d);
//			((LibSvmParameters)params).setGamma(2d);
//		}
		//		File tmpTCP = new File("/Users/staffan/Desktop/TCP_model.osgi");
		File tmpTCP = TestUtils.createTempFile("tcp.model", "");
		ConfAISerializer.saveModel(tcp, tmpTCP, null);
		// signtcp.save(tmpTCP);

		//		LoggerUtils.setDebugMode();
		TCPClassifier loaded = (TCPClassifier) ConfAISerializer.loadPredictor(tmpTCP.toURI(), null);
		
		assertEquals(tcp, loaded);
		Assert.assertTrue(systemErrRule.getLog().isEmpty());

	}
	
	public static void compareSmallestFeatureIndex(Dataset p1, Dataset p2) {
		int minIndex1=-2;
		for (DataRecord r1 : p1.getDataset())
			minIndex1 = Math.min(r1.getMinFeatureIndex(),minIndex1);
		
		int minIndex2=-2;
		for (DataRecord r1 : p2.getDataset())
			minIndex2 = Math.min(r1.getMinFeatureIndex(),minIndex2);
		
		Assert.assertEquals(minIndex1, minIndex2);
	}

	

	@Test
	public void testGenericSparseSave() throws Exception {
		Predictor pred = new ACPRegressor(new AbsDiffNCM(new LinearSVR()), 
				new RandomSampling(nrModels, calibSize));
		File tmpFile = TestUtils.createTempFile("sdfa", "");

		// No info set
		try {
			ConfAISerializer.saveModel(pred, tmpFile, null);
			Assert.fail();
		} catch(IllegalArgumentException e) {}
		try {
			ConfAISerializer.saveModel(pred, tmpFile, spec);
			// pred.saveEncrypted(tmpFile, encrFactory.getEncryptionSpec());
			Assert.fail();
		} catch(IllegalArgumentException e) {}

		// Not trained
		pred.setModelInfo(new ModelInfo("some model"));
		try {
			ConfAISerializer.saveModel(pred, tmpFile, null);
			// pred.save(tmpFile);
			Assert.fail();
		} catch(IllegalArgumentException e) {}
		try {
			ConfAISerializer.saveModel(pred, tmpFile, spec);
			// pred.saveEncrypted(tmpFile, encrFactory.getEncryptionSpec());
			Assert.fail();
		} catch(IllegalArgumentException e) {}



	}


	@Test
	public void SparsePredictorACPReg() throws Exception{
		//		LoggerUtils.setDebugMode();
		Predictor pred = new ACPRegressor(new AbsDiffNCM(new LinearSVR()),
				new RandomSampling(nrModels, calibSize)); 

		Dataset prob = TestDataLoader.getInstance().getDataset(false, true);

		pred.train(prob);

		File model = TestUtils.createTempFile("sparse-model", ".cpsign"); 

		pred.setModelInfo(new ModelInfo("random"));
		ConfAISerializer.saveModel(pred, model, null);

		Predictor loaded = ConfAISerializer.loadPredictor(model.toURI(), null);
		assertEquals(pred, loaded);
		// SYS_ERR.println(ModelSerializer.getCPSignProperties(model.toURI()));
		Version buildVersion = ModelIO.getModelBuildVersion(model.toURI());
		Assert.assertEquals(ConfAI.getVersion(), buildVersion);
		System.out.println(buildVersion);
		Map<String, String> info = ModelIO.getModelInfo(model.toURI());
		System.out.println(info);
	}

	int nrFolds = 5;

	@Test
	public void SparsePredictorACPClass() throws Exception{
		Predictor pred = new ACPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()),
			new RandomSampling(nrModels, calibSize)); 

		Dataset prob = TestDataLoader.getInstance().getDataset(true, true);

		pred.train(prob);

		File model = TestUtils.createTempFile("sparse-model", ".cpsign"); 
		pred.setModelInfo(new ModelInfo("random"));
		ConfAISerializer.saveModel(pred, model, null);
		// pred.save(model);
		//		printCPSignJSON(model);

		Predictor loaded = (Predictor)ConfAISerializer.loadPredictor(model.toURI(), null);
		assertEquals(pred, loaded);

		
	}

	@Test
	public void SparsePredictorTCPClass() throws Exception{
		//		LoggerUtils.setDebugMode();
		Predictor pred = new TCPClassifier(new InverseProbabilityNCM(new PlattScaledC_SVC()));
		Dataset prob = TestDataLoader.getInstance().getDataset(true, true);

		pred.train(prob);

		File model = TestUtils.createTempFile("sparse-model", ".cpsign");

		LoggerUtils.setDebugMode();
		pred.setModelInfo(new ModelInfo("random"));
		ConfAISerializer.saveModel(pred, model, null);
		// pred.save(model);

		Predictor loaded = (Predictor)ConfAISerializer.loadPredictor(model.toURI(), null);
		assertEquals(pred, loaded);
	}




	public static void assertEquals(Predictor sp1, Predictor sp2) throws Exception {
//		Assert.assertEquals(sp1.getProperties(), sp2.getProperties());

		if (sp1 instanceof TCP){
			// Only check that the problems equals each other
			Assert.assertEquals(sp1.isTrained(), sp2.isTrained());
			if (sp1.isTrained()) {
				Dataset p1 = ((TCP)sp1).getDataset();
				Dataset p2 = ((TCP)sp2).getDataset();
				Assert.assertEquals(p1.getNumRecords(), p2.getNumRecords());
				Assert.assertEquals(p1.getNumAttributes(), p2.getNumAttributes());
			}
			//			Assert.assertEquals((Dataset)((TCP)sp1).getProblem(),(Dataset) ((TCP)sp2).getProblem());

			//			Assert.assertEquals(((TCP)sp1).getProblem().getDataset(), ((TCP)sp2).getProblem().getDataset());
			Assert.assertEquals(sp1.getProperties(), sp2.getProperties());
			Assert.assertEquals(sp1.getClass(), sp2.getClass());
			assertEquals((TCP)sp1, (TCP)sp2);
		} else if (sp1 instanceof ACPClassifier){
			assertEquals((ACPClassifier)sp1, (ACPClassifier)sp2);
		} else if (sp1 instanceof ACPRegressor){
			assertEquals((ACPRegressor)sp1, (ACPRegressor)sp2);
		} else if (sp1 instanceof AVAPClassifier) {
			assertEquals((AVAPClassifier) sp1, (AVAPClassifier) sp2);
		}

		if (sp1 instanceof AggregatedPredictor) {
			Assert.assertEquals(((AggregatedPredictor) sp1).getStrategy(), ((AggregatedPredictor) sp2).getStrategy());
		}


	}


	public static void assertEquals(Dataset model1, Dataset model2) throws Exception {
		Assert.assertEquals(model1.getClass(), model2.getClass());
		Assert.assertEquals(model1.getProperties(), model2.getProperties());
		Assert.assertEquals(model1, model2);
	}

	public static void assertEquals(TCP model1, TCP model2) throws IllegalAccessException{

		Assert.assertEquals(model1.getProperties(), model2.getProperties());
		
	}

	public static void assertEquals(ACP sp1_model, ACP sp2_model){

//		Assert.assertEquals(sp1_model.getProperties(), sp2_model.getProperties());

		if(sp1_model instanceof ACPClassifier){
			Map<Integer,ICPClassifier> models1 = ((ACPClassifier)sp1_model).getPredictors();
			Map<Integer,ICPClassifier> models2 = ((ACPClassifier)sp2_model).getPredictors();

			Assert.assertEquals(models1.size(), models2.size());

			for (Integer i: models1.keySet()){
				Assert.assertEquals(
						((NCMMondrianClassification)models1.get(i).getNCM()).getModel().getClass(), 
						((NCMMondrianClassification)models2.get(i).getNCM()).getModel().getClass());
				ModelComparisonUtils.assertEqualMLModels(
						((NCMMondrianClassification)models1.get(i).getNCM()).getModel(), 
						((NCMMondrianClassification)models2.get(i).getNCM()).getModel());
			}

			for(Integer modelID : models1.keySet()){
				Assert.assertEquals(((ACPClassifier) sp1_model).getPredictors().get(modelID).getNCS(), ((ACPClassifier) sp2_model).getPredictors().get(modelID).getNCS());
				//				Assert.assertEquals(((ACPClassificationImpl) sp1_model).getModels().get(modelID).getNonconf1(), ((ACPClassificationImpl) sp2_model).getModels().get(modelID).getNonconf1());
			}

		} else if (sp1_model instanceof ACPRegressor){

			Map<Integer,ICPRegressor> models1 = ((ACPRegressor)sp1_model).getPredictors();
			Map<Integer,ICPRegressor> models2 = ((ACPRegressor)sp2_model).getPredictors();

			Assert.assertEquals(models1.size(), models2.size());

			for(Integer modelID : models1.keySet()){
				ICPRegressor model1reg=models1.get(modelID), model2reg= models2.get(modelID);

				// Y-models
				Assert.assertEquals(
						model1reg.getNCM().getModel().getClass(), 
						model2reg.getNCM().getModel().getClass());

				ModelComparisonUtils.assertEqualMLModels(
						model1reg.getNCM().getModel(), 
						model2reg.getNCM().getModel());

				// E-models
				Assert.assertEquals(model1reg.getNCM().requiresErrorModel(), model2reg
						.getNCM().requiresErrorModel());
				if (model1reg.getNCM().requiresErrorModel()) {
					Assert.assertEquals(
							model1reg.getNCM().getErrorModel().getClass(), 
							model2reg.getNCM().getErrorModel().getClass());
					ModelComparisonUtils.assertEqualMLModels(
							model1reg.getNCM().getErrorModel(), 
							model2reg.getNCM().getErrorModel());
				}

				// Alphas
				Assert.assertEquals(model1reg.getNCS().size(), model2reg.getNCS().size());
			}
		}
	}

	public static void assertEquals(AVAPClassifier c1, AVAPClassifier c2){
		Assert.assertEquals(c1.isTrained(), c2.isTrained());
		Assert.assertEquals(c1.getNumTrainedPredictors(), c2.getNumTrainedPredictors());

		Map<Integer, IVAPClassifier> models1 = c1.getModels();
		Map<Integer, IVAPClassifier> models2 = c2.getModels();
		Assert.assertEquals(models1.keySet(), models2.keySet());

		for (Integer modelID : models1.keySet()){
			IVAPClassifier ivap1 = models1.get(modelID);
			IVAPClassifier ivap2 = models2.get(modelID);
			Assert.assertEquals(ivap1.getScoringAlgorithm().getClass(), ivap2.getScoringAlgorithm().getClass());
			ModelComparisonUtils.assertEqualMLModels(ivap1.getScoringAlgorithm(), ivap2.getScoringAlgorithm());
		}

	}

}

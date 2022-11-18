/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cheminf.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.chem.io.in.CSVFile;
import com.arosbio.chem.io.in.SDFile;
import com.arosbio.cheminf.ChemCPClassifier;
import com.arosbio.cheminf.ChemCPRegressor;
import com.arosbio.cheminf.ChemClassifier;
import com.arosbio.cheminf.ChemPredictorImpl;
import com.arosbio.cheminf.ChemVAPClassifier;
import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.data.ChemDataset.DescriptorCalcInfo;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor.SignatureType;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.Version;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.RecordType;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.NamedLabels;
import com.arosbio.data.transform.Transformer;
import com.arosbio.data.transform.feature_selection.DropColumnSelector;
import com.arosbio.data.transform.feature_selection.SelectionCriterion;
import com.arosbio.data.transform.feature_selection.SelectionCriterion.Criterion;
import com.arosbio.data.transform.feature_selection.VarianceBasedSelector;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.ml.TrainingsetValidator;
import com.arosbio.ml.algorithms.Classifier;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.algorithms.svm.SVC;
import com.arosbio.ml.algorithms.svm.SVR;
import com.arosbio.ml.cp.ConformalClassifier;
import com.arosbio.ml.cp.ConformalRegressor;
import com.arosbio.ml.cp.acp.ACP;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.icp.ICPClassifier;
import com.arosbio.ml.cp.icp.ICPRegressor;
import com.arosbio.ml.cp.nonconf.calc.SplineInterpolatedPValue;
import com.arosbio.ml.cp.nonconf.classification.NCMMondrianClassification;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.cp.nonconf.regression.NormalizedNCM;
import com.arosbio.ml.cp.tcp.TCP;
import com.arosbio.ml.cp.tcp.TCPClassifier;
import com.arosbio.ml.interfaces.AggregatedPredictor;
import com.arosbio.ml.interfaces.ClassificationPredictor;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.io.ModelIO;
import com.arosbio.ml.io.ModelIO.ModelType;
import com.arosbio.ml.io.ModelInfo;
import com.arosbio.ml.io.MountData;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.arosbio.ml.sampling.FoldedSampling;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.sampling.SamplingStrategy;
import com.arosbio.ml.sampling.SingleSample;
import com.arosbio.ml.vap.avap.AVAPClassifier;
import com.arosbio.ml.vap.ivap.IVAPClassifier;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.GzipEncryption;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.ModelComparisonUtils;
import com.arosbio.testutils.TestChemDataLoader;
import com.arosbio.testutils.TestChemDataLoader.PreTrainedModels;
import com.arosbio.testutils.UnitTestBase;


@Category(UnitTest.class)
public class TestSaveLoadModel extends UnitTestBase {

	final CmpdData clfData = TestResources.Cls.getAMES_126();
	final CmpdData regData = TestResources.Reg.getSolubility_10();

	final boolean storeAsTmp=true, linearKernel=true, CCP = false, encrypt=true, useStereoSignatures=false;
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

	@Test
	public void testMountData() throws Exception {
		ChemDataset sp = new ChemDataset();
		sp.initializeDescriptors();
		sp.add(new SDFile(clfData.uri()).getIterator(),clfData.property(), new NamedLabels(clfData.labelsStr()));

		File outputFile = TestUtils.createTempFile("someFile", "txt");

		String text1 = "This is a metpred enabled model";
		String text2 = "This is encrypted data";
		String text3 = "Some more data";
		List<MountData> data = new ArrayList<>();
		data.add(new MountData("metpred.txt", text1));
		data.add(new MountData("encrypted.txt", text2));
		data.get(1).encryptData(spec);
		data.add(new MountData("new_dir/file.txt", text3));
		data.add(new MountData("spam.svmlight", TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_20.toURI()));
		data.get(3).encryptData(spec);

		ModelSerializer.saveDataset(sp, new ModelInfo("test"), outputFile, null, data.toArray(new MountData[]{}));

		Assert.assertEquals(data.size(), ModelIO.listMountLocations(outputFile.toURI()).size());
		Assert.assertEquals(text1, ModelIO.getMountedDataAsString(outputFile.toURI(), "metpred.txt", spec));
		Assert.assertEquals(text2, ModelIO.getMountedDataAsString(outputFile.toURI(), "encrypted.txt", spec));

		File spamFileLoaded = ModelIO.getMountedDataAsTmpFile(outputFile.toURI(), "spam.svmlight", spec);
		
		try(InputStream input = TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_20.openStream();
			InputStream unpackedSepFile = new FileInputStream(spamFileLoaded);){
			String original = IOUtils.toString(input, StandardCharsets.UTF_8);
			String unpacked = IOUtils.toString(unpackedSepFile, StandardCharsets.UTF_8);
			Assert.assertEquals(original, unpacked);
		}
	}


	@Test
	public void ACP_class() throws Exception {
		LoggerUtils.setDebugMode();
		ModelInfo inf = new ModelInfo("random_name",new Version(2,0,5,"+some-suffix"),"cat1");

		double newC = 0.5;
		int minHAC = 7;
		File singleModelFile = TestUtils.createTempFile("one", ".cpsign");
		
		SVC impl = (linearKernel ? new LinearSVC(): new C_SVC());
		if (impl instanceof LinearSVC)
			((LinearSVC)impl).setC(newC);
		else if (impl instanceof C_SVC)
			((C_SVC) impl).setC(newC);
		
		SamplingStrategy strat = (CCP ? new FoldedSampling(nrFolds):new RandomSampling(nrModels, calibSize));
		ACPClassifier modelimpl = new ACPClassifier(new NegativeDistanceToHyperplaneNCM(impl), strat);
		ChemCPClassifier signacp = new ChemCPClassifier(modelimpl);
		signacp.getDataset().setMinHAC(minHAC);
		signacp.withModelInfo(inf);
		if (useStereoSignatures)
			((SignaturesDescriptor)signacp.getDataset().getDescriptors().get(0)).setSignaturesType(SignatureType.STEREO);

		signacp.addRecords(new SDFile(clfData.uri()).getIterator(),clfData.property(), new NamedLabels(clfData.labelsStr()));

		// Should not be able to save as jar if no trained models
		try{
			ModelSerializer.saveModel(signacp, singleModelFile, null);
			Assert.fail();
		} catch(Exception e){
		}

		// Do only a single fold/model
		int trainIndex = 2;
		((ACPClassifier)signacp.getPredictor()).train(signacp.getDataset(), trainIndex);

		
		ModelSerializer.saveModel(signacp, singleModelFile, null);
		ChemCPClassifier loadedSingle = (ChemCPClassifier) ModelSerializer.loadChemPredictor(singleModelFile.toURI(), null);
		ACPClassifier loaded = (ACPClassifier) loadedSingle.getPredictor();
		Assert.assertTrue(loaded.isPartiallyTrained());
		Assert.assertFalse(loaded.isTrained());
		Assert.assertEquals(1, loaded.getNumTrainedPredictors());
		Map<Integer, ICPClassifier> allModels = loaded.getPredictors();
		Assert.assertEquals(1, allModels.size());
		Assert.assertTrue(allModels.containsKey(trainIndex));
		Assert.assertEquals(minHAC, loadedSingle.getDataset().getMinHAC());

		signacp.train();
		// LoggerUtils.setDebugMode(SYS_ERR);
		signacp.computePercentiles(new SDFile(clfData.uri()).getIterator(),10);
		System.out.println("training done");
		Map<String,Double> originalPred = signacp.predict(getTestMol());

		File modelFile=null;
		if(storeAsTmp)
			modelFile = TestUtils.createTempFile("acp.class.svm.model", "");
		else{
			String path = "/tmp/models/acp_class_libsvm.osgi";
			modelFile = new File(path);
			modelFile.getParentFile().mkdirs();
			try{
				// delete it if it already exists
				FileUtils.forceDelete(modelFile);
			} catch(Exception e) {}
			modelFile = new File(path);
		}

		ModelSerializer.saveModel(signacp, modelFile, (encrypt ? spec :null));

		System.out.println("model SAVED");


		// load the model
		ChemCPClassifier loadedSignAcp = (ChemCPClassifier) ModelSerializer.loadChemPredictor(modelFile.toURI(), spec);
		assertEquals(signacp, loadedSignAcp);
		Assert.assertEquals(minHAC, loadedSignAcp.getDataset().getMinHAC());

		Assert.assertEquals(clfData.property(), loadedSignAcp.getProperty());
		Map<String,Double> loadedPred = loadedSignAcp.predict(getTestMol());

		Assert.assertEquals(originalPred,loadedPred);

		ModelInfo loadedInfo = loadedSignAcp.getModelInfo();
		Assert.assertEquals(inf.getVersion(), loadedInfo.getVersion());
		Assert.assertEquals("cat1", loadedInfo.getCategory());
		Assert.assertEquals("random_name", loadedInfo.getName());
		Assert.assertEquals(inf, loadedInfo);

	}

	@Test
	public void ACP_class_precomp() throws Exception {
		int start=0, stop=4;
		ChemCPClassifier signacp = new ChemCPClassifier(null,start,stop);

		signacp.addRecords(new SDFile(clfData.uri()).getIterator(),clfData.property(), new NamedLabels(clfData.labelsStr()));

		File tmpPrecom = TestUtils.createTempFile("precomp", ".osgi");
		ModelSerializer.saveDataset(signacp, tmpPrecom, spec);



		// Make sure that everything is loaded OK
		ChemDataset data = ModelSerializer.loadDataset(tmpPrecom.toURI(), spec);
		ChemCPClassifier loaded = new ChemCPClassifier(null);
		loaded.setDataset(data);

		Assert.assertTrue(signacp.getDataset().equals(loaded.getDataset()));
		SignaturesDescriptor d = (SignaturesDescriptor)loaded.getDataset().getDescriptors().get(0);
		Assert.assertEquals(start, d.getStartHeight());
		Assert.assertEquals(stop, d.getEndHeight());

	}

	@Test
	public void ACP_reg() throws Exception {
		double newC = 100;
		//		LoggerUtils.setDebugMode();
		SVR alg = (linearKernel ? new LinearSVR() : new EpsilonSVR());
		SamplingStrategy strat = (CCP ? new FoldedSampling(ccpFolds) : new RandomSampling(nrModels, calibSize));
		ACPRegressor modelimpl = new ACPRegressor(new NormalizedNCM(alg, null), strat);
		modelimpl.getICPImplementation().setPValueCalculator(new SplineInterpolatedPValue());

		ChemCPRegressor signacp = new ChemCPRegressor(modelimpl);
		
		if(alg instanceof LinearSVR)
			((LinearSVR)alg).setC(newC);
		else if (alg instanceof EpsilonSVR)
			((EpsilonSVR)alg).setC(newC);

		signacp.addRecords(new CSVFile(regData.uri()).getIterator(),regData.property());

		// Should not be able to save as JAR if no trained models
		try{
			ModelSerializer.saveModel(signacp, new File("/tmp/plugin.jar"), null);
			Assert.fail();
		} catch(Exception e){}
		
		signacp.getDataset().apply(new DropColumnSelector(1, 4, 5), new VarianceBasedSelector(new SelectionCriterion(Criterion.REMOVE_ZEROS)));

		// Do only a single fold/model
		int trainIndex = 2;
		((ACPRegressor)signacp.getPredictor()).train(signacp.getDataset(), trainIndex);
		File singleModelFile = TestUtils.createTempFile("one", ".cpsign");
		ModelSerializer.saveModel(signacp, singleModelFile, null);
		ChemCPRegressor loadedSingle = (ChemCPRegressor) ModelSerializer.loadChemPredictor(singleModelFile.toURI(), null);
		ACPRegressor loaded = (ACPRegressor) loadedSingle.getPredictor();
		Assert.assertTrue(loaded.isPartiallyTrained());
		Assert.assertFalse(loaded.isTrained());
		Assert.assertEquals(1, loaded.getNumTrainedPredictors());
		Map<Integer, ICPRegressor> allModels = loaded.getPredictors();
		Assert.assertEquals(1, allModels.size());
		Assert.assertTrue(allModels.containsKey(trainIndex));
		Assert.assertEquals(2, loadedSingle.getDataset().getTransformers().size());
		Assert.assertTrue(loadedSingle.getDataset().getTransformers().get(0) instanceof DropColumnSelector);
		Assert.assertTrue(loadedSingle.getDataset().getTransformers().get(1) instanceof VarianceBasedSelector);


		signacp.train();
		System.out.println("training done");

		File modelFile=null;
		if(storeAsTmp)
			modelFile = TestUtils.createTempFile("acp.reg.svm.model", "");
		else{
			String path = "/tmp/models/acp_reg.jar";
			modelFile = new File(path);
		}

		ModelSerializer.saveModel(signacp, modelFile, encrypt?spec:null);
		System.out.println("model SAVED");

		// load the model
		ChemCPRegressor loadedSignAcp = (ChemCPRegressor) ModelSerializer.loadChemPredictor(modelFile.toURI(), spec);
		System.out.println("\n\n MODEL IS TRAINED="+loadedSignAcp.getPredictor().isTrained()+"\n\n");
		assertEquals(signacp, loadedSignAcp);

		ICPRegressor icpLoaded = ((ACPRegressor)loadedSignAcp.getPredictor()).getICPImplementation();
		Assert.assertTrue(icpLoaded.getPValueCalculator() instanceof SplineInterpolatedPValue);
		
		Assert.assertEquals(regData.property(), loadedSignAcp.getProperty());

	}
	
	@Test
	public void ACP_reg_predef_train_calib() throws Exception {
		SVR alg = (linearKernel? new LinearSVR() : new EpsilonSVR());
		SamplingStrategy strat = new SingleSample();
		ACPRegressor modelimpl = new ACPRegressor(new NormalizedNCM(alg, null), strat);
		modelimpl.getICPImplementation().setPValueCalculator(new SplineInterpolatedPValue());

		ChemCPRegressor signacp = new ChemCPRegressor(modelimpl);
		
		// Prop train
		signacp.addRecords(new CSVFile(regData.uri()).getIterator(),regData.property(), RecordType.MODELING_EXCLUSIVE);
		
		// Calib
		signacp.addRecords(new CSVFile(regData.uri()).getIterator(),regData.property(), RecordType.CALIBRATION_EXCLUSIVE);
		
		Assert.assertTrue(signacp.getDataset().getDataset().isEmpty());
		
		signacp.train();
		
		File modelFile = TestUtils.createTempFile("acp.reg.svm.model", "");
		
		ModelSerializer.saveModel(signacp, modelFile, null);
		
		ChemCPRegressor loaded = (ChemCPRegressor) ModelSerializer.loadChemPredictor(modelFile.toURI(), null);
		
		assertEquals(signacp, loaded);
		
	}

	@Test
	public void ACP_reg_precomp() throws Exception {

		ChemCPRegressor signacp = new ChemCPRegressor();

		signacp.addRecords(new CSVFile(regData.uri()).getIterator(),regData.property());
		signacp.getDataset().apply(new DropColumnSelector(1, 4, 5), new VarianceBasedSelector(new SelectionCriterion(Criterion.REMOVE_ZEROS)));

		File tmpPrecom = TestUtils.createTempFile("precomp", ".jar");
		ModelSerializer.saveDataset(signacp, tmpPrecom, null);

		ChemCPRegressor loaded = new ChemCPRegressor();
		ChemDataset data = ModelSerializer.loadDataset(tmpPrecom.toURI(), null);
		loaded.setDataset(data);
		Assert.assertEquals(2, loaded.getDataset().getTransformers().size());

		assertEquals(signacp, loaded);
	}

	@Test
	public void CVAP_class() throws Exception {
		SVC impl = (linearKernel? new LinearSVC(): new C_SVC());
		double newC = 1;
		if (impl instanceof LinearSVC)
			((LinearSVC)impl).setC(newC);
		else if (impl instanceof C_SVC)
			((C_SVC) impl).setC(newC);
		
		SamplingStrategy strat = (CCP? new FoldedSampling(nrFolds):new RandomSampling(nrModels, calibSize));
		ChemVAPClassifier cvap = new ChemVAPClassifier(new AVAPClassifier(impl, strat));
		cvap.addRecords(new SDFile(clfData.uri()).getIterator(),clfData.property(), new NamedLabels(clfData.labelsStr()));


		// Should not be able to save as model if no trained models
		try{
			ModelSerializer.saveModel(cvap, new File("/tmp/plugin.jar"), null);
			Assert.fail();
		} catch(Exception e){}

		// Do only a single fold/model
		int trainIndex = 2;
		cvap.getPredictor().train(cvap.getDataset(), trainIndex);
		File singleModelFile = TestUtils.createTempFile("one", ".cpsign");
		LoggerUtils.setDebugMode();
		ModelSerializer.saveModel(cvap, singleModelFile, null);

		ChemVAPClassifier loadedSingle = (ChemVAPClassifier) ModelSerializer.loadChemPredictor(singleModelFile.toURI(), null);
		AVAPClassifier loadedPredictor = (AVAPClassifier) loadedSingle.getPredictor();
		Assert.assertTrue(loadedPredictor.isPartiallyTrained());
		Assert.assertFalse(loadedPredictor.isTrained());
		Assert.assertEquals(1, loadedPredictor.getNumTrainedPredictors());
		Map<Integer, IVAPClassifier> allModels = ((AVAPClassifier)loadedPredictor).getModels();
		Assert.assertEquals(1, allModels.size());
		Assert.assertTrue(allModels.containsKey(trainIndex));

		// train
		cvap.train();

		File modelFile = TestUtils.createTempFile("acp.class.svm.model", "");

		ModelSerializer.saveModel(cvap, modelFile, encrypt?spec:null);

		System.out.println("model SAVED");

		System.out.println(cvap.getProperties());

		ChemVAPClassifier loaded = (ChemVAPClassifier) ModelSerializer.loadChemPredictor(modelFile.toURI(), spec);

		assertEquals(cvap, loaded);
		Assert.assertEquals(clfData.property(), loaded.getProperty());
	}


	@Test
	public void TestMultipleDatasetsClassification() throws Exception {
		SVC mlImpl = (linearKernel ? new LinearSVC() : new C_SVC());
		ACPClassifier modelimpl = new ACPClassifier(
				new NegativeDistanceToHyperplaneNCM(mlImpl), 
				new RandomSampling(nrModels, calibSize));

		int start=0, stop=4;
		ChemCPClassifier signacp = new ChemCPClassifier(modelimpl,start,stop);

		signacp.addRecords(new SDFile(clfData.uri()).getIterator(),clfData.property(), new NamedLabels(clfData.labelsStr()));
		// Split everything up into 3 datasets
		Dataset prob = signacp.getDataset();
		Assert.assertTrue(prob.getCalibrationExclusiveDataset().isEmpty());
		Assert.assertTrue(prob.getModelingExclusiveDataset().isEmpty());

		int initialSize = prob.getNumRecords();
		double splitFac = 0.5;
		int splitStatic = 3;
		SubSet[] ds_split1 = prob.getDataset().splitStatic(splitStatic);
		Assert.assertEquals(initialSize, ds_split1[0].size() + ds_split1[1].size());

		SubSet[] ds_split2 = ds_split1[1].splitRandom(splitFac); // 6* 50% = 3
		prob.withCalibrationExclusiveDataset(ds_split1[0]); // 3 recs
		Assert.assertEquals(prob.getCalibrationExclusiveDataset().size(), splitStatic);
		prob.withDataset(ds_split2[0]);
		Assert.assertEquals((int) Math.round(splitFac*(initialSize-splitStatic)), prob.getDataset().size());
		prob.withModelingExclusiveDataset(ds_split2[1]);
		Assert.assertEquals(prob.getModelingExclusiveDataset().size(), (int) (splitFac*(initialSize-splitStatic)));


		// Save it
		File tmpPrecom = TestUtils.createTempFile("precomp", ".jar");
		ModelSerializer.saveDataset(signacp, tmpPrecom, null);

		// Load it back
		ChemDataset data = ModelSerializer.loadDataset(tmpPrecom.toURI(), null);

		Assert.assertEquals(prob, data);
	}


	@Test
	public void TestMultipleDatasetsRegression() throws Exception {
		SVR mlImpl = (linearKernel? new LinearSVR() : new EpsilonSVR());
		ACPRegressor modelimpl = new ACPRegressor(
				new NormalizedNCM(mlImpl, null), 
				new RandomSampling(nrModels, calibSize));

		ChemCPRegressor signacp = new ChemCPRegressor(modelimpl);

		signacp.addRecords(new CSVFile(regData.uri()).getIterator(), regData.property());

		// Split everything up into 3 datasets
		ChemDataset prob = signacp.getDataset();
		Assert.assertEquals(10, prob.getNumRecords());
		Assert.assertEquals(10, prob.getDataset().size());
		Assert.assertTrue(prob.getCalibrationExclusiveDataset().isEmpty());
		Assert.assertTrue(prob.getModelingExclusiveDataset().isEmpty());

		int initialSize = prob.getNumRecords();
		double splitFac = 0.2;
		SubSet[] ds_split1 = prob.getDataset().splitStatic(splitFac);
		Assert.assertEquals(initialSize, ds_split1[0].size() + ds_split1[1].size());
		Assert.assertTrue(Math.abs(ds_split1[0].size()-initialSize*splitFac) <= 1);

		SubSet[] ds_split2 = ds_split1[1].splitRandom(splitFac);
		prob.withCalibrationExclusiveDataset(ds_split1[0]); // 0.2 of original size = 2
		Assert.assertEquals(prob.getCalibrationExclusiveDataset().size(), 2);
		prob.withDataset(ds_split2[0]); // 0.2 of 8 = 2
		Assert.assertEquals(prob.getDataset().size(), 2);
		prob.withModelingExclusiveDataset(ds_split2[1]); // rest = 6
		Assert.assertEquals(prob.getModelingExclusiveDataset().size(), 6);



		// Save it
		File tmpPrecom = TestUtils.createTempFile("precomp", ".jar");
		ModelSerializer.saveDataset(signacp, tmpPrecom, null);

		// Load it back
		ChemDataset loaded = ModelSerializer.loadDataset(tmpPrecom.toURI(), null);

		System.err.printf("total=%s, norm=%s, calib=%s, model=%s%n",
			loaded.getNumRecords(),
			loaded.getDataset().size(),
			loaded.getCalibrationExclusiveDataset().size(),
			loaded.getModelingExclusiveDataset().size());
		LoggerUtils.setDebugMode();
		Assert.assertEquals(prob, loaded);
		assertEquals(prob, loaded);
	}

	@Test
	public void TCP_class() throws Exception {
		SVC mlImpl = (linearKernel ? new LinearSVC() : new C_SVC());
		TCPClassifier tcp=new TCPClassifier(
			new NegativeDistanceToHyperplaneNCM(
						mlImpl));
		ChemCPClassifier signtcp = new ChemCPClassifier(tcp, 1, 2);
		signtcp.addRecords(new SDFile(clfData.uri()).getIterator(),clfData.property(), new NamedLabels(clfData.labelsStr()));
		File tmpPrecomputed = TestUtils.createTempFile("precomputed", ".tcp");
		LoggerUtils.setDebugMode();
		ModelSerializer.saveDataset(signtcp, tmpPrecomputed, null);

		ChemCPClassifier loaded = new ChemCPClassifier(tcp.clone(), 1, 2);
		ChemDataset data = ModelSerializer.loadDataset(tmpPrecomputed.toURI(), null);
		loaded.setDataset(data);

		compareSmallestFeatureIndex(signtcp.getDataset(), loaded.getDataset());
		assertEquals(signtcp, loaded);		

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
			
		File tmpTCP = TestUtils.createTempFile("tcp.model", "");
		signtcp.train();
		ModelSerializer.saveModel(signtcp, tmpTCP, null);

		ChemCPClassifier loaded_signtcp = (ChemCPClassifier) ModelSerializer.loadChemPredictor(tmpTCP.toURI(), null);
		TCPClassifier loaded_tcp = (TCPClassifier) loaded_signtcp.getPredictor();
		
		Assert.assertEquals(signtcp.getDataset().getDescriptors(), loaded_signtcp.getDataset().getDescriptors());
		Assert.assertEquals(clfData.property(), loaded_signtcp.getProperty());
		
		assertEquals(tcp, loaded_tcp);
		assertEquals(signtcp, loaded_signtcp);
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
	public void Precomp_only_reg() throws Exception {
		int minHac = 10;
		ChemDataset sp = new ChemDataset(0, 4);
		if (INCHI_AVAILABLE_ON_SYSTEM) {
			((SignaturesDescriptor)sp.getDescriptors().get(0)).setSignaturesType(SignatureType.STEREO);
		}
		sp.setMinHAC(minHac);
		sp.initializeDescriptors();

		DescriptorCalcInfo info = sp.add(new CSVFile(regData.uri()).getIterator(),regData.property());
		System.err.println(info);
		ModelInfo mInfo =  new ModelInfo("modelName"); 

		File modelFile=null;
		if(storeAsTmp)
			modelFile = TestUtils.createTempFile("precomp.reg.model", "");
		else{
			String path = "/tmp/models/precomp_reg.osgi";
			modelFile = new File(path);
		}
		ModelSerializer.saveDataset(sp, mInfo, modelFile, null);

		ChemDataset loadedModel = ModelSerializer.loadDataset(modelFile.toURI(), null);
		assertEquals(sp, loadedModel);
		SignaturesDescriptor loadedDesc = (SignaturesDescriptor) loadedModel.getDescriptors().get(0);
		if (INCHI_AVAILABLE_ON_SYSTEM)
			Assert.assertTrue(loadedDesc.getSignatureType() == SignatureType.STEREO);
		else
			Assert.assertTrue(loadedDesc.getSignatureType() == SignatureType.STANDARD);

		Assert.assertEquals(loadedModel.getMinHAC(), minHac);
		
	}

	@Test
	public void Precomp_only_Class() throws Exception {
		ChemCPClassifier sp = new ChemCPClassifier(
				new ACPClassifier(
						new NegativeDistanceToHyperplaneNCM(new LinearSVC()), new RandomSampling(nrModels, calibSize)));
		SignaturesDescriptor d = (SignaturesDescriptor) sp.getDataset().getDescriptors().get(0);
		d.setStartHeight(0);
		d.setEndHeight(4);
		sp.addRecords(new SDFile(clfData.uri()).getIterator(),clfData.property(), new NamedLabels(clfData.labelsStr()) );
		sp.withModelInfo( new ModelInfo("modelName", new Version(0,0,1), "category"));
		

		File modelFile=null;
		if(storeAsTmp){
			modelFile = TestUtils.createTempFile("precomp.class.model", "");
		} else{
			String path = "/tmp/models/precomp_class.jar";
			modelFile = new File(path);
		}
		ModelSerializer.saveDataset(sp, modelFile, null);
		//		printCPSignJSON(modelFile);

		ChemDataset loadedData = ModelSerializer.loadDataset(modelFile.toURI(), null);
		assertEquals(sp.getDataset(), loadedData);
	}



	int nrFolds = 5;

	
	@Test
	public void loadTestModels() throws Exception {
		ChemCPClassifier acp = (ChemCPClassifier) ModelSerializer.loadChemPredictor(PreTrainedModels.ACP_CLF_LIBLINEAR.toURI(), null);
		Assert.assertTrue(acp.getPredictor().isTrained());
		
		ACPClassifier impl = (ACPClassifier) acp.getPredictor();
		
		Assert.assertEquals(DEFAULT_NUM_MODELS, impl.getStrategy().getNumSamples());
	}


	@Test
	public void testLoadPhysChemWithTransformations() throws Exception{
		URI uri = PreTrainedModels.PRECOMP_CLF.toURI();
		ChemDataset data = ModelSerializer.loadDataset(uri, null);
		for (Transformer t : data.getTransformers()){
			SYS_ERR.println(t);
		}

		ChemDataset ds = data.clone();

		assertEquals(data, ds);
	}


	public static void assertEquals(Predictor sp1, Predictor sp2) throws Exception {

		if (sp1 instanceof TCP){
			// Only check that the problems equals each other
			Assert.assertEquals(sp1.isTrained(), sp2.isTrained());
			if (sp1.isTrained()) {
				Dataset p1 = ((TCP)sp1).getDataset();
				Dataset p2 = ((TCP)sp2).getDataset();
				Assert.assertEquals(p1.getNumRecords(), p2.getNumRecords());
				Assert.assertEquals(p1.getNumAttributes(), p2.getNumAttributes());
			}

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


	public static void assertEquals(ChemDataset model1, ChemDataset model2) throws Exception {
		Assert.assertEquals(model1.getClass(), model2.getClass());
		Assert.assertEquals(model1.getProperties(), model2.getProperties());
		Assert.assertEquals(model1, model2);
		Assert.assertTrue(model1.equalDescriptors(model2));

	}

	@SuppressWarnings("null")
	public static void assertEquals(ChemPredictorImpl sp1, ChemPredictorImpl sp2) throws Exception {
		if(sp1 instanceof ClassificationPredictor && ! (sp2 instanceof ClassificationPredictor))
			Assert.fail("One is regression and one is classification");
		if(sp2 instanceof ClassificationPredictor && ! (sp1 instanceof ClassificationPredictor))
			Assert.fail("One is regression and one is classification");
		if(sp1 instanceof ChemCPClassifier && ! (sp2 instanceof ChemCPClassifier))
			Assert.fail("One is regression and one is classification");

		Assert.assertTrue(sp1.getDataset().equalDescriptors(sp2.getDataset()));

		if (sp1.getPredictor() != null && sp2.getPredictor() != null)
			assertEquals((Predictor)sp1.getPredictor(), (Predictor)sp2.getPredictor());

		if (sp1 instanceof ClassificationPredictor){
			Assert.assertEquals(((ChemClassifier) sp1).getLabels(), ((ChemClassifier) sp2).getLabels());
		}

		if (sp1 instanceof ChemCPClassifier){
			System.out.println("Was ChemCPClassifier");
			ConformalClassifier sp1_model = ((ChemCPClassifier) sp1).getPredictor();
			ConformalClassifier sp2_model = ((ChemCPClassifier) sp2).getPredictor();

			if ((sp1_model == null || !sp1_model.isTrained()) && (sp2_model == null || ! sp2_model.isTrained()))
				return; // no models to check

			if (sp1_model instanceof ACPClassifier){
				Assert.assertEquals(((ACPClassifier)sp1_model).getStrategy(), ((ACPClassifier)sp2_model).getStrategy());
				if (sp1_model.isTrained())
					Assert.assertEquals(((ACPClassifier)sp1_model).getICPImplementation().getNCM().getModel().getClass(), ((ACPClassifier)sp2_model).getICPImplementation().getNCM().getModel().getClass());
			} else 
				Assert.assertEquals(sp1_model.getProperties(), sp2_model.getProperties());
			Assert.assertEquals(sp1_model.isTrained(), sp2_model.isTrained());
			
			
			

			Assert.assertEquals(((ChemCPClassifier) sp1).getLowPercentile(), ((ChemCPClassifier) sp2).getLowPercentile());
			Assert.assertEquals(((ChemCPClassifier) sp1).getHighPercentile(), ((ChemCPClassifier) sp2).getHighPercentile());

			// Check actual stuff
			Assert.assertEquals(sp1_model.getClass(), sp2_model.getClass());
			if (sp1_model instanceof ACP)
				assertEquals((ACP)sp1_model, (ACP)sp2_model);
			else
				assertEquals((TCP)sp1_model, (TCP)sp2_model);
		}
		else if (sp1 instanceof ChemCPRegressor){
			System.out.println("Was a ChemCPRegressor");

			ConformalRegressor sp1_model = ((ChemCPRegressor) sp1).getPredictor();
			ConformalRegressor sp2_model = ((ChemCPRegressor) sp2).getPredictor();

			if((sp1_model == null || !sp1_model.isTrained()) && (sp2_model == null || ! sp2_model.isTrained()))
				return; // no models to check
			if(sp1_model instanceof ACPRegressor){
				Assert.assertEquals(((ACPRegressor)sp1_model).getStrategy(), ((ACPRegressor)sp2_model).getStrategy());
			}
			Assert.assertEquals(sp1_model.getClass(), sp2_model.getClass());
			Assert.assertEquals(sp1_model.isTrained(), sp2_model.isTrained());
			if(! sp1_model.isTrained()) // if it is not trained - then we loaded data as well -otherwise only signatures and models
				Assert.assertEquals(sp1.getDataset(), sp2.getDataset());

			if (sp1_model instanceof ACP)
				assertEquals((ACP)sp1_model, (ACP)sp2_model);
		}
		else if (sp1 instanceof ChemVAPClassifier){
			System.out.println("Was a ChemVAPClassifier ");

		}

		// Need to remove the num observations - data is not stored so it will become 0 after save/load
		Map<? extends Object, Object> propNoNum = sp1.getProperties();
		Map<? extends Object, Object> propNoNum2 = sp2.getProperties();
		CollectionUtils.removeAtArbitraryDepth(propNoNum, PropertyNameSettings.NUM_OBSERVATIONS_KEY);
		CollectionUtils.removeAtArbitraryDepth(propNoNum2, PropertyNameSettings.NUM_OBSERVATIONS_KEY);

	}

	public static void assertEquals(TCP model1, TCP model2) throws IllegalAccessException{

		Assert.assertEquals(model1.getProperties(), model2.getProperties());
		
	}

	public static void assertEquals(ACP sp1_model, ACP sp2_model){

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

	@Test
	public void testLoadLegacyModels() throws Exception {
		Assert.assertEquals(ModelType.CHEM_PREDICTOR, ModelSerializer.getType(TestChemDataLoader.PreTrainedModels.ACP_CLF_LIBLINEAR.toURI()));
		Assert.assertEquals(ModelType.CHEM_PREDICTOR, ModelSerializer.getType(TestChemDataLoader.PreTrainedModels.ACP_REG_LIBSVM.toURI()));
		Assert.assertEquals(ModelType.PRECOMPUTED_DATA, ModelSerializer.getType(TestChemDataLoader.PreTrainedModels.PRECOMP_CLF.toURI()));
		Assert.assertEquals(ModelType.PRECOMPUTED_DATA, ModelSerializer.getType(TestChemDataLoader.PreTrainedModels.PRECOMP_REG.toURI()));
	}

}

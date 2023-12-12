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
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.io.iterator.IteratingSDFReader;
import org.openscience.cdk.silent.SilentChemObjectBuilder;

import com.arosbio.chem.io.in.CSVChemFileReader;
import com.arosbio.cheminf.ChemCPClassifier;
import com.arosbio.cheminf.ChemCPRegressor;
import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.NamedLabels;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.icp.ICPClassifier;
import com.arosbio.ml.cp.icp.ICPRegressor;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.cp.nonconf.regression.NormalizedNCM;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.utils.GzipEncryption;
import com.arosbio.tests.utils.TestUtils;
import com.google.common.collect.Lists;


@Category(CLITest.class)
public class TestAggregate extends CLIBaseTest{

	static File 
	precompReg1, precompReg2, precompReg_diff_sigs, precompReg_diff_encr,

	precompClass1, precompClass2, precompClass_diff_sigs, precompClass_diff_labels,precompClass_diff_encr,

	trainedReg1, trainedReg2, trainedReg_diff_sigs,trainedReg_diff_impl, trainedReg_diff_encr,

	trainedClass1, trainedClass2, trainedClass_diff_sigs, trainedClass_diff_impl,trainedClass_diff_labels, trainedClass_diff_encr;

	private static final String MODEL_FILE_SUFFIX = ".jar";

	private final static int NUM_MODELS_TOTAL=5; 
	private final static Set<Integer> SPLITS_FIRST = Set.of(0,1,2);
	private final static Set<Integer> SPLITS_SECOND = Set.of(3,4);
	private final static double CALIBRATION_RATIO = 0.25;

	final static boolean DEFAULT_LibLinear = true, FAIL_FAST=false;

	static Map<Integer,String> labelsDiff = new HashMap<>();
	static {
		labelsDiff.put(0,"NEG");
		labelsDiff.put(1,"POS");
	}
	private static File encryptionKeyFile, faultyEncryptionKeyFile;

	public static void setupFiles() throws Exception {
		//init all files
		// PRECOMP CLASS
		precompClass1 =TestUtils.createTempFile("model", MODEL_FILE_SUFFIX);
		precompClass2=TestUtils.createTempFile("model", MODEL_FILE_SUFFIX);
		precompClass_diff_encr=TestUtils.createTempFile("model", MODEL_FILE_SUFFIX);
		precompClass_diff_sigs=TestUtils.createTempFile("model", MODEL_FILE_SUFFIX);
		precompClass_diff_labels=TestUtils.createTempFile("model", MODEL_FILE_SUFFIX);

		// PRECOMP REG
		precompReg1=TestUtils.createTempFile("model", MODEL_FILE_SUFFIX); 
		precompReg2=TestUtils.createTempFile("model", MODEL_FILE_SUFFIX);
		precompReg_diff_sigs=TestUtils.createTempFile("model", MODEL_FILE_SUFFIX);
		precompReg_diff_encr=TestUtils.createTempFile("model", MODEL_FILE_SUFFIX);

		// TRAINED CLASS
		trainedClass1=TestUtils.createTempFile("model", MODEL_FILE_SUFFIX); 
		trainedClass2=TestUtils.createTempFile("model", MODEL_FILE_SUFFIX);
		trainedClass_diff_sigs=TestUtils.createTempFile("model", MODEL_FILE_SUFFIX);
		trainedClass_diff_impl=TestUtils.createTempFile("model", MODEL_FILE_SUFFIX);
		trainedClass_diff_labels=TestUtils.createTempFile("model", MODEL_FILE_SUFFIX);
		trainedClass_diff_encr=TestUtils.createTempFile("dds", MODEL_FILE_SUFFIX);

		// TRAINED REG
		trainedReg1=TestUtils.createTempFile("model", MODEL_FILE_SUFFIX);
		trainedReg2=TestUtils.createTempFile("model", MODEL_FILE_SUFFIX);
		trainedReg_diff_encr=TestUtils.createTempFile("model", MODEL_FILE_SUFFIX);
		trainedReg_diff_sigs=TestUtils.createTempFile("model", MODEL_FILE_SUFFIX);
		trainedReg_diff_impl=TestUtils.createTempFile("model", MODEL_FILE_SUFFIX);
	}

	@BeforeClass
	public static void setupModels() throws Exception {

		EncryptionSpecification spec = new GzipEncryption(); 
		byte[] key1 = spec.generateRandomKey(16);
		byte[] key2 = spec.generateRandomKey(16);
		spec.init(key1);
		EncryptionSpecification faultySpec = new GzipEncryption();
		faultySpec.init(key2);
		encryptionKeyFile = TestUtils.createTempFile("encrypt-file", ".key");
		faultyEncryptionKeyFile = TestUtils.createTempFile("another-encrypt-file", ".key");
		try (FileOutputStream fos = new FileOutputStream(encryptionKeyFile);
			FileOutputStream fosFaulty = new FileOutputStream(faultyEncryptionKeyFile)){
			fos.write(key1);
			fosFaulty.write(key2);
		}
		

		// init all files
		setupFiles();
		// ===========================
		// CLASSIFICATION
		// ===========================
		CmpdData classData_1 = TestResources.Cls.getAMES_126();
		CSVCmpdData classData_2 = TestResources.Cls.getErroneous();
		//1
		ChemCPClassifier sigacp = new ChemCPClassifier(new ACPClassifier(
				new ICPClassifier(new NegativeDistanceToHyperplaneNCM(DEFAULT_LibLinear? new LinearSVC() : new C_SVC())),
				new RandomSampling(NUM_MODELS_TOTAL, CALIBRATION_RATIO))); 
		sigacp.addRecords(new IteratingSDFReader(classData_1.url().openStream(),SilentChemObjectBuilder.getInstance()), classData_1.property(), new NamedLabels(classData_1.labelsStr()));
		Map<Integer, String> correctMapping = sigacp.getLabels();
		ModelSerializer.saveDataset(sigacp, precompClass1, null);
		ModelSerializer.saveDataset(sigacp, precompClass_diff_encr, faultySpec);
		sigacp.setLabels(labelsDiff);
		ModelSerializer.saveDataset(sigacp, precompClass_diff_labels, spec); 

		sigacp.setLabels(correctMapping);
		for (int split : SPLITS_FIRST){
			((ACPClassifier)sigacp.getPredictor()).train(sigacp.getDataset(), split);
		}
		Assert.assertEquals("Only trained partially",SPLITS_FIRST, ((ACPClassifier)sigacp.getPredictor()).getPredictors().keySet());
		Assert.assertFalse(sigacp.getPredictor().isTrained());
		Assert.assertTrue(((ACPClassifier) sigacp.getPredictor()).isPartiallyTrained());

		ModelSerializer.saveModel(sigacp, trainedClass1, null);
		ModelSerializer.saveModel(sigacp, trainedClass_diff_encr, faultySpec);

		List<String> allSigs = Lists.newArrayList(((SignaturesDescriptor) sigacp.getDataset().getDescriptors().get(0)).getSignatures());

		// 2
		ChemDataset sp = sigacp.getDataset();
		sigacp = new ChemCPClassifier(new ACPClassifier(
				new ICPClassifier(new NegativeDistanceToHyperplaneNCM(DEFAULT_LibLinear? new LinearSVC() : new C_SVC())), 
				new RandomSampling(NUM_MODELS_TOTAL, CALIBRATION_RATIO))); 
		sp.getDataset().setRecords(new ArrayList<DataRecord>());
		sigacp.addRecords(new IteratingSDFReader(classData_1.url().openStream(),SilentChemObjectBuilder.getInstance()),classData_1.property(), new NamedLabels(classData_1.labelsStr()));
		ModelSerializer.saveDataset(sigacp, precompClass2, spec); 

		sigacp.getDataset().setDescriptors(sp.getDescriptors().get(0).clone()); 
		for (int split : SPLITS_SECOND){
			((ACPClassifier)sigacp.getPredictor()).train(sigacp.getDataset(), split);
		}
		Assert.assertEquals("Only trained partially",SPLITS_SECOND, ((ACPClassifier)sigacp.getPredictor()).getPredictors().keySet());
		Assert.assertFalse(sigacp.getPredictor().isTrained());
		Assert.assertTrue(((ACPClassifier) sigacp.getPredictor()).isPartiallyTrained());
		ModelSerializer.saveModel(sigacp, trainedClass2, spec);
		

		// diff impl
		sigacp = new ChemCPClassifier(new ACPClassifier(
				new ICPClassifier(new NegativeDistanceToHyperplaneNCM(!DEFAULT_LibLinear ? new LinearSVC() : new C_SVC())), 
				new RandomSampling(NUM_MODELS_TOTAL, CALIBRATION_RATIO)));
		sigacp.addRecords(new IteratingSDFReader(classData_1.url().openStream(),SilentChemObjectBuilder.getInstance()), classData_1.property(), new NamedLabels(classData_1.labelsStr()));
		sigacp.train(); 
		ModelSerializer.saveModel(sigacp, trainedClass_diff_impl,null);
		
		// diff sigs
		sigacp = new ChemCPClassifier(new ACPClassifier(
				new ICPClassifier(new NegativeDistanceToHyperplaneNCM(DEFAULT_LibLinear? new LinearSVC() : new C_SVC())), 
				new RandomSampling(NUM_MODELS_TOTAL, CALIBRATION_RATIO)));
		sigacp.addRecords(new CSVChemFileReader(CSVFormat.TDF.builder().setHeader().build(), new InputStreamReader(classData_2.url().openStream())), classData_2.property(), new NamedLabels(new ArrayList<>(labelsDiff.values())));
		sigacp.setLabels(correctMapping); // set "correct labels"
		ModelSerializer.saveDataset(sigacp, precompClass_diff_sigs, null);
		sigacp.train(); 
		ModelSerializer.saveModel(sigacp, trainedClass_diff_sigs, spec);

		// ===========================
		// REGRESSION
		// ===========================
		CSVCmpdData regData_1 = TestResources.Reg.getSolubility_10();
		CmpdData regData_2 = TestResources.Reg.getChang();

		ChemCPRegressor acpreg = new ChemCPRegressor(new ACPRegressor(
				new ICPRegressor(new NormalizedNCM((DEFAULT_LibLinear? 
				new LinearSVR(): new EpsilonSVR()))), 
				new RandomSampling(NUM_MODELS_TOTAL, CALIBRATION_RATIO)));
		acpreg.addRecords(new CSVChemFileReader(CSVFormat.TDF.builder().setHeader().build(), new InputStreamReader(regData_1.url().openStream())), regData_1.property());
		ModelSerializer.saveDataset(acpreg, precompReg1, null); 
		ModelSerializer.saveDataset(acpreg, precompReg_diff_encr, faultySpec); 
	
		for (int split : SPLITS_FIRST){
			((ACPRegressor)acpreg.getPredictor()).train(acpreg.getDataset(), split);
		}
		Assert.assertEquals("Only trained partially",SPLITS_FIRST, ((ACPRegressor)acpreg.getPredictor()).getPredictors().keySet());
		Assert.assertFalse(acpreg.getPredictor().isTrained());
		Assert.assertTrue(((ACPRegressor) acpreg.getPredictor()).isPartiallyTrained());
		ModelSerializer.saveModel(acpreg, trainedReg1, null);
		ModelSerializer.saveModel(acpreg, trainedReg_diff_encr, faultySpec);
		allSigs = Lists.newArrayList(((SignaturesDescriptor)acpreg.getDataset().getDescriptors().get(0)).getSignatures()); 

		// second file
		ChemDataset spReg = acpreg.getDataset(); // I.e. identical signatures
		spReg.getDataset().setRecords(new ArrayList<DataRecord>());
		ChemCPRegressor acpreg2 = new ChemCPRegressor(new ACPRegressor(
				new ICPRegressor(new NormalizedNCM((DEFAULT_LibLinear? 
						new LinearSVR(): new EpsilonSVR()))), 
				new RandomSampling(NUM_MODELS_TOTAL, CALIBRATION_RATIO)));
		acpreg2.setDataset(spReg);
		acpreg2.addRecords(new CSVChemFileReader(CSVFormat.TDF.builder().setHeader().build(), new InputStreamReader(regData_1.url().openStream())), regData_1.property());
		ModelSerializer.saveDataset(acpreg2, precompReg2, null); 
		
		acpreg2.getDataset().setDescriptors(new SignaturesDescriptor()); 
		((SignaturesDescriptor) acpreg2.getDataset().getDescriptors().get(0)).setSignatures(allSigs);

		for (int split : SPLITS_SECOND){
			((ACPRegressor)acpreg2.getPredictor()).train(acpreg2.getDataset(), split);
		}
		Assert.assertEquals("Only trained partially",SPLITS_SECOND, ((ACPRegressor)acpreg2.getPredictor()).getPredictors().keySet());
		Assert.assertFalse(acpreg2.getPredictor().isTrained());
		Assert.assertTrue(((ACPRegressor) acpreg2.getPredictor()).isPartiallyTrained());
		ModelSerializer.saveModel(acpreg2, trainedReg2, null);

		// different implementation
		acpreg2.setPredictor(new ACPRegressor(
				new ICPRegressor(new NormalizedNCM((!DEFAULT_LibLinear? 
						new LinearSVR(): new EpsilonSVR()))),
				new RandomSampling(NUM_MODELS_TOTAL, CALIBRATION_RATIO)));
		acpreg2.train();
		ModelSerializer.saveModel(acpreg2, trainedReg_diff_impl, null);
		// different signatures
		acpreg.setDataset(new ChemDataset());
		acpreg.addRecords(new IteratingSDFReader(regData_2.url().openStream(),SilentChemObjectBuilder.getInstance()), regData_2.property());
		acpreg.train(); 
		ModelSerializer.saveModel(acpreg, trainedReg_diff_sigs, null);

	}


	@Test
	public void precompClass_SUCCESS()throws Exception{
		File modelOut = TestUtils.createTempFile("out", MODEL_FILE_SUFFIX);
		// This should work
		mockMain(new String[]{
				Aggregate.CMD_NAME,
				"--key-file",encryptionKeyFile.getAbsolutePath(),
				(FAIL_FAST? "": "-af"),
				"-m", precompClass1.getAbsolutePath(), precompClass2.getAbsolutePath(),
				"-mn", "valid",
				"-mo", modelOut.getAbsolutePath(),
				"--progress-bar",
		});

		//		printLogs();
	}

	@Test
	public void precompClass_DIFF_SIGS()throws Exception{
		//		exit.checkAssertionAfterwards(new PrintSysOutput());
		File modelOut = TestUtils.createTempFile("out", MODEL_FILE_SUFFIX);
		// DIFFERENT SIGNATURES 
		if (FAIL_FAST)
			exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);

		mockMain(new String[]{
				"aggregate",
				"--key-file", encryptionKeyFile.getAbsolutePath(),
				(FAIL_FAST? "": "-af"),
				"-m", precompClass1.getAbsolutePath(), precompClass2.getAbsolutePath(), precompClass_diff_sigs.getAbsolutePath(),
				"-mn", "valid",
				"-mo", modelOut.getAbsolutePath(),
		});

	}

	@Test
	public void precompClass_DIFF_ENC()throws Exception{
		//		exit.checkAssertionAfterwards(new PrintSysOutput());
		// DIFFERENT ENCRYPTION
		File modelOut = TestUtils.createTempFile("new2", MODEL_FILE_SUFFIX);
		if(FAIL_FAST)
			exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);

		mockMain(new String[]{
				"aggregate",
				"--key-file", encryptionKeyFile.getAbsolutePath(),
				(FAIL_FAST? "": "-af"),
				"-m", precompClass1.getAbsolutePath(), 
					precompClass2.getAbsolutePath(), 
					precompClass_diff_encr.getAbsolutePath(),
				"-mn", "valid",
				"-mo", modelOut.getAbsolutePath(),
		});
	}

	@Test
	public void precompClass_DIFF_LABELS()throws Exception{
		//		exit.checkAssertionAfterwards(new PrintSysOutput());
		// DIFFERENT LABELS
		File modelOut = TestUtils.createTempFile("new2", MODEL_FILE_SUFFIX);
		if (FAIL_FAST)
			exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);

		mockMain(new String[]{
				"aggregate",
				"--key-file", encryptionKeyFile.getAbsolutePath(),
				(FAIL_FAST? "": "-af"),
				"-m", precompClass1.getAbsolutePath(), precompClass2.getAbsolutePath(), precompClass_diff_labels.getAbsolutePath(),
				"-mn", "valid",
				"-mo", modelOut.getAbsolutePath(),
				"--echo",
		});

	}

	@Test
	public void precompClass_DIFF_MODEL_TYPE()throws Exception{
		//		exit.checkAssertionAfterwards(new PrintSysOutput());
		// OTHER MODEL types
		File modelOut = TestUtils.createTempFile("new", MODEL_FILE_SUFFIX);
		try{
			mockMain(new String[]{
					"aggregate",
					"--key-file", encryptionKeyFile.getAbsolutePath(),
					(FAIL_FAST? "": "-af"),
					"-m", precompClass1.getAbsolutePath(), precompClass2.getAbsolutePath(), precompReg1.getAbsolutePath(), trainedClass1.getAbsolutePath(), trainedClass2.getAbsolutePath(), trainedClass_diff_encr.getAbsolutePath(),
					"-mn", "valid",
					"-mo", modelOut.getAbsolutePath(),
			});
			if(FAIL_FAST)
				Assert.fail();
		} catch(Exception e){
			if(!FAIL_FAST)
				Assert.fail();
		}

	}


	@Test
	public void precompReg_SUCCESS()throws Exception{
		//		exit.checkAssertionAfterwards(new PrintSysOutput());
		File modelOut = TestUtils.createTempFile("out", MODEL_FILE_SUFFIX);
		// This should work
		mockMain(new String[]{
				"aggregate",
				"--key-file", encryptionKeyFile.getAbsolutePath(),
				(FAIL_FAST? "": "-af"),
				"-m", precompReg1.getAbsolutePath(), precompReg2.getAbsolutePath(),
				"-mn", "valid",
				"-mo", modelOut.getAbsolutePath(),
				"--progress-bar",
		});
		//		printLogs();
	}
	@Test
	public void precompReg_DIFF_SIGS()throws Exception{
		//		exit.checkAssertionAfterwards(new PrintSysOutput());
		if (FAIL_FAST)
			exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		// DIFFERENT SIGNATURES 
		File modelOut = TestUtils.createTempFile("new", MODEL_FILE_SUFFIX);
		//		LoggerUtils.setDebugMode();
		mockMain(new String[]{
				"aggregate",
				(FAIL_FAST? "": "-af"),
				"-m", precompReg1.getAbsolutePath(), precompReg2.getAbsolutePath(), precompReg_diff_sigs.getAbsolutePath(),
				"-mn", "valid",
				"-mo", modelOut.getAbsolutePath(),
		});
	}

	@Test
	public void precompReg_DIFF_ENC()throws Exception{
		//		exit.checkAssertionAfterwards(new PrintSysOutput());
		if(FAIL_FAST)
			exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		// DIFFERENT ENCRYPTION

		File modelOut = TestUtils.createTempFile("new2", MODEL_FILE_SUFFIX);

		mockMain(new String[]{
				"aggregate",
				(FAIL_FAST? "": "-af"),
				"-m", precompReg1.getAbsolutePath(), precompReg2.getAbsolutePath(), precompReg_diff_encr.getAbsolutePath(),
				"-mn", "valid",
				"-mo", modelOut.getAbsolutePath(),
		});

	}

	@Test
	public void precompReg_DIFF_MODEL_TYPE()throws Exception{
		//		exit.checkAssertionAfterwards(new PrintSysOutput());
		if(FAIL_FAST)
			exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		// OTHER MODEL types
		File modelOut = TestUtils.createTempFile("new", MODEL_FILE_SUFFIX);

		mockMain(new String[]{
				"aggregate",
				(FAIL_FAST? "": "-af"),
				"-m", precompReg1.getAbsolutePath(), precompReg2.getAbsolutePath(), trainedClass1.getAbsolutePath(), trainedClass2.getAbsolutePath(), trainedClass_diff_encr.getAbsolutePath(),
				"-mn", "valid",
				"-mo", modelOut.getAbsolutePath(),
		});

	}






	@Test
	public void trainedClass_SUCCESS() throws Exception {
		File modelOut = TestUtils.createTempFile("out", MODEL_FILE_SUFFIX);

		mockMain(new String[]{
				"aggregate",
				"--key-file", encryptionKeyFile.getAbsolutePath(),
				(FAIL_FAST? "": "-af"),
				"-m", trainedClass1.getAbsolutePath(), trainedClass2.getAbsolutePath(),
				"-mn", "valid",
				"-mo", modelOut.getAbsolutePath(),
				"--progress-bar",
		});
		//		printLogs();
	}

	@Test
	public void trainedClass_DIFF_SIGS() throws Exception {
		// DIFFERENT SIGNATURES 
		File modelOut = TestUtils.createTempFile("new", MODEL_FILE_SUFFIX);
		if(FAIL_FAST)
			exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);

		mockMain(new String[]{
				"aggregate",
				"--key-file", encryptionKeyFile.getAbsolutePath(),
				(FAIL_FAST? "": "-af"),
				"-m", trainedClass1.getAbsolutePath(), trainedClass2.getAbsolutePath(), trainedClass_diff_sigs.getAbsolutePath(),
				"-mn", "valid",
				"-mo", modelOut.getAbsolutePath(),
		});
	}
	@Test
	public void trainedClass_DIFF_IMPL() throws Exception {

		// DIFFERENT IMPLEMENTATION
		File modelOut = TestUtils.createTempFile("new2", MODEL_FILE_SUFFIX);
		if(FAIL_FAST)
			exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);

		mockMain(new String[]{
				"aggregate",
				"--key-file", encryptionKeyFile.getAbsolutePath(),
				(FAIL_FAST? "": "-af"),
				"-m", trainedClass1.getAbsolutePath(), trainedClass2.getAbsolutePath(), trainedClass_diff_impl.getAbsolutePath(),
				"-mn", "valid",
				"-mo", modelOut.getAbsolutePath(),
		});

	}
	@Test
	public void trainedClass_DIFF_ENC() throws Exception {
		// DIFFERENT ENCRYPTION
		File modelOut = TestUtils.createTempFile("new", MODEL_FILE_SUFFIX);
		if(FAIL_FAST)
			exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		LoggerUtils.setDebugMode();
		mockMain(new String[]{
				"aggregate",
				"--key-file", encryptionKeyFile.getAbsolutePath(),
				(FAIL_FAST? "": "-af"),
				"-m", trainedClass1.getAbsolutePath(), trainedClass2.getAbsolutePath(), trainedClass_diff_encr.getAbsolutePath(),
				"-mn", "valid",
				"-mo", modelOut.getAbsolutePath(),
		});

	}
	@Test
	public void trainedClass_DIFF_LABELS() throws Exception {

		// DIFFERENT LABELS
		File modelOut = TestUtils.createTempFile("new", MODEL_FILE_SUFFIX);
		if(FAIL_FAST)
			exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);

		mockMain(new String[]{
				"aggregate",
				"--key-file", encryptionKeyFile.getAbsolutePath(),
				(FAIL_FAST? "": "-af"),
				"-m", trainedClass1.getAbsolutePath(), trainedClass_diff_labels.getAbsolutePath(), trainedClass2.getAbsolutePath(),
				"-mn", "valid",
				"-mo", modelOut.getAbsolutePath(),
		});

	}
	@Test
	public void trainedClass_DIFF_MODEL_TYPE() throws Exception {
		// OTHER MODEL types
		File modelOut = TestUtils.createTempFile("new", MODEL_FILE_SUFFIX);
		if(FAIL_FAST)
			exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);

		mockMain(new String[]{
				"aggregate",
				(FAIL_FAST? "": "-af"),
				"-m", trainedClass1.getAbsolutePath(), precompReg1.getAbsolutePath(), precompReg1.getAbsolutePath(), trainedClass1.getAbsolutePath(), trainedClass2.getAbsolutePath(), trainedClass_diff_encr.getAbsolutePath(),
				"-mn", "valid",
				"-mo", modelOut.getAbsolutePath(),
		});


	}

	@Test
	public void trainedReg_SUCCESS()throws Exception{
		File modelOut = TestUtils.createTempFile("out", MODEL_FILE_SUFFIX);

		mockMain(new String[]{
				"aggregate",
				"--key-file", encryptionKeyFile.getAbsolutePath(),
				(FAIL_FAST? "": "-af"),
				"-m", trainedReg1.getAbsolutePath(), trainedReg2.getAbsolutePath(),
				"-mn", "valid",
				"-mo", modelOut.getAbsolutePath(),
				"--progress-bar",
		});

				// printLogs();
	}

	@Test
	public void trainedReg_DIFF_SIGS()throws Exception{
		if(FAIL_FAST)
			exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		// DIFFERENT SIGNATURES 
		File modelOut = TestUtils.createTempFile("new", MODEL_FILE_SUFFIX);

		mockMain(new String[]{
				"aggregate",
				"--key-file", encryptionKeyFile.getAbsolutePath(),
				(FAIL_FAST? "": "-af"),
				"-m", trainedReg1.getAbsolutePath(), trainedReg_diff_sigs.getAbsolutePath(), trainedReg2.getAbsolutePath(),
				"-mn", "valid",
				"-mo", modelOut.getAbsolutePath(),
		});

	}
	@Test
	public void trainedReg_DIFF_IMPL()throws Exception{
		if(FAIL_FAST)
			exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		// DIFFERENT IMPLEMENTATION
		File modelOut = TestUtils.createTempFile("new2", MODEL_FILE_SUFFIX);

		mockMain(new String[]{
				"aggregate",
				"--key-file", encryptionKeyFile.getAbsolutePath(),
				(FAIL_FAST? "": "-af"),
				"-m", trainedReg1.getAbsolutePath(), trainedReg_diff_impl.getAbsolutePath(), trainedReg2.getAbsolutePath(),
				"-mn", "valid",
				"-mo", modelOut.getAbsolutePath(),
		});

	}
	@Test
	public void trainedReg_DIFF_ENC()throws Exception{
		if(FAIL_FAST)
			exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		// DIFFERENT ENCRYPTION
		File modelOut = TestUtils.createTempFile("new", MODEL_FILE_SUFFIX);

		mockMain(new String[]{
				"aggregate",
				"--key-file", encryptionKeyFile.getAbsolutePath(),
				(FAIL_FAST? "": "-af"),
				"-m", trainedReg_diff_encr.getAbsolutePath(),trainedReg1.getAbsolutePath(), trainedReg2.getAbsolutePath(), 
				"-mn", "valid",
				"-mo", modelOut.getAbsolutePath(),
		});

	}

	@Test
	public void trainedReg_DIFF_MODEL_TYPE()throws Exception{
		if(FAIL_FAST)
			exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		// OTHER MODEL types
		File modelOut = TestUtils.createTempFile("new", MODEL_FILE_SUFFIX);

		mockMain(new String[]{
				"aggregate",
				(FAIL_FAST? "": "-af"),
				"-m", trainedReg1.getAbsolutePath(), trainedClass1.getAbsolutePath(), precompReg1.getAbsolutePath(),  trainedClass1.getAbsolutePath(), trainedReg2.getAbsolutePath(), precompClass_diff_labels.getAbsolutePath(),
				"-mn", "valid",
				"-mo", modelOut.getAbsolutePath(),
		});

		// printLogs();

	}

}

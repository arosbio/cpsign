/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp.icp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.management.ServiceNotFoundException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.encryption.utils.EncryptionSpecFactory;
import com.arosbio.io.FileSink;
import com.arosbio.io.FileSource;
import com.arosbio.io.JarDataSink;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.nonconf.classification.NCMMondrianClassification;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.sampling.SingleSample;
import com.arosbio.ml.sampling.TrainSplit;
import com.arosbio.ml.sampling.impl.RandomSplitIterator;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.GzipEncryption;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.ModelComparisonUtils;
import com.arosbio.testutils.UnitTestInitializer;

/**
 * 
 * @author ola
 *
 */
@Category(UnitTest.class)
public class TestICPClassification extends UnitTestInitializer{

	private static final double CALIBRATION_PART = 0.2;

	static EncryptionSpecification spec, spec2;
	static {
		spec = new GzipEncryption("sdsdgasdgf + dsafd");
		spec2 = new GzipEncryption("some other key");
	}
	
	
	@Test
	public void testICPwithStaticSplit() throws Exception {
		SubSet d = null;
		try (InputStream iStream = TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS.openStream();){
			d = SubSet.fromLIBSVMFormat(iStream);
		}
		
		SubSet[] splits = d.splitRandom(.3);
		Dataset p = new Dataset();
		p.withCalibrationExclusiveDataset(splits[0]);
		p.withModelingExclusiveDataset(splits[1]);
		
		ACPClassifier acp = new ACPClassifier(new ICPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC())), new SingleSample());
		acp.train(p);
		
		Assert.assertEquals(1,acp.getPredictors().size());
//		SYS_ERR.println(acp.getProperties());
		
	}

	
	@Test
	public void TestICPClassificationLibLinear() throws IOException, IllegalAccessException{
		
		List<Double> confidences= new ArrayList<>();
		confidences.add(0.6);
		confidences.add(0.7);
		confidences.add(0.8);
		confidences.add(0.9);


		InputStream iStream = TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS.openStream();

		//Read in problem from file
		Dataset problem = Dataset.fromLIBSVMFormat(iStream);

		ICPClassifier licp = new ICPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()));

		//Train model
		TrainSplit icpdataset = new RandomSplitIterator(problem, CALIBRATION_PART, 1).next();
//				randomCalibrationSet(problem.getModelAndCalibrateDataset() , CALIBRATION_PART, true, SeedGenerator.getRandomSeedsGenerator());
		
		licp.train(icpdataset);

		//Predict the first example
		for (int i=0; i<20;i++){
			System.out.println("== Example " + i );
			DataRecord example = problem.getDataset() .get(i);
			Map<Integer,Double> result = licp.predict(example.getFeatures());
			System.out.println("p-values=" + result);
			System.out.println("Correct=" + example.getLabel());
		}
	}
	
	@Test
	public void TestWriteLoadLibSVMClassificationModels() throws IOException, ClassNotFoundException, IllegalAccessException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException{
		
		LoggerUtils.setDebugMode();
		ICPClassifier libsvmclass = new ICPClassifier(new NegativeDistanceToHyperplaneNCM(new C_SVC()));
		Dataset prob = Dataset.fromLIBSVMFormat(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS.openStream());
		Assert.assertTrue(prob.getDataset() .size() > 5);
//		Assert.assertTrue(prob.getY().size() == prob .size());

		// train
		TrainSplit icpdataset = new RandomSplitIterator(prob, CALIBRATION_PART, 1).next();
//				CalibrationSetUtils.randomCalibrationSet(prob , CALIBRATION_PART, true, SeedGenerator.getRandomSeedsGenerator());
		libsvmclass.train(icpdataset);
		
		
		// Plain
		File tmpDir = Files.createTempDirectory("enc-save-dir").toFile();//createTempFile("/tmp/modelsToFile", "");
		tmpDir.deleteOnExit();
		libsvmclass.saveToDataSink(new FileSink(tmpDir), null, spec); //(tmpfile.getAbsolutePath());
		ICPClassifier loadedPlain = new ICPClassifier();
		loadedPlain.loadFromDataSource(new FileSource(tmpDir), null, spec); //fromFiles(tmpDir.getAbsolutePath(), null);
		ModelComparisonUtils.assertEqualMLModels(
				((NCMMondrianClassification)libsvmclass.getNCM()).getModel(), 
				((NCMMondrianClassification)loadedPlain.getNCM()).getModel());
		Assert.assertEquals(libsvmclass.getNCS(), loadedPlain.getNCS());
//		Assert.assertEquals(libsvmclass.getNonconf1(), loadedPlain.getNonconf1());
//		Assert.assertEquals(libsvmclass.nonconf0, loadedPlain.nonconf0);
//		Assert.assertEquals(libsvmclass.nonconf1, loadedPlain.nonconf1);

		// COMPRESS
//		File tmpfileComp = File.createTempFile("/tmp/modelsToFile", ".gz");
//		tmpfileComp.deleteOnExit();
//		libsvmclass.saveCompressed(tmpfileComp.getAbsolutePath());
//		LibSvmICPClassification loadedComp = new LibSvmICPClassification();
//		loadedComp.fromFiles(tmpfileComp.getAbsolutePath(), null);
//
//		Assert.assertEquals(true, ModelComparisonUtils.compareLibSVM_model(libsvmclass.getModel(), loadedComp.getModel()));
//		Assert.assertEquals(libsvmclass.nonconfLists, loadedComp.nonconfLists);
//		Assert.assertEquals(libsvmclass.nonconf0, loadedComp.nonconf0);
//		Assert.assertEquals(libsvmclass.nonconf1, loadedComp.nonconf1);

		// save to file encrypted
		File tmpFileEnc = Files.createTempDirectory("save-dir").toFile(); //Files.createTempDir(); //createTempFile("/tmp/modelsToFileEnc", "");
		tmpFileEnc.deleteOnExit();
//		EncryptionSpecImpl spec = new EncryptionSpecImpl("password", "salt");
		libsvmclass.saveToDataSink(new FileSink(tmpFileEnc), null, spec); //saveEncrypted(tmpFileEnc.getAbsolutePath(), spec);

		// load it back
		ICPClassifier loadedEnc = new ICPClassifier();
		loadedEnc.loadFromDataSource(new FileSource(tmpFileEnc), null, spec); //fromFiles(tmpFileEnc.getAbsolutePath(), spec);

		ModelComparisonUtils.assertEqualMLModels(
				((NCMMondrianClassification)libsvmclass.getNCM()).getModel(), 
				((NCMMondrianClassification)loadedEnc.getNCM()).getModel());
				
		Assert.assertEquals(libsvmclass.getNCS(), loadedEnc.getNCS());
//		Assert.assertEquals(libsvmclass.getNonconf1(), loadedEnc.getNonconf1());

//		Assert.assertEquals(libsvmclass.nonconf0, loadedEnc.nonconf0);
//		Assert.assertEquals(libsvmclass.nonconf1, loadedEnc.nonconf1);
	}

	@Test
	public void TestWriteLoadLibLinearClassificationModels() throws IOException, ClassNotFoundException, IllegalAccessException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, ServiceNotFoundException{
		ICPClassifier liblinclass = getACPClassificationNegDist(true,true).getICPImplementation();
		Dataset prob = Dataset.fromLIBSVMFormat(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_100.openStream());
		Assert.assertTrue(prob.getDataset() .size() > 5);

		// train
		TrainSplit icpdataset = new RandomSplitIterator(prob, CALIBRATION_PART, 1).next(); 
		System.out.println(icpdataset.getTotalNumTrainingRecords() + " ; " + icpdataset.getCalibrationSet().size() + " ; " + icpdataset.getProperTrainingSet().size());
		
		liblinclass.train(icpdataset);
		
		
		// Plain
		File tmpfile = TestUtils.createTempFile("/tmp/modelsToFile", "");
//		File tmpfile = new File("/Users/staffan/Desktop/output/liblin.class.jar");
		try(JarDataSink sink = getJarDataSink(tmpfile)){
			liblinclass.saveToDataSink(sink, "icps", null); //(tmpfile.getAbsolutePath());
		}
		
		ICPClassifier loadedPlain = new ICPClassifier();
		loadedPlain.loadFromDataSource(getJarDataSource(tmpfile), "icps", null);
		
		ModelComparisonUtils.assertEqualMLModels(
				((NCMMondrianClassification) liblinclass.getNCM()).getModel(), 
				((NCMMondrianClassification)loadedPlain.getNCM()).getModel());
				
		Assert.assertEquals(liblinclass.getNCS(), loadedPlain.getNCS());
//		Assert.assertEquals(liblinclass.getNonconf1(), loadedPlain.getNonconf1());
		
		// save to file encrypted
		File tmpFileEnc = TestUtils.createTempFile("/tmp/modelsToFileEnc", "");
//		File tmpFileEnc = new File("/Users/staffan/Desktop/output/enc/encrypted.jar");
		EncryptionSpecification spec = EncryptionSpecFactory.getInstance("");
		EncryptionSpecFactory.configure(spec, spec.generateRandomKey(16));
		
		try(JarDataSink sink = getJarDataSink(tmpFileEnc)){
			liblinclass.saveToDataSink(sink, "enc", spec); 
		}
		

		// load it back
		LoggerUtils.setDebugMode();
		ICPClassifier loadedEnc = new ICPClassifier();
		loadedEnc.loadFromDataSource(getJarDataSource(tmpFileEnc), "enc", spec);
		
		ModelComparisonUtils.assertEqualMLModels(
				((NegativeDistanceToHyperplaneNCM)liblinclass.getNCM()).getModel(), 
				((NegativeDistanceToHyperplaneNCM)loadedEnc.getNCM()).getModel());
		Assert.assertEquals(liblinclass.getNCS(), loadedEnc.getNCS());
//		Assert.assertEquals(liblinclass.getNonconf1(), loadedEnc.getNonconf1());
		
		try{
			ICPClassifier otherSpec = new ICPClassifier();
			otherSpec.loadFromDataSource(getJarDataSource(tmpFileEnc), "enc", spec2);
			Assert.fail();
		} catch (InvalidKeyException e){}
		
	}

	

}


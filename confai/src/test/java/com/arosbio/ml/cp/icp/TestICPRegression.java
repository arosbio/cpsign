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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.DataSink;
import com.arosbio.io.JarDataSink;
import com.arosbio.io.JarDataSource;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.cp.CPRegressionPrediction;
import com.arosbio.ml.cp.nonconf.regression.NormalizedNCM;
import com.arosbio.ml.sampling.RandomCalibSetIterator;
import com.arosbio.ml.sampling.TrainSplit;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.GzipEncryption;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.ModelComparisonUtils;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.UnitTestInitializer;

/**
 * 
 * @author ola
 *
 */
@Category(UnitTest.class)
public class TestICPRegression extends UnitTestInitializer{

	private static final double CALIBRATION_PART = 0.2;

	private static EncryptionSpecification spec, spec2;


	@BeforeClass
	public static void init(){
		spec = new GzipEncryption("first encryption spec");
		spec2 = new GzipEncryption("Second spec, different key");
	}

	@Test
	public void TestICPRegressionLibLinear() throws IOException, IllegalAccessException{

		List<Double> confidences= new ArrayList<>();
		confidences.add(0.6);
		confidences.add(0.7);
		confidences.add(0.8);
		confidences.add(0.9);

		SubSet problem = null;
		try(
				InputStream iStream = TestResources.SVMLIGHTFiles.REGRESSION_HOUSING.openStream();
				) {
			problem = SubSet.fromLIBSVMFormat(iStream);
		}

		//Read in problem from file

		SubSet[] splitted = problem.splitRandom(0.2);
		SubSet trainingProblem = splitted[1];
		SubSet testingProblem = splitted[0];
		Dataset prob = new Dataset();
		prob.setDataset(trainingProblem);

		ICPRegressor lacp = new ICPRegressor(new NormalizedNCM(new LinearSVR(), null));

		//Train model
		TrainSplit icpdataset = new RandomCalibSetIterator(prob, CALIBRATION_PART, 1).next();
		//				CalibrationSetUtils.randomCalibrationSet(trainingProblem , CALIBRATION_PART, false, SeedGenerator.getRandomSeedsGenerator());

		lacp.train(icpdataset);

		LoggerUtils.setDebugMode();
		// Predict all testExamples
		assertConfidencesAreTrue(testingProblem , confidences, lacp);

		//Predict the second example
		//		ISparseFeature[] example = problem.getDataset().get(1);
		//		List<ICPRegressionResult> results = lacp.predict(example,confidences);
		//
		//		System.out.println("LibLinear");
		//		assertWithinInterval(problem, results, problem.getY().get(1));

	}

	private void assertConfidencesAreTrue(List<DataRecord> testProb, List<Double> conf, ICPRegressor icp) throws IllegalAccessException {
		LoggerUtils.setDebugMode();
		int[] numFaulty = new int[conf.size()];
		for(int i=0; i<testProb.size() && i<5; i++){
			DataRecord ex = testProb.get(i);
			double obs = ex.getLabel();
			CPRegressionPrediction allRes = icp.predict(ex.getFeatures(), conf);
			for (int j=0; j< conf.size(); j++)
				if (! allRes.getInterval(conf.get(j)).getInterval().contains(obs))
					numFaulty[j]++;
		}

		for(int i=0; i<numFaulty.length;i++)
			Assert.assertTrue("UnobservedConfidence=" +conf.get(i) + " should have at least " + (conf.get(i)*100) +"% correct predictions", 
					(double)(numFaulty[i])/testProb.size() <= (1-conf.get(i)));

	}


	@Test
	public void TestICPRegressionLibSVM() throws IOException, IllegalAccessException{

		List<Double> confidences= new ArrayList<>();
		confidences.add(0.6);
		confidences.add(0.7);
		confidences.add(0.8);
		confidences.add(0.9);


		InputStream iStream = TestResources.SVMLIGHTFiles.REGRESSION_HOUSING.openStream();

		//Read in problem from file
		SubSet problem = SubSet.fromLIBSVMFormat(iStream);
		SubSet[] splitted = problem.splitRandom(0.2);
		SubSet trainingProblem = splitted[1];
		SubSet testingProblem = splitted[0];
		Dataset prob = new Dataset();
		prob.setDataset(trainingProblem);

		ICPRegressor licp = new ICPRegressor(new NormalizedNCM(new EpsilonSVR()));

		//Train model
		TrainSplit icpdataset = new RandomCalibSetIterator(prob, CALIBRATION_PART, 1).next();
		//				CalibrationSetUtils.randomCalibrationSet(trainingProblem , CALIBRATION_PART, false, SeedGenerator.getRandomSeedsGenerator());

		licp.train(icpdataset);

		assertConfidencesAreTrue(testingProblem , confidences, licp);

		//Predict the second example
		//		ISparseFeature[] example = problem.getDataset().get(1);
		//		List<ICPRegressionResult> results = licp.predict(example,confidences);
		//
		//		System.out.println("LibSVM");
		//		assertWithinInterval(problem, results, problem.getY().get(1));

	}


	@Test
	public void TestWriteLoadLibSvm() throws IOException, IllegalAccessException, NoSuchAlgorithmException, InvalidKeySpecException, ClassNotFoundException, InvalidKeyException{
		//		Logger libsvmLogger = (Logger) LoggerFactory.getLogger(LibSvmICPRegression.class);
		//		libsvmLogger.setLevel(Level.ALL);
		ICPRegressor libsvmReg = new ICPRegressor(new NormalizedNCM(new EpsilonSVR()));
		SubSet data = TestDataLoader.loadSubset(TestResources.SVMLIGHTFiles.REGRESSION_HOUSING_25);
		Assert.assertTrue(data .size() > 5);

		Dataset prob = new Dataset();
		prob.setDataset(data);
		//		Assert.assertTrue(prob.getY().size() == prob .size());
		// train

		TrainSplit icpdataset = new RandomCalibSetIterator(prob, CALIBRATION_PART, 1).next();
		//				CalibrationSetUtils.randomCalibrationSet(prob , CALIBRATION_PART, false, SeedGenerator.getRandomSeedsGenerator());

		LoggerUtils.setDebugMode();
		libsvmReg.train(icpdataset);
		// save to file
		File tmpfile = TestUtils.createTempFile("/tmp/modelsToFile", "");
		try (DataSink sink = getJarDataSink(tmpfile)){
			libsvmReg.saveToDataSink(sink, "icp.plain", null);
		}

		// load it back
		ICPRegressor loaded = new ICPRegressor();
		loaded.loadFromDataSource(getJarDataSource(tmpfile), "icp.plain", null);



		ModelComparisonUtils.assertEqualMLModels(
				((NormalizedNCM) libsvmReg.getNCM()).getErrorModel(),
				((NormalizedNCM) loaded.getNCM()).getErrorModel());
		//						libsvmReg.getErrorModelAlgorithm(), 
		//						loaded.getErrorModelAlgorithm()));

		ModelComparisonUtils.assertEqualMLModels(
				libsvmReg.getScoringAlgorithm(), 
				loaded.getScoringAlgorithm());
		Assert.assertEquals(libsvmReg.getNCS().size(), loaded.getNCS().size()); 

		// ENCRYPTION
		// save to file
		File tmpfileEnc = TestUtils.createTempFile("/tmp/modelsToFile", "");
		try(DataSink sink = getJarDataSink(tmpfileEnc)){
			libsvmReg.saveToDataSink(sink, "icp.enc", spec);
		}

		// load it back
		ICPRegressor loadedEnc = new ICPRegressor();
		loadedEnc.loadFromDataSource(getJarDataSource(tmpfileEnc), "icp.enc", spec);


		ModelComparisonUtils.assertEqualMLModels(
				((NormalizedNCM) libsvmReg.getNCM()).getErrorModel(),
				((NormalizedNCM) loadedEnc.getNCM()).getErrorModel());

		ModelComparisonUtils.assertEqualMLModels(
				libsvmReg.getScoringAlgorithm(), 
				loadedEnc.getScoringAlgorithm());
		Assert.assertEquals(libsvmReg.getNCS().size(), loadedEnc.getNCS().size());

		// Should not work with a different spec
		ICPRegressor loadEncWrongSpec = new ICPRegressor();
		try{
			loadEncWrongSpec.loadFromDataSource(getJarDataSource(tmpfileEnc), "icp.enc", spec2);
			Assert.fail();
		} catch(InvalidKeyException e){

		}
	}


	@Test
	public void TestWriteLoadLibLinear() throws IOException, IllegalAccessException, NoSuchAlgorithmException, InvalidKeySpecException, ClassNotFoundException, InvalidKeyException{
		//		Logger.getLogger(LibSvmICPRegression.class).setLevel(Level.ALL);
		ICPRegressor liblinReg = new ICPRegressor(new NormalizedNCM(new LinearSVR()));
		SubSet data = SubSet.fromLIBSVMFormat(TestResources.SVMLIGHTFiles.REGRESSION_HOUSING_25.openStream());
		Assert.assertTrue(data .size() > 5);
		Dataset prob = new Dataset();
		prob.setDataset(data);

		// train
		TrainSplit icpdataset = new RandomCalibSetIterator(prob, CALIBRATION_PART, 1).next();
		//				CalibrationSetUtils.randomCalibrationSet(prob , CALIBRATION_PART, false, SeedGenerator.getRandomSeedsGenerator());

		liblinReg.train(icpdataset);

		// save to plain file
		File tmpFile = TestUtils.createTempFile("/tmp/modelsToFile", "");
		try(JarDataSink sink = getJarDataSink(tmpFile)){
			liblinReg.saveToDataSink(sink, "icp.plain", null);
		}	

		// load back plain
		ICPRegressor loaded = new ICPRegressor(new NormalizedNCM(new LinearSVR()));
		loaded.loadFromDataSource(getJarDataSource(tmpFile), "icp.plain", null);

		ModelComparisonUtils.assertEqualMLModels(
				((NormalizedNCM) liblinReg.getNCM()).getErrorModel(),
				((NormalizedNCM) loaded.getNCM()).getErrorModel());
		ModelComparisonUtils.assertEqualMLModels(
				liblinReg.getScoringAlgorithm(), 
				loaded.getScoringAlgorithm());
		Assert.assertEquals(liblinReg.getNCS().size(), loaded.getNCS().size());


		// save to file encrypted
		File tmpFileEnc = TestUtils.createTempFile("/tmp/modelsToFileEnc", "");
		
		try(DataSink sink = getJarDataSink(tmpFileEnc)){
			liblinReg.saveToDataSink(sink, "icp.enc", spec);
		}

		// load it back
		ICPRegressor loadedEnc = new ICPRegressor();
		loadedEnc.loadFromDataSource(new JarDataSource(new JarFile(tmpFileEnc)), "icp.enc", spec);

		ModelComparisonUtils.assertEqualMLModels(
				((NormalizedNCM) liblinReg.getNCM()).getErrorModel(),
				((NormalizedNCM) loadedEnc.getNCM()).getErrorModel());
		ModelComparisonUtils.assertEqualMLModels(
				liblinReg.getScoringAlgorithm(), 
				loadedEnc.getScoringAlgorithm());
		Assert.assertEquals(liblinReg.getNCS().size(), loadedEnc.getNCS().size());


		// Should not work with a different spec
		ICPRegressor loadEncWrongSpec = new ICPRegressor();
		try{
			loadEncWrongSpec.loadFromDataSource(getJarDataSource(tmpFileEnc), "icp.enc",spec2);
			Assert.fail();
		} catch(InvalidKeyException e){

		}
	}


}


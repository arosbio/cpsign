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

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.ml.sampling.RandomCalibSetIterator;
import com.arosbio.ml.sampling.TrainSplit;
import com.arosbio.data.SparseFeature;
import com.arosbio.data.SparseFeatureImpl;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.testutils.UnitTestInitializer;

/**
 * 
 * @author ola
 *
 */
@Category(PerformanceTest.class)
public class TestICPRegressionGradient extends UnitTestInitializer{

	private static final double CALIBRATION_PART = 0.2;
	private static final int NUMBER_OBSERVATIONS = 10000;
	private static final double START_STEPSIZE = 0.1;
	private static final double END_STEPSIZE = 0.00524;
	
	private static Dataset problem;
	private static List<DataRecord> testset;
	private static List<Pair<Double, Double>> testDerivs;
	
	@BeforeClass
	public static void setupProblem(){
		problem = new Dataset();
		List<DataRecord> trainingset = new ArrayList<>();
		problem.setDataset(new SubSet(trainingset));

		testset = new ArrayList<>();
		testDerivs = new ArrayList<>();

		//Set up training and test set
		for (int i=0; i< NUMBER_OBSERVATIONS; i++){

			//Generate from function: Y = cos x2/(1 + x1^2 )
			double x1 = Math.random()*2-1;			
			double x2 = Math.random()*2-1;			
			double y = x1*x2;
			
			List<SparseFeature> instance = new ArrayList<>();
			//Index starts at 1 for liblinear and libsvm I presume?
			instance.add(new SparseFeatureImpl(1,x1));
			instance.add(new SparseFeatureImpl(2,x2));

			//Take last 100 into test set
			if (i<(NUMBER_OBSERVATIONS-100)){
				trainingset.add(new DataRecord(y, instance));
			}else{
				testset.add(new DataRecord(y, instance));

				//Add derivatives = correct result
				double derivx1 = x2;
				double derivx2 = x1;
				testDerivs.add(ImmutablePair.of(derivx1, derivx2));
			}	
			
		}
	}
	
	@Test
	public void TestICPRegressionGradientLibLinear() throws IllegalAccessException{
		
		System.out.println("training set size:" + problem.getNumRecords());
		System.out.println("test set size:" + testset.size());
		System.out.println("training...");
		
		ICPRegressor icpreg = getACPRegressionNormalized(true, true).getICPImplementation();
		
		TrainSplit icpdataset = new RandomCalibSetIterator(problem, CALIBRATION_PART, 1).next();

		icpreg.train(icpdataset);
		
		System.out.println("predicting...");
		
		//Evaluate for testset
		double lastMaxNorm=0; 
		for (double stepsize=START_STEPSIZE; stepsize>=END_STEPSIZE;stepsize=stepsize/2){
			double maxNorm = predictGradient(testset, testDerivs, icpreg, stepsize);
			System.out.println("Error for stepsize " + stepsize + ":\t" + maxNorm);

			System.out.println("stepsize: " + stepsize + "; maxnorm=" + maxNorm);
			if (lastMaxNorm>0)
				assertEquals(lastMaxNorm , maxNorm*2, 0.01);
			lastMaxNorm=maxNorm;
		}
	
	}



	
	@Test
	public void TestICPRegressionGradientLibSVM() throws IllegalAccessException{
		
//		System.out.println("training set size:" + trainingset.size());
//		System.out.println("test set size:" + testset.size());

		System.out.println("training...");
		
		ICPRegressor icpreg = getACPRegressionNormalized(false, true).getICPImplementation();
//		LibSvmICPRegression icpreg = new LibSvmICPRegression(1,1);

		LoggerUtils.setDebugMode();
		TrainSplit icpdataset = new RandomCalibSetIterator(problem, CALIBRATION_PART, 1).next(); 
//				CalibrationSetUtils.randomCalibrationSet(problem.getRecords(), CALIBRATION_PART, false, SeedGenerator.getRandomSeedsGenerator());
		System.out.println(icpdataset.toString());
		
		icpreg.train(icpdataset);
		

		System.out.println("predicting...");
		
		//Evaluate for testset
		double lastMaxNorm=0; 
		for (double stepsize=START_STEPSIZE; stepsize>=END_STEPSIZE;stepsize=stepsize/2){
			double maxNorm = predictGradient(testset, testDerivs, icpreg, stepsize);
			System.out.println("Error for stepsize " + stepsize + ":\t" + maxNorm);
			if (lastMaxNorm>0)
				assertEquals(lastMaxNorm , maxNorm*2, 0.01);
			lastMaxNorm=maxNorm;
		}
	
	}	

	
	private double predictGradient(List<DataRecord> testset,
			List<Pair<Double, Double>> testDerivs, ICPRegressor icpreg, double stepsize) throws IllegalStateException {
		double maxNorm;
		maxNorm=0;
		for (int i=0; i<testset.size();i++){
			DataRecord example = testset.get(i);
			List<SparseFeature> gradient = icpreg.calculateGradient(example.getFeatures(), stepsize);
			double diff1 = (gradient.get(0).getValue() - testDerivs.get(i).getLeft());
			double diff2 = (gradient.get(1).getValue() - testDerivs.get(i).getRight());
			if (Math.abs(diff1)>maxNorm)
				maxNorm=Math.abs(diff1);
			if (Math.abs(diff2)>maxNorm)
				maxNorm=Math.abs(diff2);
		}
		return maxNorm;
	}	
	
	

}


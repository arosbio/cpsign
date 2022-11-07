/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp.acp;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.SparseFeature;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.SVC;
import com.arosbio.ml.cp.icp.ICPClassifier;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.sampling.FoldedSampling;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.UnitTestInitializer;

/**
 * 
 * @author ola
 *
 */
@Category(UnitTest.class)
public class TestACPClassificationGradient extends UnitTestInitializer{

	private static final double CALIBRATION_PART = 0.1;
	private static final int nrFolds = 10;
	private static final int nrModels = 10;
	private static final double STEP_SIZE = 0.01;
	

	@Test
	public void TestGradientSimulatedTwoFeaturesLinearKernelACP() throws IOException, IllegalAccessException{
		System.out.println("Linear ACP");
		
		Do_TestGradientSimulatedTwoFeatures(new LinearSVC(),false);
	}

	@Test
	@Category(PerformanceTest.class)
	public void TestGradientSimulatedTwoFeaturesRBFKernelACP() throws IOException, IllegalAccessException{
		System.out.println("RBF ACP");
		Do_TestGradientSimulatedTwoFeatures(new C_SVC(),false);
	}

	@Test
	public void TestGradientSimulatedTwoFeaturesLinearKernelCCP() throws IOException, IllegalAccessException{
		System.out.println("Linear CCP");
		Do_TestGradientSimulatedTwoFeatures(new LinearSVC(),true);
	}

	@Test
	@Category(PerformanceTest.class)
	public void TestGradientSimulatedTwoFeaturesRBFKernelCCP() throws IOException, IllegalAccessException{
		System.out.println("RBF CCP");
		Do_TestGradientSimulatedTwoFeatures(new C_SVC(),true);
	}

	
	private void Do_TestGradientSimulatedTwoFeatures(SVC alg, boolean isCCP) throws IOException, IllegalAccessException{

		// Read in data sets
		Dataset trainData=TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.GENERATED_2FEAT_TRAIN_20K);
		SubSet testData = TestDataLoader.loadSubset(TestResources.SVMLIGHTFiles.GENERATED_2FEAT_TEST_1K);
		
		//Wrap in ACP
		ACPClassifier lacp = new ACPClassifier(new ICPClassifier(new NegativeDistanceToHyperplaneNCM(alg)), 
				(isCCP? new FoldedSampling(nrFolds):new RandomSampling(nrModels, CALIBRATION_PART)));
		
		// train model
		lacp.train(trainData);

		//Loop over test file and predict
		
		
		double classZeroX1 = 0;
		double classZeroX2 = 0;
		int classZeroCnt = 0;
		double classOneX1 = 0;
		double classOneX2 = 0;
		int classOneCnt = 0;
				
		int cnt=0;
//		while (line!=null && cnt<250){
		for (DataRecord r : testData) {
			cnt++;
			if (cnt > 250) {
				break;
			}
			
			
			//PREDICT and get gradient
			Map<Integer, Double> pvals = lacp.predict(r.getFeatures());
			System.out.println(pvals);
			List<SparseFeature> gradient = lacp.calculateGradient(r.getFeatures(), STEP_SIZE);
			
			System.out.println("Observed= " + r.getLabel() 
					+ "; pvalues=[" + Math.round(pvals.get(0)*1000.0)/1000.0 + ";" 
					+ Math.round(pvals.get(1)*1000.0)/1000.0 + "]" 
					+ " Gradient=[" + Math.round(gradient.get(0).getValue()*1000.0)/1000.0
					+ "; " + Math.round(gradient.get(1).getValue()*1000.0)/1000.0 + "]");
			
			if (1-r.getLabel()<0.000001){
				classZeroX1 += gradient.get(0).getValue();
				classZeroX2 += gradient.get(1).getValue();
				classZeroCnt++;
			}else{
				classOneX1 += gradient.get(0).getValue();
				classOneX2 += gradient.get(1).getValue();
				classOneCnt++;
			}

		}
		
		double zeroX1Mean = classZeroX1/classZeroCnt;
		double zeroX2Mean = classZeroX2/classZeroCnt;
		double oneX1Mean = classOneX1/classOneCnt;
		double oneX2Mean = classOneX2/classOneCnt;
		
		System.out.println("zeroX1Mean="+zeroX1Mean);
		System.out.println("zeroX2Mean="+zeroX2Mean);
		System.out.println("oneX1Mean="+oneX1Mean);
		System.out.println("oneX2Mean="+oneX2Mean);
		
		//We expect to get deriv of 2 for X1 if class 1 and -2 for X1 if class 0
		//X2 should be around zero
		Assert.assertEquals(2, zeroX1Mean, 0.1);
		Assert.assertEquals(0, zeroX2Mean, 0.1);
		Assert.assertEquals(-2, oneX1Mean, 0.1);
		Assert.assertEquals(0, oneX2Mean, 0.1);
				
	}


}


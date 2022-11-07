/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp.tcp;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.MathUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.SparseFeature;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.UnitTestInitializer;


/**
 * 
 * Test TCP according to simulated data in Ahlberg et al.
 * "Interpretation of Conformal Prediction Classification Models" 
 * 
 * @author ola
 *
 */
@Category(PerformanceTest.class)
public class TestTCPGradient extends UnitTestInitializer{

	
	static boolean doOnly30First = false;
	final static double stepsize = 0.01;

	@Test
	public void TestFictTwoFeatures() throws Exception {

		//Read in data from file
		Dataset trainData = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.GENERATED_2FEAT_TRAIN_400);
		TCPClassifier ltcp = new TCPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()));
		ltcp.train(trainData);
		
		
		//Loop over test file and predict
		SubSet testData = TestDataLoader.loadSubset(TestResources.SVMLIGHTFiles.GENERATED_2FEAT_TEST_1K);
		
//		BufferedReader testReader = new BufferedReader(new InputStreamReader(testStream));
//		String line = testReader.readLine();
		
		double classZeroX1 = 0;
		double classZeroX2 = 0;
		int classZeroCnt = 0;
		double classOneX1 = 0;
		double classOneX2 = 0;
		int classOneCnt = 0;
		
		System.out.println("==start tcp");
		
		int cnt=0;
		for (DataRecord r : testData) {
//		while(line!=null && (doOnly30First? cnt<30 : true)){
			cnt++;
			if (doOnly30First & cnt>30) {
				break;
			}

			System.out.println("== Line: " + cnt + " ====");

			//Parse line
//			String[] chunks = line.split(" ");
//			double observed = Double.parseDouble(chunks[0]);
//
//			List<SparseFeature> lineFeatures = new ArrayList<>();
//			for (int i = 1; i< chunks.length;i++){
//				String[] feature = chunks[i].split(":");
//				
//				System.out.println("Feature " + Integer.parseInt(feature[0]) + ": " 
//				+ MathUtils.roundTo3significantFigures(Double.parseDouble(feature[1])));
//				
//				lineFeatures.add(new LibLinearSparseFeature(
//						Integer.parseInt(feature[0]),
//						Double.parseDouble(feature[1])));
//			}
			

			//PREDICT and get gradient
			Map<Integer,Double> pvals = ltcp.predict(r.getFeatures());
			List<SparseFeature> gradient = ltcp.calculateGradient(r.getFeatures(), stepsize);
			
			System.out.println("Observed= " + r.getLabel() 
					+ "; pvalues=" + pvals + ";" 
//					+ MathUtils.roundTo3digits(pvals[1]) + "]" 
					+ " Gradient=[" + MathUtils.roundTo3significantFigures(gradient.get(0).getValue())
					+ "; " + MathUtils.roundTo3significantFigures(gradient.get(1).getValue()) + "]");
			Assert.assertEquals(2, gradient.size());
			
//			if (1-observed<0.000001){
//				classZeroX1 = classZeroX1 + gradient.getGradient().get(0).getValue();
//				classZeroX2 = classZeroX2 + gradient.getGradient().get(1).getValue();
//				classZeroCnt++;
//			}else{
//				classOneX1 = classOneX1 + gradient.getGradient().get(0).getValue();
//				classOneX2 = classOneX2 + gradient.getGradient().get(1).getValue();
//				classOneCnt++;
//			}
			if (r.getLabel()<0.000001){
				classZeroX1 += gradient.get(0).getValue();
				classZeroX2 += gradient.get(1).getValue();
				classZeroCnt++;
			}else{
				classOneX1 += gradient.get(0).getValue();
				classOneX2 += gradient.get(1).getValue();
				classOneCnt++;
			}
//			line = testReader.readLine();
		}
//		testReader.close();
		
		double zeroX1Mean = classZeroX1/classZeroCnt;
		double zeroX2Mean = classZeroX2/classZeroCnt;
		double oneX1Mean = classOneX1/classOneCnt;
		double oneX2Mean = classOneX2/classOneCnt;

		System.out.println("oneX1Mean="+oneX1Mean);
		System.out.println("oneX2Mean="+oneX2Mean);
		System.out.println("zeroX1Mean="+zeroX1Mean);
		System.out.println("zeroX2Mean="+zeroX2Mean);

		//We expect to get deriv of 2 for X1 if class 1 and -2 for X1 if class 0
		//X2 should be around zero
		Assert.assertEquals(2, oneX1Mean, 0.05);
		Assert.assertEquals(0, oneX2Mean, 0.05);
		Assert.assertEquals(-2, zeroX1Mean, 0.05);
		Assert.assertEquals(0, zeroX2Mean, 0.05);
				
	}

}

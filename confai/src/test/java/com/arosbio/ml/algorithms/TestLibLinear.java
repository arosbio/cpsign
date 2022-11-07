/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.algorithms;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.transform.feature_selection.VarianceBasedSelector;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.metrics.classification.BalancedAccuracy;
import com.arosbio.ml.metrics.classification.ClassifierAccuracy;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.NonSuiteTest;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.UnitTestInitializer;
import com.google.common.collect.ImmutableMap;


@Category(UnitTest.class)
public class TestLibLinear extends UnitTestInitializer{
	
	@Test
	public void testLinearSVC_train_pred() throws Exception {
		Dataset data = TestDataLoader.getInstance().getDataset(true, false);
		SubSet[] trainTest = data.getDataset().splitRandom(.7);
		SubSet train = trainTest[0];
		SubSet test = trainTest[1];
		
//		SYS_ERR.println("Num features: " + data.getNumAttributes());
		
		VarianceBasedSelector var = new VarianceBasedSelector(15);
		var.fitAndTransform(train);
//		SYS_ERR.println("Num features (after select): " + train.getNumFeatures());
		
		LinearSVC svc = new LinearSVC();
		svc.train(train);
		
		ClassifierAccuracy acc = new ClassifierAccuracy();
		for (DataRecord r : test) {
			int pred = svc.predictClass(var.transform(r.getFeatures()));
//			SYS_ERR.println(r.getFeatures().getLargestFeatureIndex() + " : " + pred);
			acc.addPrediction((int)r.getLabel(), pred);
		}
//		SYS_OUT.println(acc);
		System.out.println(acc);
	}

	@Test
	public void testLinearSVC(){
		LinearSVC m = new LinearSVC();
		System.out.println(m.getConfigParameters());

		double c = 15;
		double eps = 0.001;
		String solverStr = "L2R_L2LOSS_SVC";
		m.setConfigParameters(ImmutableMap.of("c",c,"epsilon",eps,"solver",solverStr));

		Assert.assertEquals(c,m.getC(),0.00001);
		Assert.assertEquals(eps,m.getEpsilon(),0.00001);
		Assert.assertTrue(m.getSolverType().toString().equalsIgnoreCase(solverStr));

		// Make a clone and check the same values are there!
		LinearSVC clone = m.clone();
		Assert.assertEquals(c,clone.getC(),0.00001);
		Assert.assertEquals(eps,clone.getEpsilon(),0.00001);
		Assert.assertTrue(clone.getSolverType().toString().equalsIgnoreCase(solverStr));
		// printLogs();
	}

	@Test
	public void testLinearSVR(){
		LinearSVR m = new LinearSVR();
		System.out.println(m.getConfigParameters());

		double c = 15;
		double eps = 0.001;
		String solverStr = "L2R_L2LOSS_SVC";
		try {
			m.setConfigParameters(ImmutableMap.of("c",c,"epsilon",eps,"solver",solverStr));
			Assert.fail();
		} catch (IllegalArgumentException e){
			// solver is for LinearSVC only
		}
		solverStr = "L2R_L2LOSS_SVR"; // Update with a correct one
		m.setConfigParameters(ImmutableMap.of("c",c,"epsilon",eps,"solver",solverStr));


		Assert.assertEquals(c,m.getC(),0.00001);
		Assert.assertEquals(eps,m.getEpsilon(),0.00001);
		Assert.assertTrue(m.getSolverType().toString().equalsIgnoreCase(solverStr));

		// Make a clone and check the same values are there!
		LinearSVR clone = m.clone();
		Assert.assertEquals(c,clone.getC(),0.00001);
		Assert.assertEquals(eps,clone.getEpsilon(),0.00001);
		Assert.assertTrue(clone.getSolverType().toString().equalsIgnoreCase(solverStr));

		// printLogs();
	}



	/*
	 svm data: 1000: {2.0=585, 1.0=226, 5.0=189}
ClassifierAccuracy: 0.76
Balanced accuracy: 0.6787686809616634
stdAccuracy: 0.76
stdBalanced accuracy: 0.6787686809616634
	 */
	@Category(NonSuiteTest.class)
	@Test
	public void testMulticlass() throws Exception{
		SubSet d = TestDataLoader.loadSubset(TestResources.SVMLIGHTFiles.CLASSIFICATION_7CLASS_LARGE);
//		try (InputStream is = getFromAbsPath(NumericalSVMDatasets.CLASSIFICATION_3CLASS_CON4_PATH)){
//			d = SubSet.fromLIBSVMFormat(is);
//		}
//		SYS_ERR.println("svm connect4: " + d.size() + ": " + d.getLabelFrequencies());
		
//		SYS_ERR.println("svm dna3: " + d.size() + ": " + d.getLabelFrequencies());
		d = d.splitStatic(1000)[0];
		SYS_ERR.println("svm data: " + d.size() + ": " + d.getLabelFrequencies());

//		try (InputStream is = getFromAbsPath(NumericalSVMDatasets.CLASSIFICATION_7CLASS_LARGE_PATH)){
//			d = SubSet.fromLIBSVMFormat(is);
//		}
//		SYS_ERR.println("svm cov: " + d.size() + ": " + d.getLabelFrequencies());
		SubSet[] testTrainSplit = d.splitRandom(0.1);
		
		LinearSVC ll = new LinearSVC();
//		LibLinear ll = new LibLinear(LibLinearParameters.defaultClassification());
		ll.train(testTrainSplit[1]);
		
		
		ClassifierAccuracy a = new ClassifierAccuracy();
		BalancedAccuracy ba = new BalancedAccuracy();
		
		ClassifierAccuracy stdAcc = new ClassifierAccuracy();
		BalancedAccuracy stdBA = new BalancedAccuracy();
		
		for (DataRecord r : testTrainSplit[0]) {
			Map<Integer,Double> distances = ll.predictDistanceToHyperplane(r.getFeatures());
//			SYS_ERR.println(r.getLabel() + ": " + predictionWidths);
			int predictedClass = -100;
			double bestScore = Double.NEGATIVE_INFINITY;
			for (Map.Entry<Integer, Double> p : distances.entrySet()) {
				if (p.getValue() > bestScore) {
					bestScore = p.getValue();
					predictedClass = p.getKey();
				}
				
			}
			a.addPrediction((int)r.getLabel(), predictedClass);
			ba.addPrediction((int)r.getLabel(), predictedClass);
			
			int predClassStd = ll.predictClass(r.getFeatures());
			stdAcc.addPrediction((int)r.getLabel(), predClassStd);
			stdBA.addPrediction((int)r.getLabel(), predClassStd);
		}
//		Assert.assertTrue(a.getScore() > 0.9);
//		Assert.assertTrue(ba.getScore() > 0.9);
		Assert.assertTrue(a.getScore() > 1d/3);
		Assert.assertTrue(ba.getScore() > 1d/3); // better than average
		System.err.println(a);
		System.err.println(ba);
		System.err.println("std" + stdAcc);
		System.err.println("std" + stdBA);
		
//		printLogs();
//		SYS_ERR.println(a);
//		SYS_ERR.println(ba);
	}
}

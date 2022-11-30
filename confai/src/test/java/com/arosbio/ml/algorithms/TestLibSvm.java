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
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.ml.algorithms.impl.LibSvm.KernelType;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.NuSVC;
import com.arosbio.ml.algorithms.svm.NuSVR;
import com.arosbio.ml.metrics.classification.BalancedAccuracy;
import com.arosbio.ml.metrics.classification.ClassifierAccuracy;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.NonSuiteTest;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.TestEnv;
import com.google.common.collect.ImmutableMap;

@Category(UnitTest.class)
public class TestLibSvm extends TestEnv {

	/*
	 svm data: 1000: {2.0=585, 1.0=226, 5.0=189}
ClassifierAccuracy: 0.6
Balanced accuracy: 0.4520615996025832
stdAccuracy: 0.6
stdBalanced accuracy: 0.4520615996025832
	 */
	@Category(NonSuiteTest.class)
	@Test
	public void testMulticlass() throws Exception{
		SubSet d = TestDataLoader.loadSubset(TestResources.SVMLIGHTFiles.CLASSIFICATION_7CLASS_LARGE);
//		try (InputStream is = getFromAbsPath(NumericalSVMDatasets.CLASSIFICATION_3CLASS_CON4_PATH)){
//			d = SubSet.fromLIBSVMFormat(is);
//		}
//		SYS_ERR.println("svm connect4: " + d.size() + ": " + d.getLabelFrequencies());
//		
		d = d.splitStatic(1000)[0];
		SYS_ERR.println("svm data: " + d.size() + ": " + d.getLabelFrequencies());

//		try (InputStream is = getFromAbsPath(NumericalSVMDatasets.CLASSIFICATION_7CLASS_LARGE_PATH)){
//			d = SubSet.fromLIBSVMFormat(is);
//		}
//		SYS_ERR.println("svm cov: " + d.size() + ": " + d.getLabelFrequencies());
		SubSet[] testTrainSplit = d.splitRandom(0.1);
		
		C_SVC ll = new C_SVC();
//		LibSvm ll = new LibSvm(LibSvmParameters.defaultClassification());
		ll.train(testTrainSplit[1]);
		
		
		ClassifierAccuracy a = new ClassifierAccuracy();
		BalancedAccuracy ba = new BalancedAccuracy();
		
		ClassifierAccuracy stdAcc = new ClassifierAccuracy();
		BalancedAccuracy stdBA = new BalancedAccuracy();
		for (DataRecord r : testTrainSplit[0]) {
			Map<Integer,Double> distances = ll.predictDistanceToHyperplane(r.getFeatures());
//			System.err.println(r.getLabel() + ": " + predictionWidths);
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
		Assert.assertTrue(a.getScore() > 1d/3);
		Assert.assertTrue(ba.getScore() > 1d/3); // better than average
		System.err.println(a);
		System.err.println(ba);
		System.err.println("std" + stdAcc);
		System.err.println("std" + stdBA);
		
		
//		printLogs();
	}

	@Test
	public void testC_SVC(){
		C_SVC m = new C_SVC();
		System.out.println(m.getConfigParameters());

		double c = 100;
		double eps = 0.01;
		double coef0 = 8;
		String kernel = "poly";
		double gamma = 0.2;
		m.setConfigParameters(ImmutableMap.of("c",c,"epsilon",eps,"coef0",coef0,"gamma",gamma,"kernel",kernel));

		Assert.assertEquals(c,m.getC(),0.00001);
		Assert.assertEquals(eps,m.getEpsilon(),0.00001);
		Assert.assertEquals(coef0,m.getCoef0(),0.00001);
		Assert.assertEquals(gamma,m.getGamma(),0.00001);
		Assert.assertEquals(KernelType.POLY,m.getKernel());

		// Make a clone and check the same values are there!
		C_SVC clone = m.clone();
		Assert.assertEquals(c,clone.getC(),0.00001);
		Assert.assertEquals(eps,clone.getEpsilon(),0.00001);
		Assert.assertEquals(coef0,clone.getCoef0(),0.00001);
		Assert.assertEquals(gamma,clone.getGamma(),0.00001);
		Assert.assertEquals(KernelType.POLY,clone.getKernel());
		// printLogs();
	}

	@Test
	public void testNuSVC(){
		NuSVC m = new NuSVC();
		System.out.println(m.getConfigParameters());

		double nu = .7;
		double eps = 0.01;
		double coef0 = 8;
		String kernel = "poly";
		double gamma = 0.2;
		m.setConfigParameters(ImmutableMap.of("nu",nu,"epsilon",eps,"coef0",coef0,"gamma",gamma,"kernel",kernel));

		Assert.assertEquals(nu,m.getNu(),0.00001);
		Assert.assertEquals(eps,m.getEpsilon(),0.00001);
		Assert.assertEquals(coef0,m.getCoef0(),0.00001);
		Assert.assertEquals(gamma,m.getGamma(),0.00001);
		Assert.assertEquals(KernelType.POLY,m.getKernel());

		// Make a clone and check the same values are there!
		NuSVC clone = m.clone();
		Assert.assertEquals(nu,m.getNu(),0.00001);
		Assert.assertEquals(eps,clone.getEpsilon(),0.00001);
		Assert.assertEquals(coef0,clone.getCoef0(),0.00001);
		Assert.assertEquals(gamma,clone.getGamma(),0.00001);
		Assert.assertEquals(KernelType.POLY,clone.getKernel());
		// printLogs();
	}

	@Test
	public void testEpsilonSVR(){
		EpsilonSVR m = new EpsilonSVR();
		System.out.println(m.getConfigParameters());

		double c = 100;
		double eps = 0.01;
		double coef0 = 8;
		String kernel = "sigmoid";
		double gamma = 0.2;
		int deg = 5;
		m.setConfigParameters(ImmutableMap.of("c",c,"epsilon",eps,"coef0",coef0,"gamma",gamma,"kernel",kernel,"degree",deg));

		Assert.assertEquals(c,m.getC(),0.00001);
		Assert.assertEquals(eps,m.getEpsilon(),0.00001);
		Assert.assertEquals(coef0,m.getCoef0(),0.00001);
		Assert.assertEquals(gamma,m.getGamma(),0.00001);
		Assert.assertEquals(deg,m.getDegree());
		Assert.assertEquals(KernelType.SIGMOID,m.getKernel());

		// Make a clone and check the same values are there!
		EpsilonSVR clone = m.clone();
		Assert.assertEquals(c,clone.getC(),0.00001);
		Assert.assertEquals(eps,clone.getEpsilon(),0.00001);
		Assert.assertEquals(coef0,clone.getCoef0(),0.00001);
		Assert.assertEquals(gamma,clone.getGamma(),0.00001);
		Assert.assertEquals(deg,m.getDegree());
		Assert.assertEquals(KernelType.SIGMOID,clone.getKernel());

		// printLogs();
	}

	@Test
	public void testNuSVR(){
		NuSVR m = new NuSVR();
		System.out.println(m.getConfigParameters());

		double nu = .5;
		double eps = 0.01;
		double coef0 = 8;
		String kernel = "sigmoid";
		double gamma = 0.2;
		int deg = 5;
		m.setConfigParameters(ImmutableMap.of("nu",nu,"epsilon",eps,"coef0",coef0,"gamma",gamma,"kernel",kernel,"degree",deg));

		Assert.assertEquals(nu,m.getNu(),0.00001);
		Assert.assertEquals(eps,m.getEpsilon(),0.00001);
		Assert.assertEquals(coef0,m.getCoef0(),0.00001);
		Assert.assertEquals(gamma,m.getGamma(),0.00001);
		Assert.assertEquals(deg,m.getDegree());
		Assert.assertEquals(KernelType.SIGMOID,m.getKernel());

		// Make a clone and check the same values are there!
		NuSVR clone = m.clone();
		Assert.assertEquals(nu,clone.getNu(),0.00001);
		Assert.assertEquals(eps,clone.getEpsilon(),0.00001);
		Assert.assertEquals(coef0,clone.getCoef0(),0.00001);
		Assert.assertEquals(gamma,clone.getGamma(),0.00001);
		Assert.assertEquals(deg,m.getDegree());
		Assert.assertEquals(KernelType.SIGMOID,clone.getKernel());

		// printLogs();
	}

}

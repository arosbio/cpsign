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

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.ml.algorithms.linear.LogisticRegression;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.algorithms.svm.NuSVR;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.TestEnv;

@Category(UnitTest.class)
public class TestAlgorithmService extends TestEnv {

	@Test
	public void failingOnes() {
		doFuzzyMatch("liblin", null, true);
		doFuzzyMatch("linear", null, true);
		doFuzzyMatch("linearSVR", null, true);
		doFuzzyMatch("SVR", null, true);
		doFuzzyMatch("SVC", null, true);
	}
	
	@Test
	public void testLinearSVR() {
		doFuzzyMatch("LinearSVR", new LinearSVR(), false);
		doFuzzyMatch("lin_SVr", new LinearSVR(), false);
	}
	
	@Test
	public void testEpsilonSVR() {
		doFuzzyMatch("EpsilonSVR", new EpsilonSVR(), false);
		doFuzzyMatch("epsilSVr", new EpsilonSVR(), false);
	}
	
	@Test
	public void testNuSVR() {
		doFuzzyMatch("NuSVR", new NuSVR(), false);
		doFuzzyMatch("nu-SVr", new NuSVR(), false);
	}
	
	@Test
	public void testLinearSVC() {
		doFuzzyMatch("linear-SVC", new LinearSVC(), false);
		doFuzzyMatch("linear_SVC", new LinearSVC(), false);
		doFuzzyMatch("linaer-SVC", new LinearSVC(), false);
	}
	

	@Test
	public void testLR() {
		doFuzzyMatch("LogisticRegr", new LogisticRegression(), false);
		doFuzzyMatch("LogisticReg", new LogisticRegression(), false);
		doFuzzyMatch("log_regression", new LogisticRegression(), false);
	}

//	@Test
//	public void testInterfacesStuff() {
//		Class<?>[] interf = MLAlgorithm.class.getInterfaces();
//		for (Class<?> clazz : interf) {
//			
//			System.err.println(clazz + " : " + clazz.isAssignableFrom(Named.class) + " : " + clazz.isAssignableFrom(Described.class));
//		}
//		printLogs();
//	}
	
//	@Test
//	public void testLibSVM() {
//		LoggerUtils.setDebugMode();
//		doFuzzyMatch("libsv", new LibSvm());
//		doFuzzyMatch("svm", new LibSvm());
//	}
//
//	@Test
//	public void testProbababilitySVM() {
//		doFuzzyMatch("probabi", new ProbabilisticLibSvm());
//		doFuzzyMatch("probSVM", new ProbabilisticLibSvm());
//	}

	private void doFuzzyMatch(String name, Object c, boolean shouldFail) {
//		AlgorithmService s = AlgorithmService.getInstance();
		try {
			MLAlgorithm loaded = FuzzyServiceLoader.load(MLAlgorithm.class, name); //s.getAlgorithmFuzzyMatch(name);
			Assert.assertTrue(loaded.getClass().equals(c.getClass()));
			if (shouldFail)
				Assert.fail();
		} catch(Exception e) {
			if (!shouldFail) {
				e.printStackTrace();
				Assert.fail();
			}
		}
	}

}

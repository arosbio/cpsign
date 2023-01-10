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

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.Stopwatch;
import com.arosbio.data.Dataset;
import com.arosbio.ml.algorithms.linear.LogisticRegression;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.classification.BalancedAccuracy;
import com.arosbio.ml.metrics.classification.ClassifierAccuracy;
import com.arosbio.ml.metrics.classification.ROC_AUC;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.TestEnv;

@Category(UnitTest.class)
public class TestLogisticRegression extends TestEnv {
	
	@Test
	public void testNumericalDataset() throws Exception {
		Dataset p = TestDataLoader.getInstance().getDataset(true, true);
		
		List<Metric> mets = new ArrayList<>();
		mets.add(new ROC_AUC());
		mets.add(new BalancedAccuracy());
		mets.add(new ClassifierAccuracy());
		TestRunner tester = new TestRunner.Builder(new KFoldCV()).build();
		Stopwatch sw = new Stopwatch();

		// Evaluate the LR algorithm
		sw.start();
		tester.evaluateClassifier(p, new LogisticRegression(), mets);
		sw.stop();
		System.err.println(mets);
		System.err.println("LR: " +sw);
		
		// compare with e.g. linearSVC
		List<Metric> mets2 = cloneMetrics(mets);
		sw.start();
		tester.evaluateClassifier(p, new LinearSVC(), mets2);
		sw.stop();
		System.err.println(mets2);
		System.err.println("linear SVC: " +sw);
		
		for (int i=0; i<mets.size(); i++) {
			Assert.assertEquals(((SingleValuedMetric)mets.get(i)).getScore(), ((SingleValuedMetric)mets.get(i)).getScore(),0.05);
		}
		
	}
	
	
	private static List<Metric> cloneMetrics(List<Metric> list){
		List<Metric> clones = new ArrayList<>(list.size());
		for (Metric m : list)
			clones.add(m.clone());
		return clones;
	}

}

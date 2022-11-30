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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.MathUtils;
import com.arosbio.commons.Stopwatch;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.cp.ConformalClassifier;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.MetricFactory;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.TestEnv;

/**
 * 
 * @author ola
 *
 */
@Category(PerformanceTest.class)
public class TestTCPValidity extends TestEnv {

	private static final int SIZE = 300;
	private static final int numTestRecsToTry = 10;

	@Test
	@Category(PerformanceTest.class)
	public void TestMondrianTCPLibLinear() throws Exception {

		List<Double> lowP= new ArrayList<>();
		List<Double> highP= new ArrayList<>();
		List<Double> correctLabel= new ArrayList<>();

		//Read in problem from file
		Dataset problem = TestDataLoader.getInstance().getDataset(true, false);

		TCPClassifier ltcp = new TCPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()));

		for (int l = problem.getDataset() .size()-SIZE; l<problem.getDataset() .size(); l++){

			System.out.println("Dataset subsize: " + l);

			//Set up incremental dataset
			Dataset growingDataSet = new Dataset();

			// Add all data until current dataset size
			growingDataSet.getDataset() .addAll(problem.getDataset() .subList(0, l));

			//Take next observation as example to predict
			DataRecord example = problem.getDataset() .get(l);

			ltcp.train(growingDataSet);
			//Predict
			Map<Integer,Double> result = ltcp.predict(example.getFeatures());

			System.out.println("Predicted result: " + result);
			System.out.println("Observed value: " + example.getLabel());

			lowP.add(result.get(0));
			highP.add(result.get(1));
			correctLabel.add(example.getLabel());

		}

		//Interpret results
		for (double significance=0.01; significance<1;significance=significance+0.01){
			int incorrect = 0;
			for (int example=0; example<correctLabel.size(); example++){
				if (correctLabel.get(example)==0){
					//predicted=0
					if (lowP.get(example)<=significance){
						//Incorrect prediction
						incorrect++;
					}
				}
				else{
					//predicted=1
					if (highP.get(example)<=significance){
						//Incorrect prediction
						incorrect++;
					}

				}
			}
			double errorRate = ((double)incorrect)/(double)correctLabel.size();
			System.out.println("Significance " + MathUtils.roundTo3significantFigures(significance) + ": " + incorrect + " incorrect of " + correctLabel.size() + " --> rate= " + MathUtils.roundTo3significantFigures(errorRate));
			//			System.out.println("Error rate for significance: " + significance + " = " + errorRate);

			Assert.assertEquals(significance, errorRate, 0.1);

		}
		printLogs();

	}

	@Test
	public void testTCPSameSeedShouldGiveSameResultLibLinear() throws Exception {
		Dataset ds = TestDataLoader.getInstance().getDataset(true, false).clone();
		DataRecord first = ds.getDataset() .remove(0);

		long seed = System.currentTimeMillis();
		TCPClassifier tcp = new TCPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()));
		tcp.setSeed(seed);
		tcp.train(ds);

		Map<Integer, Double> pred = tcp.predict(first.getFeatures());


		TCPClassifier tcpNEW = new TCPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()));
		tcpNEW.setSeed(seed);
		tcpNEW.train(ds);
		Map<Integer, Double> predNEW =tcpNEW.predict(first.getFeatures());

		System.out.println("pred1: " + pred);
		System.out.println("pred2: " + predNEW);

		Assert.assertEquals(pred, predNEW);

	}

	@Test
	public void testTCPSameSeedShouldGiveSameResultLibSVM() throws Exception {
		// Train using the same data set and predict the same test-instance, make sure we get the same result
		Dataset ds = TestDataLoader.getInstance().getDataset(true, false).clone();
		DataRecord first = ds.getDataset().remove(0);

		long seed = System.currentTimeMillis();
		TCPClassifier tcp = new TCPClassifier(new NegativeDistanceToHyperplaneNCM(new C_SVC()));
		tcp.setSeed(seed);
		tcp.train(ds);

		Map<Integer, Double> pred = tcp.predict(first.getFeatures());


		TCPClassifier tcpNEW = new TCPClassifier(new NegativeDistanceToHyperplaneNCM(new C_SVC()));
		tcpNEW.setSeed(seed);
		tcpNEW.train(ds);
		Map<Integer, Double> predNEW = tcpNEW.predict(first.getFeatures());
		System.out.println("pred1: " + pred);
		System.out.println("pred2: " + predNEW);

		Assert.assertEquals(pred, predNEW);

	}

	
	@Test
	public void testTCPDifferentOrderShouldGiveSimilarResultsLibSVM() throws Exception {
		Dataset data = TestDataLoader.getInstance().getDataset(true, false);
		for (int i=0; i<numTestRecsToTry; i++) {
			doDifferentOrderShouldGiveSimilarResults(data, 
					(int) (Math.random()*data.getNumRecords()), 
					new TCPClassifier(new NegativeDistanceToHyperplaneNCM(new C_SVC())));
		}
		// printLogs();

	}

	private void doDifferentOrderShouldGiveSimilarResults(Dataset p, int testRec, TCPClassifier predictor) throws Exception {
		Dataset clone = p.clone();
		DataRecord testEx = clone.getDataset().remove(testRec);

		predictor.train(p);
		Map<Integer, Double> pred = predictor.predict(testEx.getFeatures());

		TCPClassifier tcpNEW = predictor.clone();
		tcpNEW.setSeed(1537430476580l); // This is a combo that (previously) lead to errors
		tcpNEW.train(p);

		Map<Integer, Double> predNEW = tcpNEW.predict(testEx.getFeatures());

		TestUtils.assertEquals(pred, predNEW, 0.4);

	}


	@Test
	public void testTCPDifferentOrderShouldGiveSimilarResultsLibLinear() throws Exception {
		
		Dataset data = TestDataLoader.getInstance().getDataset(true, false);
		for (int i=0; i<numTestRecsToTry; i++) {
			doDifferentOrderShouldGiveSimilarResults(data, 
					(int) (Math.random()*data.getNumRecords()), 
					new TCPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC())));
		}
		// printLogs();
	}

	@Test
	public void testTCPvsACP() throws IOException {
		Dataset dACP = TestDataLoader.getInstance().getDataset(true, true);
		// System.err.println(dACP.size()); // 4601, 100
		Dataset dTCP = dACP.clone();
		KFoldCV cv = new KFoldCV(5);
		long seed = System.currentTimeMillis();
		cv.setSeed(seed);
		TestRunner runner = new TestRunner.Builder(cv).calcMeanAndStd(true).build();
		

		List<Metric> acpMetrics =  MetricFactory.getCPClassificationMetrics(false);
		List<Metric> tcpMetrics = MetricFactory.getCPClassificationMetrics(false);


		ACPClassifier acp = new ACPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()), new RandomSampling(DEFAULT_NUM_MODELS, DEFAULT_CALIBRATION_RATIO));
		TCPClassifier tcp = new TCPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()));

		
		Stopwatch watch = new Stopwatch();
		
		// Evaluate ACP
		// LoggerUtils.setDebugMode(SYS_ERR);
		evalPredictor(acp, dACP, runner, acpMetrics, watch);
		System.err.println("\n");
		// Evaluate TCP
		evalPredictor(tcp, dTCP, runner, tcpMetrics, watch);
		
		

		printLogs();
		


	}

	private static void evalPredictor(ConformalClassifier clf, Dataset data, TestRunner tester, List<Metric> metrics, Stopwatch w){
		w.start();
		List<Metric> output = tester.evaluate(data, clf, metrics);
		w.stop();

		System.err.printf("Ran test for %s in %s%n",clf.getClass().getSimpleName(), w);

		// for (Metric m : output){
		// 	System.err.println(m.getClass().getCanonicalName());
		// }
		System.err.println(output);
	}
		

}

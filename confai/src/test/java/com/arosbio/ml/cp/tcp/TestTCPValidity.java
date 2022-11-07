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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.MathUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.UnitTestInitializer;

/**
 * 
 * @author ola
 *
 */
@Category(PerformanceTest.class)
public class TestTCPValidity extends UnitTestInitializer {

	private static final int SIZE = 300;
	private static final int numTestRecsToTry = 10;

	@Test
	@Category(PerformanceTest.class)
	public void TestMondrianICPLibLinear() throws Exception {

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
		Dataset ds = TestDataLoader.getInstance().getDataset(true, false).clone();
		DataRecord first = ds.getDataset() .remove(0);

		long seed = System.currentTimeMillis();
		TCPClassifier tcp = new TCPClassifier(new NegativeDistanceToHyperplaneNCM(new C_SVC())); //new TCPClassifier(new C_SVC());
		tcp.setSeed(seed);
		tcp.train(ds);

		Map<Integer, Double> pred = tcp.predict(first.getFeatures());


		TCPClassifier tcpNEW = new TCPClassifier(new NegativeDistanceToHyperplaneNCM(new C_SVC())); //new TCPClassifier(new C_SVC());
		tcpNEW.setSeed(seed);
		tcpNEW.train(ds);
		Map<Integer, Double> predNEW =tcpNEW.predict(first.getFeatures());
		System.out.println("pred1: " + pred);
		System.out.println("pred2: " + predNEW);

		Assert.assertEquals(pred, predNEW);

	}

	//	int numRecords = 500;

	
	
	@Test
	public void testTCPDifferentOrderShouldGiveSimilarResultsLibSVM() throws Exception {
		Dataset p = TestDataLoader.getInstance().getDataset(true, false);
		for (int i=0; i<numTestRecsToTry; i++) {
			doDifferentOrderShouldGiveSimilarResults(TestDataLoader.getInstance().getDataset(true, false), 
					(int) (Math.random()*p.getNumRecords()), 
					new TCPClassifier(new NegativeDistanceToHyperplaneNCM(new C_SVC())));
		}
		printLogs();

	}

	private void doDifferentOrderShouldGiveSimilarResults(Dataset p, int testRec, TCPClassifier predictor) throws Exception {
		Dataset clone = p.clone();
		DataRecord testEx = clone.getDataset() .remove(testRec);

		predictor.train(p);
		System.err.println(predictor.getNumObservationsUsed() + ", seed=" + predictor.getSeed() + ", testRec=" + testRec);
		Map<Integer, Double> pred = predictor.predict(testEx.getFeatures());

		TCPClassifier tcpNEW = predictor.clone();
		tcpNEW.setSeed(1537430476580l); // This is a combo that leads to errors
		tcpNEW.train(p);

		System.err.println(tcpNEW.getNumObservationsUsed()+ ", seed=" + tcpNEW.getSeed());
		Map<Integer, Double> predNEW = tcpNEW.predict(testEx.getFeatures());
		System.out.println("pred1: " + pred);
		System.out.println("pred2: " + predNEW);

		TestUtils.assertEquals(pred, predNEW, 0.4);
		//			SYS_OUT.println(systemOutRule.getLog());
		//			SYS_ERR.println(systemErrRule.getLog());

	}


	@Test
	public void testTCPDifferentOrderShouldGiveSimilarResultsLibLinear() throws Exception {
		
		Dataset p = TestDataLoader.getInstance().getDataset(true, false);
		for (int i=0; i<numTestRecsToTry; i++) {
			doDifferentOrderShouldGiveSimilarResults(TestDataLoader.getInstance().getDataset(true, false), 
					(int) (Math.random()*p.getNumRecords()), 
					new TCPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC())));
		}
		printLogs();
	}
		

}

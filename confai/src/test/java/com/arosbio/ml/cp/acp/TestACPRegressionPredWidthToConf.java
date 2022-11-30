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
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.algorithms.svm.SVR;
import com.arosbio.ml.cp.CPRegressionPrediction;
import com.arosbio.ml.cp.CPRegressionPrediction.PredictedInterval;
import com.arosbio.ml.cp.nonconf.regression.AbsDiffNCM;
import com.arosbio.ml.cp.nonconf.regression.LogNormalizedNCM;
import com.arosbio.ml.cp.nonconf.regression.NCMRegression;
import com.arosbio.ml.cp.nonconf.regression.NormalizedNCM;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.TestEnv;

/**
 * 
 * @author ola
 *
 */
@Category(UnitTest.class)
public class TestACPRegressionPredWidthToConf extends TestEnv{

	static Dataset problem;
	static List<DataRecord> examples = new ArrayList<>();
	
	@BeforeClass
	public static void loadData() throws Exception {
		//Read in problem from file
		problem = TestDataLoader.getInstance().getDataset(false, false).clone();
		
		// Take 10 examples to the test-set
		for (int i=0;i<10; i++)
			examples.add(problem.getDataset().remove(0));
	}
	
	@Test
	public void testACPRegressionIntervalsLibLinear() throws Exception {
		System.out.println("LibLinear");
		SVR alg = new LinearSVR(); 
		System.out.println("LogNorm:");
		doTestACPRegressionIntervals(new LogNormalizedNCM(alg, 0.01));
		System.out.println("LogNorm - beta=0:");
		doTestACPRegressionIntervals(new LogNormalizedNCM(alg,.0));
		System.out.println("AbsDiff:");
		doTestACPRegressionIntervals(new AbsDiffNCM(alg));
	}

	@Test
	public void testACPRegressionIntervalsLibSVM() throws Exception {
		System.out.println("LibSVM");
		SVR alg = new EpsilonSVR(); 
		System.out.println("LogNorm:");
		doTestACPRegressionIntervals(new LogNormalizedNCM(alg, 0.01));
		System.out.println("Norm:");
		doTestACPRegressionIntervals(new NormalizedNCM(alg));
		System.out.println("AbsDiff:");
		doTestACPRegressionIntervals(new AbsDiffNCM(alg));
	}

	
	public void doTestACPRegressionIntervals(NCMRegression nonconf) throws IOException, IllegalAccessException{
		
		List<Double> confidences = CollectionUtils.listRange(0, 1, 0.1);

		ACPRegressor lacp = new ACPRegressor(nonconf, 
				new RandomSampling(DEFAULT_NUM_MODELS, DEFAULT_CALIBRATION_RATIO));

		//Train model
		lacp.train(problem);
		
		
		// For every example
		for (DataRecord r : examples) {
			
			for (double conf : confidences) {
				
				CPRegressionPrediction result = lacp.predict(r.getFeatures(), conf);
				
				PredictedInterval interval = result.getInterval(conf);
				Assert.assertEquals(conf, interval.getConfidence(), 0.0001);
				
				
				CPRegressionPrediction widthBasedResult = lacp.predictConfidence(r.getFeatures(), interval.getIntervalWidth());
				Assert.assertTrue(widthBasedResult.getPredictedWidths().size()==1);
				Assert.assertTrue(widthBasedResult.getConfidences().isEmpty());
				
				// Check that the widths and confidences matches!
				PredictedInterval widthInterval = widthBasedResult.getWidthToConfidenceBasedIntervals().get(widthBasedResult.getPredictedWidths().get(0));
				Assert.assertEquals(conf, widthInterval.getConfidence(), .05);
				TestUtils.assertSimilar(interval.getIntervalHalfWidth(), widthInterval.getIntervalHalfWidth(), 0.05);
				TestUtils.assertSimilar(widthInterval.getInterval().lowerEndpoint(), interval.getInterval().lowerEndpoint(), 0.05);
				TestUtils.assertSimilar(widthInterval.getInterval().upperEndpoint(), interval.getInterval().upperEndpoint(), 0.05);
				
			}
			
			
		}
		
	}
	
}


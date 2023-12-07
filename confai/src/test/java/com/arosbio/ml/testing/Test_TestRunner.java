/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.testing;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.GlobalConfig;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.ml.algorithms.Classifier;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.cp.nonconf.regression.LogNormalizedNCM;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.MetricFactory;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.cp.regression.ConfidenceGivenPredictionIntervalWidth;
import com.arosbio.ml.metrics.plots.PlotMetric;
import com.arosbio.ml.metrics.regression.RMSE;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.TestEnv;

@Category(UnitTest.class)
public class Test_TestRunner extends TestEnv {

	public static final int NUM_CV_FOLDS = 10;
	public static final long seed = System.currentTimeMillis();

	@Test
	public void testStandardRegression() {
		try {
			TestRunner runner = new TestRunner.Builder(new KFoldCV(NUM_CV_FOLDS, seed)).build();
			List<Metric> mets = runner.evaluateRegressor(TestDataLoader.getInstance().getDataset(false, true), 
					new LinearSVR(),
					Arrays.asList((SingleValuedMetric)new RMSE()));

			Assert.assertEquals(1, mets.size());
		} catch (Exception e) {
			e.printStackTrace(SYS_ERR);
			Assert.fail("Failed with: " + e.getMessage());
		}
	}

	@Test
	public void testStandardClf() throws Exception {
		Classifier svc = new LinearSVC();
		List<Metric> metrics = MetricFactory.getMetrics(svc, false);
		TestRunner runner = new TestRunner.Builder(new KFoldCV(NUM_CV_FOLDS, seed)).build();
		List<Metric> mets = runner.evaluateClassifier(TestDataLoader.getInstance().getDataset(true, true), 
				svc, 
				metrics);
		//		SYS_OUT.println(mets);
		Assert.assertEquals(metrics.size(), mets.size());
	}
	//	@Test
	//	public void testProblem() throws Exception{
	//		Dataset p = TestDataLoader.getInstance().getProblem(true, true);
	//		SYS_ERR.println(p);
	//		SYS_ERR.println(p.getLabels());
	////		SYS_ERR.println(p.getDataset().toLibSVMFormat());
	//	}

	@Test
	public void testACPClassification() throws Exception {
		runTest(TestDataLoader.getInstance().getDataset(true, true), 
				getACPClassificationNegDist(true, true));
	}

	@Test
	public void testACPRegression() throws Exception {
		runTest(TestDataLoader.getInstance().getDataset(false, true), 
				getACPRegressionNormalized(true, true));
	}

	@Test
	public void testTCPClassification() throws Exception {
		runTest(TestDataLoader.getInstance().getDataset(true, true), 
				getTCPClassification(true));
	}

	@Test
	public void testCVAP() throws Exception {
		// LoggerUtils.setDebugMode(SYS_ERR);
		GlobalConfig.getInstance().setRNGSeed(34567897654l);
		SubSet data = TestDataLoader.getInstance().getDataset(true, false).getDataset().splitStatic(200)[0];
		Dataset ds = new Dataset();
		ds.withDataset(data);
		runTest(ds,
				getAVAPClassification(true, false));
	}

	@Test
	public void testDistanceBasedMetricsRegression() throws Exception {
		Dataset data = TestDataLoader.getInstance().getDataset(false, false); 
		ACPRegressor acp = getACPRegressionNormalized(true, true);

		TestRunner runner = new TestRunner.Builder(new RandomSplit(.2, seed)).build();
		List<Metric> metsInput = MetricFactory.getMetrics(acp,false);
		metsInput.add(new ConfidenceGivenPredictionIntervalWidth(.25));
		metsInput.add(new ConfidenceGivenPredictionIntervalWidth(.3));
		metsInput.add(new ConfidenceGivenPredictionIntervalWidth(.35));
		metsInput.add(new ConfidenceGivenPredictionIntervalWidth(.4));
		List<Metric> mets = runner.evaluate(data, acp, metsInput);

		Assert.assertEquals(mets.size(), metsInput.size());
		//		SYS_OUT.println(mets);
	}

	private void runTest(Dataset p, Predictor pred) {
		try {
			TestRunner runner = new TestRunner.Builder(new KFoldCV(NUM_CV_FOLDS, seed)).build();
			List<Metric> mets = runner.evaluate(p, pred);

			Assert.assertEquals(MetricFactory.getMetrics(pred,false).size(), mets.size());
		} catch (Exception e) {
			e.printStackTrace(SYS_ERR);
			SYS_ERR.println("Failed with seed="+ GlobalConfig.getInstance().getRNGSeed());
			Assert.fail("Failed with: " + e.getMessage());
		}
	}

	@Test
	public void testRunnerReturnsSameEvalPoints() throws Exception{
		TestRunner runner = new TestRunner.Builder(new KFoldCV(10)).build();
		Dataset data = TestDataLoader.getInstance().getDataset(true, true);
		ACPClassifier model = new ACPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()), new RandomSampling());
		List<Metric> metrics = MetricFactory.getCPClassificationMetrics(false);

		List<Double> evalPoints = Arrays.asList(.75);
		MetricFactory.setEvaluationPoints(metrics, evalPoints);

		List<Metric> results = runner.evaluate(data,model,metrics);

		for (Metric m : results){
			if (m instanceof PlotMetric){
				Assert.assertEquals(evalPoints,((PlotMetric)m).getEvaluationPoints());
			}
		}

		// printLogs();

	}

	@Test
	public void testLOOCV_clf() throws Exception {
		TestRunner runner = new TestRunner.Builder(new LOOCV()).build();
		Dataset data = TestDataLoader.getInstance().getDataset(true, true);
		ACPClassifier model = new ACPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()), new RandomSampling());
		List<Metric> metrics = runner.evaluate(data, model);
		for(Metric m : metrics)
			System.err.println(m);
	}

	@Test
	public void testLOOCV_reg() throws Exception {
		TestRunner runner = new TestRunner.Builder(new LOOCV()).build();
		Dataset data = TestDataLoader.getInstance().getDataset(false, true);
		ACPRegressor model = new ACPRegressor(new LogNormalizedNCM(new LinearSVR()), new RandomSampling());
		List<Metric> metrics = runner.evaluate(data, model);
		for(Metric m : metrics)
			System.err.println(m);
	}
}

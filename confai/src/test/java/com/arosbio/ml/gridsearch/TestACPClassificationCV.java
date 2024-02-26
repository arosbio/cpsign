/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.gridsearch;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.cp.ModelCalibration;
import com.arosbio.ml.metrics.cp.classification.ProportionMultiLabelPredictionSets;
import com.arosbio.ml.metrics.cp.classification.ProportionSingleLabelPredictionSets;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.TestEnv;
/**
 * 
 * @author ola
 *
 */
@Category(PerformanceTest.class)
public class TestACPClassificationCV extends TestEnv {

	private static final int NR_FOLDS = 5; //NR_MODELS=3,
	//	private static final double CALIBRATION_RATIO = 0.2;
	private static final double CONFIDENCE = 0.8;
	//	private static final String CV_DATASET = "/resources/spambaseShuffled.svm";

	private static final boolean PRINT_DEBUG = true;
	private static final double TOLERANCE = 0.15;
	private static final long SEED = System.currentTimeMillis();

	//	private static Dataset theProblem = new Dataset();
	//
	//	static {
	//		// Load the problem
	//		InputStream trainStream = get(CV_DATASET);
	//
	//		//Read in problem from file
	//		try {
	//			SubSet tmp = SubSet.fromSparseFile(trainStream);
	//			tmp.shuffle();
	//			SubSet recs = tmp.splitStatic(NR_RECORDS_USED)[0];
	//			theProblem.setDataset(recs);
	//		} catch (IOException e) {
	//			throw new RuntimeException("Failed loading dataset");
	//		}
	//	}



	@Test
	public void TestCVGridSearchLibLinearACP() throws Exception {

		System.out.println("LibLinear CV GridSearch");
		TestCrossValidation(true);

		TestGridSearch(true, new ProportionMultiLabelPredictionSets(Arrays.asList(CONFIDENCE))); //new ProportionMultiLabelPredictions(CONFIDENCE));
		if (PRINT_DEBUG) {
			printLogs();
		}
	}

	@Test
	public void TestCVGridSearchLibLinearACP_SingleLabelPred() throws Exception{

		System.out.println("LibLinear CV GridSearch Using Ratio SingleLabelPred");
		TestCrossValidation(true);

		TestGridSearch(true, new ProportionSingleLabelPredictionSets(Arrays.asList(CONFIDENCE))); //new ProportionSingleLabelPredictions(CONFIDENCE));
		if (PRINT_DEBUG) {
			printLogs();
		}
	}

	@Test
	public void TestCVGridSearchLibSvmACP() throws Exception {
		System.out.println("LibSvm CV GridSearch");
		TestGridSearch(false, new ProportionMultiLabelPredictionSets(Arrays.asList(CONFIDENCE))); //new ProportionMultiLabelPredictions(CONFIDENCE));
		if (PRINT_DEBUG) {
			printLogs();
		}
	}


	public void TestCrossValidation(boolean liblinear) throws Exception{

		//Wrap ICP in ACP
		ACPClassifier lacp = getACPClassificationNegDist(liblinear, true);

		TestRunner cv = new TestRunner.Builder(new KFoldCV(NR_FOLDS)).evalPoints(Arrays.asList(CONFIDENCE)).build();
		List<Metric> cvRes = cv.evaluate(TestDataLoader.getInstance().getDataset(true, false), lacp);

		System.out.println("CV-res: " + cvRes);
		Assert.assertTrue(getAccuracy(cvRes, CONFIDENCE, new ModelCalibration())>= (CONFIDENCE-0.5)); // allow for some worse, it should just give that accuracy on average

	}

	public void TestGridSearch(boolean liblinear, Metric opt) throws Exception {


		//Wrap ICP in ACP
		ACPClassifier lacp = getACPClassificationNegDist(liblinear, true);
		lacp.setSeed(SEED);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		System.out.println("Grid search...");
		GridSearch gridsearch = new GridSearch.Builder().
			testStrategy(new KFoldCV(NR_FOLDS, SEED))
			.confidence(CONFIDENCE)
			.tolerance(TOLERANCE)
			.maxNumResults(-1)
			.evaluationMetric(opt)
			.loggingWriter(new OutputStreamWriter(baos))
			.build();
		// gridsearch.setConfidence(CONFIDENCE);
		// gridsearch.setTolerance(TOLERANCE);
		// gridsearch.setNumberOfResultsToReturn(-1);
		Map<String, List<?>> paramGrid = new HashMap<>();
		paramGrid.put("COST", Arrays.asList(0.01, 0.1, 1d, 10d, 100d));
		if (!liblinear)
			paramGrid.put("GAMMA", Arrays.asList(0.02, 0.5, 2d));

		
		// gridsearch.setLoggingWriter(new OutputStreamWriter(baos));

		GridSearchResult gsres = gridsearch.search(
				TestDataLoader.getInstance().getDataset(true, false), 
				lacp, paramGrid);

		if (PRINT_DEBUG){
			SYS_OUT.println(baos.toString());
		}

		// Check that enough combinations was tested
		int combos = 1;

		for (List<?> p: paramGrid.values())
			combos *= p.size();

		Assert.assertEquals(combos, gsres.getNumGSResults());

		if (PRINT_DEBUG) {
			System.out.println(gsres);
		}

		//Do a CV again
		TestRunner r = new TestRunner.Builder(new KFoldCV(NR_FOLDS, SEED)).evalPoints(Arrays.asList(CONFIDENCE)).build();
		List<Metric> cvRes = r.evaluate(TestDataLoader.getInstance().getDataset(true, false), lacp, Arrays.asList(opt, new ModelCalibration())); 

		System.out.println("CV-res opt vals: " + cvRes);
		Assert.assertTrue(getAccuracy(cvRes, CONFIDENCE, new ModelCalibration(Arrays.asList(CONFIDENCE)))>= (CONFIDENCE-0.5)); // allow for some worse, it should just give that accuracy on average

		// Given a static seed and all the same settings - we should get the same results!
		Assert.assertEquals(gsres.getBestParameters().get(0).getResult(), getEfficiency(cvRes, CONFIDENCE, opt), 0.001);
	}


}


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
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.SVC;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.classification.LogLoss;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.ml.vap.avap.AVAPClassifier;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.UnitTestInitializer;
/**
 * 
 * @author ola
 *
 */
@Category(PerformanceTest.class)
public class TestCVAPClassificationCV extends UnitTestInitializer{

	private static final int NR_FOLDS = 5, NR_MODELS=3, NR_RECORDS_USED = 300;
	private static final double CALIBRATION_RATIO = 0.2;
	private static final double CONFIDENCE = 0.7;

	private static final boolean PRINT_DEBUG = false;
	private static final double TOLERANCE = 0.07;
	private static final long SEED = System.currentTimeMillis();

	private static Dataset theProblem = new Dataset();

	static {

		// Read in data from file
		try {
			
			Dataset tmp = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS); 
			
			SubSet ds = tmp.getDataset();
			ds.shuffle();
			SubSet recs = ds.splitStatic(NR_RECORDS_USED)[0];
			theProblem.withDataset(recs);
		} catch (IOException e) {
			throw new RuntimeException("Failed loading dataset");
		}
	}



	@Test
	public void TestCVGridSearchLibLinear() throws Exception, IllegalAccessException, InstantiationException{
		System.out.println("LibLinear CV GridSearch");
		TestCrossValidation(new LinearSVC());

		TestGridSearch(new LinearSVC());
		if (PRINT_DEBUG) {
			printLogs();
		}
	}

	@Test
	public void TestCVGridSearchLibSvm() throws Exception, IllegalAccessException, InstantiationException{
		System.out.println("LibSvm CV GridSearch");
		//		TestCrossValidation(new EpsilonSVC());
		TestGridSearch(new C_SVC());
		if (PRINT_DEBUG) {
			printLogs();
		}
	}



	public void TestCrossValidation(SVC imp) throws IOException, IllegalAccessException{
		AVAPClassifier cvap = new AVAPClassifier(imp, new RandomSampling(NR_MODELS, CALIBRATION_RATIO), SEED);
		TestRunner cv = new TestRunner.Builder(new KFoldCV(NR_FOLDS)).build();
		List<Metric> cvRes = cv.evaluate(theProblem.clone(), cvap); 

		System.out.printf("CV-res: %s%n", cvRes);
		Assert.assertTrue(getLogLoss(cvRes)>0.2 && getLogLoss(cvRes)<1d); // allow for some worse, it should just give that accuracy on average

	}

	public void TestGridSearch(SVC alg) throws Exception, IllegalAccessException, InstantiationException{


		//Wrap ICP in ACP
		AVAPClassifier cvap = new AVAPClassifier(alg, new RandomSampling(NR_MODELS, CALIBRATION_RATIO));
		cvap.setSeed(SEED);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		System.out.println("Grid search...");
		GridSearch gridsearch = new GridSearch.Builder()
			.testStrategy(new KFoldCV(NR_FOLDS,SEED))
			.confidence(CONFIDENCE)
			.tolerance(TOLERANCE)
			.loggingWriter(new OutputStreamWriter(baos))
			.evaluationMetric(new LogLoss())
			.build();

		Map<String,List<?>> paramGrid = new HashMap<>();
		paramGrid.put("COST", Arrays.asList(0.01, 0.1, 1d, 10d, 100d));
		if (alg instanceof C_SVC)
			paramGrid.put("GAMMA", Arrays.asList(0.02, 0.5, 2d));

		
		GridSearchResult gsres = gridsearch.search(theProblem.clone(), cvap, paramGrid);
		if (PRINT_DEBUG){
			SYS_OUT.println(baos.toString());
		}

		// Check that enough combinations was tested
		List<String> combos = new ArrayList<>();
		for(String line: baos.toString().split("\n"))
			if(line.trim().startsWith("C="))
				combos.add(line);

		if (PRINT_DEBUG) {
			System.out.println(gsres);
		}

		//Set optimal C/Gamma - done in the end of the Grid Search!

		//Do a CV again
		TestRunner cv = new TestRunner.Builder(new KFoldCV(NR_FOLDS,SEED)).build();
//		KFoldCV cv = new KFoldCV(NR_FOLDS, SEED);
		List<Metric> cvRes = cv.evaluate(theProblem.clone(), cvap); 

		System.out.println("CV-res opt vals: " + cvRes);
//		Assert.assertTrue(getAccuracy(cvRes, CONFIDENCE)>= (CONFIDENCE-0.5)); // allow for some worse, it should just give that accuracy on average

		// Given a static seed and all the same settings - we should get the same results!
		Assert.assertEquals(gsres.getBestParameters().get(0).getResult(), getLogLoss(cvRes), 0.001);
	}
	


}


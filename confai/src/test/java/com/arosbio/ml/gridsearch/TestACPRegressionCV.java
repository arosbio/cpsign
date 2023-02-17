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
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.data.Dataset;
import com.arosbio.ml.algorithms.Regressor;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.algorithms.svm.SVR;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.icp.ICPRegressor;
import com.arosbio.ml.cp.nonconf.regression.LogNormalizedNCM;
import com.arosbio.ml.cp.nonconf.regression.NormalizedNCM;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.cp.regression.CPRegressionEfficiencyPlotBuilder;
import com.arosbio.ml.metrics.cp.regression.ConfidenceGivenPredictionIntervalWidth;
import com.arosbio.ml.metrics.cp.regression.MedianPredictionIntervalWidth;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.TestEnv;

/**
 * 
 * @author ola
 *
 */
@Category(PerformanceTest.class)
public class TestACPRegressionCV extends TestEnv {

	private final int NR_MODELS = 10;
	private final boolean PRINT_DEBUG = false;
	private static final double CALIBRATION_RATIO = 0.2;
	private static final long SEED = System.currentTimeMillis();
	private static final double CV_CONFIDENCE = 0.8;

	private static boolean small_DS = true;

	//	@BeforeClass
	//	public static void init() throws JoranException{
	//		LoggerUtils.reloadLoggingConfig();
	//	}
	//
	//	@AfterClass
	//	public static void tareDown() throws JoranException{
	//		LoggerUtils.reloadLoggingConfig();
	//	}

	@Test
	public void testACPRegressionGridSearchLibLinear() throws Exception {
		System.out.println("Liblinear regr cv");
		doTestACPRegressionCV(new LinearSVR());
		try{
			TestGridSearch(new LinearSVR(), null);
			//			Assert.fail();
		} catch(GridSearchException gse){
		}
		if (PRINT_DEBUG) {
			printLogs();
		}
	}

	@Test
	public void testACPRegressionGridSearchLibSVM() throws Exception {
		System.out.println("Libsvm regr cv");

		doTestACPRegressionCV(new EpsilonSVR());

		TestGridSearch(new EpsilonSVR(), null, Arrays.asList(0.001, 0.0001));

		TestGridSearch(new EpsilonSVR(), null);
		if (PRINT_DEBUG) {
			printLogs();
		}
	}

	@Test
	public void testMissingICPorACPimpl() throws Exception {
		try{
			doTestACPRegressionCV(null);
			Assert.fail();
		} catch(Exception e){}
	}

	boolean isLibLin = true;

	@Test
	public void testACPRegBetaValues() throws Exception {
		SVR impl = (isLibLin? new LinearSVR():new EpsilonSVR());
		System.out.println((isLibLin? "LibLinear":"LibSvm"));

		List<Double> betaList = Arrays.asList(0.0, 0.1, 0.2, 0.3, 0.5);
		TestGridSearch(impl, betaList);

		if (PRINT_DEBUG) {
			printLogs();
		}

	}


	@Test
	public void testSVRepsilonEffect() throws Exception {

		Dataset problem = TestDataLoader.getInstance().getDataset(false, small_DS);

		ACPRegressor lacp = new ACPRegressor(new NormalizedNCM(new LinearSVR(),null),
				new RandomSampling(NR_MODELS, CALIBRATION_RATIO));
		lacp.setSeed(SEED);

		Writer writer = new OutputStreamWriter(SYS_OUT);
		// gs.setLoggingWriter(writer);

		GridSearch gs = new GridSearch.Builder()
			.testStrategy(new KFoldCV(DEFUALT_NUM_CV_FOLDS, SEED))
			.confidence(CV_CONFIDENCE)
			.loggingWriter(writer)
			.evaluationMetric(new MedianPredictionIntervalWidth(CV_CONFIDENCE))
			.build();

		
		Map<String,List<?>> grid = new HashMap<>();
		grid.put("SVR_EPSILON", Arrays.asList(1.0, 0.1, 0.01, 0.001));

		GridSearchResult res = gs.search(problem, lacp, grid);
		SYS_OUT.println(res);


	}

	public void doTestACPRegressionCV(SVR impl) throws Exception {
		double conf = 0.8;

		Dataset problem = TestDataLoader.getInstance().getDataset(false, small_DS);

		ACPRegressor lacp = new ACPRegressor(new NormalizedNCM(impl, null),
				new RandomSampling(NR_MODELS, CALIBRATION_RATIO));

		TestRunner runner = new TestRunner.Builder(new KFoldCV(NR_MODELS)).evalPoints(Arrays.asList(conf)).build();

		List<Metric> cvres = runner.evaluate(problem, lacp);

		System.out.println("Efficiency: " + getEfficiency(cvres,conf, new CPRegressionEfficiencyPlotBuilder()));
		//		assertTrue(cvres.getEfficiency()<15);

	}

	public void TestGridSearch(SVR impl, List<Double> betaList) throws Exception {
		TestGridSearch(impl, betaList, null);

	}

	public void TestGridSearch(SVR impl, List<Double> betaList, List<Double> eps) throws Exception {

		Dataset problem = TestDataLoader.getInstance().getDataset(false, small_DS);

		//Wrap ICP in ACP
		System.out.printf("impl.settings =%s%n" , impl.getProperties());
		ACPRegressor acp = new ACPRegressor(new LogNormalizedNCM(impl, null,0),
		new RandomSampling(NR_MODELS, CALIBRATION_RATIO));
		acp.setSeed(SEED);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		System.out.println("Grid search...");
		GridSearch gridsearch = new GridSearch.Builder()
			.testStrategy(new KFoldCV(NR_MODELS, SEED))
			.confidence(CV_CONFIDENCE)
			.maxNumResults(-1)
			.tolerance(.5)
			.loggingWriter(new OutputStreamWriter(baos))
			.evaluationMetric(new MedianPredictionIntervalWidth(CV_CONFIDENCE))
			.build();

		Map<String,List<?>> grid = new HashMap<>();
		grid.put("COST", new ArrayList<>(CollectionUtils.listRange(-1,7,2,2)));
		if (impl instanceof EpsilonSVR)
			grid.put("GAMMA", new ArrayList<>(CollectionUtils.listRange(-3,2,2,2)));
		if (betaList != null)
			grid.put("BETA", new ArrayList<>(betaList));
		if (eps != null) {
			grid.put("EPSILON", new ArrayList<>(eps));
		}

		

		LoggerUtils.setDebugMode();
		GridSearchResult gsres = gridsearch.search(problem, acp, grid);
		System.out.println(baos.toString());

		// Check that enough combinations was tested - 
		int numSearchedCombos = 0;
		try(
				CSVParser parser = new CSVParser(new StringReader(baos.toString()), 
				CSVFormat.DEFAULT.builder().setSkipHeaderRecord(true).build())){ 
			numSearchedCombos = parser.getRecords().size();
		}



		// Count the number of combos that should be generated (some might be failing)
		int numCombos = 1;
		for (List<?> l: grid.values()) {
			numCombos *= l.size();
		}
		Assert.assertEquals("Total number of grid points should be correct",
				numCombos, 
				numSearchedCombos);


		if (PRINT_DEBUG)
			SYS_OUT.println(baos.toString());
		if(gsres !=null){
			if(PRINT_DEBUG){
				System.out.println(gsres.getBestParameters().get(0));
				//				System.out.println("Opt beta: "+gsres.getOptimalBeta());
			}

			//Set optimal C/Gamma - done by the gridsearch class

			System.out.println("optimal params="+impl.getProperties());

			//Do a CV again
			TestRunner runner = new TestRunner.Builder(new KFoldCV(NR_MODELS, SEED)).evalPoints(Arrays.asList(CV_CONFIDENCE)).build();
			List<Metric> cvRes = runner.evaluate(problem, acp);
			double efficiency = getEfficiency(cvRes, CV_CONFIDENCE, new CPRegressionEfficiencyPlotBuilder());
			System.out.printf("re-running CV giving efficiency: %s%n", efficiency);
			Assert.assertTrue(efficiency<20); // TODO - was previously < 13

			// Re-running with optimal settings should give the same results (given same folds, seed etc)
			Assert.assertEquals(gsres.getBestParameters().get(0).getResult(), efficiency, 0.001);

		}



	}

	@Test
	public void testWhichParametersAreUsed() {
		ACPRegressor reg = new ACPRegressor(new ICPRegressor(new LogNormalizedNCM(new LinearSVR(), new EpsilonSVR(),.01)), new RandomSampling(NR_MODELS, CALIBRATION_RATIO));
		if (PRINT_DEBUG)
			SYS_ERR.println(reg.getConfigParameters());
	}

	@Test
	public void testDifferentTargetVsErrorModel() throws Exception {
		TestGridSearch(new LinearSVR(), new EpsilonSVR(), null, null);
		TestGridSearch(new EpsilonSVR(), new LinearSVR(), null, null);
		TestGridSearch(new LinearSVR(), new LinearSVR(), null, null);
		TestGridSearch(new EpsilonSVR(), new EpsilonSVR(), null, null);
	}


	public void TestGridSearch(Regressor impl, Regressor errImpl, List<Double> betaList, List<Double> eps) throws Exception {

		Dataset problem = TestDataLoader.getInstance().getDataset(false, small_DS);

		//Wrap ICP in ACP
		System.out.println("impl.settings =" + impl.getProperties());
		ACPRegressor acp = null;

		if (betaList!=null && !betaList.isEmpty())
			acp = new ACPRegressor(new ICPRegressor(new LogNormalizedNCM(impl, errImpl,0d)),new RandomSampling(NR_MODELS, CALIBRATION_RATIO));
		else
			acp = new ACPRegressor(new ICPRegressor(new NormalizedNCM(impl, errImpl)),new RandomSampling(NR_MODELS, CALIBRATION_RATIO));

		acp.setSeed(SEED);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		System.out.println("Grid search...");
		GridSearch gridsearch = new GridSearch.Builder()
			.testStrategy(new KFoldCV(NR_MODELS, SEED))
			.confidence(CV_CONFIDENCE)
			.maxNumResults(-1)
			.tolerance(1)
			.evaluationMetric(new MedianPredictionIntervalWidth(CV_CONFIDENCE))
			.loggingWriter(new OutputStreamWriter(baos))
			.build();


		Map<String,List<?>> grid = new HashMap<>();
		grid.put("COST", Arrays.asList(1d, 10d, 100d));
		if (impl instanceof EpsilonSVR)
			grid.put("GAMMA", Arrays.asList(0.01, 0.1));
		grid.put("SVR_EPSILON", Arrays.asList(0.01, 0.1));
		grid.put("ERROR_MODEL_COST",grid.get("COST")); // Use the same list of values, but independently of each other

		if (betaList != null && !betaList.isEmpty())
			grid.put("BETA", new ArrayList<>(betaList));


		if (eps != null) {
			grid.put("EPSILON", new ArrayList<>(eps));
		}

		
		GridSearchResult gsres = gridsearch.search(problem, acp, grid);

		// Check that enough combinations was tested
		int searchedParameterCombos = gsres.getBestParameters().size();

		int numCombos = 1;
		for (List<?> l: grid.values()) {
			numCombos *= l.size();
		}
		Assert.assertEquals("Total number of grid points should be correct",
				numCombos, 
				searchedParameterCombos);

		if (PRINT_DEBUG)
			SYS_OUT.println(baos.toString());
		if (gsres !=null){
			if(PRINT_DEBUG){
				System.out.println(gsres);
				//				System.out.println("Opt beta: "+gsres.getOptimalBeta());
			}

			// The grid-searcher will set the optimal parameters when finished

			System.out.println("llParams="+impl.getProperties());

			//Do a CV again
			TestRunner runner = new TestRunner.Builder(new KFoldCV(NR_MODELS, SEED)).evalPoints(Arrays.asList(CV_CONFIDENCE)).build();
			List<Metric> cvRes = runner.evaluate(problem, acp); //acp.crossvalidate(problem, NR_MODELS, Arrays.asList(CV_CONFIDENCE));
			double efficiency = getEfficiency(cvRes, CV_CONFIDENCE, new CPRegressionEfficiencyPlotBuilder());
			System.out.println("re-running CV giving efficiency: " + efficiency);
			//			Assert.assertTrue(efficiency<20); // TODO - was previously < 13

			// Re-running with optimal settings should give the same results (given same folds, seed etc)
			Assert.assertEquals(gsres.getBestParameters().get(0).getResult(),efficiency, 0.001);

		}

		if (PRINT_DEBUG)
			SYS_ERR.println(gsres.getOptimizationType().getName() + 
					(impl instanceof EpsilonSVR? " libsvm":" liblinear") + 
					(errImpl instanceof EpsilonSVR? " : libsvm":" : liblinear"));

	}


	@Test
	public void CV_distanceMetric() throws Exception {
		InputStream trainStream = TestResources.SVMLIGHTFiles.REGRESSION_HOUSING.openStream();

		//Read in problem from file
		Dataset problem = Dataset.fromLIBSVMFormat(trainStream);

		ACPRegressor acp = new ACPRegressor(new ICPRegressor(new NormalizedNCM(new LinearSVR())), new RandomSampling(NR_MODELS, CALIBRATION_RATIO));
		//		KFoldCV cv = new KFoldCV(DEFUALT_NUM_CV_FOLDS);
		TestRunner cv = new TestRunner.Builder(new KFoldCV(DEFUALT_NUM_CV_FOLDS)).build();

		List<Metric> builders = new ArrayList<>();
		builders.add(new ConfidenceGivenPredictionIntervalWidth(3));
		builders.add(new ConfidenceGivenPredictionIntervalWidth(8));

		List<Metric> mets = cv.evaluate(problem, acp, builders);
		ConfidenceGivenPredictionIntervalWidth conf_dist_3 = (ConfidenceGivenPredictionIntervalWidth) mets.get(0);
		ConfidenceGivenPredictionIntervalWidth conf_dist_8 = (ConfidenceGivenPredictionIntervalWidth) mets.get(1);
		Assert.assertEquals(3,conf_dist_3.getCIWidth(),0.000001 );
		Assert.assertEquals(8,conf_dist_8.getCIWidth(),0.000001 );
		Assert.assertTrue(conf_dist_3.getMean() <= conf_dist_8.getMean());
		Assert.assertTrue(conf_dist_3.getMedian() <= conf_dist_8.getMedian());
		//		SYS_OUT.println(mets);

	}

}


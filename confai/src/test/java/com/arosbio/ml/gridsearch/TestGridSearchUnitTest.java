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
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.MathUtils;
import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.MissingDataException;
import com.arosbio.data.transform.scale.RobustScaler;
import com.arosbio.io.SystemOutWriter;
import com.arosbio.ml.algorithms.Classifier;
import com.arosbio.ml.algorithms.Regressor;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.algorithms.svm.SVC;
import com.arosbio.ml.algorithms.svm.SVR;
import com.arosbio.ml.cp.ConformalPredictor;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.icp.ICPClassifier;
import com.arosbio.ml.cp.nonconf.calc.LinearInterpolationPValue;
import com.arosbio.ml.cp.nonconf.calc.SmoothedPValue;
import com.arosbio.ml.cp.nonconf.calc.SplineInterpolatedPValue;
import com.arosbio.ml.cp.nonconf.calc.StandardPValue;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.cp.nonconf.regression.NormalizedNCM;
import com.arosbio.ml.gridsearch.GridSearch.GSResult;
import com.arosbio.ml.gridsearch.GridSearch.ProgressCallback;
import com.arosbio.ml.gridsearch.GridSearch.ProgressInfo;
import com.arosbio.ml.gridsearch.GridSearch.ProgressMonitor;
import com.arosbio.ml.interfaces.ClassificationPredictor;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.MetricFactory;
import com.arosbio.ml.metrics.classification.BinaryBrierScore;
import com.arosbio.ml.metrics.classification.ROC_AUC;
import com.arosbio.ml.metrics.cp.classification.MultiLabelPredictionsPlotBuilder;
import com.arosbio.ml.metrics.cp.classification.SingleLabelPredictionsPlotBuilder;
import com.arosbio.ml.metrics.cp.regression.MeanPredictionIntervalWidth;
import com.arosbio.ml.metrics.cp.regression.MedianPredictionIntervalWidth;
import com.arosbio.ml.metrics.regression.MAE;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.sampling.RandomStratifiedSampling;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.ml.testing.RandomSplit;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.ml.vap.avap.AVAPClassifier;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.MockFailingLinearSVC;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.TestEnv;
import com.google.common.collect.ImmutableMap;

@SuppressWarnings("deprecation")
@Category(UnitTest.class)
public class TestGridSearchUnitTest extends TestEnv {

	private static final int NUM_CV_FOLDS = 10;
	private final static double CV_CONF = .8;
	private static final boolean PRINT_GS_RES = false;

	@Test
	public void testSVMgetDefaultCostGrid() {
		SVC svm = new C_SVC();
		List<ConfigParameter> params = svm.getConfigParameters();
		for (ConfigParameter p : params) {
			for (String name : p.getNames()) {
				if (name.toLowerCase().contains("cost")) {
					List<Object> defaultValues = p.getDefaultGrid();
					Assert.assertTrue(defaultValues.size() > 2);
				}
			}

			// SYS_ERR.println(p);
		}
	}

	@Test
	public void testACPClassificationTunableParams() {
		ACPClassifier acp = new ACPClassifier(
				new ICPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC())),
				new RandomSampling(10, 0.2));
		List<ConfigParameter> params = acp.getConfigParameters();
		for (ConfigParameter p : params) {
			System.out.println(p);
		}
		if (PRINT_GS_RES)
			printLogs();
	}

	public static int getNumCombinations(double start, double step, double end) {
		int num = 1;
		for (double i = start; i < end; i += step)
			num++;
		// num++; // for last one
		return num;
	}

	@Test
	public void testGridSearchACPClassLinearKernel() throws Exception {
		ACPClassifier acp = new ACPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()),
				new RandomStratifiedSampling(DEFAULT_NUM_MODELS, DEFAULT_CALIBRATION_RATIO));

		doTestGridSearchNCMEstimator(acp, new SingleLabelPredictionsPlotBuilder(Arrays.asList(CV_CONF)));

	}


	public void doTestGridSearchNCMEstimator(Predictor pred, Metric metric) throws Exception {

		Dataset prob = TestDataLoader.getInstance().getDataset(pred instanceof ClassificationPredictor, true);

		Map<String, List<?>> paramGrid = new HashMap<>();
		List<Object> cost_values = Arrays.asList(10., 100.);
		paramGrid.put("COST", cost_values);
		paramGrid.put(ConformalPredictor.CONFIG_PVALUE_CALC_PARAM_NAMES.get(0),
				Arrays.asList(new StandardPValue(),
						new SmoothedPValue(),
						new LinearInterpolationPValue(),
						new SplineInterpolatedPValue()));

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (Writer resultsWriter = new OutputStreamWriter(baos);) {
			GridSearch gs = new GridSearch.Builder().testStrategy(new KFoldCV(NUM_CV_FOLDS))
					.confidence(CV_CONF)
					.tolerance(1)
					.maxNumResults(-1)
					.evaluationMetric(metric)
					.loggingWriter(resultsWriter).build();

			// This should be OK
			GridSearchResult res = gs.search(prob,
					pred, paramGrid);

			resultsWriter.close();
			String gsLog = baos.toString();
			System.out.println(gsLog);
			System.out.printf("best_res: %s%n", res.getBestParameters().get(0).getParams());
			
		}
		if (PRINT_GS_RES) {
			printLogs();
		}
	}

	public void doTestGridSearch(Predictor pred, Metric faultyMetric, Metric trueMetric,
			boolean usesGamma)
			throws Exception {
		
		System.err.println("Running with seed: " + GlobalConfig.getInstance().getRNGSeed());
		RandomSplit strat = new RandomSplit();
		if (pred instanceof ClassificationPredictor)
			strat.withStratify(true);
		// TestingStrategy strat = new KFoldCV(NUM_CV_FOLDS);

		Map<String, List<?>> paramGrid = new HashMap<>();
		List<Object> cost_values = Arrays.asList(0.5, 10., 100.);
		paramGrid.put("COST", cost_values);
		List<Object> gammas = Arrays.asList(0.005, 0.05, 0.5);
		if (usesGamma) {
			paramGrid.put("GAMMA", gammas);
		}

		GridSearchResult res = null;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		try (Writer resultsWriter = new OutputStreamWriter(baos);) {
			GridSearch.Builder bldr = new GridSearch.Builder()
					.testStrategy(strat)
					.confidence(CV_CONF)
					.tolerance(1)
					.maxNumResults(-1)
					.loggingWriter(resultsWriter);

			Dataset prob = null;
			if (pred instanceof ClassificationPredictor){
				// make it larger so that VAP doesn't fail occationally 
				prob = TestDataLoader.getInstance().getDataset(true, false);
				prob.withDataset(prob.getDataset().splitStatic(200)[0]); 
			} else {
				prob = TestDataLoader.getInstance().getDataset(false, true);
			}
			
			new RobustScaler().fitAndTransform(prob.getDataset());

			// Failing with a faulty-metric
			try {
				bldr.evaluationMetric(faultyMetric).build().search(prob, pred);
				Assert.fail();
			} catch (IllegalArgumentException e) {
			}

			// This should be OK
			// LoggerUtils.setDebugMode(System.out);
			res = bldr.evaluationMetric(trueMetric).build().search(prob,
					pred, paramGrid);

		}
		// resultsWriter.close();
		String gsLog = baos.toString();
		System.out.println(gsLog);
		if (PRINT_GS_RES) {
			printLogs();
		}

		// Check CSV output matches the result of the rest
		List<Double> bestCosts = new ArrayList<>(), bestGammas = new ArrayList<>();
		double bestEff = 0d;
		boolean isFirst = true;
		try (
				CSVParser resParser = new CSVParser(new StringReader(gsLog),
						CSVFormat.DEFAULT.withFirstRecordAsHeader());) {

			Iterator<CSVRecord> recsIt = resParser.iterator();
			while (recsIt.hasNext()) {
				CSVRecord r = recsIt.next();
				// No rank - scores is in first column (0)
				double eff = Double.NaN;
				try {
					eff = Double.parseDouble(r.get(0));
				} catch (Exception e) {
					// Skip if not valid
					continue;
				}

				// First time only
				if (isFirst) {
					isFirst = false;
					bestEff = eff;
					bestCosts.add(Double.parseDouble(r.get("COST")));
					if (usesGamma) {
						bestGammas.add(Double.parseDouble(r.get("GAMMA")));
					}
				} else if (Double.isNaN(eff)) {
					continue; // SKIP!
				} else if (MathUtils.equals(eff, bestEff)) {
					bestCosts.add(Double.parseDouble(r.get("COST")));
					if (usesGamma)
						bestGammas.add(Double.parseDouble(r.get("GAMMA")));
				} else if ((trueMetric.goalIsMinimization() && eff < bestEff) ||
						!trueMetric.goalIsMinimization() && eff > bestEff) {
					bestEff = eff;
					bestCosts.clear();
					bestCosts.add(Double.parseDouble(r.get("COST")));
					if (usesGamma) {
						bestGammas.clear();
						bestGammas.add(Double.parseDouble(r.get("GAMMA")));
					}
				}

			}
		}
		int combos = 1;
		for (List<?> p : paramGrid.values()) {
			combos *= p.size();
		}
		Assert.assertEquals(combos, res.getNumGSResults());

		// Check results are in the correct order.
		List<Double> resultsList = new ArrayList<>();
		for (GSResult r : res.getBestParameters()) {
			if (trueMetric.goalIsMinimization())
				resultsList.add(r.getResult());
			else
				resultsList.add(0, r.getResult());
		}

		Assert.assertTrue(CollectionUtils.isSorted(resultsList));

		double bestC = (double) res.getBestParameters().get(0).getParams().get("COST");
		System.err.println("Best C: " + bestC + " best costs: " + bestCosts);
		Assert.assertTrue(bestCosts.contains(bestC));

		if (usesGamma) {
			double bestGamma = (double) res.getBestParameters().get(0).getParams().get("GAMMA");
			System.err.println("Best GAMMA: " + bestGamma + " best costs: " + bestGammas);
			Assert.assertTrue(bestGammas.contains(bestGamma));
		}

	}

	@Test
	public void testCallbackAndMonitor(){

	}

	@Test
	public void testGridSearchACPClassRBF()
			throws IllegalArgumentException, IOException, Exception {
		ACPClassifier acp = new ACPClassifier(new NegativeDistanceToHyperplaneNCM(new C_SVC()),
				new RandomStratifiedSampling(DEFAULT_NUM_MODELS, DEFAULT_CALIBRATION_RATIO));
		doTestGridSearch(acp,
				new MedianPredictionIntervalWidth(CV_CONF),
				new SingleLabelPredictionsPlotBuilder(Arrays.asList(CV_CONF)),// new ProportionSingleLabelPredictions(CV_CONF),
				true);

		// compileBestParamsAtFile(acp);
		if (PRINT_GS_RES)
			printLogs();
	}

	@Test
	public void testGridSearchACPRegLinearSVR() throws Exception {

		ACPRegressor acp = new ACPRegressor(new NormalizedNCM(new LinearSVR()),
				new RandomSampling(DEFAULT_NUM_MODELS, DEFAULT_CALIBRATION_RATIO));

		doTestGridSearchNCMEstimator(acp, new MedianPredictionIntervalWidth(CV_CONF));

		// compileBestParamsAtFile(acp);
		if (PRINT_GS_RES)
			printLogs();
	}

	@Test
	public void testGridSearchACPRegRBF() throws Exception {
		ACPRegressor acp = new ACPRegressor(new NormalizedNCM(new EpsilonSVR()),
			new RandomSampling(DEFAULT_NUM_MODELS, DEFAULT_CALIBRATION_RATIO));

		// SYS_ERR.println(acp.getConfigParameters());
		// LoggerUtils.setDebugMode(SYS_ERR);

		doTestGridSearch(acp,
				new MultiLabelPredictionsPlotBuilder(Arrays.asList(CV_CONF)), //new ProportionMultiLabelPredictions(CV_CONF),
				new MeanPredictionIntervalWidth(CV_CONF),
				true);
		// compileBestParamsAtFile(acp);
		if (PRINT_GS_RES)
			printLogs();

	}

	@Test
	public void testGridSearchVAPClassLinearSVC() throws Exception {
		// failing for: rng seed: 1640267630074
		// CPSignSettings.getInstance().setRNGSeed(1640267630074l);
		AVAPClassifier acp = new AVAPClassifier(new LinearSVC(),
				new RandomStratifiedSampling(DEFAULT_NUM_MODELS, DEFAULT_CALIBRATION_RATIO));
		// failing for: rng seed: 1640267630074
		// CPSignSettings.getInstance().setRNGSeed(1640267630074l);
		// SYS_ERR.println("rng seed: " + CPSignSettings.getInstance().getRNGSeed());

		doTestGridSearch(acp,
				new MultiLabelPredictionsPlotBuilder(Arrays.asList(CV_CONF)), // new ProportionMultiLabelPredictions(CV_CONF),
				new ROC_AUC(),
				false);

		// compileBestParamsAtFile(acp);
		if (PRINT_GS_RES)
			printLogs();
	}

	@Test
	public void testGridSearchVAPClassRBF() throws Exception {
		AVAPClassifier acp = new AVAPClassifier(new C_SVC(),
				new RandomStratifiedSampling(DEFAULT_NUM_MODELS, DEFAULT_CALIBRATION_RATIO));

		doTestGridSearch(acp,
				new MedianPredictionIntervalWidth(CV_CONF),
				new BinaryBrierScore(),
				true);

		// compileBestParamsAtFile(acp);
		if (PRINT_GS_RES)
			printLogs();

	}

	@Test
	public void testFailingInputParam() throws Exception {
		AVAPClassifier acp = new AVAPClassifier(new LinearSVC(),
				new RandomSampling());
		Dataset prob = TestDataLoader.getInstance().getDataset(true, true);
		GridSearch gs = new GridSearch.Builder()
				.testStrategy(new RandomSplit())
				.tolerance(1)
				.maxNumResults(-1)
				.build();

		Map<String, List<?>> paramGrid = new HashMap<>();
		List<Object> cost_values = Arrays.asList(-1., 10., 100.);
		paramGrid.put("COST", cost_values);
		GridSearchResult res = gs.search(prob, acp, paramGrid);
		Assert.assertEquals(cost_values.size(), res.getBestParameters().size());

		// for (GSResult r : res.getBestParameters()) {
		// SYS_OUT.println(r);
		// }
	}

	@Test
	public void testLinearSVR() throws Exception {
		Regressor svr = new LinearSVR();
		Dataset ds = TestDataLoader.getInstance().getDataset(false, true);
		Map<String, List<?>> grid = new HashMap<>();
		grid.put("c", Arrays.asList(.5, 10., 100.));
		GridSearch gs = new GridSearch.Builder()
				.testStrategy(new KFoldCV(3))
				.secondaryMetrics(MetricFactory.getMetrics(svr, false)) //MetricFactory.filterToSingleValuedMetrics(
				.loggingWriter(new SystemOutWriter()).build();

		GridSearchResult res = gs.search(ds, svr, grid);
		Assert.assertNotNull(res);
		Assert.assertEquals(3, res.getBestParameters().size());
		Assert.assertTrue(Double.isFinite(res.getBestParameters().get(0).getResult()));
		// SYS_OUT.println(res);

		// printLogs();
	}

	@Test
	public void testRegressionAPI() throws Exception {

		Dataset data = TestDataLoader.getInstance().getDataset(false, false);

		LinearSVR model = new LinearSVR();
		model.setC(1);
		model.setEpsilon(0.001);

		// Only one of the failed settings
		TestRunner runner = new TestRunner.Builder(new KFoldCV(10)).calcMeanAndStd(true).build();
		// List<Metric> results = 
		runner.evaluateRegressor(data, model, Arrays.asList(new MAE()));
		// System.err.println(results);

		// Another failed one
		model.setEpsilon(0.01);
		// results = 
		runner.evaluateRegressor(data, model, Arrays.asList(new MAE()));
		// System.err.println(results);
		// System.err.println(((SingleValuedMetric)results.get(0)).getScore());
		// printLogs();

		// Run as gridsearch

		GridSearch gs = new GridSearch.Builder().testStrategy(new KFoldCV(10)).loggingWriter(new SystemOutWriter()).build();
		Map<String,List<?>> grid = new HashMap<>();
		// grid.put("gamma", new ArrayList<>(CollectionUtils.listRange(-1, 1, 1, 2)));
		grid.put("C", new ArrayList<>(CollectionUtils.listRange(-1, 1, 1)));
		grid.put("epsilon", new ArrayList<>(Arrays.asList(0.0001, 0.001, 0.01)));

		gs.search(data,model,grid);

	}

	// @Test
	public void testEpsilonSVR() throws Exception {
		Regressor svr = new EpsilonSVR();
		Dataset ds = TestDataLoader.getInstance().getDataset(false, true);
		Map<String, List<?>> grid = new HashMap<>();
		grid.put("c", Arrays.asList(.5, 10., 100.));
		grid.put("gamma", Arrays.asList(0.01,0.001,0.0001));
		GridSearch gs = new GridSearch.Builder()
				.testStrategy(new RandomSplit())
				.secondaryMetrics(MetricFactory.getMetrics(svr, false)) //MetricFactory.filterToSingleValuedMetrics(
				.loggingWriter(new SystemOutWriter())
				.maxNumResults(-1)
				.build();

		GridSearchResult res = gs.search(ds, svr, grid);
		System.out.println(res);
		Assert.assertNotNull(res);
		Assert.assertEquals(3*3, res.getBestParameters().size());
		

		// printLogs();
	}

	@Test
	public void testLinearSVC() throws Exception {
		Classifier svc = new LinearSVC();
		Dataset ds = TestDataLoader.getInstance().getDataset(true, true);
		Map<String, List<?>> grid = new HashMap<>();
		grid.put("c", Arrays.asList(.5, 10., 100.));
		GridSearch gs = new GridSearch.Builder()
				.testStrategy(new KFoldCV(2))
				.secondaryMetrics(MetricFactory.getMetrics(svc, false)) //MetricFactory.filterToSingleValuedMetrics(
				.loggingWriter(new SystemOutWriter())
				.build();

		GridSearchResult res = gs.search(ds, svc, grid);
		System.out.println(res);
		Assert.assertNotNull(res);
		Assert.assertEquals(3, res.getNumGSResults());
		Assert.assertTrue(Double.isFinite(res.getBestParameters().get(0).getResult()));

		// printLogs();
	}

	@Test
	public void testC_SVC() throws Exception {
		Classifier svc = new C_SVC();
		Dataset ds = TestDataLoader.getInstance().getDataset(true, true);
		Map<String, List<?>> grid = new HashMap<>();
		grid.put("c", Arrays.asList(.5, 10., 100.));
		grid.put("gamma", Arrays.asList(0.01,0.001,0.0001));
		GridSearch gs = new GridSearch.Builder()
				.testStrategy(new RandomSplit())
				.secondaryMetrics(MetricFactory.getMetrics(svc, false)) //MetricFactory.filterToSingleValuedMetrics(
				.loggingWriter(new SystemOutWriter())
				.build();

		GridSearchResult res = gs.search(ds, svc, grid);
		System.out.println(res);
		Assert.assertNotNull(res);
		Assert.assertEquals(9, res.getNumGSResults());

		// printLogs();
	}


	private static final int NR_FOLDS = 5; 
	private static final double CONFIDENCE = 0.8;

	private static final boolean PRINT_DEBUG = false;
	private static final double TOLERANCE = 0.15;
	private static final long SEED = System.currentTimeMillis();

	@Test
	public void testGSWithBadArgsPredictor() throws Exception {
		MockFailingLinearSVC mockSVC = new MockFailingLinearSVC();
		mockSVC.probTrainError = .075;
		mockSVC.probTrainExcept = .075;
		mockSVC.probPredError = 0.0001;
		mockSVC.probPredExcept = 0.0001;

		ACPClassifier lacp = new ACPClassifier(new NegativeDistanceToHyperplaneNCM(mockSVC), new RandomSampling());
		lacp.setSeed(SEED);

		System.out.println("Grid search...");
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		GridSearchResult gsres = null;
		try (OutputStreamWriter writer = new OutputStreamWriter(baos)) {

			GridSearch gridsearch = new GridSearch.Builder()
					.testStrategy(new KFoldCV(NR_FOLDS, SEED))
					.confidence(CONFIDENCE)
					.tolerance(TOLERANCE)
					.maxNumResults(-1)
					.loggingWriter(writer)
					.build();

			Map<String, List<?>> paramGrid = new HashMap<>();
			paramGrid.put("COST", Arrays.asList(0.01, 1d, 100d));
			paramGrid.put("EPSILON", Arrays.asList(0.01, "true", -1, 0.05, 0.02));

			gsres = gridsearch.search(
					TestDataLoader.getInstance().getDataset(true, false),
					lacp, paramGrid);

		} catch (Exception e) {
		}

		if (gsres != null) {
			System.out.println("Grid results; " + gsres);
			System.err.println("warnings: " + gsres.getWarning());
			// Check that enough combinations was tested
			Assert.assertEquals(3 * 5, gsres.getNumGSResults());
		}

		if (PRINT_DEBUG) {
			System.out.println(baos.toString());
			printLogs();
			// SYS_OUT.println(baos.toString());
		}

	}

	@Test
	public void testGSWithBadArgsClassifier() {
		MockFailingLinearSVC mockSVC = new MockFailingLinearSVC();
		mockSVC.probTrainError = .075;
		mockSVC.probTrainExcept = .075;
		mockSVC.probPredError = 0.0001;
		mockSVC.probPredExcept = 0.0001;

		System.out.println("Grid search...");

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		GridSearchResult gsres = null;

		try (OutputStreamWriter writer = new OutputStreamWriter(baos)) {

			GridSearch gridsearch = new GridSearch.Builder()
					.testStrategy(new KFoldCV(NR_FOLDS, SEED))
					.confidence(CONFIDENCE)
					.tolerance(TOLERANCE).maxNumResults(-1)
					.loggingWriter(writer)
					.build();

			Map<String, List<?>> paramGrid = new HashMap<>();
			paramGrid.put("COST", Arrays.asList(0.01, 1d, 100d));
			paramGrid.put("EPSILON", Arrays.asList(0.01, "true", -1, 0.05, 0.02));

			gsres = gridsearch.search(
					TestDataLoader.getInstance().getDataset(true, false),
					mockSVC, paramGrid);

		} catch (Exception e) {
		}

		if (gsres != null) {
			System.out.println("Grid results; " + gsres);
			System.err.println("warnings: " + gsres.getWarning());
			// Check that enough combinations was tested
			Assert.assertEquals(3 * 5, gsres.getNumGSResults());
		}

		if (PRINT_DEBUG) {
			System.out.println(baos.toString());
			printLogs();
			// SYS_OUT.println(baos.toString());
		}
	}

	@Test
	public void testGSWithMissingDataFeats() throws Exception {
		// List<ChemDescriptor> desc = new ArrayList<>();
		// desc.add(DescriptorFactory.getInstance().getDescriptor("AromaticAtomsCountDescriptor"));
		// // desc.add(new MockFailingDescriptor().withProbOfFailure(0.1));
		// desc.add(DescriptorFactory.getInstance().getDescriptor("AtomCountDescriptor"));

		// ChemDataset ds = new ChemDataset(desc);
		// ds.initializeDescriptors();
		Dataset ds = null;
		try (
				InputStream in = TestResources.SVMLIGHTFiles.REGRESSION_ENRICHMENT.openStream();) {

					ds = Dataset.fromLIBSVMFormat(in);
					

					// Generate some artificial missing records
					// SYS_ERR.println("NUM RECORDS: " + info.getNumSuccessfullyAdded());

					SubSet data = ds.getDataset();
					data.get(3).getFeatures().withFeature(0, null);
					data.get(7).getFeatures().withFeature(1, Double.NaN);
					data.get(15).getFeatures().withFeature(1, Double.NaN);

			Assert.assertTrue(DataUtils.containsMissingFeatures(data));
		}

		// Try using Complete Predictor
		ACPRegressor acp = getACPRegressionAbsDiff(true, true);
		try {
			new GridSearch.Builder()
					.loggingWriter(new OutputStreamWriter(System.out))
					.build()
					.search(ds, acp);
			Assert.fail("Missing data - should fail!");
		} catch (MissingDataException e) {
		}

		// Try using only ML Algorithm
		SVR svr = new LinearSVR();
		try {
			new GridSearch.Builder()
					.loggingWriter(new OutputStreamWriter(System.out))
					.build()
					.search(ds, svr, ImmutableMap.of("C", Arrays.asList(1, 10, 100)));
			Assert.fail("Missing data - should fail!");
		} catch (MissingDataException e) {
		}
	}

	@Test
	public void testProgressInfoPredictor() throws Exception {
		AVAPClassifier acp = new AVAPClassifier(new LinearSVC(),
				new RandomSampling());
		Dataset ds = TestDataLoader.getInstance().getDataset(true, true);
		Map<String, List<?>> grid = new HashMap<>();
		grid.put("c", Arrays.asList(1, 2, .5, 10., 100.));
		GridSearch gs = new GridSearch.Builder()
				.testStrategy(new RandomSplit())
				.secondaryMetrics(MetricFactory.getMetrics(acp, false)) //MetricFactory.filterToSingleValuedMetrics(
				.register(new MyProgressCallback())
				.register(new MyEarlyStoppingMonitor(3))
				.loggingWriter(new SystemOutWriter())
				.build();

		GridSearchResult res = gs.search(ds, acp, grid);
		System.out.println(res);
		Assert.assertNotNull(res);
		Assert.assertEquals(3, res.getNumGSResults());	
		Assert.assertTrue("should be a warning message",res.getWarning().length()>10);
	}

	@Test
	public void testProgressInfoMLAlg() throws Exception {
		Classifier svc = new C_SVC();
		Dataset ds = TestDataLoader.getInstance().getDataset(true, true);
		Map<String, List<?>> grid = new HashMap<>();
		grid.put("c", Arrays.asList(.5, 10., 100.));
		GridSearch gs = new GridSearch.Builder()
				.testStrategy(new RandomSplit())
				.secondaryMetrics(MetricFactory.getMetrics(svc, false)) //MetricFactory.filterToSingleValuedMetrics(
				.register(new MyProgressCallback())
				.register(new MyEarlyStoppingMonitor(2))
				.loggingWriter(new SystemOutWriter())
				.build();

		GridSearchResult res = gs.search(ds, svc, grid);
		System.out.println(res);
		Assert.assertNotNull(res);
		Assert.assertEquals(2, res.getNumGSResults());	
		Assert.assertTrue("should be a warning message",res.getWarning().length()>10);
		
	}

	private static class MyProgressCallback implements ProgressCallback {

		// First time called should be after completing the first grid point
		int counter = 1;
		@Override
		public void updatedInfo(ProgressInfo info) {
			Assert.assertEquals("the current step should always match the counter",counter, info.getNumProcessedGridPoints());
			counter++;
		}

	}

	private static class MyEarlyStoppingMonitor implements ProgressMonitor {
		public int maxNumPoints;
		// First time called should be after completing the first grid point
		int counter = 1;
		public MyEarlyStoppingMonitor(int maxStep){
			maxNumPoints = maxStep;
		}
		@Override
		public Action actOnInfo(ProgressInfo info) {
			try{
				Assert.assertEquals("the current step should always match the counter",counter, info.getNumProcessedGridPoints());
				if (counter < maxNumPoints)
					return Action.CONTINUE;
				// if equal - exit execution
				return Action.EXIT;
			} finally{
				counter++;
			}

		}

	}

}

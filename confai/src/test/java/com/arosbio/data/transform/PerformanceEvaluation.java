/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.transform;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.Stopwatch;
import com.arosbio.data.Dataset;
import com.arosbio.data.transform.feature_selection.L1_SVC_Selector;
import com.arosbio.data.transform.feature_selection.L2_SVC_Selector;
import com.arosbio.data.transform.feature_selection.L2_SVR_Selector;
import com.arosbio.data.transform.feature_selection.VarianceBasedSelector;
import com.arosbio.data.transform.scale.MinMaxScaler;
import com.arosbio.data.transform.scale.RobustScaler;
import com.arosbio.data.transform.scale.Standardizer;
import com.arosbio.ml.algorithms.Classifier;
import com.arosbio.ml.algorithms.Regressor;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.algorithms.svm.SVC;
import com.arosbio.ml.algorithms.svm.SVR;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.icp.ICPClassifier;
import com.arosbio.ml.cp.icp.ICPRegressor;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.cp.nonconf.regression.LogNormalizedNCM;
import com.arosbio.ml.cp.nonconf.regression.NormalizedNCM;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.regression.PointPredictionMetric;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.testutils.TestDataLoader;

@Category(PerformanceTest.class)
public class PerformanceEvaluation {
	
	long SEED = GlobalConfig.getInstance().getRNGSeed();
	
	static ACPClassifier getPredictor() {
		SVC alg = new LinearSVC();
		return new ACPClassifier(
				new ICPClassifier(new NegativeDistanceToHyperplaneNCM(alg)), 
				new RandomSampling(10, .2));
	}
	
	static Dataset getProb() throws Exception {
		return TestDataLoader.getInstance().getDataset(true, false);
	}
	
	static ACPRegressor getPredictorReg() {
		LinearSVR alg = new LinearSVR();

		return new ACPRegressor(
				new ICPRegressor(new LogNormalizedNCM(alg, 0d)), 
				new RandomSampling(10, .2));
	}
	
	static Dataset getProbReg() throws Exception {
		return TestDataLoader.getInstance().getDataset(false, false);
	}
	
	static Dataset getLargeRegProblem() throws IOException {
		return TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.REGRESSION_ANDROGEN);
	}

	/*
==== the getDataset(false,false) - data set
ACP METRICS:
MAE: 3.689+/-0.542
R^2: 0.663+/-0.121
RMSE: 5.125+/-0.820
CP Accuracy: 0.805+/-0.081
Confidence for given prediction interval width: 0.114+/-0.029
Calibration plot builder
Efficiency plot builder
Mean prediction-interval width: 12.370+/-2.318
Median prediction-interval width: 12.474+/-2.444
SVR METRICS:
MAE: 3.671+/-0.470
R^2: 0.671+/-0.107
RMSE: 5.073+/-0.720

== from getLargeRegProblem()
ACP METRICS:
MAE: 0.665+/-0.057
R^2: 0.147+/-0.439
RMSE: 0.900+/-0.128
CP Accuracy: 0.783+/-0.048
Confidence for given prediction interval width: 0.474+/-0.045
Calibration plot builder
Efficiency plot builder
Mean prediction-interval width: 2.325+/-0.246
Median prediction-interval width: 2.254+/-0.200
SVR METRICS:
MAE: 0.649+/-0.067
R^2: 0.232+/-0.276
RMSE: 0.863+/-0.098

===== After correcting the Bias term
ACP METRICS:
MAE: 0.644+/-0.085
R^2: 0.328+/-0.192
RMSE: 0.835+/-0.133
CP Accuracy: 0.828+/-0.058
Confidence for given prediction interval width: 0.515+/-0.027
Calibration plot builder
Efficiency plot builder
Mean prediction-interval width: 2.299+/-0.154
Median prediction-interval width: 2.244+/-0.190
SVR METRICS:
MAE: 0.624+/-0.071
R^2: 0.357+/-0.166
RMSE: 0.818+/-0.116
	 */
	@Test
	public void testModelVsACP_reg() throws Exception {
		GlobalConfig.getInstance().setRNGSeed(SEED);
		SVR model = new LinearSVR();
		Dataset data = getLargeRegProblem();
		ACPRegressor acp = new ACPRegressor(new NormalizedNCM(model.clone()), new RandomSampling());
		TestRunner tester = new TestRunner.Builder(new KFoldCV()).calcMeanAndStd(true).build();
		List<Metric> acpMetrics = tester.evaluate(data, acp);
		System.err.println("ACP METRICS:");
		for (Metric m : acpMetrics)
			System.err.println(m);
		List<Metric> svrMetrics = tester.evaluateRegressor(data, model);
		System.out.println("SVR METRICS:");
		for(Metric m : svrMetrics)
			System.out.println(m);
	}


//	@Test
//	public void testToString() {
//		System.err.println(getPredictor());
//	}
	/*
	 * ==== Running with Predictor ACP classification with Random sampling with 10 models and 0.2 calibration ratio using transformers:  ID: VANILLA
Init ds: SubSet with 4601 records and 57 features
Applied transformers 0 ms, resulted in: SubSet with 4601 records and 57 features
Finished in 5.396 s with metrics:
Observed Fuzziness : 0.03307205471329625
Observed confidence : 0.9825107282704763
UnobservedCredibility : 0.5156223388736
Calibration plot builder
Proportion single-label prediction sets builder
Proportion multi-label prediction sets builder
	 */
	@Test
	public void testVanillaClassification() throws Exception {
		doTest(getPredictor(), getProb(), Arrays.asList(), "VANILLA");
	}
	
	@Test
	public void testStandardizedClassification() throws Exception {
		doTest(getPredictor(), getProb(), Arrays.asList(new Standardizer()), "SCALED");
	}
	
	/*
	 * ==== Running with Predictor ACP classification with Random sampling with 10 models and 0.2 calibration ratio using transformers: standardize, l1-svc,  ID: SCALED+L1
Init ds: SubSet with 4601 records and 57 features
Applied transformers 335 ms, resulted in: SubSet with 4601 records and 43 features
Finished in 12.919 s with metrics:
Observed Fuzziness : 0.033806672502471165
Observed confidence : 0.9823522856942553
UnobservedCredibility : 0.5186425808398817
Calibration plot builder
Proportion single-label prediction sets builder
Proportion multi-label prediction sets builder
	 */
	@Test
	public void testScaledAndL1Classification() throws Exception {
		doTest(getPredictor(), getProb(), Arrays.asList(new Standardizer(), new L1_SVC_Selector()), "SCALED+L1");
	}
	
	/*
	 * ==== Running with Predictor ACP classification with Random sampling with 10 models and 0.2 calibration ratio using transformers: standardize, l2-svc,  ID: SCALED+L2
Init ds: SubSet with 4601 records and 57 features
Applied transformers 1.464 s, resulted in: SubSet with 4601 records and 20 features
Finished in 15.841 s with metrics:
Observed Fuzziness : 0.03837576858154998
Observed confidence : 0.9799181065292989
UnobservedCredibility : 0.5209224690660185
Calibration plot builder
Proportion single-label prediction sets builder
Proportion multi-label prediction sets builder
	 */
	@Test
	public void testScaledAndL2Classification() throws Exception {
		doTest(getPredictor(), getProb(), Arrays.asList(new Standardizer(), new L2_SVC_Selector()), "SCALED+L2");
	}
	
	/*
	 * ==== Running with Predictor ACP classification with Random sampling with 10 models and 0.2 calibration ratio using transformers: min-max-scale, variance-based-selection,  ID: Min-Max + Variance based
Init ds: SubSet with 4601 records and 57 features
Applied transformers 264 ms, resulted in: SubSet with 4601 records and 57 features
Finished in 14.922 s with metrics:
Observed Fuzziness : 0.032965388392436755
Observed confidence : 0.9826120205027287
UnobservedCredibility : 0.5156846418812948
Calibration plot builder
Proportion single-label prediction sets builder
Proportion multi-label prediction sets builder
	 */
	@Test
	public void testMinMaxVarianceClassification() throws Exception {
		doTest(getPredictor(), getProb(), Arrays.asList(new MinMaxScaler(), new VarianceBasedSelector()), "Min-Max + Variance based");
	}
	
	public static class ObservedVsPredictedCheck implements PointPredictionMetric, SingleValuedMetric {

		public List<Pair<Double,Double>> xAndDiff = new ArrayList<>();
		public List<Pair<Double,Double>> obsAndPred = new ArrayList<>();

		public ObservedVsPredictedCheck clone(){
			return new ObservedVsPredictedCheck();
		}

		@Override
		public int getNumExamples() {
			return xAndDiff.size();
		}

		@Override
		public void clear() {
			xAndDiff.clear();
			obsAndPred.clear();
		}

		@Override
		public boolean goalIsMinimization() {
			return false;
		}

		@Override
		public String getName() {
			return "this";
		}

		@Override
		public double getScore() {
			return 0;
		}

		@Override
		public Map<String, ? extends Object> asMap() {
			return new HashMap<>();
		}

		@Override
		public void addPrediction(double observedLabel, double predictedLabel) {
			xAndDiff.add(Pair.of(observedLabel, predictedLabel-observedLabel));
			obsAndPred.add(Pair.of(observedLabel,predictedLabel));
		}

	}

	// public void doTestReg(ACPRegressor pred, Dataset prob, List<Transformer> trans, String runID) throws Exception {
	// 	System.out.println("\n==== Running with Predictor "+pred + " using transformers: " + getTransList(trans) + " ID: " + runID);
		
	// 	System.out.println("Init ds: " + prob.getDataset());
	// 	Stopwatch sw = new Stopwatch();
	// 	sw.start();
	// 	prob.apply(trans);
	// 	sw.stop();
	// 	System.out.println("Applied transformers " + sw + ", resulted in: " + prob.getDataset());
		
	// 	TestRunner runner = new TestRunner.Builder(new KFoldCV(10,SEED)).calcMeanAndStd(false).build();
		
	// 	List<Metric> metrics = MetricFactory.getRegressorMetrics();
	// 	metrics.add(0,new RegChecker());

	// 	Regressor alg = pred.getICPImplementation().getNCM().getModel().clone();
	// 	List<Metric> mets = runner.evaluateRegressor(prob, alg, metrics);
	// 	System.out.println("Regressor metrics:");
	// 	for (Metric m : mets) {
	// 		System.out.println(m);
	// 	}

		
	// }
	
	public void doTest(Predictor pred, Dataset prob, List<Transformer> trans, String runID) throws Exception {
		System.out.println("\n==== Running with Predictor "+pred + " using transformers: " + getTransList(trans) + " ID: " + runID);
		
		System.out.println("Init ds: " + prob.getDataset());
		Stopwatch sw = new Stopwatch();
		sw.start();
		prob.apply(trans);
		sw.stop();
		System.out.println("Applied transformers " + sw + ", resulted in: " + prob.getDataset());
		
		TestRunner runner = new TestRunner.Builder(new KFoldCV(10,SEED)).build();
		List<Metric> mets = runner.evaluate(prob, pred);
		sw.stop();
		System.out.println("Finished in " + sw + " with metrics:");
		for (Metric m : mets) {
			System.out.println(m);
		}
		System.out.println();

		if (pred instanceof ACPRegressor){
			Regressor alg = ((ACPRegressor) pred).getICPImplementation().getNCM().getModel().clone();
			mets = runner.evaluateRegressor(prob, alg);
			System.out.println("Regressor metrics:");
			for (Metric m : mets) {
				System.out.println(m);
			}
		}else if (pred instanceof ACPClassifier){
			Classifier alg = ((ACPClassifier)pred).getICPImplementation().getNCM().getModel();
			mets = runner.evaluateClassifier(prob, alg);
			System.out.println("Classifier metrics:");
			for (Metric m : mets) {
				System.out.println(m);
			}
		}
		
	}
	
	static String getTransList(List<Transformer> ts) {
		String name = "";
		for (Transformer t : ts) {
			name+=t.getName() + ", ";
		}
		return name;
	}
	
	/*
	 * ==== Running with Predictor ACP regression with Random sampling with 10 models and 0.2 calibration ratio using transformers:  ID: VANILLA
Init ds: SubSet with 1040 records and 306 features
Applied transformers 0 ms, resulted in: SubSet with 1040 records and 306 features
Finished in 2 min 17 s with metrics:
RMSE : 0.8038762719283653
R^2 : 0.5015760535360525
CP regression efficiency plot builder
CP regression calibration plot builder
	 */
	@Test
	public void testVanillaReg() throws Exception {
		doTest(getPredictorReg(), getLargeRegProblem(), new ArrayList<>(), "VANILLA");
	}
	
	/*
	 * ==== Running with Predictor ACP regression with Random sampling with 10 models and 0.2 calibration ratio using transformers: variance-based-selection,  ID: VARIANCE
Init ds: SubSet with 1040 records and 306 features
Applied transformers 713 ms, resulted in: SubSet with 1040 records and 236 features
Finished in 1 min 19 s with metrics:
RMSE : 0.9126228256212029
R^2 : 0.35760354822163687
CP regression efficiency plot builder
CP regression calibration plot builder
	 */
	@Test
	public void testVarianceReg() throws Exception {
		doTest(getPredictorReg(), getLargeRegProblem(), Arrays.asList(new VarianceBasedSelector()), "VARIANCE");
	}
	
	/*
	 * ==== Running with Predictor ACP regression with Random sampling with 10 models and 0.2 calibration ratio using transformers: l2-svr,  ID: L2
Init ds: SubSet with 1040 records and 306 features
Applied transformers 515 ms, resulted in: SubSet with 1040 records and 98 features
Finished in 17.458 s with metrics:
RMSE : 0.7698612056749238
R^2 : 0.5428640765995183
CP regression efficiency plot builder
CP regression calibration plot builder
	 */
	@Test
	public void testL2Reg() throws Exception {
		doTest(getPredictorReg(), getLargeRegProblem(), Arrays.asList(new L2_SVR_Selector()), "L2");
	}
	
	/*

==== Running with Predictor ACP regression with Random sampling with 10 models and 0.2 calibration ratio using transformers: Standardizer,  ID: SCALED
Init ds: Dataset with 688 records and 97 features
Applied transformers 116 ms, resulted in: Dataset with 688 records and 97 features
Finished in 6.804 s with metrics:
MAE: 0.610+/-0.054
R^2: 0.393+/-0.099
RMSE: 0.795+/-0.057
CP Accuracy: 0.829+/-0.037
Confidence for given prediction interval width: 0.518+/-0.020
Calibration plot builder
Efficiency plot builder
Mean prediction-interval width: 2.270+/-0.098
Median prediction-interval width: 2.081+/-0.145

Regressor metrics:
MAE: 0.612+/-0.052
R^2: 0.385+/-0.109
RMSE: 0.800+/-0.060

	 */
	@Test
	public void testStandardizedReg() throws Exception {
		doTest(getPredictorReg(), getLargeRegProblem(), Arrays.asList(new Standardizer()), "SCALED");
	}
	
	/*
	 * 
==== Running with Predictor ACP regression with Random sampling with 10 models and 0.2 calibration ratio using transformers: Standardizer, L2SVRSelector,  ID: SCALED+L2
Init ds: Dataset with 688 records and 97 features
Applied transformers 190 ms, resulted in: Dataset with 688 records and 42 features
Finished in 1.734 s with metrics:
MAE: 0.591+/-0.043
R^2: 0.432+/-0.110
RMSE: 0.765+/-0.065
CP Accuracy: 0.804+/-0.055
Confidence for given prediction interval width: 0.544+/-0.023
Calibration plot builder
Efficiency plot builder
Mean prediction-interval width: 2.040+/-0.140
Median prediction-interval width: 1.938+/-0.117
	 */
	@Test
	public void testScaledAndL2CReg() throws Exception {
		doTest(getPredictorReg(), getLargeRegProblem(), Arrays.asList(new Standardizer(), new L2_SVR_Selector()), "SCALED+L2");
	}
	
	/*
==== Running with Predictor ACP regression with Random sampling with 10 models and 0.2 calibration ratio using transformers: Standardizer, VarianceBasedSelector,  ID: SCALED+variance
Init ds: Dataset with 688 records and 97 features
Applied transformers 152 ms, resulted in: Dataset with 688 records and 87 features
Finished in 7.146 s with metrics:
MAE: 0.605+/-0.082
R^2: 0.411+/-0.128
RMSE: 0.785+/-0.110
CP Accuracy: 0.826+/-0.066
Confidence for given prediction interval width: 0.522+/-0.018
Calibration plot builder
Efficiency plot builder
Mean prediction-interval width: 2.192+/-0.103
Median prediction-interval width: 2.018+/-0.122
	 */
	@Test
	public void testScaledVarianceReg() throws Exception {
		doTest(getPredictorReg(), getLargeRegProblem(), Arrays.asList(new Standardizer(), new VarianceBasedSelector()), "SCALED+variance");
	}
	
	/*
	 * ==== Running with Predictor ACP regression with Random sampling with 10 models and 0.2 calibration ratio using transformers: robust-scale, variance-based-selection,  ID: ROBUST_SCALED+variance
Init ds: SubSet with 1040 records and 306 features
Applied transformers 876 ms, resulted in: SubSet with 1040 records and 236 features
Finished in 1 min 19 s with metrics:
RMSE : 0.7585903554069917
R^2 : 0.5561511348867318
CP regression efficiency plot builder
CP regression calibration plot builder
	 */
	@Test
	public void testRobustScaledVarianceReg() throws Exception {
		doTest(getPredictorReg(), getLargeRegProblem(), Arrays.asList(new RobustScaler(), new VarianceBasedSelector()), "ROBUST_SCALED+variance");
	}
	
	/*
	 * ==== Running with Predictor ACP regression with Random sampling with 10 models and 0.2 calibration ratio using transformers: min-max-scale, variance-based-selection,  ID: Min-Max + Variance based
Init ds: SubSet with 1040 records and 306 features
Applied transformers 815 ms, resulted in: SubSet with 1040 records and 236 features
Finished in 38.763 s with metrics:
RMSE : 0.7895313197657458
R^2 : 0.5192058162243395
CP regression efficiency plot builder
CP regression calibration plot builder
	 */
	@Test
	public void testMinMaxVarianceReg() throws Exception {
		doTest(getPredictorReg(), getLargeRegProblem(), Arrays.asList(new MinMaxScaler(), new VarianceBasedSelector()), "Min-Max + Variance based");
	}
	

}

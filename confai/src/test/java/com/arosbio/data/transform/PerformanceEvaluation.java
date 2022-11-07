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
import java.util.List;

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
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.metrics.Metric;
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
//		SVM alg = new LibSvm(LibSvmParameters.defaultClassification());
		return new ACPClassifier(
				new ICPClassifier(new NegativeDistanceToHyperplaneNCM(alg)), 
				new RandomSampling(10, .2));
	}
	
	static Dataset getProb() throws Exception {
		return TestDataLoader.getInstance().getDataset(true, false);
	}
	
	static ACPRegressor getPredictorReg() {
		SVR alg = new LinearSVR();
//		SVM alg = new LibSvm(LibSvmParameters.defaultRegression());
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
	 * ==== Running with Predictor ACP regression with Random sampling with 10 models and 0.2 calibration ratio using transformers: standardize,  ID: SCALED
Init ds: SubSet with 1040 records and 306 features
Applied transformers 166 ms, resulted in: SubSet with 1040 records and 306 features
Finished in 15 min 24 s with metrics:
RMSE : 247.9813966059762
R^2 : -47429.60706224914
CP regression efficiency plot builder
CP regression calibration plot builder
	 */
	@Test
	public void testStandardizedReg() throws Exception {
		doTest(getPredictorReg(), getLargeRegProblem(), Arrays.asList(new Standardizer()), "SCALED");
	}
	
	/*
	 * ==== Running with Predictor ACP regression with Random sampling with 10 models and 0.2 calibration ratio using transformers: standardize, l2-svr,  ID: SCALED+L2
Init ds: SubSet with 1040 records and 306 features
Applied transformers 704 ms, resulted in: SubSet with 1040 records and 118 features
Finished in 1 min 3 s with metrics:
RMSE : 218.39280593313913
R^2 : -36786.234136209794
CP regression efficiency plot builder
CP regression calibration plot builder
	 */
	@Test
	public void testScaledAndL2CReg() throws Exception {
		doTest(getPredictorReg(), getLargeRegProblem(), Arrays.asList(new Standardizer(), new L2_SVR_Selector()), "SCALED+L2");
	}
	
	/*
	 * ==== Running with Predictor ACP regression with Random sampling with 10 models and 0.2 calibration ratio using transformers: standardize, variance-based-selection,  ID: SCALED+variance
Init ds: SubSet with 1040 records and 306 features
Applied transformers 455 ms, resulted in: SubSet with 1040 records and 236 features
Finished in 12 min 17 s with metrics:
RMSE : 247.78833327976125
R^2 : -47355.78260361923
CP regression efficiency plot builder
CP regression calibration plot builder
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

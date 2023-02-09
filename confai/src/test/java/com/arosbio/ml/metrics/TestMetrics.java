/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import com.arosbio.commons.MathUtils;
import com.arosbio.ml.metrics.classification.BalancedAccuracy;
import com.arosbio.ml.metrics.classification.BinaryBrierScore;
import com.arosbio.ml.metrics.classification.BrierScore;
import com.arosbio.ml.metrics.classification.ClassifierAccuracy;
import com.arosbio.ml.metrics.classification.F1Score;
import com.arosbio.ml.metrics.classification.LogLoss;
import com.arosbio.ml.metrics.classification.NPV;
import com.arosbio.ml.metrics.classification.Precision;
import com.arosbio.ml.metrics.classification.ROC_AUC;
import com.arosbio.ml.metrics.classification.Recall;
import com.arosbio.ml.metrics.cp.CPAccuracy;
import com.arosbio.ml.metrics.cp.CalibrationPlot;
import com.arosbio.ml.metrics.cp.ConfidenceDependentMetric;
import com.arosbio.ml.metrics.cp.EfficiencyPlot;
import com.arosbio.ml.metrics.cp.classification.AverageC;
import com.arosbio.ml.metrics.cp.classification.BalancedObservedFuzziness;
import com.arosbio.ml.metrics.cp.classification.CPClassificationCalibrationPlotBuilder;
import com.arosbio.ml.metrics.cp.classification.MultiLabelPredictionsPlotBuilder;
import com.arosbio.ml.metrics.cp.classification.ObservedFuzziness;
import com.arosbio.ml.metrics.cp.classification.ProportionMultiLabelPredictions;
import com.arosbio.ml.metrics.cp.classification.ProportionSingleLabelPredictions;
import com.arosbio.ml.metrics.cp.classification.SingleLabelPredictionsPlotBuilder;
import com.arosbio.ml.metrics.cp.classification.UnobservedConfidence;
import com.arosbio.ml.metrics.cp.classification.UnobservedCredibility;
import com.arosbio.ml.metrics.cp.regression.CPRegressionCalibrationPlotBuilder;
import com.arosbio.ml.metrics.cp.regression.CPRegressionEfficiencyPlotBuilder;
import com.arosbio.ml.metrics.cp.regression.ConfidenceGivenPredictionIntervalWidth;
import com.arosbio.ml.metrics.cp.regression.MeanPredictionIntervalWidth;
import com.arosbio.ml.metrics.cp.regression.MedianPredictionIntervalWidth;
import com.arosbio.ml.metrics.plots.Plot2D.X_Axis;
import com.arosbio.ml.metrics.regression.MAE;
import com.arosbio.ml.metrics.regression.R2;
import com.arosbio.ml.metrics.regression.RMSE;
import com.arosbio.ml.metrics.vap.MeanVAPInterval;
import com.arosbio.ml.metrics.vap.MedianVAPInterval;
import com.arosbio.ml.metrics.vap.VAPCalibrationPlotBuilder;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.TestUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;

@SuppressWarnings("deprecation")
@Category(UnitTest.class)
@RunWith(Enclosed.class)
public class TestMetrics {

	/**
	 * Probabilities taken from Sklearn doc pages (binary problem)
	 * 1: 90%, 0: 90%, 0: 80%, 1: 60%
	 */
	private static class ProbCase0 {

		// y_true = [1, 0, 0, 1]
		final static List<Integer> y_true = Arrays.asList(1, 0, 0, 1);
		// y_pred = [[.1, .9], [.9, .1], [.8, .2], [.4, .6]]
		final static List<Map<Integer, Double>> y_pred = Arrays.asList(
				toMap(.1, .9),
				toMap(.9, .1),
				toMap(.8, .2),
				toMap(.4, .6));
		final static int n_examples = y_true.size();
		// Calc with sklearn
		final static double logloss = 0.23617255159896325;

	}

	/**
	 * Probabilities taken from stack exchange question (5 classes) - 10 examples
	 * 4: 5%, 4: 1%, 4: 8%, 1: 22%
	 */
	private static class ProbCase1 {

		/*
		 * y_true = [4,4,4,1,4,2,0,1,0,0]
		 */
		static List<Integer> y_true = Arrays.asList(4, 4, 4, 1, 4, 2, 0, 1, 0, 0);
		/*
		 * y_pred = np.array([[0.13, 0.38, 0.4 , 0.04, 0.05],[0.55, 0.06, 0.34, 0.04,
		 * 0.01],[0.3 , 0.35, 0.18, 0.09, 0.08],[0.23, 0.22, 0.04, 0.05, 0.46],[0. ,
		 * 0.16, 0.47, 0.28, 0.09],[0.23, 0.13, 0.34, 0.27, 0.03],[0.32, 0.06, 0.59,
		 * 0.02, 0.01],[0.01, 0.19, 0.02, 0.03, 0.75],[0.27, 0.38, 0.03, 0.12, 0.2
		 * ],[0.17, 0.45, 0.12, 0.25, 0.01]])
		 */
		static List<Map<Integer, Double>> y_pred = Arrays.asList(
				toMap(0.14, 0.38, 0.4, 0.04, 0.05),
				toMap(0.55, 0.05, 0.34, 0.04, 0.01),
				toMap(0.3, 0.35, 0.18, 0.09, 0.08),
				toMap(0.23, 0.22, 0.04, 0.05, 0.46),
				toMap(0., 0.15, 0.47, 0.28, 0.09),
				toMap(0.23, 0.13, 0.34, 0.27, 0.03),
				toMap(0.32, 0.06, 0.59, 0.02, 0.01),
				toMap(0.01, 0.19, 0.01, 0.03, 0.75),
				toMap(0.27, 0.38, 0.03, 0.12, 0.2),
				toMap(0.17, 0.45, 0.11, 0.25, 0.01));

		final static int n_examples = y_true.size();
		// Calc with sklearn
		final static double logloss = 2.1008969758429568;

	}

	private static class ProbCase2 {

		final static List<Integer> y_true = ImmutableList.of(0, 1, 1, 0, 0, 1, 1);
		final static List<Map<Integer, Double>> y_pred = ImmutableList.of(
				toMap(.51, .49),
				toMap(.21, .79),
				toMap(.15, .85),
				toMap(.88, .12),
				toMap(1., 0.),
				toMap(0, 1),
				toMap(.5, .5));

	}

	private static class ProbCase3 {
		// y_pred = [[.9,.1], [.8,.2], [.3,.7],[.01,.99]]
		final static List<Map<Integer, Double>> y_pred = ImmutableList.of(
				toMap(.9, .1),
				toMap(.8, .2),
				toMap(.3, .7),
				toMap(.01, .99));
		// y_true = [0,0,1,1]
		final static List<Integer> y_true = ImmutableList.of(0, 0, 1, 1);
		final static int n_examples = y_pred.size();
		final static double logloss = 0.1738073366910675;
	}

	private final static Map<Integer, Double> toMap(double... vals) {
		Map<Integer, Double> map = new HashMap<>();

		for (int i = 0; i < vals.length; i++) {
			map.put(i, vals[i]);
		}

		return map;
	}

	private static void assertNone(SingleValuedMetric m) {
		Assert.assertTrue(Double.isNaN(m.getScore()));
		Assert.assertEquals(0, m.getNumExamples());
	}

	@Category(UnitTest.class)
	public static class TestFactoryMethods {
		@Test
		public void testMetricFactory() throws Exception {
			Iterator<Metric> mets = MetricFactory.getAllMetrics();

			while (mets.hasNext()) {
				Metric m = mets.next();
				m.clone(); // Make sure this doesn't fail
				m.toString();
				// System.err.println(m);
			}

			Assert.assertTrue(MetricFactory.getACPRegressionMetrics().size() > 5);
			// System.err.println("Reg:");
			// for (Metric m : MetricFactory.getACPRegressionMetrics()) {
			// System.err.println(m);
			// }

			Assert.assertTrue(MetricFactory.getCPClassificationMetrics(true).size() > 5);
			// System.err.println("Class:");
			// for (Metric m : MetricFactory.getCPClassificationMetrics()) {
			// System.err.println(m);
			// }

			Assert.assertTrue(MetricFactory.getAVAPClassificationMetrics().size() > 5);
			// System.err.println("VAP:");
			// for (Metric m : MetricFactory.getAVAPClassificationMetrics()) {
			// System.err.println(m);
			// }

		}

		@Test
		public void listAllMetrics() {
			Iterator<Metric> metrics = MetricFactory.getAllMetrics();
			int count = 0;
			while (metrics.hasNext()) {
				Metric m = metrics.next();
				if (m instanceof SingleValuedMetric) {
					count++;
					// System.err.println(m.getName());
				}
			}
			Assert.assertTrue(count > 10);
		}
	}

	@Category(UnitTest.class)
	public static class TestClassifierMetrics {

		/**
		 * Simple class predictions - (binary), using the following;
		 * 0: correct, 1: correct, 0: correct, 0: correct, 1: incorrect, 0: incorrect
		 * overall 2/3 correctly predicted
		 * 4/6 of class '0' (3 correct, 1 incorrect)
		 * 2/6 of class '1' (1 correct, 1 incorrect)
		 */
		static class Case0 {

			// y_true = [0, 1, 0, 0, 1, 0]
			static List<Integer> y_true = Arrays.asList(0, 1, 0, 0, 1, 0);
			// y_pred = [0, 1, 0, 0, 0, 1]
			static List<Integer> y_pred = Arrays.asList(0, 1, 0, 0, 0, 1);

			final static int n_examples = y_pred.size();
			final static double f1 = .5;
			final static double precision = .5;
			final static double recall = .5;
			final static double npv = 3. / 4;
		}

		/**
		 * Simple class predictions - (4 classes), using the following;
		 * 0: correct, 1: incorrect, 2: incorrect, 3: correct
		 * overall 2/4 correctly predicted
		 */
		static class Case1 {

			// y_true = [0, 1, 2, 3]
			static List<Integer> y_true = Arrays.asList(0, 1, 2, 3);
			// y_pred = [0, 2, 1, 3]
			static List<Integer> y_pred = Arrays.asList(0, 2, 1, 3);

			final static int n_examples = y_pred.size();
		}

		@Test
		public void ClassifierAccuracy() {

			// Case 0
			ClassifierAccuracy acc = new ClassifierAccuracy();
			// None added
			assertNone(acc);
			// Add first
			acc.addPrediction(Case0.y_true.get(0), Case0.y_pred.get(0));
			Assert.assertEquals(1, acc.getNumExamples());
			Assert.assertEquals(1, acc.getScore(), 0.0001);
			// Add rest
			for (int i = 1; i < Case0.y_true.size(); i++) {
				acc.addPrediction(Case0.y_true.get(i), Case0.y_pred.get(i));
			}
			Assert.assertEquals(Case0.n_examples, acc.getNumExamples());
			Assert.assertEquals(2. / 3, acc.getScore(), 0.00001);

			// Clone - no state should be copied here
			ClassifierAccuracy acc2 = acc.clone();
			assertNone(acc2);

			// Should give identical results
			for (int i = 0; i < Case0.y_true.size(); i++) {
				acc2.addPrediction(Case0.y_true.get(i), Case0.y_pred.get(i));
			}
			Assert.assertEquals(acc.getScore(), acc2.getScore(), 0.0001);

			// Batch calculation method should give the same result
			Assert.assertEquals(acc.getScore(), acc.calculate(Case0.y_true, Case0.y_pred), 0.00001);

			// Case 1
			acc.clear();
			assertNone(acc);
			acc.addPrediction(Case1.y_true.get(0), Case1.y_pred.get(0));
			Assert.assertEquals(1, acc.getNumExamples());
			Assert.assertEquals(1, acc.getScore(), 0.0001);
			for (int i = 1; i < Case1.y_true.size(); i++) {
				acc.addPrediction(Case1.y_true.get(i), Case1.y_pred.get(i));
			}
			Assert.assertEquals(Case1.n_examples, acc.getNumExamples());
			Assert.assertEquals(.5, acc.getScore(), 0.00001);
		}

		@Test
		public void BalancedAccuracy() {

			// Case 0 - first
			BalancedAccuracy acc = new BalancedAccuracy();
			assertNone(acc);

			acc.addPrediction(Case0.y_true.get(0), Case0.y_pred.get(0));
			Assert.assertEquals(1, acc.getNumExamples());
			Assert.assertEquals(1, acc.getScore(), 0.0001);

			for (int i = 1; i < Case0.y_true.size(); i++)
				acc.addPrediction(Case0.y_true.get(i), Case0.y_pred.get(i));
			Assert.assertEquals(0.625, acc.getScore(), 0.00001);
			Assert.assertEquals(Case0.n_examples, acc.getNumExamples());

			// Case 0 - second (clone)
			acc = acc.clone();
			assertNone(acc);
			acc.addPrediction(Case0.y_true.get(0), Case0.y_pred.get(0));
			Assert.assertEquals(1, acc.getNumExamples());
			Assert.assertEquals(1, acc.getScore(), 0.0001);

			for (int i = 1; i < Case0.y_true.size(); i++)
				acc.addPrediction(Case0.y_true.get(i), Case0.y_pred.get(i));
			Assert.assertEquals(0.625, acc.getScore(), 0.00001);
			Assert.assertEquals(Case0.n_examples, acc.getNumExamples());

			// Case 0 - third (clear)
			acc.clear();
			assertNone(acc);
			acc.addPrediction(Case0.y_true.get(0), Case0.y_pred.get(0));
			Assert.assertEquals(1, acc.getNumExamples());
			Assert.assertEquals(1, acc.getScore(), 0.0001);

			for (int i = 1; i < Case0.y_true.size(); i++)
				acc.addPrediction(Case0.y_true.get(i), Case0.y_pred.get(i));
			Assert.assertEquals(0.625, acc.getScore(), 0.00001);
			Assert.assertEquals(Case0.n_examples, acc.getNumExamples());

			// Case 1
			acc.clear();
			assertNone(acc);
			acc.addPrediction(Case1.y_true.get(0), Case1.y_pred.get(0));
			Assert.assertEquals(1, acc.getNumExamples());
			Assert.assertEquals(1, acc.getScore(), 0.0001);
			for (int i = 1; i < Case1.y_true.size(); i++) {
				acc.addPrediction(Case1.y_true.get(i), Case1.y_pred.get(i));
			}
			Assert.assertEquals(Case1.n_examples, acc.getNumExamples());
			Assert.assertEquals(.5, acc.getScore(), 0.00001);
		}

		@Test
		public void BinaryBrierScore() {
			BinaryBrierScore b = new BinaryBrierScore();

			// Case 0

			// None added
			assertNone(b);
			// one added
			b.addPrediction(ProbCase0.y_true.get(0), ProbCase0.y_pred.get(0));
			Assert.assertEquals(1, b.getNumExamples());
			// Add rest
			for (int i = 1; i < ProbCase0.y_true.size(); i++) {
				b.addPrediction(ProbCase0.y_true.get(i), ProbCase0.y_pred.get(i));
			}

			Assert.assertEquals(ProbCase0.n_examples, b.getNumExamples());
			Assert.assertEquals(.055, b.getScore(), 0.00001);

			// Case 0 - clone
			BinaryBrierScore b2 = b.clone();
			assertNone(b2);
			// one added
			b2.addPrediction(ProbCase0.y_true.get(0), ProbCase0.y_pred.get(0));
			Assert.assertEquals(1, b2.getNumExamples());
			// Add rest
			for (int i = 1; i < ProbCase0.y_true.size(); i++) {
				b2.addPrediction(ProbCase0.y_true.get(i), ProbCase0.y_pred.get(i));
			}

			Assert.assertEquals(ProbCase0.n_examples, b2.getNumExamples());
			Assert.assertEquals(.055, b2.getScore(), 0.00001);

			// Case 1 - clear
			b.clear();
			assertNone(b);
			// one added
			b.addPrediction(ProbCase0.y_true.get(0), ProbCase0.y_pred.get(0));
			Assert.assertEquals(1, b.getNumExamples());
			// Add rest
			for (int i = 1; i < ProbCase0.y_true.size(); i++) {
				b.addPrediction(ProbCase0.y_true.get(i), ProbCase0.y_pred.get(i));
			}

			Assert.assertEquals(ProbCase0.n_examples, b.getNumExamples());
			Assert.assertEquals(.055, b.getScore(), 0.00001);

		}

		@Test
		public void BrierScore() {
			BrierScore b = new BrierScore();

			// Case 0
			assertNone(b);
			// one added
			b.addPrediction(ProbCase0.y_true.get(0), ProbCase0.y_pred.get(0));
			Assert.assertEquals(1, b.getNumExamples());
			// Add rest
			for (int i = 1; i < ProbCase0.y_true.size(); i++) {
				b.addPrediction(ProbCase0.y_true.get(i), ProbCase0.y_pred.get(i));
			}

			Assert.assertEquals(ProbCase0.n_examples, b.getNumExamples());
			Assert.assertEquals(.055 * 2, b.getScore(), 0.00001);

			// Case 0 - clone
			b = b.clone();
			assertNone(b);
			// one added
			b.addPrediction(ProbCase0.y_true.get(0), ProbCase0.y_pred.get(0));
			Assert.assertEquals(1, b.getNumExamples());
			// Add rest
			for (int i = 1; i < ProbCase0.y_true.size(); i++) {
				b.addPrediction(ProbCase0.y_true.get(i), ProbCase0.y_pred.get(i));
			}

			Assert.assertEquals(ProbCase0.n_examples, b.getNumExamples());
			Assert.assertEquals(.055 * 2, b.getScore(), 0.00001);

			// Case 1 - multiclass, clear
			b.clear();
			assertNone(b);
			// one added
			b.addPrediction(ProbCase1.y_true.get(0), ProbCase1.y_pred.get(0));
			Assert.assertEquals(1, b.getNumExamples());
			// Add rest
			for (int i = 1; i < ProbCase1.y_true.size(); i++) {
				b.addPrediction(ProbCase1.y_true.get(i), ProbCase1.y_pred.get(i));
			}
			Assert.assertEquals(10, b.getNumExamples());
			Assert.assertEquals(1.00688999999, b.getScore(), 0.000001);

		}

		@Test
		public void LogLoss() {
			LogLoss m = new LogLoss();
			assertNone(m);

			// Single value
			m.addPrediction(ProbCase0.y_true.get(0), ProbCase0.y_pred.get(0));
			Assert.assertEquals(-Math.log(.9), m.getScore(), 0.000001);

			// Remaining examples (start at 1)
			for (int i = 1; i < ProbCase0.y_true.size(); i++) {
				m.addPrediction(ProbCase0.y_true.get(i), ProbCase0.y_pred.get(i));
			}

			Assert.assertEquals(ProbCase0.logloss, m.getScore(), 0.000001);
			Assert.assertEquals(ProbCase0.n_examples, m.getNumExamples());

			// Multiclass (clone)
			LogLoss multi = m.clone();
			assertNone(multi);

			for (int i = 0; i < ProbCase1.y_pred.size(); i++) {
				multi.addPrediction(ProbCase1.y_true.get(i), ProbCase1.y_pred.get(i));
			}

			Assert.assertEquals(ProbCase1.n_examples, multi.getNumExamples());
			Assert.assertEquals(ProbCase1.logloss, multi.getScore(), 0.000001);

			// Case 3 (clear)
			m.clear();
			assertNone(m);

			for (int i = 0; i < ProbCase3.n_examples; i++) {
				m.addPrediction(ProbCase3.y_true.get(i), ProbCase3.y_pred.get(i));
			}

			Assert.assertEquals(ProbCase3.n_examples, m.getNumExamples());
			Assert.assertEquals(ProbCase3.logloss, m.getScore(), 0.0001);
		}

		@Test
		public void F1Score() {
			F1Score m = new F1Score();
			assertNone(m);

			// one added
			m.addPrediction(Case0.y_true.get(0), Case0.y_pred.get(0));
			Assert.assertEquals(1, m.getNumExamples());
			// System.err.println(m.getScore());

			// Add rest
			for (int i = 1; i < Case0.n_examples; i++) {
				m.addPrediction(Case0.y_true.get(i), Case0.y_pred.get(i));
			}
			Assert.assertEquals(Case0.n_examples, m.getNumExamples());
			Assert.assertEquals(Case0.f1, m.getScore(), 0.00001);

			// Clone
			F1Score m2 = m.clone();
			assertNone(m2);
			for (int i = 0; i < Case0.n_examples; i++) {
				m2.addPrediction(Case0.y_true.get(i), Case0.y_pred.get(i));
			}
			Assert.assertEquals(Case0.n_examples, m2.getNumExamples());
			Assert.assertEquals(Case0.f1, m2.getScore(), 0.00001);

			// Clear
			m.clear();
			assertNone(m);
			for (int i = 0; i < Case0.n_examples; i++) {
				m.addPrediction(Case0.y_true.get(i), Case0.y_pred.get(i));
			}
			Assert.assertEquals(Case0.n_examples, m.getNumExamples());
			Assert.assertEquals(Case0.f1, m.getScore(), 0.00001);

		}

		@Test
		public void Precision() {
			Precision m = new Precision();
			assertNone(m);

			// one added
			m.addPrediction(Case0.y_true.get(0), Case0.y_pred.get(0));
			Assert.assertEquals(1, m.getNumExamples());

			// Add rest
			for (int i = 1; i < Case0.n_examples; i++) {
				m.addPrediction(Case0.y_true.get(i), Case0.y_pred.get(i));
			}
			Assert.assertEquals(Case0.n_examples, m.getNumExamples());
			Assert.assertEquals(Case0.precision, m.getScore(), 0.00001);

			// Clone
			Precision m2 = m.clone();
			assertNone(m2);
			for (int i = 0; i < Case0.n_examples; i++) {
				m2.addPrediction(Case0.y_true.get(i), Case0.y_pred.get(i));
			}
			Assert.assertEquals(Case0.n_examples, m2.getNumExamples());
			Assert.assertEquals(Case0.precision, m2.getScore(), 0.00001);

			// Clear
			m.clear();
			assertNone(m);
			for (int i = 0; i < Case0.n_examples; i++) {
				m.addPrediction(Case0.y_true.get(i), Case0.y_pred.get(i));
			}
			Assert.assertEquals(Case0.n_examples, m.getNumExamples());
			Assert.assertEquals(Case0.precision, m.getScore(), 0.00001);
		}

		@Test
		public void Recall() {
			Recall m = new Recall();
			assertNone(m);

			// one added
			m.addPrediction(Case0.y_true.get(0), Case0.y_pred.get(0));
			Assert.assertEquals(1, m.getNumExamples());

			// Add rest
			for (int i = 1; i < Case0.n_examples; i++) {
				m.addPrediction(Case0.y_true.get(i), Case0.y_pred.get(i));
			}
			Assert.assertEquals(Case0.n_examples, m.getNumExamples());
			Assert.assertEquals(Case0.recall, m.getScore(), 0.00001);

			// Clone
			Recall m2 = m.clone();
			assertNone(m2);
			for (int i = 0; i < Case0.n_examples; i++) {
				m2.addPrediction(Case0.y_true.get(i), Case0.y_pred.get(i));
			}
			Assert.assertEquals(Case0.n_examples, m2.getNumExamples());
			Assert.assertEquals(Case0.recall, m2.getScore(), 0.00001);

			// Clear
			m.clear();
			assertNone(m);
			for (int i = 0; i < Case0.n_examples; i++) {
				m.addPrediction(Case0.y_true.get(i), Case0.y_pred.get(i));
			}
			Assert.assertEquals(Case0.n_examples, m.getNumExamples());
			Assert.assertEquals(Case0.recall, m.getScore(), 0.00001);
		}

		@Test
		public void NPV() {
			NPV m = new NPV();
			assertNone(m);

			// one added
			m.addPrediction(Case0.y_true.get(0), Case0.y_pred.get(0));
			Assert.assertEquals(1, m.getNumExamples());

			// Add rest
			for (int i = 1; i < Case0.n_examples; i++) {
				m.addPrediction(Case0.y_true.get(i), Case0.y_pred.get(i));
			}
			Assert.assertEquals(Case0.n_examples, m.getNumExamples());
			Assert.assertEquals(Case0.npv, m.getScore(), 0.00001);

			// Clone
			NPV m2 = m.clone();
			assertNone(m2);
			for (int i = 0; i < Case0.n_examples; i++) {
				m2.addPrediction(Case0.y_true.get(i), Case0.y_pred.get(i));
			}
			Assert.assertEquals(Case0.n_examples, m2.getNumExamples());
			Assert.assertEquals(Case0.npv, m2.getScore(), 0.00001);

			// Clear
			m.clear();
			assertNone(m);
			for (int i = 0; i < Case0.n_examples; i++) {
				m.addPrediction(Case0.y_true.get(i), Case0.y_pred.get(i));
			}
			Assert.assertEquals(Case0.n_examples, m.getNumExamples());
			Assert.assertEquals(Case0.npv, m.getScore(), 0.00001);
		}

		private static class ROC_Case0 {
			// y_true = np.array([1, 0, 0, 1, 0, 1, 1, 1, 0, 0, 0, 1, 1, 1, 1, 0, 0, 1, 0,
			// 1, 0, 1, 1, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 1, 0, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0,
			// 0, 0, 1, 1, 1, 0, 1, 1, 0, 0, 1, 0, 1, 1, 1, 0, 1])
			final static List<Integer> y_true = Arrays.asList(1, 0, 0, 1, 0, 1, 1, 1, 0, 0, 0, 1, 1, 1, 1, 0, 0, 1, 0,
					1, 0, 1, 1, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 1, 0, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 1, 1, 0, 1,
					1, 0, 0, 1, 0, 1, 1, 1, 0, 1);
			// y_pred =
			// np.array([[0.372137163,0.627862837],[0.866020118,0.133979882],[0.768858328,0.231141672],[0.44033859500000005,0.559661405],[0.09839913899999997,0.901600861],[0.582940951,0.417059049],[0.42063864399999995,0.579361356],[0.10455362899999998,0.895446371],[0.7058989259999999,0.294101074],[0.34250438699999997,0.657495613],[0.873887257,0.126112743],[0.035764244,0.964235756],[0.39459714199999996,0.605402858],[0.747419593,0.252580407],[0.025259381000000025,0.974740619],[0.688915992,0.311084008],[0.695684914,0.304315086],[0.38049318600000004,0.619506814],[0.851157742,0.148842258],[0.19248862099999997,0.807511379],[0.902676132,0.097323868],[0.221980458,0.778019542],[0.16349469000000005,0.83650531],[0.426119241,0.573880759],[0.75843452,0.24156548],[0.56389828,0.43610172],[0.46436888499999995,0.535631115],[0.462813299,0.537186701],[0.7104765909999999,0.289523409],[0.32905522499999995,0.670944775],[0.592941684,0.407058316],[0.7678232700000001,0.23217673],[0.9818314,0.0181686],[0.5856773049999999,0.414322695],[0.44436388699999996,0.555636113],[0.07974601199999998,0.920253988],[0.630606639,0.369393361],[0.711185739,0.288814261],[0.266849259,0.733150741],[0.2116154,0.7883846],[0.30516479399999996,0.694835206],[0.369160425,0.630839575],[0.751232829,0.248767171],[0.45206771,0.54793229],[0.914818967,0.085181033],[0.8504873550000001,0.149512645],[0.799053884,0.200946116],[0.661619958,0.338380042],[0.760341892,0.239658108],[0.43565032699999995,0.564349673],[0.971829593,0.028170407],[0.03379734199999995,0.966202658],[0.36420342699999997,0.635796573],[0.68005895,0.31994105],[0.759925573,0.240074427],[0.096356879,0.903643121],[0.24027389300000002,0.759726107],[0.19828273799999996,0.801717262],[0.398306583,0.601693417],[0.682978756,0.317021244],[0.945883557,0.054116443],[0.719159773,0.280840227]])
			final static List<Map<Integer, Double>> y_pred = Arrays.asList(toMap(0.372137163, 0.627862837),
					toMap(0.866020118, 0.133979882), toMap(0.768858328, 0.231141672),
					toMap(0.44033859500000005, 0.559661405), toMap(0.09839913899999997, 0.901600861),
					toMap(0.582940951, 0.417059049), toMap(0.42063864399999995, 0.579361356),
					toMap(0.10455362899999998, 0.895446371), toMap(0.7058989259999999, 0.294101074),
					toMap(0.34250438699999997, 0.657495613), toMap(0.873887257, 0.126112743),
					toMap(0.035764244, 0.964235756), toMap(0.39459714199999996, 0.605402858),
					toMap(0.747419593, 0.252580407), toMap(0.025259381000000025, 0.974740619),
					toMap(0.688915992, 0.311084008), toMap(0.695684914, 0.304315086),
					toMap(0.38049318600000004, 0.619506814), toMap(0.851157742, 0.148842258),
					toMap(0.19248862099999997, 0.807511379), toMap(0.902676132, 0.097323868),
					toMap(0.221980458, 0.778019542), toMap(0.16349469000000005, 0.83650531),
					toMap(0.426119241, 0.573880759), toMap(0.75843452, 0.24156548), toMap(0.56389828, 0.43610172),
					toMap(0.46436888499999995, 0.535631115), toMap(0.462813299, 0.537186701),
					toMap(0.7104765909999999, 0.289523409), toMap(0.32905522499999995, 0.670944775),
					toMap(0.592941684, 0.407058316), toMap(0.7678232700000001, 0.23217673), toMap(0.9818314, 0.0181686),
					toMap(0.5856773049999999, 0.414322695), toMap(0.44436388699999996, 0.555636113),
					toMap(0.07974601199999998, 0.920253988), toMap(0.630606639, 0.369393361),
					toMap(0.711185739, 0.288814261), toMap(0.266849259, 0.733150741), toMap(0.2116154, 0.7883846),
					toMap(0.30516479399999996, 0.694835206), toMap(0.369160425, 0.630839575),
					toMap(0.751232829, 0.248767171), toMap(0.45206771, 0.54793229), toMap(0.914818967, 0.085181033),
					toMap(0.8504873550000001, 0.149512645), toMap(0.799053884, 0.200946116),
					toMap(0.661619958, 0.338380042), toMap(0.760341892, 0.239658108),
					toMap(0.43565032699999995, 0.564349673), toMap(0.971829593, 0.028170407),
					toMap(0.03379734199999995, 0.966202658), toMap(0.36420342699999997, 0.635796573),
					toMap(0.68005895, 0.31994105), toMap(0.759925573, 0.240074427), toMap(0.096356879, 0.903643121),
					toMap(0.24027389300000002, 0.759726107), toMap(0.19828273799999996, 0.801717262),
					toMap(0.398306583, 0.601693417), toMap(0.682978756, 0.317021244), toMap(0.945883557, 0.054116443),
					toMap(0.719159773, 0.280840227)); 

			// from sklearn.metrics import roc_auc_score
			// roc_auc_score(y_true[:5],y_pred[:5,1])
			final static double auc_first5 = 0.6666666666666667;
			// roc_auc_score(y_true[:20],y_pred[:20,1])
			final static double auc_first20 = 0.8080808080808081;
			// roc_auc_score(y_true,y_pred[:,1])
			final static double auc_all = 0.7925925925925926;
		}

		@Test
		public void ROC_AUC() {
			// Toy example from scikit-learn roc_curve page
			List<Integer> trueValues = Arrays.asList(1,1,2,2);
			List<Double> scores = Arrays.asList(0.1, 0.4, 0.35, 0.8);
			ROC_AUC.ComputedROC roc = ROC_AUC.calculateCurves(trueValues,scores, 2);
			Assert.assertEquals(.75, roc.auc(), .00001);
			TestUtils.assertEquals(Arrays.asList(0. , 0. , 0.5, 0.5, 1.), roc.getROC().getPoints(ROC_AUC.FALSE_POSITIVE_RATE));
			TestUtils.assertEquals(Arrays.asList(0. , 0.5, 0.5, 1. , 1.), roc.getROC().getPoints(ROC_AUC.TRUE_POSITIVE_RATE));
			TestUtils.assertEquals(Arrays.asList(1.8 , 0.8 , 0.4 , 0.35, 0.1), roc.getROC().getPoints(ROC_AUC.SCORE));


			ROC_AUC m = new ROC_AUC();
			assertNone(m);

			// First 5
			int i = 0;
			for (; i < 5; i++) {
				m.addPrediction(ROC_Case0.y_true.get(i), ROC_Case0.y_pred.get(i));
			}
			Assert.assertEquals(5, m.getNumExamples());
			Assert.assertEquals(ROC_Case0.auc_first5, m.getScore(), 0.01);
			// First 20
			for (; i < 20; i++) {
				m.addPrediction(ROC_Case0.y_true.get(i), ROC_Case0.y_pred.get(i));
			}
			Assert.assertEquals(20, m.getNumExamples());
			Assert.assertEquals(ROC_Case0.auc_first20, m.getScore(), 0.01);

			// All

			for (; i < ROC_Case0.y_true.size(); i++) {
				m.addPrediction(ROC_Case0.y_true.get(i), ROC_Case0.y_pred.get(i));
			}
			Assert.assertEquals(ROC_Case0.y_true.size(), m.getNumExamples());
			Assert.assertEquals(ROC_Case0.auc_all, m.getScore(), 0.01);

			// Clone
			ROC_AUC clone = m.clone();
			assertNone(clone);
			for (i = 0; i < ROC_Case0.y_true.size(); i++) {
				clone.addPrediction(ROC_Case0.y_true.get(i), ROC_Case0.y_pred.get(i));
			}
			Assert.assertEquals(ROC_Case0.y_true.size(), clone.getNumExamples());
			Assert.assertEquals(ROC_Case0.auc_all, clone.getScore(), 0.01);

			// Clear
			m.clear();
			assertNone(m);
			for (i = 0; i < ROC_Case0.y_true.size(); i++) {
				m.addPrediction(ROC_Case0.y_true.get(i), ROC_Case0.y_pred.get(i));
			}
			Assert.assertEquals(ROC_Case0.y_true.size(), m.getNumExamples());
			Assert.assertEquals(ROC_Case0.auc_all, m.getScore(), 0.01);

		}

	}

	@Category(UnitTest.class)
	public static class TestRegressionMetrics {

		/**
		 * Regression example (true:pred)
		 * 0: (3:2.5), 1: (-.5:0)
		 */
		static class Reg0 {
			// y_true = [3, -0.5, 2, 7]
			final static List<Double> y_true = Arrays.asList(3d, -0.5, 2d, 7d);
			// y_pred = [2.5, 0.0, 2, 8]
			final static List<Double> y_pred = Arrays.asList(2.5, 0.0, 2d, 8d);
			final static int n_examples = y_pred.size();
			final static double mea = 0.5;
			final static double r2 = 0.9486081370449679;
			// math.sqrt( (.5**2 + .5**2 + 0 + 1)/4 )
			final static double rmse = 0.6123724356957945;
		}

		static class Reg1 {
			// y_true = [0., 0.1, 5.3, 4.2, 6.5]
			final static List<Double> y_true = Arrays.asList(0., 0.1, 5.3, 4.2, 6.5);
			// y_pred = [0.5, .15, 6.7,3.3,7.1]
			final static List<Double> y_pred = Arrays.asList(0.5, .15, 6.7, 3.3, 7.1);
			final static int n_examples = y_true.size();

			final static double mae = 0.6900000000000001;
			final static double r2 = 0.9064263583047472;
			// math.sqrt((.5**2+.05**2+1.4**2+.9**2+.6**2)/5)
			final static double rmse = 0.8224962005991274;

		}

		@Test
		public void MAE() {
			MAE m = new MAE();
			assertNone(m);

			// Add first
			m.addPrediction(Reg0.y_true.get(0), Reg0.y_pred.get(0));
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(Math.abs(Reg0.y_true.get(0) - Reg0.y_pred.get(0)), m.getScore(), 0.000001);
			// Add rest
			for (int i = 1; i < Reg0.n_examples; i++) {
				m.addPrediction(Reg0.y_true.get(i), Reg0.y_pred.get(i));
			}

			Assert.assertEquals(Reg0.n_examples, m.getNumExamples());
			Assert.assertEquals(Reg0.mea, m.getScore(), 0.000001);

			// Clear
			m.clear();
			assertNone(m);
			// All correct scores - MAE should be 0
			m.addPrediction(0.0, 0.0);
			m.addPrediction(5.0, 5.0);
			m.addPrediction(7.0, 7.0);
			m.addPrediction(9.9, 9.9);
			Assert.assertEquals(0, m.getScore(), 0.000001);
			Assert.assertEquals(4, m.getNumExamples());

			// Clone
			m = m.clone();
			assertNone(m);
			for (int i = 0; i < Reg1.n_examples; i++) {
				m.addPrediction(Reg1.y_true.get(i), Reg1.y_pred.get(i));
			}
			Assert.assertEquals(Reg1.n_examples, m.getNumExamples());
			Assert.assertEquals(Reg1.mae, m.getScore(), 0.000001);

		}

		@Test
		public void R2() {
			R2 m = new R2();
			assertNone(m);

			// All correct scores - should be r2 = 1
			m.addPrediction(0.0, 0.0);
			m.addPrediction(5.0, 5.0);
			m.addPrediction(7.0, 7.0);
			m.addPrediction(9.9, 9.9);

			Assert.assertEquals(1.0, m.getScore(), 0.000001);
			Assert.assertEquals(4, m.getNumExamples());

			// Case 0 (clone)
			m = m.clone();
			assertNone(m);
			for (int i = 0; i < Reg0.n_examples; i++) {
				m.addPrediction(Reg0.y_true.get(i), Reg0.y_pred.get(i));
			}
			Assert.assertEquals(Reg0.n_examples, m.getNumExamples());
			Assert.assertEquals(Reg0.r2, m.getScore(), 0.000001);

			// Case 1 (clear)
			m.clear();
			assertNone(m);
			for (int i = 0; i < Reg1.n_examples; i++) {
				m.addPrediction(Reg1.y_true.get(i), Reg1.y_pred.get(i));
			}
			Assert.assertEquals(Reg1.n_examples, m.getNumExamples());
			Assert.assertEquals(Reg1.r2, m.getScore(), 0.000001);

		}

		@Test
		public void RMSE() {
			RMSE m = new RMSE();
			assertNone(m);

			m.addPrediction(1.05, 1.125);
			m.addPrediction(2.1, 2.04);
			m.addPrediction(3.15, 3.225);
			m.addPrediction(4.2, 4.14);
			m.addPrediction(5.25, 5.325);
			m.addPrediction(6.3, 6.24);
			m.addPrediction(7.35, 7.425);
			m.addPrediction(8.4, 8.34);
			m.addPrediction(9.45, 9.525);
			m.addPrediction(10.5, 10.44);
			m.addPrediction(11.55, 11.625);

			Assert.assertEquals(0.068589689, m.getScore(), 0.0001);
			Assert.assertEquals(11, m.getNumExamples());

			// Clone
			m = m.clone();
			assertNone(m);

			// Add first
			m.addPrediction(Reg0.y_true.get(0), Reg0.y_pred.get(0));
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(.5, m.getScore(), 0.000001);
			// Add rest
			for (int i = 1; i < Reg0.n_examples; i++) {
				m.addPrediction(Reg0.y_true.get(i), Reg0.y_pred.get(i));
			}

			Assert.assertEquals(Reg0.n_examples, m.getNumExamples());
			Assert.assertEquals(Reg0.rmse, m.getScore(), 0.000001);

			// Clear
			m.clear();
			assertNone(m);
			// All correct scores - RMSE should be 0
			m.addPrediction(0.0, 0.0);
			m.addPrediction(5.0, 5.0);
			m.addPrediction(7.0, 7.0);
			m.addPrediction(9.9, 9.9);
			Assert.assertEquals(0, m.getScore(), 0.000001);
			Assert.assertEquals(4, m.getNumExamples());

			// Clone
			m = m.clone();
			assertNone(m);
			for (int i = 0; i < Reg1.n_examples; i++) {
				m.addPrediction(Reg1.y_true.get(i), Reg1.y_pred.get(i));
			}
			Assert.assertEquals(Reg1.n_examples, m.getNumExamples());
			Assert.assertEquals(Reg1.rmse, m.getScore(), 0.000001);

		}

	}

	@Category(UnitTest.class)
	public static class TestCPMetrics {

		@Test
		public void CPAccuracy_regression() {
			CPAccuracy m = new CPAccuracy();
			assertNone(m);

			m.setConfidence(.77);
			Assert.assertEquals(.77, m.getConfidence(),0.00001);

			// First correct
			m.addPrediction(1., Range.closed(.9, 1.1));
			Assert.assertEquals(1., m.getScore(),0.00001);
			Assert.assertEquals(1, m.getNumExamples());
			
			// Second incorrect
			m.addPrediction(2., Range.closed(.9, 1.1));
			Assert.assertEquals(.5, m.getScore(),0.00001);
			Assert.assertEquals(2, m.getNumExamples());

			// Third correct
			m.addPrediction(1.09, Range.closed(.9, 1.1));
			Assert.assertEquals(2./3, m.getScore(),0.00001);
			Assert.assertEquals(3, m.getNumExamples());

			// Forth incorrect
			m.addPrediction(10., Range.closed(7.9, 9.1));
			Assert.assertEquals(.5, m.getScore(),0.00001);
			Assert.assertEquals(4, m.getNumExamples());

			// CLONE
			CPAccuracy clone = m.clone();
			assertNone(clone);
			clone.addPrediction(1., Range.closed(.9, 1.1));
			clone.addPrediction(2., Range.closed(.9, 1.1));
			clone.addPrediction(1.09, Range.closed(.9, 1.1));
			clone.addPrediction(10., Range.closed(7.9, 9.1));
			Assert.assertEquals(.5, clone.getScore(),0.00001);

			// Clear
			m.clear();
			assertNone(m);
			m.addPrediction(1., Range.closed(.9, 1.1));
			m.addPrediction(2., Range.closed(.9, 1.1));
			m.addPrediction(1.09, Range.closed(.9, 1.1));
			m.addPrediction(10., Range.closed(7.9, 9.1));
			Assert.assertEquals(.5, m.getScore(),0.00001);
		}

		@Test
		public void CPAccuracy_clf() {
			CPAccuracy m = new CPAccuracy();
			assertNone(m);

			m.setConfidence(.88);
			Assert.assertEquals(.88, m.getConfidence(),0.00001);

			// First correct
			m.addPrediction(0, toMap(.22, .1));
			Assert.assertEquals(1., m.getScore(),0.00001);
			Assert.assertEquals(1, m.getNumExamples());
			
			// Second incorrect
			m.addPrediction(1, toMap(.1,.1));
			Assert.assertEquals(.5, m.getScore(),0.00001);
			Assert.assertEquals(2, m.getNumExamples());

			// Third correct
			m.addPrediction(1, toMap(.05,.7));
			Assert.assertEquals(2./3, m.getScore(),0.00001);
			Assert.assertEquals(3, m.getNumExamples());

			// Forth incorrect
			m.addPrediction(0, toMap(.08,.5));
			Assert.assertEquals(.5, m.getScore(),0.00001);
			Assert.assertEquals(4, m.getNumExamples());

			// CLONE
			CPAccuracy clone = m.clone();
			assertNone(clone);
			clone.addPrediction(0, toMap(.22, .1));
			clone.addPrediction(1, toMap(.1,.1));
			clone.addPrediction(1, toMap(.05,.7));
			clone.addPrediction(0, toMap(.08,.5));
			Assert.assertEquals(.5, clone.getScore(),0.00001);

			// Clear
			m.clear();
			assertNone(m);
			m.addPrediction(0, toMap(.22, .1));
			m.addPrediction(1, toMap(.1,.1));
			m.addPrediction(1, toMap(.05,.7));
			m.addPrediction(0, toMap(.08,.5));
			Assert.assertEquals(.5, m.getScore(),0.00001);
		}
			
			

	}

	@Category(UnitTest.class)
	public static class TestCPClassifierMetrics {

		/**
		 * p-values for 3 examples of 2 classes
		 * 0: (.87, .15), 1: (.65,.94), 1: (.07,.34)
		 */
		private static class Pvalues {

			final static List<Integer> y_true = ImmutableList.of(0, 1, 1);

			final static List<Map<Integer, Double>> y_pred = ImmutableList.of(
					toMap(.87, .15),
					toMap(.65, .94),
					toMap(.07, .34));
			/**
			 * Number of examples in {@code p_pred}
			 */
			final static int n_examples = y_pred.size();

		}

		/**
		 * 3 classes
		 * 1: (.65,.94,.5), 0: (.05,.1,.04), 2: (.3,.3,.32)
		 */
		private static class Pvalues3class {

			final static List<Integer> y_true = ImmutableList.of(1, 0, 2);
			final static List<Map<Integer, Double>> y_pred = ImmutableList.of(
					toMap(.65, .94, .5),
					toMap(.05, .1, .04),
					toMap(.3, .3, .32));
			final static int n_examples = y_pred.size();

		}

		@Test
		public void AverageC(){
			AverageC m = new AverageC();
			assertNone(m);
			Assert.assertEquals(ConfidenceDependentMetric.DEFAULT_CONFIDENCE, m.getConfidence(), 0.0001);

			// -------------------
			// The 2 class problem
			// -------------------

			// 1
			m.addPrediction(0, Pvalues.y_pred.get(0));
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(1, m.getScore(), 0.001);
			// 2
			m.addPrediction(0, Pvalues.y_pred.get(1));
			Assert.assertEquals(2, m.getNumExamples());
			Assert.assertEquals(1.5, m.getScore(), 0.001);
			// 3
			m.addPrediction(0, Pvalues.y_pred.get(2));
			Assert.assertEquals(3, m.getNumExamples());
			Assert.assertEquals(4./3, m.getScore(), 0.001);

			// Clone
			AverageC m2 = m.clone();
			assertNone(m2);
			m2.setConfidence(.9);

			// -------------------
			// The 3 class problem
			// -------------------

			// 1
			m2.addPrediction(0, Pvalues3class.y_pred.get(0));
			Assert.assertEquals(1, m2.getNumExamples());
			Assert.assertEquals(3, m2.getScore(), 0.001);
			// 2
			m2.addPrediction(0, Pvalues3class.y_pred.get(1));
			Assert.assertEquals(2, m2.getNumExamples());
			Assert.assertEquals(2., m2.getScore(), 0.001);
			// 3
			m2.addPrediction(0, Pvalues3class.y_pred.get(2));
			Assert.assertEquals(3, m2.getNumExamples());
			Assert.assertEquals(7./3, m2.getScore(), 0.001);
		}

		@Test
		public void ObservedFuzziness() {
			ObservedFuzziness m = new ObservedFuzziness();
			assertNone(m);

			// Single prediction - 2 labels
			m.addPrediction(Pvalues.y_true.get(0), Pvalues.y_pred.get(0));
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(.15, m.getScore(), 0.001);

			// Two predictions - 2 labels
			m.addPrediction(Pvalues.y_true.get(1), Pvalues.y_pred.get(1));
			Assert.assertEquals(2, m.getNumExamples());
			Assert.assertEquals(MathUtils.mean(.15, 0.65), m.getScore(), 0.001);

			// Three
			m.addPrediction(Pvalues.y_true.get(2), Pvalues.y_pred.get(2));
			Assert.assertEquals(3, Pvalues.n_examples);
			Assert.assertEquals(MathUtils.mean(.15, 0.65, .07), m.getScore(), 0.001);

			// Clone
			ObservedFuzziness m2 = m.clone();
			assertNone(m2);
			for (int i = 0; i < Pvalues.n_examples; i++)
				m2.addPrediction(Pvalues.y_true.get(i), Pvalues.y_pred.get(i));
			Assert.assertEquals(Pvalues.n_examples, m2.getNumExamples());
			Assert.assertEquals(m.getScore(), m2.getScore(), 0.000001);

			// 3 labels (clear)
			m.clear();
			assertNone(m);
			m.addPrediction(Pvalues3class.y_true.get(0), Pvalues3class.y_pred.get(0));
			Assert.assertEquals(1, m.getNumExamples());
			double firstScore = m.getScore();
			Assert.assertEquals(.65 + 0.5, firstScore, 0.0001);

			m.addPrediction(Pvalues3class.y_true.get(1), Pvalues3class.y_pred.get(1));
			Assert.assertEquals(2, m.getNumExamples());
			double secondScore = m.getScore();
			Assert.assertEquals(MathUtils.mean(firstScore, .14), secondScore, 0.0001);

			m.addPrediction(Pvalues3class.y_true.get(2), Pvalues3class.y_pred.get(2));
			Assert.assertEquals(3, m.getNumExamples());
			Assert.assertEquals(MathUtils.mean(firstScore, .14, .6), m.getScore(), 0.0001);

		}

		@Test
		public void BalancedObservedFuzziness() {
			BalancedObservedFuzziness m = new BalancedObservedFuzziness();
			assertNone(m);

			// Single prediction (only have a value for one of the classes)
			m.addPrediction(Pvalues.y_true.get(0), Pvalues.y_pred.get(0));
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(.15, m.getScore(), 0.001);

			// Two predictions (have one each - both same weight)
			m.addPrediction(Pvalues.y_true.get(1), Pvalues.y_pred.get(1));
			Assert.assertEquals(2, m.getNumExamples());
			Assert.assertEquals(MathUtils.mean(.15, 0.65), m.getScore(), 0.001);

			// Three predictions (weight will matter!)
			m.addPrediction(Pvalues.y_true.get(2), Pvalues.y_pred.get(2));
			Assert.assertEquals(3, m.getNumExamples());
			double ofCls0 = .15;
			double ofCls1 = MathUtils.mean(.65, .07);
			double balancedOF = MathUtils.mean(ofCls0, ofCls1);
			Assert.assertEquals(balancedOF, m.getScore(), 0.001);

			// 3 labels (clone)
			BalancedObservedFuzziness m2 = m.clone();
			assertNone(m2);
			m2.addPrediction(Pvalues3class.y_true.get(0), Pvalues3class.y_pred.get(0));
			Assert.assertEquals(1, m2.getNumExamples());
			Assert.assertEquals(.65 + 0.5, m2.getScore(), 0.001);

		}

		@Test
		public void UnobservedConfidence() {
			UnobservedConfidence b = new UnobservedConfidence();
			assertNone(b);

			// Single prediction
			b.addPrediction(1, Pvalues.y_pred.get(0));
			Assert.assertEquals(1, b.getNumExamples());
			Assert.assertEquals(1 - 0.15, b.getScore(), 0.001);

			// Two predictions
			b.addPrediction(1, Pvalues.y_pred.get(1));
			Assert.assertEquals(2, b.getNumExamples());
			Assert.assertEquals(MathUtils.mean(1 - 0.15, 1 - 0.65), b.getScore(), 0.0001);

			// 3 labels
			UnobservedConfidence m2 = b.clone();
			assertNone(m2);
			m2.addPrediction(Pvalues3class.y_true.get(0), Pvalues3class.y_pred.get(0));
			Assert.assertEquals(1, m2.getNumExamples());
			Assert.assertEquals(1 - 0.65, m2.getScore(), 0.00001);
		}

		@Test
		public void UnobservedCredibility() {
			UnobservedCredibility m = new UnobservedCredibility();
			assertNone(m);
			// Single pred
			// Single prediction
			m.addPrediction(1, Pvalues.y_pred.get(0));
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(0.87, m.getScore(), 0.001);

			// Two predictions
			m.addPrediction(1, Pvalues.y_pred.get(1));
			Assert.assertEquals(2, m.getNumExamples());
			Assert.assertEquals(MathUtils.mean(0.87, 0.94), m.getScore(), 0.001);

			// 3 labels
			m = m.clone();
			assertNone(m);
			m.addPrediction(Pvalues3class.y_true.get(0), Pvalues3class.y_pred.get(0));
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(0.94, m.getScore(), 0.001);
		}

		@Test
		public void ProportionMultiLabelPredictions() {
			ProportionMultiLabelPredictions m = new ProportionMultiLabelPredictions();
			Assert.assertEquals(ConfidenceDependentMetric.DEFAULT_CONFIDENCE, m.getConfidence(), 0.00001);
			assertNone(m);

			// Add one
			m.addPrediction(Pvalues.y_true.get(0), Pvalues.y_pred.get(0));
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(0, m.getScore(), 0.00001);

			try {
				m.setConfidence(.9);
				Assert.fail("should not be able to change conf once added test-results");
			} catch (IllegalStateException e) {
			}

			// Add the rest
			for (int i = 1; i < Pvalues.n_examples; i++) {
				m.addPrediction(Pvalues.y_true.get(i), Pvalues.y_pred.get(i));
			}

			Assert.assertEquals(Pvalues.n_examples, m.getNumExamples());
			Assert.assertEquals(1. / 3, m.getScore(), 0.0000001);

			// Clone
			m = m.clone();
			assertNone(m);
			// Add all
			for (int i = 0; i < Pvalues.n_examples; i++) {
				m.addPrediction(Pvalues.y_true.get(i), Pvalues.y_pred.get(i));
			}
			Assert.assertEquals(Pvalues.n_examples, m.getNumExamples());
			Assert.assertEquals(1. / 3, m.getScore(), 0.0000001);

			// Clear (3 class)
			m.clear();
			assertNone(m);

			m.setConfidence(0.69);
			m.addPrediction(Pvalues3class.y_true.get(0), Pvalues3class.y_pred.get(0));
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(1, m.getScore(), 0.00001);
			// Add the rest
			for (int i = 1; i < Pvalues3class.n_examples; i++) {
				m.addPrediction(Pvalues3class.y_true.get(i), Pvalues3class.y_pred.get(i));
			}

			Assert.assertEquals(Pvalues3class.n_examples, m.getNumExamples());
			Assert.assertEquals(1. / 3, m.getScore(), 0.0000001);
		}

		@Test
		public void ProportionSingleLabelPredictions() {
			ProportionSingleLabelPredictions m = new ProportionSingleLabelPredictions();
			Assert.assertEquals(ConfidenceDependentMetric.DEFAULT_CONFIDENCE, m.getConfidence(), 0.00001);
			assertNone(m);

			// Add one
			m.addPrediction(Pvalues.y_true.get(0), Pvalues.y_pred.get(0));
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(1, m.getScore(), 0.00001);

			try {
				m.setConfidence(.9);
				Assert.fail("should not be able to change conf once added test-results");
			} catch (IllegalStateException e) {
			}

			// Add the rest
			for (int i = 1; i < Pvalues.n_examples; i++) {
				m.addPrediction(Pvalues.y_true.get(i), Pvalues.y_pred.get(i));
			}

			Assert.assertEquals(Pvalues.n_examples, m.getNumExamples());
			Assert.assertEquals(2. / 3, m.getScore(), 0.0000001);

			// Clone
			m = m.clone();
			assertNone(m);
			// Add all
			for (int i = 0; i < Pvalues.n_examples; i++) {
				m.addPrediction(Pvalues.y_true.get(i), Pvalues.y_pred.get(i));
			}
			Assert.assertEquals(Pvalues.n_examples, m.getNumExamples());
			Assert.assertEquals(2. / 3, m.getScore(), 0.0000001);

			// Clear (3 class)
			m.clear();
			assertNone(m);

			m.setConfidence(0.69);
			m.addPrediction(Pvalues3class.y_true.get(0), Pvalues3class.y_pred.get(0));
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(0, m.getScore(), 0.00001);
			// Add the rest
			for (int i = 1; i < Pvalues3class.n_examples; i++) {
				m.addPrediction(Pvalues3class.y_true.get(i), Pvalues3class.y_pred.get(i));
			}

			Assert.assertEquals(Pvalues3class.n_examples, m.getNumExamples());
			Assert.assertEquals(1. / 3, m.getScore(), 0.0000001); // one multi, one single, one empty
		}

		@Test
		public void CPClassificationCalibrationPlotBuilder() {
			List<Double> confs = Arrays.asList(0.6, 0.7, 0.8, 0.9);
			CPClassificationCalibrationPlotBuilder b = new CPClassificationCalibrationPlotBuilder(
					confs);

			Assert.assertEquals(confs, b.getEvaluationPoints());

			try {
				b.buildPlot();
				Assert.fail("Cannot create calibration plot when no examples added");
			} catch (IllegalStateException e) {
			}

			// First prediction
			b.addPrediction(Pvalues.y_true.get(0), Pvalues.y_pred.get(0));
			Assert.assertEquals(1, b.getNumExamples());
			CalibrationPlot cp = b.buildPlot();
			Assert.assertEquals(confs, cp.getXvalues());
			Assert.assertEquals(1, cp.getNumExamples());
			Assert.assertEquals(1, cp.getAccuracy(0.9), 0.0001);
			Assert.assertEquals(1, cp.getAccuracy(0.8), 0.0001);
			Assert.assertEquals(1, cp.getAccuracy(0.7), 0.0001);
			Assert.assertEquals(1, cp.getAccuracy(0.6), 0.0001);
			Assert.assertEquals(ImmutableSet.of(X_Axis.CONFIDENCE.label(), cp.getAccuracyLabel(),
					cp.getAccuracyLabel() + "(" + Pvalues.y_true.get(0) + ")"), cp.getCurves().keySet());
			Assert.assertEquals(
					ImmutableSet.of(cp.getAccuracyLabel(), cp.getAccuracyLabel() + "(" + Pvalues.y_true.get(0) + ")"),
					cp.getYlabels());

			// Second prediction
			b.addPrediction(Pvalues.y_true.get(1), Pvalues.y_pred.get(1));
			Assert.assertEquals(2, b.getNumExamples());
			Assert.assertEquals(Arrays.asList(0.6, 0.7, 0.8, 0.9), b.getEvaluationPoints());
			cp = b.buildPlot();
			Assert.assertEquals(2, cp.getNumExamples());
			Assert.assertEquals(1, cp.getAccuracy(0.9), 0.0001);
			Assert.assertEquals(1, cp.getAccuracy(0.8), 0.0001);
			Assert.assertEquals(1, cp.getAccuracy(0.7), 0.0001);
			Assert.assertEquals(1, cp.getAccuracy(0.6), 0.0001); // 0.5 accuracy one found one empty set

			// Third
			b.addPrediction(Pvalues.y_true.get(2), Pvalues.y_pred.get(2)); // 1=.07 2=.34
			Assert.assertEquals(3, b.getNumExamples());
			cp = b.buildPlot();
			Assert.assertEquals(1, cp.getAccuracy(0.9), 0.0001);
			Assert.assertEquals(1, cp.getAccuracy(0.8), 0.0001);
			Assert.assertEquals(1, cp.getAccuracy(0.7), 0.0001);
			Assert.assertEquals(2. / 3, cp.getAccuracy(0.6), 0.0001); // first one that is miss-classified

			Assert.assertEquals(
					ImmutableSet.of(X_Axis.CONFIDENCE.label(), cp.getAccuracyLabel(), cp.getAccuracyLabel() + "(0)",
							cp.getAccuracyLabel() + "(1)"),
					cp.getCurves().keySet());
			Assert.assertEquals(ImmutableSet.of(cp.getAccuracyLabel(), cp.getAccuracyLabel() + "(0)",
					cp.getAccuracyLabel() + "(1)"), cp.getYlabels());

			Assert.assertEquals(confs, b.clone().getEvaluationPoints());
		}

		@Test
		public void MultiLabelPredictionsPlotBuilder() {
			List<Double> confs = Arrays.asList(0.6, 0.7, 0.8, 0.9);
			MultiLabelPredictionsPlotBuilder m = new MultiLabelPredictionsPlotBuilder(
					confs);

			// Add first
			m.addPrediction(Pvalues.y_true.get(0), Pvalues.y_pred.get(0));
			EfficiencyPlot cp = m.buildPlot();
			Assert.assertEquals(1, cp.getNumExamples());
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(confs, m.getEvaluationPoints());

			Assert.assertEquals(1, cp.getEfficiency(0.9), 0.0001);
			Assert.assertEquals(0, cp.getEfficiency(0.8), 0.0001);
			Assert.assertEquals(0, cp.getEfficiency(0.7), 0.0001);
			Assert.assertEquals(0, cp.getEfficiency(0.6), 0.0001);

			// Add second
			m.addPrediction(Pvalues.y_true.get(1), Pvalues.y_pred.get(1));
			cp = m.buildPlot();
			Assert.assertEquals(2, cp.getNumExamples());
			Assert.assertEquals(2, m.getNumExamples());
			Assert.assertEquals(confs, m.getEvaluationPoints());

			Assert.assertEquals(1, cp.getEfficiency(0.9), 0.0001);
			Assert.assertEquals(.5, cp.getEfficiency(0.8), 0.0001);
			Assert.assertEquals(0.5, cp.getEfficiency(0.7), 0.0001);
			Assert.assertEquals(0.5, cp.getEfficiency(0.6), 0.0001); // 0.5 accuracy one found one empty set

			// Add third
			m.addPrediction(Pvalues.y_true.get(2), Pvalues.y_pred.get(2)); // 1=.07 2=.34 - only making single-label or
																			// empty set
			Assert.assertEquals(3, m.getNumExamples());
			cp = m.buildPlot();
			Assert.assertEquals(2. / 3, cp.getEfficiency(0.9), 0.0001);
			Assert.assertEquals(1. / 3, cp.getEfficiency(0.8), 0.0001);
			Assert.assertEquals(1. / 3, cp.getEfficiency(0.7), 0.0001);
			Assert.assertEquals(1. / 3, cp.getEfficiency(0.6), 0.0001);

			// Clone
			MultiLabelPredictionsPlotBuilder clone = m.clone();
			Assert.assertEquals(confs, clone.getEvaluationPoints());
			Assert.assertEquals(0, clone.getNumExamples());

			// Clear
			m.clear();
			Assert.assertEquals(confs, m.getEvaluationPoints());
			Assert.assertEquals(0, m.getNumExamples());
		}

		@Test
		public void SingleLabelPredictionsPlotBuilder() {
			List<Double> confs = Arrays.asList(.5, .8);
			SingleLabelPredictionsPlotBuilder m = new SingleLabelPredictionsPlotBuilder();
			m.setEvaluationPoints(confs);

			// Add first
			m.addPrediction(Pvalues.y_true.get(0), Pvalues.y_pred.get(0));
			EfficiencyPlot cp = m.buildPlot();
			Assert.assertEquals(1, cp.getNumExamples());
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(Arrays.asList(0.5, 0.8), m.getEvaluationPoints());

			Assert.assertEquals(1, cp.getEfficiency(0.8), 0.0001);
			Assert.assertEquals(1, cp.getEfficiency(0.5), 0.0001);

			// Add second
			m.addPrediction(Pvalues.y_true.get(1), Pvalues.y_pred.get(1));
			cp = m.buildPlot();
			Assert.assertEquals(2, cp.getNumExamples());
			Assert.assertEquals(2, m.getNumExamples());
			Assert.assertEquals(Arrays.asList(0.5, 0.8), m.getEvaluationPoints());

			Assert.assertEquals(.5, cp.getEfficiency(0.8), 0.0001);
			Assert.assertEquals(.5, cp.getEfficiency(0.5), 0.0001); // 0.5 accuracy one found one empty set

			// Add third
			m.addPrediction(Pvalues.y_true.get(2), Pvalues.y_pred.get(2)); // 1=.07 2=.34 - only making single-label or
																			// empty set
			Assert.assertEquals(3, m.getNumExamples());
			cp = m.buildPlot();
			Assert.assertEquals(2. / 3, cp.getEfficiency(0.8), 0.0001);
			Assert.assertEquals(1. / 3, cp.getEfficiency(0.5), 0.0001);

			// Clone
			SingleLabelPredictionsPlotBuilder clone = m.clone();
			Assert.assertEquals(confs, clone.getEvaluationPoints());
			Assert.assertEquals(0, clone.getNumExamples());

			// Clear
			m.clear();
			Assert.assertEquals(confs, m.getEvaluationPoints());
			Assert.assertEquals(0, m.getNumExamples());
		}

	}

	@Category(UnitTest.class)
	public static class TestCPRegressionMetrics {

		@Test
		public void ConfidenceGivenPredictionIntervalWidth() {
			ConfidenceGivenPredictionIntervalWidth m = new ConfidenceGivenPredictionIntervalWidth();
			m.setCIWidth(2.5);
			assertNone(m);

			// First
			m.addPrediction(1., null, .9);
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(.9, m.getScore(), 0.00001);
			Assert.assertEquals(.9, m.getMean(), 0.00001);
			Assert.assertEquals(.9, m.getMedian(), 0.00001);

			// Second
			m.addPrediction(1., null, .88);
			Assert.assertEquals(2, m.getNumExamples());
			Assert.assertEquals(.89, m.getScore(), 0.00001);
			Assert.assertEquals(.89, m.getMean(), 0.00001);
			Assert.assertEquals(.89, m.getMedian(), 0.00001);

			// Third
			m.addPrediction(1., null, .7);
			Assert.assertEquals(3, m.getNumExamples());
			Assert.assertEquals(m.getMean(), m.getScore(), 0.00001);
			Assert.assertEquals(MathUtils.mean(.9, .88, .7), m.getMean(), 0.00001);
			Assert.assertEquals(MathUtils.median(.9, .88, .7), m.getMedian(), 0.00001);

			// Clone
			ConfidenceGivenPredictionIntervalWidth m2 = m.clone();
			assertNone(m2);
			m2.addPrediction(1., null, .7);
			m2.addPrediction(1., null, .88);
			m2.addPrediction(1., null, .9);
			Assert.assertEquals(3, m2.getNumExamples());
			Assert.assertEquals(m.getScore(), m2.getScore(), 0.00001);

			// Clear
			m2.clear();
			assertNone(m2);
			m2.addPrediction(1., null, .7);
			m2.addPrediction(1., null, .88);
			m2.addPrediction(1., null, .9);
			Assert.assertEquals(3, m2.getNumExamples());
			Assert.assertEquals(m.getScore(), m2.getScore(), 0.00001);

		}

		@Test
		public void CPRegressionCalibrationPlotBuilder() {
			List<Double> confs = new ArrayList<>(Arrays.asList(0.9, 0.7, 0.8, 0.6));
			Collections.sort(confs);

			CPRegressionCalibrationPlotBuilder b = new CPRegressionCalibrationPlotBuilder(
					confs);

			// First
			Map<Double, Range<Double>> predIntervals = new HashMap<>();
			predIntervals.put(0.9, Range.closed(1., 6.));
			predIntervals.put(0.8, Range.closed(3.3, 4.2));
			predIntervals.put(0.6, Range.closed(3.6, 3.9));
			predIntervals.put(0.7, Range.closed(3.4, 4.0));
			b.addPrediction(3.5, predIntervals);
			CalibrationPlot cp = b.buildPlot();
			Assert.assertEquals(confs, cp.getXvalues());
			Assert.assertEquals(1, cp.getNumExamples());
			Assert.assertEquals(1, b.getNumExamples());
			// new HashSet<>(0.6,0.7);
			Assert.assertEquals(Arrays.asList(0.6, 0.7, 0.8, 0.9), b.getEvaluationPoints());
			Assert.assertEquals(Arrays.asList(0.6, 0.7, 0.8, 0.9), cp.getXvalues());
			Assert.assertEquals(1, cp.getAccuracy(0.9), 0.0001);
			Assert.assertEquals(1, cp.getAccuracy(0.8), 0.0001);
			Assert.assertEquals(1, cp.getAccuracy(0.7), 0.0001);
			Assert.assertEquals(0, cp.getAccuracy(0.6), 0.0001);

			// Second
			predIntervals = new HashMap<>();
			predIntervals.put(0.9, Range.closed(2., 8.));
			predIntervals.put(0.8, Range.closed(3., 7.));
			predIntervals.put(0.6, Range.closed(4., 5.));
			predIntervals.put(0.7, Range.closed(4., 5.9));
			b.addPrediction(6, predIntervals);

			// b.addPrediction(6, Range.closed(4., 5.), 0.6);
			// b.addPrediction(6, Range.closed(4., 5.9), 0.7);
			// b.addPrediction(6, Range.closed(3., 7.), 0.8);
			// b.addPrediction(6, Range.closed(2., 8.), 0.9);
			cp = b.buildPlot();
			Assert.assertEquals(2, cp.getNumExamples());
			Assert.assertEquals(2, b.getNumExamples());
			Assert.assertEquals(Arrays.asList(0.6, 0.7, 0.8, 0.9), b.getEvaluationPoints());
			// Assert.assertEquals(Sets.newHashSet(0.6, 0.7, 0.8, 0.9),b.getConfidences());
			Assert.assertEquals(Arrays.asList(0.6, 0.7, 0.8, 0.9), cp.getXvalues());
			Assert.assertEquals(1, cp.getAccuracy(0.9), 0.0001);
			Assert.assertEquals(1, cp.getAccuracy(0.8), 0.0001);
			Assert.assertEquals(.5, cp.getAccuracy(0.7), 0.0001);
			Assert.assertEquals(0, cp.getAccuracy(0.6), 0.0001);

			// Clear
			b.clear();
			Assert.assertEquals(confs, b.getEvaluationPoints());
			Assert.assertEquals(0, b.getNumExamples());

			// Clone
			CPRegressionCalibrationPlotBuilder m2 = b.clone();
			Assert.assertEquals(confs, m2.getEvaluationPoints());
			Assert.assertEquals(0, m2.getNumExamples());
		}

		@Test
		public void CPRegressionEfficiencyPlotBuilder() {
			List<Double> confs = Arrays.asList(0.6,.7, .8, 0.9);
			CPRegressionEfficiencyPlotBuilder b = new CPRegressionEfficiencyPlotBuilder(
					confs);

			Map<Double, Range<Double>> predIntervals = new HashMap<>();
			predIntervals.put(0.9, Range.closed(1., 6.));
			predIntervals.put(0.8, Range.closed(3.3, 4.2));
			predIntervals.put(0.6, Range.closed(3.6, 3.9));
			predIntervals.put(0.7, Range.closed(3.4, 4.0));
			b.addPrediction(3.5, predIntervals);
			// b.addPrediction(3.5, Range.closed(1., 6.), 0.9);
			// b.addPrediction(3.5, Range.closed(3.3, 4.2), 0.8);
			// b.addPrediction(3.5, Range.closed(3.6, 3.9), 0.6);
			// b.addPrediction(3.5, Range.closed(3.4, 4.0), 0.7);
			EfficiencyPlot cp = b.buildPlot();
			Assert.assertEquals(confs, cp.getXvalues());
			// Assert.assertEquals(1, cp.getNumExamples());
			Assert.assertEquals(1, b.getNumExamples());
			Assert.assertEquals(Arrays.asList(0.6, 0.7, 0.8, 0.9), b.getEvaluationPoints());
			// Assert.assertEquals(Sets.newHashSet(0.6, 0.7, 0.8, 0.9), b.getConfidences());
			Assert.assertEquals(Arrays.asList(0.6, 0.7, 0.8, 0.9), cp.getXvalues());
			Assert.assertEquals(5, cp.getEfficiency(0.9), 0.0001);
			Assert.assertEquals(4.2 - 3.3, cp.getEfficiency(0.8), 0.0001);
			Assert.assertEquals(0.6, cp.getEfficiency(0.7), 0.0001);
			Assert.assertEquals(.3, cp.getEfficiency(0.6), 0.0001);

			predIntervals = new HashMap<>();
			predIntervals.put(0.9, Range.closed(2., 8.));
			predIntervals.put(0.8, Range.closed(3., 7.));
			predIntervals.put(0.6, Range.closed(4., 5.));
			predIntervals.put(0.7, Range.closed(4., 5.9));
			b.addPrediction(6, predIntervals);
			// b.addPrediction(6, Range.closed(4., 5.), 0.6);
			// b.addPrediction(6, Range.closed(4., 5.9), 0.7);
			// b.addPrediction(6, Range.closed(3., 7.), 0.8);
			// b.addPrediction(6, Range.closed(2., 8.), 0.9);
			cp = b.buildPlot();
			// Assert.assertEquals(2, cp.getNumExamples());
			Assert.assertEquals(2, b.getNumExamples());
			Assert.assertEquals(Arrays.asList(0.6, 0.7, 0.8, 0.9), b.getEvaluationPoints());
			// Assert.assertEquals(Sets.newHashSet(0.6, 0.7, 0.8, 0.9),b.getConfidences());
			Assert.assertEquals(Arrays.asList(0.6, 0.7, 0.8, 0.9), cp.getXvalues());
			Assert.assertEquals(5.5, cp.getEfficiency(0.9), 0.0001);
			Assert.assertEquals(MathUtils.mean(4.2 - 3.3, 4), cp.getEfficiency(0.8), 0.0001);
			Assert.assertEquals(MathUtils.mean(0.6, 5.9 - 4), cp.getEfficiency(0.7), 0.0001);
			Assert.assertEquals(MathUtils.mean(1, .3), cp.getEfficiency(0.6), 0.0001);

			// Clear
			b.clear();
			Assert.assertEquals(0,b.getNumExamples());
			Assert.assertEquals(confs, b.getEvaluationPoints());

			// Clone
			CPRegressionEfficiencyPlotBuilder m2 = b.clone();
			Assert.assertEquals(0,m2.getNumExamples());
			Assert.assertEquals(confs, m2.getEvaluationPoints());
		}

		@Test
		public void MeanPredictionIntervalWidth() {
			MeanPredictionIntervalWidth m = new MeanPredictionIntervalWidth();
			assertNone(m);

			// First
			m.addPrediction(3, Range.closed(1., 2.));
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(1, m.getScore(), 0.00001);

			m.addPrediction(3, Range.closed(1.5, 2.));
			Assert.assertEquals(2, m.getNumExamples());
			Assert.assertEquals(MathUtils.mean(1, .5), m.getScore(), 0.00001);

			m.addPrediction(3, Range.closed(4.5, 6.8));
			Assert.assertEquals(3, m.getNumExamples());
			Assert.assertEquals(MathUtils.mean(1, .5, 6.8 - 4.5), m.getScore(), 0.00001);

			// Clone
			m = m.clone();
			assertNone(m);
			m.addPrediction(3, Range.closed(6., 9.));
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(3, m.getScore(), 0.00001);

			// Clear
			m.clear();
			assertNone(m);
			m.addPrediction(3, Range.closed(-6., 9.));
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(15, m.getScore(), 0.00001);
		}

		@Test
		public void MedianPredictionIntervalWidth() {
			MedianPredictionIntervalWidth m = new MedianPredictionIntervalWidth();
			assertNone(m);

			// First
			m.addPrediction(3, Range.closed(1., 2.));
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(1, m.getScore(), 0.00001);

			m.addPrediction(3, Range.closed(1.5, 2.));
			Assert.assertEquals(2, m.getNumExamples());
			Assert.assertEquals(MathUtils.mean(1, .5), m.getScore(), 0.00001);

			m.addPrediction(3, Range.closed(4.5, 6.8));
			Assert.assertEquals(3, m.getNumExamples());
			Assert.assertEquals(MathUtils.median(1, .5, 6.8 - 4.5), m.getScore(), 0.00001);

			// Clone
			m = m.clone();
			assertNone(m);
			m.addPrediction(3, Range.closed(6., 9.));
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(3, m.getScore(), 0.00001);

			// Clear
			m.clear();
			assertNone(m);
			m.addPrediction(3, Range.closed(-6., 9.));
			Assert.assertEquals(1, m.getNumExamples());
			Assert.assertEquals(15, m.getScore(), 0.00001);
		}
	}

	@Category(UnitTest.class)
	public static class TestVAPMetrics {

		@Test
		public void MeanVAPInterval() {

			MeanVAPInterval m = new MeanVAPInterval(); // 150, 152, 159, 170, 250
			assertNone(m);
			m.addPrediction(1, ProbCase2.y_pred.get(0), 0.01, 0.015);
			m.addPrediction(2, ProbCase2.y_pred.get(1), 0.02, 0.025);
			m.addPrediction(1, ProbCase2.y_pred.get(2), 0.03, 0.017);
			m.addPrediction(2, ProbCase2.y_pred.get(3), 0.04, 0.0152);
			m.addPrediction(1, ProbCase2.y_pred.get(4), 0.05, 0.0159);

			Assert.assertEquals(5, m.getNumExamples());
			Assert.assertEquals(0.03, m.getScore(), 0.00001);

			// Clone
			MeanVAPInterval m2 = m.clone();

			assertNone(m2);
			m2.addPrediction(1, ProbCase2.y_pred.get(0), 0.01, 0.015);
			m2.addPrediction(2, ProbCase2.y_pred.get(1), 0.02, 0.025);
			m2.addPrediction(1, ProbCase2.y_pred.get(2), 0.03, 0.017);
			m2.addPrediction(2, ProbCase2.y_pred.get(3), 0.04, 0.0152);
			m2.addPrediction(1, ProbCase2.y_pred.get(4), 0.05, 0.0159);

			Assert.assertEquals(5, m2.getNumExamples());
			Assert.assertEquals(0.03, m2.getScore(), 0.00001);

			// Clear
			m.clear();
			assertNone(m);
			m.addPrediction(1, ProbCase2.y_pred.get(0), 0.01, 0.015);
			m.addPrediction(2, ProbCase2.y_pred.get(1), 0.02, 0.025);
			m.addPrediction(1, ProbCase2.y_pred.get(2), 0.03, 0.017);
			m.addPrediction(2, ProbCase2.y_pred.get(3), 0.04, 0.0152);
			m.addPrediction(1, ProbCase2.y_pred.get(4), 0.05, 0.0159);

			Assert.assertEquals(5, m.getNumExamples());
			Assert.assertEquals(0.03, m.getScore(), 0.00001);
		}

		@Test
		public void MedianVAPInterval() {

			MedianVAPInterval median = new MedianVAPInterval();
			assertNone(median);

			median.addPrediction(1, ProbCase2.y_pred.get(0), 0.01, 0.015);
			median.addPrediction(2, ProbCase2.y_pred.get(1), 0.02, 0.025);
			median.addPrediction(1, ProbCase2.y_pred.get(2), 0.03, 0.017);
			median.addPrediction(2, ProbCase2.y_pred.get(3), 0.04, 0.0152);
			median.addPrediction(1, ProbCase2.y_pred.get(4), 0.05, 0.0159);

			Assert.assertEquals(5, median.getNumExamples());
			Assert.assertEquals(0.0159, median.getScore(), 0.00001);

			// Clone
			MedianVAPInterval m2 = median.clone();
			assertNone(m2);
			m2.addPrediction(1, ProbCase2.y_pred.get(0), 0.01, 0.015);
			m2.addPrediction(2, ProbCase2.y_pred.get(1), 0.02, 0.025);
			m2.addPrediction(1, ProbCase2.y_pred.get(2), 0.03, 0.017);
			m2.addPrediction(2, ProbCase2.y_pred.get(3), 0.04, 0.0152);
			m2.addPrediction(1, ProbCase2.y_pred.get(4), 0.05, 0.0159);

			Assert.assertEquals(5, m2.getNumExamples());
			Assert.assertEquals(median.getScore(), m2.getScore(), 0.00001);

			// Clear
			median.clear();
			assertNone(median);

			median.addPrediction(1, ProbCase2.y_pred.get(0), 0.01, 0.015);
			median.addPrediction(2, ProbCase2.y_pred.get(1), 0.02, 0.025);
			median.addPrediction(1, ProbCase2.y_pred.get(2), 0.03, 0.017);
			median.addPrediction(2, ProbCase2.y_pred.get(3), 0.04, 0.0152);
			median.addPrediction(1, ProbCase2.y_pred.get(4), 0.05, 0.0159);

			Assert.assertEquals(5, median.getNumExamples());
			Assert.assertEquals(0.0159, median.getScore(), 0.00001);
		}

		@Test
		public void VAPCalibrationPlotBuilder() {
			VAPCalibrationPlotBuilder b = new VAPCalibrationPlotBuilder();
			List<Double> evalPoints = new ArrayList<>(b.getEvaluationPoints());
			// List<Range<Double>> bins = b.getBins();
			// System.err.println(bins);

			b.addPrediction(ProbCase2.y_true.get(0), ProbCase2.y_pred.get(0));
			Assert.assertEquals(1, b.getNumExamples());
			CalibrationPlot cp = b.buildPlot();
			Assert.assertEquals(1, cp.getXvalues().size());

			b.addPrediction(ProbCase2.y_true.get(1), ProbCase2.y_pred.get(1));
			Assert.assertEquals(2, b.getNumExamples());
			cp = b.buildPlot();
			Assert.assertEquals(2, cp.getXvalues().size());

			b.addPrediction(ProbCase2.y_true.get(2), ProbCase2.y_pred.get(2));
			Assert.assertEquals(3, b.getNumExamples());
			cp = b.buildPlot();
			Assert.assertEquals(3, cp.getXvalues().size());

			b.addPrediction(ProbCase2.y_true.get(3), ProbCase2.y_pred.get(3));
			Assert.assertEquals(4, b.getNumExamples());
			cp = b.buildPlot();
			Assert.assertEquals(4, cp.getXvalues().size());

			b.addPrediction(ProbCase2.y_true.get(4), ProbCase2.y_pred.get(4));
			Assert.assertEquals(5, b.getNumExamples());
			cp = b.buildPlot();
			Assert.assertEquals(5, cp.getXvalues().size());

			b.addPrediction(ProbCase2.y_true.get(5), ProbCase2.y_pred.get(5));
			Assert.assertEquals(6, b.getNumExamples());
			cp = b.buildPlot();
			Assert.assertEquals(6, cp.getXvalues().size());

			b.addPrediction(ProbCase2.y_true.get(6), ProbCase2.y_pred.get(6));
			Assert.assertEquals(7, b.getNumExamples());
			cp = b.buildPlot();
			// System.err.println(cp.getXvalues());
			Assert.assertEquals(7, cp.getXvalues().size());
			List<Number> instPerBin = cp.getCurves().get(VAPCalibrationPlotBuilder.NUM_EX_PER_BIN_LABEL);
			for (Number n : instPerBin){
				Assert.assertEquals(1, n.intValue());
			}

			Assert.assertEquals(1., cp.getAccuracy(.55), 0.001); // Now the bins are non-overlapping and all ends up in a separate bin
			// int indx = cp.getXvalues().indexOf(0.55);
			// Assert.assertEquals(1, cp.getCurves().get(VAPCalibrationPlotBuilder.NUM_EX_PER_BIN_LABEL).get(indx));

			VAPCalibrationPlotBuilder clone = b.clone();
			Assert.assertEquals(0, clone.getNumExamples());
			Assert.assertEquals(evalPoints, clone.getEvaluationPoints());

			b.clear();
			Assert.assertEquals(0, b.getNumExamples());
			Assert.assertEquals(evalPoints, b.getEvaluationPoints());
		}

	}

}

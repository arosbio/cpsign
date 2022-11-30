/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.vap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.MathUtils;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.SparseFeature;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.SVC;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.classification.ROC_AUC;
import com.arosbio.ml.sampling.FoldedSampling;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.testing.ModelEvaluator;
import com.arosbio.ml.vap.avap.AVAPClassifier;
import com.arosbio.ml.vap.avap.CVAPPrediction;
import com.arosbio.ml.vap.ivap.IVAPClassifier;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.TestEnv;
import com.github.sanity.pav.PairAdjacentViolators;
import com.github.sanity.pav.Point;

import kotlin.jvm.functions.Function1;

@Category(UnitTest.class)
public class TestCVAPClassification extends TestEnv {

	@Test
	public void testCVAP() throws Exception {
		AVAPClassifier cvap = new AVAPClassifier(new LinearSVC(), new FoldedSampling(10));
		SubSet trainingset = SubSet.fromLIBSVMFormat(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_NEG_POS.openStream());
		SubSet[] splits = trainingset.splitRandom(0.1);
		Dataset prob = new Dataset();
		prob.withDataset(splits[1]);
		LoggerUtils.setDebugMode();

		cvap.train(prob);
		Assert.assertTrue(cvap.isTrained());

		systemOutRule.clearLog();

		for (DataRecord rec: splits[0].subList(0, 10)) {
			CVAPPrediction<?> res = cvap.predict(rec.getFeatures());
			System.out.println(rec.getLabel() + " : "+ res);
		}
		System.out.println(systemOutRule.getLog());
	}
	
//	@Test
	public void testOnlyOneClass() throws Exception {
		AVAPClassifier cvap = new AVAPClassifier(new LinearSVC(), new FoldedSampling(10));
		Dataset data = TestDataLoader.getInstance().getDataset(true, true);
		
		SubSet ds = data.getDataset();
		DataRecord testRec = ds.remove(0);
		List<DataRecord> zeroCls = new ArrayList<>();
		DataRecord oneCls = null;
		for (DataRecord r : ds) {
			if (r.getLabel() == 0) {
				zeroCls.add(r);
			} else if (oneCls == null){
				oneCls = r;
			}
		}
		
		Dataset problem = new Dataset();
		problem.withDataset(new SubSet(zeroCls));
		problem.getDataset().add(oneCls);
		cvap.train(problem);
		
		CVAPPrediction<?> res = cvap.predict(testRec.getFeatures());
		System.err.println(res);
	}

//	@Test
	public void printCalibrationCurve() throws Exception {
		AVAPClassifier cvap = new AVAPClassifier(new LinearSVC(), new FoldedSampling(10));
		Dataset sp = TestDataLoader.getInstance().getDataset(true, false);
		
		// sp.initializeDescriptors();
		// sp.add(new SDFReader(new FileInputStream(AmesBinaryClass.LARGE_FILE_PATH)), AmesBinaryClass.PROPERTY, new NamedLabels(AmesBinaryClass.AMES_LABELS));
//		sp.fromChemFile(getURIFromFullPath(LARGE_FILE_PATH), PROPERTY, AMES_LABELS);

		FeatureVector testEx = sp.getDataset().remove(0).getFeatures();

		cvap.train(sp);

		IVAPClassifier ivap = cvap.getModels().get(1);
		List<Point> calibrationPoints = ivap.getCalibrationPoints();

		double score = ivap.getScoringAlgorithm().predictScores(testEx).get(ivap.getScoringAlgorithm().getLabels().get(0)); 

		// Fit isotonic regression using first hypothetical label
		calibrationPoints.add(new Point(score, 0d)); //(double)labels[0]));
		PairAdjacentViolators pavLabel0 = new PairAdjacentViolators(calibrationPoints);
		final Function1<Double, Double> interpolatorLabel0 = pavLabel0.interpolator();
		//				double p0 = MathUtils.truncate(interpolatorLabel0.invoke(score), 0d, 1d);

		List<Point> scoresF0 = calibrationPoints.stream().map(new Function<Point, Point>() {

			@Override
			public Point apply(Point t) {
				return new Point(t.getX(), MathUtils.truncate(interpolatorLabel0.invoke(t.getX()), 0, 1));
			}
		}).collect(Collectors.toList());
		calibrationPoints.remove(calibrationPoints.size()-1); // remove the added example

		Collections.sort(scoresF0, new Comparator<Point>() {

			@Override
			public int compare(Point o1, Point o2) {
				if (o1.getX() > o2.getX())
					return 1;
				else if (o1.getX() < o2.getX())
					return -1;
				return 0;
			}

		});
		for (Point p: scoresF0) {
			SYS_OUT.println(p.getX() + "\t" + p.getY());
		}


		// Fit isotonic regression using second hypothetical label
		calibrationPoints.add(new Point(score, 1d)); //(double)labels[1]));
		PairAdjacentViolators pavLabel1 = new PairAdjacentViolators(calibrationPoints);
		final Function1<Double, Double> interpolatorLabel1 = pavLabel1.interpolator();

		List<Point> scoresF1 = calibrationPoints.stream().map(new Function<Point, Point>() {

			@Override
			public Point apply(Point t) {
				return new Point(t.getX(), MathUtils.truncate(interpolatorLabel1.invoke(t.getX()), 0, 1));
			}
		}).collect(Collectors.toList());


		Collections.sort(scoresF1, new Comparator<Point>() {

			@Override
			public int compare(Point o1, Point o2) {
				if (o1.getX() > o2.getX())
					return 1;
				else if (o1.getX() < o2.getX())
					return -1;
				return 0;
			}

		});
		for (Point p: scoresF1) {
			SYS_ERR.println(p.getX() + "\t" + p.getY());
		}
	}

	@Test
	public void testCalcROC() throws Exception {

		AVAPClassifier pred = new AVAPClassifier(new LinearSVC(),new RandomSampling(5, .2));
		Dataset allData = TestDataLoader.getInstance().getDataset(true, false);
		SubSet[] splits = allData.getDataset().splitRandom(.3);
		SubSet testSet = splits[0];
		Dataset training = new Dataset();
		training.withDataset(splits[1]);

		pred.train(training);

		// SDFReader r = new SDFile(new File(AmesBinaryClass.MINI_FILE_PATH+GZIP_SUFFIX).toURI()).getIterator();
		// List<IAtomContainer> mols = ImmutableList.copyOf(r);
		// List<DataRecord> testSet = new ArrayList<>();
		// ChemDataset ds = pred.getDataset();
		// NamedLabels labels = ds.getTextualLabels();
		// for (IAtomContainer m : mols){
		// 	testSet.add(new DataRecord((double) labels.getValue(m.getProperty(AmesBinaryClass.PROPERTY).toString()), ds.convertToFeatureVector(m)));
		// }
		// Pair<List<DataRecord>,DescriptorCalcInfo> computedTestSet = pred.getDataset().convertToFeatureVector(mols);
		ROC_AUC roc = new ROC_AUC();
		ModelEvaluator.evaluate(pred, testSet, (Metric) roc);
		
		
		// TestRunner tester = new TestRunner.Builder(new FixedTestSet(testSet)).build();
		// List<Metric> mets = tester.evaluate(pred, ImmutableList.of(new ROC_AUC()));
		System.err.println(roc);
		System.err.println(roc.rocAsCSV(','));
		// printLogs();
	}

	@Test
	public void testCalcGradient() throws Exception {
		AVAPClassifier cvap = new AVAPClassifier(new LinearSVC(), new FoldedSampling(10));
		SubSet trainingset = TestDataLoader.getInstance().getDataset(true, false).getDataset().shuffle().splitStatic(400)[0];
		SubSet[] splits = trainingset.splitRandom(0.1);
		Dataset prob = new Dataset();
		prob.withDataset(splits[1]);

		cvap.train(prob);
		
		for (DataRecord r : splits[0]) {
			List<SparseFeature> cvapRes = cvap.calculateGradient(r.getFeatures());
			Assert.assertEquals(r.getFeatures().getNumExplicitFeatures(),cvapRes.size());
		}
//		printLogs();

		//		for (DataRecord rec: splits[0].getRecords().subList(0, 10))
		//			original.println(cvap.calculateGradient(rec.getFeatures()));
	}
	
	@Test
	public void testOutputAdds2OneSmall() throws Exception {
		doTestProbabilitiesSum2one(new LinearSVC(), TestDataLoader.getInstance().getDataset(true, true));
	}
	
	@Test
	@Category(PerformanceTest.class)
	public void testOutputAdds2OneLarge() throws Exception {
		doTestProbabilitiesSum2one(new LinearSVC(), TestDataLoader.getInstance().getDataset(true, false));
	}
	
	@Test
	public void testOutputAdds2OneAMES_DATA() throws Exception {
		doTestProbabilitiesSum2one(new C_SVC(), TestDataLoader.getInstance().getDataset(true, false));
	}
	
	public void doTestProbabilitiesSum2one(SVC alg, Dataset data) throws Exception {
		AVAPClassifier cvap = new AVAPClassifier(alg, new FoldedSampling(10));
		
		SubSet[] splits = data.getDataset().splitRandom(0.1);
		Dataset problem = new Dataset();
		problem.withDataset(splits[1]);

		cvap.train(problem);
		
		int numInvalid = 0;
		for (DataRecord r : splits[0]) {
			CVAPPrediction<Integer> res = cvap.predict(r.getFeatures());
			Map<Integer,Double> probs = res.getProbabilities();
			double sum = MathUtils.sumDoubles(probs.values());
			if (sum > 1) {
				SYS_ERR.println("Probability was too large: " + sum);
				numInvalid++;
			} else if (sum < 1) {
				SYS_ERR.println("Probability was too small: " + sum);
				numInvalid++;
			}
		}
		Assert.assertEquals(0, numInvalid);
		
	}


}

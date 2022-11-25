/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.sampling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.splitting.FoldedSplitter;
import com.arosbio.data.splitting.RandomSplitter;
import com.arosbio.data.splitting.TestDataSplitting;
import com.arosbio.data.transform.duplicates.UseVoting;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.icp.ICPClassifier;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.sampling.impl.TrainSplitWrapper;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.TestDataLoader;

import ch.qos.logback.core.joran.spi.JoranException;

@Category(UnitTest.class)
@RunWith(Enclosed.class)
public class TestSamplingStrategies {

	public static final boolean DO_EXTENSIVE_CHECK = true;

	// General stuff

	@BeforeClass
	public static void reloadLogging() throws JoranException {
		LoggerUtils.reloadLoggingConfig();
	}

	@Test
	public void testNames() {
		Assert.assertTrue(FuzzyServiceLoader.load(SamplingStrategy.class, "folded") instanceof FoldedSampling);
		Assert.assertTrue(
				FuzzyServiceLoader.load(SamplingStrategy.class, "folded strat") instanceof FoldedStratifiedSampling);
		Assert.assertTrue(FuzzyServiceLoader.load(SamplingStrategy.class, "rand") instanceof RandomSampling);
		Assert.assertTrue(
				FuzzyServiceLoader.load(SamplingStrategy.class, "random-strat") instanceof RandomStratifiedSampling);
	}

	@Test
	public void testSaveLoad() throws Exception {
		Iterator<SamplingStrategy> iter = FuzzyServiceLoader.iterator(SamplingStrategy.class);
		while (iter.hasNext()) {
			SamplingStrategy ss = iter.next();
			doTestSaveLoad(ss);
		}

		doTestSaveLoad(new RandomSampling(4, 0.5));
		doTestSaveLoad(new RandomStratifiedSampling(7, .4));

		doTestSaveLoad(new FoldedSampling(8));
		doTestSaveLoad(new FoldedStratifiedSampling(7));

	}

	private void doTestSaveLoad(SamplingStrategy ss) {
		Map<String, Object> props = ss.getProperties();
		SamplingStrategy loaded = SamplingStrategyUtils.fromProperties(props);

		Assert.assertEquals(ss.getClass(), loaded.getClass());
		Assert.assertEquals(ss.getNumSamples(), loaded.getNumSamples());
		if (ss instanceof RandomSampling) {
			Assert.assertEquals(((RandomSampling) ss).getCalibrationRatio(),
					((RandomSampling) loaded).getCalibrationRatio(), 0.001);
		}
		Assert.assertEquals(ss.getProperties(), loaded.getProperties());
	}

	private static void assertEqualICPDatasets(TrainSplit ds1, TrainSplit ds2) {
		Assert.assertEquals(ds1.getCalibrationSet(), ds2.getCalibrationSet());
		Assert.assertEquals(ds1.getProperTrainingSet(), ds2.getProperTrainingSet());
	}

	private static void assertNOTEqualSplits(TrainSplit ts1, TrainSplit ts2) {
		Assert.assertFalse(ts1.getCalibrationSet().equals(ts2.getCalibrationSet()));
		Assert.assertFalse(ts1.getProperTrainingSet().equals(ts2.getProperTrainingSet()));

	}

	private static void assertAllRecordsPreserved(Dataset problem, TrainSplit dataset) {
		for (int i = 0; i < problem.getDataset().size(); i++) {
			boolean foundMatch = false;
			DataRecord problemRecord = problem.getDataset().get(i);
			// Double problemActivity = problem.getY().get(i);

			// Check for the record in the calibration set
			for (int j = 0; j < dataset.getCalibrationSet().size(); j++)
				if (problemRecord.equals(dataset.getCalibrationSet().get(j))) {
					foundMatch = true;
					break;
				}

			if (foundMatch)
				continue;

			// Check for the record in the proper training set
			for (int j = 0; j < dataset.getProperTrainingSet().size(); j++)
				if (problemRecord.equals(dataset.getProperTrainingSet().get(j))) {
					foundMatch = true;
					break;
				}

			Assert.assertTrue(foundMatch);
		}
	}

	@Category(UnitTest.class)
	public static class TestFoldedSampling {

		private static final int NR_FOLDS = 10;

		@Test
		public void TestFoldedCalibrationSet() throws Exception {

			// Read in problem from file
			Dataset problem = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.REGRESSION_HOUSING);

			int sizeBefore = problem.getDataset().size();

			Iterator<TrainSplit> datasets = new TrainSplitWrapper(new FoldedSplitter.Builder().numFolds(NR_FOLDS).build(problem));
			// new FoldedCalibSetIterator(problem,
			// 		10);

			Assert.assertTrue(problem.getDataset().size() == sizeBefore);

			assertFoldedCalibrationSetupOK(datasets, 10, problem);

		}

		@Test
		public void TestFoldedUsingStaticSeed() throws Exception {
			Dataset data = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.REGRESSION_HOUSING);
			Dataset clonedData = data.clone();
			long seed = 42L;
			int nrFolds = 3;

			Iterator<TrainSplit> datasets1 = new TrainSplitWrapper(new FoldedSplitter.Builder().numFolds(nrFolds).seed(seed).build(data)); 
			//new FoldedCalibSetIterator(problem, nrFolds, seed);
			Iterator<TrainSplit> datasets2 = new TrainSplitWrapper(new FoldedSplitter.Builder().numFolds(nrFolds).seed(seed).build(clonedData)); 
			// new FoldedCalibSetIterator(clonedProblem, nrFolds, seed);

			for (int i = 0; i < nrFolds; i++) {
				TrainSplit ds1 = datasets1.next();
				TrainSplit ds2 = datasets2.next();

				assertEqualICPDatasets(ds1, ds2);
			}
		}

		@Test
		public void TestFoldedTestsplitsWithExclusiveDatasets() throws Exception {
			Dataset dataset = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.REGRESSION_HOUSING);
			int initSize = dataset.getNumRecords(); // now all of them is in the model-and-calibrate dataset
			double splitFac = 0.2;
			SubSet[] ds_split1 = dataset.getDataset().splitStatic(splitFac);
			SubSet[] ds_split2 = ds_split1[1].splitRandom(splitFac);
			dataset.withDataset(ds_split2[1]); // this should be 64% of the data
			dataset.withCalibrationExclusiveDataset(ds_split1[0]); // this should be 20% of the data
			dataset.withModelingExclusiveDataset(ds_split2[0]); // this should be 16% of the data

			Iterator<TrainSplit> datasets1 = new TrainSplitWrapper(new FoldedSplitter.Builder().numFolds(NR_FOLDS).build(dataset));
			// new FoldedCalibSetIterator(problem, NR_FOLDS);
			int nrDs = 0;
			while (datasets1.hasNext()) {
				nrDs++;
				TrainSplit ds1 = datasets1.next();

				Assert.assertEquals(initSize, ds1.getCalibrationSet().size() + ds1.getProperTrainingSet().size());
				if (DO_EXTENSIVE_CHECK) {
					ds1.getCalibrationSet().containsAll(dataset.getCalibrationExclusiveDataset());
					ds1.getProperTrainingSet().containsAll(dataset.getModelingExclusiveDataset());
				}

			}
			Assert.assertEquals(NR_FOLDS, nrDs);
		}

		@Test
		public void TestFoldedSetupDifficultIndexes() throws Exception {
			// Read in problem from file
			Dataset problem = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.REGRESSION_HOUSING);

			// 34 - 10 folds
			Dataset smallProblem34 = new Dataset();
			smallProblem34.getDataset().setRecords(new ArrayList<>(problem.getDataset().subList(0, 34)));
			// smallProblem34.setY(new ArrayList<>(problem.getY().subList(0, 34)));
			int sizeBefore = smallProblem34.getDataset().size();
			Iterator<TrainSplit> datasets = new TrainSplitWrapper(new FoldedSplitter.Builder().numFolds(NR_FOLDS).build(smallProblem34));
			//   FoldedCalibSetIterator(smallProblem34,10);
			Assert.assertTrue(smallProblem34.getDataset().size() == sizeBefore);

			assertFoldedCalibrationSetupOK(datasets, 10, smallProblem34);

			// 36 - 10 folds
			Dataset smallProblem36 = new Dataset();
			smallProblem36.getDataset().setRecords(new ArrayList<>(problem.getDataset().subList(0, 36)));
			// smallProblem36.setY(new ArrayList<>(problem.getY().subList(0, 36)));

			sizeBefore = smallProblem36.getDataset().size();

			Iterator<TrainSplit> datasets36 = new TrainSplitWrapper(new FoldedSplitter.Builder().numFolds(NR_FOLDS).build(smallProblem36));
			// new FoldedCalibSetIterator(smallProblem36,
			// 		10);

			Assert.assertTrue(smallProblem36.getDataset().size() == sizeBefore);

			assertFoldedCalibrationSetupOK(datasets36, 10, smallProblem36);

		}

		@Test
		public void TestStratifiedFoldedSplit() throws Exception {
			long seed = 21423l;
			// Read in problem from file
			Dataset problem = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS);
			problem.apply(new UseVoting());
			// problem.getDataset().resolveDuplicates(DuplicatesStrategyFactory.keepOfClass(0));
			int initSize = problem.getNumRecords();
			Map<Double, Integer> freqs = problem.getDataset().getLabelFrequencies();
			double class0ratio = (double) (freqs.get(Double.valueOf(0))) / initSize;
			// original_err.println(freqs);
			// original_err.println(initSize + ", class0freq=" + class0ratio + ",
			// class0num=" + freqs.get(new Double(0.0)));

			// LoggerUtils.setDebugMode();
			Iterator<TrainSplit> it = new TrainSplitWrapper(new FoldedSplitter.Builder().numFolds(NR_FOLDS).stratify(true).seed(seed).build(problem)); 
			//new StratifiedFoldedCalibSetIterator(problem, NR_FOLDS, seed);
			Iterator<TrainSplit> it2 = new TrainSplitWrapper(new FoldedSplitter.Builder().numFolds(NR_FOLDS).stratify(true).seed(seed).build(problem)); 
			//new StratifiedFoldedCalibSetIterator(problem, NR_FOLDS, seed);
			TrainSplit split1 = null;

			int nrSplits = 0;
			while (it.hasNext()) {
				TrainSplit ts = it.next();
				TrainSplit ts2 = it2.next();
				nrSplits++;
				SubSet calibset = new SubSet();
				calibset.setRecords(ts.getCalibrationSet());
				// original.println(calibset.getLabelFrequencies().get(new Double(0))+ ", "+
				// calibset.size()+", " + class0ratio);
				SubSet prop = new SubSet();
				prop.setRecords(ts.getProperTrainingSet());
				// original.println(prop.getLabelFrequencies().get(new Double(0))+",
				// "+prop.size()+ ", " + class0ratio);

				Assert.assertTrue(Math.abs(
						calibset.getLabelFrequencies().get(Double.valueOf(0)) - calibset.size() * class0ratio) <= 2);
				Assert.assertTrue(
						Math.abs(prop.getLabelFrequencies().get(Double.valueOf(0)) - prop.size() * class0ratio) <= 2);

				Assert.assertEquals(initSize, prop.size() + calibset.size());
				Assert.assertTrue(Math.abs(initSize / NR_FOLDS - calibset.size()) <= 2);

				if (DO_EXTENSIVE_CHECK)
					assertEqualICPDatasets(ts, ts2);
				// original.println("round=" +nrSplits);
				if (nrSplits == 1) {
					split1 = ts;
				} else {
					assertNOTEqualSplits(split1, ts);
				}
			}

			Assert.assertEquals(NR_FOLDS, nrSplits);

			Iterator<TrainSplit> it3 = new TrainSplitWrapper(new FoldedSplitter.Builder().numFolds(NR_FOLDS).stratify(true).build(problem));  
			//new StratifiedFoldedCalibSetIterator(problem, NR_FOLDS);
			assertFoldedCalibrationSetupOK(it3, NR_FOLDS, problem);
		}

		@Test
		public void TestStratifiedFoldedSplitMoreDatasets() throws Exception {
			long seed = 21423l;
			// Read in problem from file
			Dataset dataset = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS);

			dataset.apply(new UseVoting());
			// problem.getDataset().resolveDuplicates(DuplicatesStrategyFactory.keepOfClass(0));

			SubSet[] split_ds1 = dataset.getDataset().splitRandom(.530);
			SubSet[] split_ds2 = split_ds1[1].splitRandom(0.854);
			dataset.withCalibrationExclusiveDataset(split_ds1[0])
					.withModelingExclusiveDataset(split_ds2[0])
					.withDataset(split_ds2[1]);

			int initSize = dataset.getDataset().size();
			int calibExclusiveSize = dataset.getCalibrationExclusiveDataset().size();
			int calibExclusiveClass0 = dataset.getCalibrationExclusiveDataset().getLabelFrequencies()
					.get(Double.valueOf(0));
			int modelExclusiveSize = dataset.getModelingExclusiveDataset().size();
			int modelExclusiveClass0 = dataset.getModelingExclusiveDataset().getLabelFrequencies()
					.get(Double.valueOf(0));
			Map<Double, Integer> freqs = dataset.getDataset().getLabelFrequencies();
			double class0ratio = (double) (freqs.get(Double.valueOf(0))) / initSize;
			// original_err.println(freqs);
			// original_err.println(initSize + ", class0freq=" + class0ratio + ",
			// class0num=" + freqs.get(new Double(0.0)));

			// LoggerUtils.setDebugMode();
			Iterator<TrainSplit> it = new TrainSplitWrapper(new FoldedSplitter.Builder().numFolds(NR_FOLDS).stratify(true).seed(seed).build(dataset)); 
			//new StratifiedFoldedCalibSetIterator(dataset, NR_FOLDS, seed);
			Iterator<TrainSplit> it2 = new TrainSplitWrapper(new FoldedSplitter.Builder().numFolds(NR_FOLDS).stratify(true).seed(seed).build(dataset)); 
			// new StratifiedFoldedCalibSetIterator(dataset, NR_FOLDS, seed);
			TrainSplit split1 = null;

			int nrSplits = 0;
			while (it.hasNext()) {
				TrainSplit ts = it.next();
				TrainSplit ts2 = it2.next();
				nrSplits++;
				SubSet calibset = new SubSet();
				calibset.setRecords(ts.getCalibrationSet());
				// original.println(calibset.getLabelFrequencies().get(new Double(0))+ ", "+
				// calibset.size()+", " + class0ratio);
				SubSet prop = new SubSet();
				prop.setRecords(ts.getProperTrainingSet());
				// original.println(prop.getLabelFrequencies().get(new Double(0))+",
				// "+prop.size()+ ", " + class0ratio);
				Assert.assertTrue(Math.abs(initSize / NR_FOLDS + calibExclusiveSize - calibset.size()) <= 2);
				Assert.assertEquals(class0ratio,
						((double) calibset.getLabelFrequencies().get(Double.valueOf(0)) - calibExclusiveClass0)
								/ (calibset.size() - calibExclusiveSize),
						0.05);

				Assert.assertTrue(
						Math.abs(initSize * (NR_FOLDS - 1) / NR_FOLDS + modelExclusiveSize - prop.size()) <= 2);
				Assert.assertEquals(class0ratio,
						((double) prop.getLabelFrequencies().get(Double.valueOf(0)) - modelExclusiveClass0)
								/ (prop.size() - modelExclusiveSize),
						0.05);

				Assert.assertEquals(initSize + calibExclusiveSize + modelExclusiveSize, prop.size() + calibset.size());

				if (DO_EXTENSIVE_CHECK)
					assertEqualICPDatasets(ts, ts2);
				if (nrSplits == 1) {
					split1 = ts;
				} else {
					assertNOTEqualSplits(split1, ts);
				}
			}

			Assert.assertEquals(NR_FOLDS, nrSplits);

			Iterator<TrainSplit> it3 = new TrainSplitWrapper(new FoldedSplitter.Builder().numFolds(NR_FOLDS).stratify(true).build(dataset)); 
			// new StratifiedFoldedCalibSetIterator(dataset, NR_FOLDS);
			assertFoldedCalibrationSetupOK(it3, NR_FOLDS, dataset);
		}

		private void assertFoldedCalibrationSetupOK(Iterator<TrainSplit> iter, int numDatasets, Dataset problem)
				throws Exception {
			int numDatasetsReal = 0;

			List<DataRecord> glist = new ArrayList<>();

			while (iter.hasNext()) {
				TrainSplit trainSet = iter.next();
				numDatasetsReal++;

				Assert.assertEquals(problem.getNumRecords(),
						(trainSet.getCalibrationSet().size() + trainSet.getProperTrainingSet().size()));

				// Ensure none in calibration set is in global list
				// CCP folds should cover all dataset
				List<DataRecord> calibRecords = trainSet.getCalibrationSet();
				if (!problem.getCalibrationExclusiveDataset().isEmpty())
					calibRecords.removeAll(problem.getCalibrationExclusiveDataset());
				Assert.assertTrue(Collections.disjoint(glist, calibRecords));
				glist.addAll(calibRecords);

				if (DO_EXTENSIVE_CHECK)
					assertAllRecordsPreserved(problem, trainSet);

			}
			Assert.assertEquals(numDatasets, numDatasetsReal);
			Assert.assertEquals(glist.size(), problem.getDataset().size());
		}
	}

	@Category(UnitTest.class)
	public static class TestRandomSampling {

		private static final double CALIB_PART = 0.2;
		private static final int NUM_SAMPLES = 10;

		@Test
		public void TestStratifiedRandomSplit() throws Exception {
			// Read in problem from file
			Dataset problem = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS);

			int initSize = problem.getNumRecords();
			Map<Double, Integer> originalFreq = problem.getDataset().getLabelFrequencies();

			long seed = 500l;
			// LoggerUtils.setDebugMode();
			Iterator<TrainSplit> it = new TrainSplitWrapper(new RandomSplitter.Builder().stratify(true).splitRatio(CALIB_PART).seed(seed).numSplits(NUM_SAMPLES).build(problem));
			Iterator<TrainSplit> it2 = new TrainSplitWrapper(new RandomSplitter.Builder().stratify(true).splitRatio(CALIB_PART).seed(seed).numSplits(NUM_SAMPLES).build(problem));
			TrainSplit split1 = null;

			int nrSplits = 0;
			while (it.hasNext()) {
				TrainSplit ts = it.next();
				TrainSplit ts2 = it2.next();
				nrSplits++;

				TestDataSplitting.assertSameFreq(originalFreq, DataUtils.countLabels(ts.getCalibrationSet()), true);
				TestDataSplitting.assertSameFreq(originalFreq, DataUtils.countLabels(ts.getProperTrainingSet()), true); 

				Assert.assertEquals(initSize, ts.getCalibrationSet().size() + ts.getProperTrainingSet().size());
				Assert.assertTrue(Math.abs(initSize * CALIB_PART - ts.getCalibrationSet().size()) <= 2);

				if (DO_EXTENSIVE_CHECK)
					assertEqualICPDatasets(ts, ts2);
				if (nrSplits == 1) {
					split1 = ts;
				} else {
					assertNOTEqualSplits(split1, ts);
				}
			}

			Assert.assertEquals(NUM_SAMPLES, nrSplits);
		}

		@Test
		public void TestStratifiedRandomSplitMoreDatasets() throws Exception {
			// Read in problem from file
			Dataset problem = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS);

			problem.apply(new UseVoting()); 

			SubSet[] split_ds1 = problem.getDataset().splitRandom(.124);
			SubSet[] split_ds2 = split_ds1[1].splitRandom(0.353);
			problem.withCalibrationExclusiveDataset(split_ds1[0])
					.withModelingExclusiveDataset(split_ds2[0])
					.withDataset(split_ds2[1]);

			int initSize = problem.getDataset().size();
			int calibExclusiveSize = problem.getCalibrationExclusiveDataset().size();
			int calibExclusiveClass0 = problem.getCalibrationExclusiveDataset().getLabelFrequencies()
					.get(Double.valueOf(0));
			int modelExclusiveSize = problem.getModelingExclusiveDataset().size();
			int modelExclusiveClass0 = problem.getModelingExclusiveDataset().getLabelFrequencies()
					.get(Double.valueOf(0));
			Map<Double, Integer> freqs = problem.getDataset().getLabelFrequencies();
			double class0ratio = (double) (freqs.get(Double.valueOf(0))) / initSize;
			// System.err.println(freqs);
			// System.err.println(initSize + ", class0freq=" + class0ratio + ", class0num="
			// + freqs.get(new Double(0.0)));
			long seed = 500l;
			// LoggerUtils.setDebugMode();
			Iterator<TrainSplit> it = new TrainSplitWrapper(new RandomSplitter.Builder().stratify(true).splitRatio(CALIB_PART).seed(seed).numSplits(NUM_SAMPLES).build(problem));
			// new StratifiedRandomCalibSetIterator(problem, CALIB_PART, NUM_SAMPLES, 500l);
			Iterator<TrainSplit> it2 = new TrainSplitWrapper(new RandomSplitter.Builder().stratify(true).splitRatio(CALIB_PART).seed(seed).numSplits(NUM_SAMPLES).build(problem));
			// new StratifiedRandomCalibSetIterator(problem, CALIB_PART, NUM_SAMPLES, 500l);
			TrainSplit split1 = null;

			int nrSplits = 0;
			while (it.hasNext()) {
				TrainSplit ts = it.next();
				TrainSplit ts2 = it2.next();
				nrSplits++;
				SubSet calibset = new SubSet();
				calibset.setRecords(ts.getCalibrationSet());
				Assert.assertTrue(Math.abs(initSize * CALIB_PART + calibExclusiveSize - calibset.size()) <= 2);
				Assert.assertEquals(class0ratio,
						((double) calibset.getLabelFrequencies().get(Double.valueOf(0)) - calibExclusiveClass0)
								/ (calibset.size() - calibExclusiveSize),
						0.05);

				SubSet prop = new SubSet();
				prop.setRecords(ts.getProperTrainingSet());
				Assert.assertTrue(Math.abs(initSize * (1 - CALIB_PART) + modelExclusiveSize - prop.size()) <= 2);
				Assert.assertEquals(class0ratio,
						((double) prop.getLabelFrequencies().get(Double.valueOf(0)) - modelExclusiveClass0)
								/ (prop.size() - modelExclusiveSize),
						0.05);

				Assert.assertEquals(initSize + calibExclusiveSize + modelExclusiveSize, prop.size() + calibset.size());

				if (DO_EXTENSIVE_CHECK)
					assertEqualICPDatasets(ts, ts2);
				// System.out.println("round=" +nrSplits);
				if (nrSplits == 1) {
					split1 = ts;
				} else {
					assertNOTEqualSplits(split1, ts);
				}
			}

			Assert.assertEquals(NUM_SAMPLES, nrSplits);
		}

		@Test
		public void testRandomSamplingNumModels() throws Exception {
			Dataset data = TestDataLoader.getInstance().getDataset(true, true);

			ACPClassifier acp = new ACPClassifier(
					new ICPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC())),
					new RandomSampling(10, .2));

			acp.train(data);
			Assert.assertEquals(10, acp.getNumTrainedPredictors());
		}

		@Test
		public void TestRandomCalibrationUsingStaticSeed() throws Exception {
			Dataset data = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.REGRESSION_HOUSING);
			Dataset clonedData = data.clone();
			long seed = 1242L;

			Iterator<TrainSplit> datasets1 = new TrainSplitWrapper(new RandomSplitter.Builder().splitRatio(CALIB_PART).seed(seed).numSplits(NUM_SAMPLES).build(data));
			Iterator<TrainSplit> datasets2 = new TrainSplitWrapper(new RandomSplitter.Builder().splitRatio(CALIB_PART).seed(seed).numSplits(NUM_SAMPLES).build(clonedData));

			for (int i = 0; i < NUM_SAMPLES; i++) {
				TrainSplit ds1 = datasets1.next();
				TrainSplit ds2 = datasets2.next();

				assertEqualICPDatasets(ds1, ds2);
			}
		}

		@Test
		public void TestRandomTestsplitsWithExclusiveDatasets() throws Exception {
			Dataset data = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.REGRESSION_HOUSING);
			int initSize = data.getNumRecords(); // now all of them is in the model-and-calibrate dataset
			double splitFac = 0.2;
			SubSet[] ds_split1 = data.getDataset().splitStatic(splitFac);
			SubSet[] ds_split2 = ds_split1[1].splitRandom(splitFac);
			data.withDataset(ds_split2[1]); // this should be 64% of the data
			data.withCalibrationExclusiveDataset(ds_split1[0]); // this should be 20% of the data
			data.withModelingExclusiveDataset(ds_split2[0]); // this should be 16% of the data

			Iterator<TrainSplit> datasets1 = new TrainSplitWrapper(new RandomSplitter.Builder().splitRatio(CALIB_PART).numSplits(NUM_SAMPLES).build(data));
			// new RandomSplitIterator(problem, CALIB_PART, NUM_SAMPLES);
			int nrDs = 0;
			while (datasets1.hasNext()) {
				nrDs++;
				TrainSplit ds1 = datasets1.next();

				Assert.assertEquals(initSize, ds1.getCalibrationSet().size() + ds1.getProperTrainingSet().size());
				if (DO_EXTENSIVE_CHECK) {
					ds1.getCalibrationSet().containsAll(data.getCalibrationExclusiveDataset());
					ds1.getProperTrainingSet().containsAll(data.getModelingExclusiveDataset());
				}

			}
			Assert.assertEquals(NUM_SAMPLES, nrDs);
		}

	}

}

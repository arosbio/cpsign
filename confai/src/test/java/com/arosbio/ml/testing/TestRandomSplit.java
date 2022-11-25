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

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.splitting.TestDataSplitting;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.TestDataLoader;

@Category(UnitTest.class)
public class TestRandomSplit {


	@Test
	public void testFixedNumberTest() throws Exception {
		int nRecs = 44;
		int numTest = 10;
		SubSet d = TestDataLoader.getInstance().getDataset(true, true).getDataset().clone();
		d.shuffle();


		// Only use enough to make even partition 
		d = d.splitStatic(nRecs)[0];
		Assert.assertEquals(nRecs, d.size());
		SubSet original_cpy = d.clone();

		Dataset p = new Dataset();
		p.withDataset(d);

		TestingStrategy strat = new RandomSplit(numTest);
		Assert.assertEquals(1, strat.getNumberOfSplitsAndValidate(p));

		Set<DataRecord> allTestRecs = new HashSet<>();
		Set<DataRecord> allTrainRecs = new HashSet<>();

		Iterator<TestTrainSplit> iter = strat.getSplits(p);
		int num = 0;
		while (iter.hasNext()) {
			num++;
			TestTrainSplit split = iter.next();
			// Test records
			List<DataRecord> test = split.getTestSet();
			Assert.assertEquals(numTest, test.size());
			allTestRecs.addAll(test);

			allTrainRecs.addAll(split.getTrainingSet().getDataset() );
			//			Assert.assertEquals(test.size()+preAddingToSetSize, allTestRecs.size());
			// Train records
			Dataset train = split.getTrainingSet();
			Assert.assertTrue(train.getCalibrationExclusiveDataset().isEmpty());
			Assert.assertTrue(train.getModelingExclusiveDataset().isEmpty());
			Assert.assertEquals(nRecs-numTest, train.getNumRecords());

			for (DataRecord trec: test) {
				Assert.assertFalse("no test records should be in training data",train.getDataset() .contains(trec));
			}

		}

		try {
			iter.next();
			Assert.fail();
		} catch (Exception e) {}

		Assert.assertEquals(original_cpy .size(), allTestRecs.size() + allTrainRecs.size());
		allTestRecs.addAll(allTrainRecs);
		Assert.assertTrue(allTestRecs.containsAll(original_cpy ));
		Assert.assertEquals(1, num);

		Assert.assertEquals("Splitter should not change the original data",original_cpy, p.getDataset()); 

	}

	@Test
	public void testRatioOutsideAllowedRange() {
		try {
			new RandomSplit(0d);
			Assert.fail();
		} catch(IllegalArgumentException e) {}

		try {
			new RandomSplit(-1d*Math.random());
			Assert.fail();
		} catch(IllegalArgumentException e) {}


		try {
			new RandomSplit(1d);
			Assert.fail();
		} catch(IllegalArgumentException e) {}

		try {
			new RandomSplit(1d + 0.001 + Math.random()*5);
			Assert.fail();
		} catch(IllegalArgumentException e) {}
	}

	@Test
	public void testRatioTest() throws Exception {
		int nRecs = 44;
		double fractionTest = .1;
		SubSet d = TestDataLoader.getInstance().getDataset(true, true).getDataset().clone();
		d.shuffle();


		// Only use enough to make even partition 
		d = d.splitStatic(nRecs)[0];
		Assert.assertEquals(nRecs, d.size());
		SubSet original_cpy = d.clone();

		Dataset p = new Dataset();
		p.withDataset(d);

		TestingStrategy strat = new RandomSplit(fractionTest);
		Assert.assertEquals(1, strat.getNumberOfSplitsAndValidate(p));

		Set<DataRecord> allTestRecs = new HashSet<>();
		Set<DataRecord> allTrainRecs = new HashSet<>();

		Iterator<TestTrainSplit> iter = strat.getSplits(p);
		int num = 0;
		while (iter.hasNext()) {
			num++;
			TestTrainSplit split = iter.next();
			// Test records
			List<DataRecord> test = split.getTestSet();
			Assert.assertEquals(Math.round(fractionTest*nRecs), test.size());
			allTestRecs.addAll(test);
			allTrainRecs.addAll(split.getTrainingSet().getDataset() );
			//			Assert.assertEquals(test.size()+preAddingToSetSize, allTestRecs.size());
			// Train records
			Dataset train = split.getTrainingSet();
			Assert.assertTrue(train.getCalibrationExclusiveDataset().isEmpty());
			Assert.assertTrue(train.getModelingExclusiveDataset().isEmpty());
			Assert.assertEquals(nRecs, train.getNumRecords() + test.size());

			for (DataRecord trec: test) {
				Assert.assertFalse("no test records should be in training data",train.getDataset() .contains(trec));
			}

		}

		try {
			iter.next();
			Assert.fail();
		} catch (Exception e) {}

		Assert.assertEquals(original_cpy .size(), allTestRecs.size() + allTrainRecs.size());
		allTestRecs.addAll(allTrainRecs);
		Assert.assertTrue(allTestRecs.containsAll(original_cpy ));
		Assert.assertEquals(1, num);

		Assert.assertEquals("Splitter should not change the original data",original_cpy, p.getDataset()); 

	}

	@Test
	public void testStratified() throws Exception {
		int n = 2;
		RandomSplit tester = new RandomSplit();
		tester.withNumRepeat(n);

		Assert.assertFalse("default is NOT to stratify",tester.isStratified());

		tester.withStratify(true);
		Assert.assertTrue("test setter",tester.isStratified());

		Dataset original = TestDataLoader.getInstance().getDataset(true, true);
		TestKFoldCV.makeImbalanced(original);
		SubSet original_cpy = original.getDataset().clone();
		int numRecs = original.getNumRecords();

		Map<Double,Integer> originalFreq = original_cpy.getLabelFrequencies();

		Iterator<TestTrainSplit> splitsIter = tester.getSplits(original);
		int count = 0;

		while (splitsIter.hasNext()) {
			TestTrainSplit split = splitsIter.next();
			count++;

			List<DataRecord> test = split.getTestSet();
			Dataset train = split.getTrainingSet();
			// System.err.println("test: " + new SubSet(test).getLabelFrequencies());
			// System.err.println("train: " + train.getDataset().getLabelFrequencies());

			Assert.assertEquals(numRecs, test.size() + train.getNumRecords());
			for (DataRecord trec : test) {
				Assert.assertFalse("no test records should be in training data",
						train.getDataset().contains(trec));
			}

			Assert.assertEquals("Make sure no records are duplicated in training set either",
					train.getNumRecords(), new HashSet<>(train.getDataset() ).size());

			// Frequencies should be consistent
			TestDataSplitting.assertSameFreq(originalFreq, DataUtils.countLabels(test), false);
			TestDataSplitting.assertSameFreq(originalFreq, train.getLabelFrequencies(), false);
			TestKFoldCV.assertKeepsOriginalStratas(originalFreq, new SubSet(test));
			TestKFoldCV.assertKeepsOriginalStratas(originalFreq, train.getDataset());

		}
		Assert.assertEquals(n, count);

		Assert.assertEquals("Splitter should make no changes to original ds",original_cpy, original.getDataset()); 
	}

}

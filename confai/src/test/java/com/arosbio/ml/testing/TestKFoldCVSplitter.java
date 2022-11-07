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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.TestDataLoader;

@Category(UnitTest.class)
public class TestKFoldCVSplitter {

	@Test
	public void testKlessThan2() {
		try {
			new KFoldCV(1);
			Assert.fail();
		} catch (IllegalArgumentException e) {}
	}

	@Test
	public void testKgreaterThanN() throws Exception {
		SubSet d = TestDataLoader.getInstance().getDataset(true, true).getDataset().clone();
		SubSet d10 = d.splitStatic(10)[0];
		Assert.assertEquals(10, d10.size());

		Dataset p = new Dataset();
		p.setDataset(d10);
		KFoldCV splitter = new KFoldCV(11);

		try {
			splitter.getSplits(p);
			Assert.fail();
		} catch(IllegalArgumentException e) {}
	}

	@Test
	public void testEvenSplits() throws Exception {
		int k = 11, numPerSplit=2;
		SubSet d = TestDataLoader.getInstance().getDataset(true, true).getDataset().clone();
		d.shuffle();
		// Only use enough to make even partion 
		d = d.splitStatic(k*numPerSplit)[0];
		Assert.assertEquals(k*numPerSplit, d.size());
		SubSet original_cpy = d.clone();

		Dataset p = new Dataset();
		p.setDataset(d);

		TestingStrategy strat = new KFoldCV(k);
		Assert.assertEquals(k, strat.getNumberOfSplitsAndValidate(p));

		Set<DataRecord> allTestRecs = new HashSet<>();

		Iterator<TestTrainSplit> iter = strat.getSplits(p);
		int num = 0;
		while (iter.hasNext()) {
			num++;
			TestTrainSplit split = iter.next();
			// Test records
			List<DataRecord> test = split.getTestSet();
			int preAddingToSetSize = allTestRecs.size();
			allTestRecs.addAll(test);
			Assert.assertEquals(test.size()+preAddingToSetSize, allTestRecs.size());
			Assert.assertEquals(numPerSplit, test.size());
			// Train records
			Dataset train = split.getTrainingSet();
			Assert.assertTrue(train.getCalibrationExclusiveDataset().isEmpty());
			Assert.assertTrue(train.getModelingExclusiveDataset().isEmpty());
			Assert.assertEquals((k-1)*numPerSplit, train.getNumRecords());

			for (DataRecord trec: test) {
				Assert.assertFalse("no test records should be in training data",train.getDataset().contains(trec));
			}

		}

		try {
			iter.next();
			Assert.fail();
		} catch (Exception e) {}

		Assert.assertEquals(original_cpy.size(), allTestRecs.size());
		Assert.assertTrue(allTestRecs.containsAll(original_cpy));
		Assert.assertEquals(k, num);

		Assert.assertEquals("Splitter should not change the original data",original_cpy, p.getDataset()); 

	}

	@Test
	public void testUnevenSplits() throws Exception {
		int k = 11, numPerSplit=2, numExtra = 3, totalSize = k*numPerSplit + numExtra;
		SubSet d = TestDataLoader.getInstance().getDataset(true, true).getDataset().clone();
		d.shuffle();
		// Use 2 per split + 3 that doesn't add up!
		d = d.splitStatic(totalSize)[0];
		Assert.assertEquals(totalSize, d.size());
		SubSet original_cpy = d.clone();

		Dataset p = new Dataset();
		p.setDataset(d);

		TestingStrategy strat = new KFoldCV(k);
		Assert.assertEquals(k, strat.getNumberOfSplitsAndValidate(p));

		Set<DataRecord> allTestRecs = new HashSet<>();
		List<Integer> testSizes = new ArrayList<>();

		Iterator<TestTrainSplit> iter = strat.getSplits(p);
		int num = 0, 
				max_test_size= Integer.MIN_VALUE, 
				min_test_size = Integer.MAX_VALUE;
		while (iter.hasNext()) {
			num++;
			TestTrainSplit split = iter.next();
			// Test records
			List<DataRecord> test = split.getTestSet();
			max_test_size = Math.max(max_test_size, test.size());
			min_test_size = Math.min(min_test_size, test.size());
			testSizes.add(test.size());
			int preAddingToSetSize = allTestRecs.size();
			allTestRecs.addAll(test);
			Assert.assertEquals(test.size()+preAddingToSetSize, allTestRecs.size());
			//			Assert.assertEquals(numPerSplit, test.size(), 1);
			// Train records
			Dataset train = split.getTrainingSet();
			Assert.assertTrue(train.getCalibrationExclusiveDataset().isEmpty());
			Assert.assertTrue(train.getModelingExclusiveDataset().isEmpty());
			Assert.assertEquals(totalSize, train.getNumRecords()+test.size());

			for (DataRecord trec: test) {
				Assert.assertFalse("no test records should be in training data",train.getDataset().contains(trec));
			}

		}
		Assert.assertEquals(original_cpy.size(), allTestRecs.size());

		Assert.assertEquals(numPerSplit, min_test_size);
		Assert.assertEquals(numPerSplit+1, max_test_size);

		try {
			iter.next();
			Assert.fail();
		} catch (Exception e) {}

		Assert.assertEquals(k, num);

		Assert.assertEquals("Splitter should not change the original data",original_cpy, p.getDataset()); 

	}

	@Test
	public void testStratified() throws Exception {
		int k = 5;
		int n = 2;
		KFoldCV tester = new KFoldCV(k);
		tester.setNumRepeat(n);

		Assert.assertFalse("default is NOT to stratify",tester.isStratified());

		tester.setStratified(true);
		Assert.assertTrue("test setter",tester.isStratified());

		Dataset original = TestDataLoader.getInstance().getDataset(true, true);
		makeImbalanced(original);
		SubSet original_cpy = original.getDataset().clone();
		int numRecs = original.getNumRecords();

		Map<Double,Integer> originalFreq = original_cpy.getLabelFrequencies();
		//		System.err.println(originalFreq);
		//		System.err.println(original_cpy.size());

		Iterator<TestTrainSplit> splitsIter = tester.getSplits(original);
		int count = 0;
		while (splitsIter.hasNext()) {
			TestTrainSplit split = splitsIter
					.next();
			count++;

			List<DataRecord> test = split.getTestSet();
			Dataset train = split.getTrainingSet();
			//			System.err.println("test: " + new SubSet(test).getLabelFrequencies());
			//			System.err.println("train: " + train.getDataset().getLabelFrequencies());

			Assert.assertEquals(numRecs, test.size() + train.getNumRecords());
			for (DataRecord trec: test) {
				Assert.assertFalse("no test records should be in training data",
						train.getDataset() .contains(trec));
			}

			Assert.assertEquals("Make sure no records are duplicated in training set either",
					train.getNumRecords(), new HashSet<>(train.getDataset() ).size());

			// Frequencies should be consistent
			assertKeepsOriginalStratas(originalFreq, new SubSet(test));
			assertKeepsOriginalStratas(originalFreq, train.getDataset());

		}
		Assert.assertEquals(k*n, count);

		Assert.assertEquals("Splitter should make no changes to original ds",original_cpy, original.getDataset()); 
	}

	public static void assertKeepsOriginalStratas(Map<Double,Integer> originalFreqs, SubSet split) {
		int origCount = CollectionUtils.sumInts(originalFreqs.values());
		Map<Double,Integer> splitFreqs = split.getLabelFrequencies();

		for (double label : originalFreqs.keySet()) {
			double origFreq = ((double) originalFreqs.get(label)) / origCount;
			double splitFreq = ((double) splitFreqs.get(label)) /split.size();
			Assert.assertEquals(origFreq, splitFreq, 1d/split.size());
		}
	}


	public static void makeImbalanced(Dataset p) {
		SubSet d = p.getDataset();
		int s = d.size();
		int numToShift = (int) (s * .3);

		Random rng = new Random(); 
		for (int i=0; i<numToShift; i++) {
			d .get(rng.nextInt(s)).setLabel(0);;
		}
	}

	@Test
	public void testNoShuffle() throws Exception {
		
		Dataset original = TestDataLoader.getInstance().getDataset(true, true);
		SubSet original_cpy = original.getDataset().clone();
		int numRecs = original.getNumRecords();
		
		int k = 5;
		KFoldCV tester = new KFoldCV(k);
		Assert.assertTrue(tester.usesShuffle());
		tester.setShuffle(false);
		Assert.assertFalse(tester.usesShuffle());
		
		tester.setNumRepeat(10);
		try {
			tester.getSplits(original);
			Assert.fail();
		} catch (IllegalArgumentException e) {}

		tester.setNumRepeat(1);

		Iterator<TestTrainSplit> splitsIter = tester.getSplits(original);
		int count = 0;
		while (splitsIter.hasNext()) {
			TestTrainSplit split = splitsIter
					.next();
			count++;

			List<DataRecord> test = split.getTestSet();
			Dataset train = split.getTrainingSet();
			assertSameOrder(original_cpy , test);

			Assert.assertEquals(numRecs, test.size() + train.getNumRecords());
			for (DataRecord trec: test) {
				Assert.assertFalse("no test records should be in training data",
						train.getDataset() .contains(trec));
			}

			Assert.assertEquals("Make sure no records are duplicated in training set either",
					train.getNumRecords(), new HashSet<>(train.getDataset() ).size());

		}
		Assert.assertEquals(k, count);



		Assert.assertEquals("Splitter should make no changes to original ds",original_cpy, original.getDataset());
	}

	public static void assertSameOrder(List<DataRecord> template, List<DataRecord> test) {
		for (int i=0; i<template.size(); i++) {
			if (template.get(i).equals(test.get(0))) {
				
				Assert.assertEquals(template.subList(i, i+test.size()), test);
				// Here we've done all matchings and can return
				return;
			}
		}
	}

}

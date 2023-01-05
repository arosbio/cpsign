/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.splitting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.SparseVector;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.TestEnv;


@Category(UnitTest.class)
@RunWith(Enclosed.class)
public class TestDataSplitting extends TestEnv {

	private final static boolean PRINT_DEBUG = false;

	/**
	 * Assertion method for checking that stratified splitting is OK
	 * @param f1
	 * @param f2
	 * @param evenlyDivisible
	 */
	public static void assertSameFreq(Map<Double,Integer> f1, Map<Double,Integer> f2, boolean evenlyDivisible){
		Assert.assertEquals(f1.size(), f2.size());
		Assert.assertEquals(f1.keySet(), f2.keySet());
		int tot1 = CollectionUtils.sumInts(f1.values()), tot2 = CollectionUtils.sumInts(f2.values());
		double percentageF2 = ((double) tot2) / tot1;
		int numAllowedDiff = evenlyDivisible ? 2 : 3; // 

		for(Double label : f1.keySet()) {
			int expectedNumIn2 = Math.round( (float)(f1.get(label)*percentageF2));
			int actualDeviance = Math.abs(f2.get(label) - expectedNumIn2);
			Assert.assertTrue("actual: "+ actualDeviance + " vs allowed: " + numAllowedDiff + " inputs: f1="+f1 + ", f2="+f2,
				actualDeviance <= numAllowedDiff);
			
		}

	}

	private static final int NUM_FOLDS = 10;
	private static final int NUM_SPLITS = 10;
	private static final double RATIO_SECOND = 0.2;

	@Category(UnitTest.class)
	public static class TestFolded {

		
		@Test
		public void foldedSplitting() throws IOException {
			Dataset originalData = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_100);
			Dataset firstData = originalData.clone();
			Dataset secondData = originalData.clone();

			Assert.assertEquals(firstData, secondData);
			
			Assert.assertEquals(100, originalData.getNumRecords());

			long seed = System.currentTimeMillis();

			FoldedSplitter splitter = new FoldedSplitter.Builder()
				.findLabelRange(true)
				.numFolds(NUM_FOLDS)
				.stratify(false)
				.seed(seed)
				.build(firstData);
			FoldedSplitter splitter2 = new FoldedSplitter.Builder()
				.findLabelRange(true)
				.numFolds(NUM_FOLDS)
				.stratify(false)
				.seed(seed)
				.build(secondData);

			// LoggerUtils.setDebugMode(SYS_ERR);
			doCheckFoldedSplits(originalData, splitter, splitter2, false);

			
		}
		
		@Test
		public void foldedStratifiedSplitting() throws IOException {
			Dataset originalDataset = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_100);
			
			Assert.assertEquals(100, originalDataset.getNumRecords());

			long seed = System.currentTimeMillis();
			Dataset firstDataset = originalDataset.clone();
			Dataset secondDataset = originalDataset.clone();
			FoldedSplitter splitter = new FoldedSplitter.Builder()
				.findLabelRange(true)
				.numFolds(NUM_FOLDS)
				.stratify(true)
				.seed(seed)
				.build(firstDataset);
			FoldedSplitter splitter2 = new FoldedSplitter.Builder()
				.findLabelRange(true)
				.numFolds(NUM_FOLDS)
				.stratify(true)
				.seed(seed)
				.build(secondDataset);

			doCheckFoldedSplits(originalDataset, splitter, splitter2, true);
			

			// Check that regression data should fail
			try {
				new FoldedSplitter.Builder()
				.numFolds(NUM_SPLITS)
				.seed(seed)
				.stratify(true)
				.build(TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.REGRESSION_HOUSING));
				Assert.fail("Regression data not allowed for stratified splitting");
			} catch (IllegalArgumentException e){
			}
		}

		
		@Test
		public void testToyDataset10() throws Exception {

			SubSet data = new SubSet();
			// 8 of class 0
			data.add(new DataRecord(0d, new SparseVector()));
			data.add(new DataRecord(0d, new SparseVector()));
			data.add(new DataRecord(0d, new SparseVector()));
			data.add(new DataRecord(0d, new SparseVector()));
			data.add(new DataRecord(0d, new SparseVector()));
			data.add(new DataRecord(0d, new SparseVector()));
			data.add(new DataRecord(0d, new SparseVector()));
			data.add(new DataRecord(0d, new SparseVector()));
			// 2 of class 1
			data.add(new DataRecord(1d, new SparseVector()));
			data.add(new DataRecord(1d, new SparseVector()));

			Assert.assertEquals(10, data.size());
			FoldedSplitter splitter = new FoldedSplitter.Builder()
				.stratify(true)
				.numFolds(5)
				.build(new Dataset().withDataset(data));

			while (splitter.hasNext()){
				DataSplit split = splitter.next();
				Map<Double,Integer> freq1 = split.getFirst().getLabelFrequencies();
				Map<Double,Integer> freq2 = DataUtils.countLabels(split.getSecond());
				
				// The folds are evenly divisible with 10 examples - 2 in each of the 5 folds
				Assert.assertEquals(8, CollectionUtils.sumInts(freq1.values()));
				Assert.assertEquals(2, CollectionUtils.sumInts(freq2.values()));

				// should be 0 or 1 of the minority class in each of the test-splits
				Assert.assertTrue(freq2.get(1.)== null || freq2.get(1.) <= 1);
				// should be 1 or 2 of minority in the train-splits
				Assert.assertTrue(1<= freq1.get(1.) && freq1.get(1.) <= 2);
			}

		}

		@Test
		public void testToyDataset14() throws Exception {

			// Construct data set with 14 records, 3/14 ~ 21.4% of class 1, remaining from class 0

			SubSet data = new SubSet();
			// 11 of class 0
			data.add(new DataRecord(0d, new SparseVector()));
			data.add(new DataRecord(0d, new SparseVector()));
			data.add(new DataRecord(0d, new SparseVector()));
			data.add(new DataRecord(0d, new SparseVector()));
			data.add(new DataRecord(0d, new SparseVector()));
			data.add(new DataRecord(0d, new SparseVector()));
			data.add(new DataRecord(0d, new SparseVector()));
			data.add(new DataRecord(0d, new SparseVector()));
			data.add(new DataRecord(0d, new SparseVector()));
			data.add(new DataRecord(0d, new SparseVector()));
			data.add(new DataRecord(0d, new SparseVector()));
			// 3 of class 1
			data.add(new DataRecord(1d, new SparseVector()));
			data.add(new DataRecord(1d, new SparseVector()));
			data.add(new DataRecord(1d, new SparseVector()));
			
			Assert.assertEquals(14, data.size());
		
			FoldedSplitter splitter = new FoldedSplitter.Builder()
				.stratify(true)
				.numFolds(5)
				.build(new Dataset().withDataset(data));

			while (splitter.hasNext()){
				DataSplit split = splitter.next();
				Map<Double,Integer> freq1 = split.getFirst().getLabelFrequencies();
				Map<Double,Integer> freq2 = DataUtils.countLabels(split.getSecond());

				// all records should be part in the splits
				Assert.assertEquals(14, split.getSecond().size() + split.getFirst().size()); 
				// Check number in each split should be 2 or 3 in each "test split"
				Assert.assertTrue(2 == split.getSecond().size() || 3 == split.getSecond().size());
				
				// 21.4% of minority class, with 11 or 12 in the first split
				Assert.assertTrue(2<=freq1.get(1.) || freq1.get(1.) <= 3); // 2 or 3 from the minority class in second split
				Assert.assertTrue(freq1.toString(), 8<=freq1.get(0.) && freq1.get(0.) <=9 ); // 8 or 9 of majority in first split


				// 21.4% of minority class, with 2 or 3 in the second split
				Assert.assertTrue(freq2.get(1.)== null || freq2.get(1.) <= 1); // 0 or 1 from the minority class in second split
				Assert.assertTrue(freq2.toString(), 2<=freq2.get(0.) && freq2.get(0.) <=3 ); // 2 or 3 of majority in second split

			}

		}
		

		private void doCheckFoldedSplits(Dataset originalData, FoldedSplitter splitter, FoldedSplitter splitter2, boolean isStratified) {
			List<DataRecord> allSecondSplitRecs = new ArrayList<>();
			Map<Double, Integer> initialFreq = originalData.getDataset().getLabelFrequencies();
			
			int fold = 0;
			while (splitter.hasNext()) {

				DataSplit split = splitter.next();

				Assert.assertEquals("All data should be part of the output",
					originalData.getDataset().size(),
					split.getFirst().getNumRecords() + split.getSecond().size());
				// Should be no exclusive data 
				Assert.assertTrue(split.getFirst().getCalibrationExclusiveDataset().isEmpty());
				Assert.assertTrue(split.getFirst().getModelingExclusiveDataset().isEmpty());

				allSecondSplitRecs.addAll(split.getSecond());

				// Make sure the get(fold) returns the same thing as the iterator
				DataSplit getSplit = splitter.get(fold), 
					getAgain = splitter.get(fold);

				Assert.assertEquals(split.getSecond(), getSplit.getSecond());
				Assert.assertEquals(split.getSecond(), getAgain.getSecond());
				Assert.assertEquals(split.getFirst().getDataset(), getSplit.getFirst().getDataset());

				// Make sure frequency is correct!
				if (isStratified) {
					Map<Double,Integer> freqFirst = split.getFirst().getLabelFrequencies();
					assertSameFreq(initialFreq, freqFirst, false);
					Map<Double,Integer> freqSecond = DataUtils.countLabels(split.getSecond());
					assertSameFreq(initialFreq, freqSecond, false);
					if (PRINT_DEBUG){
						System.out.printf("Initial freq %s first: %s (%s) second: %s (%s)%n",
							initialFreq,freqFirst,CollectionUtils.sumInts(freqFirst.values()),freqSecond, CollectionUtils.sumInts(freqSecond.values()));
					}
				}
				// Check with a second splitter to make sure everything is still the same!
				DataSplit split2 = splitter2.get(fold);
				Assert.assertEquals(split.getSecond(), split2.getSecond());
				Assert.assertEquals(split.getFirst(), split2.getFirst());

				fold++;
			}

			// Make sure all records will end up in the calibration-set at one time!
			Assert.assertEquals(originalData.getNumRecords(), allSecondSplitRecs.size());
			for(DataRecord rec : originalData.getDataset())
				allSecondSplitRecs.remove(rec);
			Assert.assertTrue(allSecondSplitRecs.isEmpty());

			try {
				splitter.get(NUM_FOLDS);
				Assert.fail();
			} catch (NoSuchElementException e) {
				Assert.assertTrue(e.getMessage().toLowerCase().contains("invalid"));
			}
			try {
				splitter.get(-1);
				Assert.fail();
			} catch (NoSuchElementException e) {
				Assert.assertTrue(e.getMessage().toLowerCase().contains("invalid"));
			}
		}

	}

	@Category(UnitTest.class)
	public static class TestRandom {

		@Test
		public void randomSplitting() throws IOException {
			Dataset original = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_100);
			Assert.assertEquals(100, original.getNumRecords());

			long seed = System.currentTimeMillis();
			Dataset firstDataset = original.clone();
			Dataset secondDataset = original.clone();
			RandomSplitter splitter = new RandomSplitter.Builder().splitRatio(RATIO_SECOND).numSplits(NUM_SPLITS).seed(seed).build(firstDataset);
			RandomSplitter splitter2 = new RandomSplitter.Builder().splitRatio(RATIO_SECOND).numSplits(NUM_SPLITS).seed(seed).build(secondDataset);

			doCheckRandomSplits(original, splitter, splitter2, false);
		}


		@Test
		public void randomStratifiedSplitting() throws IOException {
			Dataset original = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_100);
			Assert.assertEquals(100, original.getNumRecords());

			long seed = System.currentTimeMillis();
			Dataset firstDataset = original.clone();
			Dataset secondDataset = original.clone();
			RandomSplitter splitter = new RandomSplitter.Builder()
				.splitRatio(RATIO_SECOND)
				.numSplits(NUM_SPLITS)
				.seed(seed)
				.stratify(true)
				.build(firstDataset);
			RandomSplitter splitter2 = new RandomSplitter.Builder()
				.splitRatio(RATIO_SECOND)
				.numSplits(NUM_SPLITS)
				.seed(seed)
				.stratify(true)
				.build(secondDataset);


			doCheckRandomSplits(original, splitter, splitter2, true);

			// Check that regression data should fail
			try {
				new RandomSplitter.Builder()
				.splitRatio(RATIO_SECOND)
				.numSplits(NUM_SPLITS)
				.seed(seed)
				.stratify(true)
				.build(TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.REGRESSION_HOUSING));
				Assert.fail("Regression data not allowed for stratified splitting");
			} catch (IllegalArgumentException e){
			}
		}

		private void doCheckRandomSplits(Dataset prob, RandomSplitter splitter, RandomSplitter splitter2, boolean isStratified) {

			Map<Double, Integer> originalFreq = prob.getDataset().getLabelFrequencies();
			int index = 0;
			while(splitter.hasNext()) {
				DataSplit split = splitter.next();
				Assert.assertEquals(prob.getDataset().size(), split.getFirst().size()+split.getSecond().size());

				// Make sure the get(fold) returns the same thing as the iterator
				DataSplit getSplit = splitter.get(index);
				// LoggerUtils.setDebugMode(SYS_ERR);
				Assert.assertTrue(DataUtils.equals(split.getFirst(), getSplit.getFirst()));
				Assert.assertEquals(split.getSecond(), getSplit.getSecond());

				// Make sure frequency is correct!
				if (isStratified) {
					Map<Double,Integer> freqFirst = split.getFirst().getLabelFrequencies();
					Map<Double, Integer> freqSecond = DataUtils.countLabels(split.getSecond());
					assertSameFreq(originalFreq, freqSecond, true);
					assertSameFreq(originalFreq, freqFirst, true);
					if (PRINT_DEBUG){
						System.out.printf("Initial freq %s first: %s (%s) second: %s (%s)%n",
						originalFreq,freqFirst,CollectionUtils.sumInts(freqFirst.values()),freqSecond, CollectionUtils.sumInts(freqSecond.values()));
					}
				}

				// Check with a second splitter to make sure everything is still the same!
				DataSplit split2 = splitter2.get(index);
				Assert.assertEquals(split.getSecond(), split2.getSecond());
				Assert.assertEquals(split.getFirst(), split2.getFirst());
				Assert.assertEquals(split.getSeed(), split2.getSeed());

				index++;
			}

			try {
				splitter.get(NUM_SPLITS);
				Assert.fail();
			} catch (NoSuchElementException e) {
				Assert.assertTrue(e.getMessage().contains("Cannot get index"));
			}
			try {
				splitter.get(-1);
				Assert.fail();
			} catch (NoSuchElementException e) {
				Assert.assertTrue(e.getMessage().contains("Cannot get index"));
			}

			// Make sure that different models are not the same dataset!
			for (int i=0; i<NUM_SPLITS-1; i++) {
				for (int j=i+1; j<NUM_SPLITS; j++) {
					DataSplit sp1 = splitter.get(i);
					DataSplit sp2 = splitter.get(j);
					Assert.assertNotEquals(sp1.getSecond(), sp2.getSecond());
					Assert.assertFalse(DataUtils.equals(sp1.getFirst(), sp2.getFirst()));
				}
			}
		}
	}

	@Category(UnitTest.class)
	public static class TestLOOCVSplitter {
	

		@Test
		public void testSplitsAreCorrect() throws Exception {
			int nRecs = 10;
			SubSet d = TestDataLoader.getInstance().getDataset(true, true).getDataset();
			d.shuffle();
			
			
			// Only use enough to make even partition 
			d = d.splitStatic(nRecs)[0];
			Assert.assertEquals(nRecs, d.size());
			SubSet original_cpy = d.clone();

			Dataset p = new Dataset();
			p.withDataset(d);

			Set<DataRecord> allTestRecs = new HashSet<>();

			Iterator<DataSplit> iter = new LOOSplitter.Builder().shuffle(true).build(p);
			int num = 0;
			while (iter.hasNext()) {
				num++;
				DataSplit split = iter.next();
				// Test records
				List<DataRecord> test = split.getSecond();
				Assert.assertEquals(1, test.size());
				allTestRecs.addAll(test);
				// Train records
				Dataset train = split.getFirst();
				Assert.assertTrue(train.getCalibrationExclusiveDataset().isEmpty());
				Assert.assertTrue(train.getModelingExclusiveDataset().isEmpty());
				Assert.assertEquals(nRecs-1, train.getNumRecords());

				for (DataRecord trec : test) {
					Assert.assertFalse("no test records should be in training data",train.getDataset().contains(trec));
				}

			}

			try {
				iter.next();
				Assert.fail();
			} catch (Exception e) {}

			Assert.assertEquals(original_cpy.size(), allTestRecs.size());
			Assert.assertTrue(allTestRecs.containsAll(original_cpy));
			Assert.assertEquals(nRecs, num);

			Assert.assertEquals("Splitter should not change the original data",original_cpy, p.getDataset()); 

		}


	}



}

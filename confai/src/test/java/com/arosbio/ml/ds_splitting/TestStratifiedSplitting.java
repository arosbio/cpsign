/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.ds_splitting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.ml.sampling.FoldedCalibSetIterator;
import com.arosbio.ml.sampling.RandomCalibSetIterator;
import com.arosbio.ml.sampling.StratifiedFoldedCalibSetIterator;
import com.arosbio.ml.sampling.StratifiedRandomCalibSetIterator;
import com.arosbio.ml.sampling.TrainSplit;
import com.arosbio.ml.sampling.TrainSplitIterator;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.TestEnv;


@Category(UnitTest.class)
public class TestStratifiedSplitting extends TestEnv{

	int numFolds = 10;
	int numModels = 10;
	double calibrationPart = 0.2;
	
	@Test
	public void foldedStratifiedSplitting() throws IOException {
		Dataset prob = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_100);
		
		Assert.assertEquals(100, prob.getNumRecords());

		long seed = System.currentTimeMillis();
		Dataset secondProblem = prob.clone();
		StratifiedFoldedCalibSetIterator splitter = new StratifiedFoldedCalibSetIterator(prob, numFolds, seed);
		StratifiedFoldedCalibSetIterator splitter2 = new StratifiedFoldedCalibSetIterator(secondProblem, numFolds, seed);

		doCheckFoldedSplits(prob, splitter, splitter2, true);
	}
	
	@Test
	public void foldedSplitting() throws IOException {
		Dataset prob = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_100);
		
		Assert.assertEquals(100, prob.getNumRecords());

		long seed = System.currentTimeMillis();
		Dataset secondProblem = prob.clone();
		TrainSplitIterator splitter = new FoldedCalibSetIterator(prob, numFolds, seed);
		TrainSplitIterator splitter2 = new FoldedCalibSetIterator(secondProblem, numFolds, seed);

		doCheckFoldedSplits(prob, splitter, splitter2, false);
	}

	private void doCheckFoldedSplits(Dataset prob, TrainSplitIterator splitter, TrainSplitIterator splitter2, boolean isStratified) {
		List<DataRecord> allCalibRecs = new ArrayList<>();
		Map<Double, Integer> freqs = prob.getDataset().getLabelFrequencies();
		
		int fold = 0;
		while(splitter.hasNext()) {
			TrainSplit split = splitter.next();
			Assert.assertEquals(prob.getDataset().size(), split.getCalibrationSet().size()+split.getProperTrainingSet().size());

			allCalibRecs.addAll(split.getCalibrationSet());

			// Make sure the get(fold) returns the same thing as the iterator
			TrainSplit getSplit = splitter.get(fold);
			Assert.assertEquals(split.getCalibrationSet(), getSplit.getCalibrationSet());
			Assert.assertEquals(split.getProperTrainingSet(), getSplit.getProperTrainingSet());

			// Make sure frequency is correct!
			if(isStratified) {
				Map<Double, Integer> freqsCalib = new SubSet(split.getCalibrationSet()).getLabelFrequencies();
				Map<Double, Integer> freqsProp = new SubSet(split.getProperTrainingSet()).getLabelFrequencies();
				for(Double label: freqs.keySet()) {
					Assert.assertTrue(Math.abs(freqsCalib.get(label) - freqs.get(label)/numFolds)<=1);
					Assert.assertTrue(Math.abs(freqsProp.get(label) - freqs.get(label)*(numFolds-1)/numFolds)<=1);
				}
			}
			// Check with a second splitter to make sure everything is still the same!
			TrainSplit split2 = splitter2.get(fold);
			Assert.assertEquals(split.getCalibrationSet(), split2.getCalibrationSet());
			Assert.assertEquals(split.getProperTrainingSet(), split2.getProperTrainingSet());

			fold++;
		}

		// Make sure all records will end up in the calibration-set at one time!
		Assert.assertEquals(prob.getNumRecords(), allCalibRecs.size());
		for(DataRecord rec: prob.getDataset())
			allCalibRecs.remove(rec);
		Assert.assertTrue(allCalibRecs.isEmpty());

		try {
			splitter.get(numFolds);
			Assert.fail();
		} catch (NoSuchElementException e) {
			Assert.assertTrue(e.getMessage().contains("Cannot get fold"));
			//			original_err.println(e.getMessage());
		}
		try {
			splitter.get(-1);
			Assert.fail();
		} catch (NoSuchElementException e) {
			Assert.assertTrue(e.getMessage().contains("Cannot get fold"));
			//			original_err.println(e.getMessage());
		}
	}

	@Test
	public void randomSplitting() throws IOException {
		Dataset prob = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_100);
		Assert.assertEquals(100, prob.getNumRecords());
		//		original.println(prob.getNumRecords());
		//		original.println(prob.getDataset().getLabelFrequencies());
		//		Map<Double, Integer> freqs = prob.getDataset().getLabelFrequencies();

		long seed = System.currentTimeMillis();
		Dataset secondProblem = prob.clone();
		TrainSplitIterator splitter = new RandomCalibSetIterator(prob, calibrationPart,numModels,seed);
		TrainSplitIterator splitter2 = new RandomCalibSetIterator(secondProblem, calibrationPart,numModels, seed);

		doCheckRandomSplits(prob, splitter, splitter2, false);
	}


	@Test
	public void randomStratifiedSplitting() throws IOException {
		Dataset prob = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_100);
		Assert.assertEquals(100, prob.getNumRecords());
		//		original.println(prob.getNumRecords());
		//		original.println(prob.getDataset().getLabelFrequencies());
		//		Map<Double, Integer> freqs = prob.getDataset().getLabelFrequencies();

		long seed = System.currentTimeMillis();
		Dataset secondProblem = prob.clone();
		StratifiedRandomCalibSetIterator splitter = new StratifiedRandomCalibSetIterator(prob, calibrationPart,numModels,seed);
		StratifiedRandomCalibSetIterator splitter2 = new StratifiedRandomCalibSetIterator(secondProblem, calibrationPart,numModels, seed);

		doCheckRandomSplits(prob, splitter, splitter2, true);

		//		int fold = 0;
		//		while(splitter.hasNext()) {
		//			TrainingsetSplit split = splitter.next();
		//			Assert.assertEquals(prob.getDataset().size(), split.getCalibrationSet().size()+split.getProperTrainingSet().size());
		//			//			original.println(split);
		//
		//			// Make sure the get(fold) returns the same thing as the iterator
		//			TrainingsetSplit getSplit = splitter.get(fold);
		//			Assert.assertEquals(split.getCalibrationSet(), getSplit.getCalibrationSet());
		//			Assert.assertEquals(split.getProperTrainingSet(), getSplit.getProperTrainingSet());
		//
		//			// Make sure frequency is correct!
		//			Map<Double, Integer> freqsCalib = new SubSet(split.getCalibrationSet()).getLabelFrequencies();
		//			//			original.println(freqsCalib);
		//			Map<Double, Integer> freqsProp = new SubSet(split.getProperTrainingSet()).getLabelFrequencies();
		//			//			original.println(freqsProp);
		//			for(Double label: freqs.keySet()) {
		//				Assert.assertTrue(Math.abs(freqsCalib.get(label) - freqs.get(label)*calibrationPart)<=1);
		//				Assert.assertTrue(Math.abs(freqsProp.get(label) - freqs.get(label)*(1-calibrationPart))<=1);
		//			}
		//
		//			// Check with a second splitter to make sure everything is still the same!
		//			TrainingsetSplit split2 = splitter2.get(fold);
		//			Assert.assertEquals(split.getCalibrationSet(), split2.getCalibrationSet());
		//			Assert.assertEquals(split.getProperTrainingSet(), split2.getProperTrainingSet());
		//
		//			fold++;
		//		}
		//
		//		try {
		//			splitter.get(numModels);
		//			Assert.fail();
		//		} catch (NoSuchElementException e) {
		//			Assert.assertTrue(e.getMessage().contains("Cannot get index"));
		//			//			original_err.println(e.getMessage());
		//		}
		//		try {
		//			splitter.get(-1);
		//			Assert.fail();
		//		} catch (NoSuchElementException e) {
		//			Assert.assertTrue(e.getMessage().contains("Cannot get index"));
		//			//			original_err.println(e.getMessage());
		//		}
		//
		//		// Make sure that different models are not the same dataset!
		//		for (int i=0; i<numModels-1; i++) {
		//			for (int j=i+1; j<numModels; j++) {
		//				TrainingsetSplit sp1 = splitter.get(i);
		//				TrainingsetSplit sp2 = splitter.get(j);
		//				Assert.assertNotEquals(sp1.getCalibrationSet(), sp2.getCalibrationSet());
		//				Assert.assertNotEquals(sp1.getProperTrainingSet(), sp2.getProperTrainingSet());
		//			}
		//		}
	}

	private void doCheckRandomSplits(Dataset prob, TrainSplitIterator splitter, TrainSplitIterator splitter2, boolean isStratified) {

		Map<Double, Integer> freqs = prob.getDataset().getLabelFrequencies();
		int index = 0;
		while(splitter.hasNext()) {
			TrainSplit split = splitter.next();
			Assert.assertEquals(prob.getDataset().size(), split.getCalibrationSet().size()+split.getProperTrainingSet().size());

			// Make sure the get(fold) returns the same thing as the iterator
			TrainSplit getSplit = splitter.get(index);
			Assert.assertEquals(split.getCalibrationSet(), getSplit.getCalibrationSet());
			Assert.assertEquals(split.getProperTrainingSet(), getSplit.getProperTrainingSet());

			// Make sure frequency is correct!
			if (isStratified) {
				Map<Double, Integer> freqsCalib = new SubSet(split.getCalibrationSet()).getLabelFrequencies();
				//			original.println(freqsCalib);
				Map<Double, Integer> freqsProp = new SubSet(split.getProperTrainingSet()).getLabelFrequencies();
				//			original.println(freqsProp);
				for(Double label: freqs.keySet()) {
					Assert.assertTrue(Math.abs(freqsCalib.get(label) - freqs.get(label)*calibrationPart)<=1);
					Assert.assertTrue(Math.abs(freqsProp.get(label) - freqs.get(label)*(1-calibrationPart))<=1);
				}
			}

			// Check with a second splitter to make sure everything is still the same!
			TrainSplit split2 = splitter2.get(index);
			Assert.assertEquals(split.getCalibrationSet(), split2.getCalibrationSet());
			Assert.assertEquals(split.getProperTrainingSet(), split2.getProperTrainingSet());

			index++;
		}

		try {
			splitter.get(numModels);
			Assert.fail();
		} catch (NoSuchElementException e) {
			Assert.assertTrue(e.getMessage().contains("Cannot get index"));
			//			original_err.println(e.getMessage());
		}
		try {
			splitter.get(-1);
			Assert.fail();
		} catch (NoSuchElementException e) {
			Assert.assertTrue(e.getMessage().contains("Cannot get index"));
			//			original_err.println(e.getMessage());
		}

		// Make sure that different models are not the same dataset!
		for (int i=0; i<numModels-1; i++) {
			for (int j=i+1; j<numModels; j++) {
				TrainSplit sp1 = splitter.get(i);
				TrainSplit sp2 = splitter.get(j);
				Assert.assertNotEquals(sp1.getCalibrationSet(), sp2.getCalibrationSet());
				Assert.assertNotEquals(sp1.getProperTrainingSet(), sp2.getProperTrainingSet());
			}
		}
	}




}

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
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.TestDataLoader;

@Category(UnitTest.class)
public class TestLOOCVSplitter {
	

	@Test
	public void testSplitsAreCorrect() throws Exception {
//		int k = 11, numPerSplit=2;
		int nRecs = 10;
		SubSet d = TestDataLoader.getInstance().getDataset(true, true).getDataset().clone();
		d.shuffle();
		
		
		// Only use enough to make even partition 
		d = d.splitStatic(nRecs)[0];
		Assert.assertEquals(nRecs, d.size());
		SubSet original_cpy = d.clone();

		Dataset p = new Dataset();
		p.setDataset(d);

		TestingStrategy strat = new LOOCV();
		Assert.assertEquals(nRecs, strat.getNumberOfSplitsAndValidate(p));

		Set<DataRecord> allTestRecs = new HashSet<>();

		Iterator<TestTrainSplit> iter = strat.getSplits(p);
		int num = 0;
		while (iter.hasNext()) {
			num++;
			TestTrainSplit split = iter.next();
			// Test records
			List<DataRecord> test = split.getTestSet();
			Assert.assertEquals(1, test.size());
			allTestRecs.addAll(test);
//			Assert.assertEquals(test.size()+preAddingToSetSize, allTestRecs.size());
			// Train records
			Dataset train = split.getTrainingSet();
			Assert.assertTrue(train.getCalibrationExclusiveDataset().isEmpty());
			Assert.assertTrue(train.getModelingExclusiveDataset().isEmpty());
			Assert.assertEquals(nRecs-1, train.getNumRecords());

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
		Assert.assertEquals(nRecs, num);

		Assert.assertEquals("Splitter should not change the original data",original_cpy, p.getDataset()); 

	}


}

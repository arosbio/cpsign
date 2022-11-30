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

import java.util.Iterator;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.sampling.FoldedSampling;
import com.arosbio.ml.sampling.TrainSplit;
import com.arosbio.ml.vap.ivap.IVAPClassifier;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.TestEnv;


@Category(UnitTest.class)
public class TestIVAPClassification extends TestEnv {

	@Test
	public void testIVAPClass() throws Exception {
		IVAPClassifier ivap = new IVAPClassifier(new C_SVC());
		SubSet trainingset = SubSet.fromLIBSVMFormat(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS.openStream());
		SubSet[] splits = trainingset.splitRandom(0.1);
		Dataset prb = new Dataset();
		prb.withDataset(splits[1]);
		Iterator<TrainSplit> trainingSplits = new FoldedSampling(10).getIterator(prb);
		ivap.train(trainingSplits.next());
		
		Assert.assertTrue(ivap.isTrained());
		
		System.out.println("Saved model!");

		
		System.out.println(systemOutRule.getLog());
	}
	
	@Test
	public void testCalcGradient() throws Exception {
		IVAPClassifier ivap = new IVAPClassifier(new C_SVC()); 
		Dataset prb = TestDataLoader.getInstance().getDataset(true, true);
		Iterator<TrainSplit> trainingSplits = new FoldedSampling(10).getIterator(prb);
		ivap.train(trainingSplits.next());
		
		Assert.assertTrue(ivap.isTrained());
		
		for (DataRecord rec: prb.getDataset()) {
			System.out.println(ivap.calculateGradient(rec.getFeatures(), (int)rec.getLabel()));
		}
		
	}
	
}

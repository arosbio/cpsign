/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.data.Dataset.FeatureInfo;
import com.arosbio.io.DataSink;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.TestEnv;

@Category(UnitTest.class)
public class TestDataset extends TestEnv {

	@Test
	public void testSaveLoad()throws Exception {
		Dataset prob = Dataset.fromLIBSVMFormat(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_20.openStream());
		Dataset clone = prob.clone();

		File jarFile = TestUtils.createTempFile("dfda", ".jar");
		
		LoggerUtils.setDebugMode();
		// Save it
		try(DataSink sink= getJarDataSink(jarFile)){
			prob.saveToDataSink(sink, null, null);
		}
		Dataset loaded = new Dataset();
		loaded.loadFromDataSource(getJarDataSource(jarFile), null);

		Assert.assertEquals(clone, prob);
		Assert.assertEquals(prob, loaded);

	}

	@Test
	public void testSaveEmptyProblemShouldFail() throws InvalidKeyException, IllegalAccessException, FileNotFoundException, IOException {
		Dataset prob = new Dataset();
		try{
			prob.saveToDataSink(getJarDataSink(TestUtils.createTempFile("sdfas", "")), null, null);
			Assert.fail();
		} catch(IllegalStateException e){}
	}
	
	@Test
	public void testGetFeatureInfo() throws Exception {
		Dataset clfData = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_20);
		List<FeatureInfo> info = clfData.getFeaturesInfo();
		clfData = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_20_MISSING_VALUES);
		List<FeatureInfo> missingDataInfo = clfData.getFeaturesInfo();

		Assert.assertEquals(info.size(), missingDataInfo.size());
		for (int i=0; i<info.size(); i++){
			FeatureInfo f1 = info.get(i);
			FeatureInfo f2 = missingDataInfo.get(i);
			Assert.assertEquals(f1.index, f2.index); // exact match
			
			// allow 15% difference if contains NaNs, otherwise should be identical
			double allowedPercentageDiff = f2.containsNaN ? .15 : 1e-8; 
			
			TestUtils.assertSimilar(f1.minValue, f2.minValue,allowedPercentageDiff);
			TestUtils.assertSimilar(f1.maxValue, f2.maxValue,allowedPercentageDiff);
			TestUtils.assertSimilar(f1.meanValue, f2.meanValue,allowedPercentageDiff*3); // mean value more influenced 
			TestUtils.assertSimilar(f1.medianValue, f2.medianValue,allowedPercentageDiff);

		}

		// Toy size version
		Dataset ds = new Dataset();
		try{
			ds.getFeaturesInfo();
			Assert.fail("no data - no feature info to get");
		} catch (IllegalStateException e){}

		ds.getDataset().add(new DataRecord(1d, new SparseVector(Arrays.asList(new SparseFeatureImpl(0,.3),new SparseFeatureImpl(3,5)))));
		// 0:0.3, (1:0), (2:0), 3:5
		info = ds.getFeaturesInfo();
		Assert.assertEquals("4 features (2 implicit zeros) - list should be of length 4",4, info.size());

		Assert.assertTrue(info.get(0).equals(new FeatureInfo(0, .3, .3, .3, .3, false)));
		Assert.assertTrue(info.get(1).equals(new FeatureInfo(1, 0, 0, 0, 0, false)));
		Assert.assertTrue(info.get(2).equals(new FeatureInfo(2, 0, 0, 0, 0, false)));
		Assert.assertTrue(info.get(3).equals(new FeatureInfo(3, 5, 5, 5, 5, false)));


		ds.getModelingExclusiveDataset().add(new DataRecord(1d, new SparseVector(Arrays.asList(new SparseFeatureImpl(0,.5),new MissingValueFeature(2),new SparseFeatureImpl(3,-1)))));
		// 0:0.3, (1:0), (2:0), 3:5
		// 0:0.5, (1:0), 3:NaN, 3:-1
		info = ds.getFeaturesInfo();
		Assert.assertEquals("4 features (1 implicit zero) - list should be of length 4",4, info.size());

		Assert.assertTrue(info.get(0).equals(new FeatureInfo(0, .3, .5, .4, .4, false)));
		Assert.assertTrue(info.get(1).equals(new FeatureInfo(1, 0, 0, 0, 0, false)));
		Assert.assertTrue(info.get(2).equals(new FeatureInfo(2, 0, 0, 0, 0, true)));
		Assert.assertTrue(info.get(3).equals(new FeatureInfo(3, -1, 5, 2, 2, false)));


		ds.getCalibrationExclusiveDataset().add(new DataRecord(1d, new SparseVector(Arrays.asList(new MissingValueFeature(0),new SparseFeatureImpl(1,3),new SparseFeatureImpl(2,1),new SparseFeatureImpl(3,-11)))));
		// 0:0.3, (1:0), (2:0), 3:5
		// 0:0.5, (1:0), 3:NaN, 3:-1
		// 0:NaN, (1:3), 3:1, 3:-11
		info = ds.getFeaturesInfo();
		Assert.assertEquals("4 features - list should be of length 4",4, info.size());

		Assert.assertTrue(info.get(0).equals(new FeatureInfo(0, .3, .5, .4, .4, true)));
		Assert.assertTrue(info.get(1).equals(new FeatureInfo(1, 0, 3, 1, 0, false)));
		Assert.assertTrue(info.get(2).equals(new FeatureInfo(2, 0, 1, .5, .5, true)));
		Assert.assertTrue(info.get(3).equals(new FeatureInfo(3, -11, 5, -7d/3, -1, false)));
		// Assert.assertEquals(3, info.size());
		// Assert.assertEquals(3, info.size());
		// Assert.assertEquals(3, info.size());
		// Assert.assertEquals(3, info.size());

	}

}

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

import java.util.Iterator;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.ml.sampling.FoldedStratifiedSampling;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.sampling.RandomStratifiedSampling;
import com.arosbio.ml.sampling.SamplingStrategy;
import com.arosbio.ml.sampling.SamplingStrategyUtils;
import com.arosbio.tests.suites.UnitTest;

@Category(UnitTest.class)
public class TestSamplingStrategy {
	
	@Test
	public void testSaveLoad() throws Exception {
		Iterator<SamplingStrategy> iter = FuzzyServiceLoader.iterator(SamplingStrategy.class);
		while(iter.hasNext()) {
			SamplingStrategy ss = iter.next();
			doTestSaveLoad(ss);
		}
		
		doTestSaveLoad(new RandomSampling(4, 0.5));
		doTestSaveLoad(new FoldedStratifiedSampling(7));
		doTestSaveLoad(new RandomStratifiedSampling(7, .4));
	}
	
	private void doTestSaveLoad(SamplingStrategy ss) {
		Map<String,Object> props = ss.getProperties();
		SamplingStrategy loaded = SamplingStrategyUtils.fromProperties(props);
		
		Assert.assertEquals(ss.getClass(), loaded.getClass());
		Assert.assertEquals(ss.getNumSamples(), loaded.getNumSamples());
		if (ss instanceof RandomSampling) {
			Assert.assertEquals(((RandomSampling) ss).getCalibrationRatio(), ((RandomSampling)loaded).getCalibrationRatio(),0.001);
		}
		Assert.assertEquals(ss.getProperties(), loaded.getProperties());
	}

}

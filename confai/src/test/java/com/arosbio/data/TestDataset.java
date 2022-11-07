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

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.io.DataSink;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.UnitTestInitializer;

@Category(UnitTest.class)
public class TestDataset extends UnitTestInitializer{

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
	


}

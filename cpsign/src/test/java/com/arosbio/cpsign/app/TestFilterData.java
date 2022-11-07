/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.utils.TestUtils;

@Category(CLITest.class)
public class TestFilterData extends CLIBaseTest{


	@Test
	public void testUsage() {
		mockMain(FilterData.CMD_NAME);
//				printLogs();
	}


	@Test
	public void testImputeTransformer() throws Exception {
		File preFirst = TestUtils.createTempFile("datafile", ".csv");
		CmpdData ames = TestResources.Cls.getAMES_126();

		mockMain(
				FilterData.CMD_NAME,
				"-td", ames.format(), ames.uri().toString(), 
				"-pr", ames.property(), 
				"--transformations", "drop-missing-data-feats", "single_feat_imp:median:colMinIndex=10:colMaxIndex=200","num-non-zer-select:4", "variance-based-select:2:50","standardize",
				"--labels", getLabelsArg(ames.labels()),
				"-of", "csv",
				"-o", preFirst.getAbsolutePath(),
				"--time"
				);
		

//		printLogs();
	}
	
	@Test
	public void testVoting() throws Exception {
		File preFirst = TestUtils.createTempFile("datafile", ".csv");
		CmpdData ames = TestResources.Cls.getAMES_1337();

		mockMain(
				FilterData.CMD_NAME,
				"-td", ames.format(), ames.uri().toString(),
				"-pr", ames.property(), 
				"--transformations", "voting",
				"--labels", getLabelsArg(ames.labels()), 
				"-of", "csv",
				"-o", preFirst.getAbsolutePath(),
				"--time"
				);
		
		try (InputStream input = new FileInputStream(preFirst);){
			List<String> lines = IOUtils.readLines(input, StandardCharsets.UTF_8);
			Assert.assertEquals("The resulting filtered data set should have the correct number of lines", 1296, lines.size());
		}

//		printLogs();
	}
}

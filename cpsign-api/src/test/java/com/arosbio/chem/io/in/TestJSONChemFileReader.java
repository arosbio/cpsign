/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem.io.in;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.UnitTestBase;
import com.github.cliftonlabs.json_simple.JsonException;


@Category(UnitTest.class)
public class TestJSONChemFileReader extends UnitTestBase {
	
	@Test
	public void testLongJSON() throws IOException, JsonException{
		CmpdData chang = TestResources.Reg.getChang_json();
		LoggerUtils.setDebugMode();
		JSONChemFileReader reader = new JSONChemFileReader(chang.url().openStream());
		reader.setEarlyTerminationAfter(-1);
		Assert.assertTrue(reader.hasNext());
		int numMols = 0;
		while(reader.hasNext()){
			reader.next();
			numMols++;
		}
		Assert.assertTrue(numMols>5);
		
//		SYS_OUT.println("numMols="+numMols);
	}
	
	
	@Test
	public void testOneRowJSONfile() throws Exception {
		// 34 records, 1 missing activity and 1 with invalid activity and 1 invalid smiles
		CmpdData chang_oneline = TestResources.Reg.getChang_json_no_indent();
		ChemFileIterator iter = new JSONChemFileReader(chang_oneline.url().openStream());
		iter.setEarlyTerminationAfter(-1);
		int numOK=0;
		while (iter.hasNext()){
			iter.next();
			numOK++;
		}
		Assert.assertEquals(34-1, numOK);
		Assert.assertEquals(1, iter.getRecordsSkipped());
		Assert.assertEquals(1, iter.getFailedRecords().size());

//		SYS_ERR.println(iter.getRecordsSkipped());
	}

}

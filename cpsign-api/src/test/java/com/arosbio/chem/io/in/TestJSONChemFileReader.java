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
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.commons.TypeUtils;
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
		reader.setProgressTracker(ProgressTracker.createNoEarlyStopping());
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
		iter.setProgressTracker(ProgressTracker.createNoEarlyStopping());
		int numOK=0, numMissingProp = 0, numNumericVal = 0;
		while (iter.hasNext()){
			IAtomContainer mol = iter.next();
			numOK++;
			if (! mol.getProperties().containsKey(chang_oneline.property())){
				numMissingProp ++;
			} else if (! TypeUtils.isDouble(mol.getProperties().get(chang_oneline.property()))){
				numNumericVal++;
			}
		}
		Assert.assertEquals("34 in total, one with invalid smiles = 33 'valid records'",33, numOK); 
		Assert.assertEquals(1, iter.getProgressTracker().getFailures().size());
		Assert.assertEquals(1, iter.getProgressTracker().getFailures().get(0).getIndex()); // index starts at 0, second record: index = 1

		Assert.assertEquals(1, numMissingProp);
		Assert.assertEquals(1, numNumericVal);
	}

}

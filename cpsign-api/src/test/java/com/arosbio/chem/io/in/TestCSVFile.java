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

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.TestUtils;

@Category(UnitTest.class)
public class TestCSVFile {

	@Test
	public void testFileWithNoHeader() throws Exception {
		File csvFile = TestUtils.createTempFile("myFile", "csv");
		FileUtils.write(csvFile, "Cc1ccc(Br)cc1-c1cc(Nc2ccc(Br)cc2)nc(N)n1	1", StandardCharsets.UTF_8);

//		LoggerUtils.setDebugMode(System.out);
		CSVFile f = new CSVFile(csvFile.toURI()).setUserDefinedHeader("SMILES","FLAG"); //.setSmilesColumnHeader(smilesColumn)
				//.convert("my-flag", Arrays.asList("csv", "header:SMILES,FLAG", csvFile.getAbsolutePath()));
		//		System.err.println(f.getProperties());
		Assert.assertEquals(1, f.countNumRecords());
		ChemFileIterator it = f.getIterator();
		Assert.assertTrue(it.hasNext());
		it.next();
		Assert.assertFalse(it.hasNext());


		// Write a new file, with an original header that should be replaced
		FileUtils.write(csvFile, "chemical structure	some_flag\nCc1ccc(Br)cc1-c1cc(Nc2ccc(Br)cc2)nc(N)n1	1", StandardCharsets.UTF_8,false);
		f.setSkipFirstRow(true); // ChemFileConverter.convert("my-flag", Arrays.asList("csv", "header:SMILES,FLAG", "skip_header:T", csvFile.getAbsolutePath()));
//		System.err.println(f.getProperties());
		Assert.assertEquals(1, f.countNumRecords());
		it = f.getIterator();
		Assert.assertTrue(it.hasNext());
		it.next();
		Assert.assertFalse(it.hasNext());

		// Instead of replacing the header - we can set an explicit smiles-header instead
		f = new CSVFile(csvFile.toURI()).setSmilesColumnHeader("chemical structure").setSkipFirstRow(true); 
		//UserDefinedHeader("SMILES","FLAG"); //ChemFileConverter.convert("my-flag", Arrays.asList("csv", "smiles_col:chemical structure", "skip_header:T", csvFile.getAbsolutePath()));
//		System.err.println(f.getProperties());
		Assert.assertEquals(1, f.countNumRecords());
		it = f.getIterator();
		Assert.assertTrue(it.hasNext());
		it.next();
		Assert.assertFalse(it.hasNext());
	}

}

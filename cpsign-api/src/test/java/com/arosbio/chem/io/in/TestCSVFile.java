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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.TestUtils;

@Category(UnitTest.class)
public class TestCSVFile {

	// @Test
	public void testRawCSVParser() throws Exception {
		File csvFile = TestUtils.createTempFile("myFile", "csv");
		FileUtils.write(csvFile, "Cc1ccc(Br)cc1-c1cc(Nc2ccc(Br)cc2)nc(N)n1	1	0.982	# Comment\n#line with comments\n456789	09876	dafads\n\n", StandardCharsets.UTF_8);
		CSVParser p = CSVFormat.TDF.builder().setCommentMarker('#').setRecordSeparator("\n").setHeader("Header 1", "Header 2", "Header 3").build().parse(new InputStreamReader(new FileInputStream(csvFile)));
		System.err.println(p.getHeaderMap());
		System.err.println(p.getHeaderNames());
		Iterator<CSVRecord> iter = p.iterator();
		while (iter.hasNext()){
			CSVRecord r = iter.next();
			System.err.print(r);
			System.err.print(", num values: " + r.size());
			System.err.println(", consistent="+r.isConsistent());
		}
	}

	@Test
	public void testWrongNumberOfHeaders() throws Exception {
		File csvFile = TestUtils.createTempFile("myFile", "csv");
		FileUtils.write(csvFile, "Cc1ccc(Br)cc1-c1cc(Nc2ccc(Br)cc2)nc(N)n1	1	0.982	Comment", StandardCharsets.UTF_8);

		// Try with too few headers - this _should_ fail
		CSVFile f = new CSVFile(csvFile.toURI()).setUserDefinedHeader("SMILES","FLAG");
		Assert.assertEquals(1, f.countNumRecords());
		ChemFileIterator it = f.getIterator();
		Assert.assertFalse("There should be no valid records in this (miss-match of number of columns)",it.hasNext());
		FailedRecord r = it.getProgressTracker().getFailures().get(0);
		String lcReason = r.getReason().toLowerCase();
		Assert.assertTrue(lcReason.contains("inconsistent"));
		Assert.assertTrue(lcReason.contains("columns"));
		Assert.assertTrue(lcReason.contains("fields"));
		
		// Try with too many headers (i.e. some records miss some values, this we pass)
		f = new CSVFile(csvFile.toURI()).setUserDefinedHeader("SMILES","FLAG", "Some property", "comments", "something not there", "another col");
		Assert.assertEquals(1, f.countNumRecords());
		it = f.getIterator();
		Assert.assertTrue(it.hasNext());
		IAtomContainer mol = it.next();
		Map<Object,Object> props = mol.getProperties();
		Assert.assertTrue(props.containsKey("SMILES"));
		Assert.assertTrue(props.containsKey("FLAG"));
		
	}

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

	@Test
	public void testExcelInput_and_BOM_behavior() throws IOException {
		// Note - before we had to specifically set to read the BOM, now we remove it if there is one (automagically)
		CSVCmpdData csv = TestResources.Reg.getSolubility_10_excel();

		ChemDataset dataset = new ChemDataset();
		dataset.initializeDescriptors();

		// Try with an excel file (containing a BOM) but not reading the BOM separately
		CSVFile csvFile = new CSVFile(csv.uri()).setDelimiter(csv.delim()).setSkipFirstRow(true);
		try (
			ChemFileIterator iter = csvFile.getIterator();
			MolAndActivityConverter molConv = MolAndActivityConverter.Builder.regressionConverter(iter,csv.property()).build();
		) {
			dataset.add(molConv);
		} catch (IllegalArgumentException e){
		}
		Assert.assertEquals(csv.numValidRecords(), dataset.size());

	}

}

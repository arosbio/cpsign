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
import java.io.InputStreamReader;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.UnitTestBase;

@Category(UnitTest.class)
public class TestCSVChemReader extends UnitTestBase {


	@Test
	public void testTABDelim() throws IOException{
		CSVCmpdData tabFile = TestResources.Cls.getErroneous();
		try(
				CSVChemFileReader smilesReader = new CSVChemFileReader(CSVFormat.TDF.builder().setHeader().build(), new InputStreamReader(tabFile.url().openStream()));){
			IAtomContainer mol;
			int numMols=0, numMolsWithValue=0;
			while (smilesReader.hasNext()){
				mol = smilesReader.next();
				numMols++;
				
				// Assert.assertNotNull(mol.getProperty("Sample_ID"));
				// Assert.assertNotNull(mol.getProperty("#SMILES"));
				// String resp = mol.getProperty("Response value");
				Assert.assertNotNull(mol.getProperty("canonical_smiles"));
				// Assert.assertNotNull(mol.getProperty("solubility(fake!)"));
				// Assert.assertNotNull(mol.getProperty("smiles"));
				String resp = mol.getProperty(tabFile.property()); 
				if (resp != null && ! resp.isEmpty())
					numMolsWithValue++;
				System.out.println(mol.getProperties());
//				SYS_ERR.println("id: " +CPSignMolProperties.getRecordIndex(mol));
			}
			Assert.assertEquals(18, numMols);
			Assert.assertEquals(17, numMolsWithValue);
		}
	}

	@Test
	public void testCommaDelim() throws Exception {
		CSVFile csvFile = new CSVFile(TestResources.Cls.getBBB().uri());
		csvFile.setDelimiter(',');
		Assert.assertEquals(415, csvFile.countNumRecords()); // Just to make sure it doesn't fail
	}

	@Test
	public void testInvalidSmilesRecordIndex() throws Exception {

		CSVCmpdData containsInvalidSMILES = TestResources.Reg.getErroneous();

		CSVFile csvFile = new CSVFile(containsInvalidSMILES.uri()).setDelimiter(containsInvalidSMILES.delim());

		try (CSVChemFileReader smilesReader = csvFile.getIterator()){
			IAtomContainer mol;
			while (smilesReader.hasNext()){
				mol = smilesReader.next();
				System.out.println(mol.getProperties());
			}
			
			List<FailedRecord> failed = smilesReader.getFailedRecords();
			Assert.assertEquals(2, failed.size());
			Assert.assertEquals(0, failed.get(0).getIndex());
			Assert.assertEquals(7, failed.get(1).getIndex());
		}
		
		// Test but with replacing the header with an existing one
		csvFile = new CSVFile(containsInvalidSMILES.uri())
				.setDelimiter(containsInvalidSMILES.delim())
				.setUserDefinedHeader("target","smiles")
				.setSkipFirstRow(true);

		try (CSVChemFileReader smilesReader = csvFile.getIterator()){
			IAtomContainer mol;
			while (smilesReader.hasNext()){
				mol = smilesReader.next();
				System.out.println(mol.getProperties());
			}
			
			List<FailedRecord> failed = smilesReader.getFailedRecords();
			Assert.assertEquals(2, failed.size());
			Assert.assertEquals(0, failed.get(0).getIndex());
			Assert.assertEquals(7, failed.get(1).getIndex());
		}
		
		// Test at the next stage, with the iterating Mol and activity 
		try (CSVChemFileReader smilesReader = csvFile.getIterator();
				MolAndActivityConverter reader = MolAndActivityConverter.Builder.regressionConverter(smilesReader, "target").build();
				){
			
			Pair<IAtomContainer,Double> rec;
			while (reader.hasNext()){
				rec = reader.next();
				IAtomContainer mol = rec.getLeft();
				System.out.println(mol.getProperties());
//				SYS_OUT.println("id: " + CPSignMolProperties.getRecordIndex(mol));
			}
			
//			SYS_ERR.println("top-level: " + topLevel);
			List<FailedRecord> failed = smilesReader.getFailedRecords();
//			SYS_ERR.println("lower-level: " + failed);
			Assert.assertEquals(2, failed.size());
			Assert.assertEquals(0, failed.get(0).getIndex());
			Assert.assertEquals(7, failed.get(1).getIndex());
			
			List<FailedRecord> topLevel = reader.getFailedRecords();
			// The top-level mol-and-activity iterator contains all lower-level iterator fails
			Assert.assertTrue(topLevel.containsAll(failed));
			Assert.assertEquals(1, topLevel.get(1).getIndex());
			Assert.assertEquals(9, topLevel.get(topLevel.size()-1).getIndex());
		}
	}

	

	@Test
	public void testExcelCSV_flipped_order() throws Exception {
		CSVCmpdData soluExcel = TestResources.Reg.getSolubility_10_excel();
		try(
				CSVChemFileReader smilesReader = new CSVChemFileReader(CSVFormat.EXCEL.builder().setHeader().setDelimiter(soluExcel.delim()).build(), 
					new InputStreamReader(soluExcel.url().openStream()));){
			int numMolecules=0;
			while(smilesReader.hasNext()){
				IAtomContainer mol = smilesReader.next();
				numMolecules++;
				System.out.println(mol.getProperties());
			}

			Assert.assertEquals(10, numMolecules);
		}
	}


}
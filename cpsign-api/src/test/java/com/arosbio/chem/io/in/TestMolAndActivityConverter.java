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

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.data.NamedLabels;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.UnitTestBase;

@Category(UnitTest.class)
public class TestMolAndActivityConverter extends UnitTestBase {


	@Test
	public void Test_CSV_Classification() throws Exception{
		// 19 lines of records, 1 not OK at all, 1 line with missing value
		CSVCmpdData data = TestResources.Cls.getErronious();
		try(MolAndActivityConverter molAct = MolAndActivityConverter.classificationConverter(new CSVFile(data.uri()).getIterator(), 
			data.property(), 
			new NamedLabels(data.labelsStr()));){

			int numMolecules=0;
			while(molAct.hasNext()){
				Pair<IAtomContainer, Double> instance = molAct.next();
				Assert.assertNotNull(instance.getLeft());
				Assert.assertTrue(instance.getRight().equals(0.0) || instance.getRight().equals(1.0));
				numMolecules++;
			}

			Assert.assertEquals(17, numMolecules);
			Assert.assertEquals(1, molAct.getMolsSkippedMissingActivity());
			Assert.assertEquals(2, molAct.getFailedRecords().size());
			// Assert.assertEquals(1, molAct.getMols);
		}

	}

	@Test
	public void testNotCauseStackOverflowExceptWithFaultyParams() throws Exception {
		
		// REGRESSION
		CSVCmpdData bigCSV = TestResources.Reg.getSolubility();

		try(
			CSVChemFileReader reader = new CSVFile(bigCSV.uri()).setDelimiter(bigCSV.delim()).getIterator();
			MolAndActivityConverter conv = MolAndActivityConverter.regressionConverter(reader, "wrong");
		){
			int numSuccess = 0;
			conv.setStopAfterNumFails(-1);
			
			while (conv.hasNext()){
				conv.next();
				numSuccess ++;
			}
			Assert.assertEquals(0, numSuccess);
		}

		// CLASSIFICATION
		try(
			CSVChemFileReader reader = new CSVFile(bigCSV.uri()).setDelimiter(bigCSV.delim()).getIterator();
			MolAndActivityConverter conv = MolAndActivityConverter.classificationConverter(reader, "wrong", new NamedLabels("label0","label1"));
		){
			int numSuccess = 0;
			conv.setStopAfterNumFails(-1);
			
			while (conv.hasNext()){
				conv.next();
				numSuccess ++;
			}
			Assert.assertEquals(0, numSuccess);
		}



		// printLogs();
	}

	@Test
	public void Test_CSV_Regression() throws Exception {
		// 10 lines total, 2 bad smiles, 1 missing target value, 1 invalid target
		CSVCmpdData data = TestResources.Reg.getErronious();

		
		try (
			CSVChemFileReader reader = new CSVFile(data.uri()).setDelimiter(data.delim()).setHasBOM(true).getIterator();	
			MolAndActivityConverter molAct = MolAndActivityConverter.regressionConverter(reader, data.property());
			
			){
			int numMolecules=0;
			while( molAct.hasNext()){
				Pair<IAtomContainer, Double> instance = molAct.next();
				Assert.assertNotNull(instance.getLeft());
				Assert.assertNotNull(instance.getRight());
				numMolecules++;
			}
			System.err.println(molAct.getFailedRecords());
			Assert.assertEquals(6, numMolecules);
			Assert.assertEquals(1, molAct.getMolsSkippedMissingActivity());
			Assert.assertEquals(1, molAct.getMolsSkippedInvalidActivity());
			Assert.assertEquals(4,molAct.getNumFailedMols());
			Assert.assertEquals(4, molAct.getFailedRecords().size());
		}
	}

	@Test
	public void testErr_SDF_NoPropertyGiven() throws Exception {
		try (MolAndActivityConverter m = MolAndActivityConverter.classificationConverter(new SDFile(TestResources.Cls.getAMES_10().uri()).getIterator(),null, new NamedLabels(TestResources.Cls.AMES_LABELS));){
			Assert.fail("Should fail without giving a propertyName");
		} catch (IllegalArgumentException e){
			System.out.println(e.getMessage());
			Assert.assertTrue(e.getMessage().toLowerCase().contains("property"));
		}
	}

	@Test
	public void Test_SDF_Classification() throws Exception {
		int numMolecules=0;
		CmpdData ames = TestResources.Cls.getAMES_10();
		ChemFile sdf = new SDFile(ames.uri());

		try(MolAndActivityConverter molAct = MolAndActivityConverter.classificationConverter(
				sdf.getIterator(), ames.property(), new NamedLabels(ames.labelsStr())); ){

			while( molAct.hasNext()){
				Pair<IAtomContainer, Double> instance = molAct.next();
				Assert.assertNotNull(instance.getLeft());
				Assert.assertTrue(instance.getRight().equals(0.0) || instance.getRight().equals(1.0));
				numMolecules++;
			}

			Assert.assertEquals(10, numMolecules);
			Assert.assertEquals(0, molAct.getMolsSkippedMissingActivity());
		}
	}

	@Test
	public void TestSDFRegression() throws Exception {
		CmpdData chang = TestResources.Reg.getChang();
		try (MolAndActivityConverter molAct = MolAndActivityConverter.regressionConverter(
				new SDFile(chang.uri()).getIterator(), chang.property());){

			int numMolecules=0;
			while( molAct.hasNext()){
				Pair<IAtomContainer, Double> instance = molAct.next();
				Assert.assertNotNull(instance.getLeft());
				Assert.assertNotNull(instance.getRight());
				numMolecules++;
			}

			Assert.assertEquals(34, numMolecules);
			Assert.assertEquals(0, molAct.getMolsSkippedMissingActivity());
		}
	}

	@Test
	public void testJSONClassification() throws Exception{
		CmpdData amesJSON = TestResources.Cls.getAMES_10_json();
		LoggerUtils.setDebugMode();
		try (MolAndActivityConverter molsIterator = MolAndActivityConverter.classificationConverter(
				new JSONFile(amesJSON.uri()).getIterator(), amesJSON.property(), new NamedLabels(amesJSON.labelsStr()));){
			//		Assert.assertTrue(molsIterator instanceof );

			int numMolecules=0;
			Pair<IAtomContainer, Double> record;
			while(molsIterator.hasNext()){
				record = molsIterator.next();
				numMolecules++;

				String activity = record.getLeft().getProperty(amesJSON.property());
				Assert.assertNotNull(activity);
				Assert.assertTrue( activity.equals("mutagen") || activity.equals("nonmutagen"));
				Assert.assertTrue(record.getRight()== 0.0 || record.getRight() == 1.0);
			}

			Assert.assertEquals(10, numMolecules);
		}
	}

	@Test
	public void testJSONClassificationBoolean() throws Exception{
		CmpdData amesJSON = TestResources.Cls.getAMES_10_json_bool();
		LoggerUtils.setDebugMode();
		try(MolAndActivityConverter molsIterator = 
				MolAndActivityConverter.classificationConverter(
						new JSONFile(amesJSON.uri()).getIterator(), 
						amesJSON.property(), 
						new NamedLabels(amesJSON.labelsStr())); ){
			//		Assert.assertTrue(molsIterator instanceof );

			int numMolecules=0;
			Pair<IAtomContainer, Double> record;
			while(molsIterator.hasNext()){
				record = molsIterator.next();
				numMolecules++;

				String activity = record.getLeft().getProperty(amesJSON.property());
				Assert.assertNotNull(activity);
				//			Assert.assertTrue( activity.equals("mutagen") || activity.equals("nonmutagen"));
				Assert.assertTrue(record.getRight()== 0.0 || record.getRight() == 1.0);
			}
			//		System.out.println(((MolAndActivityConverter) molsIterator).getMolsSkippedDueToMissingActivity());
			Assert.assertEquals(10, numMolecules);
		}
	}


	@Test
	public void testJSONRegression() throws Exception{
		CmpdData changJSON = TestResources.Reg.getChang_json();
		LoggerUtils.setDebugMode();
		try(MolAndActivityConverter molsIterator = 
				MolAndActivityConverter.regressionConverter(
						new JSONFile(changJSON.uri()).getIterator(), 
						changJSON.property()); ){

			int numMolecules=0;
			Pair<IAtomContainer, Double> record;
			while(molsIterator.hasNext()){
				record = molsIterator.next();
				numMolecules++;

				String activity = record.getLeft().getProperty(changJSON.property());
				Assert.assertNotNull(activity);
			}
			//		System.out.println(((MolAndActivityConverter) molsIterator).getMolsSkippedDueToMissingActivity());
			Assert.assertEquals(34, numMolecules);
		}
	}


}

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

import java.io.InputStream;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.UnitTestBase;

@Category(UnitTest.class)
public class TestChemFileParserUtils extends UnitTestBase{

	@Test
	public void testFindProperties() throws Exception {
		CSVCmpdData sol_csv = TestResources.Reg.getSolubility_10_multicol();
		CmpdData chang = TestResources.Reg.getChang();
		CSVFile csv = new CSVFile(sol_csv.uri());
		csv.setDelimiter(sol_csv.delim());
		System.out.println(ChemFileParserUtils.findProperties(csv));
		SDFile sdf = new SDFile(chang.uri());
		System.out.println(ChemFileParserUtils.findProperties(sdf));

	}


	@Test
	public void TestParseSMILES() throws InvalidSmilesException{
		String SMILESwithName = "C1=CC=C(C=C1)N=NC2=C(N=C(C=C2)N)N.Cl\tMolName";
		IAtomContainer mol = ChemFileParserUtils.parseSMILES(SMILESwithName);
		Assert.assertNotNull(mol);
		//		System.out.println(mol.getProperty(CPSignParemeters.CDK_TITLE));
		Assert.assertEquals("MolName", mol.getProperty(CDKConstants.TITLE));
	}
	
	@Test
	public void testFailingSMILESdueToHAC() throws InvalidSmilesException {
		String smiles = "CC1C(C(CC(O1)OC2C(OC(CC2O)OC3CC(CC4=C(C5=C(C(=C34)O)C(=O)C6=CC=CC=C6C5=O)O)(C(=O)CO)O)C)N)O";
		IAtomContainer mol = ChemFileParserUtils.parseSMILES(smiles);
		Assert.assertTrue(mol.getAtomCount()>30);
	}

	@Test
	public void testIsJSONFile() throws Exception {
		try (
			InputStream json1 = TestResources.Cls.getAMES_10_json().url().openStream();
			InputStream json2 = TestResources.Cls.getAMES_10_json_bool().url().openStream();
			InputStream json3 = TestResources.Reg.getChang_json_no_indent().url().openStream();
			InputStream sdf = TestResources.Cls.getAMES_10().url().openStream();
			InputStream csv = TestResources.Reg.getSolubility_10().url().openStream();){
				Assert.assertTrue(ChemFileParserUtils.isJSONFile(json1));
				Assert.assertTrue(ChemFileParserUtils.isJSONFile(json2));
				Assert.assertTrue(ChemFileParserUtils.isJSONFile(json3));
				Assert.assertFalse(ChemFileParserUtils.isJSONFile(sdf));
				Assert.assertFalse(ChemFileParserUtils.isJSONFile(csv));
		}
		
	}
	
}

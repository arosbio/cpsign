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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.chem.io.in.CSVFile;
import com.arosbio.chem.io.in.SDFile;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.cpsign.out.OutputNamingSettings.JSON;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.CLITest;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;


@Category(CLITest.class)
public class TestGenSign extends CLIBaseTest {

	CmpdData sdfFile = TestResources.Cls.getAMES_10();
	CmpdData sdfGZ = TestResources.Cls.getAMES_10_gzip();
	CSVCmpdData csv = TestResources.Reg.getSolubility_10();
	CmpdData json = TestResources.Cls.getAMES_10_json();

	@Before
	@After
	public void resetLogging() throws Exception {
		LoggerUtils.reloadLoggingConfig();
	}

	@Test
	public void testUsage() throws Exception {
		mockMain(GenerateSignatures.CMD_NAME);
	}


	@Test
	public void testGenerateSignaturesSDF() throws Exception{
		//The test model files
		
		mockMain(GenerateSignatures.CMD_NAME,
		"-q",
		"-i", SDFile.FORMAT_NAME, sdfFile.uri().toString()
		// ,"-o", "~/Desktop/generated_sign.json"
		);
		
		JsonArray outputArr = (JsonArray) Jsoner.deserialize(systemOutRule.getLog());
		Assert.assertEquals(10,outputArr.size());

		// Check with gzip-ed file
		systemOutRule.clearLog();

		//The test model files
		mockMain("gensign",
		"-q",
		"-i",SDFile.FORMAT_NAME, sdfGZ.uri().toString());
		
		JsonArray outputArrGZ = (JsonArray) Jsoner.deserialize(systemOutRule.getLog()); 
		Assert.assertEquals(10,outputArrGZ.size());

		// Check that normal file and GZIP-file give the same results
		Assert.assertEquals(outputArr, outputArrGZ);
	}

	@Test
	public void testGenerateSignaturesSingleSMILES() throws Exception{
		//The test model files
		String[] args = { 
				GenerateSignatures.CMD_NAME,
				"--smiles","CC1=CN=C(C(=C1OC)C)CS(=O)C2=NC3=C(N2)C=C(C=C3)OC",
				"--silent"
		};
		mockMain(args);

		String outputSignatures = systemOutRule.getLog().toString();

		JsonArray output = (JsonArray) Jsoner.deserialize(outputSignatures); 
		JsonObject rec = (JsonObject) output.get(0);
		JsonObject signatures = (JsonObject) rec.get(JSON.GENERATED_SIGNATURES_SECTION_KEY);
		Assert.assertTrue(signatures.size() > 10);
		Assert.assertTrue(systemErrRule.getLog().isEmpty());

	}

	@Test
	public void testGenerateSignaturesCSVFile() throws Exception {
		
		//The test model files
		mockMain(new String[]{ 
				"gensign",
				"-i",csv.format(), csv.uri().toString(),
				"--silent"
		});

		JsonArray outputArr = (JsonArray) Jsoner.deserialize(systemOutRule.getLog());
		Assert.assertTrue(outputArr.size()>1);
		
		// Test with corresponding compressed file
		systemOutRule.clearLog();
		mockMain(new String[]{ 
				"gensign",
				"-i",csv.format(), csv.uri().toString(),
				"--silent"
		});

		JsonArray outputArrGZ = (JsonArray) Jsoner.deserialize(systemOutRule.getLog()); 
		

		// Check that normal file and GZIP-file give the same results
		Assert.assertEquals(outputArr, outputArrGZ);
		Assert.assertTrue(systemErrRule.getLog().isEmpty());

	}
	
	
	@Test
	public void testSMILES() throws Exception{
		mockMain(new String[]{
				GenerateSignatures.CMD_NAME, 
				"-sm", "CCCCC"
				});
	}
	
	@Test
	public void testInputfile() throws Exception{
		mockMain(
				GenerateSignatures.CMD_NAME, 
				"-i", SDFile.FORMAT_NAME, sdfGZ.uri().toString()
				);
	}
	
	@Test
	public void testSMILESandInputfile() throws Exception{
		mockMain(new String[]{
				GenerateSignatures.CMD_NAME, 
				"-sm", "CCCCC",
				"-i", SDFile.FORMAT_NAME, sdfFile.uri().toString(),
				});
	}

}

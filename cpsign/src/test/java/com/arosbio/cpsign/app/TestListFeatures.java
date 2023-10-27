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
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.cpsign.app.params.CLIParameters;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.TestChemDataLoader.PreTrainedModels;

import ch.qos.logback.core.joran.spi.JoranException;


@Category(CLITest.class)
public class TestListFeatures extends CLIBaseTest {



	@AfterClass
	public static void teardown()throws JoranException{
		LoggerUtils.reloadLoggingConfig();
	}

	@Before
	public void betweenMethods() {
		super.betweenMethods();
		try {
			LoggerUtils.reloadLoggingConfig();
			systemErrRule.clearLog();
			systemOutRule.clearLog();
		} catch(JoranException e){ Assert.fail();}
	}
	
	@Test
	public void testMissingArgs() throws Exception {
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new PrintSysOutput(false));
		mockMain(
				ListFeatures.CMD_NAME,
				"-ro", ""+CLIParameters.TextOutputType.CSV
		);
	}
	
	@Test
	public void testListStdModelNoSignatures() throws Exception {
		mockMain(new String[] {
				ListFeatures.CMD_NAME,
				"-rf", ""+CLIParameters.TextOutputType.CSV,
				"-m", PreTrainedModels.ACP_CLF_LIBLINEAR.toString(),
				"--echo",
		});
		// printLogs();
	}

	@Test
	public void testTrainedModelVerbose() throws Exception {
		mockMain(new String[] {
				ListFeatures.CMD_NAME,
				"-rf", ""+CLIParameters.TextOutputType.CSV,
				"-m", PreTrainedModels.ACP_CLF_LIBLINEAR.toString(),
				"--echo",
				"--include-signatures",
				"-v"
		});
		Assert.assertTrue(systemErrRule.getLog().length()>10); // Should print in system err about --verbose mode
		Assert.assertTrue(systemOutRule.getLog().length()>10000); // Very long ~11k lines of CSV
	}
	
	@Test
	public void testListStdModelWithSignatures() throws Exception {
		File of = TestUtils.createTempFile("output", ".csv");
		
		mockMain(new String[] {
				ListFeatures.CMD_NAME,
				"-rf", ""+CLIParameters.TextOutputType.TSV,
				"-m", PreTrainedModels.ACP_CLF_LIBLINEAR.toString(),
				"--include-signatures",
				"--echo",
				"-ro", of.getAbsolutePath(),
		});
//		printLogs();
		List<String> lines = FileUtils.readLines(of, STANDARD_CHARSET);
		Assert.assertTrue(lines.size() > 1000);
	}
	
	@Test
	public void testOtherDescrAndTransformers() throws Exception {
		File preFile = TestUtils.createTempFile("precomputed", ".jar");
		// Generate the precomputed file
		CmpdData ames = TestResources.Cls.getAMES_10();
		
		mockMain(new String[] {
				Precompute.CMD_NAME,
				"--train-data", ames.format(), ames.uri().toString(),
				"--labels", getLabelsArg(ames.labels()), 
				"--property", ames.property(), 
				"--model-type", "classification",
				"-mo", preFile.getAbsolutePath(),
				"-mn", "ames-precomp",
				"-d", "signatures",
				"-d", "AminoAcidCountDescriptor", "AtomCountDescriptor", "AutocorrelationDescriptorCharge", "BCUTDescriptor","ChiChainDescriptor",
				"--transformations", "VarianceBasedSelection:criterion=keepN:n=50",
		});
		
//		printLogs();
		clearLogs();
		File of = TestUtils.createTempFile("output", ".csv"); 
		
		mockMain(new String[] {
				ListFeatures.CMD_NAME,
				"-rf", ""+CLIParameters.TextOutputType.TSV,
				"-m", preFile.getAbsolutePath(),
				"--include-signatures",
				"--echo",
				"-ro", of.getAbsolutePath(),
		});
		// printLogs();
		List<String> lines = FileUtils.readLines(of, STANDARD_CHARSET);
		// for(String l : lines)
		// 	SYS_ERR.println(l);
		Assert.assertEquals(51, lines.size()); // keep 50 + 1 header line
		
		// Try verbose mode
		clearLogs();
		of = TestUtils.createTempFile("output", ".csv"); 
		
		mockMain(new String[] {
				ListFeatures.CMD_NAME,
				"-rf", ""+CLIParameters.TextOutputType.TSV,
				"-m", preFile.getAbsolutePath(),
				"--include-signatures",
				"--echo",
				"-ro", of.getAbsolutePath(),
				"-v"
		});
		// printLogs();
		List<String> verboseLines = FileUtils.readLines(of, STANDARD_CHARSET);

		// for(String l : verboseLines)
		// 	SYS_ERR.println(l);
		Assert.assertEquals(51, verboseLines.size()); // keep 50 + 1 header line

		// Check that the initial part of the lines are identical, but verbose should be longer..
		for (int i = 0; i<lines.size(); i++){
			Assert.assertTrue(verboseLines.get(i).startsWith(lines.get(i)));
			Assert.assertTrue(verboseLines.get(i).length()*1.25>lines.get(i).length());
		}

//		for(String l : lines)
//			SYS_ERR.println(l);
		
	}

	@Test
	public void testEncryptedModel() throws Exception {

		String key = generateEncryptionKey();
		// SYS_ERR.println("generated key: " + key);

		File modelFile = TestUtils.createTempFile("precomp", ".jar");
		CSVCmpdData solu10 = TestResources.Reg.getSolubility_10();
		mockMain(Precompute.CMD_NAME, 
			"-td", solu10.format(), solu10.uri().toString(), 
			"-mt", PRECOMPUTE_REGRESSION, 
			"--property", solu10.property(),
			"--descriptors", "ALOGPDescriptor","AutocorrelationDescriptorCharge",
			"--key", key,
			"-mo", modelFile.getAbsolutePath());

		systemOutRule.clearLog();
		mockMain(ListFeatures.CMD_NAME, "-m", modelFile.getAbsolutePath(), "--key", key);
		String log = systemOutRule.getLog().toLowerCase();
		Assert.assertTrue(log.contains("amr"));
		Assert.assertTrue(log.contains("alogp2"));
		Assert.assertTrue(log.contains("atsc1"));
		Assert.assertTrue(log.contains("feature"));

		
		// Try without supplying the encryption key - should fail!
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		mockMain(ListFeatures.CMD_NAME, "-m", modelFile.getAbsolutePath());

	}

	
}
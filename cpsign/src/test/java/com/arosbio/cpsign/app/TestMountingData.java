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
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.cpsign.app.PrecomputedDatasets.Classification;
import com.arosbio.cpsign.app.PrecomputedDatasets.Regression;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.io.ModelIO;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.utils.TestUtils;

@Category(CLITest.class)
public class TestMountingData extends CLIBaseTest {

	private static CSVCmpdData solu10 = TestResources.Reg.getSolubility_10();

	private static File fileToMount = TestUtils.getFile("/runconfigs/precomp_reg.txt");
	private static String filePath = fileToMount.getAbsolutePath();

	private static File secFileToMount = TestUtils.getFile("/runconfigs/precomp_reg_split2.txt"); //new File(TestUtils.class.getResource("/runconfigs/precomp_reg_split2.txt").getFile());
	private static String secFilePath = secFileToMount.getAbsolutePath();

	@Test
	public void testPrecompute() throws Exception {
		File dataFile = TestUtils.createTempFile("datafile", ".csr.jar");

		// =====================================
		// ORDINARY PLAIN TEXT PRECOMPUTED FILES 
		// =====================================
		mockMain(
				Precompute.CMD_NAME,
				"-td" , solu10.format(), solu10.uri().toString(),
				"--property", solu10.property(),
				"-mt", PRECOMPUTE_REGRESSION,
				"--descriptors", "signatures:1:3",
				"-mo", dataFile.getAbsolutePath(),
				"-mn", "dasf",
				"--mount", "file.txt:"+filePath //TestUtils.getPath("runconfigs/precomp_reg.txt") // RegressionSolubility.SOLUBILITY_10_FILE_PATH,
		);
		

		assertFileIsThere(dataFile, fileToMount, "file.txt");
	}

	@Test
	public void testTrainReg() throws Exception {
		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.jar"); 
		
		mockMain(new String[] {
				"train",
				"-pt", ACP_REGRESSION_TYPE,
				"-ds", Regression.getSolu10().getAbsolutePath(), 
				"-ss", strategy(RANDOM_SAMPLING, 1),
				"-sc", LinearSVR.ALG_NAME, //"libsvm",
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "trained-acp-reg",
				"--percentiles", "0",
				"--time",
				"--mount", "file.txt:"+filePath,
		});
		
		assertFileIsThere(modelFile, fileToMount, "file.txt");
	}

	@Test
	public void testTrainACPClass() throws Exception{
		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.jar"); 

		mockMain(new String[] {
				Train.CMD_NAME,
				"-pt", ACP_CLASSIFICATION_TYPE,
				"-ds", Classification.getAmes123().getAbsolutePath(), 
				"-ss", strategy(RANDOM_SAMPLING, 1),
				"-sc", LinearSVC.ALG_NAME,
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "sdagas",
				"--percentiles", "0",
				"--mount", "file.txt:"+filePath
		});
		
		assertFileIsThere(modelFile, fileToMount, "file.txt");
	}

	@Test
	public void testTrainVAPClass() throws Exception {
		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.jar"); 

		mockMain(new String[] {
				Train.CMD_NAME,
				"-pt", CVAP_CLASSIFICATION_TYPE,
				"-ds", Classification.getAmes123().getAbsolutePath(), 
				"-ss", strategy(RANDOM_SAMPLING, 1),
				"-sc", LinearSVC.ALG_NAME,
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "sdagas",
				"--percentiles", "0",
				"--mount", "file.txt:"+filePath, "file2.txt:"+secFilePath
		});
		
		assertFileIsThere(modelFile, fileToMount, "file.txt");
		assertFileIsThere(modelFile, secFileToMount, "file2.txt");
	}

	@Test
	public void testNotValidURI() throws Exception {
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("non_existing", "not", "valid"));
		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.jar"); 

		mockMain(new String[] {
				Train.CMD_NAME,
				"-pt", CVAP_CLASSIFICATION_TYPE,
				"-mi", Classification.getAmes123().getAbsolutePath(), 
				"-ss", strategy(RANDOM_SAMPLING, 1),
				"-sc", LinearSVR.ALG_NAME,
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "sdagas",
				"--percentiles", "0",
				"--mount", "file.txt:non_existing",
				"--echo"
		});
	}
	
	@Test
	public void testNotValidFormatting() throws Exception {
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("formatted", "not", "file.txt=non_existing"));
		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.jar"); 

		mockMain(new String[] {
				Train.CMD_NAME,
				"-pt", CVAP_CLASSIFICATION_TYPE,
				"-mi", Classification.getAmes123().getAbsolutePath(),
				"-ss", strategy(RANDOM_SAMPLING, 1),
				"-sc", LinearSVC.ALG_NAME,
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "sdagas",
				"--percentiles", "0",
				"--mount", "file.txt=non_existing",
				"--echo"
		});
	}
	

	private static void assertFileIsThere(File osgi, File shouldBeMounted, String location) throws Exception {
		File loaded = null;
		try {
			loaded = ModelIO.getMountedDataAsTmpFile(osgi.toURI(), location, null);

			// Must compare line by line, as the EOL differs and the FileUtils.contentEqualsIgnoreEOL did not work..
			List<String> origLines = FileUtils.readLines(shouldBeMounted, STANDARD_CHARSET);
			List<String> fromMountedLines = FileUtils.readLines(loaded, STANDARD_CHARSET);
			Assert.assertEquals(origLines.size(), fromMountedLines.size());
			for (int i=0; i<origLines.size(); i++)
				Assert.assertEquals(origLines.get(i), fromMountedLines.get(i));

		} finally {
			if (loaded != null)
				loaded.delete();
		}
	}
	
}

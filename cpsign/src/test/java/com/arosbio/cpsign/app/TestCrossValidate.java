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

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.cpsign.app.PrecomputedDatasets.Classification;
import com.arosbio.cpsign.app.PrecomputedDatasets.Regression;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.utils.TestUtils;

@Category(CLITest.class)
public class TestCrossValidate extends CLIBaseTest{
	
	private static final boolean PRINT_RESULTS = false;

	@Test
	public void testCLIArgs() throws Exception {
		mockMain(CrossValidate.CMD_NAME);
	}
	
	
	@Test
	public void testWhenHavingMissingValues() throws Exception {
		expectExit(ExitStatus.USER_ERROR);
		exit.checkAssertionAfterwards(new PrintSysOutput(PRINT_RESULTS));
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("data", "features", "missing", "revise"));
		
		
		try {
			mockMain(new String[] {
					CrossValidate.CMD_NAME,
					"-ds", Classification.getMissingDataDS().getAbsolutePath(),
					"--time"
			}
			);
			Assert.fail();
			
		} catch(Exception e){
		}
	}
	
	@Test
	public void testCrossValidateACPClassification() throws Exception {
		mockMain(new String[]{
				CrossValidate.CMD_ALIAS,
				"-pt", ""+ACP_CLASSIFICATION_TYPE,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"-k", "10",
				"--result-format", "tsv",//
				"-cp", "0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9",
				"--seed", "41214",
		});
		
		String log = systemOutRule.getLog();
		String res = log.split("statistics:\n")[1];
		res = res.split("\n\n")[0];
		System.err.println("Splitted:\n"+res+"\n---------");
		
		File crossValRes = TestUtils.createTempFile("crossvalidationRes", ".tsv");
		System.out.println(crossValRes);
		mockMain(new String[]{
				CrossValidate.CMD_NAME, // });
				"-pt", ""+ACP_CLASSIFICATION_TYPE,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"-k", "10",
				"--result-format", "tsv",//
				"-cp", "0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9",
				"--seed", "41214",
				"-ro", crossValRes.getAbsolutePath(),
				"--echo"
		});
		
		System.out.println(FileUtils.readFileToString(crossValRes, STANDARD_CHARSET));
		if (PRINT_RESULTS)
			printLogs();
	}
	
	@Test
	public void testCrossValidateACPClassificationLOOCV() throws Exception {
		mockMain(new String[]{
				CrossValidate.CMD_ALIAS, // });
				"-pt", ""+ACP_CLASSIFICATION_TYPE,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"-k", "loocv",
				"--result-format", "json",//
				"-cp", "0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9",
				"--seed", "41214",
		});
		if (PRINT_RESULTS)
			printLogs();
	}
	
	@Test
	public void testCrossValidateACPClassificationSingleSplit() throws Exception {
		mockMain(new String[]{
				CrossValidate.CMD_ALIAS, // });
				"-pt", ""+ACP_CLASSIFICATION_TYPE,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"-k", "random-split:test_fraction=0.3",
				"--result-format", "tsv",//
				"-cp", "0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9",
				"--seed", "41214",
		});
		if (PRINT_RESULTS)
			printLogs();
	}
	
	@Test
	public void testCrossValidateACPReg() throws Exception {
		mockMain(new String[]{
				CrossValidate.CMD_NAME, // });
				"-pt", ""+ACP_REGRESSION_TYPE,
				"-ds", Regression.getChang().getAbsolutePath(),
				"-k", "10",
				"--calibration-points","0.5,0.7,0.9",
				"--result-format", "tsv"	
		});
		if (PRINT_RESULTS)
			printLogs();
	}

	@Test
	public void testCrossValidateACPRegOneCalibPoint() throws Exception {
		mockMain(new String[]{
				CrossValidate.CMD_NAME, // });
				"-pt", ""+ACP_REGRESSION_TYPE,
				"-ds", Regression.getChang().getAbsolutePath(),
				"-k", "10",
				"--calibration-points","0.8",
				"--result-format", "text"	
		});
		if (PRINT_RESULTS)
			printLogs();
	}
	
	
	@Test
	public void testCrossValidateACPRegLOOCV() throws Exception {
		mockMain(new String[]{
				CrossValidate.CMD_NAME, // });
				"-pt", ""+ACP_REGRESSION_TYPE,
				"-ds", Regression.getChang().getAbsolutePath(),
				"-k", "loocv",
				"--calibration-points","0.5,0.7,0.9",
				"--result-format", "tsv"	
		});
		if (PRINT_RESULTS)
		printLogs();
	}
	
	@Test
	public void testCrossValidateACPRegSingleTestTrainSplit() throws Exception {
		mockMain(new String[]{
				CrossValidate.CMD_NAME, 
				"-pt", ""+ACP_REGRESSION_TYPE,
				"-ds", Regression.getChang().getAbsolutePath(),
				"--test-strategy", "random-split:test_fract=.3",
				"--calibration-points","0.5,0.7,0.9",
				"--result-format", "tsv"	
		});
		if (PRINT_RESULTS)
		printLogs();
	}
	
	@Test
	public void testNumFoldsOverNumRecords() throws Exception{
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("folds", "records"));

		mockMain( new String[]{
				CrossValidate.CMD_NAME,
				"-pt", ACP_REGRESSION_TYPE,
				"--cv-folds", "11", // One more than number of records
				"-ds", Regression.getSolu10().getAbsolutePath(),
				"-ss", strategy(RANDOM_SAMPLING, 10),
				"--nonconf-measure", "lognormalizedNCM:0.4",
//				"--nonconf-beta", "0.4",
				"--time",
				"-cp", "0.2 0.5 0.7 0.9",
				"-rf", "tsv"
		});
	}
	
	
	@Test
	public void testCrossValidateNoData() throws Exception {
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
//		exit.checkAssertionAfterwards(new PrintSysOutput());
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("required", "--data-set"));
		
		mockMain(new String[]{
				CrossValidate.CMD_NAME,
				"-pt", ""+ACP_CLASSIFICATION_TYPE,
				"-k", "10",
				"--result-format", "json"	
		});
	}
	
	@Test
	public void testFailingCVRunVennABERS() throws Exception{
		mockMain(new String[] {"crossvalidate",
				"-pt", CVAP_CLASSIFICATION_TYPE,
				"-k", "5",
				"--calibration-points","0.1:0.9:0.2",
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"--echo",
				"--seed", "1597821770306"
				});
	}
	

}

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

// import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.tests.suites.CLITest;
import com.arosbio.testutils.TestChemDataLoader.PreTrainedModels;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.Jsoner;

@Category(CLITest.class)
public class TestCheckVersion extends CLIBaseTest {

	private static boolean PRINT_OUTPUT = false;

	@Test
	public void testNoArgs() throws Exception{
		mockMain(ModelInfoCMD.CMD_NAME);
		if (PRINT_OUTPUT)
			printLogs();
	}

	@Test
	public void testVAPModel() throws Exception {
		loopTests(PreTrainedModels.CVAP_LIBLINEAR.toString());
	}

	@Test
	public void testACP_Class() throws Exception {
		loopTests(PreTrainedModels.ACP_CLF_LIBLINEAR.toString());
	}

	@Test
	public void testACP_Reg() throws Exception {
		loopTests(PreTrainedModels.ACP_REG_LIBSVM.toString());
	}

	@Test
	public void testTCP() throws Exception {
		loopTests(PreTrainedModels.TCP_CLF_LIBLINEAR.toString());
	}

	@Test
	public void testPrecomputed_Class() throws Exception {
		loopTests(PreTrainedModels.PRECOMP_CLF.toString());
	}

	@Test
	public void testPrecomputed_Reg() throws Exception {
		loopTests(PreTrainedModels.PRECOMP_REG.toString());
	}

	@Test
	public void testNumeric_Reg() throws Exception {
		loopTests(PreTrainedModels.NON_CHEM_REG.toString());
	}

	private void loopTests(String modelPath) throws Exception {
		doTest(modelPath, true, "json");
		doTest(modelPath, false, "json");
		doTest(modelPath, true, "text");
		doTest(modelPath, false, "text");
	}

	private void doTest(String modelpath, boolean doFull, String outFormat) throws Exception {
		mockMain(new String[] {
				ModelInfoCMD.CMD_NAME,
				"-m", modelpath,
				(doFull? "--verbose" : ""),
				"-rf", outFormat,
//				"--silent"

		});
		
		Map<String,Object> output = null;
		if (outFormat.equals("json")) {
			output = getData(systemOutRule.getLog().split("=-",2)[1], true);
		} else {
			output = getData(systemOutRule.getLog(), false);
		}
		
//		getData(systemOutRule.getLog(), (outFormat.equals("json")?true:false));

		if (doFull) {
			Assert.assertTrue(output.size() >5);
		} else {
			Assert.assertEquals(1, output.size());
		}

		Assert.assertTrue(output.containsKey("CPSign build version"));

		if (PRINT_OUTPUT)
			printLogs();

		systemErrRule.clearLog();
		systemOutRule.clearLog();
	}

	@SuppressWarnings("unchecked")
	private Map<String,Object> getData(String log, boolean isJSON) throws JsonException{
		if (isJSON) {
			String[] rows = log.split("\n");
			for(String row: rows) {
				if (row.isEmpty())
					continue;
				try {
					return (Map<String, Object>) Jsoner.deserialize(log); //new JSONParser().parse(log);
				} catch(Exception e) {
					e.printStackTrace();
					SYS_ERR.println("Failed for log="+log);
					Assert.fail();
				}
			}
			return null;
		} else {
			String[] rows = log.split("\n");
			Map<String,Object> data = new LinkedHashMap<>();
			
			int startInd = 0;
			for (int i=0; i<5; i++) {
				if (rows[i].isEmpty() || rows[i].contains(ModelInfoCMD.CMD_NAME.toUpperCase()))
					startInd++;
				else
					break;
			}

			Assert.assertTrue(rows[startInd].toLowerCase().startsWith("model info")); // info-line
			Assert.assertTrue(rows[startInd+1].contains("----")); // tidy output
			
			for (int i=startInd+2; i<rows.length; i++) {
				if (rows[i]== null || rows[i].isEmpty())
					continue;
				String[] parts = rows[i].split(":");
				String key = parts[0].trim();
				String value = parts[1].trim();
				data.put(key, value);
			}
			return data;
		}
	}
	


}

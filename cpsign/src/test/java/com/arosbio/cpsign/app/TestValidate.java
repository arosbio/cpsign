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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.chem.io.in.CSVChemFileReader;
import com.arosbio.chem.io.in.FailedRecord;
import com.arosbio.chem.io.in.MolAndActivityConverter;
import com.arosbio.chem.io.in.SDFile;
import com.arosbio.cheminf.ChemCPRegressor;
import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.data.ChemDataset.DescriptorCalcInfo;
import com.arosbio.cheminf.descriptors.ChemDescriptor;
import com.arosbio.cheminf.descriptors.DescriptorFactory;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.cpsign.app.params.CLIParameters.TextOutputType;
import com.arosbio.data.DataUtils;
import com.arosbio.data.NamedLabels;
import com.arosbio.data.transform.feature_selection.DropMissingDataSelector;
import com.arosbio.data.transform.filter.MissingDataFilter;
import com.arosbio.data.transform.impute.SingleFeatureImputer;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.TestResources.Reg;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.MockFailingDescriptor;
import com.arosbio.testutils.TestChemDataLoader.PreTrainedModels;

@Category(CLITest.class)
public class TestValidate extends CLIBaseTest{

	CSVCmpdData regSoluData = TestResources.Reg.getSolubility_100();
	CmpdData regChangData = TestResources.Reg.getChang();
	CmpdData clsData = TestResources.Cls.getAMES_126();

	final static String[] RESULT_OUTPUT_FORMAT = new String[] {
		TextOutputType.CSV.toString(), 
		TextOutputType.JSON.toString(), 
		TextOutputType.TEXT.toString(), 
		TextOutputType.TSV.toString() };
	final static boolean PRINT_RESULTS = false;

	// @Test
	public void testInfoAboutTCP(){
		mockMain(ModelInfoCMD.CMD_NAME , "-m", PreTrainedModels.TCP_CLF_SMALL_LIBLINEAR.toString(), "-v");
		printLogs();
	}

	@Test
	public void testMissingValidationEndpoint() throws Exception {
		mockMain(
				Validate.CMD_NAME,
				"-cp", "0.5 ", "0.7", "0.9",
				"-m", PreTrainedModels.ACP_REG_LIBSVM.toString(),
				"-p", regChangData.format(), regChangData.uri().toString(),
				"-rf", "csv",
				"--print-predictions",
				"-of", "csv",
				"--time",
				"--echo"
		);
		// Thread.sleep(100);
		if(PRINT_RESULTS)
			printLogs();
	}

	@Test
	public void testMissingAndDifferentEndpoint() throws Exception {
		File tmpPredFile = TestUtils.createTempFile("pred-file", ".sdf");

		// Replace the property with something else (so it doesn't exist)
		String chang = null;
		try(InputStream stream = regChangData.url().openStream();){
			chang = IOUtils.toString(stream, StandardCharsets.UTF_8);
		}
		String changAltered = chang.replace(regChangData.property(), "Property");
		FileUtils.write(tmpPredFile, changAltered, "UTF-8");

		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("invalid","argument"));
		exit.checkAssertionAfterwards(new AssertSysOutContainsString(tmpPredFile.getAbsolutePath(),regChangData.property(), "propert", "Error"));

		mockMain(new String[]{
				Validate.CMD_NAME,
				"-cp", "0.5 ", "0.7", "0.9",
				"-m", PreTrainedModels.ACP_REG_LIBSVM.toString(),
				"-p", SDFile.FORMAT_NAME, tmpPredFile.getAbsolutePath(),
				"-rf", "csv",
				"--print-predictions",
				"-of", "csv",
				"--list-failed",
				"--echo"
		});
	}

	@Test
	public void testArgs() throws Exception{
		mockMain(Validate.CMD_NAME);
		// printLogs();
	}

	@Test 
	public void testTCPClass() throws Exception {
		for (String s : RESULT_OUTPUT_FORMAT) {
			doTCPTest(s);
		}
		if(PRINT_RESULTS)
			printLogs();
	}

	
	private void doTCPTest(String format) throws Exception {
		mockMain(
				Validate.CMD_NAME,
				"-cp", "0.5 ", "0.7", "0.9",
				"-m",PreTrainedModels.TCP_CLF_SMALL_LIBLINEAR.toString(),
				"-p", clsData.format(), clsData.uri().toString(), 
				"-vp", clsData.property(),
				"--echo",
				"--print-predictions",
				//				"--progress-bar",
				"-rf", format,
				"--time"
		);
	}

	//	@Test
	public void testReadTestFile() throws IOException, Exception {
		CmpdData ames10 = TestResources.Cls.getAMES_10();
		SDFile sdf = new SDFile(ames10.uri());
		Assert.assertEquals(10, sdf.countNumRecords());
		NamedLabels nl = new NamedLabels();
		nl.addLabel(0, "mutagen");
		nl.addLabel(1, "nonmutagen");
		try(
				MolAndActivityConverter imar = MolAndActivityConverter.Builder.classificationConverter(sdf.getIterator(), ames10.property(), nl).build(); ){
			while(imar.hasNext()) {
				Pair<IAtomContainer, Double> i = imar.next();
				System.out.println("found mol with activity = " + i.getRight());
			}
		}
	}

	@Test 
	public void testACPReg() throws Exception {
		for (String s: RESULT_OUTPUT_FORMAT) {
			doTestACPReg(s);
		}
		if(PRINT_RESULTS)
			printLogs();
	}

	private void doTestACPReg(String format) throws Exception {
		mockMain(
				Validate.CMD_NAME,
				"-cp", "0.5 ", "0.7", "0.9",
				"-m", PreTrainedModels.ACP_REG_LIBSVM.toString(),
				"-p", regSoluData.format(), regSoluData.uri().toString(),
				"-vp", regSoluData.property(),
				"--progress-bar",
				"-rf", format,
				"--print-predictions",
				"-of", "csv",
				"--echo"
		);
		// Thread.sleep(100);
	}

	@Test
	public void testEarlyStopping() throws Exception {
		CSVCmpdData predFile = Reg.getErroneous();
		// by default all should work, but there are errors
		mockMain(
				Validate.CMD_NAME,
				"-cp", "0.5 ", "0.7", "0.9",
				"-m", PreTrainedModels.ACP_REG_LIBSVM.toString(),
				"-p", predFile.format(), "delim="+predFile.delim(), predFile.uri().toString(),
				"-vp", predFile.property(),
				// "--progress-bar",
				"-rf", "csv",
				"--print-predictions",
				"-of", "csv",
				"--echo",
				"--list-failed"
		);
		// printLogs();
		systemErrRule.clearLog();
		systemOutRule.clearLog();

		// Set a maximum number of failures, now it should fail (4 invalid records)
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("Invalid"));
		exit.checkAssertionAfterwards(new AssertSysOutContainsString("ERROR", "fail"));
		mockMain(
				Validate.CMD_NAME,
				"-cp", "0.5 ", "0.7", "0.9",
				"-m", PreTrainedModels.ACP_REG_LIBSVM.toString(),
				"-p", predFile.format(), "delim="+predFile.delim(), predFile.uri().toString(),
				"-vp", predFile.property(),
				// "--progress-bar",
				"-rf", "csv",
				"--print-predictions",
				"-of", "csv",
				"--echo",
				"--list-failed",
				"--early-termination", "1"
		);

	}


	@Test 
	public void testACPClass() throws Exception {
		for (String s: RESULT_OUTPUT_FORMAT) {
			doTestACPClass(s);
		}
		
		if(PRINT_RESULTS)
			printLogs();
	}

	private void doTestACPClass(String format) throws Exception {
		mockMain(new String[]{
				Validate.CMD_NAME,
				// "-cp", "0.5 ", "0.7", "0.9",
				"-m", PreTrainedModels.ACP_CLF_LIBLINEAR.toString(),
				"-p", clsData.format(), clsData.uri().toString(),
				"-vp", clsData.property(),
				"--progress-bar",
				"--print-predictions",
				"-rf", format,
				"--echo",
				"--verbose",
				
				
		});
		// Thread.sleep(100);

	}

	@Test 
	public void testAVAPClass() throws Exception {
		for (String s: RESULT_OUTPUT_FORMAT) {
			// SYS_ERR.println("cvap :"+s);
			doTestAVAPClass(s);
		}
		if(PRINT_RESULTS)
			printLogs();
	}

	private void doTestAVAPClass(String format) throws Exception {
		mockMain(new String[]{
				Validate.CMD_NAME,
				// "-cp", "0.5 ", "0.7", "0.9",
				"-m", PreTrainedModels.CVAP_LIBLINEAR.toString(),
				"-p", clsData.format(), clsData.uri().toString(),
				"-vp", clsData.property(),
				"--progress-bar",
				"--print-predictions",
				"-rf", format,
				"--roc",
				"--echo"
		});
		// Thread.sleep(100);
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testFailingDescriptors() throws Exception {
		
		DescriptorFactory factory = DescriptorFactory.getInstance();
		Class<DescriptorFactory> cls = DescriptorFactory.class;
		Field f = cls.getDeclaredField("allDescriptors");
		f.setAccessible(true);
		List<ChemDescriptor> descriptors = (List<ChemDescriptor>) f.get(factory);
		descriptors.add(new MockFailingDescriptor());
		
		
		List<ChemDescriptor> desc = new ArrayList<>();
		// Use 10 standard CDK ones 
		desc.addAll(DescriptorFactory.getCDKDescriptorsNo3D().subList(0, 10));
		// And the failing one
		desc.add(new MockFailingDescriptor());
		Collections.shuffle(desc); // Shuffle to make it more realistic
		
		
		ChemDataset ds = new ChemDataset(desc);
		ds.initializeDescriptors();
		try (
				InputStream in = regSoluData.url().openStream();
				InputStreamReader reader = new InputStreamReader(in);
				CSVChemFileReader iterator = new CSVChemFileReader(CSVFormat.TDF.builder().setHeader().setSkipHeaderRecord(true).build(), reader);
						){
			DescriptorCalcInfo info = ds.add(iterator, regSoluData.property());
			if (info.getFailedRecords() != null)
				for (FailedRecord fr: info.getFailedRecords())
					System.out.println(fr);

		}
		// with a 20% chance of failure we should have some missing ones
		Assert.assertTrue(DataUtils.containsMissingFeatures(ds));
		
		// Verify that training a model with this FAILS
		try {
			ACPRegressor acp = getACPRegressionAbsDiff(true, true);
			acp.train(ds);
			Assert.fail("Should not be able to train using missing data features");
		} catch (Exception e) {}
		
		
		
		// Attempt 1 - use transformation to remove only the examples with missing features
		// This should fail some observations at predict-time
		ChemDataset rmNaNExamples = ds.clone();
		rmNaNExamples.apply(new MissingDataFilter());
		runMissingDataTests(rmNaNExamples, true, true);
		
		// Attempt 2 - remove the Features which contains MissingFeatures
		// This is applied to future observations and should work without issues
		ChemDataset rmMissingFeatures = ds.clone();
		rmMissingFeatures.apply(new DropMissingDataSelector());
		runMissingDataTests(rmMissingFeatures, false, false);
		
		// Attempt 3 - Impute the missing features - this should be applied
		// to future objects and work as well!
		ChemDataset imputeMissingFeatures = ds;
		imputeMissingFeatures.apply(new SingleFeatureImputer());
		runMissingDataTests(imputeMissingFeatures, false, false);
		
	}
	
	private void runMissingDataTests(ChemDataset ds, boolean shouldFail, boolean list) throws IOException, InvalidKeyException, IllegalArgumentException {
		
		File tmpTrainedModel = TestUtils.createTempFile("tmp", ".jar");
		File tmpPredOut = TestUtils.createTempFile("preds", ".txt");
		ACPRegressor acp = getACPRegressionAbsDiff(true, true);
		ChemCPRegressor predictor = new ChemCPRegressor(acp);
		predictor.setDataset(ds);
		predictor.train();
		
		// Save it
		ModelSerializer.saveModel(predictor, tmpTrainedModel, null);
		
		clearLogs();
		
		// Predict 
		mockMain(Predict.CMD_NAME,
				"-m", tmpTrainedModel.getAbsolutePath(),
				"-p", regChangData.format(), regChangData.uri().toString(),
				"-co","0.8",
				"-o",tmpPredOut.getAbsolutePath(),
				(list? "--list-failed" : ""),
				"--echo"
				);
		if (PRINT_RESULTS)
			printLogs();
		
		if (shouldFail) {
			String log = systemOutRule.getLog();
			Assert.assertTrue(log.contains("Failed predicting"));
		}
		
		
		clearLogs();
		
		// Validate
		mockMain(Validate.CMD_NAME,
				"-m", tmpTrainedModel.getAbsolutePath(),
				"-p", regSoluData.format(), regSoluData.uri().toString(),
				(list? "--list-failed" : ""),
				"--echo"
				);
		if (PRINT_RESULTS)
			printLogs();
		
		if (shouldFail) {
			String log = systemOutRule.getLog();
			Assert.assertTrue(log.contains("Failed predicting"));
		}
		
	}
	

}

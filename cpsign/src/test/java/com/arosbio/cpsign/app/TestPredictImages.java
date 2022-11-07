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

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.chem.io.in.SDFReader;
import com.arosbio.cheminf.ChemCPClassifier;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.cpsign.app.PrecomputedDatasets.Classification;
import com.arosbio.data.NamedLabels;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.TestChemDataLoader.PreTrainedModels;


@Category(CLITest.class)
public class TestPredictImages extends CLITestReqOutputDir {


	private static final String SMILESToPredict="CC(C(=O)NC1=CC=C(C=C1)C(F)(F)F)N(C)CC(=O)N2CCN(CC2)CC3=CC=CC=C3";
	private static final int nrModels = 3;

	private static final String imageFolder = new File(TEST_DIR_FILE, "generatedImages").getAbsolutePath();

	private static final String imageSize = "200";

	// private static final String predictSDF = AmesBinaryClass.MINI_FILE_PATH;
	private static final CSVCmpdData solu_100 = TestResources.Reg.getSolubility_100();
	private static final CmpdData predictFile = TestResources.Cls.getAMES_10();
	private static final CmpdData ames_126 = TestResources.Cls.getAMES_126();

	@Test
	public void testTCPClassification() throws IOException{
		
		File dataFile = TestUtils.createTempFile("data", ".jar");
		mockMain(Precompute.CMD_NAME,
				"-td", ames_126.format(), ames_126.uri().toString(),
				"--property", ames_126.property(),
				"-l", getLabelsArg(ames_126.labels()),
				"-mo", dataFile.getAbsolutePath(),
				"--descriptors", "signatures", "xlogPdescriptor", "Rule-Of-Five-ChemDescriptor", "MDEDescriptor"// Add additional descriptor
				);

		mockMain(
				PredictOnline.CMD_NAME,
				"-ds", dataFile.getAbsolutePath(),
				"--smiles", SMILESToPredict,
				"-gi",
				"-gi:o", imageFolder + "/tcp.png",
				"--gi:height", imageSize,
				"--gi:width", imageSize,
				"--gi:legend"
				);
			
	
		mockMain(
				PredictOnline.CMD_NAME,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"--smiles", SMILESToPredict,
				"-si",
				"-si:o", imageFolder+"/tcpRainbow.png",
				"-si:h", imageSize,
				"-si:w", imageSize,
				"--si:legend"
		);

		// Predict 
		mockMain(
				Predict.CMD_NAME,
				"-m", PreTrainedModels.TCP_CLF_SMALL_LIBLINEAR.toString(),
				"--smiles", SMILESToPredict,
				"-gi",
				"-gi:o", imageFolder+"/tcp-with-trained.png",
				"--gi:height", imageSize,
				"--gi:width", imageSize
		);
		
	}

	@Test
	public void testACPRegression() throws Exception {

		mockMain(
				Predict.CMD_NAME,
				"-m", PreTrainedModels.ACP_REG_LIBSVM.toString(),
				"-p", predictFile.format(), predictFile.uri().toString(),
				"--confidences", "0.7",
				"-gi",
				"-gi:o", imageFolder+"/acpReg.png",
				"--gi:legend",
				"--gi:height", imageSize,
				"-gi:w", imageSize

		);
	

		mockMain(
				Predict.CMD_NAME,
				"-m", PreTrainedModels.ACP_REG_LIBSVM.toString(),
				"-p", predictFile.format(), predictFile.uri().toString(),
				"--confidences", "0.7",
				"-si",
				"-si:o", imageFolder+"/acpReg_SignSignOnly.png",
				"--si:legend",
				"-si:h", imageSize,
				"-si:w", imageSize
		);


	}

	@Category(PerformanceTest.class)
	@Test
	public void testUsingAdditionalDescriptors() throws Exception {
		// Precompute first
		File dataFile = TestUtils.createTempFile("data", ".svm.jar");
		mockMain(
				Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_REGRESSION,
				"-td", solu_100.format(), solu_100.uri().toString(),
				"-pr", solu_100.property(),
				"-mo", dataFile.getAbsolutePath(),
				"-mn", "fds",
				"--descriptors", "signatures", "xlogPdescrip", "MDEDescriptor"
		);

		
		// Train first
		File modelFile = TestUtils.createTempFile("model", ".svm.jar");

		mockMain(
				Train.CMD_NAME,
				"-pt", ACP_REGRESSION_TYPE,
				"--data-set", dataFile.getAbsolutePath(),
				"-ss", strategy(RANDOM_SAMPLING, nrModels),
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "fds",
				"--percentiles","50",
				"--percentiles-data", solu_100.format(), solu_100.uri().toString()
		);
		

		mockMain(
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"-p",predictFile.format(), predictFile.uri().toString(),
				"--confidences", "0.7",
				"-gi",
				"-gi:o", imageFolder+"/acpReg.png",
				"--gi:legend",
				"--gi:height", imageSize,
				"--gi:width", imageSize
		);
		
	}


	@Test
	public void testPredictWhenNoSignaturesDescriptorsShouldFail() throws Exception {
		// Precompute
		File dataFile = TestUtils.createTempFile("model", ".svm.jar");

		mockMain(
				Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_REGRESSION,
				"-td", solu_100.format(), solu_100.uri().toString(),
				//  RegressionSolubility.FILE_FORMAT, RegressionSolubility.SOLUBILITY_100_FILE_PATH,
				"-pr", solu_100.property(), // RegressionSolubility.PROPERTY,
				"-mo", dataFile.getAbsolutePath(),
				"-mn", "fds",
				"--descriptors", "xlogPDescriptor", "MDEDescriptor"
		);

		
		// Train first
		File modelFile = TestUtils.createTempFile("model", ".svm.jar");

		try{
			mockMain(new String[] {
					Train.CMD_NAME,
					"-pt", "2",
					"-ds", dataFile.getAbsolutePath(),
					"-ss", strategy(RANDOM_SAMPLING, 1),
					"-mo", modelFile.getAbsolutePath(),
					"-mn", "fds",
			});
		} catch(Exception e){
			e.printStackTrace();
			Assert.fail();
		}

		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("percentiles", "image", "computed"));
		
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"-p",predictFile.format(), predictFile.uri().toString(),
				"--confidences", "0.7",
				"-gi",
				"-gi:o", imageFolder+"/acpRegNoSignatures.png",
				"--gi:legend",
				"--gi:height", imageSize,
				"--gi:width", imageSize,

		});
		
//		printLogs();

	}


	@Test
	public void testACPClassification() throws Exception {

		// Then predict
		try{
			mockMain(new String[] {
					Predict.CMD_NAME,
					"-m", PreTrainedModels.ACP_CLF_LIBLINEAR.toString(), 
					"--smiles",SMILESToPredict,
					"-gi",
					"--gi:height",imageSize,
					"--gi:width", imageSize,
					"--confidences", "0.1",
					"-gi:o", imageFolder +"/acpClassRainbow.png",
					"-gi:cs", "rainbow",
					"--gi:legend",
			});
		} catch(Exception e){
			e.printStackTrace();
			Assert.fail();
		}

		// Then predict
		try{
			mockMain(new String[] {
					Predict.CMD_NAME,
					"-m", PreTrainedModels.ACP_CLF_LIBLINEAR.toString(),
					"--smiles",SMILESToPredict,
					"-gi",
					"--gi:height", imageSize,
					"--gi:width", imageSize,
					"--confidences", "0.1",
					"-gi:o", imageFolder +"/acpClassBlueRed.png", 
					"-gi:cs", "blue:red",
					"--gi:legend",
					"--gi:atom-numbers", "forestgreen",
			});
		} catch(Exception e){
			e.printStackTrace();
			Assert.fail();
		}

		// Then predict SignificantSignature only
		try{
			mockMain(new String[] {
					Predict.CMD_NAME,
					"-m", PreTrainedModels.ACP_CLF_LIBLINEAR.toString(),
					"-p",predictFile.format(), predictFile.uri().toString(),
					"--confidences", "0.1",
					"-si",
					"-si:o", imageFolder +"/acpClassSSOnly.png",
					"-si:c", "orange",
					"--si:legend",
					"--si:height", imageSize,
					"--si:width", imageSize,
			});
		} catch(Exception e){
			e.printStackTrace();
			Assert.fail();
		}

	}

	@Test
	public void testCVAPClassification() throws Exception {

		// Then predict
		try{
			mockMain(new String[] {
					Predict.CMD_NAME,
					"-m", PreTrainedModels.CVAP_LIBLINEAR.toString(),
					"--smiles",SMILESToPredict,
					"-gi",
					"--gi:height",imageSize,
					"--gi:width", imageSize,
					"--confidences", "0.1",
					"-gi:o", imageFolder +"/cvapGradientRainbow.png",
					"-gi:cs", "rainbow",
					"--gi:legend",
			});
		} catch(Exception e){
			e.printStackTrace();
			Assert.fail();
		}

		// Then predict
		try{
			mockMain(new String[] {
					Predict.CMD_NAME,
					"-m", PreTrainedModels.CVAP_LIBLINEAR.toString(),
					"--smiles",SMILESToPredict,
					"-gi",
					"--gi:height", imageSize,
					"--gi:width", imageSize,
					"--confidences", "0.1",
					"-gi:o", imageFolder +"/cvapGradientBlueRed.png",
					"-gi:cs", "blue:red",
					"--gi:legend",
					"--gi:atom-numbers",
			});
		} catch(Exception e){
			e.printStackTrace();
			Assert.fail();
		}

		// Then predict SignificantSignature only
		try{
			mockMain(new String[] {
					Predict.CMD_NAME,
					"-m", PreTrainedModels.CVAP_LIBLINEAR.toString(),
					"-p",predictFile.format(), predictFile.uri().toString(),
					"--confidences", "0.1",
					"-si",
					"-si:o", imageFolder +"/cvapSS.png",
					"-si:c", "green",
					"--si:legend",
					"--si:height", imageSize,
					"--si:width", imageSize,
			});
		} catch(Exception e){
			e.printStackTrace();
			Assert.fail();
		}

	}



	@Test
	public void testACPClassificationNoPercentilesComputed () throws Exception {
		// Train first
		File modelFile = TestUtils.createTempFile("model", ".svm.jar");

		ChemCPClassifier sigacp = new ChemCPClassifier(getACPClassificationNegDist(true, true)); 
		sigacp.addRecords(new SDFReader(ames_126.url().openStream()), ames_126.property(), new NamedLabels(ames_126.labelsStr()));
		sigacp.train(); 
		ModelSerializer.saveModel(sigacp, modelFile, null);

		sigacp = (ChemCPClassifier) ModelSerializer.loadChemPredictor(modelFile.toURI(), null);

		// Then predict - should fail!
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("percentiles", "image", "computed"));
		
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFile.getAbsolutePath(),
				"--smiles",SMILESToPredict,
				"-gi",
				"--confidences", "0.1",
				"-gi:o", imageFolder +"/acpClasNormal.png",
				"-gi:cs", "rainbow"
		});

//		Assert.assertTrue("There should be a warning printed for non-computed percentiles",systemErrRule.getLog().length() > 10);

//		printLogs();
	}


}

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cheminf;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.chem.io.in.CSVFile;
import com.arosbio.chem.io.in.SDFile;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.nonconf.regression.NormalizedNCM;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.cp.CalibrationPlot;
import com.arosbio.ml.sampling.FoldedSampling;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.UnitTestBase;


/**
 * 
 * @author ola
 *
 */
@Category(UnitTest.class)
public class TestACPRegressionCrossValidation extends UnitTestBase{

	final int nrModels=5;

	private final double calibrationRatio = 0.2;
	private final double confidence=0.8;
	private final int cvFolds=10;
	
	private static final CmpdData LARGE_DATASET = TestResources.Reg.getGluc();
	private static final CSVCmpdData SMALL_DATASET = TestResources.Reg.getSolubility_100();

	@Test
	@Category(PerformanceTest.class)
	public void TestLibSVMACP_large() throws Exception {
		doLibSVMACP(LARGE_DATASET);
	}
	@Test
	public void TestLibSVMACP_small() throws Exception {
		doLibSVMACP(SMALL_DATASET);
	}
	public void doLibSVMACP(CmpdData dataset) throws Exception{
		System.out.println("=== Testing LIBSVM ACP===");
		ACPRegressor libsvmacp = new ACPRegressor(new NormalizedNCM(new EpsilonSVR(),null), 
				new RandomSampling(nrModels, calibrationRatio));
		ChemCPRegressor acp = new ChemCPRegressor(libsvmacp, 1, 3);
		TestACPRegressionCV(acp, dataset);
	}


	@Test
	@Category(PerformanceTest.class)
	public void TestLibLinearACP_large() throws Exception{
		doLibLinearACP(LARGE_DATASET);
	}
	@Test
	public void TestLibLinearACP_small() throws Exception{
		doLibLinearACP(SMALL_DATASET);
	}
	public void doLibLinearACP(CmpdData dataset) throws Exception {
		System.out.println("=== Testing LIBLINEAR ACP===");
		ACPRegressor libsvmacp = new ACPRegressor(new NormalizedNCM(new LinearSVR(),null), 
				new RandomSampling(nrModels, calibrationRatio));
		ChemCPRegressor acp = new ChemCPRegressor(libsvmacp, 1, 3);
		TestACPRegressionCV(acp, dataset);
	}

	@Test
	@Category(PerformanceTest.class)
	public void TestLibLinearCCP_large() throws Exception{
		doLibLinearCCP(LARGE_DATASET);
	}
	@Test
	public void TestLibLinearCCP_small() throws Exception{
		doLibLinearCCP(SMALL_DATASET);
	}
	
	public void doLibLinearCCP(CmpdData dataset) throws Exception {
		System.out.println("=== Testing LIBLINEAR CCP===");
		ACPRegressor acpImpl = new ACPRegressor(new NormalizedNCM(
				new LinearSVR(), null),
				new FoldedSampling(cvFolds));
		ChemCPRegressor signCCP = new ChemCPRegressor(acpImpl, 1, 3);
		TestACPRegressionCV(signCCP, dataset);
	}

	@Test
	@Category(PerformanceTest.class)
	public void TestLibSVMCCP_large() throws Exception{
		doLibSVMCCP(LARGE_DATASET);
	}
	@Test
	public void TestLibSVMCCP_small() throws Exception{
		doLibSVMCCP(SMALL_DATASET);
	}
	
	public void doLibSVMCCP(CmpdData dataset) throws Exception{
		System.out.println("=== Testing LIBSVM CCP===");
		ACPRegressor acpImpl = new ACPRegressor(new NormalizedNCM(new EpsilonSVR(), null), new FoldedSampling(cvFolds));
		ChemCPRegressor signCCP  = new ChemCPRegressor(acpImpl, 1, 3);
		TestACPRegressionCV(signCCP, dataset);
	}

	public void TestACPRegressionCV(ChemCPRegressor signCCP, CmpdData dataset) throws Exception{

		if (dataset instanceof CSVCmpdData) {
			CSVCmpdData csv = (CSVCmpdData) dataset;
			signCCP.addRecords(new CSVFile(dataset.uri()).setDelimiter(csv.delim()).getIterator(), dataset.property());
		} else {
			signCCP.addRecords(new SDFile(dataset.uri()).getIterator(), dataset.property());
		}
		signCCP.getDataset(); 
		
		TestRunner cv = new TestRunner.Builder(new KFoldCV(cvFolds)).evalPoints(Arrays.asList(confidence)).build();
		
		List<Metric> cvres = cv.evaluate(signCCP.getDataset(),signCCP.getPredictor());
		double acc = -1;
		for (Metric m: cvres) {
			if (m instanceof CalibrationPlot)
				acc = ((CalibrationPlot) m).getAccuracy(confidence);
		}
		
		System.out.println(cvres);
		if (dataset.equals(LARGE_DATASET))
			Assert.assertEquals(acc, confidence, 0.025);
		
	}




}

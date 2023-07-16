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
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.chem.io.in.SDFile;
import com.arosbio.data.NamedLabels;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.MetricFactory;
import com.arosbio.ml.metrics.cp.CalibrationPlot;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.GzipEncryption;
import com.arosbio.testutils.UnitTestBase;


/**
 * 
 * @author ola
 *
 */
@Category(UnitTest.class)
public class TestACPClassificationCrossValidation extends UnitTestBase{

	EncryptionSpecification spec, spec2;
	int nrModels=5;
	double calibrationRatio=.2;

	double confidence=0.7;

	private int cvFolds=10;

	@Before
	public void init(){
		spec = new GzipEncryption("some password and salt");
		spec2 = new GzipEncryption("another password and different salt");
	}

	@Test
	public void TestLibSVMACP() throws Exception{
		System.out.println("=== Testing LIBSVM ACP===");
		ACPClassifier libsvmacp = getACPClassificationNegDist(false, true); 
		ChemCPClassifier acp = new ChemCPClassifier(libsvmacp, 1, 3);
		TestACPClassificationCV(acp);
	}


	@Test
	public void TestLibLinearACP() throws Exception{
		System.out.println("=== Testing LIBLINEAR ACP===");
		ACPClassifier libsvmacp = getACPClassificationNegDist(true, true);
		ChemCPClassifier acp = new ChemCPClassifier(libsvmacp, 1, 3);
		TestACPClassificationCV(acp);

	}

	@Test
	public void TestLibLinearCCP() throws Exception{
		System.out.println("=== Testing LIBLINEAR CCP===");
		ACPClassifier acpImpl = getACPClassificationNegDist(true, false);
		ChemCPClassifier signCCP = new ChemCPClassifier(acpImpl, 1, 3);
		TestACPClassificationCV(signCCP);
	}

	@Test
	public void TestLibSVMCCP() throws Exception{
		System.out.println("=== Testing LIBSVM CCP===");
		
		ACPClassifier ccpImpl = getACPClassificationNegDist(false, false); 
		ChemCPClassifier signCCP = new ChemCPClassifier(ccpImpl, 1, 3);
		TestACPClassificationCV(signCCP);
	}

	public void TestACPClassificationCV(ChemCPClassifier signCCP) throws Exception{
		CmpdData sdf = TestResources.Cls.getHERG();
		
		signCCP.addRecords(new SDFile(sdf.uri()).getIterator(), sdf.property(),new NamedLabels(sdf.labelsStr()));
		signCCP.getDataset();

		TestRunner runner = new TestRunner.Builder(new KFoldCV(cvFolds)).build();
		List<Metric> metrics = MetricFactory.getMetrics(signCCP.getPredictor(),false);
		MetricFactory.setEvaluationPoints(metrics, Arrays.asList(confidence));
		List<? extends Metric> acc = runner.evaluate(signCCP.getDataset(),signCCP.getPredictor(), metrics);
		for (Metric m: acc) {
			System.out.println(m);
			if (m instanceof CalibrationPlot)
				Assert.assertEquals(((CalibrationPlot) m).getAccuracy(confidence),confidence, 0.06);
		}
	}




}

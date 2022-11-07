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

import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.chem.io.in.CSVFile;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor.SignatureType;
import com.arosbio.commons.Stopwatch;
import com.arosbio.data.NamedLabels;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.testutils.UnitTestBase;

@Category(PerformanceTest.class)
public class TestSignatureGenerationNormalVsStereoChemistry extends UnitTestBase {


	final private int cvFolds=10;
	final private double confidence=.8;

	@Test
	public void TestPerformaceForLargeDataset() throws IllegalArgumentException, IOException, Exception{
		CSVCmpdData cas_data = TestResources.Cls.getCAS_N6512();

		ChemCPClassifier normal = new ChemCPClassifier(getACPClassificationNegDist(true,true));
		ChemCPClassifier stereo = new ChemCPClassifier(((ACPClassifier) normal.getPredictor()).clone());
		SignaturesDescriptor stereoDesc = new SignaturesDescriptor();
		stereoDesc.setSignaturesType(SignatureType.STEREO);
		stereo.getDataset().setDescriptors(Arrays.asList(stereoDesc)); //setSignaturesGenerator(new SignaturesGeneratorStereo());

		Stopwatch watch = new Stopwatch();

		watch.start();
		normal.addRecords(new CSVFile(cas_data.uri()).getIterator(), cas_data.property(), new NamedLabels(cas_data.labelsStr()));
		watch.stop();
		System.out.println("Normal time= "+watch.toString());

		watch.start();
		stereo.addRecords(new CSVFile(cas_data.uri()).getIterator(), cas_data.property(), new NamedLabels(cas_data.labelsStr()));
		watch.stop();
		System.out.println("Stereo time= "+watch.toString());

		// Check performance
		TestRunner cv = new TestRunner.Builder(new KFoldCV(cvFolds)).evalPoints(Arrays.asList(confidence)).build();
		System.out.println("NORMAL:\n"+cv.evaluate(normal.getDataset(),normal.getPredictor())); 
		System.out.println("STEREO:\n"+cv.evaluate(stereo.getDataset(),normal.getPredictor())); 
		printLogs();

	}
}

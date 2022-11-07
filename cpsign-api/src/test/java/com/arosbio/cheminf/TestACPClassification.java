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


import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import com.arosbio.chem.io.in.CSVFile;
import com.arosbio.chem.io.in.MolAndActivityConverter;
import com.arosbio.chem.io.in.SDFile;
import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector.Feature;
import com.arosbio.data.NamedLabels;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.NonSuiteTest;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.UnitTestBase;

/**
 * 
 * @author ola
 *
 */
@Category(UnitTest.class)
public class TestACPClassification extends UnitTestBase {

	final int nrModels = 5;
	final double calibrationRatio = 0.2;
	private static long seed=42;


	@Test
	@Category(PerformanceTest.class)
	public void testACPClassification() throws Exception {

		CmpdData trainData = TestResources.Cls.getAMES_2497();
		CmpdData testData = TestResources.Cls.getAMES_1337();

		ACPClassifier acp = getACPClassificationNegDist(true, true);
		acp.setSeed(seed);
		ChemCPClassifier signacp = new ChemCPClassifier(acp);
		ChemCPClassifier signClass = new ChemCPClassifier(acp.clone());


		signacp.addRecords(new SDFile(trainData.uri()).getIterator(), trainData.property(), new NamedLabels(trainData.labelsStr()));
		signClass.setDataset(signacp.getDataset());
		signClass.setLabels(signacp.getLabels());

		System.out.println("Training " + nrModels + " ACP models");
		signacp.train();
		signClass.train();
		
		
		// This is probably not correct I'd say - a higher s
		int singletons = predictForSignificance(signacp, signClass, getClassIter(testData), 0.1);
		assertTrue(singletons > 800);

		singletons = predictForSignificance(signacp, signClass, getClassIter(testData), 0.2);
		assertTrue(singletons > 700);

		singletons = predictForSignificance(signacp, signClass, getClassIter(testData), 0.3);
		assertTrue(singletons > 500);

		singletons = predictForSignificance(signacp, signClass, getClassIter(testData), 0.5);
		assertTrue(singletons > 300);
		
		singletons = predictForSignificance(signacp, signClass, getClassIter(testData), 0.8);
		assertTrue(singletons > 50);

	}
	
	private MolAndActivityConverter getClassIter(CmpdData test) throws Exception {
		return MolAndActivityConverter.classificationConverter(new SDFile(test.uri()).getIterator(), test.property(), new NamedLabels(test.labelsStr()));
	}

	@Test
	@Category(PerformanceTest.class)
	public void TestCCPClassification() throws Exception {
		CmpdData trainData = TestResources.Cls.getAMES_2497();
		CmpdData testData = TestResources.Cls.getAMES_1337();


		ACPClassifier ccp = getACPClassificationNegDist(true, false); 
		
		ccp.setSeed(seed+124);
		ChemCPClassifier signacp = new ChemCPClassifier(ccp);
		ChemCPClassifier signClass = new ChemCPClassifier(ccp.clone());

		signacp.addRecords(new SDFile(trainData.uri()).getIterator(), trainData.property(), new NamedLabels(trainData.labelsStr()));
		signClass.setDataset(signacp.getDataset().clone());
		signClass.setLabels(signacp.getLabels());

		System.out.println("Training CCP with " + nrModels + " folds");
		signacp.train();
		signClass.train();

		int singletons = predictForSignificance(signacp, signClass, getClassIter(testData), 0.1);
		assertTrue(singletons > 300);

		singletons = predictForSignificance(signacp, signClass, getClassIter(testData), 0.2);
		assertTrue(singletons > 400);

		singletons = predictForSignificance(signacp, signClass, getClassIter(testData), 0.3);
		assertTrue(singletons > 400);

		singletons = predictForSignificance(signacp, signClass, getClassIter(testData), 0.5);
		assertTrue(singletons > 300);

	}
	

	@Test
	public void TestACPClassificationGradient() throws Exception {
		CmpdData trainData = TestResources.Cls.getAMES_2497();

		ACPClassifier acp = getACPClassificationNegDist(true, true); 
		ChemCPClassifier signacp = new ChemCPClassifier(acp);
		signacp.addRecords(new SDFile(trainData.uri()).getIterator(), trainData.property(), new NamedLabels(trainData.labelsStr()));

		System.out.println("Training " + nrModels + " ACP models");
		signacp.train();

		// Construct a new molecule
		SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
		IAtomContainer mol = parser.parseSmiles("Cc1cnc(c(c1OC)C)CS(=O)c2[nH]c3ccc(cc3n2)OC");

		SignificantSignature signSign = signacp.predictSignificantSignature(mol);

		System.out.println("Sign sign: " + signSign);

		assertTrue(signSign != null);
		assertTrue(signSign.getSignature() != null);
		assertTrue(signSign.getSignature().length() > 5);
		assertTrue(signSign.getAtomContributions().size() == mol.getAtomCount());
		assertTrue(signSign.getHeight() > 0);

		// Assert at least one non-zero atom value
		boolean nonZeros = false;
		for (double d : signSign.getAtomContributions().values()) {
			if (d != 0.0)
				nonZeros = true;
		}
		assertTrue(nonZeros);

	}

	private int predictForSignificance(ChemCPClassifier signacp, ChemCPClassifier sigClass,
			MolAndActivityConverter molReader, double significance) throws CDKException, IllegalAccessException {
		int correctSingletons = 0;
		int noMols = 0;
		int correct = 0;
		int singletons = 0;
		while (molReader.hasNext()) {
			noMols++;
			Pair<IAtomContainer, Double> nextmol = molReader.next();

			Map<String, Double> result = signacp.predict(nextmol.getLeft());
			Map<String, Double> result_newAPI = sigClass.predict(nextmol.getLeft());

			// System.out.println("pvals=" + pvals
			// + "obs=" + nextmol.getValue1());

			// If observed class == 0
			if (Math.abs(nextmol.getRight()) < 0.00001) {

				if (result.get(signacp.getLabels().get(0)) > significance) {
					correct++;

					if (result.get(signacp.getLabels().get(1)) < significance)
						// singleton class 0
						correctSingletons++;
				}
			}

			// If observed class == 1
			else if (Math.abs(nextmol.getRight() - 1) < 0.00001) {

				if (result.get(signacp.getLabels().get(1)) > significance) {
					correct++;

					if (result.get(signacp.getLabels().get(0)) < significance)
						correctSingletons++;
				}
			}
			
			int numInPredSet = 0;
			for (double pval : result.values()) {
				if (pval > significance)
					numInPredSet++;
			}
			if (numInPredSet == 1)
				singletons++;

			// if(significance > 0.7)
			for (String label : result.keySet()) {
				if (Math.abs(result.get(label) - result_newAPI.get(label)) > 0.00001)
					System.err.println("Old: " + result.get(label) + ", New: " + result_newAPI.get(label));
			}

		}

		System.out.println("Significance: " + significance + " ; No mols: " + noMols + " ; Correct predictions: "
				+ correct + " ; Correct singletons: " + correctSingletons + " ; Singletons: " + singletons);

//		return correctSingletons;
		return singletons;
	}

	@Test
	public void TestACPClassificationTrainSaveLoad() throws Exception {
		CmpdData trainData = TestResources.Cls.getAMES_126();

		
		ACPClassifier acp = getACPClassificationNegDist(false, true); 
		ChemCPClassifier signacp = new ChemCPClassifier(acp);

		signacp.addRecords(new SDFile(trainData.uri()).getIterator(), trainData.property(),new NamedLabels(trainData.labelsStr()) );
//		signacp.fromChemFile(molFile.toURI(), PROPERTY, AMES_LABELS);

		// Test train both, but simply test results from CCP later
		signacp.train();
		signacp.setPredictor(getACPClassificationNegDist(false, false));
		signacp.train();

		// Construct a new molecule
		SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
		IAtomContainer mol = parser.parseSmiles("Cc1cnc(c(c1OC)C)CS(=O)c2[nH]c3ccc(cc3n2)OC");

		Map<String, Double> result1 = signacp.predict(mol);
		Assert.assertEquals(2, result1.size());

	}
	
	
	@Test
	@Category(NonSuiteTest.class)
	public void testFeatureScales() throws Exception {
		CSVCmpdData screenData = TestResources.Cls.getScreen_U251();
		ChemDataset sp = new ChemDataset();
		sp.initializeDescriptors();
		sp.add(new CSVFile(screenData.uri()).getIterator(), screenData.property(), new NamedLabels(screenData.labelsStr()) );
		System.err.println("ds.size=" + sp.getNumRecords());
		Dataset prob = (Dataset) sp;
		Map<Integer,Integer> histogram = new HashMap<>();
		
		SubSet ds = prob.getDataset();
		for (DataRecord r : ds) {
			for (Feature f : r.getFeatures()) {
				histogram.put((int)f.getValue(), 1+histogram.getOrDefault((int)f.getValue(), 0));
			}
		}
		
		System.out.println(histogram);
		
		List<Integer> xAxis = new ArrayList<>(histogram.keySet());
		Collections.sort(xAxis);
		
		StringBuilder sb = new StringBuilder();
		for (Integer x: xAxis) {
			sb.append(x);
			sb.append('\t');
			sb.append(histogram.get(x));
			sb.append('\n');
		}
		
		System.out.println(sb.toString());
	}

}

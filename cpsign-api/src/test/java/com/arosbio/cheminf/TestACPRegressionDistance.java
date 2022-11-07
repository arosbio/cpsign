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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import com.arosbio.chem.io.in.CSVFile;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.cp.CPRegressionPrediction;
import com.arosbio.ml.cp.CPRegressionPrediction.PredictedInterval;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.nonconf.regression.NormalizedNCM;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.tests.utils.GzipEncryption;
import com.arosbio.testutils.UnitTestBase;

/**
 * 
 * @author ola
 *
 */
@Category(PerformanceTest.class)
public class TestACPRegressionDistance extends UnitTestBase{

	ChemCPClassifier cpsign;
	EncryptionSpecification spec, spec2;
	int nrModels=5;

	private double calibrationRatio=0.2;

	@Before
	public void init(){
		spec = new GzipEncryption("some password and salt");
		spec2 = new GzipEncryption("another password and different salt");
	}

//	@Test
	public void TestLibSVM() throws Exception{
		System.out.println("=== Testing LIBSVM ===");
		ACPRegressor libsvmacp = new ACPRegressor(new NormalizedNCM(new EpsilonSVR(),null), 
				new RandomSampling(nrModels, calibrationRatio)); 
		TestACPRegressionInterval(libsvmacp);
	}

	@Test
	public void TestLibLinear() throws Exception{
		System.out.println("=== Testing LIBLINEAR ===");
		ACPRegressor liblinearacpreg = new ACPRegressor(new NormalizedNCM(new LinearSVR(),null), 
				new RandomSampling(nrModels, calibrationRatio));
		testACPDistanceFromModel(liblinearacpreg);
	}

	CSVCmpdData data = TestResources.Reg.getSolubility_500();

	public void TestACPRegressionInterval(ACPRegressor acpimpl) throws Exception{

//		File smilesFile = new File(this.getClass().getResource("/resources/solubility_4000train.smi").getFile());
		// File smilesFile = getFile("/resources/solubility_400train.smi");


		List<Double> confidences= new ArrayList<>();
		confidences.add(0.1);
		confidences.add(0.2);
		confidences.add(0.3);
		confidences.add(0.4);
		confidences.add(0.5);
		confidences.add(0.6);
		confidences.add(0.7);
		confidences.add(0.8);
		confidences.add(0.9);
		
		//Construct a molecule to predict
		//From train file, first example
//		CC(C1=CC=CC=C1)NCCC(C2=CC=CC=C2)C3=CC=CC=C3.Cl	52.8
		SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
		IAtomContainer mol=parser.parseSmiles("CC(C1=CC=CC=C1)NCCC(C2=CC=CC=C2)C3=CC=CC=C3.Cl");
		double observed=52.8;
		
		//First do LibSVM
		ChemCPRegressor acp = new ChemCPRegressor(acpimpl, 1, 3);
		acp.addRecords(new CSVFile(data.uri()).getIterator(), data.property());

		acp.train();
		CPRegressionPrediction results = acp.predict(mol, confidences);

		//Assert we get results
		assertResultsNotNull(observed, results);

		//Predict an interval
		double distance = 10;
		CPRegressionPrediction res = acp.predictConfidence(mol, Arrays.asList(distance));
		
		System.out.println("Result: " + res);

		PredictedInterval interval = res.getIntervals().values().iterator().next();
		assertTrue(interval.getInterval().lowerEndpoint()>0.2);
		
	}


	private void assertResultsNotNull(double observed, CPRegressionPrediction results) {
		for (double conf: results.getConfidences()) {
			double lower = results.getInterval(conf).getInterval().lowerEndpoint();
			double upper = results.getInterval(conf).getInterval().upperEndpoint();
			double midpoint = results.getY_hat();
			double confidence = results.getInterval(conf).getConfidence();
			assertNotNull(lower);
			assertNotNull(upper);
			assertNotNull(midpoint);

			System.out.println("UnobservedConfidence: " + confidence + " , obs=" + observed 
					+ " prediction: midpoint= " + midpoint + " interval=[" + lower + "," + upper + "]"); 
			
		}
	}
	
	public void testACPDistanceFromModel(ACPRegressor lacp) throws IOException, CDKException, IllegalAccessException, InvalidKeyException{

		
		String modelFile = this.getClass().getResource("/resources/acp/ditest").getFile()+"/logd_new.model";
//		String signFile = this.getClass().getResource("/resources/acp/ditest").getFile()+"/logd_new.sign";
		InputStream signFile = this.getClass().getResourceAsStream("/resources/acp/ditest/logd_new.sign");
		
		ChemCPRegressor sacp = new ChemCPRegressor(lacp);
		throw new RuntimeException("TODO"); //TODO
//		((ACPRegressionImpl) sacp.getModelImpl()).loadModelFiles(new File (modelFile), 5, spec);
//		sacp.loadSignatures(signFile, spec);
		
//		String smilesToPredict = "CC(C1=CC=CC=C1)NCCC(C2=CC=CC=C2)C3=CC=CC=C3";
//		IAtomContainer testMol = CPSignFactory.parseSMILES(smilesToPredict);
//
//		double distance = 2;
//		List<CPRegressionPrediction> result = sacp.predict(testMol, Arrays.asList(distance));
//		System.out.println("Distance 2: " + result.get(0));
//		assertTrue(result!=null);
//
//		result = sacp.predict(testMol, new Pair<Double, Double>(4.4, 4.6));
//		System.out.println("[4.4;4.6]: " + result);
//
//		result = sacp.predict(testMol, new Pair<Double, Double>(4.6, 4.7));
//		System.out.println("[4.6;4.7]: " + result);
		
		
//		CPRegressionPrediction result = sacp.predict(testMol, 0.2);
		
//		System.out.println(result);


	}
	
		

}

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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.aromaticity.Aromaticity;
import org.openscience.cdk.aromaticity.ElectronDonation;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.graph.Cycles;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.io.MDLV2000Reader;
import org.openscience.cdk.io.iterator.IteratingSDFReader;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.io.in.SDFile;
import com.arosbio.data.NamedLabels;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.cp.tcp.TCPClassifier;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.UnitTestBase;


/**
 * 
 * @author ola
 *
 */
@Category(PerformanceTest.class)
public class TestTCPmondrian extends UnitTestBase{

	private static final Logger LOGGER = LoggerFactory.getLogger(TestTCPmondrian.class);

	
	@Test
	public void testExecutorPvals() throws InterruptedException, ExecutionException, IllegalAccessException {
		ExecutorService executor = Executors.newFixedThreadPool(4);
		int numberOfJobs = 4;
		List<Future<TestReturn>> results = new ArrayList<>(numberOfJobs);
		for(int i=0;i<numberOfJobs;i++) {
			
			results.add(executor.submit(new Callable<TestReturn>() {
				@Override
				public TestReturn call() throws Exception {
					return TestThreadSafetyPvals();
				}
			}));
		}
		
		executor.shutdown();
		executor.awaitTermination(5, TimeUnit.MINUTES);
		TestReturn last = null;
		for(Future<TestReturn> f:results) {
			if(last == null)
				last = f.get();
			else {
				TestReturn next = f.get();
				Assert.assertEquals(last.getTcpModel().getDataset().getDataset().toString(), next.getTcpModel().getDataset().getDataset().toString());
				Assert.assertEquals(last.getTcpModel().getDataset(), next.getTcpModel().getDataset());

				// Difference should be within 0.2 in p-value
				TestUtils.assertEquals(last.getPvals(), next.getPvals());

			}
			
		}
	}	
	
	public TestReturn TestThreadSafetyPvals() throws Exception {
		
		//The test model files
		CmpdData trainData = TestResources.Cls.getAMES_126_gzip();

		//Construct a new molecule
		SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
		
		//Construct tcp model from data file
		TCPClassifier liblinearTCP = new TCPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()));
		final ChemCPClassifier tcpModel = new ChemCPClassifier(liblinearTCP, 1, 3);
		tcpModel.addRecords(new SDFile(trainData.uri()).getIterator(), trainData.property(), new NamedLabels(trainData.labelsStr()));
		tcpModel.train();

		IAtomContainer mol = parser.parseSmiles("N=C2Nc1c(cccc1S2)Cl");
		
		final Map<String, Double> pvals = tcpModel.predict(mol);
		return new TestReturn() {
			public ChemCPClassifier getTcpModel() {
				return tcpModel;
			}
			public Map<String, Double> getPvals() {
				return pvals;
			}
		};
	}
	
	interface TestReturn {
		ChemCPClassifier getTcpModel();
		Map<String, Double> getPvals();
	}
	
	
	@Test
	public void testRunThreadedTCP() throws InterruptedException, ExecutionException {
		int numThreads = 4;
		ExecutorService executor = Executors.newFixedThreadPool(numThreads);
		
		List<Future<Pair<SignificantSignature,Map<String,Double>>>> results = new ArrayList<>(numThreads);
		for(int i=0;i<numThreads;i++) {
			
			results.add(executor.submit(new Callable<Pair<SignificantSignature,Map<String,Double>>>() {
				@Override
				public Pair<SignificantSignature,Map<String,Double>> call() throws Exception {
					return runModelling();
				}
			}));
		}
		
		executor.shutdown();
		executor.awaitTermination(5, TimeUnit.MINUTES);
		SignificantSignature last = null;
		for(Future<Pair<SignificantSignature,Map<String,Double>>> f : results) {
			if (last == null)
				last = f.get().getLeft();
			else {
				SignificantSignature next = f.get().getLeft();
				Assert.assertEquals(last.getSignature(), next.getSignature());
			}
			
		}
	}
	
	@Test
	public void TestSequentialThreadSafety() throws Exception {
		int numRuns = 10;
		Pair<SignificantSignature,Map<String,Double>> firstRes = runModelling();

		for(int i=0; i<numRuns; i++){
			Pair<SignificantSignature,Map<String,Double>> r = runModelling();
			// Compare ss
			SignificantSignature ss = r.getLeft();
			Assert.assertEquals(firstRes.getLeft().getHeight(), ss.getHeight());
			Assert.assertEquals(firstRes.getLeft().getSignature(), ss.getSignature());

			// Compare P-vals
			TestUtils.assertEquals(firstRes.getRight(), r.getRight());
			
		}
	}
	
	public Pair<SignificantSignature,Map<String,Double>> runModelling() throws Exception {
		

		// The train data
		CmpdData ames = TestResources.Cls.getAMES_126();
		
		//Construct a new molecule
		SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
		
		//Construct tcp model from data file
		TCPClassifier liblinearTCP = new TCPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()));
		ChemCPClassifier tcpModel = new ChemCPClassifier(liblinearTCP, 1, 3);
		tcpModel.addRecords(new SDFile(ames.uri()).getIterator(), ames.property(), new NamedLabels(ames.labelsStr()));
		tcpModel.train();

//		IAtomContainer mol = parser.parseSmiles("N=C2Nc1c(cccc1S2)Cl");
		IAtomContainer mol = parser.parseSmiles("CC(NC1=CC=C(O)C=C1)=O");
		
		Map<String, Double> pvals = tcpModel.predict(mol);
		Assert.assertNotNull(pvals);
		LOGGER.debug("Result from prediction: [" + pvals + "]");
		
		SignificantSignature signSign = tcpModel.predictSignificantSignature(mol);
		// Map<String,Double> pvals = tcpModel.predict(mol);
		StringBuffer sb = new StringBuffer();
		for (int m : signSign.getAtomContributions().keySet()){
			sb.append(" ["+ m + ";" + signSign.getAtomContributions().get(m) + "]");
		}
		LOGGER.debug("Significant signature: " + signSign.getSignature() 
				+ " with pvals: " + pvals);

		return Pair.of(signSign,pvals);
	}
		
	
	@Test
	public void TestPredictMondrianTwoClasses() throws Exception {

		// The test model files
		CmpdData amesData = TestResources.Cls.getAMES_4337();

		//Construct a new molecule
		SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
//		IAtomContainer mol=parser.parseSmiles("Cc1cnc(c(c1OC)C)CS(=O)c2[nH]c3ccc(cc3n2)OC");
		IAtomContainer mol=parser.parseSmiles("OCc1ccc(cc1)Cl");
		
		//Construct tcp model from data file
		TCPClassifier liblinearTCP = new TCPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()));
		ChemCPClassifier tcpModel = new ChemCPClassifier(liblinearTCP, 0, 3);
//		tcpModel.fromSDF(datafile, "Activity", Arrays.asList("POS", "NEG"),  true);
		tcpModel.addRecords(new SDFile(amesData.uri()).getIterator(), amesData.property(), new NamedLabels(amesData.labelsStr()));
//		tcpModel.fromChemFile(datafile.toURI(), PROPERTY, AMES_LABELS);

		//No 1 in ernst paper
		mol=parser.parseSmiles("c1cc(ccc1N(=O)O)Cl");
		//No 2 in ernst paper
//		mol=parser.parseSmiles("O=C(NO)c1ccc2ccccc2(c1)");
		
		//no 4 in paper
//		mol=parser.parseSmiles("C(CBr)Cl");
		
//		no 5
//		O=C(O)CCCCC1CCSS1
		
		//Näst sista
		mol=parser.parseSmiles("N=C2Nc1c(cccc1S2)Cl");
		
		tcpModel.train();
		
		Map<String, Double> pvals = tcpModel.predict(mol);
		Assert.assertNotNull(pvals);
		LOGGER.debug("Result from prediction: [" + pvals + "]");
		
		SignificantSignature signSign = tcpModel.predictSignificantSignature(mol);
		StringBuffer sb = new StringBuffer();
		for (int m : signSign.getAtomContributions().keySet()){
			sb.append(" ["+ m + ";" + signSign.getAtomContributions().get(m) + "]");
		}
		LOGGER.debug("Significant signature: " + signSign.getSignature() 
		+ " with pvals: " + pvals);


	}
	
	
//	@Test
	public void TestPredictMondrianTwoClassesSDF() throws Exception {

		//The test model files
		CmpdData trainData = TestResources.Cls.getAMES_4337();
		// File datafile = new File(this.getClass().getResource("/resources/bursi_nosalts_molsign.sdf").getFile());
		// InputStream testfile = this.getClass().getResourceAsStream(RegressionChang.CHANG_FILE_PATH);

		
		//Construct tcp model from training file in SDF format
		TCPClassifier liblinearTCP = new TCPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()));
		ChemCPClassifier tcpModel = new ChemCPClassifier(liblinearTCP, 0, 3);
		tcpModel.addRecords(new SDFile(trainData.uri()).getIterator(), trainData.property(), new NamedLabels(trainData.labelsStr()));

		//Read test molecules
		Iterator<IAtomContainer> iter = new IteratingSDFReader(TestResources.Reg.getChang().url().openStream(), SilentChemObjectBuilder.getInstance());
		// IChemObjectBuilder bldr   = SilentChemObjectBuilder.getInstance();
		Aromaticity        arom   = new Aromaticity(ElectronDonation.daylight(),
		                                            Cycles.or(Cycles.all(), Cycles.all(6)));
		
		// BufferedReader brdr = new BufferedReader(new InputStreamReader(testfile));
		// MDLV2000Reader mdlr = new MDLV2000Reader(brdr);
		List<Integer> heights = new ArrayList<>();
		IAtomContainer mol;
		while (iter.hasNext()){ //(mol = mdlr.read(bldr.newInstance(IAtomContainer.class, 0,0,0,0))) != null) {
			mol = iter.next();
		    try {
		        arom.apply(mol);
		        AtomContainerManipulator.suppressHydrogens(mol);
		        
				SignificantSignature signSign = tcpModel.predictSignificantSignature(mol);
				heights.add(signSign.getHeight());
		        
		    } catch (CDKException e) {
		    }
		}		
		

		System.out.println("HEIGHT FREQUENCY");
		System.out.println("h0:" + Collections.frequency(heights, 0));
		System.out.println("h1:" + Collections.frequency(heights, 1));
		System.out.println("h2:" + Collections.frequency(heights, 2));
		System.out.println("h3:" + Collections.frequency(heights, 3));

	}
	
//	@Test
	public void TestNoSignificantSignatures() throws IOException, CDKException, CloneNotSupportedException, IllegalAccessException{

		//The test model files
		File datafile = new File(this.getClass().getResource("/resources/cleanData_luciaOrig_0.0_2011-01-01.sdf").getFile());
		InputStream testfile = this.getClass().getResourceAsStream("/resources/foo_1.sdf");

		
		//Construct tcp model from training file in SDF format
		TCPClassifier liblinearTCP = new TCPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()));
		ChemCPClassifier tcpModel = new ChemCPClassifier(liblinearTCP, 1, 3);
		tcpModel.addRecords(new SDFile(datafile.toURI()).getIterator(), "Activity", new NamedLabels(Arrays.asList("POS", "NEG")));
//		tcpModel.getProblem().fromChemFile(datafile.toURI(), "Activity", Arrays.asList("POS", "NEG"));

		//Read test molecules
		IChemObjectBuilder bldr   = SilentChemObjectBuilder.getInstance();
		Aromaticity        arom   = new Aromaticity(ElectronDonation.daylight(),
		                                            Cycles.or(Cycles.all(), Cycles.all(6)));
		
		BufferedReader brdr = new BufferedReader(new InputStreamReader(testfile));
		MDLV2000Reader mdlr = new MDLV2000Reader(brdr);
		IAtomContainer mol;
		while ((mol = mdlr.read(bldr.newInstance(IAtomContainer.class, 0,0,0,0))) != null) {
		    try {
		        arom.apply(mol);
		        AtomContainerManipulator.suppressHydrogens(mol);
		        
				SignificantSignature signSign = tcpModel.predictSignificantSignature(mol);
				Assert.assertNotNull(signSign);
		        
		    } catch (CDKException e) {
		    }
		}		
		mdlr.close();
		brdr.close();		

	}

}

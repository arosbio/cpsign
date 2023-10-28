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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.transform.Transformer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import com.arosbio.chem.io.in.CSVFile;
import com.arosbio.chem.io.in.SDFile;
import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.data.TestChemDataset;
import com.arosbio.cheminf.descriptors.ChemDescriptor;
import com.arosbio.cheminf.descriptors.DescriptorFactory;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.cheminf.descriptors.UserSuppliedDescriptor;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.MathUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.transform.feature_selection.DropColumnSelector;
import com.arosbio.data.transform.feature_selection.DropMissingDataSelector;
import com.arosbio.data.transform.format.MakeDenseTransformer;
import com.arosbio.data.transform.scale.RobustScaler;
import com.arosbio.data.transform.scale.VectorNormalizer;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.ml.algorithms.Regressor;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.cp.CPRegressionPrediction;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.icp.ICPRegressor;
import com.arosbio.ml.cp.nonconf.regression.AbsDiffNCM;
import com.arosbio.ml.cp.nonconf.regression.LogNormalizedNCM;
import com.arosbio.ml.cp.nonconf.regression.NormalizedNCM;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.cp.regression.CPRegressionCalibrationPlotBuilder;
import com.arosbio.ml.metrics.cp.regression.CPRegressionEfficiencyPlotBuilder;
import com.arosbio.ml.metrics.regression.RMSE;
import com.arosbio.ml.sampling.FoldedSampling;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.GzipEncryption;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.UnitTestBase;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;


/**
 * 
 * @author ola
 *
 */
@Category(UnitTest.class)
public class TestACPRegression extends UnitTestBase {


	private static final long seed = 253252l;
	EncryptionSpecification spec, spec2;
	final int nrModels=5;
	final private int cvFolds =10;
	final private double calibrationRatio=.2;
	final private double confidence=.75;

	@Before
	public void init(){
		spec = new GzipEncryption("some password and salt");
		spec2 = new GzipEncryption("another password and different salt");
		// spec = EncryptionSpecFactory.getSpec("password", "salt");
		// spec2 = EncryptionSpecFactory.getSpec("another password", "adslfköma");
	}

	@Test
	public void testGradientWithAdditionalFeatures() throws Exception {
		GlobalConfig.getInstance().setRNGSeed(seed);
		System.out.println("Running with seed: " + seed);
		
		ACPRegressor acp1 = getACPRegressionNormalized(true, true);
		ACPRegressor acp2 = acp1.clone();
		
		// Train the first one using only normal signatures
		ChemCPRegressor sigACP1 = new ChemCPRegressor(acp1);
		sigACP1.getDataset().setDescriptors(new SignaturesDescriptor());
		
//		LoggerUtils.setDebugMode(SYS_ERR);
		CSVCmpdData solu100 = TestResources.Reg.getSolubility_100();

		CSVFile trainData = new CSVFile(solu100.uri()).setDelimiter(solu100.delim());
		sigACP1.addRecords(trainData.getIterator(), solu100.property());
		
		System.out.println("original size: " + sigACP1.getDataset());
		
		sigACP1.train();
		
		SignificantSignature res1 = sigACP1.predictSignificantSignature(getTestMol());
		System.out.println("original res: " + res1);
		System.out.println("original gradient: "+ res1.getAtomContributions());
		
		
		// The second one - using additional features!
		List<String> dummyFeatures = Arrays.asList("feat1", "feat2", "feat3");
		ChemCPRegressor sigACP2 = new ChemCPRegressor(acp2);
		sigACP2.getDataset().setDescriptors(new UserSuppliedDescriptor(dummyFeatures), new SignaturesDescriptor());
		sigACP2.addRecords(new WrapperIterator(trainData.getIterator(), dummyFeatures), solu100.property());
		
		System.out.println("new size: " + sigACP2.getDataset());
		
		sigACP2.train();
		
		
		IAtomContainer testMol = getTestMol();
		for (String p : dummyFeatures) {
			testMol.setProperty(p,0);
		}
		
		SignificantSignature res2 = sigACP2.predictSignificantSignature(testMol);
		System.out.println("new res: " + res2);
		System.out.println("new gradient: "+ res2.getAtomContributions());
		System.out.println("new add-gradient: " + res2.getAdditionalFeaturesGradient());
		TestUtils.assertEquals(res1.getAtomContributions(), res2.getAtomContributions(), 1e-6); // These should be identical (or ish)
		Assert.assertEquals(3, res2.getAdditionalFeaturesGradient().size());
		Assert.assertEquals(res1.getSignature(), res2.getSignature());
		
		// Third attempt!
		sigACP2.getDataset().apply(new DropColumnSelector(Sets.newHashSet(1, 10, 50, 100, 101)));
		System.out.println("new+trans size: " + sigACP2.getDataset());
		sigACP2.train(); // now we've removed stuff!
		SignificantSignature res3 = sigACP2.predictSignificantSignature(testMol);
		System.out.println("new res: " + res3);
		System.out.println("new gradient: "+ res3.getAtomContributions());
		System.out.println("new add-gradient: " + res3.getAdditionalFeaturesGradient());
		TestUtils.assertEqualsSum(res1.getAtomContributions(), res3.getAtomContributions(), res1.getAtomContributions().size()*0.2); // These should be "similar" - but perhaps not identical (we've removed some signatures as well)
		Assert.assertEquals(2, res3.getAdditionalFeaturesGradient().size());
		Assert.assertEquals(res1.getSignature(), res3.getSignature());
		
		
		// Predict gradient but without signatures!
		ChemCPRegressor sigACP4 = new ChemCPRegressor(acp1);
		sigACP4.getDataset().setDescriptors(DescriptorFactory.getInstance().getDescriptorsList().subList(20, 40));
		sigACP4.addRecords(trainData.getIterator(),solu100.property());
		int origSize = sigACP4.getDataset().getFeatureNames(true).size();
		sigACP4.getDataset().apply(Arrays.asList(new MakeDenseTransformer(), new DropMissingDataSelector())); //, new L2_SVR_Selecter(), new Standardizer()
		int postSize = sigACP4.getDataset().getFeatureNames(true).size();
		Assert.assertTrue(origSize > postSize);
		
		sigACP4.train();
		
		SignificantSignature res4 = sigACP4.predictSignificantSignature(getTestMol());
		Assert.assertEquals(postSize, res4.getAdditionalFeaturesGradient().size());
		
//		printLogs();
	}
	
	
	
	private static class WrapperIterator implements Iterator<IAtomContainer>{
		
		private Iterator<IAtomContainer> toWrap;
		private List<String> toAdd;
		public WrapperIterator(Iterator<IAtomContainer> toWrap, List<String> dummiesToAdd) {
			this.toWrap = toWrap;
			this.toAdd = dummiesToAdd;
		}
		
		@Override
		public boolean hasNext() {
			return toWrap.hasNext();
		}

		@Override
		public IAtomContainer next() {
			IAtomContainer next = toWrap.next();
			for (String p : toAdd) {
				next.setProperty(p, 0);
			}
			return next;
		}
		
	}


	@Test
	public void TestACPRegressionGradient() throws Exception{

		int nrModels = 1;
		CSVCmpdData csv = TestResources.Reg.getSolubility_500();
		double calibrationRatio = 0.2;

		List<Double> confidences= new ArrayList<>();
		confidences.add(0.1);
		confidences.add(0.5);
		confidences.add(0.9);

		ACPRegressor libsvmacp = getACPRegressionNormalized(false, true);
		libsvmacp.setStrategy(new RandomSampling(nrModels, calibrationRatio));
		ChemCPRegressor acp = new ChemCPRegressor(libsvmacp, 1, 3);

		acp.addRecords(new CSVFile(csv.uri()).setDelimiter(csv.delim()).getIterator(), csv.property()); 

		acp.train();

		//Construct a new molecule
		SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
		IAtomContainer mol=parser.parseSmiles("Cc1cnc(c(c1OC)C)CS(=O)c2[nH]c3ccc(cc3n2)OC");

		Map<Integer, Double> atomGradientMap = acp.predictSignificantSignature(mol).getAtomContributions();
		assertTrue(atomGradientMap.size()>0);

		System.out.println(atomGradientMap);

	}


	//	@Test
	public void TestACPRegressionRealDataLibLinear() throws Exception{
		assertACPRegressionRealData(new LinearSVR()); 
	}

	//	@Test
	public void TestACPRegressionRealDataLibSVM() throws Exception{
		assertACPRegressionRealData(new EpsilonSVR()); 
	}


	public void assertACPRegressionRealData(Regressor alg) throws Exception {
		int nrModels = 1;
		CSVCmpdData csv = TestResources.Reg.getSolubility_500();
		double calibrationRatio = 0.2;

		List<Double> confidences= new ArrayList<>();
		confidences.add(0.1);
		confidences.add(0.5);
		confidences.add(0.9);

		ACPRegressor libsvmacp = new ACPRegressor(new NormalizedNCM(alg, alg.clone()), new RandomSampling(nrModels, calibrationRatio));
		ChemCPRegressor acp = new ChemCPRegressor(libsvmacp, 1, 3);

		acp.getDataset().add(new CSVFile(csv.uri()).setDelimiter(csv.delim()).getIterator(), csv.property());
		ChemDataset problem = acp.getDataset();
		SubSet[] splittedProblem = problem.getDataset().clone().splitStatic(20); // Keep the first 20 for testing, the rest is used for training
		problem.getDataset().setRecords(splittedProblem[1] );


		acp.train();

		Iterator<IAtomContainer> mols = new CSVFile(csv.uri()).setDelimiter(csv.delim()).getIterator();

		IAtomContainer mol;
		int numMols=0, numFaulty0_5=0, numFaulty0_1=0, numFaulty0_9=0;
		while(mols.hasNext() && numMols < 10){
			mol = mols.next();
			CPRegressionPrediction allRes = acp.predict(mol, confidences);
			double observed = Double.parseDouble((String)mol.getProperty("solubility"));
			System.out.print("Predicted: " + MathUtils.roundToNSignificantFigures(allRes.getY_hat(),5) + ", Observed: " + observed);
			//			for(CPRegressionPrediction res: allRes) 
			for (double conf: confidences) {
				System.out.print(", " + allRes.getInterval(conf).getInterval() + "(" + allRes.getInterval(conf).getConfidence()+")");
			}

			// For 0.1
			if(!allRes.getInterval(.1).getInterval().contains(observed))
				numFaulty0_1++;

			// For 0.5
			if(!allRes.getInterval(.5).getInterval().contains(observed))
				numFaulty0_5++;

			// For 0.9 
			if(!allRes.getInterval(.9).getInterval().contains(observed))
				numFaulty0_9++;

			//				System.out.println();
			numMols++;
		}

		Assert.assertTrue("UnobservedConfidence=0.1, should be at least 10% correct", (double)(numFaulty0_1)/numMols <= .9);
		Assert.assertTrue("UnobservedConfidence=0.5, should be at least 50% correct", (double)(numFaulty0_5)/numMols <= 0.5);
		Assert.assertTrue("UnobservedConfidence=0.9, should be at least 90% correct", (double)(numFaulty0_9)/numMols <= 0.1);

	}


	@Test
	public void testACPRegression() throws Exception{
		CmpdData chang = TestResources.Reg.getChang();
		int nrModels = 10;
		File modelFile = TestUtils.createTempFile("/tmp/solubility", "svm");

		double calibrationRatio = 0.2;

		List<Double> confidences= new ArrayList<>();
		confidences.add(0.1);
		confidences.add(0.5);
		confidences.add(0.9);

		ACPRegressor libsvmacp = new ACPRegressor(new NormalizedNCM(new EpsilonSVR()), new RandomSampling(nrModels, calibrationRatio));
		libsvmacp.setSeed(seed);
		ChemCPRegressor acp = new ChemCPRegressor(libsvmacp, 1, 3);
		SignaturesDescriptor d = (SignaturesDescriptor) acp.getDataset().getDescriptors().get(0);
		ChemCPRegressor acpReg = new ChemCPRegressor(libsvmacp.clone(), 
				d.getStartHeight(), 
				d.getEndHeight());

		acp.addRecords(new SDFile(chang.uri()).getIterator(),chang.property());
		acpReg.setDataset(acp.getDataset().clone());
		acpReg.initializeDescriptors();

		acp.train();
		acpReg.train();

		//Construct a new molecule
		SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
		IAtomContainer mol = parser.parseSmiles("Cc1cnc(c(c1OC)C)CS(=O)c2[nH]c3ccc(cc3n2)OC");

		CPRegressionPrediction result = acp.predict(mol, confidences);
		SignificantSignature atomVals = acp.predictSignificantSignature(mol);

		CPRegressionPrediction resultsNEW = acpReg.predict(mol, confidences);
		SignificantSignature atomValsNEW = acpReg.predictSignificantSignature(mol);

		ModelSerializer.saveModel(acp, modelFile, null);


		ChemCPRegressor acp2 = (ChemCPRegressor) ModelSerializer.loadChemPredictor(modelFile.toURI(), spec);

		CPRegressionPrediction result2 = acp2.predict(mol, confidences);
		SignificantSignature atomVals2 = acp2.predictSignificantSignature(mol);

		String string1 = getJSONMoleculeData(mol) + ", " + result + ", " + atomVals.getAtoms().toString(); //JSONFormattingHelper.getJSONStringRegression(results, atomVals, mol);
		String string2 = getJSONMoleculeData(mol) + ", " + result2 + ", " + atomVals2.getAtoms().toString(); //JSONFormattingHelper.getJSONStringRegression(result2, atomVals2, mol);
		String string3 = getJSONMoleculeData(mol) + ", " + resultsNEW + ", " + atomValsNEW.getAtoms().toString(); //JSONFormattingHelper.getJSONStringRegression(resultsNEW, atomValsNEW, mol);

		System.out.println(string1);
		System.out.println(string2);
		System.out.println(string3);

		assertEquals(string1, string2);
		assertEquals(string1, string3);
		assertEquals(atomVals.getAtomContributions(), atomVals2.getAtomContributions());
		assertEquals(atomVals.getAtomContributions(), atomValsNEW.getAtomContributions());

//		printLogs();
	}


	public static JsonObject getJSONMoleculeData(IAtomContainer ac){
		JsonObject molVals = new JsonObject(CollectionUtils.toStringKeys(ac.getProperties()));

		// Title only if explicitly set
		if(molVals.get(CDKConstants.TITLE)==null)
			molVals.remove(CDKConstants.TITLE);
		// cdk:Remark only if explicitly set
		if(molVals.get(CDKConstants.REMARK)==null)
			molVals.remove(CDKConstants.REMARK);

		return molVals;
	}

	@Test
	public void TestFromPrecomputed() throws CDKException, IOException, CloneNotSupportedException, IllegalAccessException, InvalidKeyException{
		// load data (will create signatures and the data)
		CSVCmpdData solu100 = TestResources.Reg.getSolubility_100();

		ChemCPRegressor reg = new ChemCPRegressor();
		reg.setDataset(new ChemDataset(new SignaturesDescriptor(1, 3)));
		reg.addRecords(new CSVFile(solu100.uri()).setDelimiter(solu100.delim()).getIterator(), solu100.property());

		// Plain - save
		ByteArrayOutputStream baos_plain_sigs = new ByteArrayOutputStream();
		ByteArrayOutputStream baos_plain_data = new ByteArrayOutputStream();
		reg.getDataset().getDataset().writeRecords(baos_plain_data, false); 
		((SignaturesDescriptor)reg.getDataset().getDescriptors().get(0)).writeSignatures(baos_plain_sigs, false);
		ByteArrayInputStream bais_plain_sigs = new ByteArrayInputStream(baos_plain_sigs.toByteArray());
		ByteArrayInputStream bais_plain_data = new ByteArrayInputStream(baos_plain_data.toByteArray());

		// Plain - load
		ChemCPRegressor plain = new ChemCPRegressor(new ACPRegressor(new NormalizedNCM(new EpsilonSVR(), null), new RandomSampling(nrModels, calibrationRatio)), 1, 3);
		plain.getDataset().getDataset().readRecords(bais_plain_data); 
		((SignaturesDescriptor)plain.getDataset().getDescriptors().get(0)).readSignatures(bais_plain_sigs);
		ChemDataset plainProb = plain.getDataset();
		List<String> plainSigs = Lists.newArrayList( 
				((SignaturesDescriptor) plainProb.getDescriptors().get(0)).
				getSignatures());
		List<DataRecord> plainData = plainProb.getDataset();

		// Compressed
		ChemCPRegressor comp = new ChemCPRegressor();
		ByteArrayOutputStream baos_comp_sigs = new ByteArrayOutputStream();
		ByteArrayOutputStream baos_comp_data = new ByteArrayOutputStream();
		reg.getDataset().getDataset().writeRecords(baos_comp_data, true); 
		((SignaturesDescriptor) reg.getDataset().getDescriptors().get(0)).writeSignatures(baos_comp_sigs, true);
		ByteArrayInputStream bais_comp_sigs = new ByteArrayInputStream(baos_comp_sigs.toByteArray());
		ByteArrayInputStream bais_comp_data = new ByteArrayInputStream(baos_comp_data.toByteArray());
		comp.getDataset().getDataset().readRecords(bais_comp_data); 
		((SignaturesDescriptor) comp.getDataset().getDescriptors().get(0)).readSignatures(bais_comp_sigs);
		ChemDataset compProb = comp.getDataset();
		List<String> compSigs = Lists.newArrayList(((SignaturesDescriptor) compProb.getDescriptors().get(0)).getSignatures());
		List<DataRecord> compData = compProb.getDataset();

		// Encrypted
		ChemCPRegressor enc = new ChemCPRegressor(); 
		ByteArrayOutputStream baos_enc_sigs = new ByteArrayOutputStream();
		ByteArrayOutputStream baos_enc_data = new ByteArrayOutputStream();
		reg.getDataset().getDataset().writeRecords(baos_enc_data, spec); 
		((SignaturesDescriptor) reg.getDataset().getDescriptors().get(0)).writeSignaturesEncrypted(baos_enc_sigs, spec);
		ByteArrayInputStream bais_enc_sigs = new ByteArrayInputStream(baos_enc_sigs.toByteArray());
		ByteArrayInputStream bais_enc_data = new ByteArrayInputStream(baos_enc_data.toByteArray());
		enc.getDataset().getDataset().readRecords(bais_enc_data, spec); 
		((SignaturesDescriptor) enc.getDataset().getDescriptors().get(0)).readSignatures(bais_enc_sigs, spec);
		ChemDataset encProb = enc.getDataset();
		List<String> encSigs = Lists.newArrayList(((SignaturesDescriptor) encProb.getDescriptors().get(0)).getSignatures());
		List<DataRecord> encData = encProb.getDataset() ;

		// Verify that everything works (Data)
		Assert.assertEquals(true, TestChemDataset.datasetsEquals(plainData,compData));
		Assert.assertEquals(true, TestChemDataset.datasetsEquals(plainData,encData));

		// Verify that everything works (Signatures)
		Assert.assertEquals(plainSigs, compSigs);
		Assert.assertEquals(plainSigs, encSigs);
	}
	@Test
	public void TestSaveLoadCCPModel()throws CDKException, IOException, CloneNotSupportedException, IllegalAccessException, ClassNotFoundException, InvalidKeyException{
		// load data (will create signatures and the data)
		CSVCmpdData solu = TestResources.Reg.getSolubility_100();
		ChemCPRegressor reg = new ChemCPRegressor(
			new ACPRegressor(new NormalizedNCM(new LinearSVR(), null),
						new FoldedSampling(nrModels)), 1, 3);
		reg.addRecords(new CSVFile(solu.uri()).setDelimiter(solu.delim()).getIterator(), solu.property());
		reg.train();
		File bndFile = TestUtils.createTempFile("model", ".jar");
		ModelSerializer.saveModel(reg, bndFile, null);
		// reg.save(bndFile);
		ChemCPRegressor loadedPlain = (ChemCPRegressor) ModelSerializer.loadChemPredictor(bndFile.toURI(), null);
		Assert.assertEquals(((ACPRegressor)reg.getPredictor()).getStrategy(), ((ACPRegressor)loadedPlain.getPredictor()).getStrategy());
	}

	
	final double beta = 0.1;

	@Test
	@Category(PerformanceTest.class)
	public void testDifferentNonconf() throws Exception {

		CmpdData big_dataset = TestResources.Reg.getGluc();

		Regressor mlModel = new LinearSVR(); 
		ACPRegressor acpImpl = new ACPRegressor(new NormalizedNCM(mlModel), new RandomSampling(nrModels, calibrationRatio));
		ChemCPRegressor acpReg = new ChemCPRegressor(acpImpl);

		acpReg.addRecords(new SDFile(big_dataset.uri()).getIterator(), big_dataset.property());

		TestRunner cv = new TestRunner.Builder(new KFoldCV(cvFolds)).calcMeanAndStd(false).evalPoints(Arrays.asList(confidence)).build();

		List<Metric> normCV = cv.evaluate(acpReg.getDataset(),acpReg.getPredictor()); 
		System.out.println("Normal CV: " + normCV);

		double rmse = getEfficiency(normCV, confidence, new RMSE()),
				eff= getEfficiency(normCV, confidence, new CPRegressionEfficiencyPlotBuilder()), 
				acc= getAccuracy(normCV, confidence, new CPRegressionCalibrationPlotBuilder());

		acpImpl.setICPImplementation(new ICPRegressor(new AbsDiffNCM(mlModel))); 
		List<Metric> absDiffCV = cv.evaluate(acpReg.getDataset(),acpReg.getPredictor()); 
		System.out.println("abs-diff CV: " + absDiffCV);

		acpImpl.setICPImplementation(new ICPRegressor(new LogNormalizedNCM(mlModel, null,beta))); 
		List<Metric> logNormCV = cv.evaluate(acpReg.getDataset(),acpReg.getPredictor()); 
		System.out.println("log-norm CV (beta=" + beta +"): " + logNormCV);


		// Make sure CV results are similar
		double rmseABS = getEfficiency(absDiffCV, confidence, new RMSE()),
				effABS= getEfficiency(absDiffCV, confidence, new CPRegressionEfficiencyPlotBuilder()), 
				accABS= getAccuracy(absDiffCV, confidence, new CPRegressionCalibrationPlotBuilder());
		
		double rmseLOG = getEfficiency(logNormCV, confidence, new RMSE()),
				effLOG= getEfficiency(logNormCV, confidence, new CPRegressionEfficiencyPlotBuilder()), 
				accLOG= getAccuracy(logNormCV, confidence, new CPRegressionCalibrationPlotBuilder());
		
		Assert.assertEquals(rmse, rmseABS, 0.2);
		Assert.assertEquals(eff, effABS, 1d);
		Assert.assertEquals(acc, accABS, 0.2);
		
		Assert.assertEquals(rmse, rmseLOG, 0.2);
		Assert.assertEquals(eff, effLOG, 1d);
		Assert.assertEquals(acc, accLOG, 0.2);
		
//		for (Metric abs: absDiffCV) {
//			if (abs instanceof EfficiencyPlot)
//				Assert.assertEquals(eff, ((EfficiencyPlot) abs).getEfficiency(confidence), 1d);
//			else if (abs instanceof CalibrationPlot)
//				Assert.assertEquals(acc, ((CalibrationPlot) abs).getAccuracy(confidence), 0.2);
//			else if (abs instanceof RMSE)
//				Assert.assertEquals(rmse, ((RMSE) abs).getScore(), 0.2);
//		}
//
//		for (Metric abs: logNormCV) {
//			if (abs instanceof EfficiencyPlot)
//				Assert.assertEquals(eff, ((EfficiencyPlot) abs).getEfficiency(confidence), 0.5);
//			else if (abs instanceof CalibrationPlot)
//				Assert.assertEquals(acc, ((CalibrationPlot) abs).getAccuracy(confidence), 0.2);
//			else if (abs instanceof RMSE)
//				Assert.assertEquals(rmse, ((RMSE) abs).getScore(), 0.2);
//		}
	}

	//	@Test
	//	public void testJoinDifferentSignaturesGenerator() throws Exception {
	//		ChemCPRegressor sigacp = new ChemCPRegressor(ACPRegressionImpl
	//				.getACPRegression(new LibLinearICPRegression(), nrModels, calibrationRatio));
	//		sigacp.fromChemFile(getFile(CHANG_FILE_PATH).toURI(), PROPERTY);
	//
	//		ChemCPRegressor sigacpLibSVM = new ChemCPRegressor(
	//				ACPRegressionImpl.getACPRegression(new LibSvmICPRegression(), nrModels, calibrationRatio));
	//		sigacpLibSVM.fromChemFile(getFile(CHANG_FILE_PATH).toURI(), PROPERTY);
	//
	//		sigacp.join(sigacpLibSVM); // will overwrite the ICP impl
	//
	//		Assert.assertTrue(sigacp.getPredictionModel() instanceof ACPRegressor);
	//		Assert.assertTrue(((ACPRegressor)sigacp.getPredictionModel()).getICPImplementation() instanceof LibSvmICPRegression);
	//
	//		sigacp.getProblem().setSignaturesGenerator(new SignaturesGeneratorStereo());
	//
	//		try{
	//			sigacp.join(sigacpLibSVM);
	//			Assert.fail();
	//		} catch (IllegalArgumentException e){
	//			//			System.out.println(e.getMessage());
	//			Assert.assertTrue(e.getMessage().toLowerCase().contains("signaturesgenerator"));
	//		}
	//	}

	@Test
	public void testVectorScaling() throws Exception {
		CSVCmpdData solu100 = TestResources.Reg.getSolubility_100();

		List<Double> confidences= new ArrayList<>();
		confidences.add(0.1);
		confidences.add(0.5);
		confidences.add(0.9);

		ACPRegressor libsvmacp = getACPRegressionNormalized(false, true); 
		libsvmacp.setStrategy(new RandomSampling(1, 0.2));
		ChemCPRegressor acp = new ChemCPRegressor(libsvmacp); 
		List<ChemDescriptor> descs = DescriptorFactory.getCDKDescriptorsNo3D().subList(0, 10);
		
		acp.getDataset().setDescriptors(descs);

		acp.addRecords(new CSVFile(solu100.uri()).setDelimiter(solu100.delim()).getIterator(), solu100.property());
		
		acp.getDataset().apply(new DropMissingDataSelector(),new RobustScaler(),new VectorNormalizer());

		acp.train();
		
		CPRegressionPrediction resVanilla = acp.predict(getTestMol(), confidences);
		// standard: CPRegressionPrediction: {y_hat=43.03527641508845, maxObs=69.7, intervals={0.5={cappedInterval=[23.23873378881166..62.831819041365236], intervalWidth=39.59308525255357, confidence=0.5, interval=[23.23873378881166..62.831819041365236]}, 0.9={cappedInterval=[10.42935093931473..69.7], intervalWidth=65.21185095154743, confidence=0.9, interval=[10.42935093931473..75.64120189086216]}, 0.1={cappedInterval=[40.76078702265713..45.30976580751977], intervalWidth=4.548978784862642, confidence=0.1, interval=[40.76078702265713..45.30976580751977]}}, minObs=0.9, intervalScaling=14.409754431612507}
//		SYS_ERR.println("standard: " + resVanilla);
		
		// Save the model and re-load it
		File jar = TestUtils.createTempFile("acp-saved", ".jar");
		ModelSerializer.saveModel(acp, jar, null);
		// acp.save(jar);
		
		// Load it again
		ChemCPRegressor acpLoaded = (ChemCPRegressor) ModelSerializer.loadChemPredictor(jar.toURI(), null);
		List<Transformer> trans = acpLoaded.getDataset().getTransformers();
		Assert.assertEquals(3, trans.size());
		
		CPRegressionPrediction resLoaded = acpLoaded.predict(getTestMol(), confidences);
		
		Assert.assertEquals(resVanilla.toString(), resLoaded.toString());
//		SYS_ERR.println("Loaded: " + resLoaded);
		
	}


}

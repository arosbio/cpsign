/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.testutils;

import java.io.File;
import java.util.List;

import org.junit.Test;
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.chem.io.in.CSVChemFileReader;
import com.arosbio.chem.io.in.CSVFile;
import com.arosbio.chem.io.in.SDFReader;
import com.arosbio.chem.io.in.SDFile;
import com.arosbio.cheminf.ChemCPClassifier;
import com.arosbio.cheminf.ChemCPRegressor;
import com.arosbio.cheminf.ChemVAPClassifier;
import com.arosbio.cheminf.SignificantSignature;
import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.descriptors.DescriptorFactory;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.commons.Stopwatch;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.NamedLabels;
import com.arosbio.ml.TrainingsetValidator;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.icp.ICPRegressor;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.cp.nonconf.regression.LogNormalizedNCM;
import com.arosbio.ml.cp.tcp.TCPClassifier;
import com.arosbio.ml.io.ModelInfo;
import com.arosbio.ml.sampling.FoldedSampling;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.vap.avap.AVAPClassifier;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.Cls;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.TestResources.Reg;
import com.arosbio.testutils.TestChemDataLoader.PreTrainedModels;


/**
 * Code for generating the {@link PreTrainedModels} that can be re-used by the tests
 */
public class GenerateTestModels {

	final static File baseDir = new File(new File("").getAbsoluteFile(), "src/test/resources/" + TestChemDataLoader.PreTrainedModels.DIR_NAME); 

	// Used for computing percentiles for all models
	static final CSVCmpdData solu100 = TestResources.Reg.getSolubility_100();

	private static File cleanAndGet(String name){
		File tmp = new File(baseDir,name);
		tmp.delete(); // delete old file (if any)
		return new File(baseDir,name);
	}


	@Test
	public void generateModels() throws Exception {
		Stopwatch watch = new Stopwatch().start();
		
		ACP_CLASSIFICATION();
		System.out.println("ACP Cls: " + watch.stop());

		// watch.start();
		// CVAP_CLASSIFICATION();
		// System.out.println("CVAP Cls: " + watch.stop());
		
		// watch.start();
		// ACP_REGRESSION();
		// System.out.println("ACP Reg: " + watch.stop());

		// watch.start();
		// TCP_CLASSIFICATION();
		// System.out.println("TCP Cls: " + watch.stop());

		// watch.start();
		// TCP_CLASSIFICATION_SMALL();
		// System.out.println("TCP (small) Cls: " + watch.stop());

		// watch.start();
		// PRECOMPUTED_CLASSIFICATION();
		// System.out.println("Precomp Cls: " + watch.stop());

		// watch.start();
		// PRECOMPUTED_REGRESSION();
		// System.out.println("Precomp reg: " + watch.stop());

		// watch.start();
		// NON_CHEM_ACP_REG();
		// System.out.println("No-chem Reg model: " + watch.stop());

		System.out.println("\n\n---------Finished---------");
	}
	
	

	/**
	 * Generates a LibLinear model in acp (random sampling strategy)
	 * Generates a LibSvm model in ccp (folded sampling strategy)
	 * @throws Exception
	 */
	public static void ACP_CLASSIFICATION() throws Exception{

		// LibLinear
		File modelFile = cleanAndGet(PreTrainedModels.ACP_CLF_LIBLINEAR_FILENAME);

		ChemCPClassifier acp = new ChemCPClassifier(new ACPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()), new RandomSampling(5, 0.25)));
		CmpdData ames = Cls.getAMES_1337();
		acp.addRecords(new SDFile(ames.uri()).getIterator(), ames.property(), new NamedLabels(ames.labelsStr()));

		acp.train();

		// this is heavy, but done _once_ for speeding up future tests
		// LoggerUtils.setTraceMode();
		// LoggerUtils.setDebugMode(System.out);
		// try (CSVChemFileReader iter = new CSVFile(solu100.uri()).setDelimiter(solu100.delim()).getIterator()){
		try (SDFReader iter = new SDFile(ames.uri()).getIterator()){
			acp.computePercentiles(iter);
		}
		IAtomContainer mol = UnitTestBase.getTestMol();
		SignificantSignature ss =  acp.predictSignificantSignature(mol);
		System.err.println("from ss.full: "+ ss.getFullGradient());
		System.err.println("from plain; " + acp.getPredictor().calculateGradient(acp.getDataset().convertToFeatureVector(mol)));

		System.err.println(acp.getLowPercentile() + "= low : high=" + acp.getHighPercentile());
		

		acp.withModelInfo(new ModelInfo("Ames ACP model"));
		ModelSerializer.saveModel(acp, modelFile, null);

		System.out.println("Finished ACP Classification");
	}

	public static void CVAP_CLASSIFICATION() throws Exception{

		File modelFile = cleanAndGet(PreTrainedModels.CVAP_LIBLINEAR_FILENAME);

		ChemVAPClassifier cvap = new ChemVAPClassifier(new AVAPClassifier(new LinearSVC(), new FoldedSampling(8)));

		CmpdData ames = Cls.getAMES_126();

		cvap.addRecords(new SDFile(ames.uri()).getIterator(),ames.property(), new NamedLabels(ames.labelsStr()));

		cvap.train();

		try (CSVChemFileReader iter = new CSVFile(solu100.uri()).getIterator()){
			cvap.computePercentiles(iter);
		}

		cvap.withModelInfo(new ModelInfo("AMES Venn-ABERS predictor"));

		ModelSerializer.saveModel(cvap, modelFile,null);
		
		System.out.println("Finished CVAP Classification");
	}

	/**
	 * Generates a LibLinear model in acp (random sampling strategy)
	 * Generates a LibSvm model in ccp (folded sampling strategy)
	 * @throws Exception
	 */
	public static void ACP_REGRESSION() throws Exception{

		File modelFile = cleanAndGet(PreTrainedModels.ACP_REG_LIBSVM_FILENAME);

		ChemCPRegressor acp = new ChemCPRegressor(new ACPRegressor(new LogNormalizedNCM(new EpsilonSVR(),0.05),
			new RandomSampling(10, .2)));

		CmpdData chang = Reg.getChang();
		acp.addRecords(new SDFile(chang.uri()).getIterator(), chang.property());

		acp.train();

		try (CSVChemFileReader iter = new CSVFile(solu100.uri()).getIterator()){
			acp.computePercentiles(iter);
		}

		acp.withModelInfo(new ModelInfo("Chang Reg model"));
		ModelSerializer.saveModel(acp, modelFile, null);
		
		System.out.println("Finished ACP REGRESSION");
	}

	public static void TCP_CLASSIFICATION() throws Exception {
		File modelFile = cleanAndGet(PreTrainedModels.TCP_CLF_LINEAR_FILENAME);
		ChemCPClassifier tcp = new ChemCPClassifier(new TCPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC())));
		CmpdData ames = Cls.getAMES_126_gzip();
		tcp.addRecords(new SDFile(ames.uri()).getIterator(), ames.property(), new NamedLabels(ames.labelsStr()));
		tcp.train();
		// this is heavy, but done _once_ for speeding up future tests
		try (CSVChemFileReader iter = new CSVFile(solu100.uri()).getIterator()){
			tcp.computePercentiles(iter);
		}
		tcp.withModelInfo(new ModelInfo("Ames TCP model"));
		ModelSerializer.saveModel(tcp, modelFile, null);

		System.out.println("Finished TCP Classification");
	}

	public static void TCP_CLASSIFICATION_SMALL() throws Exception {
		File modelFile = cleanAndGet(PreTrainedModels.TCP_CLF_SMALL_LINEAR_FILENAME);
		
		ChemDataset data = TestChemDataLoader.getInstance().getChemicalProblem(true,true);
		List<List<DataRecord>> stratas = DataUtils.stratify(data.getDataset());
		SubSet d = new SubSet();
		d.addAll(stratas.get(0).subList(0, 20));
		d.addAll(stratas.get(1).subList(0, 20));
		d.shuffle();
		data.getDataset().setRecords(d);

		ChemCPClassifier tcp = new ChemCPClassifier(new TCPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC())));
		tcp.withModelInfo(new ModelInfo("small TCP model"));
		tcp.setDataset(data);
		tcp.train();

		// Compute percentiles on a larger dataset (done once when generating this model)
		try(CSVChemFileReader iter = new CSVFile(solu100.uri()).setDelimiter(solu100.delim()).getIterator(); ){
			tcp.computePercentiles(iter);
		}

		ModelSerializer.saveModel(tcp, modelFile, null);
		

		System.out.println("Finished TCP Classification (small)");
	}

	/**
	 * Should be Phys-Chem descriptors
	 * @throws Exception
	 */
	public static void PRECOMPUTED_CLASSIFICATION() throws Exception {
		
		File modelFile = cleanAndGet(PreTrainedModels.PRECOMP_CLF_FILENAME);

		ChemDataset cls = new ChemDataset(DescriptorFactory.getCDKDescriptorsNo3D());
		cls.initializeDescriptors();
		
		CmpdData herg = Cls.getHERG();
		cls.add(new SDFile(herg.uri()).getIterator(), herg.property(), new NamedLabels(herg.labelsStr()));

		ModelSerializer.saveDataset(cls, new ModelInfo("hERG dataset"), modelFile, null);
		
		System.out.println("Finished PRECOMP Classification");
	}

	public static void PRECOMPUTED_REGRESSION() throws Exception {
		// String path=fixName(PreTrainedModels.PRECOMP_DATA_REG_PATH);
		File modelFile = cleanAndGet(PreTrainedModels.PRECOMP_REG_FILENAME);

		ChemDataset reg = new ChemDataset();
		reg.initializeDescriptors();
		CmpdData chang = Reg.getChang();
		reg.add(new SDFile(chang.uri()).getIterator(), chang.property());

		ModelSerializer.saveDataset(reg, new ModelInfo("chang dataset"), modelFile, null);

		System.out.println("Finished PRECOMP Regression");
	}


	public static void NON_CHEM_ACP_REG() throws Exception {
		TrainingsetValidator.setTestingEnv();

		ACPRegressor acpReg = new ACPRegressor(
				new ICPRegressor(new LogNormalizedNCM(new LinearSVR(),0.0)), 
				new RandomSampling(11, .26));
		Dataset data = TestDataLoader.getInstance().getDataset(false, true);
		acpReg.train(data);

		// String path = fixName(PreTrainedModels.NUMERIC_ACP_REG);
		File modelFile = cleanAndGet(PreTrainedModels.NON_CHEM_FILENAME);
		
		acpReg.setModelInfo(new ModelInfo("non chem reg-model"));

		ModelSerializer.saveModel(acpReg, modelFile, null);

		System.out.println("Finished non-Chem ACP Regression");
	}
	
}

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp.acp;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.Stopwatch;
import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.transform.feature_selection.L2_SVR_Selector;
import com.arosbio.io.DataSink;
import com.arosbio.io.JarDataSink;
import com.arosbio.io.JarDataSource;
import com.arosbio.ml.algorithms.Regressor;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.cp.CPRegressionPrediction;
import com.arosbio.ml.cp.icp.ICPRegressor;
import com.arosbio.ml.cp.nonconf.regression.AbsDiffNCM;
import com.arosbio.ml.cp.nonconf.regression.LogNormalizedNCM;
import com.arosbio.ml.cp.nonconf.regression.NCMRegression;
import com.arosbio.ml.cp.nonconf.regression.NormalizedNCM;
import com.arosbio.ml.io.ConfAISerializer;
import com.arosbio.ml.io.ModelInfo;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.NonSuiteTest;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.TestEnv;

import de.bwaldvogel.liblinear.SolverType;

/**
 * 
 * @author ola
 *
 */
@Category(UnitTest.class)
public class TestACPRegression extends TestEnv{

	private static final double CONFIDENCE = 0.8;
	int recordToTest=1;


	@Test
	public void testLibLinLibSvm() throws Exception {
		doTestDiffTargetAndErrorAlgorithm(new LinearSVR(),
				new EpsilonSVR());
	}
	
	@Test
	public void testLibSvmLibLin() throws Exception {
		doTestDiffTargetAndErrorAlgorithm(new EpsilonSVR(),
				new LinearSVR());
	}
	
	public void doTestDiffTargetAndErrorAlgorithm(Regressor target, Regressor error) throws Exception {

		//Read in problem from file
		Dataset problem = TestDataLoader.getInstance().getDataset(false, false).clone();
		//Predict the second example (first one was failing sometimes at the "lowest confidence" - most narrow interval)
		DataRecord example = problem.getDataset().remove(recordToTest);

		ACPRegressor acp = new ACPRegressor(new LogNormalizedNCM(target, error, 0.001), 
				new RandomSampling(DEFAULT_NUM_MODELS, DEFAULT_CALIBRATION_RATIO));
		acp.train(problem);
		Map<Integer,ICPRegressor> modelsPreLoaded = ((ACPRegressor)acp).getPredictors();
		
		CPRegressionPrediction res = acp.predict(example.getFeatures(), CONFIDENCE);
		
		File tmpFile = TestUtils.createTempFile("acpReg", ".jar");
		acp.setModelInfo(new ModelInfo("acp"));
		ConfAISerializer.saveModel(acp, tmpFile, null);
		ACPRegressor loaded = (ACPRegressor) ConfAISerializer.loadPredictor(tmpFile.toURI(), null);
		
		Map<Integer,ICPRegressor> modelsLoaded = ((ACPRegressor)loaded).getPredictors();
		for (Integer id: modelsLoaded.keySet()) {
			Assert.assertEquals(
					modelsLoaded.get(id).getNCM().getErrorModel().getClass(),
					modelsPreLoaded.get(id).getNCM().getErrorModel().getClass());
			Assert.assertEquals(
					modelsLoaded.get(id).getNCM().getModel().getClass(),
					modelsPreLoaded.get(id).getNCM().getModel().getClass());
		}

		CPRegressionPrediction resLoaded = loaded.predict(example.getFeatures(), CONFIDENCE);
		Assert.assertEquals(res.toString(), resLoaded.toString());
//		SYS_OUT.println(systemOutRule.getLog());
//		printLogs();
	}
	


	@Test
	public void TestACPRegressionLinearKernel() throws IOException {

		List<Double> confidences= new ArrayList<>();
		confidences.add(0.9);
		confidences.add(0.8);
		confidences.add(0.75);

		//Read in problem from file
		Dataset problem = TestDataLoader.getInstance().getDataset(false, false);

		ACPRegressor lacp = getACPRegressionLogNormalized(true, true, 0.01);

		//Predict the second example (first one was failing sometimes at the "lowest confidence" - most narrow interval)
		DataRecord example = problem.getDataset() .remove(recordToTest);

		//Train model
		lacp.train(problem); 

		CPRegressionPrediction results = lacp.predict(example.getFeatures(),confidences);
		
		System.out.println(results);

		for (double conf : confidences){
			// Make sure all are set
			double lower = results.getInterval(conf).getInterval().lowerEndpoint();
			double upper = results.getInterval(conf).getInterval().upperEndpoint();
			double midpoint = results.getY_hat();
			double distance = results.getInterval(conf).getIntervalHalfWidth();
			double confidence = results.getInterval(conf).getConfidence();
			assertNotNull(lower);
			assertNotNull(upper);
			assertNotNull(midpoint);
			assertNotNull(distance);
			assertNotNull(confidence);
		}

		//High confidence should give a larger distance than smaller
		Assert.assertTrue(results.getInterval(confidences.get(0)).getIntervalHalfWidth() > results.getInterval(confidences.get(1)).getIntervalHalfWidth());
		Assert.assertTrue(results.getInterval(confidences.get(1)).getIntervalHalfWidth() > results.getInterval(confidences.get(2)).getIntervalHalfWidth());

//		printLogs();

	}

	boolean absDiffNonconfMeasure = true;

	@Test
	public void TestSaveLoadLibLinearModel() throws Exception{
		File modelFile = File.createTempFile("/tmp/housingScale.model", "");

		LoggerUtils.setDebugMode();

		List<Double> confidences= new ArrayList<>();
		confidences.add(0.9);
		confidences.add(0.8);
		confidences.add(0.75);

		//Read in problem from file
		Dataset problem = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.REGRESSION_HOUSING);
		
		//Predict the second example (first one was failing sometimes at the "lowest confidence" - most narrow interval)
		DataRecord example = problem.getDataset().get(recordToTest);

		NCMRegression ncm = null;
		
		if (absDiffNonconfMeasure)
			ncm = new AbsDiffNCM(new LinearSVR());
		else
			ncm = new NormalizedNCM(new LinearSVR());
		
		ACPRegressor lacp = new ACPRegressor(new ICPRegressor(ncm),
				new RandomSampling(DEFAULT_NUM_MODELS, DEFAULT_CALIBRATION_RATIO));

		//Train model
		lacp.train(problem);

		//Save model
		DataSink modelSink = new JarDataSink(new JarOutputStream(new FileOutputStream(modelFile))); 
		lacp.saveToDataSink(modelSink, "acps", null);
		modelSink.close();

		// Load into new ACPRegression instance
		ACPRegressor lacp_loaded = new ACPRegressor();
		lacp_loaded.loadFromDataSource(new JarDataSource(new JarFile(modelFile)), null); 

		// Predict with normal

		CPRegressionPrediction result_trained = lacp.predict(example.getFeatures(),confidences);
		CPRegressionPrediction result_loaded = lacp_loaded.predict(example.getFeatures(),confidences);

		System.out.println("Trained: " + result_trained + "\nLoaded:  " + result_loaded);
		assertTrue(result_trained.getIntervals().size() ==result_loaded.getIntervals().size());

	}


	@Test
	public void TestACPRegressionLibSVM() throws IOException, IllegalAccessException{

		//		int nrModels = 10;

		List<Double> confidences= new ArrayList<>();
		confidences.add(0.9);
		confidences.add(0.8);
		confidences.add(0.7);
		confidences.add(0.6);
		confidences.add(0.5);



		//Read in problem from file
		Dataset problem = TestDataLoader.getInstance().getDataset(false, false); 
		DataRecord example = problem.getDataset() .get(recordToTest);

		ACPRegressor lacp = getACPRegressionNormalized(false, true); 

		//Train model
		lacp.train(problem);

		//Predict the second example (first one was failing sometimes at the "lowest confidence" - most narrow interval)

		CPRegressionPrediction results = lacp.predict(example.getFeatures(),confidences);

		for (double conf : confidences){

			double lower = results.getInterval(conf).getInterval().lowerEndpoint();
			double upper = results.getInterval(conf).getInterval().upperEndpoint();
			double midpoint = results.getY_hat();
			double distance = results.getInterval(conf).getIntervalHalfWidth();
			double confidence = results.getInterval(conf).getConfidence();
			assertNotNull(lower);
			assertNotNull(upper);
			assertNotNull(midpoint);
			assertNotNull(distance);
			assertNotNull(confidence);

		}

	}

	@Test
	public void TestSaveLoadLibSVMModel() throws Exception{
		File modelFile = TestUtils.createTempFile("/tmp/housingScaleLibSVM.model", "");

		List<Double> confidences= new ArrayList<>();
		confidences.add(0.9);
		confidences.add(0.8);
		confidences.add(0.75);

		//Read in problem from file
		Dataset problem = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.REGRESSION_HOUSING);
		//Predict the second example (first one was failing sometimes at the "lowest confidence" - most narrow interval)
		DataRecord example = problem.getDataset() .get(recordToTest);

		ACPRegressor lacp = getACPRegressionNormalized(false, true);

		//Train model
		lacp.train(problem); 

		//Save model
		DataSink sink = getJarDataSink(modelFile);
		lacp.saveToDataSink(sink, null, null);
		sink.close();

		// Load into new LibLinearACPRegression
		ACPRegressor lacp_loaded = new ACPRegressor();
		lacp_loaded.loadFromDataSource(getJarDataSource(modelFile), null);

		// Predict with normal

		CPRegressionPrediction result_trained = lacp.predict(example.getFeatures(),confidences);
		CPRegressionPrediction result_loaded = lacp_loaded.predict(example.getFeatures(),confidences);

		System.out.println("Trained: " + result_trained + "\nLoaded:  " + result_loaded);
		assertTrue(result_trained.getIntervals().size()==result_loaded.getIntervals().size());

	}


	@Test
	public void TestSaveLoadNonconfMeasure() throws Exception {
		File modelFile = TestUtils.createTempFile("/tmp/housingScaleLibSVM.model", "");

		List<Double> confidences= new ArrayList<>();
		confidences.add(0.9);
		confidences.add(0.8);
		confidences.add(0.75);

		//Read in problem from file
		SubSet problem = TestDataLoader.loadSubset(TestResources.SVMLIGHTFiles.REGRESSION_HOUSING);
		SubSet[] probs = problem.splitStatic(problem.size()-10);
		problem = probs[0];
		SubSet testEx = probs[1];

		ACPRegressor lacp = getACPRegressionAbsDiff(false, true);

		Dataset trainProblem = new Dataset();
		trainProblem.withDataset(problem);
		lacp.train(trainProblem); 

		//Save model
		DataSink modelSink = getJarDataSink(modelFile);
		lacp.saveToDataSink(modelSink, null, null);
		modelSink.close();

		// Load into new LibLinearACPRegression
		ACPRegressor lacp_loaded = new ACPRegressor();
		lacp_loaded.loadFromDataSource(getJarDataSource(modelFile), null);

		for(int i=0; i<testEx.size(); i++){
			CPRegressionPrediction normal = lacp.predict(testEx .get(i).getFeatures(), confidences);
			CPRegressionPrediction loaded_pred = lacp_loaded.predict(testEx .get(i).getFeatures(), confidences);
			Assert.assertEquals(normal.getIntervals().size(), loaded_pred.getIntervals().size());
			
			for (double conf: confidences) {
				Assert.assertEquals(normal.getInterval(conf).getConfidence(), loaded_pred.getInterval(conf).getConfidence(), 0.001);
				Assert.assertEquals(normal.getInterval(conf).getIntervalHalfWidth(), loaded_pred.getInterval(conf).getIntervalHalfWidth(), 0.001);
				Assert.assertEquals(normal.getInterval(conf).getInterval().lowerEndpoint(), loaded_pred.getInterval(conf).getInterval().lowerEndpoint(), 0.001);
				Assert.assertEquals(normal.getInterval(conf).getInterval().upperEndpoint(), loaded_pred.getInterval(conf).getInterval().upperEndpoint(), 0.001);
				Assert.assertEquals(normal.getInterval(conf).getCappedInterval().lowerEndpoint(), loaded_pred.getInterval(conf).getCappedInterval().lowerEndpoint(), 0.001);
				Assert.assertEquals(normal.getInterval(conf).getCappedInterval().upperEndpoint(), loaded_pred.getInterval(conf).getCappedInterval().upperEndpoint(), 0.001);
			}
			
		}
	}
	
	/*
	 // Get problem(false, false)
	 type:L2R_L1LOSS_SVR_DUAL  1.888 s metrics[RMSE : 5.447817560708261, R^2 : 0.6484379031813504, CP regression efficiency plot builder, CP regression calibration plot builder]
type:L2R_L2LOSS_SVR  415 ms metrics[RMSE : 5.098800533065186, R^2 : 0.6920409433580386, CP regression efficiency plot builder, CP regression calibration plot builder]
type:L2R_L2LOSS_SVR_DUAL  3.311 s metrics[RMSE : 5.0962329603523235, R^2 : 0.6923510194943784, CP regression efficiency plot builder, CP regression calibration plot builder]

// Get problem(false, true)
type:L2R_L1LOSS_SVR_DUAL  541 ms metrics[RMSE : 3.3921915435309935, R^2 : 0.6937505357984544, CP regression efficiency plot builder, CP regression calibration plot builder]
type:L2R_L2LOSS_SVR  52 ms metrics[RMSE : 4.328073594348454, R^2 : 0.5014555601767781, CP regression efficiency plot builder, CP regression calibration plot builder]
type:L2R_L2LOSS_SVR_DUAL  351 ms metrics[RMSE : 4.275695113996504, R^2 : 0.5134493442069252, CP regression efficiency plot builder, CP regression calibration plot builder]

// getChemicalProblem (false, true)
type:L2R_L1LOSS_SVR_DUAL  806 ms metrics[RMSE : 21.3371565704787, R^2 : 0.062372306748966766, CP regression efficiency plot builder, CP regression calibration plot builder]
type:L2R_L2LOSS_SVR  566 ms metrics[RMSE : 22.95974527638589, R^2 : -0.08565408023204402, CP regression efficiency plot builder, CP regression calibration plot builder]
type:L2R_L2LOSS_SVR_DUAL  527 ms metrics[RMSE : 21.33701726912105, R^2 : 0.06238454946592442, CP regression efficiency plot builder, CP regression calibration plot builder]

// getChemicalProblem(false,false)
type:L2R_L1LOSS_SVR_DUAL  3 min 22 s metrics[RMSE : 22.075548122559816, R^2 : 0.09429073199698468, CP regression efficiency plot builder, CP regression calibration plot builder]
type:L2R_L2LOSS_SVR  34.227 s metrics[RMSE : 23.350393382253955, R^2 : -0.013337754044659311, CP regression efficiency plot builder, CP regression calibration plot builder]
type:L2R_L2LOSS_SVR_DUAL  3 min 10 s metrics[RMSE : 22.811517802550426, R^2 : 0.032893758745256685, CP regression efficiency plot builder, CP regression calibration plot builder]

// getChemicalProblem(false, true);
original num feats: 1994
after num feats: 820
type:L2R_L1LOSS_SVR_DUAL  736 ms metrics[RMSE : 17.44272714011027, R^2 : 0.37340617300008483, CP regression efficiency plot builder, CP regression calibration plot builder]
type:L2R_L2LOSS_SVR  237 ms metrics[RMSE : 16.408430657180634, R^2 : 0.44551292159871225, CP regression efficiency plot builder, CP regression calibration plot builder]
type:L2R_L2LOSS_SVR_DUAL  457 ms metrics[RMSE : 17.442985927635117, R^2 : 0.373387580051039, CP regression efficiency plot builder, CP regression calibration plot builder]

// getChemicalProblem(false,false)
original num feats: 13926
after num feats: 4996
type:L2R_L1LOSS_SVR_DUAL  2 min 21 s metrics[RMSE : 19.566442316099753, R^2 : 0.2884759988041068, CP regression efficiency plot builder, CP regression calibration plot builder]
type:L2R_L2LOSS_SVR  27.749 s metrics[RMSE : 21.668383498358953, R^2 : 0.12739268917997637, CP regression efficiency plot builder, CP regression calibration plot builder]
type:L2R_L2LOSS_SVR_DUAL  2 min 31 s metrics[RMSE : 21.155261334887836, R^2 : 0.16823123061733403, CP regression efficiency plot builder, CP regression calibration plot builder]

// getChemicalProblem(false,false)
original num feats: 13926
after num feats: 1872
type:L2R_L1LOSS_SVR_DUAL  2 min 9 s metrics[RMSE : 20.297040222925705, R^2 : 0.23434831033825698, CP regression efficiency plot builder, CP regression calibration plot builder]
type:L2R_L2LOSS_SVR  37.371 s metrics[RMSE : 20.566301210585582, R^2 : 0.21389926059290876, CP regression efficiency plot builder, CP regression calibration plot builder]
type:L2R_L2LOSS_SVR_DUAL  2 min 33 s metrics[RMSE : 20.68127542899751, R^2 : 0.20508542947689112, CP regression efficiency plot builder, CP regression calibration plot builder]
	 */
	@Test
	@Category(NonSuiteTest.class)
	public void testDifferentSolverTypesLiblinear()throws Exception {
		TestRunner runner = new TestRunner.Builder(new KFoldCV()).build();
//		LibLinearParameters params = LibLinearParameters.defaultRegression();
		LinearSVR svr = new LinearSVR();
		ICPRegressor icp = new ICPRegressor(new LogNormalizedNCM(svr, 0.1));
		ACPRegressor acp = new ACPRegressor(icp, new RandomSampling(10, .2));
		SYS_OUT.println(GlobalConfig.getInstance().getRNGSeed());
		Dataset prob = TestDataLoader.getInstance().getDataset(false, false);
		SYS_OUT.println("original num feats: " + prob.getNumAttributes());
		L2_SVR_Selector selecter = new L2_SVR_Selector();
		selecter.setC(0.00001);
		prob.apply(selecter);
		SYS_OUT.println("after num feats: " + prob.getNumAttributes());
		
		
//		List<SolverType> types = Arrays.asList(SolverType.L2R_L1LOSS_SVR_DUAL, SolverType.L2R_L2LOSS_SVR, SolverType.L2R_L2LOSS_SVR_DUAL);

		for (SolverType t : LinearSVR.ALLOWED_SOLVERS) {
			svr.setSolverType(t);
			Stopwatch sw = new Stopwatch();
			sw.start();
			List<Metric> m = runner.evaluate(prob, acp);
			sw.stop();
			SYS_ERR.println("type:" +t + "  " + sw + " metrics"+m);
			
		}
		
		
	}

//	@Test
	public void listConfigurables() {
		ACPRegressor acp = new ACPRegressor(
				new LogNormalizedNCM(new LinearSVR()), new RandomSampling());

		for (ConfigParameter p : acp.getConfigParameters())
			SYS_ERR.println(p);
	}

}


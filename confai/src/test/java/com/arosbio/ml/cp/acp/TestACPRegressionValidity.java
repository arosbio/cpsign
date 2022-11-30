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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.cp.CPRegressionPrediction;
import com.arosbio.ml.cp.CPRegressionPrediction.PredictedInterval;
import com.arosbio.ml.cp.icp.ICPRegressor;
import com.arosbio.ml.cp.nonconf.calc.SplineInterpolatedPValue;
import com.arosbio.ml.cp.nonconf.regression.AbsDiffNCM;
import com.arosbio.ml.cp.nonconf.regression.LogNormalizedNCM;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.MetricFactory;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.cp.CPAccuracy;
import com.arosbio.ml.metrics.cp.regression.CPRegressionCalibrationPlotBuilder;
import com.arosbio.ml.metrics.cp.regression.CPRegressionEfficiencyPlotBuilder;
import com.arosbio.ml.metrics.plots.MergedPlot;
import com.arosbio.ml.metrics.plots.Plot2D;
import com.arosbio.ml.metrics.plots.PlotMetric;
import com.arosbio.ml.metrics.regression.R2;
import com.arosbio.ml.metrics.regression.RMSE;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.TestEnv;

/**
 * 
 * @author ola
 *
 */
@Category(PerformanceTest.class)
public class TestACPRegressionValidity extends TestEnv{

	private static final int EVALUATE_LAST_NO = 40;  //How many of the last observations to run
	private static final double STEP_SIZE = 0.1;
	private static final int NUMBER_OF_ROUNDS = 5;

	@Test
	public void testACPRegressionValidityLibLinear() throws Exception {

		ACPRegressor lacp = getACPRegressionNormalized(true, true); 
		TestACP(lacp, false);

	}

	@Test
	public void testACPRegressionValidityLibSVM() throws Exception {
		ACPRegressor lacp = getACPRegressionNormalized(false, true);
		TestACP(lacp, false);
	}

	@Test
	public void testCCPRegressionValidityLibLinear() throws Exception{

		ACPRegressor lacp = getACPRegressionNormalized(true, false); 
		TestACP(lacp, true);

	}

	@Test
	public void testCCPRegressionValidityLibSVM() throws Exception {
		ACPRegressor lacp = getACPRegressionNormalized(false, false); 
		TestACP(lacp, true);
	}

	public void TestACP(ACPRegressor lacp, boolean isCCP) throws Exception, IllegalAccessException{

		//		LogManager.getLogger("com.arosbio.modeling")
		//		.setLevel(Level.WARN);

		List<Double> confidences= new ArrayList<>();
		for (double i=0;i<1;i=i+STEP_SIZE){
			confidences.add(Math.round(i * 100.0) / 100.0);
		}

		InputStream iStream = TestResources.SVMLIGHTFiles.REGRESSION_HOUSING.openStream();

		//Read in problem from file
		Dataset problem = Dataset.fromLIBSVMFormat(iStream);


		//Map from confidence level to number of correct predictions 
		Map<Double, Integer> correctMap = new HashMap<Double, Integer>();
		//Map from confidence level to number of incorrect predictions 
		Map<Double, Integer> incorrectMap = new HashMap<Double, Integer>();

		for (int round=0;round<NUMBER_OF_ROUNDS;round++){
			System.out.println("Starting round " + round + " of " + NUMBER_OF_ROUNDS );

			//Permute the problem dataset
			problem.shuffle();

			//Loop over the last observations in the dataset and predict them in an online setting
			for (int l = problem.getDataset() .size()-(EVALUATE_LAST_NO-1); l<problem.getDataset() .size(); l++){

				if (l%100==0)
					System.out.println("Dataset subsize: " + l + " of " + problem.getDataset() .size());

				//Set up incremental dataset
				Dataset growingDataSet = new Dataset();

				//Add all data until current dataset size
				for (int cnt=0; cnt<l; cnt++){
					growingDataSet.getDataset() .add(problem.getDataset() .get(cnt));
					//					growingDataSet.getY().add(problem.getY().get(cnt));

					//				StringBuffer sb = new StringBuffer();
					//				for (int r = 0; r< problem.getDataset().get(cnt).length; r++){
					//					sb.append(" " + problem.getDataset().get(cnt)[r].getIndex()+":"+ problem.getDataset().get(cnt)[r].getValue());
					//				}
					//				
					//				System.out.println("Added Y: " + problem.getY().get(cnt) 
					//						+ " ; Added X: " + sb.toString());

				}

				//Take next observation as example to predict
				DataRecord example = problem.getDataset() .get(l);
				Double observed = example.getLabel();
				//			System.out.println("Predicting example with Y-value: " + observed);
				//			for (int r = 0; r< example.length; r++){
				//				System.out.print(" " + example[r].getIndex()+":"+ example[r].getValue());
				//			}

				//Train model on the growing dataset which is problem minus last row
				lacp.train(growingDataSet);
				//				if (isCCP)
				//					lacp.trainCCP(growingDataSet, NR_MODELS);
				//				else
				//					lacp.trainACP(growingDataSet, CALIBRATION_PART, NR_MODELS);

				//Predict
				CPRegressionPrediction results = lacp.predict(example.getFeatures(),confidences);

				for (double confidence : confidences){
					PredictedInterval res = results.getInterval(confidence);

					//If within interval, +1 to correct
					if (res.getInterval().contains(observed) ){

						if (correctMap.containsKey(confidence)){
							Integer oldval = correctMap.get(confidence);
							correctMap.put(confidence, oldval+1);
						}else
							correctMap.put(confidence, 1); //This is the first						
					}
					//If not within interval, +1 to incorrect
					else{
						if (incorrectMap.containsKey(confidence)){
							Integer oldval = incorrectMap.get(confidence);
							incorrectMap.put(confidence, oldval+1);
						}else
							incorrectMap.put(confidence, 1); //This is the first						
					}

				}

			}



		}


		//RESULTS
		//=======

		//For each confidence level, ensure we get lower error rate
		for (int i=confidences.size()-1;i>0; i--){
			double confidence = confidences.get(i);
			double errorRate=-1;
			if (incorrectMap.get(confidence)!=null){
				if (correctMap.get(confidence)!=null){
					errorRate = (double)incorrectMap.get(confidence)/(double)(correctMap.get(confidence)+ incorrectMap.get(confidence));
				}else{
					//all are incorrect
					errorRate=1;
				}
			}else{
				if (correctMap.get(confidence)!=null){
					//All are correct
					errorRate=0;
				}else{
					//should not happen, no correct and no incorrect
				}

			}

			String s="";
			if ((incorrectMap.get(confidence)!=null) && 
					correctMap.get(confidence)!=null)
				s = " [" + incorrectMap.get(confidence) + "/" + (correctMap.get(confidence)+ incorrectMap.get(confidence) + "]");

			System.out.println("Significance: " + (1-confidence) + 
					" --> error rate= " + errorRate + s);

			if ((1-confidence)>0.1)
				Assert.assertTrue(
						errorRate<(1-confidence) || //we get less than we expect - OK
						(errorRate-(1-confidence))<0.08  ); //we accept a certain misclassification by chance

		}


	}
	
	@Test
	public void testLargestDataset() throws Exception {
		Dataset data = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.REGRESSION_ENRICHMENT);
		
		SYS_OUT.println("Loaded " + data.getNumRecords() + " records from file");

		LoggerUtils.setDebugMode();
		ACPRegressor acp = getACPRegressionNormalized(true, true);
		acp.getICPImplementation().setPValueCalculator(new SplineInterpolatedPValue());
		acp.setStrategy(new RandomSampling(10, 0.2));

		doTestUsingCV(acp, data);
	}

	@Test
	public void testUsingOtherDataset() throws Exception {
		Dataset data = TestDataLoader.getInstance().getDataset(false, false);
		ACPRegressor acp = getACPRegressionLogNormalized(true, true, 0d); 
		acp.setStrategy(new RandomSampling(10, 0.2));
		doTestUsingCV(acp, data);
	}

	public void doTestUsingCV(ACPRegressor acp, Dataset data) throws Exception {
		int k = 10;
		long seed = System.currentTimeMillis();
		GlobalConfig.getInstance().setRNGSeed(seed);
		acp.setSeed(seed);

		TestRunner runner  = new TestRunner.Builder(new KFoldCV(k)).build();

		PlotMetric calib = new CPRegressionCalibrationPlotBuilder();
		PlotMetric efficiency = new CPRegressionEfficiencyPlotBuilder();
		SingleValuedMetric r2 = new R2();
		SingleValuedMetric rmse = new RMSE();

		List<Metric> metrics = Arrays.asList(calib, efficiency, r2, rmse);

		MetricFactory.setEvaluationPoints(metrics, CollectionUtils.listRange(0d, 1d, 0.01));

		runner.evaluate(data, acp, metrics);

		List<Plot2D> plots = new ArrayList<>();

		for (Metric m : metrics) {
			SYS_OUT.println(m.getName());
			if (m instanceof PlotMetric) {
				plots.add(((PlotMetric) m).buildPlot());
			} else if (m instanceof SingleValuedMetric) {
				SYS_OUT.println(((SingleValuedMetric) m).asMap());
			}
		}

		SYS_OUT.println(new MergedPlot(plots).getAsCSV());



	}

	@Test
	@Category(PerformanceTest.class)
	public void testACPValidityGluco() throws IllegalArgumentException, IOException, Exception {
		ICPRegressor icp = new ICPRegressor(new AbsDiffNCM(new LinearSVR()));

		Dataset sp = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.REGRESSION_ENRICHMENT);

		List<Double> confs = CollectionUtils.listRange(0, 1, .05);
		
		ACPRegressor acp = new ACPRegressor(icp, new RandomSampling(10, .15));
		
		TestRunner runner = new TestRunner.Builder(new KFoldCV(10)).build();
		
		List<Metric> metrics = MetricFactory.getACPRegressionMetrics();
		MetricFactory.setEvaluationPoints(metrics, confs);
		metrics.add(new CPAccuracy(0.75));
		
		runner.evaluate(sp, acp, metrics);
		
		List<Plot2D> plots = new ArrayList<>();

		for (Metric m : metrics) {
			SYS_OUT.println(m.getName());
			if (m instanceof PlotMetric) {
				plots.add(((PlotMetric) m).buildPlot());
			} else if (m instanceof SingleValuedMetric) {
				SYS_OUT.println(((SingleValuedMetric) m).asMap());
			}
		}

		SYS_OUT.println(new MergedPlot(plots).getAsCSV());
	}
	
	@Test
	public void testSmallCalibrationSets() throws Exception {
		GlobalConfig.getInstance().setRNGSeed(12412409124l);
		
		Dataset p = TestDataLoader.getInstance().getDataset(false, true);
		p.getDataset().shuffle();
		// SYS_ERR.println(p.getNumRecords());

		ACPRegressor acp = new ACPRegressor(new LogNormalizedNCM(new LinearSVR(),.1),
				new RandomSampling(1, .1));
//		acp.getICPImplementation().setPValueCalculator(new LinearInterpolationPValue());
		
		List<Metric> metrics = MetricFactory.getACPRegressionMetrics();
		MetricFactory.setEvaluationPoints(metrics, CollectionUtils.listRange(0, 1, 0.01));
		
		TestRunner runner = new TestRunner.Builder(new KFoldCV(10)).build();
		runner.evaluate(p,acp, metrics);
		
		
		List<Plot2D> plots = new ArrayList<>();
		for (Metric m : metrics) {
			SYS_OUT.println(m.getName());
			if (m instanceof PlotMetric) {
				Plot2D plot = ((PlotMetric) m).buildPlot();
				plots.add(plot);
			} else if (m instanceof SingleValuedMetric) {
				SYS_OUT.println(((SingleValuedMetric) m).asMap());
			}
		}

		SYS_OUT.println(new MergedPlot(plots).getAsCSV());
		
		
	}

}

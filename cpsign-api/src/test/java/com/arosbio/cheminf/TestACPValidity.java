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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.exception.CDKException;

import com.arosbio.chem.io.in.CSVFile;
import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.commons.MathUtils;
import com.arosbio.data.Dataset;
import com.arosbio.data.FeatureVector;
import com.arosbio.ml.cp.CPRegressionPrediction;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.testutils.TestChemDataLoader;
import com.arosbio.testutils.UnitTestBase;
import com.google.common.base.Stopwatch;

/**
 * 
 * @author ola
 *
 */
@Category(PerformanceTest.class)
public class TestACPValidity extends UnitTestBase{

	private static final double STEP_SIZE = 0.1;

//	@Test
	public void TestACPRegressionValidity() throws Exception{

		int observationsToRun = 490;
		int rounds = 10;

		Stopwatch totalwatch = Stopwatch.createStarted();

		List<Double> confidences= new ArrayList<>();
		for (double i=0;i<1;i=i+STEP_SIZE){
			if (i==0) continue;
			confidences.add(Math.round(i * 100.0) / 100.0);
		}

		//Read in problem from file
		Dataset problem = TestChemDataLoader.loadDataset(TestResources.SVMLIGHTFiles.REGRESSION_HOUSING);

		ACPRegressor lacp = getACPRegressionNormalized(true, true);

		System.out.println("Time for dataset setup: " + totalwatch);

		runModeling(confidences, lacp, problem, observationsToRun, rounds);

		System.out.println("Elapsed total time: " + totalwatch);


	}


	
	
	@Test
	public void TestSignaturesCPRegressionValidity() throws Exception, CDKException, CloneNotSupportedException{

		int observationsToRun = 500;
		int rounds = 1;
		
		Stopwatch totalwatch = Stopwatch.createStarted();

		List<Double> confidences= new ArrayList<>();
		for (double i=0;i<1;i=i+STEP_SIZE){
			if (i==0) continue;
			confidences.add(Math.round(i * 100.0) / 100.0);
		}

		//The data file
		CSVCmpdData trainData = TestResources.Reg.getSolubility_5k();

		ACPRegressor lacp = getACPRegressionNormalized(false, true);
		ChemCPRegressor signacp = new ChemCPRegressor(lacp, 1, 3);
		ChemDataset problem = signacp.getDataset(); 
		problem.add(new CSVFile(trainData.uri()).getIterator(),trainData.property());

		System.out.println("Time for dataset setup: " + totalwatch);

		runModeling(confidences, lacp, problem,observationsToRun, rounds);

		System.out.println("Elapsed total time: " + totalwatch);

	}

	public void runModeling(List<Double> confidences,
			ACPRegressor lacp, Dataset problem, int observationsToRun, int rounds) throws Exception {
		Stopwatch modelwatch = Stopwatch.createStarted();

		//Map from confidence level to number of correct predictions 
		Map<Double, Integer> correctMap = new HashMap<Double, Integer>();
		//Map from confidence level to number of incorrect predictions 
		Map<Double, Integer> incorrectMap = new HashMap<Double, Integer>();

		Map<Double, List<Double>> intervalSizes = new HashMap<>();

		//Example map to list of interval sizes
		Map<Integer, List<Double>> exampleIntervals = new HashMap<>();
		
		
		for (int round=0;round<rounds;round++){
			System.out.println("Starting round " + (round+1) + " of " + rounds );

			//Permute the problem dataset
			problem.shuffle();

			//Loop over the last observations in the dataset and predict them in an online setting
			for (int l = problem.getDataset() .size()-(observationsToRun-1); l<problem.getDataset() .size(); l++){

				if (l%100==0)
				System.out.println("Dataset subsize: " + l + " of " + problem.getDataset() .size());

				//Set up incremental dataset
				Dataset growingDataSet = new Dataset();

				//Add all data until current dataset size
				for (int cnt=0; cnt<l; cnt++){
					growingDataSet.getDataset() .add(problem.getDataset() .get(cnt));
//					growingDataSet.getY().add(problem.getY().get(cnt));

				}

				//Take next observation as example to predict
				FeatureVector example = problem.getDataset() .get(l).getFeatures();
				Double observed = problem.getDataset() .get(l).getLabel();

				//Train model on the growing dataset which is problem minus last row
				lacp.train(growingDataSet); //, CALIBRATION_PART, NR_MODELS);

				//Predict
				CPRegressionPrediction results = lacp.predict(example,confidences);

//				for (CPRegressionPrediction result : results.get){
				for (double conf: confidences) {
					double confidence = results.getInterval(conf).getConfidence();

					//If within interval, +1 to correct
					if (results.getInterval(conf).getInterval().contains(observed) ){

							correctMap.put(confidence, correctMap.getOrDefault(confidence,0) + 1); //This is the first						
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
		
		System.out.println("MEDIAN WIDTHS for 0.8 confidence");
		for (int l = problem.getDataset().size()-(observationsToRun-1); l<problem.getDataset() .size(); l++){
			System.out.println(l + " " + MathUtils.median(exampleIntervals.get(l)));
		}
		
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

			double mediansize = MathUtils.median(intervalSizes.get(confidence)); 

			System.out.println("Significance: " + (1-confidence) + 
					" --> error rate= " + errorRate + s + "  median interval size: " + mediansize);

//			Assert.assertTrue( (1-confidence) > errorRate);

		}

		System.out.println("Elapsed modeling time: " + modelwatch);
	}

}

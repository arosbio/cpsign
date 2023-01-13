/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.algorithms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import com.arosbio.commons.GlobalConfig;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.TestDataLoader;


/**
 * 
 */
@Category(PerformanceTest.class)
@RunWith(Enclosed.class)
public class TestThreadSafety {

	// private static final Logger LOGGER = LoggerFactory.getLogger(TestThreadSafety.class);

	static {
		GlobalConfig.getInstance().setRNGSeed(98765l);
	}

	public static class TestReturn {
		public List<Map<Integer,Double>> predictions = new ArrayList<>();
		public int ID;
	}

	public static class TestRegressionReturn {
		public List<Double> predictions = new ArrayList<>();
		public int ID;
	}

	public static void assertEqualResults(List<Future<TestReturn>> results) throws Exception {
		int job =0;
		TestReturn firstResult = null;
		for(Future<TestReturn> f : results) {
			if (firstResult == null)
				firstResult = f.get();
			else {
				System.err.println("Comparing result from job " + job);
				TestReturn next = f.get();
				for (int i = 0; i<next.predictions.size(); i++){
					TestUtils.assertEquals(firstResult.predictions.get(i),next.predictions.get(i));
				}
			}
			job++;
			
		}
	}

	public static void assertEqualRegResults(List<Future<TestRegressionReturn>> results) throws Exception {
		int job =0;
		TestRegressionReturn firstResult = null;
		for(Future<TestRegressionReturn> f : results) {
			if (firstResult == null)
				firstResult = f.get();
			else {
				System.err.println("Comparing result from job " + job);
				TestRegressionReturn next = f.get();
				TestUtils.assertEquals(firstResult.predictions,next.predictions);
			}
			job++;
			
		}
	}

	public static class LIBLINEAR_TESTS {

		@Test
		public void testLibLinearPredSameInstance() throws Exception {
			int numThreads = 500;
			
			SubSet allData = TestDataLoader.getInstance().getDataset(false, false).getDataset();
			SubSet[] trainTest = allData.splitRandom(0.8);

			List<DataRecord> trainRecs = trainTest[0];
			List<DataRecord> testRecs = trainTest[1];

			LinearSVR model = new LinearSVR();
			model.train(trainRecs);

			ExecutorService executor = Executors.newFixedThreadPool(numThreads);
			List<Future<TestRegressionReturn>> results = new ArrayList<>(numThreads);

			for(int i=0;i<numThreads; i++) {
				results.add(executor.submit(new LibLinearPredictUsingSameModel(model, testRecs, i)));
			}

			executor.shutdown();
			executor.awaitTermination(5, TimeUnit.MINUTES);

			assertEqualRegResults(results);
		}

		public static class LibLinearPredictUsingSameModel implements Callable<TestRegressionReturn> {
			private LinearSVR model;
			private List<DataRecord> testRecs;
			private int id;
			LibLinearPredictUsingSameModel(LinearSVR m,List<DataRecord> testRecs, int ID){
				this.model = m;
				this.testRecs = testRecs;
				this.id = ID;
			}
			@Override
			public TestRegressionReturn call() throws Exception {
				Thread.sleep((int) (Math.random()*5));
				System.err.println("NOW predicting for ID " + id);
				TestRegressionReturn result = new TestRegressionReturn();
				for (DataRecord r : testRecs){
					result.predictions.add(model.predictValue(r.getFeatures()));
				}
				return result;
			}
		}
	
		@Test
		public void testLibLinearTrainAndPredSeparate() throws Exception {
			int numThreads = 500;
			
			SubSet allData = TestDataLoader.getInstance().getDataset(true, false).getDataset();
			System.err.println(allData);
			SubSet[] trainTest = allData.splitRandom(0.8);

			List<DataRecord> trainRecs = trainTest[0];
			List<DataRecord> testRecs = trainTest[1];

			ExecutorService executor = Executors.newFixedThreadPool(numThreads);
			List<Future<TestReturn>> results = new ArrayList<>(numThreads);
			for(int i=0;i<numThreads; i++) {
				results.add(executor.submit(new LibLinearTrainAndPredictSeparate(trainRecs, testRecs, i)));
			}
			
			executor.shutdown();
			executor.awaitTermination(5, TimeUnit.MINUTES);

			assertEqualResults(results);
		}
		
		public static class LibLinearTrainAndPredictSeparate implements Callable<TestReturn> {

			private List<DataRecord> trainRecs, testRecs;
			private int id;

			public LibLinearTrainAndPredictSeparate(List<DataRecord> train, List<DataRecord> test, int id){
				this.trainRecs = train;
				this.testRecs = test;
				this.id = id;
			}

			public TestReturn call() throws Exception {
			
				LinearSVC model = new LinearSVC();
				model.train(trainRecs);

				Thread.sleep((int)(Math.random()*50)); // Sleep for a little while 
				System.err.println("now running predict from " + id);

				TestReturn result = new TestReturn();
				result.ID = id;
				result.predictions = new ArrayList<>();
				for (DataRecord r : testRecs){
					result.predictions.add(model.predictScores(r.getFeatures()));
				}
				return result;
			}
		}
		
	}
	

	public static class LIBSVM_TESTS {

		@Test
		public void testLibSVMPredSameInstance() throws Exception {
			int numThreads = 500;
			
			SubSet allData = TestDataLoader.getInstance().getDataset(false, false).getDataset();
			SubSet[] trainTest = allData.splitRandom(0.8);

			List<DataRecord> trainRecs = trainTest[0];
			List<DataRecord> testRecs = trainTest[1];

			EpsilonSVR model = new EpsilonSVR();
			model.train(trainRecs);

			ExecutorService executor = Executors.newFixedThreadPool(numThreads);
			List<Future<TestRegressionReturn>> results = new ArrayList<>(numThreads);

			for(int i=0;i<numThreads; i++) {
				results.add(executor.submit(new LibLinearPredictUsingSameModel(model, testRecs, i)));
			}

			executor.shutdown();
			executor.awaitTermination(5, TimeUnit.MINUTES);

			assertEqualRegResults(results);
		}

		public static class LibLinearPredictUsingSameModel implements Callable<TestRegressionReturn> {
			private EpsilonSVR model;
			private List<DataRecord> testRecs;
			private int id;
			LibLinearPredictUsingSameModel(EpsilonSVR m,List<DataRecord> testRecs, int ID){
				this.model = m;
				this.testRecs = testRecs;
				this.id = ID;
			}
			@Override
			public TestRegressionReturn call() throws Exception {
				Thread.sleep((int) (Math.random()*5));
				System.err.println("NOW predicting for ID " + id);
				TestRegressionReturn result = new TestRegressionReturn();
				for (DataRecord r : testRecs){
					result.predictions.add(model.predictValue(r.getFeatures()));
				}
				return result;
			}
		}

		@Test
		public void testC_SVCTrainAndPredSeparate() throws Exception {
			int numThreads = 100;
			
			SubSet allData = TestDataLoader.getInstance().getDataset(true, false).getDataset();
			System.err.println(allData);
			SubSet[] trainTest = allData.splitRandom(0.8);

			List<DataRecord> trainRecs = trainTest[0];
			List<DataRecord> testRecs = trainTest[1];

			ExecutorService executor = Executors.newFixedThreadPool(numThreads);
			List<Future<TestReturn>> results = new ArrayList<>(numThreads);
			for(int i=0;i<numThreads; i++) {
				results.add(executor.submit(new LibSVMTrainAndPredictSeparate(trainRecs, testRecs, i)));
			}
			
			executor.shutdown();
			executor.awaitTermination(5, TimeUnit.MINUTES);

			assertEqualResults(results);
			
		}

		public static class LibSVMTrainAndPredictSeparate implements Callable<TestReturn> {

			private List<DataRecord> trainRecs, testRecs;
			private int id;

			public LibSVMTrainAndPredictSeparate(List<DataRecord> train, List<DataRecord> test, int id){
				this.trainRecs = train;
				this.testRecs = test;
				this.id = id;
			}

			public TestReturn call() throws Exception {
			
				C_SVC model = new C_SVC();
				model.train(trainRecs);

				Thread.sleep((int)(Math.random()*50)); // Sleep for a little while 
				System.err.println("now running predict from " + id);

				TestReturn result = new TestReturn();
				result.ID = id;
				result.predictions = new ArrayList<>();
				for (DataRecord r : testRecs){
					result.predictions.add(model.predictScores(r.getFeatures()));
				}
				return result;
			}
		}
	}
}

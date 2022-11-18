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
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.PlattScaledC_SVC;
import com.arosbio.ml.cp.icp.ICPClassifier;
import com.arosbio.ml.cp.nonconf.calc.LinearInterpolationPValue;
import com.arosbio.ml.cp.nonconf.classification.InverseProbabilityNCM;
import com.arosbio.ml.cp.nonconf.classification.NCMMondrianClassification;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.cp.nonconf.classification.PositiveDistanceToHyperplaneNCM;
import com.arosbio.ml.cp.nonconf.classification.ProbabilityMarginNCM;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.MetricFactory;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.cp.CalibrationPlot;
import com.arosbio.ml.metrics.cp.classification.CPClassificationCalibrationPlotBuilder;
import com.arosbio.ml.metrics.cp.classification.MultiLabelPredictionsPlotBuilder;
import com.arosbio.ml.metrics.cp.classification.ObservedFuzziness;
import com.arosbio.ml.metrics.cp.classification.SingleLabelPredictionsPlotBuilder;
import com.arosbio.ml.metrics.plots.MergedPlot;
import com.arosbio.ml.metrics.plots.Plot2D;
import com.arosbio.ml.metrics.plots.PlotMetric;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.ml.testing.RandomSplit;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.testutils.ConfaiTestUtils;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.TestEnv;

/**
 * 
 * @author ola
 *
 */
@Category(PerformanceTest.class)
public class TestACPClassificationValidity extends TestEnv {

	private static final int SIZE = 200;
	static final double tol_2class = 0.05, tol_3class = 0.09;
	
	private static Dataset multiclassP;
	
	@BeforeClass
	public static void loadMulticlassDs() {
		try (InputStream multiclassStream = TestResources.SVMLIGHTFiles.CLASSIFICATION_3CLASS_CON4.openStream()){
			Dataset p = Dataset.fromLIBSVMFormat(multiclassStream);
			SubSet d = p.getDataset();
			SubSet[] ds = d.splitRandom(3456789, 0.1); // Use only 6.7k examples
			multiclassP = new Dataset();
			multiclassP.withDataset(ds[0]);
		} catch (Exception e) {
			Assert.fail("Failed loading the multiclass problem: " + e.getMessage());
		}
	}

	
	@Test
	public void testInverseProb() {
		doNCMValidityCheck(new InverseProbabilityNCM(new PlattScaledC_SVC()));
	}
	
	@Test
	public void testProbMargin() {
		doNCMValidityCheck(new ProbabilityMarginNCM(new PlattScaledC_SVC()));
	}
	
	@Test
	public void testNegDistance() {
		doNCMValidityCheck(new NegativeDistanceToHyperplaneNCM(new LinearSVC()));
	}
	
	@Test
	public void testPosDist() {
		doNCMValidityCheck(new PositiveDistanceToHyperplaneNCM(new LinearSVC()));
	}
	
	
	private static void doNCMValidityCheck(NCMMondrianClassification ncm) {
		try {
			doNCMValidityCheck(ncm, TestDataLoader.getInstance().getDataset(true, false), tol_2class);
		} catch (Exception e) {
			Assert.fail("Failed 2 class problem: " + e.getMessage());
		}
		
		try {
			doNCMValidityCheck(ncm, multiclassP.clone(), tol_3class);
		} catch (Exception e) {
			Assert.fail("Failed 3 class problem: " + e.getMessage());
		}
	}
	
	
	private static void doNCMValidityCheck(NCMMondrianClassification ncm, Dataset p, double tol) {
		try {
		ICPClassifier icp = new ICPClassifier(ncm);
		
		TestRunner tester = new TestRunner.Builder(new RandomSplit()).build();
		
		CPClassificationCalibrationPlotBuilder calibMetric = new CPClassificationCalibrationPlotBuilder(CollectionUtils.listRange(0, 1, 0.01));
		tester.evaluate(p, new ACPClassifier(icp, new RandomSampling(1, DEFAULT_CALIBRATION_RATIO)), Arrays.asList(calibMetric));
		
		CalibrationPlot plt = calibMetric.buildPlot();
		Assert.assertTrue(plt.getNumExamples() > 20);
		
		ConfaiTestUtils.assertValidModel(plt, tol);
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail("Failed due to: " + e.getMessage());
		}
	}
	
	@Test
	public void testSmallCalibrationSets() throws Exception {
		GlobalConfig.getInstance().setRNGSeed(12412409124l);
		
		Dataset p = TestDataLoader.getInstance().getDataset(true, true);
		p.getDataset().shuffle();

		ACPClassifier acp = new ACPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()), 
				new RandomSampling(1, .1));
		acp.getICPImplementation().setPValueCalculator(new LinearInterpolationPValue());
		
		List<Metric> metrics = MetricFactory.getCPClassificationMetrics(false);
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

	@Test
	public void TestACPCLassificationValidityLibLinear() throws IOException, IllegalAccessException{


		List<Double> lowP= new ArrayList<>();
		List<Double> highP= new ArrayList<>();
		List<Double> correctLabel= new ArrayList<>();

		//Read in problem from file
		Dataset problem = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS);

		ACPClassifier acp = getACPClassificationNegDist(true, true); 

		for (int l = problem.getDataset().size()-SIZE; l<problem.getDataset().size(); l++){

			System.out.println("Dataset subsize: " + l);

			//Set up incremental dataset
			Dataset growingDataSet = new Dataset();

			//Add all data until current dataset size
			for (int cnt=0; cnt<l; cnt++){
				growingDataSet.getDataset().add(problem.getDataset().get(cnt));
				//				growingDataSet.getY().add(problem.getY().get(cnt));

				//				StringBuffer sb = new StringBuffer();
				//				for (int r = 0; r< problem.getDataset().get(cnt).length; r++){
				//					sb.append(" " + problem.getDataset().get(cnt)[r].getIndex()+":"+ problem.getDataset().get(cnt)[r].getValue());
				//				}
				//				
				//				System.out.println("Added Y: " + problem.getY().get(cnt) 
				//						+ " ; Added X: " + sb.toString());

			}

			//Take next observation as example to predict
			DataRecord example = problem.getDataset().get(l);
			//			System.out.println("Predicting example: ");
			//			for (int r = 0; r< example.length; r++){
			//				System.out.print(" " + example[r].getIndex()+":"+ example[r].getValue());
			//			}

			//Train and Predict
			acp.train(problem);
			Map<Integer,Double> result = acp.predict(example.getFeatures());

			System.out.println("Predicted result: " + result);
			System.out.println("Obesrved value: " + problem.getDataset().get(l).getLabel());

			lowP.add(result.get(0));
			highP.add(result.get(1));
			correctLabel.add(problem.getDataset().get(l).getLabel());

		}

		//Interpret results
		for (double significance=0.01; significance<1;significance=significance+0.01){
			int incorrect = 0;
			for (int example=0; example<correctLabel.size(); example++){
				if (correctLabel.get(example)==0){
					//predicted=0
					if (lowP.get(example)<=significance){
						//Incorrect prediction
						incorrect++;
					}
				}
				else{
					//predicted=1
					if (highP.get(example)<=significance){
						//Incorrect prediction
						incorrect++;
					}

				}
			}
			double errorRate = (double)incorrect/(double)correctLabel.size();
			System.out.println("Significance " + significance + ": " + incorrect + " incorrect of " + correctLabel.size() + " --> rate= " + errorRate);
			//			System.out.println("Error rate for significance: " + significance + " = " + errorRate);

			Assert.assertEquals(significance, errorRate, 0.1);

		}

	}

	public static final double ALLOWED_TOLERANCE_TO_VALIDITY = 0.1;
	
	@Test
	public void testUsingCV() throws Exception {
		int k = 10;
		long seed = 21412412412l;
		GlobalConfig.getInstance().setRNGSeed(seed);

		Dataset data = TestDataLoader.getInstance().getDataset(true, false);
		ACPClassifier acp = getACPClassificationNegDist(true, true);

		TestRunner runner  = new TestRunner.Builder(new KFoldCV(k)).build();

		PlotMetric calib = new CPClassificationCalibrationPlotBuilder();
		PlotMetric single = new SingleLabelPredictionsPlotBuilder();
		PlotMetric multi = new MultiLabelPredictionsPlotBuilder();
		Metric obsFuzz = new ObservedFuzziness();

		List<Metric> metrics = Arrays.asList(calib, single, multi, obsFuzz);

		MetricFactory.setEvaluationPoints(metrics, CollectionUtils.listRange(0d, 1d, 0.01));

		runner.evaluate(data, acp, metrics);

		List<Plot2D> plots = new ArrayList<>();

		for (Metric m : metrics) {
			SYS_OUT.println(m.getName());
			if (m instanceof PlotMetric) {
				Plot2D p = ((PlotMetric) m).buildPlot();
				plots.add(p);
				if (p instanceof CalibrationPlot) {
					Assert.assertTrue(((CalibrationPlot) p).isValidWithinTolerance(ALLOWED_TOLERANCE_TO_VALIDITY));
				}
			} else if (m instanceof SingleValuedMetric) {
				SYS_OUT.println(((SingleValuedMetric) m).asMap());
			}
		}

		SYS_OUT.println(new MergedPlot(plots).getAsCSV());

		printLogs();

	}

	
	public void doCVMulticlass(Dataset data) throws Exception {
		ACPClassifier acp = new ACPClassifier(
				new ICPClassifier(new InverseProbabilityNCM(new PlattScaledC_SVC())),
				new RandomSampling(DEFAULT_NUM_MODELS, DEFAULT_CALIBRATION_RATIO));

		TestRunner tr = new TestRunner.Builder(new KFoldCV(10))
			.evalPoints(CollectionUtils.listRange(0, 1, 0.05)).build();


		List<Metric> results = tr.evaluate(data, acp);

		List<Plot2D> plots = new ArrayList<>();

		for (Metric m : results) {
			SYS_OUT.println(m.getName());
			if (m instanceof PlotMetric) {
				Plot2D p = ((PlotMetric) m).buildPlot();
				plots.add(p);
				if (p instanceof CalibrationPlot) {
					Assert.assertTrue(((CalibrationPlot) p).isValidWithinTolerance(ALLOWED_TOLERANCE_TO_VALIDITY));
				}
			} else if (m instanceof SingleValuedMetric) {
				SYS_OUT.println(((SingleValuedMetric) m).asMap());
			}
		}

		SYS_OUT.println(new MergedPlot(plots).getAsCSV());
	}

	@Test
	public void testACPMulticlassConnect4() throws Exception {
		
		Dataset p = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.CLASSIFICATION_3CLASS_CON4);
		p.shuffle();
		SubSet d = p.getDataset().splitStatic(1000)[0];
		p.withDataset(d);
		doCVMulticlass(p);
		
	}
	
	@Test
	public void testACPMultiDNA() throws Exception {
		doCVMulticlass(TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.CLASSIFICATION_3CLASS));
	}


}

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.SparseFeature;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.PlattScaledC_SVC;
import com.arosbio.ml.cp.icp.ICPClassifier;
import com.arosbio.ml.cp.nonconf.classification.InverseProbabilityNCM;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.MetricFactory;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.cp.CalibrationPlot;
import com.arosbio.ml.metrics.cp.classification.CPClassificationCalibrationPlotBuilder;
import com.arosbio.ml.metrics.cp.classification.CPClassificationMetric;
import com.arosbio.ml.metrics.cp.classification.MultiLabelPredictionsPlotBuilder;
import com.arosbio.ml.metrics.cp.classification.SingleLabelPredictionsPlotBuilder;
import com.arosbio.ml.metrics.plots.MergedPlot;
import com.arosbio.ml.metrics.plots.Plot2D;
import com.arosbio.ml.metrics.plots.PlotMetric;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.ConfaiTestUtils;
import com.arosbio.testutils.UnitTestInitializer;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.Jsoner;

import de.bwaldvogel.liblinear.Linear;

/**
 * 
 * @author ola
 *
 */
@Category(UnitTest.class)
public class TestACPClassification extends UnitTestInitializer{

	private static final int numToTest = 20;
	private static final boolean PRINT_RESULTS = false;

	//	@Test
	public void arrToJsonStr() throws JsonException {
		String jsonARR = Jsoner.serialize(Arrays.asList(0.5,0.6,.076, 1d));
		SYS_ERR.println(jsonARR);
		JsonArray arr = (JsonArray) Jsoner.deserialize(jsonARR);
		SYS_ERR.println("Parsed: " + arr);
	}

		// @Test
	public void testPkg(){
		new Linear();
		Package pkg = this.getClass().getClassLoader().getDefinedPackage("de.bwaldvogel.liblinear");
		System.out.println("Package name:\t" + pkg.getName());
		System.out.println("Spec title:\t" + pkg.getSpecificationTitle());
		System.out.println("Spec vendor:\t" + pkg.getSpecificationVendor()); 
		System.out.println("Spec version:\t" + pkg.getSpecificationVersion());
		System.out.println("Impl title:\t" + pkg.getImplementationTitle());
		System.out.println("Impl vendor:\t" + pkg.getImplementationVendor());
		System.out.println("Impl version:\t" + pkg.getImplementationVersion());
		printLogs();
	}

	//	@Test
	public void generateResultsForEbba() throws Exception{
		Dataset problem = TestDataLoader.getInstance().getDataset(true, false);
		SubSet[] testTrainRecs = problem.getDataset().splitRandom(.5);
		SubSet test = testTrainRecs[0];
		problem.setDataset(testTrainRecs[1]);

		ACPClassifier acp = new ACPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()), new RandomSampling(1, .3));
		acp.train(problem);

		Map<Integer,ICPClassifier> icps = acp.getPredictors();
		ICPClassifier icp = (ICPClassifier) icps.values().iterator().next();
		

		// Print nonconf-values
		// Map<Integer, List<Double>> nonconfs = icp.getNonconfScores();
		//		File out = new File("/Users/staffan/Desktop/ebba.nonconf");
		//		CSVPrinter nonconfPrinter = CSVFormat.DEFAULT.print(out,StandardCharsets.UTF_8);
		//
		//		nonconfPrinter.printRecord("class","nonconf");
		//		for (Map.Entry<Integer, List<Double>> ncs : nonconfs.entrySet()) {
		//			int label = ncs.getKey();
		//			for (double nc : ncs.getValue()) {
		//				nonconfPrinter.printRecord(label, nc);
		//			}
		//
		//		}
		//		nonconfPrinter.flush();
		//		nonconfPrinter.close();

		// NCMMondrianClassification ncm = icp.getNCM();

		CPClassificationCalibrationPlotBuilder plotBuilder = new CPClassificationCalibrationPlotBuilder();
		SingleLabelPredictionsPlotBuilder singleBuilder = new SingleLabelPredictionsPlotBuilder();
		MultiLabelPredictionsPlotBuilder multiBuilder = new MultiLabelPredictionsPlotBuilder();
		List<CPClassificationMetric> mets = new ArrayList<>();
		mets.add(plotBuilder);
		mets.add(singleBuilder);
		mets.add(multiBuilder);
		MetricFactory.setEvaluationPoints(mets, CollectionUtils.listRange(0.01, 0.99, .01));


		// Predict and get the ncs and p-values for each test-object
		for (DataRecord r : test) {
			Map<Integer,Double> pvals = icp.predict(r.getFeatures());
			plotBuilder.addPrediction((int)r.getLabel(), pvals);
			singleBuilder.addPrediction((int)r.getLabel(), pvals);
			multiBuilder.addPrediction((int)r.getLabel(), pvals);
		}

		List<Plot2D> plots = new ArrayList<>();
		plots.add(plotBuilder.buildPlot());
		plots.add(singleBuilder.buildPlot());
		plots.add(multiBuilder.buildPlot());

		MergedPlot mplot = new MergedPlot(plots);
		SYS_ERR.println(mplot.getAsCSV());
	}

	@Test
	public void testACPClassProbabilityNCM() throws Exception {
		long seed = System.currentTimeMillis();
		int numRecordsToUse = 1000;

		//Read in problem from file
		Dataset problem = TestDataLoader.getInstance().getDataset(true, false).clone();
		problem.setDataset(problem.getDataset().splitStatic(numRecordsToUse)[0]);


		ACPClassifier acp = new ACPClassifier(
				new ICPClassifier(new InverseProbabilityNCM(new PlattScaledC_SVC())),
				new RandomSampling(DEFAULT_NUM_MODELS, DEFAULT_CALIBRATION_RATIO));

		TestRunner tr = new TestRunner.Builder(new KFoldCV(5, seed)).build();

		List<Metric> results = tr.evaluate(problem, acp);

		for (Metric m: results) {
			try {
				if (m instanceof SingleValuedMetric) {
					System.out.println(((SingleValuedMetric) m).asMap());
				} else if (m instanceof PlotMetric) {
					Plot2D plot = ((PlotMetric) m).buildPlot();
					System.out.println(plot.getAsCSV());
					if (plot instanceof CalibrationPlot) {
						ConfaiTestUtils.assertValidModel((CalibrationPlot)plot, 0.1);
					}
				}
			} catch (Exception e) {
				e.printStackTrace(SYS_OUT);
			}
		}
		if (PRINT_RESULTS)
			printLogs();
	}

	@Test
	public void testACPClassification() throws Exception {
		long seed = System.currentTimeMillis();

		//Read in problem from file
		Dataset problem = TestDataLoader.getInstance().getDataset(true, false).clone();

		ACPClassifier lacp = getACPClassificationNegDist(true, true); 
		lacp.setSeed(seed);
		ACPClassifier lccp = getACPClassificationNegDist(true, false); 
		lccp.setSeed(seed);

		SubSet[] ds_splits = problem.getDataset().splitStatic(numToTest);
		List<DataRecord> testExamples = ds_splits[0];
		problem.setDataset(ds_splits[1]); // Set training examples

		//Train model
		lacp.train(problem);

		//Train model
		lccp.train(problem);

		//Predict the first examples
		int i= 0;
		for (DataRecord example : testExamples){
			System.out.println("== Example " + i );
			Map<Integer, Double> resultACP = lacp.predict(example.getFeatures());
			Map<Integer, Double> resultCCP = lccp.predict(example.getFeatures());

			System.out.println("ACP p-values=" + resultACP);
			System.out.println("CCP p-values=" + resultCCP);
			System.out.println("Correct=" + example.getLabel());

			for(Integer label: resultACP.keySet())
				Assert.assertEquals(resultACP.get(label), resultCCP.get(label), 0.2);

			List<SparseFeature> gradientACP = lacp.calculateGradient(example.getFeatures());
			List<SparseFeature> gradientCCP = lccp.calculateGradient(example.getFeatures());

			System.out.println("ACP gradient =" + gradientACP);
			System.out.println("CCP gradient =" + gradientCCP);

			Assert.assertEquals(gradientACP.size(),example.getFeatures().getNumExplicitFeatures());
			Assert.assertEquals(gradientCCP.size(),example.getFeatures().getNumExplicitFeatures());
			i++;
		}


	}

//	@Test
	public void listConfigurables() {
		ACPClassifier acp = new ACPClassifier(
				new NegativeDistanceToHyperplaneNCM(new LinearSVC()), new RandomSampling());

		for (ConfigParameter p : acp.getConfigParameters())
			SYS_ERR.println(p);
	}

}


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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.ml.algorithms.impl.DefaultMLParameterSettings;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.SVC;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.cp.tcp.TCPClassifier;
import com.arosbio.ml.gridsearch.GridSearch;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.classification.BalancedAccuracy;
import com.arosbio.ml.metrics.classification.F1Score;
import com.arosbio.ml.metrics.cp.classification.ObservedFuzziness;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.sampling.SamplingStrategy;
import com.arosbio.ml.testing.FixedTestSet;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.testutils.BenchmarkResources;
import com.arosbio.testutils.TestChemDataLoader;

/**
 * This class checks the performance of ACP vs TCP for a few small datasets that was found to be problematic - i.e. they
 * gave much better predictive performance for ACP than TCP. This was then done to debug what was going on and any ways of
 * resolving the issues. NOTE: these data sets were chosen because they had much worse efficiency than the ACP model so they 
 * are not representative of a true comparison of ACP vs TCP modelling.
 * @author Staffan
 */
@Category(PerformanceTest.class)
public class BenchmarkACPvsTCP {


    private static List<Metric> clone(List<Metric> i, boolean includeOF){
        List<Metric> out = new ArrayList<>();
        for (Metric m : i){
			if (m instanceof ObservedFuzziness && ! includeOF)
				continue;
            out.add(m.clone());
        }
        return out;
    }

	private static final long seed = 567890987654l;
	private static final SamplingStrategy ACP_STRATEGY = new RandomSampling(10, .2);


	/*
	Running evaluation of dataset ADAM5 of size: 268x2315 with label-freq: {0.0=129, 1.0=139}
Algorithm/Predictor	Underlying Model	Parameters	F1Score	Balanced Accuracy	OF
LinearSVC	-	default	0.7037037037037037	0.7046703296703296	-
LinearSVC	-	svc-tuned	0.6909090909090909	0.6854395604395604	-
ACP	LinearSVC	default	0.6551724137931034	0.6277472527472527	0.28827295917072226
ACP	LinearSVC	svc-tuned	0.6551724137931034	0.6277472527472527	0.291646544746032
ACP	LinearSVC	CP-tuned	0.6551724137931034	0.6277472527472527	0.2894248083039856
TCP	LinearSVC	default	0.5614035087719298	0.5357142857142857	0.35732147006148185
TCP	LinearSVC	svc-tuned	0.5283018867924528	0.5384615384615384	0.3577449966300893
TCP	LinearSVC	CP-tuned	0.6785714285714286	0.6662087912087913	0.2890288777572365
C_SVC	-	default	0.6666666666666666	0.646978021978022	-
C_SVC	-	svc-tuned	0.6666666666666666	0.646978021978022	-
ACP	C_SVC	default	0.6440677966101694	0.6085164835164836	0.23231144651992897
ACP	C_SVC	svc-tuned	0.6440677966101694	0.6085164835164836	0.23231144651992897
ACP	C_SVC	CP-tuned	0.6428571428571429	0.6291208791208791	0.23200331611875666
TCP	C_SVC	default	0.7213114754098361	0.6813186813186813	0.3977190015479096
TCP	C_SVC	svc-tuned	0.7213114754098361	0.6813186813186813	0.3977190015479096
TCP	C_SVC	CP-tuned	0.6415094339622641	0.6497252747252746	0.2660883081720023
	 */
	@Test
	public void runADAM_CSV() throws Exception {
		evalDatasetToCSV(TestChemDataLoader.loadDataset(BenchmarkResources.getADAM5()), "ADAM5");
	}

	/*
Running evaluation of dataset Cyclin of size: 375x4206 with label-freq: {0.0=49, 1.0=326}
Algorithm/Predictor	Underlying Model	Parameters	F1Score	Balanced Accuracy	OF
LinearSVC	-	default	0.9285714285714286	0.5424242424242425	-
LinearSVC	-	svc-tuned	0.9285714285714286	0.5424242424242425	-
ACP	LinearSVC	default	0.7155963302752294	0.5954545454545455	0.29837962776952337
ACP	LinearSVC	svc-tuned	0.7272727272727273	0.603030303030303	0.3154100413590234
ACP	LinearSVC	CP-tuned	0.7758620689655172	0.5909090909090908	0.2961776606792743
TCP	LinearSVC	default	0.7478260869565218	0.5257575757575758	0.34533182197557705
TCP	LinearSVC	svc-tuned	0.7272727272727273	0.603030303030303	0.4066561768146251
TCP	LinearSVC	CP-tuned	0.6666666666666666	0.5651515151515152	0.4418612447038425
C_SVC	-	default	0.9285714285714286	0.5424242424242425	-
C_SVC	-	svc-tuned	0.9361702127659575	0.55	-
ACP	C_SVC	default	0.7586206896551724	0.5333333333333333	0.27538817156307216
ACP	C_SVC	svc-tuned	0.6851851851851852	0.5303030303030303	0.28367506601493675
ACP	C_SVC	CP-tuned	0.7478260869565218	0.5257575757575758	0.2778852602443714
TCP	C_SVC	default	0.66	0.7	0.4446909933843958
TCP	C_SVC	svc-tuned	0.5714285714285714	0.5121212121212121	0.4848097232107603
TCP	C_SVC	CP-tuned	0.8	0.42878787878787883	0.22344155889452433

	 */
	@Test
	public void runCyclin() throws Exception {
		evalDatasetToCSV(TestChemDataLoader.loadDataset(BenchmarkResources.getCyclin()), "Cyclin");
	}

	/*
Running evaluation of dataset D_Amino of size: 398x2580 with label-freq: {0.0=203, 1.0=195}
Algorithm/Predictor	Underlying Model	Parameters	F1Score	Balanced Accuracy	OF
LinearSVC	-	default	0.7948717948717948	0.7998749218261414	-
LinearSVC	-	svc-tuned	0.7804878048780488	0.7761100687929956	-
ACP	LinearSVC	default	0.8	0.7895559724828017	0.20162158440261874
ACP	LinearSVC	svc-tuned	0.7804878048780488	0.7761100687929956	0.2016395361887601
ACP	LinearSVC	CP-tuned	0.7804878048780488	0.7761100687929956	0.2016395361887601
TCP	LinearSVC	default	0.75	0.75046904315197	0.21849166574363688
TCP	LinearSVC	svc-tuned	0.775	0.7754846779237023	0.15008759506065533
TCP	LinearSVC	CP-tuned	0.775	0.7754846779237023	0.15008759506065533
C_SVC	-	default	0.7901234567901234	0.7883051907442151	-
C_SVC	-	svc-tuned	0.6972477064220184	0.5969355847404628	-
ACP	C_SVC	default	0.7710843373493976	0.7639149468417761	0.18709501713004756
ACP	C_SVC	svc-tuned	0.68	0.606629143214509	0.2549698852464806
ACP	C_SVC	CP-tuned	0.6666666666666666	0.5938086303939962	0.2853101835922428
TCP	C_SVC	default	0.7560975609756098	0.7510944340212633	0.21981464741319928
TCP	C_SVC	svc-tuned	0.66	0.5816135084427767	0.201116438083633
TCP	C_SVC	CP-tuned	0.66	0.5816135084427767	0.201116438083633
	 */
	@Test
	public void runD_Amino() throws Exception {
		evalDatasetToCSV(TestChemDataLoader.loadDataset(BenchmarkResources.getD_Amino()), "D_Amino");
	}
/*
Running evaluation of dataset Serine of size: 299x3083 with label-freq: {0.0=105, 1.0=194}
Algorithm/Predictor	Underlying Model	Parameters	F1Score	Balanced Accuracy	OF
LinearSVC	-	default	0.8735632183908046	0.7490842490842491	-
LinearSVC	-	svc-tuned	0.8351648351648352	0.6538461538461537	-
ACP	LinearSVC	default	0.8292682926829268	0.7216117216117216	0.16709271101215933
ACP	LinearSVC	svc-tuned	0.8444444444444444	0.6776556776556777	0.1499585940146454
ACP	LinearSVC	CP-tuned	0.8433734939759037	0.7344322344322345	0.160578278182198
TCP	LinearSVC	default	0.7894736842105263	0.717948717948718	0.24525312004103567
TCP	LinearSVC	svc-tuned	0.810126582278481	0.7197802197802198	0.2559079717252991
TCP	LinearSVC	CP-tuned	0.810126582278481	0.7197802197802198	0.2559079717252991
C_SVC	-	default	0.8351648351648352	0.6538461538461537	-
C_SVC	-	svc-tuned	0.8351648351648352	0.6538461538461537	-
ACP	C_SVC	default	0.8351648351648352	0.6538461538461537	0.16813613577912223
ACP	C_SVC	svc-tuned	0.8314606741573034	0.6648351648351648	0.14210614576388633
ACP	C_SVC	CP-tuned	0.8314606741573034	0.6648351648351648	0.14210614576388633
TCP	C_SVC	default	0.6933333333333334	0.5952380952380952	0.3552413403408353
TCP	C_SVC	svc-tuned	0.6753246753246753	0.5476190476190476	0.33311410329535374
TCP	C_SVC	CP-tuned	0.8	0.5586080586080586	0.1663311987301398

 */
	@Test
	public void runSerine() throws Exception {
		evalDatasetToCSV(TestChemDataLoader.loadDataset(BenchmarkResources.getSerine()), "Serine");
	}

	private static void evalDatasetToCSV(Dataset d, String dsName) throws Exception {

		System.out.printf("Running evaluation of dataset %s of size: %dx%d with label-freq: %s%n",
			dsName,d.getNumRecords(),d.getNumFeatures(),d.getLabelFrequencies());
		
		// Fix a seed
		
		GlobalConfig.getInstance().setRNGSeed(seed);
        
		
		// Split into a separate test-evaluation set (20% for model-evaluation)
		SubSet[] sets = d.getDataset().splitStratified(seed, .2);
		SubSet validationSet = sets[0];
		Dataset trainingSet = new Dataset().withDataset(sets[1]);

		KFoldCV gridTestStrategy = new KFoldCV(5).withSeed(seed);
		GridSearch tuner = new GridSearch.Builder().testStrategy(gridTestStrategy).evaluationMetric(new F1Score()).build();

		TestRunner finalEvaluator = new TestRunner.Builder(new FixedTestSet(validationSet))
			.calcMeanAndStd(false)
			.evalPoints(CollectionUtils.listRange(0,1,0.01)).build();
		List<Metric> metrics = Arrays.asList(new F1Score(), new BalancedAccuracy(), new ObservedFuzziness());

		CSVPrinter resultPrinter = CSVFormat.TDF.print(System.out);
		// Set the header
		resultPrinter.printRecord("Algorithm/Predictor", "Underlying Model","Parameters", "F1Score", "Balanced Accuracy", "OF");
		Map<String,List<?>> grid = Map.of("cost", DefaultMLParameterSettings.COST_CONFIG.getDefaultGrid());
		
		for (SVC svc : Arrays.asList(new LinearSVC(), new C_SVC())){

			// SVC default params
			
			List<Metric> metricsOut = finalEvaluator.evaluateClassifier(trainingSet, svc.clone(), clone(metrics,false));
			resultPrinter.printRecord(svc.getName(), 
				"-", // Underlying model
				"default", // Parameters
				((SingleValuedMetric) metricsOut.get(0)).getScore(), // F1 score
				((SingleValuedMetric) metricsOut.get(1)).getScore(), // balanced accuracy
				"-" // OF
				);
			// SVC tuned
			SVC tunedSVC = svc.clone();
			tuner.search(trainingSet, tunedSVC, grid);
			metricsOut = finalEvaluator.evaluateClassifier(trainingSet, tunedSVC, clone(metrics,false));
			resultPrinter.printRecord(tunedSVC.getName(), 
				"-", // Underlying model
				"svc-tuned", // Parameters
				((SingleValuedMetric) metricsOut.get(0)).getScore(), // F1 score
				((SingleValuedMetric) metricsOut.get(1)).getScore(), // balanced accuracy
				"-" // OF
				);
			// ACP default
			metricsOut = finalEvaluator.evaluate(trainingSet, new ACPClassifier(new NegativeDistanceToHyperplaneNCM(svc.clone()),ACP_STRATEGY),clone(metrics,true));
			resultPrinter.printRecord("ACP", 
				svc.getName(), // Underlying model
				"default", // Parameters
				((SingleValuedMetric) metricsOut.get(0)).getScore(), // F1 score
				((SingleValuedMetric) metricsOut.get(1)).getScore(), // balanced accuracy
				((SingleValuedMetric) metricsOut.get(2)).getScore() // OF
				);
			// ACP SVC-tuned
			metricsOut = finalEvaluator.evaluate(trainingSet, new ACPClassifier(new NegativeDistanceToHyperplaneNCM(tunedSVC.clone()),ACP_STRATEGY),clone(metrics,true));
			resultPrinter.printRecord("ACP", 
				svc.getName(), // Underlying model
				"svc-tuned", // Parameters
				((SingleValuedMetric) metricsOut.get(0)).getScore(), // F1 score
				((SingleValuedMetric) metricsOut.get(1)).getScore(), // balanced accuracy
				((SingleValuedMetric) metricsOut.get(2)).getScore() // OF
				);
			// ACP CP-tuned
			ACPClassifier tunedACP = new ACPClassifier(new NegativeDistanceToHyperplaneNCM(svc.clone()),ACP_STRATEGY);
			tuner.search(trainingSet, tunedACP, grid);
			metricsOut = finalEvaluator.evaluate(trainingSet, tunedACP, clone(metrics,true));
			resultPrinter.printRecord("ACP", 
				svc.getName(), // Underlying model
				"CP-tuned", // Parameters
				((SingleValuedMetric) metricsOut.get(0)).getScore(), // F1 score
				((SingleValuedMetric) metricsOut.get(1)).getScore(), // balanced accuracy
				((SingleValuedMetric) metricsOut.get(2)).getScore() // OF
				);
			// ================================================================
			// TCP default
			metricsOut = finalEvaluator.evaluate(trainingSet, new TCPClassifier(new NegativeDistanceToHyperplaneNCM(svc.clone())),clone(metrics,true));
			resultPrinter.printRecord("TCP", 
				svc.getName(), // Underlying model
				"default", // Parameters
				((SingleValuedMetric) metricsOut.get(0)).getScore(), // F1 score
				((SingleValuedMetric) metricsOut.get(1)).getScore(), // balanced accuracy
				((SingleValuedMetric) metricsOut.get(2)).getScore() // OF
				);
			// TCP SVC-tuned
			metricsOut = finalEvaluator.evaluate(trainingSet, new TCPClassifier(new NegativeDistanceToHyperplaneNCM(tunedSVC.clone())),clone(metrics,true));
			resultPrinter.printRecord("TCP", 
				svc.getName(), // Underlying model
				"svc-tuned", // Parameters
				((SingleValuedMetric) metricsOut.get(0)).getScore(), // F1 score
				((SingleValuedMetric) metricsOut.get(1)).getScore(), // balanced accuracy
				((SingleValuedMetric) metricsOut.get(2)).getScore() // OF
				);
			// ACP CP-tuned
			TCPClassifier tunedTCP = new TCPClassifier(new NegativeDistanceToHyperplaneNCM(svc.clone()));
			tuner.search(trainingSet, tunedTCP, grid);
			metricsOut = finalEvaluator.evaluate(trainingSet, tunedTCP, clone(metrics,true));
			resultPrinter.printRecord("TCP", 
				svc.getName(), // Underlying model
				"CP-tuned", // Parameters
				((SingleValuedMetric) metricsOut.get(0)).getScore(), // F1 score
				((SingleValuedMetric) metricsOut.get(1)).getScore(), // balanced accuracy
				((SingleValuedMetric) metricsOut.get(2)).getScore() // OF
				);

		}


    }
	
    
}

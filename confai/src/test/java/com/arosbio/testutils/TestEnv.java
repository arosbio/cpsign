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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;

import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.JarDataSink;
import com.arosbio.io.JarDataSource;
import com.arosbio.ml.TrainingsetValidator;
import com.arosbio.ml.algorithms.linear.LogisticRegression;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.algorithms.svm.PlattScaledC_SVC;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.icp.ICPClassifier;
import com.arosbio.ml.cp.icp.ICPRegressor;
import com.arosbio.ml.cp.nonconf.classification.InverseProbabilityNCM;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.cp.nonconf.regression.AbsDiffNCM;
import com.arosbio.ml.cp.nonconf.regression.LogNormalizedNCM;
import com.arosbio.ml.cp.nonconf.regression.NormalizedNCM;
import com.arosbio.ml.cp.tcp.TCPClassifier;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.MetricAggregation;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.classification.LogLoss;
import com.arosbio.ml.metrics.cp.CalibrationPlot;
import com.arosbio.ml.metrics.cp.EfficiencyPlot;
import com.arosbio.ml.metrics.plots.Plot2D;
import com.arosbio.ml.metrics.plots.PlotMetric;
import com.arosbio.ml.metrics.plots.PlotMetricAggregation;
import com.arosbio.ml.sampling.FoldedSampling;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.vap.avap.AVAPClassifier;
import com.arosbio.tests.TestBase;
import com.arosbio.tests.utils.GzipEncryption;
import com.github.cliftonlabs.json_simple.JsonObject;

public abstract class TestEnv extends TestBase {

	
	@BeforeClass
	public static void initTestEnv() {
		TrainingsetValidator.setTestingEnv();
	}

	@AfterClass
	public static void exitTestEnv() {
		TrainingsetValidator.setProductionEnv();
	}

	public static JarDataSink getJarDataSink(File jarFile) throws FileNotFoundException, IOException{
		return new JarDataSink(new JarOutputStream(new FileOutputStream(jarFile)));
	}

	public static JarDataSource getJarDataSource(File jarFile) throws IOException {
		return new JarDataSource(new JarFile(jarFile));
	}

	public static double getAccuracy(List<Metric> metrics, double conf, Metric accuracyType) {
		for (Metric m : metrics) {
			if (m.getClass() == accuracyType.getClass()) {
				if (m instanceof PlotMetric) {
					Plot2D p = ((PlotMetric) m).buildPlot();
					if (p instanceof CalibrationPlot)
						return ((CalibrationPlot) p).getAccuracy(conf);
				} else if (m instanceof SingleValuedMetric) {
					return ((SingleValuedMetric) m).getScore();
				}
				throw new IllegalArgumentException("could not find correct accuracy metric");
			}
			else if (m instanceof MetricAggregation && (((MetricAggregation<?>)m).spawnNewMetricInstance().getClass() == accuracyType.getClass())){
				return ((MetricAggregation<?>) m).getScore();
			}

		}
		throw new IllegalArgumentException("could not find correct accuracy metric");
	}

	public static double getEfficiency(List<Metric> metrics, double conf, Metric effType) {
		for (Metric m : metrics) {
			if (m.getClass() == effType.getClass()) {
				if (m instanceof PlotMetric) {
					Plot2D p = ((PlotMetric) m).buildPlot();
					if (p instanceof EfficiencyPlot)
						return ((EfficiencyPlot) p).getEfficiency(conf);
					throw new IllegalArgumentException("Could not find the correct efficiency metric");
				} else if (m instanceof SingleValuedMetric){
					return ((SingleValuedMetric) m).getScore();
				} else {
					throw new IllegalArgumentException("Could not find the correct efficiency metric");
				}
			} else if (m instanceof MetricAggregation){
				Class<?> clz = ((MetricAggregation<?>)m).spawnNewMetricInstance().getClass();
				if (clz == effType.getClass()){
					return ((MetricAggregation<?>)m).getScore();
				}
			} else if (m instanceof PlotMetricAggregation){
				PlotMetric base = ((PlotMetricAggregation)m).spawnNewMetricInstance();
				Class<?> clz = base.getClass();
				if (clz == effType.getClass()){
					Plot2D p = ((PlotMetricAggregation) m).buildPlot();
					if (p instanceof EfficiencyPlot)
						return ((EfficiencyPlot) p).getEfficiency(conf);
					throw new IllegalArgumentException("Could not find the correct efficiency metric");
				}
			}
		}
		throw new IllegalArgumentException("Could not find the correct efficiency metric");
	}

	public static double getLogLoss(List<Metric> metrics) {
		for (Metric m: metrics) {
			if (m instanceof LogLoss)
				return ((LogLoss) m).getScore();
			else if (m instanceof MetricAggregation && ((MetricAggregation<?>)m).spawnNewMetricInstance() instanceof LogLoss){
				return ((MetricAggregation<?>)m).getScore();
			}
		}
		throw new IllegalArgumentException("No LogLoss found");
	}

	@SuppressWarnings("unchecked")
	protected static Object findObject(JsonObject json, Object key) {
		if (json.containsKey(key))
			return json.get(key);

		if (json instanceof Map)
			return findObject((Map<String,Object>)json, key);
		else if (json instanceof List)
			return findObject((List<Object>) json, key);

		return null;
	}

	@SuppressWarnings("unchecked")
	private static Object findObject(Map<String,Object> json, Object key) {
		if (json.containsKey(key))
			return json.get(key);
		for (Map.Entry<String, Object> entry: json.entrySet()) {
			if (entry.getValue() instanceof Map)
				return findObject((Map<String,Object>)entry.getValue(), key);
			else if (entry.getValue() instanceof List)
				return findObject((List<Object>) entry.getValue(), key);
		}
		return null;	
	}

	@SuppressWarnings("unchecked")
	private static Object findObject(List<Object> json, Object key) {
		for (Object entry: json) {
			if (entry instanceof Map)
				return findObject((Map<String,Object>)entry, key);
			else if (entry instanceof List)
				return findObject((List<Object>) entry, key);
		}
		return null;	
	}

	public static void assertContainsNumModels(String log, int numModels, String...extraTexts) {
		log = log.toLowerCase();
		String[]lines = log.split("\n");
		boolean containsNumModels = false;
		for (int i=0; i<lines.length;i++) {
			String line = lines[i];
			if (line.contains(""+numModels) && line.contains("load")) {
				containsNumModels = true;
				break;
			}
		}
		Assert.assertTrue(containsNumModels);
		for (String s: extraTexts) {
			Assert.assertTrue(log.contains(s.toLowerCase()));
		}
	}

	public static ACPClassifier getACPClassificationNegDist(boolean linearKernel, boolean randomSampling) {
		return new ACPClassifier(
				new ICPClassifier(
						new NegativeDistanceToHyperplaneNCM(
								(linearKernel? 
										new LinearSVC() :
											new C_SVC())
								)), 
				(randomSampling ? new RandomSampling(DEFAULT_NUM_MODELS, DEFAULT_CALIBRATION_RATIO):
					new FoldedSampling(DEFAULT_NUM_MODELS))
				);
	}

	public static ACPClassifier getACPClassificationProbabilityNCM(boolean randomSampling, boolean logReg) {
		return new ACPClassifier(new ICPClassifier(new InverseProbabilityNCM(logReg? new LogisticRegression() : new PlattScaledC_SVC())), // InverseProbabilityNCM(new ProbabilisticLibSvm(LIB_SVM_CLASS_PARAMS))),
				(randomSampling ? new RandomSampling(DEFAULT_NUM_MODELS, DEFAULT_CALIBRATION_RATIO):
					new FoldedSampling(DEFAULT_NUM_MODELS)));
	}

	public static ACPRegressor getACPRegressionAbsDiff(boolean linearKernel, boolean randomSampling) {
		return new ACPRegressor(new ICPRegressor(new AbsDiffNCM((linearKernel? 
				new LinearSVR() :
					new EpsilonSVR())
				)), 
				(randomSampling ? new RandomSampling(DEFAULT_NUM_MODELS, DEFAULT_CALIBRATION_RATIO):
					new FoldedSampling(DEFAULT_NUM_MODELS)));
	}

	public static ACPRegressor getACPRegressionNormalized(boolean linearKernel, boolean randomSampling) {
		return new ACPRegressor(new ICPRegressor(new NormalizedNCM((linearKernel? 
				new LinearSVR() :
					new EpsilonSVR())
				)), 
				(randomSampling ? new RandomSampling(DEFAULT_NUM_MODELS, DEFAULT_CALIBRATION_RATIO):
					new FoldedSampling(DEFAULT_NUM_MODELS)));
	}

	public static ACPRegressor getACPRegressionLogNormalized(boolean linearKernel, boolean randomSampling, double beta) {
		return new ACPRegressor(new ICPRegressor(
				new LogNormalizedNCM((linearKernel? 
						new LinearSVR() :
							new EpsilonSVR()),
						beta)
				), 
				(randomSampling ? new RandomSampling(DEFAULT_NUM_MODELS, DEFAULT_CALIBRATION_RATIO):
					new FoldedSampling(DEFAULT_NUM_MODELS)));
	}


	public static TCPClassifier getTCPClassification(boolean linearKernel) {
		return new TCPClassifier(new NegativeDistanceToHyperplaneNCM(
				linearKernel? 
						new LinearSVC() :
							new C_SVC())
				);
	}

	public static AVAPClassifier getAVAPClassification(boolean linearKernel, boolean randomSampling) {
		return new AVAPClassifier(linearKernel? new LinearSVC() : new C_SVC(), 
				(randomSampling ? new RandomSampling(DEFAULT_NUM_MODELS, DEFAULT_CALIBRATION_RATIO):
					new FoldedSampling(DEFAULT_NUM_MODELS)));
	}

	/**
	 * Get an initialized EncryptionSpecification object, with a randomly generated key 
	 */
	public static EncryptionSpecification getSpec(){
		try{
			EncryptionSpecification spec = new GzipEncryption();
			spec.init(spec.generateRandomKey(GzipEncryption.ALLOWED_KEY_LEN));
			return spec;
		} catch (Exception e){
			Assert.fail("Failed generating a random encryption spec: "+e.getMessage());
			return null;
		}
	}

	/**
	 * Generates and init's an EncryptionSpecification based on the key. The 
	 * key is not required to be "good" - but the resulting spec should be deterministic
	 * @param key
	 * @return
	 */
	public static EncryptionSpecification getSpec(String key){
		try {
			byte[] bytes = key.getBytes(StandardCharsets.UTF_16);
			if (bytes.length < GzipEncryption.ALLOWED_KEY_LEN){
				byte[] tmp = new byte[GzipEncryption.ALLOWED_KEY_LEN];
				// Copy the first bytes from the input
				System.arraycopy(bytes, 0, tmp, 0, bytes.length);
				// Fill the rest with 42
				Arrays.fill(tmp, bytes.length, tmp.length, (byte)42);
				bytes = tmp;
			}
			GzipEncryption spec = new GzipEncryption();
			spec.init(bytes);
			return spec;
		} catch (Exception e){
			Assert.fail("Could not init the GzipEncryption spec: " + e.getMessage());
			return null;
		}
	}


}

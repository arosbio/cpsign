/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.data.NamedLabels;
import com.arosbio.ml.algorithms.Classifier;
import com.arosbio.ml.algorithms.MLAlgorithm;
import com.arosbio.ml.algorithms.PseudoProbabilisticClassifier;
import com.arosbio.ml.algorithms.Regressor;
import com.arosbio.ml.algorithms.ScoringClassifier;
import com.arosbio.ml.cp.ConformalClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.metrics.classification.ClassifierMetric;
import com.arosbio.ml.metrics.classification.LabelDependent;
import com.arosbio.ml.metrics.classification.PointClassifierMetric;
import com.arosbio.ml.metrics.classification.ProbabilisticMetric;
import com.arosbio.ml.metrics.classification.ScoringClassifierMetric;
import com.arosbio.ml.metrics.cp.classification.CPClassificationMetric;
import com.arosbio.ml.metrics.cp.regression.CIWidthBasedMetric;
import com.arosbio.ml.metrics.cp.regression.CPRegressionMetric;
import com.arosbio.ml.metrics.cp.regression.CPRegressionMultiMetric;
import com.arosbio.ml.metrics.plots.PlotMetric;
import com.arosbio.ml.metrics.regression.PointPredictionMetric;
import com.arosbio.ml.metrics.vap.VAPMetric;
import com.arosbio.ml.vap.avap.AVAPClassifier;

public class MetricFactory {
	
	public static Iterator<Metric> getAllMetrics(){
		ServiceLoader<Metric> loader = ServiceLoader.load(Metric.class);
		return loader.iterator();
	}
	
	public static Metric fuzzyMatch(String name) throws IllegalArgumentException {
		return FuzzyServiceLoader.load(Metric.class, name).clone();
	}

	public static List<Metric> getMetrics(Predictor predictor, boolean multiclass) {

		if (predictor instanceof AVAPClassifier) {
			return getAVAPClassificationMetrics();
		} else if (predictor instanceof ConformalClassifier) {
			return getCPClassificationMetrics(multiclass);
		} else if (predictor instanceof ACPRegressor) {
			return getACPRegressionMetrics();
		} 
		
		throw new IllegalArgumentException("No metrics supported for predictor of type: " + predictor.getPredictorType());

	}

	/**
	 * Note: Venn-ABERS only does binary classification
	 * @return a list of metrics
	 */
	public static List<Metric> getAVAPClassificationMetrics(){
		List<Metric> metrics = new ArrayList<>();
		
		Iterator<Metric> iter = getAllMetrics();
		while (iter.hasNext()) {
			Metric m = iter.next();
			if (m instanceof ProbabilisticMetric || 
					m instanceof ScoringClassifierMetric ||
					m instanceof PointClassifierMetric || 
					m instanceof VAPMetric)
				metrics.add(m);
		}
		
		return metrics;
	}

	public static List<Metric> getCPClassificationMetrics(boolean multiclass){
		List<Metric> metrics = new ArrayList<>();
		
		Iterator<Metric> iter = getAllMetrics();
		while (iter.hasNext()) {
			Metric m = iter.next();
			
			if (m instanceof CPClassificationMetric) {
				// Skip if we have a multi-class problem but metric dosn't support multiclass
				if (multiclass && ! ((CPClassificationMetric) m).supportsMulticlass())
					continue;
				
				metrics.add(m);
			} else if (m instanceof PointClassifierMetric) {
				// Skip if we have a multi-class problem but metric dosn't support multiclass
				if (multiclass && ! ((PointClassifierMetric) m).supportsMulticlass())
					continue;
				metrics.add(m);
			} else if (m instanceof ScoringClassifierMetric){
				// Skip if we have a multi-class problem but metric dosn't support multiclass
				if (multiclass && ! ((ScoringClassifierMetric) m).supportsMulticlass())
					continue;
				metrics.add(m);
			}
				
		}
				
		return metrics;
	}

	public static List<Metric> getACPRegressionMetrics(){
		List<Metric> metrics = new ArrayList<>();
		Iterator<Metric> iter = getAllMetrics();
		while (iter.hasNext()) {
			Metric m = iter.next();
			if (m instanceof PointPredictionMetric ||
					m instanceof CPRegressionMetric || 
					m instanceof CPRegressionMultiMetric ||
					m instanceof CIWidthBasedMetric)
				metrics.add(m);
		}
		
		return metrics;
	}
	
	public static List<Metric> getMetrics(MLAlgorithm alg, boolean multiclass){
		if (alg instanceof Regressor) {
			return getRegressorMetrics();
		} else if (alg instanceof PseudoProbabilisticClassifier) {
			return getProbabilisticMetrics(multiclass);
		} else if (alg instanceof ScoringClassifier) {
			return getScoringClassifierMetrics(multiclass);
		} else if (alg instanceof Classifier) {
			return getClassifierMetrics(multiclass);
		}
		throw new RuntimeException("No metrics supported for algorithm of type " + alg.getName());
	}
	
	public static List<Metric> getRegressorMetrics(){
		List<Metric> metrics = new ArrayList<>();
		Iterator<Metric> iter = getAllMetrics();
		while (iter.hasNext()) {
			Metric m = iter.next();
			if (m instanceof PointPredictionMetric)
				metrics.add(m);
		}
		
		return metrics;
	}
	
	public static List<Metric> getProbabilisticMetrics(boolean multiclass){
		List<Metric> metrics = new ArrayList<>();
		Iterator<Metric> iter = getAllMetrics();
		while (iter.hasNext()) {
			Metric m = iter.next();
			if (m instanceof PointClassifierMetric || m instanceof ScoringClassifierMetric || m instanceof ProbabilisticMetric) {
				if (multiclass && !((ClassifierMetric)m).supportsMulticlass())
					continue;
				metrics.add(m);
			}
		}
		
		return metrics;
}
	
	public static List<Metric> getScoringClassifierMetrics(boolean multiclass){
		List<Metric> metrics = new ArrayList<>();
		Iterator<Metric> iter = getAllMetrics();
		while (iter.hasNext()) {
			Metric m = iter.next();
			if (m instanceof PointClassifierMetric || m instanceof ScoringClassifierMetric) {
				if (multiclass && !((ClassifierMetric)m).supportsMulticlass())
					continue;
				metrics.add(m);
			}
		}
		
		return metrics;
	}
	
	public static List<Metric> getClassifierMetrics(boolean multiclass){
		List<Metric> metrics = new ArrayList<>();
		Iterator<Metric> iter = getAllMetrics();
		while (iter.hasNext()) {
			Metric m = iter.next();
			if (m instanceof PointClassifierMetric) {
				if (multiclass && ! ((PointClassifierMetric) m).supportsMulticlass()) {
					continue;
				}
				metrics.add(m);
			}
		}
		
		return metrics;
	}
	
	public static void setEvaluationPoints(List<? extends Metric> metrics, List<Double> points) {
		for (Metric m : metrics) {
			if (m instanceof PlotMetric) {
				((PlotMetric) m).setEvaluationPoints(points);
			}
		}
	}
	
	@SafeVarargs
	public static <M extends Metric> void setClassificationLabels(NamedLabels labels, M... metrics) {
		int posLabel = Collections.max(labels.getLabels().keySet());
		for (M m : metrics) {
			if (m instanceof LabelsMixin)
				((LabelsMixin) m).setLabels(labels);
			else if (m instanceof LabelDependent)
				((LabelDependent) m).setPositiveLabel(posLabel);
		}
	}
	
	public static void setClassificationLabels(NamedLabels labels, Collection<? extends Metric> metrics) {
		int posLabel = Collections.max(labels.getLabels().keySet());
		for (Metric m : metrics) {
			if (m instanceof LabelsMixin)
				((LabelsMixin) m).setLabels(labels);
			else if (m instanceof LabelDependent)
				((LabelDependent) m).setPositiveLabel(posLabel);
		}
	}
	
	public static List<SingleValuedMetric> filterToSingleValuedMetrics(Collection<? extends Metric> metrics){
		List<SingleValuedMetric> res = new ArrayList<>(metrics.size());
		for (Metric m : metrics) {
			if (m instanceof SingleValuedMetric)
				res.add((SingleValuedMetric) m);
		}
		return res;
	}
}

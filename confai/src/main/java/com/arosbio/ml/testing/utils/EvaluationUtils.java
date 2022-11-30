/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.testing.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.arosbio.data.DataRecord;
import com.arosbio.ml.ClassificationUtils;
import com.arosbio.ml.cp.CPRegressionPrediction;
import com.arosbio.ml.cp.CPRegressionPrediction.PredictedInterval;
import com.arosbio.ml.cp.ConformalClassifier;
import com.arosbio.ml.cp.ConformalRegressor;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.classification.PointClassifierMetric;
import com.arosbio.ml.metrics.classification.ProbabilisticMetric;
import com.arosbio.ml.metrics.classification.ScoringClassifierMetric;
import com.arosbio.ml.metrics.cp.classification.CPClassificationMetric;
import com.arosbio.ml.metrics.cp.regression.CIWidthBasedMetric;
import com.arosbio.ml.metrics.cp.regression.CPRegressionMetric;
import com.arosbio.ml.metrics.cp.regression.CPRegressionMultiMetric;
import com.arosbio.ml.metrics.regression.PointPredictionMetric;
import com.arosbio.ml.metrics.vap.VAPMetric;
import com.arosbio.ml.testing.TestRunner.UnsupportedPredictorException;
import com.arosbio.ml.vap.avap.AVAPClassifier;
import com.arosbio.ml.vap.avap.CVAPPrediction;
import com.google.common.collect.Range;

/**
 * Internal utility class for trained models and algorithms, for connecting the algorithms and models to their
 * matching metric types. Single-source updates to minimize updates across many classes.
 */
public class EvaluationUtils {

    public static boolean supports(Predictor predictor) {
        return predictor instanceof ConformalClassifier || predictor instanceof ConformalRegressor || predictor instanceof AVAPClassifier;
	}

    public static void evaluateExample(Predictor predictor, DataRecord ex, Metric... metrics){
        evaluateExample(predictor, ex, Arrays.asList(metrics));
    }
    public static void evaluateExample(Predictor predictor, DataRecord ex, Iterable<? extends Metric> metrics){
        if (predictor instanceof ConformalClassifier){
            evaluateExample((ConformalClassifier) predictor, ex, metrics);
        } else if (predictor instanceof ConformalRegressor){
            evaluateExample((ConformalRegressor)predictor, ex, metrics);
        } else if (predictor instanceof AVAPClassifier){
            evaluateExample((AVAPClassifier)predictor, ex, metrics);
        } else {
            throw new UnsupportedPredictorException("Preditor type "+predictor.getClass().getSimpleName()+ " not supported");
        }
    }

    public static List<Metric> validateMetrics(Predictor predictor, Metric... metrics){
        return validateMetrics(predictor, Arrays.asList(metrics));
    }
    public static List<Metric> validateMetrics(Predictor predictor, Iterable<? extends Metric> metrics){
        if (predictor instanceof ConformalClassifier){
            return validateMetrics((ConformalClassifier)predictor, metrics);
        } else if (predictor instanceof ConformalRegressor){
            return validateMetrics((ConformalRegressor) predictor, metrics);
        } else if (predictor instanceof AVAPClassifier){
            return validateMetrics((AVAPClassifier) predictor, metrics);
        } else {
            throw new UnsupportedPredictorException("Preditor type "+predictor.getClass().getSimpleName()+ " not supported");
        }
    }

    public static List<Metric> validateMetrics(ConformalRegressor predictor, Metric... metrics){
        return validateMetrics(predictor, Arrays.asList(metrics));
    }
    public static List<Metric> validateMetrics(ConformalRegressor predictor, Iterable<? extends Metric> metrics){
        List<Metric> unsupported = new ArrayList<>();
        for (Metric m : metrics){
            if (m instanceof PointPredictionMetric 
                || m instanceof CPRegressionMetric 
                || m instanceof CPRegressionMultiMetric 
                || m instanceof CIWidthBasedMetric){
                // OK
            } else {
                unsupported.add(m);
            }

        }
        return unsupported;
    }

    public static void evaluateExample(ConformalRegressor predictor, DataRecord example, Iterable<? extends Metric> metrics) 
			throws IllegalStateException {
        // find which confidences to use
		Set<Double> confSet = new HashSet<>();
		Set<Double> intervalWidths = new HashSet<>();
		for (Metric builder: metrics) {
			if (builder instanceof CPRegressionMetric) {
				confSet.add(((CPRegressionMetric) builder).getConfidence());
			} else if (builder instanceof CPRegressionMultiMetric) {
				confSet.addAll(((CPRegressionMultiMetric) builder).getEvaluationPoints());
			} else if (builder instanceof CIWidthBasedMetric) {
				intervalWidths.add(((CIWidthBasedMetric) builder).getCIWidth());
			}
		}
        evaluateExample(predictor, example, metrics, confSet, intervalWidths);
    }

    public static void evaluateExample(ConformalRegressor predictor, DataRecord example, Iterable<? extends Metric> metrics, Set<Double> confs, Set<Double> widths) 
	throws IllegalStateException {

		CPRegressionPrediction prediction = predictor.predict(example.getFeatures(), confs);
		CPRegressionPrediction widthPrediction = null;
		if (!widths.isEmpty()) {
			widthPrediction = predictor.predictConfidence(example.getFeatures(), widths);
		}

		for (Metric m : metrics) {
			if (m instanceof PointPredictionMetric) {
				// Simple metric only uses the point-prediction
				((PointPredictionMetric)m).addPrediction(example.getLabel(), prediction.getY_hat());
			} else if (m instanceof CPRegressionMetric) {
				// Find the confidence used for each given metric
				((CPRegressionMetric) m).addPrediction(example.getLabel(), 
						prediction.getInterval(((CPRegressionMetric) m).getConfidence()).getInterval());
			} else if (m instanceof CPRegressionMultiMetric) {
				List<Double> currConfs = ((CPRegressionMultiMetric) m).getEvaluationPoints();
				Map<Double,Range<Double>> predIntervals = new HashMap<>();
				for (double c : currConfs) {
					predIntervals.put(c, prediction.getInterval(c).getInterval());
				}
				((CPRegressionMultiMetric) m).addPrediction(example.getLabel(), predIntervals);
			} else if (m instanceof CIWidthBasedMetric && widthPrediction!=null) {
				PredictedInterval interval = widthPrediction.getWidthToConfidenceBasedIntervals().get(((CIWidthBasedMetric) m).getCIWidth());
				((CIWidthBasedMetric) m).addPrediction(example.getLabel(), interval.getInterval(), interval.getConfidence());
			} else {
				throw new IllegalArgumentException("Metric of non-supported class for Conformal Regression: " + m.getClass());
			}

		}
	}
    public static List<Metric> validateMetrics(ConformalClassifier predictor, Metric... metrics){
        return validateMetrics(predictor, Arrays.asList(metrics));
    }
    public static List<Metric> validateMetrics(ConformalClassifier predictor, Iterable<? extends Metric> metrics){
        List<Metric> unsupported = new ArrayList<>();
        for (Metric m : metrics){
            if (m instanceof CPClassificationMetric 
                || m instanceof ScoringClassifierMetric
                || m instanceof PointClassifierMetric){
                // OK
            } else {
                unsupported.add(m);
            }

        }
        return unsupported;
    }
    public static void evaluateExample(ConformalClassifier predictor, DataRecord example, Iterable<? extends Metric> metrics) 
			throws IllegalStateException {
		
		Map<Integer, Double> pvals = predictor.predict(example.getFeatures());
		int obsClass = (int)example.getLabel();
		int predClass = ClassificationUtils.getPredictedClass(pvals);
		for (Metric m : metrics) {
			if (m instanceof CPClassificationMetric) {
				((CPClassificationMetric) m).addPrediction(obsClass, pvals);
			} else if (m instanceof ScoringClassifierMetric){
				((ScoringClassifierMetric) m).addPrediction(obsClass,pvals);
			} else if (m instanceof PointClassifierMetric) {
				((PointClassifierMetric) m).addPrediction(obsClass, predClass);
			} else {
				throw new IllegalArgumentException("Metric of non-supported class for Conformal Classification: " + m.getName());
			}
		}
	}
    public static List<Metric> validateMetrics(AVAPClassifier predictor, Metric... metrics){
        return validateMetrics(predictor, Arrays.asList(metrics));
    }
    public static List<Metric> validateMetrics(AVAPClassifier predictor, Iterable<? extends Metric> metrics){
        List<Metric> unsupported = new ArrayList<>();
        for (Metric m : metrics){
            if (m instanceof ProbabilisticMetric
                || m instanceof VAPMetric 
                || m instanceof ScoringClassifierMetric
                || m instanceof PointClassifierMetric ){
                // OK
            } else {
                unsupported.add(m);
            }

        }
        return unsupported;
    }

    public static void evaluateExample(AVAPClassifier predictor, DataRecord example, Iterable<? extends Metric> metrics) 
    throws IllegalStateException {

        CVAPPrediction<Integer> res = predictor.predict(example.getFeatures());

        int observedClass = (int) example.getLabel();
        int predictedClass = ClassificationUtils.getPredictedClass(res.getProbabilities());

        for (Metric m : metrics) {
            if (m instanceof ProbabilisticMetric) {
                ((ProbabilisticMetric) m).addPrediction(observedClass, res.getProbabilities());
            } else if (m instanceof VAPMetric) {
                ((VAPMetric) m).addPrediction(observedClass, res.getProbabilities(), res.getMeanP0P1Width(), res.getMedianP0P1Width());
            } else if (m instanceof ScoringClassifierMetric) {
                ((ScoringClassifierMetric) m).addPrediction(observedClass, res.getProbabilities());
            } else if (m instanceof PointClassifierMetric) {
                ((PointClassifierMetric) m).addPrediction(observedClass, predictedClass);
            } else {
                throw new IllegalArgumentException("Metric of non-supported class for VAP Classification: " + m.getName());
            }
        } 
    }
    
}

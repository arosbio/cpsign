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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.StringUtils;
import com.arosbio.commons.TypeUtils;
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
import com.arosbio.ml.metrics.cp.classification.CPClassifierMetric;
import com.arosbio.ml.metrics.cp.regression.CIWidthBasedMetric;
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

    private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(EvaluationUtils.class);

    public static boolean supports(Predictor predictor) {
        return predictor instanceof ConformalClassifier || predictor instanceof ConformalRegressor || predictor instanceof AVAPClassifier;
	}

    public static void evaluate(Predictor predictor, List<DataRecord> testSet, Metric... metrics)
	throws IllegalArgumentException, UnsupportedPredictorException {
        evaluate(predictor, testSet, Arrays.asList(metrics));
    }

    public static <M extends Metric> void evaluate(Predictor predictor, List<DataRecord> testSet, Iterable<M> metrics)
	throws IllegalArgumentException, UnsupportedPredictorException {
        if (!EvaluationUtils.supports(predictor)){
            throw new IllegalArgumentException("Predictor of type " + predictor.getClass().getSimpleName() + " not supported");
        }
        if (!predictor.isTrained()){
            throw new IllegalStateException("Predictor not trained");
        }

        List<Metric> unsupported = EvaluationUtils.getOffendingMetrics(predictor, metrics);
        if (unsupported!=null && !unsupported.isEmpty()){
            throw new IllegalArgumentException("Unsupported metrics for predictor of type " + predictor.getClass().getSimpleName() + ": " + StringUtils.join(", ", unsupported));
        }

        for (DataRecord r : testSet){
            EvaluationUtils.evaluateExample(predictor, r, metrics);
        }
        
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
            throw new UnsupportedPredictorException("Predictor type "+predictor.getClass().getSimpleName()+ " not supported");
        }
    }

    public static void evaluateExample(ConformalRegressor predictor, DataRecord example, Iterable<? extends Metric> metrics) 
    throws IllegalStateException {
        // find which confidences to use
        Set<Double> confSet = new HashSet<>();
        Set<Double> intervalWidths = new HashSet<>();
        for (Metric builder: metrics) {
            if (builder instanceof CPRegressionMultiMetric) {
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
    public static void evaluateExample(ConformalClassifier predictor, DataRecord example, Iterable<? extends Metric> metrics) 
			throws IllegalStateException {
		
		Map<Integer, Double> pvals = predictor.predict(example.getFeatures());
		int obsClass = (int)example.getLabel();
		int predClass = ClassificationUtils.getPredictedClass(pvals);
		for (Metric m : metrics) {
			if (m instanceof CPClassifierMetric) {
				((CPClassifierMetric) m).addPrediction(obsClass, pvals);
			} else if (m instanceof ScoringClassifierMetric){
				((ScoringClassifierMetric) m).addPrediction(obsClass,pvals);
			} else if (m instanceof PointClassifierMetric) {
				((PointClassifierMetric) m).addPrediction(obsClass, predClass);
			} else {
				throw new IllegalArgumentException("Metric of non-supported class for Conformal Classification: " + m.getName());
			}
		}
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

    // ========================================================
    // Evaluation if metrics supported by the predictor itself
    // ========================================================

    private static final Class<?>[] VAP_METRICS = new Class[]{ProbabilisticMetric.class,
        VAPMetric.class, ScoringClassifierMetric.class,PointClassifierMetric.class};
    private static final Class<?>[] CP_CLF_METRICS = new Class[]{CPClassifierMetric.class,
        ScoringClassifierMetric.class,PointClassifierMetric.class};
    private static final Class<?>[] CP_REG_METRICS = new Class[]{PointPredictionMetric.class,
        CPRegressionMultiMetric.class, CIWidthBasedMetric.class};
    
    public static Class<?>[] getSupportedMetricClasses(Class<?> predictorCls) throws UnsupportedPredictorException {
        if (predictorCls.isAssignableFrom(ConformalClassifier.class)){
            return CP_CLF_METRICS;
        } else if (predictorCls.isAssignableFrom(ConformalRegressor.class)){
            return CP_REG_METRICS;
        } else if (predictorCls.isAssignableFrom(AVAPClassifier.class)){
            return VAP_METRICS;
        } else {
            throw new UnsupportedPredictorException("Predictor type "+predictorCls.getSimpleName()+ " not supported");
        }
    }
    public static Class<?>[] getSupportedMetricClasses(Predictor predictor) throws UnsupportedPredictorException {
        if (predictor instanceof ConformalClassifier){
            return CP_CLF_METRICS;
        } else if (predictor instanceof ConformalRegressor){
            return CP_REG_METRICS;
        } else if (predictor instanceof AVAPClassifier){
            return VAP_METRICS;
        } else {
            throw new UnsupportedPredictorException("Predictor type "+predictor.getClass().getSimpleName()+ " not supported");
        }
    }

    public static List<Metric> getOffendingMetrics(Predictor predictor, Metric... metrics) throws UnsupportedPredictorException {
        return getOffendingMetrics(predictor, Arrays.asList(metrics));
    }

    public static List<Metric> getOffendingMetrics(Predictor predictor, Iterable<? extends Metric> metrics) throws UnsupportedPredictorException {
        List<Metric> unsupported = new ArrayList<>();
        // determine the list of supported metrics 
        Class<?>[] allowedMetrics = getSupportedMetricClasses(predictor);
        for (Metric m : metrics){
            if (!TypeUtils.objectIsOfType(m, allowedMetrics)){
                unsupported.add(m);
            }
        }
        return unsupported;
    }

    public static boolean validateMetrics(Predictor predictor, Metric... metrics) throws UnsupportedPredictorException {
        return validateMetrics(predictor, Arrays.asList(metrics));
    }
    public static boolean validateMetrics(Predictor predictor, Iterable<? extends Metric> metrics) throws UnsupportedPredictorException {
        List<Metric> unsupported = getOffendingMetrics(predictor, metrics);
        if (unsupported.isEmpty()){
            return true;
        }
        // If not empty - we have unsupported metrics
        LOGGER.debug("Unsupported metrics: {}", unsupported);
        return false;
        
    }
    
}

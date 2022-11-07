/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.testing;

import java.util.Arrays;
import java.util.List;

import com.arosbio.commons.Experimental;
import com.arosbio.commons.StringUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.testing.TestRunner.UnsupportedPredictorException;
import com.arosbio.ml.testing.utils.EvaluationUtils;

@Experimental
public class ModelEvaluator {

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

        List<Metric> unsupported = EvaluationUtils.validateMetrics(predictor, metrics);
        if (unsupported!=null && !unsupported.isEmpty()){
            throw new IllegalArgumentException("Unsupported metrics for predictor of type " + predictor.getClass().getSimpleName() + ": " + StringUtils.join(", ", unsupported));
        }

        for (DataRecord r : testSet){
            EvaluationUtils.evaluateExample(predictor, r, metrics);
        }

        // Do validation stuff
        // if (predictor instanceof ConformalClassifier){
        //     return evaluateClf((ConformalClassifier) predictor, testSet, metrics);
        // }

        
    }

    // private <M> List<M> evaluateClf(ConformalClassifier predictor, Iterable<DataRecord> testSet, List<M> metrics){
    //     for (DataRecord r : testSet){
    //         TestUtils.eval
            

    //     }

    //     return metrics;
    // }

}

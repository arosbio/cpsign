/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp;

import java.util.Collection;
import java.util.List;

import com.arosbio.data.FeatureVector;
import com.arosbio.data.SparseFeature;
import com.arosbio.ml.interfaces.RegressionPredictor;

public interface ConformalRegressor extends ConformalPredictor, RegressionPredictor {

	/**
	 * Predict a new example with a list of specific confidences. The
	 * result is a Map with confidence as key and a Pair of min/max-values for
	 * that confidence as value.
	 * 
	 * @param example A new feature to predict
	 * @param confidences A list of specified confidences
	 * @return A {@link com.arosbio.ml.cp.CPRegressionPrediction} containing the result for each confidence given
	 * @throws IllegalStateException No trained models
	 */
	public abstract CPRegressionPrediction predict(FeatureVector example, Collection<Double> confidences)
			throws IllegalStateException;
	
	/**
	 * Predict a new example for a distance, returning the confidence.
	 * 
	 * @param example A new feature to predict
	 * @param widths A {@link List} of prediction interval widths to use
	 * @return The confidence for interval defined by the distance to prediction
	 * @throws IllegalStateException No trained models 
	 */
	public CPRegressionPrediction predictConfidence(FeatureVector example, Collection<Double> widths)
			throws IllegalStateException;
	
	// Override to get the correct return-class
	public List<SparseFeature> calculateGradient(FeatureVector example) 
			throws IllegalStateException;
	
	public List<SparseFeature> calculateGradient(FeatureVector example, double stepsize) 
			throws IllegalStateException;
	
}

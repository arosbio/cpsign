/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.interfaces;

import java.util.List;

import com.arosbio.data.FeatureVector;
import com.arosbio.data.SparseFeature;

public interface RegressionPredictor {
	
	/**
	 * Will calculate the gradient of the model for this given example, to 
	 * find which feature was the most important for the outcome.
	 * @param instance An example to predict
	 * @return The gradient for this instance
	 * @throws IllegalStateException If model(s) are not trained yet
	 */
	public List<SparseFeature> calculateGradient(FeatureVector instance) 
			throws IllegalStateException;
	
	/**
	 * Will calculate the gradient of the model for this given example, to 
	 * find which feature was the most important for the outcome.
	 * @param instance An example to predict
	 * @param stepsize use specific stepsize
	 * @return The gradient for this instance
	 * @throws IllegalStateException If model(s) are not trained yet
	 */
	public List<SparseFeature> calculateGradient(FeatureVector instance, double stepsize) 
			throws IllegalStateException;

}

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
import java.util.Set;

import com.arosbio.data.FeatureVector;
import com.arosbio.data.SparseFeature;

public interface ClassificationPredictor {

	/**
	 * Get the class labels used
	 * @return a set of labels, empty if not trained yet
	 */
	public Set<Integer> getLabels();
	
	/**
	 * Get the number of classes used for this classification predictor
	 * @return The number of classes used, or -1 if not trained yet
	 */
	public int getNumClasses();
	
	/**
	 * Will calculate the gradient of the model for this given example, to 
	 * find which feature was the most important for the outcome.
	 * @param instance An example to predict
	 * @param label The label to consider during evaluation
	 * @return The gradient for this test instance
	 * @throws IllegalStateException If model(s) are not trained yet
	 * @throws IllegalArgumentException If the <code>label</code> parameter is not correct
	 */
	public List<SparseFeature> calculateGradient(FeatureVector instance, int label) 
			throws IllegalArgumentException, IllegalStateException;
	
	/**
	 * find which feature was the most important for the outcome.
	 * Will calculate the gradient of the model for this given example, to 
	 * @param instance An example to predict
	 * @param stepsize use specific stepsize
	 * @param label The label to consider during evaluation
	 * @return The gradient for this feature
	 * @throws IllegalStateException If model(s) are not trained yet
	 * @throws IllegalArgumentException If the <code>label</code> parameter is not correct
	 */
	public List<SparseFeature> calculateGradient(FeatureVector instance, double stepsize, int label) 
			throws IllegalStateException, IllegalArgumentException;
}

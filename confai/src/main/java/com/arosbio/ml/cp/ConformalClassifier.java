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

import java.util.List;
import java.util.Map;

import com.arosbio.data.FeatureVector;
import com.arosbio.data.SparseFeature;
import com.arosbio.ml.interfaces.ClassificationPredictor;

public interface ConformalClassifier extends ConformalPredictor, ClassificationPredictor {

	/**
	 * This method returns the p-values (used in Conformal Prediction) for
	 * the classes, given the previous training samples and the new
	 * example to predict. 
	 * @param example The new example to predict
	 * @return A Map of p-values for each class 
	 * @throws IllegalStateException Model not trained yet
	 */
	public Map<Integer, Double> predict(FeatureVector example) 
			throws IllegalStateException;
	
	// Override just to give the correct return-class
	public List<SparseFeature> calculateGradient(FeatureVector example, int label) 
			throws IllegalStateException;
	
	public List<SparseFeature> calculateGradient(FeatureVector example, double stepsize, int label) 
			throws IllegalStateException;
	
}

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

import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.mixins.HasProperties;
import com.arosbio.commons.mixins.RequireRNGSeed;
import com.arosbio.commons.mixins.ResourceAllocator;
import com.arosbio.data.Dataset;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.SparseFeature;
import com.arosbio.io.Saveable;
import com.arosbio.ml.io.ModelInfo;
import com.arosbio.ml.metrics.Metric;

public interface Predictor extends RequireRNGSeed, Saveable, ResourceAllocator, HasProperties, Configurable {
	
	public static final String CONFIG_SAMPLING_STRATEGY_PARAM_NAME = "samplingStrategy";
	
	public Metric getDefaultOptimizationMetric();

	public ModelInfo getModelInfo();

	public void setModelInfo(ModelInfo info);
	/**
	 * Checks if the underlying model is trained or not
	 * @return <code>true</code> if the underlying model is fully trained, <code>false</code> otherwise
	 */
	public boolean isTrained();
	
	/**
	 * Returns the number of examples used for training the model. Will thus return <code>0</code> if the
	 * model has not been trained
	 * @return Number of training examples used, or <code>0</code> if model has not been trained
	 */
	public int getNumObservationsUsed();

	/**
	 * Get the type of predictor as a String
	 * @return the type
	 */
	public String getPredictorType();
	
	/**
	 * Train a prediction model, given a dataset 
	 * @param problem The training dataset to be used
	 * @throws IllegalArgumentException If any settings are faulty or the {@link com.arosbio.data.Dataset Dataset} is empty or <code>null</code>
	 */
	public void train(Dataset problem) 
			throws IllegalArgumentException;
	
	/**
	 * Will calculate the gradient of the model for this given example, to 
	 * find which feature was the most important for the outcome.
	 * @param example An example to predict
	 * @return The gradient for this feature
	 * @throws IllegalStateException If models are not trained yet
	 */
	public List<SparseFeature> calculateGradient(FeatureVector example) 
			throws IllegalStateException;
	
	/**
	 * Will calculate the gradient of the model for this given example, to 
	 * find which feature was the most important for the outcome.
	 * @param example An example to predict
	 * @param stepsize use specific stepsize
	 * @return The gradient for this feature
	 * @throws IllegalStateException If models are not trained yet
	 */
	public List<SparseFeature> calculateGradient(FeatureVector example, double stepsize) 
			throws IllegalStateException;
	
	public Predictor clone();
	
}

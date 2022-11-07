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

import com.arosbio.data.Dataset;
import com.arosbio.ml.sampling.SamplingStrategy;

/**
 * An <code>AggregatedPredictor</code> uses several individual predictors and aggregate their predictions 
 * to a better one. Examples are Aggregated Conformal Predictors and Cross Venn-ABERS Predictors. 
 * The <code>AggregatedPredictor</code> uses a {@link com.arosbio.ml.sampling.SamplingStrategy SamplingStrategy}
 * that decides how sampling of the training data should be performed to each of the individual predictors.  
 *  
 * @author staffan
 *
 */
public interface AggregatedPredictor extends Predictor {

	/**
	 * Checks if at least one of the aggregated predictors has been trained (potentially all the the predictors can be trained).
	 * @return <code>true</code> if at least one of underlying predictors are trained (but can also be fully trained), <code>false</code> otherwise
	 */
	public boolean isPartiallyTrained();
	
	/**
	 * Getter for the {@link com.arosbio.ml.sampling.SamplingStrategy SamplingStrategy} used for the aggregation
	 * of the models
	 * @return the {@link com.arosbio.ml.sampling.SamplingStrategy SamplingStrategy} used
	 */
	public SamplingStrategy getStrategy();
	
	/**
	 * Get the number of trained predictors, which might be <code>numTrainedPredictors &ne; SamplingStrategy.nrModels</code>
	 * @return the number of trained predictors 
	 */
	public int getNumTrainedPredictors();
	
	/**
	 * Train only a specific predictor out of potentially multiple aggregated predictors
	 * @param problem The {@link com.arosbio.data.Dataset Dataset} that should be trained
	 * @param index the index to train, counting starts at 0 [0,total number of models)
	 * @throws IllegalArgumentException If any settings are faulty or the {@link com.arosbio.data.Dataset Dataset} is empty or <code>null</code>
	 */
	public void train(Dataset problem, int index) throws IllegalArgumentException;

}

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

import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.mixins.HasProperties;
import com.arosbio.commons.mixins.RequireRNGSeed;
import com.arosbio.commons.mixins.ResourceAllocator;
import com.arosbio.io.Saveable;
import com.arosbio.ml.sampling.TrainSplit;

public interface PartialPredictor extends RequireRNGSeed, Saveable, ResourceAllocator, Configurable, HasProperties {

	/**
	 * Checks if the underlying model is completely trained or not
	 * @return <code>true</code> if the underlying model is trained, <code>false</code> otherwise
	 */
	public boolean isTrained();
	
	/**
	 * Returns the number of examples used for training the model. Will thus return <code>0</code> if the
	 * model has not been trained
	 * @return Number of training examples used, or <code>0</code> if model has not been trained
	 */
	public int getNumObservationsUsed();
	
	/**
	 * Returns a shallow copy of the current {@link PartialPredictor}, with the same settings but empty (no models are copied)
	 * @return A shallow copy of the {@link PartialPredictor} 
	 */
	public PartialPredictor clone();

	public void train(TrainSplit trainingset) 
			throws IllegalArgumentException;
	
}

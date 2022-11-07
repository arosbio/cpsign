/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.algorithms;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.mixins.Described;
import com.arosbio.commons.mixins.HasID;
import com.arosbio.commons.mixins.HasProperties;
import com.arosbio.commons.mixins.Named;
import com.arosbio.commons.mixins.RequireRNGSeed;
import com.arosbio.data.DataRecord;

/**
 * Common interface for scoring algorithms. Note that for serialization of the models
 * they need to supply the mappings {@link #ML_ID_PARAM_KEY} and/or {@link #ML_NAME_PARAM_KEY} 
 * from the {@link #getProperties()} method. Which is used at loading time to instantiate the 
 * appropriate class using the ServiceLoader and finally loaded using the 
 * {@link #loadFromStream(InputStream)} of the correct instance.
 * 
 * @author staffan
 *
 */
public interface MLAlgorithm extends Described, Named, HasID, Cloneable, Configurable, RequireRNGSeed, HasProperties {

	public static final String ML_ID_PARAM_KEY = "mlImplementation";
	public static final String ML_NAME_PARAM_KEY = "mlImplementationName";

	/**
	 * Train the model using some training records
	 * @param trainingset training records
	 * @throws IllegalStateException If algorithm parameters/settings are faulty, or the model failed to be trained
	 * @throws IllegalArgumentException If training data is bad in some way
	 */
	public void train(List<DataRecord> trainingset) throws IllegalStateException, IllegalArgumentException;
	/**
	 * Train the model using a batch of training records, alias for {@link #train(List)}
	 * @param trainingset a batch of records
	 * @throws IllegalStateException If algorithm parameters/settings are faulty, or the model failed to be trained
	 * @throws IllegalArgumentException If training data is bad in some way
	 */
	public void fit(List<DataRecord> trainingset) throws IllegalStateException, IllegalArgumentException;
	
	public void saveToStream(OutputStream ostream) throws IOException, IllegalStateException;
	public void loadFromStream(InputStream istream) throws IOException;
	
	/**
	 * Create a clone with the same parameters - but with no actual 'model' copied.
	 * @return a new {@link MLAlgorithm} with the same parameters
	 */
	public MLAlgorithm clone();
	
	/**
	 * Check if the model has been trained/fitted from training data.
	 * @return {@code true} if trained/fitted, {@code false} otherwise
	 */
	public boolean isFitted();
	
}

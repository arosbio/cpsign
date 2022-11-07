/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp.nonconf;

import java.util.List;
import java.util.Map;

import com.arosbio.commons.Version;
import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.mixins.Described;
import com.arosbio.commons.mixins.HasID;
import com.arosbio.commons.mixins.Named;
import com.arosbio.data.DataRecord;
import com.arosbio.io.Saveable;
import com.arosbio.ml.algorithms.MLAlgorithm;

/**
 * Interface that should be implemented for all Non-Conformity Measures (NCM). NCMs are loaded and located using the
 * {@link java.util.ServiceLoader} and thus custom NCMs must follow the requirements needed by that class. 
 * 
 * @author staffan
 */
public interface NCM extends Cloneable, Saveable, Configurable, Described, Named, HasID {
	
    /**
	 * Check if the NCM is ready, has trained underlying models or what it requires
	 * @return {@code true} if the NCM is ready to compute NCS, {@code false} otherwise
	 */
	public boolean isFitted();
	
	/**
	 * The version of the NCM - to verify in case updates are made on the NCM
	 * @return The {@link com.arosbio.commons.Version Version} for the NCM
	 */
	public Version getVersion();

	/**
	 * Getter for the underlying scoring model
	 * @return the {@link MLAlgorithm} in use
	 */
	public MLAlgorithm getModel();
	
	/**
	 * Getter for properties of the NCM
	 * @return A Map with properties for the istance
	 */
	public Map<String, Object> getProperties();
	
	/**
	 * Train the nonconformity measure (likely an underlying scoring machine learning model)
	 * @param data A List of data records
	 * @throws IllegalArgumentException If input data not fulfilling all requirements
	 */
	public void trainNCM(List<DataRecord> data) throws IllegalArgumentException;
	
	/**
	 * Get a shallow clone of the current instance, i.e. no underlying models fitted
	 * or any other data
	 * @return A shallow clone of the current instance
	 */
	public NCM clone();
	
}

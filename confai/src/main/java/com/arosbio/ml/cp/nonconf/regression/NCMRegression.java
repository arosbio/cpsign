/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp.nonconf.regression;

import java.util.Arrays;
import java.util.List;

import com.arosbio.data.DataRecord;
import com.arosbio.data.FeatureVector;
import com.arosbio.ml.algorithms.MLAlgorithm;
import com.arosbio.ml.algorithms.Regressor;
import com.arosbio.ml.cp.nonconf.NCM;

/**
 * Interface for regression nonconformity measures. Makes it possible to define your own,
 * custom, nonconformity measure
 * 
 * @author Aros Bio AB 
 * @author staffan
 *
 */
public interface NCMRegression extends NCM {
	
	public static final List<String> CONFIG_BETA_PARAM_NAMES = Arrays.asList("beta", "ncmBeta");
    final static String INVALID_BETA_ERR_MSG = "Beta value must be in range [0..+∞)";
    final static double DEFAULT_BETA_VALUE = 0.01;
	final static String BETA_PROPERTY_KEY = "ncm_beta";
	
	public double calcNCS(DataRecord example) throws IllegalStateException;
	
	public double predictMidpoint(FeatureVector example) throws IllegalStateException;
	
	public double calcIntervalScaling(FeatureVector example) throws IllegalStateException;
	
	/**
	 * If this nonconformity measure needs an error model to be generated
	 * @return {@code true} If an error model should be used, {@code false} otherwise
	 */
	public boolean requiresErrorModel();
	
	
	/**
	 * Returns the error model or <code>null</code> if no error model is used
	 * @return the error algorithm
	 */
	public MLAlgorithm getErrorModel();
	
	public void setErrorModel(MLAlgorithm model) throws IllegalArgumentException;
	
	public NCMRegression clone();
	
	public Regressor getModel();
	
	public void setModel(Regressor model) throws IllegalArgumentException;
	
}

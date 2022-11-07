/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp.nonconf.calc;

import java.util.List;

import com.arosbio.commons.mixins.HasID;
import com.arosbio.commons.mixins.Named;

/**
 * An interface for calculating the confidence to nonconformity score (NCS) function. Allows for user-definable 
 * ways of interpolating the NCS in better ways than the original description of Conformal Prediction
 * @author staffan
 *
 */
public interface PValueCalculator extends Named, HasID {

	public final static String PVALUE_CALCULATOR_NAME_KEY = "pValueCalculatorName";
	public final static String PVALUE_CALCULATOR_ID_KEY = "pValueCalculatorID";
	public final static String PVALUE_CALCULATOR_SEED_KEY = "pValueCalculatorSeed";
	
	/**
	 * Allows to pre-fit smoothing functions or similar
	 * @param ncs the nonconformity scores
	 * @throws IllegalArgumentException If too few scores are sent, or other requirements that the extractor-implementation might have
	 */
	public void build(List<Double> ncs) throws IllegalArgumentException;
	
	
	/**
	 * Return an unmodifiable view of the NCS scores 
	 * @return An unmodifiable list of NCS scores
	 */
	public List<Double> getNCSscores();
	
	/**
	 * Checks if the extractor has been built, used the {@link #build(List)} method
	 * @return if the extractor can be used or has to be built
	 */
	public boolean isReady();
	
	/**
	 * Set the Random Number Generator (RNG) seed
	 * @param seed Seed to use
	 */
	public void setRNGSeed(long seed);
	
	/**
	 * Get the Random Number Generator (RNG) seed that is used
	 * @return the seed used by this object, or {@code null} if not used 
	 */
	public Long getRNGSeed();
	
	/**
	 * The extractor method that is called in the prediction phase 
	 * @param confidence the confidence the nonconformity score should correspond to
	 * @return the nonconformity score corresponding to the confidence
	 * @throws IllegalStateException Should be thrown in case the method is called before the {@link #build(List)} method has been called
	 * @throws IllegalArgumentException If confidence is less than 0 or greater than 1
	 */
	public double getNCScore(double confidence) throws IllegalStateException, IllegalArgumentException;
	
	/**
	 * The inverse extractor method, go from NCS (nonconformity score) to p-value
	 * @param ncs desired NCS to get corresponding p-value for
	 * @return the p-value for the given NCS
	 * @throws IllegalStateException if the calculator is not initialized by calling the {@link #build(List)} method before
	 */
	public double getPvalue(double ncs) throws IllegalStateException;
	
	/**
	 * Generate a new instance of the class, but without any data 
	 * @return an empty instance of the same class
	 */
	public PValueCalculator clone();

}

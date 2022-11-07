/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics.cp;

public interface ConfidenceDependentMetric extends CPMetric {
	
	public static final double DEFAULT_CONFIDENCE = 0.8;
	
	public double getConfidence();
	
	/**
	 * Sets the confidence to use for this metric
	 * @param confidence new confidence, should be in the range [0..1]
	 * @throws IllegalArgumentException If <code>confidence</code> is outside the allowed range [0..1]
	 * @throws IllegalStateException If predictions has been added already and confidence cannot be changed
	 */
	public void setConfidence(double confidence) throws IllegalArgumentException, IllegalStateException;

}

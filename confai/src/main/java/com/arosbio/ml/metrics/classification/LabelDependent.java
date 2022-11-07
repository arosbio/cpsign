/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics.classification;

/**
 * For binary classification tasks, set/get the positive label - and the 'other' label will be
 * regarded as the negative label
 * @author staffan
 *
 */
public interface LabelDependent {
	
	public static final int DEFAULT_POS_LABEL = 1; 

	/**
	 * Set the label that should be regarded as the positive class
	 * @param positive label to regard as positive class 
	 * @throws IllegalStateException If predictions have been added - cannot change the label
	 */
	public void setPositiveLabel(int positive) throws IllegalStateException;
	public int getPositiveLabel();
	
}

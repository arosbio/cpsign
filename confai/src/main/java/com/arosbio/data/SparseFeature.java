/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data;

import com.arosbio.data.FeatureVector.Feature;

/**
 * The {@link SparseFeature} holds a single feature of a single record. Has an index and a value for the given feature
 * 
 * 
 * @author ola
 *
 */
public interface SparseFeature extends Comparable<SparseFeature>, Feature {
	
	/**
	 * Getter for the current index of the feature
	 * @return the feature index
	 */
	public int getIndex();
	
	/**
	 * Creates a new feature with the same value but with the new index
	 * @param index new index
	 * @return A new SparseFeature object
	 */
	public SparseFeature withIndex(int index);
	
	/**
	 * Update the feature index to a new one
	 * @param index The feature index
	 * @throws IndexOutOfBoundsException In case the index is negative
	 */
	public void setIndex(int index) throws IndexOutOfBoundsException;
	
	/**
	 * Shift the index with a fixed number of indices. A negative <code>shift</code>
	 * moves the feature "left" and a positive <code>shift</code> "right"
	 * @param shift a positive or negative number of indices to shift the current index with
	 * @throws IndexOutOfBoundsException In case the index would becomes negative if the <code>shift</code> would be applied 
	 */
	public void shiftIndex(int shift) throws IndexOutOfBoundsException;

	public double getValue();
	
	/**
	 * Create a new instance with the same index but with a different value
	 * @param value the value of the new instance
	 * @return a new SparseFeature instance 
	 */
	public SparseFeature withValue(double value);

	public String toString();
	
	public SparseFeature clone();
}

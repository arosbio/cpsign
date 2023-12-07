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

import java.util.List;

import com.google.common.collect.Range;

/**
 * {@link FeatureVector FeatureVectors} contain the features for records. The feature indices starts from index 0.
 * 
 * @author staffan
 *
 */
public interface FeatureVector extends Iterable<FeatureVector.Feature> {
	
	/**
	 * A {@code Feature} is a single attribute out of a single record, the underlying
	 * {@link FeatureVector} implementation is unknown but the {@code Feature} itself
	 * is immutable.
	 * 
	 * @author staffan
	 *
	 */
	public static interface Feature {

		/** prime number used in hash method, multiply with the index */
		static final int PRIME_MULTIPLIER = 43; 
		
		/**
		 * Getter for the index of the feature, <b>note:</b> indices starts at 0!
		 * @return The index
		 */
		public int getIndex();
		
		/**
		 * Getter for the value of the feature, underlying storage may not be double
		 * precision floating point numbers but they should here be given as a double.
		 * Most algorithms require this.
		 * 
		 * @return the value, or {@code Double.NaN} if missing value
		 */
		public double getValue();
		
		/**
		 * Create a deep clone of the current instance
		 * @return an identical copy of the current instance
		 */
		public Feature clone();

		public static int hashCode(Feature f){
			return PRIME_MULTIPLIER * f.getIndex() + Double.hashCode(f.getValue());
		}
	}

	/**
	 * Get the number of explicit features
	 * @return Number of explicitly saved features
	 */
	public int getNumExplicitFeatures();
	
	/**
	 * Get the smallest feature index in this vector. For debugging mostly
	 * @return The smallest index for this vector 
	 */
	public int getSmallestFeatureIndex();
	
	/**
	 * Get the largest feature index in this vector, might be the overall largest (for dense vectors) or 
	 * the largest of this particular instance (for sparse vectors)
	 * @return The largest index number for this vector, or -1 if no features 
	 */
	public int getLargestFeatureIndex();
	
	/**
	 * Remove a single feature at a given index from the vector. This should be performed by
	 * shifting features that comes after the given index. 
	 * @param index the feature index to remove
	 * @throws IndexOutOfBoundsException Trying to remove an index that is not saved within this vector
	 */
	public void removeFeatureIndex(int index) 
			throws IndexOutOfBoundsException;
	
	/**
	 * Remove multiple indices at the same time in similar fashion as {@link #removeFeatureIndex(int)}
	 * @param sortedIndices multiple indices to remove, <b>MUST</b> be sorted
	 * @throws IndexOutOfBoundsException Trying to remove an index that is not saved within this vector
	 */
	public void removeFeatureIndices(List<Integer> sortedIndices) 
			throws IndexOutOfBoundsException;
	
	/**
	 * Remove indices specified by a range, e.g. remove all features with index greater than 1000
	 * @param range A range of indices to remove
	 * @throws IndexOutOfBoundsException If the range specifies invalid indices
	 */
	public void removeFeatureIndices(Range<Integer> range) 
			throws IndexOutOfBoundsException;
	
	/**
	 * Get the feature of a specific index
	 * @param index the index to get
	 * @return The feature value for this index, could be <code>null</code> or <code>Double.NaN</code>
	 * @throws IndexOutOfBoundsException Trying to access an index that is not within this vector
	 */
	public double getFeature(int index) throws IndexOutOfBoundsException;
	
	/**
	 * Update the value at a specific index
	 * @param index The index
	 * @param newValue The new value that should be set
	 * @return the calling instance, for fluid API
	 * @throws IndexOutOfBoundsException Trying to set a feature that is not within the range of this vector
	 */
	public FeatureVector withFeature(int index, Double newValue) throws IndexOutOfBoundsException;

	/**
	 * Update the value at a specific index
	 * @param index The index
	 * @param newValue The new value of that index
	 * @return the calling instance, for fluid API
	 * @throws IndexOutOfBoundsException Trying to set a feature that is not within the range of this vector
	 */
	public FeatureVector withFeature(int index, int newValue) throws IndexOutOfBoundsException;
	
	/**
	 * Generate a deep copy of the instance
	 * @return a new, identical, copy of this instance
	 */
	public FeatureVector clone();
	
	/**
	 * Equality only needs to be checked for a vector of the same type. Use 
	 * {@link DataUtils#equals(com.arosbio.data.Dataset.SubSet, com.arosbio.data.Dataset.SubSet)}
	 * or {@link com.arosbio.data.DataUtils#equals(FeatureVector, FeatureVector, double)} for extensive checks.
	 * @param o The other object
	 * @return {@code true} if equal
	 */
	public boolean equals(Object o);
	
	/**
	 * Checks if the vector contains missing features, i.e. values not available
	 * and that thus will require imputation of the feature or removal of the record
	 * before further analysis
	 * @return if the feature contain missing feature(s)
	 */
	public boolean containsMissingFeatures();
	
}

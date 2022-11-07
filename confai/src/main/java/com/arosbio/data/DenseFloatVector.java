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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.arosbio.commons.CollectionUtils;
import com.google.common.collect.BoundType;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;

/**
 * Similar to the {@link DenseVector} but uses an {@code float[]} to store the values,
 * thus saving roughly half the memory of that of the {@link DenseVector}. 
 * @author staffan
 *
 */
public class DenseFloatVector implements FeatureVector {

	private float[] vector;
	
	/**
	 * Create a vector with {@code maxFeatureIndex} as the largest feature index (i.e. {@code maxFeatureIndex+1} attributes of features).
	 * The values will be initialized with {code Float.NaN} to signify that no values has been added yet.
	 * @param maxFeatureIndex The largest feature index
	 */
	public DenseFloatVector(int maxFeatureIndex) {
		this.vector = new float[maxFeatureIndex+1];
		Arrays.fill(vector,Float.NaN);
	}

	/**
	 * DenseVectors are 0 based, so all indices will be used 
	 * @param vector A vector in the form of a float array
	 */
	public DenseFloatVector(float[] vector) {
		this.vector = vector;
		if (vector == null)
			this.vector = new float[0];
	}

	public DenseFloatVector(FeatureVector vector, int maxFeatureIndex) {
		if (vector instanceof DenseFloatVector) {
			if (((DenseFloatVector)vector).vector.length == (maxFeatureIndex+1)){
				// Equal size - just copy the float array
				this.vector = ((DenseFloatVector) vector).vector.clone();
			} else {
				this.vector = new float[maxFeatureIndex+1];
				// Copy as much as possible
				int len = Math.min(this.vector.length, ((DenseFloatVector)vector).vector.length);
				System.arraycopy(((DenseFloatVector)vector).vector, 0, this.vector, 0, len);
				if (len < this.vector.length){
					// If the new array is longer, fill remaining slots with NaN
					Arrays.fill(this.vector, len, this.vector.length, Float.NaN);
				}
			}
			
		} else {
			// Deduce the largest feature index otherwise
			if (maxFeatureIndex < 0)
				maxFeatureIndex = vector.getLargestFeatureIndex();

			this.vector = new float[maxFeatureIndex+1]; // Need to add one to the length, index 0 -> length 1, index 1 -> length 2,...
			// Init all instances with 0
			Arrays.fill(this.vector, 0f);
			for (Feature f : vector) {
				this.vector[f.getIndex()] = (float)f.getValue();
			}
		}
	}

	public float[] getInternalArray(){
		return vector;
	}

	@Override
	public int getNumExplicitFeatures() {
		return vector.length;
	}

	@Override
	public int getSmallestFeatureIndex() {
		if (vector.length>0)
			return 0;
		return -1;
	}

	@Override
	public int getLargestFeatureIndex() {
		return vector.length - 1;
	}

	public String toString() {
		return toCSV(',');
	}

	public String toCSV(char delim) {
		StringBuffer buffer = new StringBuffer();
		for (int i=0;i<vector.length;i++) {
			buffer.append(vector[i]);
			buffer.append(delim);
		}
		// Remove trailing delim
		if (buffer.length()>0){
			buffer.delete(buffer.length()-1, buffer.length());
		}
		return buffer.toString();
	}

	public DenseFloatVector clone() {
		return new DenseFloatVector(vector.clone());
	}

	@Override
	public Iterator<Feature> iterator() {
		return new Iterator<Feature>(){

			private int currentIndex = 0;

			@Override
			public boolean hasNext() {
				return currentIndex < vector.length;
			}

			@Override
			public Feature next(){
				if (!hasNext())
					throw new NoSuchElementException("No more items in iterator");
				try {
					return new ImmutableFeature(currentIndex, vector[currentIndex]);
				} finally {
					currentIndex++;
				}
			}

		};
	}
	
	@Override
	public double getFeature(int index) {
		return vector[index];
	}

	@Override
	public void setFeature(int index, Double newValue) {
		vector[index] = (newValue !=null ? newValue.floatValue() : Float.NaN);
	}

	public boolean equals(Object o) {
		if (this == o)
			return true;

		if (!(o instanceof DenseFloatVector)) {
			return false;
		}

		return Arrays.equals(vector, ((DenseFloatVector) o).vector);
	}

	@Override
	public boolean containsMissingFeatures() {
		for (int i=0; i<vector.length; i++) {
			if (Float.isNaN(vector[i]))
				return true;
		}
		return false;
	}

	@Override
	public void removeFeatureIndex(int index) throws IndexOutOfBoundsException {
		if (index < 0 || index>getLargestFeatureIndex())
			throw new IndexOutOfBoundsException("Cannot remove index " + index + ": out of bounds for vector of length " + getLargestFeatureIndex());
		float[] newVec = new float[vector.length-1];
		// Copy the first section
		System.arraycopy(vector, 0, newVec, 0, index);

		// Copy the rest
		System.arraycopy(vector, index+1, newVec, index, vector.length-index-1);

		this.vector = newVec;
	}

	@Override
	public void removeFeatureIndices(List<Integer> indices) 
			throws IndexOutOfBoundsException {
		if (indices.isEmpty())
			return;
		if (CollectionUtils.containsNull(indices))
			throw new NullPointerException("Indices cannot be null");
		if (indices.size() == 1) {
			removeFeatureIndex(indices.iterator().next());
			return;
		}

		List<Integer> sortedList = new ArrayList<>(indices);

		if (sortedList.get(0) < 0 || sortedList.get(sortedList.size()-1) > vector.length)
			throw new IndexOutOfBoundsException("Index specified outside current feature indices");

		// Allocate a new array of correct size
		float[] newVec = new float[vector.length - sortedList.size()];

		int oldVecIndex = 0;
		// this might be slow
		for (int newVecInd=0; newVecInd<newVec.length; newVecInd++) {
			if (!sortedList.isEmpty() && sortedList.get(0) == oldVecIndex) {
				sortedList.remove(0); // remove it from the list
				newVecInd--; // Redo the same index for the new vector
			} else {
				newVec[newVecInd] = vector[oldVecIndex];
			}
			oldVecIndex++;
		}

		vector = newVec;

	}

	@Override
	public void removeFeatureIndices(Range<Integer> range) throws IndexOutOfBoundsException {
		if (CollectionUtils.rangeHasNoBounds(range)) {
			vector = new float[0];
		} else if (range.hasLowerBound() && ! range.hasUpperBound()) {
			// Only a lower bound
			removeFeaturesFrom(range.lowerEndpoint()+(range.lowerBoundType()==BoundType.CLOSED? 0 : 1));
		} else if (!range.hasLowerBound() && range.hasUpperBound()){
			// Only an upper bound
			removeFeatureUpUntil(range.upperEndpoint() + (range.upperBoundType()==BoundType.CLOSED? 0 : -1));
		} else {
			// Both an upper and lower bound
			int lowInclusive = range.lowerEndpoint() + (range.lowerBoundType()==BoundType.CLOSED? 0 : 1);
			int upperInclusive = range.upperEndpoint() + (range.upperBoundType()==BoundType.CLOSED? 0 : -1);
			if (lowInclusive < 0 || upperInclusive > vector.length)
				throw new IndexOutOfBoundsException("Cannot remove index outside range of features");

			// make a set and use the removeFeatureIndices(set) method
			removeFeatureIndices(StreamSupport.stream(
					Spliterators.spliteratorUnknownSize(
							ContiguousSet.create(range, DiscreteDomain.integers()).iterator(),
							Spliterator.ORDERED), false)
					.collect(Collectors.toList()));
		}	
	}

	private void removeFeaturesFrom(int inclusiveFeatureIndex) {
		if (inclusiveFeatureIndex < 0)
			throw new IndexOutOfBoundsException("Cannot remove index smaller than 0");
		float[] tmp = new float[inclusiveFeatureIndex];

		System.arraycopy(vector, 0, tmp, 0, inclusiveFeatureIndex);

		vector = tmp;
	}

	private void removeFeatureUpUntil(int inclusiveFeatureIndex) {
		if (inclusiveFeatureIndex>= vector.length) {
			// clear and we're done
			vector = new float[0];
			return;
		}
		float[] tmp = new float[vector.length - (inclusiveFeatureIndex+1)];

		System.arraycopy(vector, (inclusiveFeatureIndex+1), tmp, 0, vector.length - (inclusiveFeatureIndex+1));

		vector = tmp;

	}

}

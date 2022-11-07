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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.google.common.collect.BoundType;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;

public class SparseVector implements FeatureVector {

	// ---------------------------------------------------------------------
	// STATIC DATA
	// ---------------------------------------------------------------------
	private static final Logger LOGGER = LoggerFactory.getLogger(SparseVector.class);

	private List<SparseFeature> vector; 

	public SparseVector() {
		this.vector = new ArrayList<>();
	}

	public SparseVector(List<SparseFeature> vector) {
		this.vector = vector;
		if (this.vector == null) {
			this.vector = new ArrayList<>();
		}
	}

	public SparseVector(FeatureVector vec) {
		vector = new ArrayList<>(vec.getNumExplicitFeatures());
		for (Feature f : vec) {
			if (f.getValue() == 0d)
				continue; // Skip the identical to 0 indices
			vector.add(new SparseFeatureImpl(f.getIndex(), f.getValue()));
		}
	}

	public SparseVector(int[] indices, double[] values) throws IllegalArgumentException {
		Objects.requireNonNull(indices, "indices cannot be null");
		Objects.requireNonNull(values, "values cannot be null");
		if (indices.length != values.length)
			throw new IllegalArgumentException("indices and values must be of equal length");

		vector = new ArrayList<>();

		for (int i=0; i<indices.length; i++){
			vector.add(new SparseFeatureImpl(indices[i], values[i]));
		}
		Collections.sort(vector);

		if (!vector.isEmpty() && vector.get(0).getIndex() < 0){
			throw new IllegalArgumentException("Smallest index must be >=0");
		}

	}

	public List<SparseFeature> getInternalList(){
		return vector;
	}

	@Override
	public int getNumExplicitFeatures() {
		return vector.size();
	}

	@Override
	public int getSmallestFeatureIndex() {
		if (vector.isEmpty())
			return -1;
		return vector.get(0).getIndex();
	}

	@Override
	public int getLargestFeatureIndex() {
		if (vector.isEmpty())
			return -1;
		return vector.get(vector.size()-1).getIndex();
	}

	public SparseVector clone() {
		SparseVector clone = new SparseVector();
		for (SparseFeature f : vector) {
			clone.vector.add(f.clone());
		}
		return clone;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (SparseFeature f: vector) {
			if (sb.length()>0)
				sb.append(' ');
			sb.append(f.getIndex());
			sb.append(':');
			sb.append(f.getValue());
		}
		return sb.toString();
	}

	@Override
	public Iterator<Feature> iterator() {
		return new MyWrapperIterator();
	}

	private class MyWrapperIterator implements Iterator<Feature>{

		private int index;

		@Override
		public boolean hasNext() {
			return index < vector.size();
		}

		@Override
		public Feature next() {
			try {
				return (Feature) vector.get(index);
			} finally {
				index ++;
			}
		}

	}

	@Override
	public double getFeature(int index) throws IndexOutOfBoundsException {
		if (index < 0) {
			throw new IndexOutOfBoundsException("Indices starts at 0, got: " + index);
		}
		// Check if the index is outside of the current size of this vector - return 0 then
		if (vector.isEmpty() || vector.get(vector.size()-1).getIndex() < index) {
			return 0d;
		}
		// Else - search for it
		final int listIndex = Collections.binarySearch(vector, new SparseFeatureImpl(index, -1));
		if (listIndex>= 0)
			return vector.get(listIndex).getValue();
		// return 0 otherwise
		return 0d;
	}

	@Override
	public void setFeature(int index, Double newValue) 
			throws IndexOutOfBoundsException {
		if (index < 0) {
			throw new IndexOutOfBoundsException("Indexes starts at 0");
		}
		
		// Search for the index
		final int listIndex = Collections.binarySearch(vector, new SparseFeatureImpl(index, -1));
		if (listIndex>= 0) {
			if (newValue == null || Double.isNaN(newValue)){
				vector.set(listIndex, new MissingValueFeature(index));
			} else {
				vector.set(listIndex, vector.get(listIndex).withValue(newValue));
			}
		} else {
			int insertIndex =  -1*(listIndex+1);
			if (newValue == null || Double.isNaN(newValue)){
				vector.add(insertIndex, new MissingValueFeature(index));	
			} else {
				vector.add(insertIndex, new SparseFeatureImpl(index, newValue));
			}
		}

	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (! (o instanceof FeatureVector))
			return false;

		if (o instanceof SparseVector) {
			SparseVector v = (SparseVector) o;

			return vector.equals(v.vector);
		} 

		return false;
	}

	@Override
	public boolean containsMissingFeatures() {
		for (SparseFeature f: vector) {
			if (f instanceof MissingValueFeature)
				return true;
		}
		return false;
	}

	@Override
	public void removeFeatureIndex(int index) {
		removeFeatureIndices(Arrays.asList(index));
	}

	@Override
	public void removeFeatureIndices(final List<Integer> indices) {
		if (indices == null || indices.isEmpty())
			return;


		int i=0, j=0, toReduceIndexWith = 0;

		//		List<SparseFeature> newVector = new ArrayList<>(vector.size());
		for ( ; i<vector.size(); i++) {
			SparseFeature f = vector.get(i);
			if (j>=indices.size()) {
				// No more indices to remove
				f.setIndex(f.getIndex()-toReduceIndexWith);
				//				newVector.add(new SparseFeatureImpl(f.getIndex()-toReduceIndexWith, f.getValue()));
			} else if (f.getIndex() == indices.get(j)) {
				// At a remove-index
				vector.remove(i);
				i--; // redo this index in the list
				j++; // move to next index to remove
				toReduceIndexWith++; // following indices must be decreased another step
			} else if (f.getIndex() > indices.get(j)) {
				while (j<indices.size() && indices.get(j) < f.getIndex()) {
					j++;
					toReduceIndexWith++;
				}
				i--; // Redo this index in the outer loop
			} else if (f.getIndex() < indices.get(j)) {
				// simply reduce this index and continue 
				f.setIndex(f.getIndex()-toReduceIndexWith);
			} else {
				LOGGER.error("Unknown state in iteration loop (removeFeatureIndices(List<Integer>)");
				throw new RuntimeException("FAIL");
			}


		}
	}

	@Override
	public void removeFeatureIndices(Range<Integer> range) throws IndexOutOfBoundsException {
		if (CollectionUtils.rangeHasNoBounds(range)) {
			// no bounds - remove all
			vector.clear();
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
			if (lowInclusive < 0)
				throw new IndexOutOfBoundsException("Cannot remove index outside range of features");

			if (upperInclusive > vector.get(vector.size()-1).getIndex()) {
				// if the upper index is outside the size of this vector, use removeFeatureFrom
				removeFeaturesFrom(lowInclusive);
				return;
			}

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
		int index = Collections.binarySearch(vector, new SparseFeatureImpl(inclusiveFeatureIndex, Double.NaN));
		if (index >=0) {
			vector.subList(index, vector.size()).clear();
		} else {
			// feature not explicitly saved - remove the next index and all other
			int ind = (index+1)*-1;
			vector.subList(ind, vector.size()).clear();

		}

	}

	private void removeFeatureUpUntil(int inclusiveFeatureIndex) {
		if (inclusiveFeatureIndex>= vector.get(vector.size()-1).getIndex()) {
			// clear and we're done
			vector.clear();
			return;
		}

		int index = Collections.binarySearch(vector, new SparseFeatureImpl(inclusiveFeatureIndex, Double.NaN));
		if (index >=0) {
			vector.subList(0, index+1).clear(); // Sublist is exclusive in the second arg, need +1
		} else {
			// feature not explicitly saved - remove the next index and all other
			int ind = (index+1)*-1;
			vector.subList(0, ind+1).clear(); // Sublist is exclusive in the second arg, need +1

		}

		// Update the index of remaining ones
		for (SparseFeature sf : vector) {
			sf.shiftIndex(-(1 + inclusiveFeatureIndex)); // need +1 using 0 as starting-index
		}
	}

}

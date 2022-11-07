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

import com.arosbio.commons.MathUtils;

/**
 * 
 * @author staffan
 *
 */
@Deprecated
public class SparseFeatureShort implements SparseFeature {

	private int index;
	private short value;

	public SparseFeatureShort(int index, short value) {
		this.index = index;
		this.value = value;
	}


	@Override
	public int getIndex() {
		return index;
	}
	
	public SparseFeatureShort withIndex(int index) {
		return new SparseFeatureShort(index, this.value);
	}

	@Override
	public double getValue() {
		return value;
	}
	
	public SparseFeature withValue(double value) {
		return new SparseFeatureImpl(this.index, value);
	}
	
	public SparseFeatureShort withValue(short value) {
		return new SparseFeatureShort(this.index, value);
	}

//	@Override
//	public void setValue(double value) {
//		this.value = value;
//	}

//	public Pair<Integer, Double> getFeature() {
//		return Pair.with(index, value);
//	}
//
//	public void setFeature(Pair<Integer, Double> feature) {
//		this.index = feature.getValue0();
//		this.value = feature.getValue1();
//	}

//	public static SparseFeature fromFeature(SparseFeature feature) {
//		if (feature instanceof SparseFeatureShort)
//			return (SparseFeatureShort) feature;
//		if (Double.isNaN(feature.getValue()) )
//			return new MissingValueFeature(feature.getIndex());
//		
//		return new SparseFeatureShort(feature.getIndex(), feature.getValue());
//	}

	@Override
	public String toString() {
		return "[" + index + "," + value + "]";
	}

	@Override
	public int compareTo(SparseFeature o) {
		return index - o.getIndex();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + index;
		long temp;
		temp = Double.doubleToLongBits(value);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object o){
		if (this==o)
			return true;

		if(!(o instanceof SparseFeature))
			return false;
		SparseFeature o_sf = (SparseFeature) o;
		if (o_sf.getIndex() != index)
			return false;
		if (!MathUtils.equals(o_sf.getValue(), value))
			return false;

		return true;
	}
	
	public SparseFeatureShort clone() {
		return new SparseFeatureShort(index, value);
	}


	@Override
	public void setIndex(int index) throws IndexOutOfBoundsException {
		if (index < 0)
			throw new IndexOutOfBoundsException("Index cannot be negative, got: " + index);
		this.index = index;
	}


	@Override
	public void shiftIndex(int shift) throws IndexOutOfBoundsException {
		if (index + shift < 0)
			throw new IndexOutOfBoundsException("Cannot have negative feature indices: " + (index + shift));
		
		this.index += shift;
	}

}
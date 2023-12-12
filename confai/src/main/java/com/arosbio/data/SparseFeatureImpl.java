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
import com.arosbio.data.FeatureVector.Feature;

/**
 * 
 * @author ola
 * @author staffan
 *
 */
public class SparseFeatureImpl implements SparseFeature {
	
	private int index;
	private double value;

	public SparseFeatureImpl(int index, double value) {
		if (index < 0)
			throw new IndexOutOfBoundsException("Index cannot be negative, got: " + index);
		this.index = index;
		this.value = value;
	}


	@Override
	public int getIndex() {
		return index;
	}
	
	@Override
	public void setIndex(int index) {
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
	
	@Override
	public SparseFeatureImpl withIndex(int index) {
		return new SparseFeatureImpl(index, value);
	}

	@Override
	public double getValue() {
		return value;
	}
	
	@Override
	public SparseFeatureImpl withValue(double value) {
		return new SparseFeatureImpl(this.index, value);
	}
	
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
		return Feature.hashCode(this);
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
		return MathUtils.equals(o_sf.getValue(), value);
	}
	
	public SparseFeatureImpl clone() {
		return new SparseFeatureImpl(index, value);
	}
	
}
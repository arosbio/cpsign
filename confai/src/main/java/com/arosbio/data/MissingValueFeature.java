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

public class MissingValueFeature implements SparseFeature {
	
	private int index;
	
	public MissingValueFeature(int index) {
		this.index = index;
	}

	@Override
	public int compareTo(SparseFeature o) {
		return index - o.getIndex();
	}

	@Override
	public int getIndex() {
		return index;
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
	
	@Override
	public MissingValueFeature withIndex(int index) {
		return new MissingValueFeature(index);
	}

	@Override
	public double getValue() {
		return Double.NaN;
	}
	
	@Override
	public SparseFeature withValue(double value) {
		if (Double.isNaN(value) || Double.isInfinite(value))
			return new MissingValueFeature(this.index);
		return new SparseFeatureImpl(index, value);
	}
	
	public MissingValueFeature clone() {
		return new MissingValueFeature(index);
	}
	
	/**
	 * We consider a sparse feature with a value of Double.NaN to be equal to a missing value feature, as long as index matches
	 */
	public boolean equals(Object o) {
		if (o==this)
			return true;
		if (! (o instanceof SparseFeature))
			return false;

		if (o instanceof MissingValueFeature) {
			return ((MissingValueFeature) o).getIndex() == index;
		} else {
			SparseFeature sf = (SparseFeature) o;
			if (sf.getIndex() != index)
				return false;
			return Double.isNaN(sf.getValue());
		}
		
	}
	
	public String toString() {
		return index + ":NaN";
	}

}

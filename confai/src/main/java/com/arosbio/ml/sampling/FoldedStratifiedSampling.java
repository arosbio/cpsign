/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.sampling;

import java.util.Map;

import com.arosbio.data.Dataset;
import com.arosbio.ml.io.impl.PropertyNameSettings;

public class FoldedStratifiedSampling extends FoldedSampling {
	
	public static final int ID = 4;
	public static final String NAME = "FoldedStratified";
	
	
	public FoldedStratifiedSampling() {
		super();
	}
	
	public FoldedStratifiedSampling(int numFolds) {
		super(numFolds);
	}
	
	public int getID() {
		return ID;
	}
	
	public String getName(){
		return NAME;
	}
	

	public FoldedStratifiedSampling clone(){
		return new FoldedStratifiedSampling(getNumSamples());
	}
	
	@Override
	public boolean isStratified() {
		return true;
	}
	
	@Override
	public TrainSplitIterator getIterator(Dataset dataset)
			throws IllegalArgumentException {
		return new StratifiedFoldedCalibSetIterator(dataset, getNumSamples());
	}
	
	@Override
	public TrainSplitIterator getIterator(Dataset dataset, long seed)
			throws IllegalArgumentException {
		return new StratifiedFoldedCalibSetIterator(dataset, getNumSamples(), seed);
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String,Object> props = super.getProperties();
		props.put(PropertyNameSettings.SAMPLING_STRATEGY_KEY, ID);
		return props;
	}
	
	@Override
	public boolean equals(Object obj){
		if (! (obj instanceof FoldedStratifiedSampling))
			return false;
		FoldedStratifiedSampling other = (FoldedStratifiedSampling) obj;
		if (this.getNumSamples() != other.getNumSamples())
			return false;
		if (this.isStratified() != other.isStratified())
			return false;
		return true;
	}
	
	public String toString() {
		return "Stratified folded sampling with " + getNumSamples() + " splits";
	}
	
}

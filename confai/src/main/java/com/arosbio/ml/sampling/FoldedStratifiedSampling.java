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

import com.arosbio.commons.GlobalConfig;
import com.arosbio.data.Dataset;
import com.arosbio.data.splitting.FoldedSplitter;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.arosbio.ml.sampling.impl.TrainSplitWrapper;

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
	
	public FoldedStratifiedSampling withNumSamples(int folds){
		super.withNumSamples(folds);
		return this;
	}

	public FoldedStratifiedSampling clone(){
		return new FoldedStratifiedSampling(getNumSamples());
	}
	
	@Override
	public boolean isStratified() {
		return true;
	}
	
	@Override
	public TrainSplitGenerator getIterator(Dataset dataset)
			throws IllegalArgumentException {
		return getIterator(dataset, GlobalConfig.getInstance().getRNGSeed());
	}
	
	@Override
	public TrainSplitGenerator getIterator(Dataset dataset, long seed)
			throws IllegalArgumentException {
		return new TrainSplitWrapper(new FoldedSplitter.Builder()
			.numFolds(getNumSamples())
			.numRepeat(getNumRepeats())
			.seed(seed)
			.shuffle(true)
			.stratify(true)
			.findLabelRange(true)
			.build(dataset));
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
		return this.getNumSamples() == other.getNumSamples() && 
			this.getNumRepeats() == other.getNumRepeats();
	}
	
	public String toString() {
		return "Stratified folded sampling with " + getNumSamples() + " splits";
	}
	
}

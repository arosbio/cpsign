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
import com.arosbio.commons.MathUtils;
import com.arosbio.data.Dataset;
import com.arosbio.data.splitting.RandomSplitter;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.arosbio.ml.sampling.impl.TrainSplitWrapper;

public class RandomStratifiedSampling extends RandomSampling {
	
	public static final int ID = 2;
	public static final String NAME = "RandomStratified";
	
	public RandomStratifiedSampling() {
		super();
	}
	
	public RandomStratifiedSampling(int numSamples, double calibrationRatio) {
		super(numSamples, calibrationRatio);
	}

	public RandomStratifiedSampling(int numSamples, int numCalibrationInstances){
		super(numSamples, numCalibrationInstances);
	}
	
	public int getID() {
		return ID;
	}
	
	@Override
	public String getName(){
		return NAME;
	}

	public RandomStratifiedSampling withNumSamples(int num){
		super.withNumSamples(num);
		return this;
	}
	
	public RandomStratifiedSampling clone(){
		if (getCalibrationRatio() != null) {
			return new RandomStratifiedSampling(getNumSamples(), getCalibrationRatio());
		} else {
			return new RandomStratifiedSampling(getNumSamples(), getNumCalibrationInstances());
		}
	}
	
	@Override
	public TrainSplitGenerator getIterator(Dataset dataset)
			throws IllegalArgumentException {
		return getIterator(dataset, GlobalConfig.getInstance().getRNGSeed());
	}
	
	@Override
	public TrainSplitGenerator getIterator(Dataset dataset, long seed)
			throws IllegalArgumentException {
		return new TrainSplitWrapper(new RandomSplitter.Builder()
			.numSplits(getNumSamples())
			.splitRatio(getCalibrationRatio())
			.splitNumInstances(getNumCalibrationInstances())
			.shuffle(true)
			.seed(seed)
			.stratify(true)
			.findLabelRange(true)
			.name(NAME)
			.build(dataset));
	}

	@Override
	public boolean isStratified() {
		return true;
	}
	
	public String toString() {
		return "Random stratified sampling with "+getNumSamples() + " models and " + getCalibrationRatio() + " calibration ratio";
	}
	
	@Override
	public Map<String, Object> getProperties() {
		Map<String,Object> props = super.getProperties();
		props.put(PropertyNameSettings.SAMPLING_STRATEGY_KEY, ID);
		return props;
	}
	
	@Override
	public boolean equals(Object obj){
		if (! (obj.getClass().equals(RandomStratifiedSampling.class)))
			return false;
		RandomStratifiedSampling other = (RandomStratifiedSampling) obj;
		return (this.getNumSamples() == other.getNumSamples()) &&
			MathUtils.equals(this.getCalibrationRatio(), other.getCalibrationRatio()) &&
			(this.getNumCalibrationInstances() == other.getNumCalibrationInstances());
	}

}

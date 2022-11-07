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

public class RandomStratifiedSampling extends RandomSampling {
	
	public static final int ID = 2;
	public static final String NAME = "RandomStratified";
	
	public RandomStratifiedSampling() {
		super();
	}
	
	public RandomStratifiedSampling(int numModels, double calibrationRatio) {
		super(numModels, calibrationRatio);
	}
	
	public int getID() {
		return ID;
	}
	
	@Override
	public String getName(){
		return NAME;
	}
	
	public RandomStratifiedSampling clone(){
		return new RandomStratifiedSampling(getNumSamples(), getCalibrationRatio());
	}
	
	@Override
	public TrainSplitIterator getIterator(Dataset dataset)
			throws IllegalArgumentException {
		return new StratifiedRandomCalibSetIterator(dataset, getCalibrationRatio(), getNumSamples());
	}
	
	@Override
	public TrainSplitIterator getIterator(Dataset dataset, long seed)
			throws IllegalArgumentException {
		return new StratifiedRandomCalibSetIterator(dataset, getCalibrationRatio(), getNumSamples(), seed);
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
//		props.put(CONFIG_NUM_SAMPLES_PARAM_NAMES[0], getNumSamples());
//		props.put(PropertyFileSettings.SAMPLING_STRATEGY_NR_MODELS_KEY, getNumSamples());
//		props.put(PropertyFileSettings.SAMPLING_STRATEGY_CALIB_RATIO_KEY, getCalibrationRatio());
		return props;
	}
	
	@Override
	public boolean equals(Object obj){
		if (! (obj instanceof RandomStratifiedSampling))
			return false;
		RandomStratifiedSampling other = (RandomStratifiedSampling) obj;
		if (this.getNumSamples() != other.getNumSamples())
			return false;
		if (this.getCalibrationRatio() != other.getCalibrationRatio())
			return false;
		return true;
	}

}

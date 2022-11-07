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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.IntegerConfig;
import com.arosbio.commons.config.NumericConfig;
import com.arosbio.data.Dataset;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.google.common.collect.Range;

public class RandomSampling implements MultiSampling {

	public static final int DEFAULT_NUM_SAMPLES = 1;
	public static final double DEFAULT_CALIBRATION_RATIO = 0.2;
	public static final String[] CONFIG_NUM_SAMPLES_PARAM_NAMES = new String[] {"numSamples"};
	public static final String[] CONFIG_CALIBRATION_RATIO_PARAM_NAMES = new String[] {"calibRatio", "calibrationRatio"};

	public static final int ID = 1;
	public static final String NAME = "Random";


	private int numModels;
	private double calibrationRatio;

	/**
	 * Using default number of samples (1) and default calibration ratio (0.2)
	 */
	public RandomSampling() {
		this(DEFAULT_NUM_SAMPLES, DEFAULT_CALIBRATION_RATIO);
	}

	public RandomSampling(int numModels, double calibrationRatio) {
		super();
		setNumSamples(numModels);
		setCalibrationRatio(calibrationRatio);
	}

	public int getID() {
		return ID;
	}

	public String getName(){
		return NAME;
	}

	public RandomSampling clone(){
		return new RandomSampling(numModels, calibrationRatio);
	}

	public void setNumSamples(int num){
		if (num < 1)
			throw new IllegalArgumentException("Number of samplings must be at least 1");
		this.numModels = num;
	}

	@Override
	public int getNumSamples() {
		return numModels;
	}

	public void setCalibrationRatio(double ratio) {
		if (ratio <= 0 || ratio >= 1)
			throw new IllegalArgumentException("Calibration ratio must be in the range (0..1)");
		calibrationRatio = ratio;
	}

	public double getCalibrationRatio(){
		return calibrationRatio;
	}

	public String toString() {
		return "Random sampling with "+numModels + " models and " + calibrationRatio + " calibration ratio";
	}

	@Override
	public TrainSplitIterator getIterator(Dataset dataset)
			throws IllegalArgumentException {
		return new RandomCalibSetIterator(dataset, calibrationRatio, numModels);
	}

	@Override
	public TrainSplitIterator getIterator(Dataset dataset, long seed)
			throws IllegalArgumentException {
		return new RandomCalibSetIterator(dataset, calibrationRatio, numModels, seed);
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String,Object> props = new HashMap<>();
		props.put(PropertyNameSettings.SAMPLING_STRATEGY_KEY, ID);
		props.put(CONFIG_NUM_SAMPLES_PARAM_NAMES[0], getNumSamples());
		props.put(CONFIG_CALIBRATION_RATIO_PARAM_NAMES[0], getCalibrationRatio());
		return props;
	}

	@Override
	public boolean isFolded() {
		return false;
	}

	@Override
	public boolean isStratified() {
		return false;
	}

	@Override
	public boolean equals(Object obj){
		if (! (obj instanceof RandomSampling))
			return false;
		RandomSampling other = (RandomSampling) obj;
		if (this.getNumSamples() != other.getNumSamples())
			return false;
		if (this.getCalibrationRatio() != other.getCalibrationRatio())
			return false;
		if (this.isStratified() != other.isStratified())
			return false;
		return true;
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		return Arrays.asList(
			new IntegerConfig.Builder(Arrays.asList(CONFIG_NUM_SAMPLES_PARAM_NAMES), DEFAULT_NUM_SAMPLES).range(Range.atLeast(1)).build(),
			new NumericConfig.Builder(Arrays.asList(CONFIG_CALIBRATION_RATIO_PARAM_NAMES), DEFAULT_CALIBRATION_RATIO).range(Range.open(0d, 1d)).build());
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
		for (Map.Entry<String, Object> kv: params.entrySet()) {
			if (CollectionUtils.containsIgnoreCase(CONFIG_NUM_SAMPLES_PARAM_NAMES, kv.getKey())) {
				if (!TypeUtils.isInt(kv.getValue())) {
					throw new IllegalArgumentException("Parameter " + CONFIG_NUM_SAMPLES_PARAM_NAMES[0] + " must be integer number, got '" + kv.getValue()+'\'');
				}
				int nSamples = TypeUtils.asInt(kv.getValue());
				if (nSamples < 1)
					throw new IllegalArgumentException("Parameter " + CONFIG_NUM_SAMPLES_PARAM_NAMES[0] + " must be >=1");
				numModels = nSamples;
			} else if (CollectionUtils.containsIgnoreCase(CONFIG_CALIBRATION_RATIO_PARAM_NAMES, kv.getKey())) {
				if (! TypeUtils.isDouble(kv.getValue())) {
					throw new IllegalArgumentException("Parameter " + CONFIG_CALIBRATION_RATIO_PARAM_NAMES[0] + " must be floating point number, got '" + kv.getValue()+'\'');
				}
				double calib = TypeUtils.asDouble(kv.getValue());
				if (calib <= 0 || calib>= 1)
					throw new IllegalArgumentException("Parameter " + CONFIG_CALIBRATION_RATIO_PARAM_NAMES[0] + " must be a number in the range (0..1), got '" + kv.getValue()+'\'');
				calibrationRatio = calib;
			}
		}
	}

}

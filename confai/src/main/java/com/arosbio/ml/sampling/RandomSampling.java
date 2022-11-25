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
import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.MathUtils;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.config.IntegerConfig;
import com.arosbio.commons.config.NumericConfig;
import com.arosbio.data.Dataset;
import com.arosbio.data.splitting.RandomSplitter;
import com.arosbio.ml.TrainingsetValidator;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.arosbio.ml.sampling.impl.TrainSplitWrapper;
import com.google.common.collect.Range;

public class RandomSampling implements MultiSampling {

	public static final int DEFAULT_NUM_SAMPLES = 1;
	public static final double DEFAULT_CALIBRATION_RATIO = 0.2;
	public static final String[] CONFIG_NUM_SAMPLES_PARAM_NAMES = new String[] {"numSamples"};
	public static final String[] CONFIG_CALIBRATION_RATIO_PARAM_NAMES = new String[] {"calibRatio", "calibrationRatio"};
	public static final String[] CONFIG_NUM_CALIBRATION_INSTANCES_PARAM_NAMES = new String[] {"nCalib", "numCalib"};

	public static final int ID = 1;
	public static final String NAME = "Random";

	private int numberOfSamples;
	private Double calibrationRatio;
	private Integer numCalibrationInstances;

	/**
	 * Using default number of samples (1) and default calibration ratio (0.2)
	 */
	public RandomSampling() {
		this(DEFAULT_NUM_SAMPLES, DEFAULT_CALIBRATION_RATIO);
	}

	public RandomSampling(int numSamples, double calibrationRatio) {
		super();
		withNumSamples(numSamples);
		withCalibrationRatio(calibrationRatio);
	}

	public RandomSampling(int numSamples, int numCalibrationInstances){
		super();
		withNumSamples(numSamples);
		withNumCalibrationInstances(numCalibrationInstances);
	}

	public int getID() {
		return ID;
	}

	public String getName(){
		return NAME;
	}

	public RandomSampling clone(){
		RandomSampling c = new RandomSampling();
		c.numCalibrationInstances = numCalibrationInstances;
		c.calibrationRatio = calibrationRatio;
		c.numberOfSamples = numberOfSamples;
		return c;
	}

	@Override
	public RandomSampling withNumSamples(int num){
		if (num < 1)
			throw new IllegalArgumentException("Number of samplings must be at least 1");
		this.numberOfSamples = num;
		return this;
	}

	@Override
	public int getNumSamples() {
		return numberOfSamples;
	}

	public RandomSampling withCalibrationRatio(double ratio) throws IllegalArgumentException {
		if (ratio <= 0 || ratio >= 1)
			throw new IllegalArgumentException("Calibration ratio must be in the range (0..1)");
		calibrationRatio = ratio;
		numCalibrationInstances = null; // Set to null to make sure to use this ratio, not the explicit number
		return this;
	}

	public Double getCalibrationRatio(){
		return calibrationRatio;
	}

	public RandomSampling withNumCalibrationInstances(int num) throws IllegalArgumentException {
		if (num < TrainingsetValidator.getInstance().MIN_NUM_CALIBRATION_INSTANCES){
			throw new IllegalArgumentException("To few calibration instances specified: "+num +" vs required minimum: " + TrainingsetValidator.getInstance().MIN_NUM_CALIBRATION_INSTANCES);
		}
		this.numCalibrationInstances = num;
		this.calibrationRatio = null; // Set to null to make sure to use the explicit number instead
		return this;
	}

	public Integer getNumCalibrationInstances(){
		return numCalibrationInstances;
	}

	public String toString() {
		return "Random sampling with "+numberOfSamples + " models and " + calibrationRatio + " calibration ratio";
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
			.numSplits(numberOfSamples)
			.splitRatio(calibrationRatio)
			.splitNumInstances(numCalibrationInstances)
			.shuffle(true)
			.seed(seed)
			.stratify(false)
			.findLabelRange(true)
			.name(NAME)
			.build(dataset));
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String,Object> props = new HashMap<>();
		props.put(PropertyNameSettings.SAMPLING_STRATEGY_KEY, ID);
		props.put(CONFIG_NUM_SAMPLES_PARAM_NAMES[0], numberOfSamples);
		props.put(CONFIG_CALIBRATION_RATIO_PARAM_NAMES[0], calibrationRatio);
		props.put(CONFIG_NUM_CALIBRATION_INSTANCES_PARAM_NAMES[0], numCalibrationInstances);
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
		if (! (obj.getClass().equals(RandomSampling.class))){
			return false;
		}
		RandomSampling other = (RandomSampling) obj;

		return (this.numberOfSamples == other.numberOfSamples) &&
			MathUtils.equals(this.calibrationRatio, other.calibrationRatio) &&
			(this.numCalibrationInstances == other.numCalibrationInstances);
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		return Arrays.asList(
			new IntegerConfig.Builder(Arrays.asList(CONFIG_NUM_SAMPLES_PARAM_NAMES), DEFAULT_NUM_SAMPLES)
				.range(Range.atLeast(1)).build(),
			new NumericConfig.Builder(Arrays.asList(CONFIG_CALIBRATION_RATIO_PARAM_NAMES), DEFAULT_CALIBRATION_RATIO)
				.range(Range.open(0d, 1d))
				.description("Use a ratio of how many training examples should be used for calibration. Note: cannot be given at the same time as the @|bold " + CONFIG_NUM_CALIBRATION_INSTANCES_PARAM_NAMES[0] + "|@ parameter.")
				.build(),
			new IntegerConfig.Builder(Arrays.asList(CONFIG_NUM_CALIBRATION_INSTANCES_PARAM_NAMES),null)
				.range(Range.atLeast(TrainingsetValidator.getInstance().MIN_NUM_CALIBRATION_INSTANCES))
				.description("Use an explicit number of training examples for calibration. Note: cannot be given at the same time as the @|bold " + CONFIG_CALIBRATION_RATIO_PARAM_NAMES[0] + "|@ parameter.")
				.build()
			);
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
		
		params = CollectionUtils.dropNullValues(params);

		Configurable.checkForNonCombinableConfigsGiven(params, CONFIG_CALIBRATION_RATIO_PARAM_NAMES,CONFIG_NUM_CALIBRATION_INSTANCES_PARAM_NAMES);
		for (Map.Entry<String, Object> kv: params.entrySet()) {
			if (CollectionUtils.containsIgnoreCase(CONFIG_NUM_SAMPLES_PARAM_NAMES, kv.getKey())) {
				if (!TypeUtils.isInt(kv.getValue())) {
					throw new IllegalArgumentException("Parameter " + CONFIG_NUM_SAMPLES_PARAM_NAMES[0] + " must be integer number, got '" + kv.getValue()+'\'');
				}
				int nSamples = TypeUtils.asInt(kv.getValue());
				if (nSamples < 1)
					throw new IllegalArgumentException("Parameter " + CONFIG_NUM_SAMPLES_PARAM_NAMES[0] + " must be >=1");
				numberOfSamples = nSamples;
			} else if (CollectionUtils.containsIgnoreCase(CONFIG_CALIBRATION_RATIO_PARAM_NAMES, kv.getKey())) {
				if (! TypeUtils.isDouble(kv.getValue())) {
					throw new IllegalArgumentException("Parameter " + CONFIG_CALIBRATION_RATIO_PARAM_NAMES[0] + " must be floating point number, got '" + kv.getValue()+'\'');
				}
				double calib = TypeUtils.asDouble(kv.getValue());
				if (calib <= 0 || calib>= 1)
					throw new IllegalArgumentException("Parameter " + CONFIG_CALIBRATION_RATIO_PARAM_NAMES[0] + " must be a number in the range (0..1), got '" + kv.getValue()+'\'');
				withCalibrationRatio(calib);
			} else if (CollectionUtils.containsIgnoreCase(CONFIG_NUM_CALIBRATION_INSTANCES_PARAM_NAMES, kv.getKey())){
				if (!TypeUtils.isInt(kv.getValue())) {
					throw new IllegalArgumentException("Parameter " + CONFIG_NUM_SAMPLES_PARAM_NAMES[0] + " must be integer number, got '" + kv.getValue()+'\'');
				}
				int nCalib = TypeUtils.asInt(kv.getValue());
				if (nCalib < TrainingsetValidator.getInstance().MIN_NUM_CALIBRATION_INSTANCES)
					throw new IllegalArgumentException("Parameter " + CONFIG_NUM_SAMPLES_PARAM_NAMES[0] + " must be >=" + TrainingsetValidator.getInstance().MIN_NUM_CALIBRATION_INSTANCES);
				withNumCalibrationInstances(nCalib);
			}
		}
	}

}

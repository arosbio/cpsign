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
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.IntegerConfig;
import com.arosbio.data.Dataset;
import com.arosbio.data.splitting.FoldedSplitter;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.arosbio.ml.sampling.impl.TrainSplitWrapper;
import com.google.common.collect.Range;

public class FoldedSampling implements MultiSampling {
	public static final int ID = 3;
	public static final String NAME = "Folded";
	public static final int DEFAULT_NUM_SAMPLES = 10;
	public static final String[] CONFIG_NUM_SAMPLES_PARAM_NAMES = new String[] {"folds", "numSamples"};
	
	private int numFolds;
	
	public FoldedSampling() {
		this(DEFAULT_NUM_SAMPLES);
	}
	
	public FoldedSampling(int numFolds) {
		super();
		withNumSamples(numFolds);
	}
	
	public int getID() {
		return ID;
	}
	
	public String getName(){
		return NAME;
	}
	
	public FoldedSampling clone(){
		return new FoldedSampling(numFolds);
	}
	
	public FoldedSampling withNumSamples(int folds) {
		if (folds <= 1)
			throw new IllegalArgumentException("Number of samplings must be over 1 when using folded sampling");
		this.numFolds = folds;
		return this;
	}

	@Override
	public int getNumSamples() {
		return numFolds;
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
			.numFolds(numFolds)
			.seed(seed)
			.shuffle(true)
			.stratify(false)
			.findLabelRange(true)
			.build(dataset)); 
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String,Object> props = new HashMap<>();
		props.put(PropertyNameSettings.SAMPLING_STRATEGY_KEY, ID);
		props.put(CONFIG_NUM_SAMPLES_PARAM_NAMES[0], numFolds);
		return props;
	}

	@Override
	public boolean isFolded() {
		return true;
	}

	@Override
	public boolean isStratified() {
		return false;
	}
	
	@Override
	public boolean equals(Object obj){
		if (! (obj instanceof FoldedSampling))
			return false;
		FoldedSampling other = (FoldedSampling) obj;
		return this.numFolds == other.numFolds;
	}
	
	public String toString() {
		return "Folded sampling with " + numFolds + " splits";
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		return Arrays.asList(new IntegerConfig.Builder(Arrays.asList(CONFIG_NUM_SAMPLES_PARAM_NAMES), DEFAULT_NUM_SAMPLES)
			.range(Range.atLeast(2))
			.description("Number of folds to split the dataset into").build());
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
		params = CollectionUtils.dropNullValues(params);
		for (Map.Entry<String, Object> kv : params.entrySet()) {
			if (CollectionUtils.containsIgnoreCase(Arrays.asList(CONFIG_NUM_SAMPLES_PARAM_NAMES), kv.getKey())) {
				if (!TypeUtils.isInt(kv.getValue())) {
					throw new IllegalArgumentException("Parameter " + CONFIG_NUM_SAMPLES_PARAM_NAMES[0] + " must be integer number, got: " + kv.getValue());
				}
				int nFold = TypeUtils.asInt(kv.getValue());
				if (nFold < 2)
					throw new IllegalArgumentException("Parameter " + CONFIG_NUM_SAMPLES_PARAM_NAMES[0] + " must be >=2");
				numFolds = nFold;
			}
		}
	}
}

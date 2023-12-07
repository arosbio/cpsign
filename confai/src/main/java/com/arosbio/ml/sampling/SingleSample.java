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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;

import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.mixins.Described;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.google.common.collect.Range;

/**
 * A static sample for a single model (Uses only the model or calibration exclusive datasets)
 * @author staffan
 *
 */
public class SingleSample implements SamplingStrategy, Described {
	public static final int ID = 5;
	public static final String NAME = "PreDefined";
	
	public int getID() {
		return ID;
	}
	
	@Override
	public String getName(){
		return NAME;
	}
	
	@Override
	public int getNumSamples() {
		return 1;
	}
	
	@Override
	public String toString() {
		return NAME;
	}
	
	@Override
	public String getDescription() {
		return "The " + NAME + " sampling strategy is used for a single, user-defined, split into proper training and calibration data. These should be specified " + 
				"by using the proper training and calibration exclusive datasets.";
	}

	@Override
	public TrainSplitGenerator getIterator(Dataset dataset) throws IllegalArgumentException {
		return getIterator(dataset, GlobalConfig.getInstance().getRNGSeed());
	}

	@Override
	public TrainSplitGenerator getIterator(Dataset dataset, long seed) throws IllegalArgumentException {
		return new SingleSampleIterator(dataset, seed);
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String,Object> props = new HashMap<>();
		props.put(PropertyNameSettings.SAMPLING_STRATEGY_KEY, ID);
		return props;
	}

	@Override
	public SamplingStrategy clone() {
		return new SingleSample();
	}

	@Override
	public boolean isFolded() {
		return false;
	}

	@Override
	public boolean isStratified() {
		return false;
	}
	
	public boolean equals(Object o) {
		return o instanceof SingleSample;
	}
	
	public static class SingleSampleIterator implements TrainSplitGenerator {
		
		private final List<DataRecord> properTrainSet, calibrationSet;
		private final Range<Double> foundRange;

		private boolean hasNext = true;
		
		public SingleSampleIterator(Dataset p, long seed) {
			this.properTrainSet = new ArrayList<>(p.getModelingExclusiveDataset());
			Collections.shuffle(properTrainSet, new Random(seed));
			this.calibrationSet = new ArrayList<>(p.getCalibrationExclusiveDataset());
			Collections.shuffle(calibrationSet, new Random(seed));

			// Find the regression label space once in case we should
			try {
				foundRange = DataUtils.findLabelRange(p);
				LOGGER.debug("found label-range: {}", foundRange);
			} catch (Exception e){
				LOGGER.debug("failed to find the observed label-range", e);
				throw new IllegalArgumentException("could not find the min and max observed values: " + e.getMessage());
			}
		}

		@Override
		public boolean hasNext() {
			return hasNext;
		}

		@Override
		public TrainSplit next() {
			TrainSplit sp = get(0);
			hasNext=false;
			return sp;
		}

		@Override
		public TrainSplit get(int index) throws NoSuchElementException {
			if (index != 0)
				throw new NoSuchElementException("index " + index + " not allowed");
			return new TrainSplit(properTrainSet, calibrationSet, foundRange);
		}

		@Override
		public int getMaxSplitIndex() {
			return 0;
		}

		@Override
		public int getMinSplitIndex() {
			return 0;
		}
		
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		return new ArrayList<>();
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
		// do nothing
	}

}

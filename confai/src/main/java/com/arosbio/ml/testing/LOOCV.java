/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.testing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.mixins.Aliased;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;

public class LOOCV implements TestingStrategy, Aliased {
	
	public static final String NAME = "LOO_CV";
	public static final String[] ALIASES = new String[] {"LeaveOneOutCV"};


	private static final Logger LOGGER = LoggerFactory.getLogger(LOOCV.class);
	
	
	private long rngSeed = GlobalConfig.getInstance().getRNGSeed();
	private boolean shuffle=true;
	
	public boolean hasDescription() {
		return true;
	}
	
	@Override
	public String getDescription() {
		return "Performs a leave-one-out cross-validation testing. I.e. for N examples - N test-train splits will be generated, "
				+ "each one with a single example in the test-set and N-1 examples in the training set. Extremely computationally demanding.";
	}
	
	@Override
	public String getName() {
		return NAME;
	}
	
	@Override
	public String[] getAliases() {
		return ALIASES;
	}
	
	@Override
	public void setSeed(long seed) {
		rngSeed = seed;
	}

	@Override
	public Long getSeed() {
		return rngSeed;
	}

	@Override
	public int getNumberOfSplitsAndValidate(Dataset data) throws IllegalArgumentException {
		if (data == null || data.getDataset().isEmpty())
			throw new IllegalArgumentException("Must supply data to split");
		return data.getDataset().size();
	}

	@Override
	public Iterator<TestTrainSplit> getSplits(Dataset data) {
		return new SplitIterator(data, shuffle, rngSeed);
	}
	
	public String toString() {
		return "Leave-one-out cross-validation";
	}

	private static class SplitIterator implements Iterator<TestTrainSplit>{
		
		private SubSet calib, proper;
		private List<DataRecord> shuffledRecords;
		
		int index=0;
		
		public SplitIterator(Dataset data, boolean shuffle, long seed) {
			calib = data.getCalibrationExclusiveDataset().clone();
			proper = data.getModelingExclusiveDataset().clone();
			
			shuffledRecords = new ArrayList<>(data.getDataset());
			if (shuffle) {
				Collections.shuffle(shuffledRecords, new Random(seed));
			}
		}

		@Override
		public boolean hasNext() {
			return index < shuffledRecords.size();
		}

		@Override
		public TestTrainSplit next() {
			if (! hasNext())
				throw new NoSuchElementException("No more test-train splits");
			
			LOGGER.debug("Generating split {}/{}", (index+1), shuffledRecords.size());
			
			Dataset trainingData = new Dataset();
			trainingData.setCalibrationExclusiveDataset(calib.clone());
			trainingData.setModelingExclusiveDataset(proper.clone());

			// Generate the training and test-set
			List<DataRecord> trainingSet = new ArrayList<>(shuffledRecords);
			trainingSet.remove(index);
			List<DataRecord> testSet = new ArrayList<>();
			testSet.add(shuffledRecords.get(index));
			index++;
			
			trainingData.setDataset(new SubSet(trainingSet));
			
			LOGGER.debug("Using {} examples for training and {} examples for testing (not counting model-exclusive or calibration-exclusive data)",
				trainingSet.size(),testSet.size());
			
			return new TestTrainSplit(trainingData,testSet);
		}
		
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		return Arrays.asList(TestStrategiesUtils.shuffleParameter);
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalArgumentException {
		for (Map.Entry<String,Object> p : params.entrySet()) {
			if (CollectionUtils.containsIgnoreCase(TestStrategiesUtils.shuffleParamNames, p.getKey())) {
				try {
					shuffle = TypeUtils.asBoolean(p.getValue());
				} catch (Exception e) {
					LOGGER.debug("Got parameter shuffle with invalid value: {}", e.getMessage());
					throw new IllegalArgumentException("Invalid input for parameter " + p.getKey() + ": " + p.getValue());
				}
			}
		}
	}

	public LOOCV clone(){
        LOOCV c = new LOOCV();
		c.shuffle = shuffle;
		c.rngSeed = rngSeed;
        return c;
    }

}

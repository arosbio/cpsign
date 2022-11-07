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
import com.arosbio.commons.config.IntegerConfig;
import com.arosbio.commons.config.NumericConfig;
import com.arosbio.commons.mixins.Aliased;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.google.common.collect.Range;

public class RandomSplit implements TestingStrategy, Aliased {

	private static final Logger LOGGER = LoggerFactory.getLogger(RandomSplit.class);
	
	public static final String NAME = "RandomSplit";
	public static final String[] ALIASES = new String[] {"TestTrainSplit"};
	
	public static final double DEFAULT_TEST_FRAC = 0.3;

	private boolean stratified = false;
	private boolean shuffle = true;
	private int numRepeat = 1;
	private Double testFraction;
	private Integer numTestExamples;
	private long rngSeed = GlobalConfig.getInstance().getRNGSeed();

	/**
	 * Default is using 0.3 as test-fraction, i.e. same as calling the {@link #RandomSplit(double)} with 0.3. 
	 */
	public RandomSplit() {
		this(DEFAULT_TEST_FRAC);
	}

	public RandomSplit(int numTestRecords) {
		this.numTestExamples = numTestRecords;
		if (numTestRecords < 1)
			throw new IllegalArgumentException("Number of test-records cannot be less than 1");
	}
	public RandomSplit(int numTestRecords, long rngSeed) {
		this(numTestRecords);
		this.rngSeed = rngSeed;
	}

	public RandomSplit(double fractionTestRecords) {
		if (fractionTestRecords <= 0 || fractionTestRecords >= 1)
			throw new IllegalArgumentException("The fraction of test-examples must be within (0..1)");
		this.testFraction = fractionTestRecords;
	}
	public RandomSplit(double fractionTestRecords, long rngSeed) {
		this(fractionTestRecords);
		this.rngSeed = rngSeed;
	}

	public boolean hasDescription() {
		return true;
	}

	public String getDescription() {
		return "Performs a single test-train split using either a specified fraction used for testing or a specified number of test-instances.";
	}

	public String getName() {
		return NAME;
	}
	
	@Override
	public String[] getAliases() {
		return ALIASES;
	}

	public boolean isStratified() {
		return stratified;
	}

	public void setStratified(boolean stratify) {
		this.stratified = stratify;
	}

	public boolean usesShuffle() {
		return shuffle;
	}

	public void setShuffle(boolean shuffle) {
		this.shuffle = shuffle;
	}

	public int getNumRepeat() {
		return numRepeat;
	}

	public void setNumRepeat(int numRepeat) {
		if (numRepeat > 0)
			this.numRepeat = numRepeat;
		else
			this.numRepeat = 1;
	}

	@Override
	public void setSeed(long seed) {
		this.rngSeed = seed;
	}

	@Override
	public Long getSeed() {
		return rngSeed;
	}

	@Override
	public int getNumberOfSplitsAndValidate(Dataset data) throws IllegalArgumentException {
		if (data == null || data.getDataset().isEmpty())
			throw new IllegalArgumentException("Must supply data to split");

		if (testFraction != null) {
			// We compute the fraction
			int numTestRecords = (int) Math.round(testFraction*data.getDataset().size());
			int numTrainingRecords = data.getDataset().size() - numTestRecords;
			if (numTestRecords < 1)
				throw new IllegalArgumentException("Invalid testing-strategy: no test-records when using test-fraction of {"+testFraction +'}');
			if (numTrainingRecords < 1)
				throw new IllegalArgumentException("Invalid testing-strategy: no training-records when using test-fraction of {"+testFraction +'}');
		} else if (numTestExamples != null) {
			// static test-partition
			if (numTestExamples < 1)
				throw new IllegalArgumentException("Number of test-records cannot be less than 1");
			if (numTestExamples >= data.getDataset().size())
				throw new IllegalArgumentException("Invalid testing-strategy: no training-records when using "+numTestExamples + " records for testing");
		} else {
			throw new IllegalArgumentException("Invalid state for " + NAME + ": must specify the fraction or number of examples that should be in the test-partition");
		}

		if (! shuffle && numRepeat > 1)
			throw new IllegalArgumentException("shuffle cannot be false if numRepeat > 1");

		return numRepeat;
	}

	@Override
	public Iterator<TestTrainSplit> getSplits(Dataset data) {
		getNumberOfSplitsAndValidate(data);
		Double fracTest = testFraction;
		if (fracTest == null) {
			fracTest = ((double) numTestExamples) / data.getDataset().size(); 
		}
		return new SplitIterator(data,fracTest,shuffle,rngSeed,stratified,numRepeat);
	}

	public String toString() {
		String baseStr = "Random "+
				(stratified? "stratified ":"")+
				"test-train split using";
		String repStr = (numRepeat>1? " repeated " + numRepeat + " times" : "");

		if (testFraction != null)
			return baseStr + " test-fraction="+testFraction + repStr;
		else
			return baseStr + " test-examples="+numTestExamples + repStr;
	}

	private static class SplitIterator implements Iterator<TestTrainSplit>{

		private final Dataset problemClone;
		private final double fractionInTest;
		private final boolean stratify, shuffle;
		private final int numRepeats;
		private final long seed;

		// Iteration state
		private int rep = 0;


		public SplitIterator(Dataset data, 
				final double fractionTest, 
				final boolean shuffle, 
				final long seed, 
				final boolean stratify,
				final int numRepeats) {

			if (!shuffle && numRepeats>1)
				throw new IllegalArgumentException("Shuffling cannot be false if number of repeated test-train splits is more than 1");
			this.problemClone = data.cloneDataOnly(); 
			this.fractionInTest = fractionTest;
			this.stratify = stratify;
			this.shuffle = shuffle;
			this.numRepeats = numRepeats;
			this.seed = seed;

		}

		@Override
		public boolean hasNext() {
			return rep < numRepeats;
		}

		@Override
		public TestTrainSplit next() {
			if (! hasNext())
				throw new NoSuchElementException("No more test-train splits");

			LOGGER.debug("Generating split 1/1, rep={}",rep);

			Dataset trainingData = new Dataset();
			trainingData.setCalibrationExclusiveDataset(problemClone.getCalibrationExclusiveDataset().clone());
			trainingData.setModelingExclusiveDataset(problemClone.getModelingExclusiveDataset().clone());
			List<DataRecord> testSet = null;

			// STRATIFIED
			if (stratify) {
				List<List<DataRecord>> strataRecs = DataUtils.stratify(problemClone.getDataset());

				// Shuffle
				if (shuffle) {
					// Shuffle each strata list 
					for (List<DataRecord> recs : strataRecs) {
						Collections.shuffle(recs, new Random(seed+rep));
					}
				}

				testSet = new ArrayList<>();
				List<DataRecord> trainingSet = new ArrayList<>();

				// Do the splitting
				for (List<DataRecord> strata : strataRecs) {
					int splitIndex = (int) (strata.size() * fractionInTest);
					testSet.addAll(strata.subList(0, splitIndex));
					trainingSet.addAll(strata.subList(splitIndex, strata.size()));
				}

				// Shuffle the lists (so not arranged in order of their labels)
				Collections.shuffle(trainingSet, new Random(seed+rep));
				Collections.shuffle(testSet, new Random(seed+rep));
				
				trainingData.setDataset(new SubSet(trainingSet));
			}

			// NON-STRATIFIED
			else {

				List<DataRecord> recs = new ArrayList<>(problemClone.getDataset());

				// Shuffle
				if (shuffle) {
					Collections.shuffle(recs, new Random(seed+rep));
				}

				// Split into two disjoint sets
				int splitIndex = (int) (recs.size() * fractionInTest);
				testSet = new ArrayList<>(recs.subList(0, splitIndex));
				List<DataRecord> trainingSet = new ArrayList<>(recs.subList(splitIndex, recs.size()));

				trainingData.setDataset(new SubSet(trainingSet));
			}


			LOGGER.debug("Using {} examples for training and {} examples for testing (not counting model-exclusive or calibration-exclusive data)",
				trainingData.getDataset().size(),testSet.size());

			// Increase the counter
			rep ++;

			return new TestTrainSplit(trainingData,testSet);
		}



	}

	public static final String[] CONFIG_TEST_FRACTION_PARAM_NAMES = new String[] {"fraction", "testFraction"};
	public static final String[] CONFIG_TEST_NUMBER_OF_INSTANCES_PARAM_NAMES = new String[] {"numTest"};

	@Override
	public List<ConfigParameter> getConfigParameters() {
		return Arrays.asList(new NumericConfig.Builder(Arrays.asList(CONFIG_TEST_FRACTION_PARAM_NAMES), DEFAULT_TEST_FRAC).range(Range.open(0d, 1d)).build(),
			new IntegerConfig.Builder(Arrays.asList(CONFIG_TEST_NUMBER_OF_INSTANCES_PARAM_NAMES), -1).range(Range.atLeast(1)).build(),
			TestStrategiesUtils.shuffleParameter,
			TestStrategiesUtils.numRepParameter,
			TestStrategiesUtils.stratifiedParameter);
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
		Double inputRatio = null;
		Integer inputNumTest=null;
		for (Map.Entry<String, Object> kv : params.entrySet()) {

			try {
				// Test ratio / number
				if (CollectionUtils.containsIgnoreCase(CONFIG_TEST_FRACTION_PARAM_NAMES, kv.getKey())) {
					inputRatio = TypeUtils.asDouble(kv.getValue());
				} else if (CollectionUtils.containsIgnoreCase(CONFIG_TEST_NUMBER_OF_INSTANCES_PARAM_NAMES, kv.getKey())) {
					inputNumTest = TypeUtils.asInt(kv.getValue());
				}

				// shuffle
				else if (CollectionUtils.containsIgnoreCase(TestStrategiesUtils.shuffleParamNames, kv.getKey())) {
					setShuffle(TypeUtils.asBoolean(kv.getValue()));
				} 

				// num reps
				else if (CollectionUtils.containsIgnoreCase(TestStrategiesUtils.numRepParamNames, kv.getKey())) {
					setNumRepeat(TypeUtils.asInt(kv.getValue()));
				} 

				// stratified
				else if (CollectionUtils.containsIgnoreCase(TestStrategiesUtils.stratifiedParamNames, kv.getKey())) {
					setStratified(TypeUtils.asBoolean(kv.getValue()));
				} 

			} catch (Exception e) {
				throw new IllegalArgumentException("Invalid input for parameter " + kv.getKey() + ": " + kv.getValue());
			}

		}

		if (inputNumTest!=null && inputRatio!=null) {
			throw new IllegalArgumentException("Argument " + CONFIG_TEST_NUMBER_OF_INSTANCES_PARAM_NAMES[0] + " and " + CONFIG_TEST_FRACTION_PARAM_NAMES[0] + " cannot be given at the same time");
		}
		if (inputRatio != null) {
			if (inputRatio <= 0 || inputRatio >= 1) {
				throw new IllegalArgumentException("Argument " + CONFIG_TEST_FRACTION_PARAM_NAMES[0] + " must be in the range "+Range.open(0, 1));
			}
			testFraction = inputRatio;
			numTestExamples = null;
		}
		if (inputNumTest !=null) {
			if (inputNumTest < 1) {
				throw new IllegalArgumentException("Argument " + CONFIG_TEST_NUMBER_OF_INSTANCES_PARAM_NAMES + " cannot be smaller than 1");
			}
			testFraction = null;
			numTestExamples = inputNumTest;
		}

	}

	public RandomSplit clone(){
        RandomSplit c = new RandomSplit();
		c.stratified = stratified;
		c.shuffle = shuffle;
		c.numRepeat = numRepeat;
		c.testFraction = testFraction;
		c.numTestExamples = numTestExamples;
		c.rngSeed = rngSeed;
        return c;
    }

}

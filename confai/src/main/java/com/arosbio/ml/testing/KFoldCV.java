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
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;

/**
 * TestRunner - k-Fold cross-validation 
 * 
 * @author staffan
 * @author Aros bio
 */
public class KFoldCV implements TestingStrategy {

	public static final String STRATEGY_NAME = "KFoldCV";
	private static final Logger LOGGER = LoggerFactory.getLogger(KFoldCV.class);
	public static final int DEFAULT_K = 10;

	private boolean stratified = false;
	private boolean shuffle = true;
	private int numRepeat = 1;
	private int numFolds = DEFAULT_K;
	private long rngSeed = GlobalConfig.getInstance().getRNGSeed();

	/**
	 * Default constructor, uses <code>k=10</code> and the default global seed from {@link GlobalConfig}
	 */
	public KFoldCV() {
	}

	/**
	 * Set the <code>k</code> parameter for the number of folds, and use the default global seed from {@link GlobalConfig}
	 * @param k number of folds, <code>k&gt;=2</code>
	 * @throws IllegalArgumentException if <code>k&lt;2</code>
	 */
	public KFoldCV(int k) {
		if (k < 2)
			throw new IllegalArgumentException("k cannot be less than 2 in k-fold CV");
		this.numFolds = k;
	}

	public KFoldCV(int k, long seed) {
		this(k);
		rngSeed = seed;
	}

	public KFoldCV(int k, boolean stratify) {
		this(k);
		this.stratified = stratify;
	}

	public boolean hasDescription() {
		return true;
	}

	public String getDescription() {
		return "k-fold cross-validation. Randomize the order of the data, splits the full dataset into k disjoint folds and iteratively use one fold for test-set and remaining (k-1) folds for training.";
	}

	public String getName() {
		return STRATEGY_NAME;
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

	public int getNumFolds() {
		return numFolds;
	}

	/**
	 * Set the number of folds, must be &ge;2
	 * @param numFolds num folds, if a value &le;2 is given, it will be set to the default (10)
	 */
	public void setNumFolds(int numFolds) {
		if (numFolds < 2)
			throw new IllegalArgumentException("Num folds in "+STRATEGY_NAME + " must be >=2, got '"+numFolds+'\'');
		this.numFolds = numFolds;
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
	public int getNumberOfSplitsAndValidate(Dataset data) 
			throws IllegalArgumentException {
		if (data.getDataset().size() < numFolds)
			throw new IllegalArgumentException("Cannot run k-Fold CV with more folds than records!");
		if (! shuffle && numRepeat > 1)
			throw new IllegalArgumentException("shuffle cannot be false if numRepeat > 1");
		return numFolds * numRepeat;
	}

	@Override
	public Iterator<TestTrainSplit> getSplits(Dataset data) {
		return new CVFoldsIterator(data, numFolds, shuffle, rngSeed, stratified, numRepeat);
	}

	public String toString() {
		return String.format("%s-fold %scross-validation%s", numFolds,
				(stratified? "stratified ":""),
				(numRepeat>1? " repeated " + numRepeat + " times" : ""));
	}

	private static class CVFoldsIterator implements Iterator<TestTrainSplit>{

		private final Dataset problemClone;

		private final int numberOfDataRecords;
		private final boolean stratify, shuffle;
		private final int numRepeats, numFolds;
		private final long seed;

		// Iteration state
		private List<List<DataRecord>> folds;
		private int fold = 0;
		private int rep = 0;

		public CVFoldsIterator(final Dataset data, 
				final int numFolds, 
				final boolean shuffle, 
				final long seed, 
				final boolean stratify, 
				final int numRepeats) {

			if (data.getDataset().size() < numFolds) {
				throw new IllegalArgumentException("Cannot run k-Fold CV with more folds than records!");
			}
			if (numFolds < 2)
				throw new IllegalArgumentException("Number of folds must be >= 2");
			if (!shuffle && numRepeats>1)
				throw new IllegalArgumentException("Shuffling cannot be false if number of repeated k-fold runs is more than 1");
			this.numFolds = numFolds;
			this.stratify = stratify;
			this.shuffle = shuffle;
			this.numRepeats = numRepeats;
			this.seed = seed;
			this.problemClone = data.cloneDataOnly(); 
			this.numberOfDataRecords = data.getDataset().size();

			setupFolds();
		}

		private void setupFolds() {

			if (stratify) {
				List<List<DataRecord>> strataRecs = DataUtils.stratify(problemClone.getDataset());

				// Init the folds
				folds = new ArrayList<>();
				for (int i=0; i<numFolds; i++)
					folds.add(new ArrayList<>());

				// Shuffle
				if (shuffle) {
					// Shuffle each strata list 
					for (List<DataRecord> recs : strataRecs) {
						Collections.shuffle(recs, new Random(seed+rep));
					}
				}

				// split the stratified datasets into the folds
				for (List<DataRecord> recs : strataRecs) {
					List<List<DataRecord>> foldStrata = CollectionUtils.getDisjunctSets(recs, numFolds); 
					for(int i=0; i<numFolds; i++) {
						folds.get(i).addAll(foldStrata.get(i));
					}
				}

				// Shuffle the folds (so not arranged in order of their labels)
				for (List<DataRecord> f : folds) {
					Collections.shuffle(f, new Random(seed+rep));
				}

			} else {
				List<DataRecord> recs = new ArrayList<>(problemClone.getDataset());

				if (shuffle)
					Collections.shuffle(recs, new Random(seed+rep));
				folds = CollectionUtils.getDisjunctSets(recs, numFolds);
			}

			fold = 0; // reset the fold-index
		}

		@Override
		public boolean hasNext() {
			// If more folds for the current repetition
			if (fold < folds.size())
				return true;
			// finished the current rep - start new
			rep ++;
			// Check if there are more reps
			if (rep < numRepeats) {
				setupFolds();
				return true;
			}
			// No more reps 
			return false;
		}

		@Override
		public TestTrainSplit next() throws NoSuchElementException {
			if (! hasNext())
				throw new NoSuchElementException("No more folds!");

			LOGGER.debug("Generating fold {}/{}",(fold+1),folds.size());

			Dataset trainingData = new Dataset();
			trainingData.setCalibrationExclusiveDataset(problemClone.getCalibrationExclusiveDataset().clone());
			trainingData.setModelingExclusiveDataset(problemClone.getModelingExclusiveDataset().clone());

			List<DataRecord> trainingSet = new ArrayList<>(numberOfDataRecords);
			List<DataRecord> testSet = null;
			for (int i=0; i<folds.size(); i++) {
				if (i == fold) {
					testSet = folds.get(i);
				} else {
					trainingSet.addAll(folds.get(i));
				}
			}

			trainingData.setDataset(new SubSet(trainingSet));

			LOGGER.debug("Using {} examples for training and {} examples for testing (not counting model-exclusive or calibration-exclusive data)",
				trainingSet.size(),testSet.size());
			// Update the fold for next calls to next()
			fold++;

			return new TestTrainSplit(trainingData,testSet);
		}

	}

	public static final String[] K_PARAM_NAMES = new String[] {"numSplits","folds", "k"};

	@Override
	public List<ConfigParameter> getConfigParameters() {
		return ImmutableList.of(
			new IntegerConfig.Builder(Arrays.asList(K_PARAM_NAMES), DEFAULT_K)
				.range(Range.atLeast(2)).description("Number of folds to use").build(),
			TestStrategiesUtils.shuffleParameter,
			TestStrategiesUtils.numRepParameter,
			TestStrategiesUtils.stratifiedParameter);
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalArgumentException {
		for (Map.Entry<String, Object> kv : params.entrySet()) {
			try {
				// Folds
				if (CollectionUtils.containsIgnoreCase(K_PARAM_NAMES, kv.getKey())) {
					setNumFolds(TypeUtils.asInt(kv.getValue()));
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

			} catch (IllegalArgumentException e) {
				// Pass along
				throw e;
			} catch (Exception e) {
				throw new IllegalArgumentException("Invalid input for parameter " + kv.getKey() + ": " + kv.getValue());
			}
		}

	}

	public KFoldCV clone(){
        KFoldCV c = new KFoldCV();
		c.stratified = stratified;
		c.shuffle = shuffle;
		c.numRepeat = numRepeat;
		c.numFolds = numFolds;
		c.rngSeed = rngSeed;
        return c;
    }

}
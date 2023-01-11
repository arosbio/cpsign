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

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.IntegerConfig;
import com.arosbio.data.Dataset;
import com.arosbio.data.splitting.FoldedSplitter;
import com.arosbio.ml.testing.utils.TestStrategiesUtils;
import com.arosbio.ml.testing.utils.TestTrainWrapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;

/**
 * k-fold cross-validation 
 * 
 * @author staffan
 * @author Aros bio
 */
public class KFoldCV implements TestingStrategy {

	public static final String STRATEGY_NAME = "KFoldCV";
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

	public KFoldCV withStratified(boolean stratify) {
		this.stratified = stratify;
		return this;
	}

	public boolean usesShuffle() {
		return shuffle;
	}

	public KFoldCV withShuffle(boolean shuffle) {
		this.shuffle = shuffle;
		return this;
	}

	public int getNumRepeat() {
		return numRepeat;
	}

	public KFoldCV withNumRepeat(int numRepeat) {
		if (numRepeat > 0)
			this.numRepeat = numRepeat;
		else
			this.numRepeat = 1;
		return this;
	}

	public int getNumFolds() {
		return numFolds;
	}

	/**
	 * Set the number of folds, must be &ge;2
	 * @param numFolds num folds, if a value &le;2 is given, it will be set to the default (10)
	 */
	public KFoldCV withNumFolds(int numFolds) {
		if (numFolds < 2)
			throw new IllegalArgumentException("Num folds in "+STRATEGY_NAME + " must be >=2, got '"+numFolds+'\'');
		this.numFolds = numFolds;
		return this;
	}

	@Override
	public Long getSeed() {
		return rngSeed;
	}

	@Override
	public void setSeed(long seed) {
		this.rngSeed = seed;
	}

	public KFoldCV withSeed(long seed){
		this.rngSeed = seed;
		return this;
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
		return new TestTrainWrapper(new FoldedSplitter.Builder()
			.seed(rngSeed)
			.name(STRATEGY_NAME)
			.numFolds(numFolds)
			.shuffle(shuffle)
			.stratify(stratified)
			.numRepeat(numRepeat)
			.findLabelRange(false)
			.build(data));
	}

	public String toString() {
		return String.format("%s-fold %scross-validation%s", numFolds,
				(stratified? "stratified ":""),
				(numRepeat>1? " repeated " + numRepeat + " times" : ""));
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
		Map<String, Object> noNullParams = CollectionUtils.dropNullValues(params);
		for (Map.Entry<String, Object> kv : noNullParams.entrySet()) {
			try {
				// Folds
				if (CollectionUtils.containsIgnoreCase(K_PARAM_NAMES, kv.getKey())) {
					withNumFolds(TypeUtils.asInt(kv.getValue()));
				} 
				// shuffle
				else if (CollectionUtils.containsIgnoreCase(TestStrategiesUtils.shuffleParamNames, kv.getKey())) {
					withShuffle(TypeUtils.asBoolean(kv.getValue()));
				} 

				// num reps
				else if (CollectionUtils.containsIgnoreCase(TestStrategiesUtils.numRepParamNames, kv.getKey())) {
					withNumRepeat(TypeUtils.asInt(kv.getValue()));
				} 

				// stratified
				else if (CollectionUtils.containsIgnoreCase(TestStrategiesUtils.stratifiedParamNames, kv.getKey())) {
					withStratified(TypeUtils.asBoolean(kv.getValue()));
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
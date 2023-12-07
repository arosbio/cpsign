/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.splitting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.MathUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.ml.sampling.TrainSplit;
import com.google.common.collect.Range;

public class RandomSplitter implements DataSplitter {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(RandomSplitter.class);
	
	private final Dataset dataClone;
	private final int numSamples;
	private final long seed;
	private final boolean stratify;
	private final Integer numInSecond;
	private Range<Double> foundRange;

	// Iteration state
	private int currentSplitIndex=0;

	/**
	 * Helper object that holds the stratified records and manages
	 * the random partition if {@code stratify} is {@code true}
	 */
	private StratifiedSplitterHelper helper;

	public static class Builder {
        private int numSamples = 10;
		private Double splitRatio = .2;
		private Integer splitNum = null;
        private boolean shuffle = true;
        private long seed = GlobalConfig.getInstance().getRNGSeed(); 
        private boolean stratify = false;
        private boolean findObservedLabelSpace = false;
        private String name = "Random split";

        public int numSplits(){
            return numSamples;
        }
        public Builder numSplits(int num){
            if (num < 1){
                throw new IllegalArgumentException("Invalid number of splits: " + num + ", must be >=1");
            }
            this.numSamples = num;
            return this;
        }
		public Double splitRatio(){
			return splitRatio;
		}
		/**
		 * Set the ratio that should be in the <b>second split</b>
		 * @param ratio the ratio of instances to take from the {@link Dataset#getDataset()} subset of data
		 * @return the same builder instance
		 */
		public Builder splitRatio(Double ratio){
			this.splitRatio = ratio;
			return this;
		}
		
		public Integer splitNumInstances(){
			return splitNum;
		}
		/**
		 * The explicit number of instances to put in the <b>second split(s)</b>
		 * @param num the number of instances that should be in the second split(s)
		 * @return the same builder instance
		 */
		public Builder splitNumInstances(Integer num){
			this.splitNum = num;
			return this;
		}
        public boolean shuffle(){
            return shuffle;
        }
        public Builder shuffle(boolean shuffle){
            this.shuffle = shuffle;
            return this;
        }
        public long seed(){
            return seed;
        }
        public Builder seed(long seed){
            this.seed = seed;
            return this;
        }
        public boolean stratify(){
            return stratify;
        }
        public Builder stratify(boolean stratify){
            this.stratify = stratify;
            return this;
        }
        public boolean findLabelRange(){
            return findObservedLabelSpace;
        }
        public Builder findLabelRange(boolean findRange){
            this.findObservedLabelSpace = findRange;
            return this;
        }
        public String name(){
            return name;
        }
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        public RandomSplitter build(Dataset data){
            return new RandomSplitter(this, data); 
        }
    }

	private RandomSplitter(final Builder b, final Dataset dataset) {

		Objects.requireNonNull(dataset, "data must not be null");
		if (dataset.getDataset().isEmpty())
			throw new IllegalArgumentException("Training data is empty - cannot split it");
		this.dataClone = dataset;
		this.numSamples = b.numSamples;
		this.seed=b.seed;
		this.stratify = b.stratify;

		// Set the number of instances that should be in the second split
		if (b.splitNum == null && b.splitRatio == null){
			throw new IllegalArgumentException("Either an explicit number or a ratio must be given for number of instances in the splits");
		}
		if (b.splitNum != null){
			// explicit number given
			this.numInSecond = b.splitNum;
		} else {
			this.numInSecond = (int) (dataClone.getDataset().size()*b.splitRatio);
		}
		if (stratify){
			LOGGER.debug("setting up the stratas for future splitting");
			helper = new StratifiedSplitterHelper();
		}

		if (b.findObservedLabelSpace){
            try{
                findObservedLabelSpace();
            } catch (Exception e){
                LOGGER.debug("attempted to find label-space but failed: {}",e.getMessage());
            }
        }
	}

	private void findObservedLabelSpace(){
        // Find the regression label space once in case we should
        try {
            foundRange = DataUtils.findLabelRange(dataClone);
            LOGGER.debug("found label-range: {}", foundRange);
        } catch (Exception e){
            LOGGER.debug("failed to find the observed label-range", e);
            throw new IllegalArgumentException("could not find the min and max observed values: " + e.getMessage());
        }
    }

	@Override
	public boolean hasNext() {
		return currentSplitIndex < numSamples;
	}

	@Override
	public DataSplit next() {
		if (! hasNext()){
			throw new NoSuchElementException("No more splits available");
		}
		try {
			return stratify ? helper.getRandomSplit(currentSplitIndex) : getRandomSplit(currentSplitIndex); 
		} finally {
			currentSplitIndex++;
		}
		
	}

	@Override
	public void remove() {
		currentSplitIndex++;
	}
	
	/**
	 * Get a specific index, will not interfere with the {@link #hasNext()} or {@link #next()} methods 
	 * @param index The index <code>[0,num models)</code>
	 * @return the {@link TrainSplit} for the <code>index</code>
	 */
	public DataSplit get(int index) throws NoSuchElementException{
		if (index < 0 || index >= numSamples)
			throw new NoSuchElementException("Cannot get index "+index+", the only allowed indexes are [0,"+(numSamples-1)+"]");
		return stratify ? helper.getRandomSplit(index) : getRandomSplit(index); 
	}

	private class StratifiedSplitterHelper {

		private final List<List<DataRecord>> stratas;
		private final List<Integer> numFromEachStrata;

		private StratifiedSplitterHelper(){
			stratas = DataUtils.stratify(dataClone.getDataset());
			numFromEachStrata = new ArrayList<>();
			
			// deduce number to take from each strata
			double fracSecond = ((double)numInSecond)/dataClone.getDataset().size();
			int numAdded = 0;
			for (List<DataRecord> strata : stratas) {
				int n = (int) (fracSecond*strata.size());
				numFromEachStrata.add(n);
				numAdded += n;
			}
			if (numAdded < numInSecond){
				// distribute the remaining examples
				List<Integer> cpy = new ArrayList<>(numFromEachStrata);
				while (numAdded < numInSecond){
					int indexToAddTo = MathUtils.findMaxIndex(cpy);
					numFromEachStrata.set(indexToAddTo, numFromEachStrata.get(indexToAddTo)+1);
					numAdded++;
					cpy.set(indexToAddTo, 0); // make sure not to add to the same strate multiple times
				}
			}

			if (LOGGER.isDebugEnabled()){
				// Log some info
				StringBuffer sb = new StringBuffer();
				sb.append("Class-fractions (normal ds only): ");
				for (List<DataRecord> strata : stratas) {
					sb.append(strata.get(0).getLabel())
						.append('=')
						.append(strata.size())
						.append(' ');
				}
				LOGGER.debug(sb.toString());
			}

		}

		private DataSplit getRandomSplit(int index) {
			long currentSeed = seed+index;
			// Init lists for records 
			List<DataRecord> firstSplit = new ArrayList<>(dataClone.getDataset().size() - numInSecond);
			List<DataRecord> secondSplit = new ArrayList<>(numInSecond);

			for (int i=0; i<stratas.size(); i++) {
				// Split each strata using the same seed
				Pair<List<DataRecord>, List<DataRecord>> splits = CollectionUtils.splitRandomly(stratas.get(i), numFromEachStrata.get(i), currentSeed);
				firstSplit.addAll(splits.getLeft());
				secondSplit.addAll(splits.getRight());
			}

			Dataset first = new Dataset()
				.withDataset(new SubSet(firstSplit))
				.withModelingExclusiveDataset(dataClone.getModelingExclusiveDataset())
				.withCalibrationExclusiveDataset(dataClone.getCalibrationExclusiveDataset());
			
			// Make sure to shuffle the first and second split, they are now in blocks of the same label
			first.getDataset().shuffle(currentSeed);
			Collections.shuffle(secondSplit, new Random(currentSeed));

			LOGGER.debug("Num in first split: {}, num in second split: {}", firstSplit.size(), secondSplit.size());

			return new DataSplit(first, secondSplit, currentSeed, foundRange);
		}

	}
	
	/**
	 * Handles getting the next random split when <b>not</b> using stratify
	 * @param index
	 * @return a {@link DataSplit} with data
	 */
	private DataSplit getRandomSplit(int index) {
		long currentSeed = seed+index;

		LOGGER.debug("generating random (non-stratified) split with {} instances taken out of total size {}", numInSecond, dataClone.getDataset().size());

		Pair<List<DataRecord>, List<DataRecord>> splits = CollectionUtils.splitRandomly(dataClone.getDataset(), numInSecond, currentSeed);

		Dataset first = new Dataset()
			.withDataset(new SubSet(splits.getLeft()))
			.withModelingExclusiveDataset(dataClone.getModelingExclusiveDataset())
			.withCalibrationExclusiveDataset(dataClone.getCalibrationExclusiveDataset());

		// put together the second split
		List<DataRecord> second = splits.getRight();
		
		return new DataSplit(first, second, currentSeed, foundRange);
	}
	
	@Override
	public int getMinSplitIndex() {
		return 0;
	}

	@Override
	public int getMaxSplitIndex() {
		return numSamples-1;
	}

}


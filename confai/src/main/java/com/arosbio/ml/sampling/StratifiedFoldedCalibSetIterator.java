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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.GlobalConfig;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;

public class StratifiedFoldedCalibSetIterator implements TrainSplitIterator{
	private static final Logger LOGGER = LoggerFactory.getLogger(StratifiedFoldedCalibSetIterator.class);

	private final Dataset dataset;
	private final Map<Double,List<DataRecord>> recs = new HashMap<>();
	private final Map<Double,List<Integer>> splits = new HashMap<>();
	private final int totalNumFolds;
	private final long seed;

	
	private int nextFold=0;
	

	public StratifiedFoldedCalibSetIterator(Dataset problem,
			int folds, long seed) throws IllegalArgumentException {
		this.totalNumFolds = folds;
		this.dataset = problem;

		this.seed = seed;
		
		// Split into a separate list for each label
		for (DataRecord rec: dataset.getDataset()) {
			if (! recs.containsKey(rec.getLabel())) {
				LOGGER.debug("Found new label when splitting dataset={}",rec.getLabel());
				recs.put(rec.getLabel(), new ArrayList<DataRecord>());
			}
			recs.get(rec.getLabel()).add(rec);
		}
		
		// If more than 2 classes
		if (recs.size()>2)
			throw new IllegalArgumentException("Stratified sampling is only supported for binary classification - you've entered dataset with "+ recs.size() + " classes");
		
		// Shuffle the lists + debug class info + decide splits
		StringBuffer sb = new StringBuffer();
		sb.append("Class-fractions (normal ds only): ");
		for (Map.Entry<Double, List<DataRecord>> entry: recs.entrySet()) {
			sb.append(entry.getKey());
			sb.append('=');
			sb.append(entry.getValue().size());
			sb.append(' ');
			Collections.shuffle(entry.getValue(), new Random(seed));
			
			// Decide splits for current label
			int defaultFoldSize = (int) Math.floor(((double)entry.getValue().size())/folds);
			if (defaultFoldSize < 1)
				throw new IllegalArgumentException("Using " + folds + " folds generate a fold with no examples of one of the classes");
			
			int recordsLeftToAssign = entry.getValue().size() - folds*defaultFoldSize;

			int currentSplitIndex = 0;
			List<Integer> indexSplits = new ArrayList<>();
			indexSplits.add(currentSplitIndex);
			for(int i=0; i<folds-1; i++){
				currentSplitIndex += defaultFoldSize;
				if(recordsLeftToAssign>0)
					currentSplitIndex++;
				recordsLeftToAssign--;
				indexSplits.add(currentSplitIndex);
			}
			indexSplits.add(entry.getValue().size());
			LOGGER.debug("Index-splits for class={}: {}",entry.getKey(),indexSplits);
			splits.put(entry.getKey(),indexSplits);
			
		}
		LOGGER.debug(sb.toString());
	}
	
	public StratifiedFoldedCalibSetIterator(Dataset problem,
			int folds) throws IllegalArgumentException {
		this(problem,folds,GlobalConfig.getInstance().getRNGSeed());
	}
	
	public Dataset getProblem() {
		return dataset;
	}
	
	public int getNumFolds() {
		return totalNumFolds;
	}

	@Override
	public boolean hasNext() {
		return nextFold < totalNumFolds;
	}

	@Override
	public TrainSplit next() throws NoSuchElementException {
		if (!hasNext())
			throw new NoSuchElementException("No more folds left");
		return foldedCalibrationSet(nextFold++);
	}
	
	/**
	 * Get a specific fold, will not interfere with the {@link #hasNext()} or {@link #next()} methods 
	 * @param fold The fold <code>[0,num folds)</code>
	 * @return the {@link TrainSplit} for the <code>fold</code>
	 */
	public TrainSplit get(int fold) throws NoSuchElementException{
		if (fold <0 || fold>=totalNumFolds)
			throw new NoSuchElementException("Cannot get fold "+fold+", the only allowed folds are [0,"+(totalNumFolds-1)+"]");
		return foldedCalibrationSet(fold);
	}

	@Override
	public void remove() {
		nextFold++;
	}

	private TrainSplit foldedCalibrationSet(int fold) {

		// Calibration set is the current fold
		List<DataRecord> calibrationSet = new ArrayList<>(); 
		
		for (Double label: recs.keySet()) {
			calibrationSet.addAll(recs.get(label).subList(
					splits.get(label).get(fold), splits.get(label).get(fold+1)));
		}
		
		if (! dataset.getCalibrationExclusiveDataset().isEmpty())
			calibrationSet.addAll(dataset.getCalibrationExclusiveDataset());
		calibrationSet = CalibrationSetUtils.shuffleList(calibrationSet, seed);

		// Remaining data should be in the proper training set
		List<DataRecord> properTrainingSet = new ArrayList<>();
		
		for (Double label: recs.keySet()) {
			properTrainingSet.addAll(recs.get(label).subList(
					0, splits.get(label).get(fold)));
			properTrainingSet.addAll(recs.get(label).subList(
					splits.get(label).get(fold+1), recs.get(label).size()));
		}
		if (! dataset.getModelingExclusiveDataset().isEmpty())
			properTrainingSet.addAll(dataset.getModelingExclusiveDataset());
		properTrainingSet = CalibrationSetUtils.shuffleList(properTrainingSet, seed);

		return new TrainSplit(properTrainingSet, calibrationSet);
	}

	@Override
	public int getMaximumSplitIndex() {
		return totalNumFolds-1;
	}

	@Override
	public int getMinimumSplitIndex() {
		return 0;
	}
}

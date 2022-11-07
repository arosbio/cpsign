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
import java.util.List;
import java.util.NoSuchElementException;

import com.arosbio.commons.GlobalConfig;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;

public class FoldedCalibSetIterator implements TrainSplitIterator {

	private final Dataset dataset;
	private final int totalNumberOfFolds;
	private final long seed;

	private int currentFold=0;
	private List<Integer> indexSplits;

	public FoldedCalibSetIterator(Dataset problem,
			int folds, long seed) {
		this.totalNumberOfFolds = folds;
		this.seed = seed;

		dataset = problem.clone(); // clone to make sure the shuffling doesn't mess things up
		dataset.shuffle(seed);

		// Decide the start and end-indexes 
		int defaultFoldSize = (int) Math.floor(((double)problem.getDataset().size())/folds);
		if (defaultFoldSize < 1)
			throw new IllegalArgumentException("Using " + folds + " folds on a dataset of size " + problem.getDataset().size() + " give less than 1 record per fold");
		int recordsLeftToAssign = problem.getDataset().size() - folds*defaultFoldSize;

		int currentSplitIndex = 0;
		indexSplits = new ArrayList<>();
		indexSplits.add(currentSplitIndex);
		for(int i=0; i<folds-1; i++){
			currentSplitIndex += defaultFoldSize;
			if(recordsLeftToAssign>0)
				currentSplitIndex++;
			recordsLeftToAssign--;
			indexSplits.add(currentSplitIndex);
		}
		indexSplits.add(problem.getDataset().size());
	}
	
	public FoldedCalibSetIterator(Dataset problem, int folds) {
		this(problem,folds,GlobalConfig.getInstance().getRNGSeed());
	}
	
	public Dataset getProblem() {
		return dataset;
	}

	@Override
	public boolean hasNext() {
		return currentFold < totalNumberOfFolds;
	}

	@Override
	public TrainSplit next() {
		if (!hasNext())
			throw new NoSuchElementException("No more folds left");
		return foldedCalibrationSet(currentFold++);
	}

	@Override
	public void remove() {
		currentFold++;
	}
	
	/**
	 * Get a specific fold, will not interfere with the {@link #hasNext()} or {@link #next()} methods 
	 * @param fold The fold <code>[0,num folds)</code>
	 * @return The {@link TrainSplit} for the given <code>fold</code>
	 */
	public TrainSplit get(int fold) throws NoSuchElementException{
		if (fold <0 || fold>=totalNumberOfFolds)
			throw new NoSuchElementException("Cannot get fold "+fold+", the only allowed folds are [0,"+(totalNumberOfFolds-1)+"]");
		return foldedCalibrationSet(fold);
	}
	
	private TrainSplit foldedCalibrationSet(int fold) {
		int foldStartIndex = indexSplits.get(fold);
		int foldEndIndex = indexSplits.get(fold+1);
		int foldSize = foldEndIndex - foldStartIndex;

		// Calibration set is the current fold
		List<DataRecord> calibrationSet = new ArrayList<>(dataset.getDataset() .subList(foldStartIndex, foldEndIndex));

		// Remaining data should be in the proper training set
		List<DataRecord> properTrainingSet = new ArrayList<>(dataset.getDataset().size()-foldSize+dataset.getModelingExclusiveDataset().size());

		properTrainingSet.addAll(dataset.getDataset() .subList(0, foldStartIndex));
		properTrainingSet.addAll(dataset.getDataset() .subList(foldEndIndex, dataset.getDataset() .size()));
		
		// Add the additional data from exclusive calib/modeling datasets
		if (! dataset.getModelingExclusiveDataset().isEmpty()) {
			properTrainingSet.addAll(dataset.getModelingExclusiveDataset() );
			properTrainingSet = CalibrationSetUtils.shuffleList(properTrainingSet, seed);
		} if (! dataset.getCalibrationExclusiveDataset().isEmpty()) {
			calibrationSet.addAll(dataset.getCalibrationExclusiveDataset() );
			calibrationSet = CalibrationSetUtils.shuffleList(calibrationSet, seed);
		}

		return new TrainSplit(properTrainingSet, calibrationSet);
	}

	@Override
	public int getMaximumSplitIndex() {
		return totalNumberOfFolds-1;
	}

	@Override
	public int getMinimumSplitIndex() {
		return 0;
	}

}

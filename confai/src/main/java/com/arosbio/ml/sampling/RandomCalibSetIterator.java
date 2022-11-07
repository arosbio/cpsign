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
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.GlobalConfig;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;

public class RandomCalibSetIterator implements TrainSplitIterator {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(RandomCalibSetIterator.class);
	
	private final Dataset dataset;
	private final double calibrationRatio;
	private final int nrModels;
	private final long seed;

	private int currentSplitIndex=0;

	public RandomCalibSetIterator(Dataset problem,
			double calibrationRatio, int nrModels, long seed) {
		this.dataset = problem;
		this.calibrationRatio = calibrationRatio;
		this.nrModels = nrModels;
		this.seed=seed;
	}
	
	public RandomCalibSetIterator(Dataset problem,
			double calibrationRatio, int nrModels) {
		this(problem,calibrationRatio,nrModels,GlobalConfig.getInstance().getRNGSeed());
	}
	
	public Dataset getProblem() {
		return dataset;
	}

	@Override
	public boolean hasNext() {
		return currentSplitIndex < nrModels;
	}

	@Override
	public TrainSplit next() {
		return getRandomTrainingsetSplit(currentSplitIndex++); 
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
	public TrainSplit get(int index) throws NoSuchElementException{
		if (index <0 || index>=nrModels)
			throw new NoSuchElementException("Cannot get index "+index+", the only allowed indexes are [0,"+(nrModels-1)+"]");
		return getRandomTrainingsetSplit(index);
	}
	
	private TrainSplit getRandomTrainingsetSplit(int index) {

		int calibrationSize = (int) (dataset.getDataset().size()*calibrationRatio);
		LOGGER.debug("total datasetsize="+dataset.getDataset().size()+", calibration size="+calibrationSize);

		//Make a copy of the training set to proper training and properActivites
		List<DataRecord> properTrainingSet=new ArrayList<>(dataset.getDataset());
		//Set up calibration set
		List<DataRecord> calibrationSet = new ArrayList<>(calibrationSize+dataset.getCalibrationExclusiveDataset().size());

		//Draw calibrationSize observation from properTraining to calibrationSet
		Random generator = new Random(seed+index);
		for (int i=0; i< calibrationSize; i++){

			int pickIX = generator.nextInt(properTrainingSet.size());

			//Move this observation from properTraining to Calibration
			calibrationSet.add(properTrainingSet.remove(pickIX));
		}
		
		// Add exclusive calib/modeling datasets and do further randomization if needed
		if (! dataset.getModelingExclusiveDataset().isEmpty()) {
			properTrainingSet.addAll(dataset.getModelingExclusiveDataset());
			properTrainingSet = CalibrationSetUtils.shuffleList(properTrainingSet, seed);
		} if (! dataset.getCalibrationExclusiveDataset().isEmpty()) {
			calibrationSet.addAll(dataset.getCalibrationExclusiveDataset());
			calibrationSet = CalibrationSetUtils.shuffleList(calibrationSet, seed);
		}

		return new TrainSplit(properTrainingSet, calibrationSet);
	}
	
	@Override
	public int getMaximumSplitIndex() {
		return nrModels-1;
	}

	@Override
	public int getMinimumSplitIndex() {
		return 0;
	}

}


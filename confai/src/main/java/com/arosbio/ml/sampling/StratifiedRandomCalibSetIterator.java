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

public class StratifiedRandomCalibSetIterator implements TrainSplitIterator{
	private static final Logger LOGGER = LoggerFactory.getLogger(StratifiedRandomCalibSetIterator.class);

	private final Dataset dataset;
	private final Map<Double,List<DataRecord>> recs = new HashMap<>();
	private final double calibrationRatio;
	private final int totalNumModels;
	private final long seed;

	private int modelNr=0;

	public StratifiedRandomCalibSetIterator(Dataset problem,
			double calibrationRatio, int nrModels, long seed) throws IllegalArgumentException {
		this.dataset = problem;
		this.calibrationRatio = calibrationRatio;
		this.totalNumModels = nrModels;
		this.seed = seed;

		for (DataRecord rec: dataset.getDataset()) {
			if (! recs.containsKey(rec.getLabel())) {
				LOGGER.debug("Found new label when splitting dataset="+rec.getLabel());
				recs.put(rec.getLabel(), new ArrayList<DataRecord>());
			}
			recs.get(rec.getLabel()).add(rec);
		}

		// If more than 2 classes
		if (recs.size()>2)
			throw new IllegalArgumentException("Stratified sampling is only supported for binary classification - you've entered dataset with "+ recs.size() + " classes");

		StringBuffer sb = new StringBuffer();
		sb.append("Class-fractions (normal ds only): ");
		for (Map.Entry<Double, List<DataRecord>> entry: recs.entrySet()) {
			sb.append(entry.getKey());
			sb.append('=');
			sb.append(entry.getValue().size());
			sb.append(' ');
		}
		LOGGER.debug(sb.toString());
	}

	public StratifiedRandomCalibSetIterator(Dataset problem,
			double calibrationRatio, int nrModels) throws IllegalArgumentException {
		this(problem,calibrationRatio,nrModels,GlobalConfig.getInstance().getRNGSeed());
	}
	
	@Override
	public boolean hasNext() {
		return modelNr < totalNumModels;
	}

	@Override
	public TrainSplit next() {
		int currModelNr = modelNr;
		modelNr++;
		return getRandomTrainingsetSplit(currModelNr); 
	}

	@Override
	public void remove() {
		modelNr++;
	}

	/**
	 * Get a specific index, will not interfere with the {@link #hasNext()} or {@link #next()} methods 
	 * @param index The index <code>[0,num models)</code>
	 * @return the {@link TrainSplit} for the <code>index</code>
	 */
	public TrainSplit get(int index) throws NoSuchElementException{
		if (index <0 || index>=totalNumModels)
			throw new NoSuchElementException("Cannot get index "+index+", the only allowed indexes are [0,"+(totalNumModels-1)+"]");
		return getRandomTrainingsetSplit(index);
	}
	
	public Dataset getProblem() {
		return dataset;
	}

	private TrainSplit getRandomTrainingsetSplit(int index) {

		// Init lists for records 
		List<DataRecord> calibrationSet = new ArrayList<>((int)(dataset.getDataset().size()*calibrationRatio) + dataset.getCalibrationExclusiveDataset().size()+5);
		List<DataRecord> properTrainingSet = new ArrayList<>((int)(dataset.getDataset().size()*(1-calibrationRatio)) + dataset.getModelingExclusiveDataset().size()+5);

		//Draw calibrationSize observation from properTraining to calibrationSet (class 0)
		Random generator = new Random();

		for (Double label : recs.keySet()) {

			// For every label
			generator.setSeed(seed+index);
			List<DataRecord> records = new ArrayList<>(recs.get(label));
			int numInCalib = Math.round((float)(records.size()*calibrationRatio));
			for (int i=0; i<numInCalib; i++) {
				int pickIX = generator.nextInt(records.size());
				calibrationSet.add(records.remove(pickIX));
			}

			// Put the remaining records in proper training set
			properTrainingSet.addAll(records);
		}

		// Set up final calibration set (randomized)
		if (! dataset.getCalibrationExclusiveDataset().isEmpty())
			calibrationSet.addAll(dataset.getCalibrationExclusiveDataset());
		calibrationSet = CalibrationSetUtils.shuffleList(calibrationSet, seed);

		// Set up the final proper training set (randomized)
		if (! dataset.getModelingExclusiveDataset().isEmpty())
			properTrainingSet.addAll(dataset.getModelingExclusiveDataset());
		properTrainingSet = CalibrationSetUtils.shuffleList(properTrainingSet, seed);

		LOGGER.debug("Num in prop set: {}, num in calib set: {}", properTrainingSet.size(), calibrationSet.size());

		return new TrainSplit(properTrainingSet, calibrationSet);
	}
	
	@Override
	public int getMaximumSplitIndex() {
		return totalNumModels-1;
	}

	@Override
	public int getMinimumSplitIndex() {
		return 0;
	}

}


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

import java.util.List;

import com.arosbio.data.DataRecord;
import com.google.common.collect.Range;

/**
 * Holds a split (either created in a folded or a random manner) used in 
 * Conformal Prediction or Venn-ABERS Prediction
 * 
 * @author staffan
 *
 */
public class TrainSplit {
	
	private final List<DataRecord> properTrainingSet;

	private final List<DataRecord> calibrationSet;

	private final int totalNumTrainingRecords;

	private final Range<Double> observedLabelSpace;

	public TrainSplit(List<DataRecord> properTrainingSet,
			List<DataRecord> calibrationSet
			) {
		this.properTrainingSet=properTrainingSet;
		this.calibrationSet=calibrationSet;
		this.totalNumTrainingRecords = properTrainingSet.size() + calibrationSet.size();
		this.observedLabelSpace = null;
	}

	public TrainSplit(List<DataRecord> properTrainingSet,
			List<DataRecord> calibrationSet, 
			Range<Double> minMaxLabelSpace
			) {
		this.properTrainingSet=properTrainingSet;
		this.calibrationSet=calibrationSet;
		this.totalNumTrainingRecords = properTrainingSet.size() + calibrationSet.size();
		this.observedLabelSpace = minMaxLabelSpace;
	}
	

	public List<DataRecord> getProperTrainingSet() {
		return properTrainingSet;
	}

	public List<DataRecord> getCalibrationSet() {
		return calibrationSet;
	}
	
	public int getTotalNumTrainingRecords(){
		return totalNumTrainingRecords;
	}
	
	public Range<Double> getObservedLabelSpace(){
		return observedLabelSpace;
	}
	
	public String toString(){
		StringBuilder sb = new StringBuilder(100);
		sb.append("propTrainSize="+properTrainingSet.size());
		sb.append("\ncalibSize="+calibrationSet.size());
		sb.append("\ntotalNumRecs="+totalNumTrainingRecords);
		if (observedLabelSpace != null)
			sb.append("\nobservedLabelSpace=").append(observedLabelSpace);
		return sb.toString();
	}

}

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

/**
 * Holds a split (either created in a folded or a random manner) used in 
 * Conformal Prediction or Venn-ABERS Prediction
 * 
 * @author staffan
 *
 */
public class TrainSplit {
	
	private List<DataRecord> properTrainingSet;

	private List<DataRecord> calibrationSet;

	private int totalNumTrainingRecords;

	private Double minActivity=null;

	private Double maxActivity=null;

	public TrainSplit(List<DataRecord> properTrainingSet,
			List<DataRecord> calibrationSet
			) {
		
		this.properTrainingSet=properTrainingSet;
		this.calibrationSet=calibrationSet;
		this.totalNumTrainingRecords = properTrainingSet.size() + calibrationSet.size();
	}
	
	/**
	 * Frees all memory
	 */
	public void clear(){
		properTrainingSet.clear();
		calibrationSet.clear();
	}

	public List<DataRecord> getProperTrainingSet() {
		return properTrainingSet;
	}

	public void setProperTrainingSet(List<DataRecord> properTrainingSet) {
		this.properTrainingSet = properTrainingSet;
	}

	public List<DataRecord> getCalibrationSet() {
		return calibrationSet;
	}

	public void setCalibrationSet(List<DataRecord> calibrationSet) {
		this.calibrationSet = calibrationSet;
	}
	
	public int getTotalNumTrainingRecords(){
		return totalNumTrainingRecords;
	}
	
	public double getMinRegressionActivity(){
		if (minActivity == null)
			findMinMaxActivity();
		return minActivity;
	}
	
	public double getMaxRegressionActivity(){
		if (maxActivity == null)
			findMinMaxActivity();
		return maxActivity;
	}
	
	private void findMinMaxActivity(){
		double label=0;
		double minObservation=Double.MAX_VALUE;
		double maxObservation=-Double.MAX_VALUE;
		for (DataRecord rec : properTrainingSet) {
			label = rec.getLabel();
			if (label<minObservation)
				minObservation=label;
			if (label>maxObservation)
				maxObservation=label;
		}
		for (DataRecord rec : calibrationSet) {
			label = rec.getLabel();
			if (label<minObservation)
				minObservation=label;
			if (label>maxObservation)
				maxObservation=label;
		}
		minActivity = minObservation;
		maxActivity = maxObservation;
	}
	
	public String toString(){
		StringBuilder sb = new StringBuilder(100);
		sb.append("propTrainSize="+properTrainingSet.size());
		sb.append("\ncalibSize="+calibrationSet.size());
		sb.append("\ntotalNumRecs="+totalNumTrainingRecords);
		if (minActivity!=null)
			sb.append("\nminActivity="+minActivity);
		if (maxActivity!=null)
			sb.append("\nmaxActivity="+maxActivity);
		return sb.toString();
	}

}

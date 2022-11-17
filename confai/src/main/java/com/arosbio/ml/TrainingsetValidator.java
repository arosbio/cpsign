/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml;

import java.util.List;
import java.util.Map;

import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.ml.sampling.TrainSplit;

public class TrainingsetValidator {
	
	private static TrainingsetValidator instance = systemDefault();

	// Thresholds for minimum number of examples used in training
	private static final int MAX_THRESHOLD_TO_CHECK = 50; // pretty expensive looking through all data in case 
	private static final int MIN_NUMBER_FOR_TRAINING_SVM = 5;
	/** The minimum number of instances needed for calibration. For classification tasks this applies to each <b>class</b> */
	private static final int MIN_NUMBER_FOR_CALIBRATING_PREDICTOR = 3;
	// Testing environment
	private static final int MAX_THRESHOLD_TESTING = 50;
	private static final int MIN_NUM_TRAINING_SVM_TESTING = 1;
	private static final int MIN_NUM_CALIB_TESTING = 1;


	/** Sets a threshold for how many training records to go through */
	public final int MAX_NUM_INSTANCES_TO_CHECK;
	/** The minium number of training records needed for fitting an underlying scorer */
	public final int MIN_NUM_MODEL_TRAINING_INSTANCES;
	/** The minimum number of instances needed for calibration. For classification tasks this applies to each <b>class</b> */
	public final int MIN_NUM_CALIBRATION_INSTANCES;

	/* ==========================================================================================
	 * 
	 * 			INSTANTIATION
	 * 
	 * ==========================================================================================
	 */
	
	public static synchronized TrainingsetValidator getInstance() {
		return instance;
	}
	
	private TrainingsetValidator(int minNumberForSVMTraining,
			int minNumberForCalibration,
			int maxNumberToValidate) throws IllegalArgumentException {
		if (minNumberForCalibration < 1)
			throw new IllegalArgumentException("Minimum number of examples for calibration is 1");
		if (minNumberForSVMTraining < 1)
			throw new IllegalArgumentException("Minimum number of examples for SVM training is 1");
		if (maxNumberToValidate < 20)
			throw new IllegalArgumentException("Maximum to validate must be at least 20");
		this.MIN_NUM_CALIBRATION_INSTANCES = minNumberForCalibration;
		this.MIN_NUM_MODEL_TRAINING_INSTANCES = minNumberForSVMTraining;
		this.MAX_NUM_INSTANCES_TO_CHECK = maxNumberToValidate;
	}

	private static TrainingsetValidator systemDefault() {
		return new TrainingsetValidator(MIN_NUMBER_FOR_TRAINING_SVM, MIN_NUMBER_FOR_CALIBRATING_PREDICTOR, MAX_THRESHOLD_TO_CHECK);
	}
	
	/* ==========================================================================================
	 * 
	 * 			TOGGLE BETWEEN TESTING AND PRODUCTION CODE
	 * 
	 * ==========================================================================================
	 */
	
	public static void setTestingEnv() {
		instance = new TrainingsetValidator(MIN_NUM_TRAINING_SVM_TESTING, MIN_NUM_CALIB_TESTING, MAX_THRESHOLD_TESTING);
	}
	
	public static void setProductionEnv() {
		instance = systemDefault();
	}

	/* ==========================================================================================
	 * 
	 * 			Validation
	 * 
	 * ==========================================================================================
	 */
	
	/**
	 * Validates a classification dataset for a Calibrated Predictor
	 * @param trainingset A {@link TrainSplit} of training data
	 * @throws IllegalArgumentException If the <code>trainingset</code> is not fulfilling the requirements
	 */
	public void validateClassification(TrainSplit trainingset) throws IllegalArgumentException {
		if (trainingset.getProperTrainingSet().size() < MIN_NUM_MODEL_TRAINING_INSTANCES)
			throw new IllegalArgumentException("Too few examples used for training the predictor; requires at least " + MIN_NUM_MODEL_TRAINING_INSTANCES + " examples for training underlying algorithm");
		if (trainingset.getCalibrationSet().size() < MIN_NUM_CALIBRATION_INSTANCES)
			throw new IllegalArgumentException("Too few examples used for calibrating the predictor; requires at least " + MIN_NUM_CALIBRATION_INSTANCES + " examples");

		// return instead of having to check through all data
		if (	trainingset.getCalibrationSet().size() > MAX_NUM_INSTANCES_TO_CHECK &&
				trainingset.getProperTrainingSet().size() > MAX_NUM_INSTANCES_TO_CHECK)
			return;

		// Calibration set
		Map<Double,Integer> labelFreqs = DataUtils.countLabels(trainingset.getCalibrationSet());
		for (Map.Entry<Double, Integer> lab: labelFreqs.entrySet()) {
			if (lab.getValue() < MIN_NUM_CALIBRATION_INSTANCES)
				throw new IllegalArgumentException("Too few examples for calibrating the prediction; requires at least " + MIN_NUM_CALIBRATION_INSTANCES + " for each label");
		}

		// Proper training set
		Map<Double, Integer> propLabels = DataUtils.countLabels(trainingset.getProperTrainingSet());
		for (Map.Entry<Double, Integer> lab: propLabels.entrySet()) {
			if (lab.getValue() < MIN_NUM_MODEL_TRAINING_INSTANCES)
				throw new IllegalArgumentException("Too few examples for training underlying algorithm; requires at least " + MIN_NUM_MODEL_TRAINING_INSTANCES + " for each label");
		}

	}

	/**
	 * Validates a classification dataset for a non-Calibrated predictor (i.e. does not use a calibration set)
	 * @param trainingset a List of training data
	 * @throws IllegalArgumentException If the <code>trainingset</code> is not fulfilling the requirements
	 */
	public void validateClassification(List<DataRecord> trainingset) throws IllegalArgumentException {
		if (trainingset.size() > MAX_NUM_INSTANCES_TO_CHECK)
			return;

		if (trainingset.size() < MIN_NUM_MODEL_TRAINING_INSTANCES) 
			throw new IllegalArgumentException("Too few examples for training predictor; requires at least " + MIN_NUM_MODEL_TRAINING_INSTANCES + " examples");

		Map<Double,Integer> labelFrequncies = DataUtils.countLabels(trainingset);
		for (Map.Entry<Double, Integer> lab: labelFrequncies.entrySet()) {
			if (lab.getValue() < MIN_NUM_MODEL_TRAINING_INSTANCES)
				throw new IllegalArgumentException("Too few examples for training underlying algorithm; requires at least " + MIN_NUM_MODEL_TRAINING_INSTANCES + " for each label");
		}
	}

	/**
	 * Validates a regression dataset for a Calibrated predictor
	 * @param trainingset A {@link TrainSplit} of training data
	 * @throws IllegalArgumentException If the <code>trainingset</code> is not fulfilling the requirements
	 */
	public void validateRegression(TrainSplit trainingset) throws IllegalArgumentException {
		if (trainingset.getProperTrainingSet().size() < MIN_NUM_MODEL_TRAINING_INSTANCES)
			throw new IllegalArgumentException("Too few examples used for training the predictor; requires at least " + MIN_NUM_MODEL_TRAINING_INSTANCES + " examples for training underlying algorithm");
		if (trainingset.getCalibrationSet().size() < MIN_NUM_CALIBRATION_INSTANCES)
			throw new IllegalArgumentException("Too few examples used for calibrating the predictor; requires at least " + MIN_NUM_CALIBRATION_INSTANCES + " examples");
	}


}

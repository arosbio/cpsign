/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics.classification;

import java.util.Map;

import com.arosbio.commons.mixins.Aliased;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.google.common.collect.ImmutableMap;

public class Precision implements SingleValuedMetric, PointClassifierMetric, LabelDependent, Aliased { 

	public static final String METRIC_NAME = "Precision";
	public static final String METRIC_ALIAS = "PPV";
	public static final String METRIC_DESCRIPTION = "Precision or Positive Predictive Value (PPV) or - calculated as TP/(TP+FP), where TP=True Positives and FP=False Positives. Note that the 'positive' label is set to be the last label given to the CLI, or the largest numerical label when using the API.";

	// if we know the negative class
	private int positiveClass = LabelDependent.DEFAULT_POS_LABEL;
	private int truePos = 0, falsePos = 0, numPredictionsDone=0;


	// CONSTRUCTORS

	public Precision() {}

	public Precision(int positiveClass) {
		this.positiveClass = positiveClass;
	}

	// GETTERS AND SETTERS

	public boolean supportsMulticlass() {
		return false;
	}

	@Override
	public String getDescription() {
		return METRIC_DESCRIPTION;
	}

	// ADD PREDICTIONS

	/*
	 * Main method for adding the predictions
	 */
	@Override
	public void addPrediction(int observedLabel, int predictedLabel) {
		if (predictedLabel == positiveClass && predictedLabel == observedLabel) {
			truePos++;
		} else if (predictedLabel == positiveClass) {
			falsePos++;
		}
		numPredictionsDone++;
	}


	@Override
	public double getScore() {
		if (numPredictionsDone <= 0)
			return Double.NaN;
		else if (truePos+falsePos == 0) {
			// If divisor is 0 - return 0/1 instead (similar to sklearn) - allows to compute for single classes
			return 0;
		}
		return ((double)truePos)/(truePos+falsePos);

	}


	@Override
	public int getNumExamples() {
		return numPredictionsDone;
	}

	public void setPositiveLabel(int positiveLabel) {
		if (numPredictionsDone > 0) {
			throw new IllegalStateException("Cannot change the positive class after adding prediction");
		}
		this.positiveClass = positiveLabel;
	}

	public int getPositiveLabel() {
		return positiveClass;
	}


	public String toString() {
		return SingleValuedMetric.toString(this);
	}

	@Override
	public Map<String, ? extends Object> asMap() {
		return ImmutableMap.of(METRIC_NAME, getScore());
	}

	@Override
	public String getName() {
		return METRIC_NAME;
	}

	@Override
	public String[] getAliases() {
		return new String[] {METRIC_ALIAS};
	}

	@Override
	public Precision clone() {
		Precision clone = new Precision();
		clone.positiveClass=positiveClass;
		return clone;
	}

	@Override
	public void clear() {
		truePos = 0;
		falsePos = 0;
		numPredictionsDone = 0;
	}

	@Override
	public boolean goalIsMinimization() {
		return false;
	}

}

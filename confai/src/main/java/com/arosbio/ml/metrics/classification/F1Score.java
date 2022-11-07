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

import com.arosbio.commons.mixins.Described;
import com.arosbio.ml.ClassificationUtils;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.google.common.collect.ImmutableMap;

public class F1Score implements SingleValuedMetric, PointClassifierMetric, ProbabilisticMetric, LabelDependent, Described {

	public static final String METRIC_NAME = "F1Score";
	public static final String METRIC_DESCRIPTION = "F1 score, the harmonic mean of Precision and Recall. The optimal value is 1.0. Only available for binary classification.";

	private int positiveClass = LabelDependent.DEFAULT_POS_LABEL;
	private int truePos = 0, falsePos = 0, falseNeg=0, numPredsDone=0;

	// CONSTRUCTORS

	public F1Score() {}

	public F1Score(int positiveClass) {
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
	@Override
	public void addPrediction(int observedLabel, Map<Integer, Double> probabilities) {
		addPrediction(observedLabel, ClassificationUtils.getPredictedClass(probabilities));
	}

	/*
	 * Main method for adding the predictions
	 */
	@Override
	public void addPrediction(int observedLabel, int predictedLabel) {
		if (observedLabel == positiveClass) {
			if (observedLabel == predictedLabel)
				truePos++;
			else
				falseNeg++;
		} else {
			// obs value is negative
			if (predictedLabel == positiveClass)
				falsePos++;
		}
		numPredsDone++;
	}

	@Override
	public double getScore() {
		if (numPredsDone == 0)
			return Double.NaN;
		if (truePos + 0.5*(falsePos+falseNeg) == 0)
			return 0;
		return ((double)truePos)/(truePos+ 0.5*(falsePos+falseNeg));
	}

	@Override
	public int getNumExamples() {
		return numPredsDone;
	}

	public void setPositiveLabel(int positiveLabel) {
		if (truePos + falsePos > 0) {
			throw new IllegalStateException("Cannot change the positive class label when added predictions to the metric");
		}
		this.positiveClass = positiveLabel;
	}

	@Override
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
	public F1Score clone() {
		F1Score clone = new F1Score();
		clone.positiveClass=positiveClass;
		return clone;
	}

	@Override
	public void clear() {
		truePos = 0;
		falsePos = 0;
		falseNeg = 0;
		numPredsDone = 0;
	}

	@Override
	public boolean goalIsMinimization() {
		return false;
	}


}

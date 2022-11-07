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
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.google.common.collect.ImmutableMap;

public class NPV implements SingleValuedMetric, PointClassifierMetric, LabelDependent, Described { 

	public static final String METRIC_NAME = "NPV";
	public static final String METRIC_DESCRIPTION = "Negative Predictive Value (NPV) - calculated as TN/(TN+FP), TN=True Negative, FP=False Postive. Note that the 'negative' label is set to be the first label given to the CLI, or the smallest numerical label when using the API.";

	// if we know the negative class
	private int positiveLabel=LabelDependent.DEFAULT_POS_LABEL;
	private int trueNeg = 0, falseNeg = 0, numPredsDone=0;

	// CONSTRUCTORS

	public NPV() {}

	public NPV(int positiveLabel) {
		this.positiveLabel = positiveLabel;
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
		if (predictedLabel != positiveLabel) {
			if (predictedLabel == observedLabel)
				trueNeg++;
			else
				falseNeg++;
		}
		numPredsDone++;
	}

	@Override
	public double getScore() {
		if (numPredsDone <=0)
			return Double.NaN;
		if (trueNeg+falseNeg == 0)
			return 0;
		return ((double)trueNeg)/(trueNeg+falseNeg);

	}

	@Override
	public int getNumExamples() {
		return numPredsDone;
	}

	@Override
	public void setPositiveLabel(int positive) {
		this.positiveLabel = positive;
	}

	@Override
	public int getPositiveLabel() {
		return positiveLabel;
	}

	public String toString() {
		return SingleValuedMetric.toString(this);
	}

	@Override
	public Map<String, ?> asMap() {
		return ImmutableMap.of(METRIC_NAME, getScore());
	}

	@Override
	public String getName() {
		return METRIC_NAME;
	}

	@Override
	public NPV clone() {
		NPV clone = new NPV();
		clone.positiveLabel = positiveLabel;
		return clone;
	}

	@Override
	public void clear() {
		trueNeg = 0;
		falseNeg = 0;
		numPredsDone = 0;
	}

	@Override
	public boolean goalIsMinimization() {
		return false;
	}

}

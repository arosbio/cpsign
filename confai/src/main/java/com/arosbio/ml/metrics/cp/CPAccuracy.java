/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics.cp;

import java.util.Map;

import com.arosbio.commons.mixins.Described;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.cp.classification.CPClassificationMetric;
import com.arosbio.ml.metrics.cp.regression.CPRegressionMetric;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;

public class CPAccuracy implements SingleValuedMetric, CPClassificationMetric, CPRegressionMetric, Described {

	public static final String METRIC_NAME = "CP Accuracy";
	public static final String METRIC_DESCRIPTION = "Conformal Prediction (CP) accuracy calculate the proportion of correct predictions (i.e., where the prediction set/interval contains the true value).";
	
	private double confidence = ConfidenceDependentMetric.DEFAULT_CONFIDENCE;
	private int numCorrect = 0;
	private int numTotal = 0;
	
	public CPAccuracy() {}
	
	public CPAccuracy(double confidence) {
		setConfidence(confidence);
	}
	
	@Override
	public String getName() {
		return METRIC_NAME;
	}

	@Override
	public String getDescription() {
		return METRIC_DESCRIPTION;
	}
	
	@Override
	public int getNumExamples() {
		return numTotal;
	}

	@Override
	public CPAccuracy clone() {
		return new CPAccuracy(confidence);
	}

	@Override
	public void clear() {
		numCorrect=0;
		numTotal = 0;
	}

	@Override
	public boolean goalIsMinimization() {
		return false;
	}


	@Override
	public void addPrediction(double trueLabel, Range<Double> predictedInterval) {
		if (predictedInterval.contains(trueLabel))
			numCorrect++;
		numTotal++;
	}
	
	@Override
	public void addPrediction(int trueLabel, Map<Integer, Double> pValues) {
		if (pValues.get(trueLabel) > (1-confidence)) {
			numCorrect++;
		}
		numTotal++;
	}

	@Override
	public double getScore() {
		if (numTotal == 0)
			return Double.NaN;
		return ((double)numCorrect)/numTotal;
	}

	@Override
	public Map<String, ? extends Object> asMap() {
		return ImmutableMap.of(METRIC_NAME, getScore());
	}

	@Override
	public double getConfidence(){
		return confidence;
	}
	
	@Override
	public void setConfidence(double confidence) {
		if (confidence>1 || confidence <0)
			throw new IllegalArgumentException("confidence must be in the range [0..1]");
		if (numTotal > 0)
			throw new IllegalStateException("Already started to add predictions - cannot change confidence at this point");
		this.confidence = confidence;
	}
	
	public String toString() {
		return SingleValuedMetric.toString(this);
	}

	@Override
	public boolean supportsMulticlass() {
		return true;
	}


}

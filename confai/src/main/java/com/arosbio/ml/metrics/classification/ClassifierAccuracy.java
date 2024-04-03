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

import java.util.List;
import java.util.Map;

import com.arosbio.ml.metrics.SingleValuedMetric;
import com.google.common.collect.ImmutableMap;

public class ClassifierAccuracy implements PointClassifierMetric, SingleValuedMetric {

	public final static String METRIC_NAME = "Classifier Accuracy";
	public final static String METRIC_DESCRIPTION = "Calculates the percentage of accurate predictions out of all predictions.";
	
	private int numCorrect = 0, numTotal=0;
	
	@Override
	public boolean supportsMulticlass() {
		return true;
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
	public ClassifierAccuracy clone() {
		return new ClassifierAccuracy();
	}

	@Override
	public void clear() {
		numTotal=0;
		numCorrect=0;
	}

	@Override
	public boolean goalIsMinimization() {
		return false;
	}

	@Override
	public void addPrediction(int observedLabel, int predictedLabel) {
		if (observedLabel == predictedLabel)
			numCorrect++;
		numTotal++;
	}
	
	public double calculate(final List<Integer> trueLabels, final List<Integer> predictions) 
			throws IllegalArgumentException {
		if (trueLabels.size() != predictions.size())
			throw new IllegalArgumentException("The true labels and predictions must be of equal size");
		if (trueLabels.isEmpty())
			return 0;
		double nCorrect=0;
		for (int i=0; i<trueLabels.size(); i++) {
			if (trueLabels.get(i)==predictions.get(i))
				nCorrect++;
		}
		return ((double)nCorrect)/trueLabels.size();
	}
	
	@Override
	public String toString() {
		return SingleValuedMetric.toString(this);
	}

	@Override
	public double getScore() {
		if (numTotal<1)
			return Double.NaN;
		return ((double)numCorrect)/numTotal;
	}

	@Override
	public Map<String, ? extends Object> asMap() {
		return ImmutableMap.of(METRIC_NAME,getScore());
	}

}

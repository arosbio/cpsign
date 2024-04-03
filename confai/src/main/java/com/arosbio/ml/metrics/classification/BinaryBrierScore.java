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

@Deprecated
/**
 * Remove the binary version of brier score, to keep only one. The original brier score (see {@link BrierScore} works for multiclass, whereas this one does not. 
 * @author staffan
 * @see {@link BrierScore}
 */
public class BinaryBrierScore implements SingleValuedMetric, ProbabilisticMetric, Aliased {

	public final static String METRIC_NAME = "Binary Brier Score";
	public final static String METRIC_ALIAS = "BrierScore";
	public final static String METRIC_DESCRIPTION = "A metric for probabilistic predictions, smaller values are preferable. See https://en.wikipedia.org/wiki/Brier_score#Definition. Only applicable to binary classification.";

	private double sqrErrorSum = 0d;
	private int numExamples = 0;

	@Override
	public String getName() {
		return METRIC_NAME;
	}
	
	@Override
	public String[] getAliases() {
		return new String[] {METRIC_ALIAS};
	}
	
	@Override
	public String getDescription() {
		return METRIC_DESCRIPTION;
	}
	
	@Override
	public boolean supportsMulticlass() {
		return false;
	}

	private void addPrediction(double probabilityForTrueLabel) {
		sqrErrorSum += (1.-probabilityForTrueLabel)*(1.-probabilityForTrueLabel);
		numExamples++;
	}

	public void addPrediction(int trueLabel, Map<Integer, Double> prob) {
		if (!prob.containsKey(trueLabel))
			throw new RuntimeException("Something went wrong computing Squared error sum");
		if (prob.size() != 2)
			return;

		addPrediction(prob.get(trueLabel));
	}

	public double getScore() {
		if (numExamples == 0)
			return Double.NaN;
		return sqrErrorSum/numExamples;
	}

	@Override
	public Map<String, ? extends Object> asMap() {
		return ImmutableMap.of(METRIC_NAME,getScore());
	}

	@Override
	public int getNumExamples() {
		return numExamples;
	}

	public void clear() {
		sqrErrorSum = 0;
		numExamples = 0;
	}
	
	public BinaryBrierScore clone() {
		return new BinaryBrierScore();
	}

	@Override
	public boolean goalIsMinimization() {
		return true;
	}

	public String toString() {
		return SingleValuedMetric.toString(this);
	}

}

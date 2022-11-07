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

import com.arosbio.commons.MathUtils;
import com.arosbio.commons.mixins.Aliased;
import com.arosbio.commons.mixins.Described;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.google.common.collect.ImmutableMap;

public class LogLoss implements SingleValuedMetric, ProbabilisticMetric, Aliased, Described {

	public static final String METRIC_NAME = "Log loss";
	public static final String METRIC_ALIAS = "Cross-entropy";
	public static final String METRIC_DESCRIPTION = "Log loss or Cross-entropy loss is commonly used for evaluating the performance of probabilistic predictions, see more on Scikit Learn: https://scikit-learn.org/stable/modules/model_evaluation.html#log-loss";
	/** Truncate probabilities to not be identical to 0 or 1, as it causes numerical issues when computing logarithm (undefined value) */
	public static final double PROBABILITY_TRUNCATION = 1e-15;

	private double loglossSum = 0d;
	private int numExamples = 0;
	
	@Override
	public String getName() {
		return METRIC_NAME;
	}
	
	@Override
	public String[] getAliases() {
		return new String[]{METRIC_ALIAS};
	}
	
	@Override
	public boolean supportsMulticlass() {
		return true;
	}
	
	@Override
	public String getDescription() {
		return METRIC_DESCRIPTION;
	}
	
	/**
	 * 
	 * @param probability the probability for the <b>true label</b>
	 */
	private void addPrediction(double probability) {
		loglossSum += Math.log(MathUtils.truncate(probability, PROBABILITY_TRUNCATION, 1));
		numExamples++;
	}
	
	public void addPrediction(int trueLabel, Map<Integer, Double> prob) {
		if (!prob.containsKey(trueLabel))
			throw new IllegalArgumentException("The observedLabel ("+trueLabel+") is not in the probabilites given (computing log loss)");
		
		addPrediction(prob.get(trueLabel));
	}
	
	public String toString() {
		return SingleValuedMetric.toString(this);
	}
	
	@Override
	public int getNumExamples() {
		return numExamples;
	}

	@Override
	public Map<String, ? extends Object> asMap() {
		return ImmutableMap.of(METRIC_NAME,getScore());
	}

	@Override
	public LogLoss clone() {
		return new LogLoss();
	}

	@Override
	public void clear() {
		loglossSum = 0;
		numExamples = 0;
	}

	@Override
	public double getScore() {
		if (numExamples == 0)
			return Double.NaN;
		return -loglossSum/numExamples;
	}

	@Override
	public boolean goalIsMinimization() {
		return true;
	}

}

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
import com.arosbio.commons.mixins.Described;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.google.common.collect.ImmutableMap;

public class BrierScore implements SingleValuedMetric, ProbabilisticMetric, Aliased, Described {

	public final static String METRIC_NAME = "Brier Score";
	public final static String METRIC_ALIAS = "BrierScore";
	public final static String METRIC_DESCRIPTION = "The original metric proposed by Brier, used for evaluating (multiclass) probabilistic predictions, smaller values are preferable. See https://en.wikipedia.org/wiki/Brier_score#Original_definition_by_Brier for further details.";

	private double sqrErrorSumSum = 0d;
	private int numExamples = 0;

	@Override
	public String getName() {
		return METRIC_NAME;
	}
	
	@Override
	public String getDescription() {
		return METRIC_DESCRIPTION;
	}
	
	@Override
	public String[] getAliases() {
		return new String[] {METRIC_ALIAS};
	}
	
	@Override
	public boolean supportsMulticlass() {
		return true;
	}
	
	public void addPrediction(int trueLabel, Map<Integer, Double> probabilities) {
		if (!probabilities.containsKey(trueLabel))
			throw new RuntimeException("Something went wrong computing Squared error sum");
		
		for (Map.Entry<Integer, Double> p : probabilities.entrySet()) {
			if (p.getKey() == trueLabel) {
				sqrErrorSumSum += (1 - p.getValue())*(1-p.getValue());
			} else {
				sqrErrorSumSum += p.getValue()*p.getValue();
			}
		}
		numExamples++;
	}

	
	public double getScore() {
		if (numExamples == 0)
			return Double.NaN;
		return sqrErrorSumSum/numExamples;
	}

	public Map<String,Double> asMap(){
		return ImmutableMap.of(METRIC_NAME,getScore());
	}

	@Override
	public int getNumExamples() {
		return numExamples;
	}

	public void clear() {
		sqrErrorSumSum = 0;
		numExamples = 0;
	}
	
	public BrierScore clone() {
		return new BrierScore();
	}

	@Override
	public boolean goalIsMinimization() {
		return true;
	}

	public String toString() {
		return SingleValuedMetric.toString(this);
	}

}

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics.cp.classification;

import java.util.Collections;
import java.util.Map;

import com.arosbio.commons.mixins.Aliased;
import com.arosbio.commons.mixins.Described;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.google.common.collect.ImmutableMap;

public class UnobservedCredibility implements SingleValuedMetric, CPClassificationMetric, Described, Aliased {

	public static final String METRIC_NAME = "Unobserved Credibility";
	public final static String METRIC_DESCRIPTION = "An unobserved metric that aims to evaluate the efficiency of a Conformal classifier without knowing the true label of the test examples. This metric computes the average of the largest p-value in the predictions. Larger values are preferable.";
	private final static String[] METRIC_ALIASES = new String[] {"CP_Credibility"};

	private double credibilitySum = 0d;
	private int numExamples = 0;

	@Override
	public void addPrediction(int trueLabel, Map<Integer, Double> pValues) {
		credibilitySum +=Collections.max(pValues.values());
		numExamples++;
	}
	
	@Override
	public boolean supportsMulticlass() {
		return true;
	}

	@Override
	public int getNumExamples() {
		return numExamples;
	}

	@Override
	public String getName() {
		return METRIC_NAME;
	}
	
	@Override
	public String[] getAliases() {
		return METRIC_ALIASES;
	}
	
	@Override
	public String getDescription() {
		return METRIC_DESCRIPTION;
	}

	public Map<String,Double> asMap(){
		return ImmutableMap.of(METRIC_NAME, getScore());
	}

	public String toString() {
		return SingleValuedMetric.toString(this);
	}


	@Override
	public UnobservedCredibility clone() {
		return new UnobservedCredibility();
	}

	@Override
	public void clear() {
		credibilitySum = 0;
		numExamples = 0;
	}

	@Override
	public double getScore() {
		if (numExamples == 0)
			return Double.NaN;
		return credibilitySum/numExamples;
	}

	@Override
	public boolean goalIsMinimization() {
		return false;
	}


}

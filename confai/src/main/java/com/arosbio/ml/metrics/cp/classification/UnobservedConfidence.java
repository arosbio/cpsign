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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.arosbio.commons.mixins.Aliased;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.google.common.collect.ImmutableMap;

/**
 * The <b>confidence</b> in a prediction is 1-"next to largest p-value". This is confidence-independent and does not 
 * regard the true label and hence it is <b>unobserved</b>. Typically this is often only called confidence, but this may
 * be confusing as we often set a chosen confidence level in the prediction to yield the prediction set so we call it
 * UnobservedConfidence to make a distinction. 
 * @author staffan
 *
 */
public class UnobservedConfidence implements SingleValuedMetric, CPClassifierMetric, Aliased {
	
	public final static String METRIC_NAME = "Unobserved Confidence";
	public final static String METRIC_DESCRIPTION = "An unobserved metric that aims to evaluate the efficiency of a Conformal classifier without knowing the true label of the test examples. This metric computes the average of 1 - 'the second largest p-value' in the predictions. Larger values are preferable.";
	private final static String[] METRIC_ALIASES = new String[] {"CP_Confidence"};
	
	private double confidenceSum = 0d;
	private int numExamples = 0;
	
	@Override
	public void addPrediction(int trueLabel, Map<Integer, Double> pValues) {
		if (pValues.size() == 2)
			confidenceSum +=1-Collections.min(pValues.values());
		else if (pValues.size() > 2){
			List<Double> ps = new ArrayList<>(pValues.values());
			Collections.sort(ps);
			confidenceSum += 1-ps.get(ps.size()-2); // second largest
		} else
			throw new RuntimeException("faulty argument sent to UnobservedConfidence");
		numExamples++;
	}
	
	@Override
	public int getNumExamples() {
		return numExamples;
	}
	
	@Override
	public boolean supportsMulticlass() {
		return true;
	}

	public Map<String,Double> asMap(){
		return ImmutableMap.of(METRIC_NAME,getScore());
	}
	
	public String toString() {
		return SingleValuedMetric.toString(this);
	}

	@Override
	public double getScore() {
		if (numExamples == 0)
			return Double.NaN;
		return confidenceSum/numExamples;
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
	
	public UnobservedConfidence clone() {
		return new UnobservedConfidence();
	}

	@Override
	public void clear() {
		confidenceSum = 0;
		numExamples = 0;
	}

	@Override
	public boolean goalIsMinimization() {
		return false;
	}
	
}

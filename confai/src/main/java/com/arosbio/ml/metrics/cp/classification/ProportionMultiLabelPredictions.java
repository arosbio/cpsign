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

import java.util.Map;

import com.arosbio.commons.mixins.Aliased;
import com.arosbio.commons.mixins.Described;
import com.arosbio.ml.cp.PValueTools;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.cp.ConfidenceDependentMetric;
import com.google.common.collect.ImmutableMap;

public class ProportionMultiLabelPredictions implements SingleValuedMetric, 
	CPClassificationMetric, ConfidenceDependentMetric, Aliased, Described {

	public static final String METRIC_NAME = "Proportion multi-label prediction sets";
	public static final String METRIC_ALIAS = "PropMultiLabel";
	public final static String METRIC_DESCRIPTION = "The proportion of multi-label predictions at a given confidence, the proportion should be minimized.";
	
	private double confidenceLevel;
	private int numExamples = 0, numMultiLabel=0;
	
	/**
	 * Uses default confidence of {@link com.arosbio.ml.metrics.cp.ConfidenceDependentMetric#DEFAULT_CONFIDENCE} (0.8)
	 */
	public ProportionMultiLabelPredictions() {
		this(ConfidenceDependentMetric.DEFAULT_CONFIDENCE);
	}
	
	public ProportionMultiLabelPredictions(double confidenceLevel) {
		this.confidenceLevel = confidenceLevel;
	}
	
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
	public String[] getAliases() {
		return new String[]{METRIC_ALIAS};
	}
	
	public String toString() {
		return SingleValuedMetric.toString(this);
	}

	@Override
	public int getNumExamples() {
		return numExamples;
	}

	@Override
	public ProportionMultiLabelPredictions clone() {
		return new ProportionMultiLabelPredictions(confidenceLevel);
	}

	@Override
	public void clear() {
		numExamples=0;
		numMultiLabel=0;
	}

	@Override
	public boolean goalIsMinimization() {
		return true;
	}

	@Override
	public double getConfidence() {
		return confidenceLevel;
	}

	@Override
	public double getScore() {
		if (numExamples < 1)
			return Double.NaN;
		return ((double)numMultiLabel)/numExamples;
	}

	@Override
	public Map<String, ? extends Object> asMap() {
		return ImmutableMap.of(METRIC_NAME,getScore());
	}

	@Override
	public void setConfidence(double confidence) 
		throws IllegalArgumentException, IllegalStateException {
		
		if (confidence< 0 || confidence > 1)
			throw new IllegalArgumentException("Confidence must be in the range [0,1]");
		if (numExamples>0)
			throw new IllegalStateException("Cannot change the confidence when predictions has been added");
		this.confidenceLevel = confidence;
		
	}

	@Override
	public void addPrediction(int trueLabel, Map<Integer, Double> pValues) {
		if (PValueTools.getPredictionSetSize(pValues, confidenceLevel)>1)
			numMultiLabel++;
		numExamples++;
	}

}

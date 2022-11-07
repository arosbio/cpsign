/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics.cp.regression;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.arosbio.commons.MathUtils;
import com.arosbio.commons.mixins.Aliased;
import com.arosbio.commons.mixins.Described;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.cp.ConfidenceDependentMetric;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;

public class MeanPredictionIntervalWidth implements SingleValuedMetric, 
	CPRegressionMetric, Aliased, Described {

	public static final String METRIC_NAME = "Mean prediction-interval width";
	public static final String METRIC_ALIAS = "MeanPredWidth";
	public final static String METRIC_DESCRIPTION = "The mean prediction interval width at a fixed confidence level. The metric should be minimized for the highest informational efficiency.";

	private List<Double> intervalWidths = new ArrayList<>();
	private double confidenceLevel;

	public MeanPredictionIntervalWidth() {
		this(ConfidenceDependentMetric.DEFAULT_CONFIDENCE);
	}
	
	public MeanPredictionIntervalWidth(double confidenceLevel) {
		this.confidenceLevel = confidenceLevel;
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
	
	@Override
	public int getNumExamples() {
		return intervalWidths.size();
	}

	@Override
	public void clear() {
		intervalWidths.clear();
	}

	@Override
	public boolean goalIsMinimization() {
		return true;
	}

	@Override
	public void addPrediction(double trueLabel, Range<Double> predictedInterval) {
		intervalWidths.add(predictedInterval.upperEndpoint()-predictedInterval.lowerEndpoint());
	}

	@Override
	public double getConfidence() {
		return confidenceLevel;
	}
	
	public void setConfidence(double confidence) {
		if (confidence< 0 || confidence > 1)
			throw new IllegalArgumentException("Confidence must be in the range [0..1]");
		if (!intervalWidths.isEmpty())
			throw new IllegalStateException("Cannot change the confidence when predictions has been added");
		this.confidenceLevel = confidence;
	}

	@Override
	public double getScore() {
		if (intervalWidths.isEmpty())
			return Double.NaN;
		return MathUtils.mean(intervalWidths);
	}

	@Override
	public Map<String, ? extends Object> asMap() {
		return ImmutableMap.of(METRIC_NAME,getScore());
	}

	@Override
	public MeanPredictionIntervalWidth clone() {
		return new MeanPredictionIntervalWidth(confidenceLevel);
	}

	public String toString() {
		return SingleValuedMetric.toString(this);
	}

}

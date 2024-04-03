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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.arosbio.commons.MathUtils;
import com.arosbio.commons.mixins.Aliased;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.google.common.collect.Range;

public class ConfidenceGivenPredictionIntervalWidth implements SingleValuedMetric, 
	CIWidthBasedMetric, Aliased {
	
	public final static String METRIC_NAME = "Confidence for given prediction interval width";
	public final static String METRIC_DESCRIPTION = "Calculates the confidence in predictions, based on a given interval width. The metric should be maximized, but is very dependent on the particular data set and the range of the label space.";
	private final static String[] METRIC_ALIASES = new String[] {"ConfGivenIntervalWidth"};
	
	private double intervalWidth;
	private List<Double> confidences = new ArrayList<>();
	
	public ConfidenceGivenPredictionIntervalWidth() {
		this(1); // This is totally arbitrary - depends on the dataset too much, but must be able to construct the metric with service-loader
	}
	
	public ConfidenceGivenPredictionIntervalWidth(double width) {
		this.intervalWidth = width;
	}
	
	@Override
	public int getNumExamples() {
		return confidences.size();
	}

	@Override
	public double getCIWidth() {
		return intervalWidth;
	}
	
	public void setCIWidth(double width) {
		if (width<= 0 )
			throw new IllegalArgumentException("Width must be strictly positive (>0)");
		if (!confidences.isEmpty())
			throw new IllegalStateException("Cannot change the width when predictions has been added");
		this.intervalWidth = width;
	}

	@Override
	public void addPrediction(double trueLabel, Range<Double> predictedInterval, double confidence) {
		confidences.add(confidence);
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

	@Override
	public ConfidenceGivenPredictionIntervalWidth clone() {
		return new ConfidenceGivenPredictionIntervalWidth(intervalWidth);
	}

	@Override
	public void clear() {
		confidences.clear();
	}
	
	public double getMean() {
		if (confidences == null || confidences.isEmpty())
			return Double.NaN;
		return MathUtils.mean(confidences);
	}
	
	public double getMedian() {
		if (confidences == null || confidences.isEmpty())
			return Double.NaN;
		return MathUtils.median(confidences);
	}

	/**
	 * Returns the <b>mean</b> value
	 */
	@Override
	public double getScore() {
		return getMean();
	}

	@Override
	public Map<String, ? extends Object> asMap() {
		Map<String,Double> res = new HashMap<>();
		res.put(String.format("Mean Confidence (prediction width=%.2f)",intervalWidth), getMean());
		res.put(String.format("Median Confidence (prediction width=%.2f)",intervalWidth), getMedian());
		return res;
	}

	@Override
	public boolean goalIsMinimization() {
		return false;
	}

	public String toString() {
		double median = getMedian();
		if (Double.isFinite(median)) {
			return "Median confidence="+median + " given prediction region width="+intervalWidth;
		}
		return "Median confidence given prediction region width="+intervalWidth;
	}
	
}

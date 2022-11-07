/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;

import com.arosbio.commons.MathUtils;
import com.google.common.collect.ImmutableMap;

/**
 * This class handles aggregation of several metrics of a single type, e.g. computed by k-fold CV, and computes
 * the mean and standard deviation of these. 
 * @author staffan
 *
 * 
 */
public class MetricAggregation implements SingleValuedMetric {
	
	public final String STANDARD_DEVIATION_NAME_SUFFIX = "_SD"; 

	private List<Double> scores = new ArrayList<>();
	private List<Integer> counts = new ArrayList<>();
	
	private SingleValuedMetric type;
	
	public MetricAggregation(SingleValuedMetric metric) {
		type = metric.clone();
	}
	
	public void addSplitEval(SingleValuedMetric metric) {
		scores.add(metric.getScore());
		counts.add(metric.getNumExamples());
	}
	
	@Override
	public int getNumExamples() {
		return MathUtils.sumInts(counts);
	}

	@Override
	public void clear() {
		scores.clear();
		counts.clear();
	}

	@Override
	public boolean goalIsMinimization() {
		return type.goalIsMinimization();
	}

	@Override
	public String getName() {
		return type.getName();
	}

	@Override
	public double getScore() {
		return MathUtils.mean(scores);
	}
	
	public double getStd() {
		if (scores.isEmpty())
			return Double.NaN;
		StandardDeviation std = new StandardDeviation();
		double[] sc = new double[scores.size()];
		for (int i=0; i<sc.length; i++) {
			sc[i] = scores.get(i);
		}
		return std.evaluate(sc);
	}
	
	public SingleValuedMetric spawnNewMetricInstance() {
		return type.clone();
	}

	@Override
	public Map<String, ? extends Object> asMap() {
		return ImmutableMap.of(type.getName(), getScore(), 
				type.getName()+STANDARD_DEVIATION_NAME_SUFFIX, getStd());
	}

	@Override
	public MetricAggregation clone() {
		return new MetricAggregation(type);
	}

	@Override
	public String toString() {
		return String.format(Locale.ENGLISH,"%s: %.3f+/-%.03f", type.getName(),getScore(),getStd());
	}


}

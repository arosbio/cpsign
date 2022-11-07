/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics.regression;

import java.util.Map;

import com.arosbio.commons.mixins.Aliased;
import com.arosbio.commons.mixins.Described;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.google.common.collect.ImmutableMap;

/**
 * Mean Absolute Error
 * @author staffan
 *
 */
public class MAE implements SingleValuedMetric, PointPredictionMetric, 
	Described, Aliased {

	public static final String METRIC_NAME = "MAE";
	public static final String METRIC_DESCRIPTION = "Mean Absolute Error (MAE). One of the most common metrics used for evaulating the performance of a regression point prediction. Smaller values are preferable.";
	private static final String[] METRIC_ALIASES = new String[] {"MeanAbsErr"};
	
	// State
	private double absoluteResidualSum = 0.0;
	private int numExamples = 0;


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
	public Map<String,Double> asMap(){
		return ImmutableMap.of(METRIC_NAME, getScore());
	}
	
	@Override
	public String toString() {
		return Metric.toString(this);
	}

	public double getScore() {
		return absoluteResidualSum/numExamples;
	}

	@Override
	public void addPrediction(double trueLabel, double predicted) {
		absoluteResidualSum += Math.abs(trueLabel - predicted);
		numExamples++;
	}

	@Override
	public int getNumExamples() {
		return numExamples;
	}

	public MAE clone() {
		return new MAE();
	}

	@Override
	public void clear() {
		absoluteResidualSum = 0;
		numExamples = 0;
	}

	@Override
	public boolean goalIsMinimization() {
		return true;
	}

}

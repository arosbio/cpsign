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

import com.arosbio.commons.mixins.Described;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.google.common.collect.ImmutableMap;

public class RMSE implements SingleValuedMetric, PointPredictionMetric, Described {

	public static final String METRIC_NAME = "RMSE";
	public static final String METRIC_DESCRIPTION = "Root-Mean-Square Error (RMSE). One of the most common metrics used for evaluating the performance of regression point predictions. Smaller values are preferable.";

	private double seSum = 0.0;
	private int numExamples = 0;

	@Override
	public String getName() {
		return METRIC_NAME;
	}
	
	@Override
	public String getDescription() {
		return METRIC_DESCRIPTION;
	}

	public Map<String,Double> asMap(){
		return ImmutableMap.of(METRIC_NAME,getScore());
	}
	public String toString() {
		return Metric.toString(this);
	}

	public double getScore() {
		if (numExamples < 1)
			return Double.NaN;
		return Math.sqrt(seSum/numExamples);
	}

	@Override
	public void addPrediction(double trueLabel, double predictedMidpoint) {
		seSum += (trueLabel - predictedMidpoint)*(trueLabel - predictedMidpoint);
		numExamples++;
	}

	@Override
	public int getNumExamples() {
		return numExamples;
	}

	public RMSE clone() {
		return new RMSE();
	}

	@Override
	public void clear() {
		seSum = 0;
		numExamples = 0;
	}

	@Override
	public boolean goalIsMinimization() {
		return true;
	}

}

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.arosbio.commons.MathUtils;
import com.arosbio.commons.mixins.Described;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.google.common.collect.ImmutableMap;

public class R2 implements SingleValuedMetric, PointPredictionMetric, Described {
	
	public static final String METRIC_NAME = "R^2";
	public static final String METRIC_DESCRIPTION = "The coefficient of determination, where 1.0 is the perfect score and smaller values are worse.";
	
	private List<Double> ys = new ArrayList<>();
	private double ss_res = 0;
	
	@Override
	public void addPrediction(double trueLabel, double predictedMidpoint) {
		ss_res += (trueLabel - predictedMidpoint)*(trueLabel - predictedMidpoint);
		ys.add(trueLabel);
	}

	@Override
	public int getNumExamples() {
		return ys.size();
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
	public Map<String, Double> asMap() {
		return ImmutableMap.of(METRIC_NAME,getScore());
	}

	@Override
	public R2 clone() {
		return new R2();
	}

	@Override
	public void clear() {
		ys.clear();
		ss_res = 0;
	}

	@Override
	public double getScore() {
		double ss_tot = 0;
		double y_mean = MathUtils.mean(ys);
		for(double y : ys){
			ss_tot += (y -y_mean)*(y -y_mean);
		}
		return 1d - (ss_res / ss_tot);
		
	}

	@Override
	public boolean goalIsMinimization() {
		return false;
	}
	
	public String toString() {
		return Metric.toString(this);
	}

}

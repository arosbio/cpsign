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

import com.arosbio.ml.metrics.cp.CPMetric;
import com.arosbio.ml.metrics.regression.RegressionMetric;
import com.google.common.collect.Range;

/**
 * Metric that is based on computing the confidence <b>given</b> a specific prediction/confidence interval (CI) width 
 * @author staffan
 *
 */
public interface CIWidthBasedMetric extends CPMetric, RegressionMetric {
	
	public double getCIWidth();
	
	/**
	 * Set the Confidence Interval (CI) width to use
	 * @param width The desired width, which confidence should be calculated for
	 * @throws IllegalArgumentException If the width not strictly positive (required &gt;0)
	 * @throws IllegalStateException After predictions have been added to the metric, the width is not allowed to be altered
	 */
	public void setCIWidth(double width) throws IllegalArgumentException, IllegalStateException;
	
	public void addPrediction(double observedLabel, Range<Double> predictedInterval, double confidence);
}

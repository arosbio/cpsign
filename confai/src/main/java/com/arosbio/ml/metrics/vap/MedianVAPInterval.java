/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics.vap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.arosbio.commons.MathUtils;
import com.arosbio.ml.metrics.SingleValuedMetric;

public class MedianVAPInterval implements SingleValuedMetric, VAPMetric {

	public static final String METRIC_NAME = "Median P0-P1 width";
	public static final String METRIC_DESCRIPTION = "The Venn-ABERS algorithm produces two probabilities, and the P0-P1 width adds information about how much the prediction can be trusted. The smaller the difference between P0 and P1, the more stable the result is and the more the prediction can be trusted.";

	private List<Double> medianWidths = new ArrayList<>();

	@Override
	public boolean supportsMulticlass() {
		return false;
	}
	
	@Override
	public Map<String, Double> asMap() {
		Map<String,Double> res = new HashMap<>();
		res.put(METRIC_NAME, getScore());
		return res;
	}

	public List<Double> getMeanWidths(){
		return medianWidths;
	}

	public double getScore() {
		return MathUtils.median(medianWidths);
	}


	public String toString() {
		return SingleValuedMetric.toString(this);
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
	public void addPrediction(int trueLabel, 
			Map<Integer, Double> probabilities, 
			double meanIntervalWidth,
			double medianIntervalWidth) {
		medianWidths.add(medianIntervalWidth);
	}

	@Override
	public int getNumExamples() {
		return medianWidths.size();
	}

	public MedianVAPInterval clone() {
		return new MedianVAPInterval();
	}

	@Override
	public void clear() {
		medianWidths.clear();
	}

	@Override
	public boolean goalIsMinimization() {
		return true;
	}

}

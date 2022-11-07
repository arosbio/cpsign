/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics.classification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.arosbio.commons.MathUtils;
import com.arosbio.commons.mixins.Described;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.google.common.collect.ImmutableMap;

public class BalancedAccuracy implements PointClassifierMetric, SingleValuedMetric, Described {

	public final static String METRIC_NAME = "Balanced Accuracy"; 
	public final static String METRIC_DESCRIPTION = "Computes the accuracy of a standard classifier, balanced so that each class has the same impact on the score, thus works well for imbalanced data sets.";

	/**
	 * correct for each class, map of class -&gt; "number of corrects"
	 */
	private Map<Integer,Integer> corrects = new HashMap<>();
	/**
	 * total number of each class, map of class -&gt; number
	 */
	private Map<Integer,Integer> totals = new HashMap<>(); 

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
	public int getNumExamples() {
		if (totals.isEmpty())
			return 0;
		int tot=0;
		for (int num : totals.values()) {
			tot+=num;
		}
		return tot;
	}

	@Override
	public BalancedAccuracy clone() {
		return new BalancedAccuracy();
	}

	@Override
	public void clear() {
		corrects.clear();
		totals.clear();
	}

	@Override
	public boolean goalIsMinimization() {
		return false;
	}

	@Override
	public void addPrediction(int observedLabel, int predictedLabel) {
		if (observedLabel == predictedLabel)
			corrects.put(observedLabel, corrects.getOrDefault(observedLabel, 0)+1);
		totals.put(observedLabel, totals.getOrDefault(observedLabel, 0)+1);
	}
	
	public double calculate(final List<Integer> trueLabels, final List<Integer> predictions) 
			throws IllegalArgumentException {
		if (trueLabels.size() != predictions.size())
			throw new IllegalArgumentException("The true labels and predictions must be of equal size");
		if (trueLabels.isEmpty())
			return 0;
		Map<Integer,Integer> clsToNCorrect = new HashMap<>();
		Map<Integer,Integer> clsToCount = new HashMap<>();
		
		for (int i=0; i<trueLabels.size(); i++) {
			int cls = trueLabels.get(i);
			if (cls == predictions.get(i)) {
				clsToNCorrect.put(cls, clsToNCorrect.getOrDefault(cls, 0)+1);
			}
			clsToCount.put(cls, clsToCount.getOrDefault(cls, 0)+1);
		}
		List<Double> accs = new ArrayList<>();
		for (Map.Entry<Integer, Integer> labelCount : clsToCount.entrySet()) {
			int numCorr = clsToNCorrect.getOrDefault(labelCount.getKey(), 0);
			accs.add(((double)numCorr)/labelCount.getValue());
		}
		
		return MathUtils.mean(accs);
	}

	@Override
	public String toString() {
		return SingleValuedMetric.toString(this);
	}
	
	@Override
	public double getScore() {
		if (getNumExamples()==0)
			return Double.NaN;
		List<Double> accs = new ArrayList<>();
		for (Map.Entry<Integer, Integer> labelCount : totals.entrySet()) {
			int numCorr = corrects.getOrDefault(labelCount.getKey(), 0);
			accs.add(((double)numCorr)/labelCount.getValue());
		}
		
		return MathUtils.mean(accs);
	}

	@Override
	public Map<String, ? extends Object> asMap() {
		return ImmutableMap.of(METRIC_NAME,getScore());
	}

}

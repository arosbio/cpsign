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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.MathUtils;
import com.arosbio.commons.mixins.Aliased;
import com.arosbio.commons.mixins.Described;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.google.common.collect.ImmutableMap;

public class BalancedObservedFuzziness implements SingleValuedMetric, CPClassifierMetric, Aliased, Described { 

	public static final String METRIC_NAME = "Balanced Observed Fuzziness";
	public static final String METRIC_ALIAS = "BalancedOF";
	public final static String METRIC_DESCRIPTION = "Observed Fuzziness (OF) but balanced over all classes to give more information in cases with imbalanced data sets.";

	private Map<Integer, Double> labelWiseFuzzinessSum = new HashMap<>();
	private Map<Integer, Integer> numExamplesPerClass = new HashMap<>();

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
		return new String[] {METRIC_ALIAS};
	}
	
	@Override
	public boolean supportsMulticlass() {
		return true;
	}
	
	@Override
	public boolean goalIsMinimization() {
		return true;
	}

	public double getScore() {
		if (numExamplesPerClass.isEmpty())
			return Double.NaN;
		List<Double> fuzz = new ArrayList<>();
		for (int label : numExamplesPerClass.keySet()) {
			if (labelWiseFuzzinessSum.containsKey(label)) {
				fuzz.add(labelWiseFuzzinessSum.get(label) / numExamplesPerClass.get(label));
			}
		}
		return MathUtils.mean(fuzz);
	}

	public Map<String, Object> asMap(){
		return ImmutableMap.of(METRIC_NAME, getScore());
	}

	@Override
	public void addPrediction(int trueLabel, Map<Integer, Double> pValues) {
		double sum = 0d;
		for (Map.Entry<Integer, Double> val: pValues.entrySet()) {
			if (! val.getKey().equals(trueLabel))
				sum += val.getValue();
		}

		labelWiseFuzzinessSum.put(trueLabel, labelWiseFuzzinessSum.getOrDefault(trueLabel,0d)+sum);
		numExamplesPerClass.put(trueLabel, numExamplesPerClass.getOrDefault(trueLabel,0)+1);
	}

	@Override
	public int getNumExamples() {
		return CollectionUtils.sumInts(numExamplesPerClass.values());
	}

	public String toString() {
		return SingleValuedMetric.toString(this);
	}

	public BalancedObservedFuzziness clone() {
		BalancedObservedFuzziness clone = new BalancedObservedFuzziness();
		return clone;
	}

	public void clear() {
		labelWiseFuzzinessSum.clear();
		numExamplesPerClass.clear();
	}


}

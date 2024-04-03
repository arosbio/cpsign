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

import java.util.Map;

import com.arosbio.commons.mixins.Aliased;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.google.common.collect.ImmutableMap;

public class ObservedFuzziness implements SingleValuedMetric, 
	CPClassifierMetric, Aliased { 

	public static final String METRIC_NAME = "Observed Fuzziness";
	public static final String METRIC_ALIAS = "OF";
	public final static String METRIC_DESCRIPTION = "Observed Fuzziness (OF) is a confidence-independent metric for conformal classifiers. Smaller values are preferable.";

	private double fuzzinessSum = 0d;
	private int numExamples = 0;

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
		return fuzzinessSum/numExamples;
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

		fuzzinessSum += sum;
		numExamples++;
	}

	@Override
	public int getNumExamples() {
		return numExamples;
	}

	public String toString() {
		return SingleValuedMetric.toString(this);
	}

	public ObservedFuzziness clone() {
		return new ObservedFuzziness();
	}

	public void clear() {
		fuzzinessSum = 0;
		numExamples = 0;
	}


}

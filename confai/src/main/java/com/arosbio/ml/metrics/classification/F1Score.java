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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.arosbio.commons.mixins.Described;
import com.arosbio.ml.ClassificationUtils;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.google.common.collect.ImmutableMap;

public class F1Score implements SingleValuedMetric, PointClassifierMetric, ProbabilisticMetric, Described {

	public static final String METRIC_NAME = "F1Score";
	private static final String MICRO_SUFFIX = "_micro";
	private static final String MACRO_SUFFIX = "_macro";
	private static final String WEIGHTED_SUFFIX = "_weighted";
	public static final String METRIC_DESCRIPTION = "F1 score, when used for optimization the Macro F1 score is used, but calculates both the micro and the weighted F1 score as well.";

	private int numPredictionsDone=0;
	boolean setupDone = false;
	private Map<Integer,Counter> counters = new HashMap<>();

	private static class Counter {
		int tp=0, fp=0, fn=0;

		public double calculate(){
			if (tp==0)
				return 0;
			return tp/(tp+ 0.5*(fp+fn));
		}
	}

	// GETTERS AND SETTERS

	public boolean supportsMulticlass() {
		return true;
	}

	@Override
	public String getDescription() {
		return METRIC_DESCRIPTION;
	}

	private void setupCounters(Collection<Integer> labels){
		for (int l : labels){
			counters.put(l, new Counter());
		}
		setupDone = true;
	}
	private void checkAdd(int label){
		if (!counters.containsKey(label)){
			counters.put(label, new Counter());
		}
	}

	// ADD PREDICTIONS
	@Override
	public void addPrediction(int observedLabel, Map<Integer, Double> probabilities) {
		if (!setupDone){
			setupCounters(probabilities.keySet());
		}
		addPrediction(observedLabel, ClassificationUtils.getPredictedClass(probabilities));
	}

	/*
	 * Main method for adding the predictions
	 */
	@Override
	public void addPrediction(int observedLabel, int predictedLabel) {
		if (!setupDone){
			checkAdd(predictedLabel);
			checkAdd(observedLabel);
		}
		
		for (Map.Entry<Integer,Counter> kv : counters.entrySet()){
			int l = kv.getKey();
			if (l == observedLabel){
				// either true positive or false negative
				if (observedLabel == predictedLabel)
					kv.getValue().tp++;
				else
					kv.getValue().fn++;
			} else if (l == predictedLabel){
				// false positive, otherwise l==observedLabel
				kv.getValue().fp++;
			}
		}

		numPredictionsDone++;
	}

	public double getMacroF1(){
		double sum = 0;
		for (Counter c : counters.values()){
			sum += c.calculate();
		}
		if (sum==0)
			return sum;
		return sum/counters.size();
	}

	public double getMicroF1(){
		Counter tmp = new Counter();
		for (Counter c : counters.values()){
			tmp.fn += c.fn;
			tmp.fp += c.fp;
			tmp.tp += c.tp;
		}
		return tmp.calculate();
	}

	public double getWeightedF1(){
		int denom = 0;
		double sum = 0;
		for (Counter c : counters.values()){
			int num = (c.tp+c.fn);
			sum += num*c.calculate();
			denom += num;
		}
		return sum/denom;
	}

	@Override
	public double getScore() {
		if (numPredictionsDone == 0)
			return Double.NaN;
		return getMacroF1();
	}

	@Override
	public int getNumExamples() {
		return numPredictionsDone;
	}

	public String toString() {
		return SingleValuedMetric.toString(this);
	}

	@Override
	public Map<String, ? extends Object> asMap() {
		return ImmutableMap.of(METRIC_NAME+MACRO_SUFFIX, getMacroF1(), 
			METRIC_NAME+MICRO_SUFFIX, getMicroF1(),
			METRIC_NAME+WEIGHTED_SUFFIX,getWeightedF1());
	}

	@Override
	public String getName() {
		return METRIC_NAME;
	}

	@Override
	public F1Score clone() {
		return new F1Score();
		// clone.positiveClass=positiveClass;
		// return clone;
	}

	@Override
	public void clear() {
		counters.clear();
		numPredictionsDone = 0;
	}

	@Override
	public boolean goalIsMinimization() {
		return false;
	}


}

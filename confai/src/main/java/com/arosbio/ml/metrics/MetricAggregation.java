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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;

import com.arosbio.commons.MathUtils;
import com.arosbio.commons.TypeUtils;
import com.google.common.collect.ImmutableMap;

/**
 * This class handles aggregation of several metrics of a single type, e.g. computed by k-fold CV, and computes
 * the mean and standard deviation of these. 
 * @author staffan
 *
 * 
 */
@SuppressWarnings("unchecked")
public class MetricAggregation<T extends SingleValuedMetric> implements SingleValuedMetric {
	
	public final String STANDARD_DEVIATION_NAME_SUFFIX = "_SD"; 

	private List<Double> scores = new ArrayList<>();
	private Map<String,List<Double>> mapValues = new HashMap<>();
	private List<Integer> counts = new ArrayList<>();
	
	private T type;
	
	public MetricAggregation(T metric) {
		type = (T) metric.clone();
	}

	public void addSplitEval(SingleValuedMetric metric) throws IllegalArgumentException {
		if (metric.getClass() != type.getClass()){
			throw new IllegalArgumentException("Invalid metric sent to aggregate");
		}
		scores.add(metric.getScore());
		counts.add(metric.getNumExamples());
		Map<String,? extends Object> res = metric.asMap();
		if (res.size()>1){
			// only care in case we have several values
			for (Map.Entry<String,?> kv : res.entrySet()){
				if (!mapValues.containsKey(kv.getKey())){
					mapValues.put(kv.getKey(), new ArrayList<>());
				}
				mapValues.get(kv.getKey()).add(TypeUtils.asDouble(kv.getValue()));
			}
		}
	}
	
	@Override
	public int getNumExamples() {
		return MathUtils.sumInts(counts);
	}

	@Override
	public void clear() {
		scores.clear();
		counts.clear();
		mapValues.clear();
	}

	@Override
	public boolean goalIsMinimization() {
		return type.goalIsMinimization();
	}

	@Override
	public String getName() {
		return type.getName();
	}

	public List<Double> getScores(){
		return new ArrayList<>(scores);
	}
	@Override
	public double getScore() {
		return MathUtils.mean(scores);
	}
	
	public double getStd() {
		if (scores.isEmpty())
			return Double.NaN;
		return getStd(scores); 
	}
	private static double getStd(List<Double> values){
		if (values.isEmpty())
			return Double.NaN;
		StandardDeviation std = new StandardDeviation();
		double[] sc = new double[values.size()];
		for (int i=0; i<sc.length; i++) {
			sc[i] = values.get(i);
		}
		return std.evaluate(sc);
	}
	
	public T spawnNewMetricInstance() {
		return (T) type.clone();
	}

	@Override
	public Map<String, ? extends Object> asMap() {
		if (!mapValues.isEmpty()){
			// if we have several values
			Map<String,Double> result = new LinkedHashMap<>();
			for (Map.Entry<String,List<Double>> kv : mapValues.entrySet()){
				result.put(kv.getKey(), MathUtils.mean(kv.getValue()));
				result.put(kv.getKey()+STANDARD_DEVIATION_NAME_SUFFIX, getStd(kv.getValue()));
			}
			return result;
		} else {
			// Only return the scores
			return ImmutableMap.of(type.getName(), getScore(), 
					type.getName()+STANDARD_DEVIATION_NAME_SUFFIX, getStd());
		}
	}

	@Override
	public MetricAggregation<T> clone() {
		return new MetricAggregation<>(type);
	}

	@Override
	public String toString() {
		if (!scores.isEmpty())
			return String.format(Locale.ENGLISH,"%s: %.3f+/-%.03f", type.getName(),getScore(),getStd());
		return getName();
	}


}

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.commons;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;

import com.google.common.collect.Range;

public class MathUtils {

	public static boolean equals(double v1, double v2) {
		if (Double.isNaN(v1) && Double.isNaN(v2))
			return true;
		if (Double.isInfinite(v1) && Double.isInfinite(v2)) {
			return (v1>0 && v2>0) ||(v1<0 && v2<0); 
		}
		return Math.abs(v1-v2) < 0.00001;
	}

	/**
	 * Calculate the factorial of n.
	 *
	 * @param n the number to calculate the factorial of.
	 * @return n! - the factorial of n.
	 */
	public static int fact(int n) {

		// Base Case: 
		//    If n <= 1 then n! = 1.
		if (n <= 1) {
			return 1;
		}
		// Recursive Case:  
		//    If n > 1 then n! = n * (n-1)!
		else {
			return n * fact(n-1);
		}
	}

	public static Pair<Double,Double> roundTo3significantFigures(Pair<Double,Double> val){
		return ImmutablePair.of(roundTo3significantFigures(val.getLeft()), roundTo3significantFigures(val.getRight()));
	}

	public static Range<Double> roundTo3significantFigures(Range<Double> val){
		return Range.range(roundTo3significantFigures(val.lowerEndpoint()), val.lowerBoundType(),
				roundTo3significantFigures(val.upperEndpoint()), val.upperBoundType());
	}

	public static double roundTo3significantFigures(double val){
		return roundToNSignificantFigures(val, 3);
	}

	public static double roundToNSignificantFigures(double val, int n){
		try {
			BigDecimal bd = new BigDecimal(val);
			bd = bd.round(new MathContext(n));
			return bd.doubleValue();
		} catch (NumberFormatException e) {
			return val;
		}
	}

	public static double median(List<Double> values){
		return median(CollectionUtils.toArray(values));
	}

	public static double median(double... values){
		if (values==null || values.length==0)
			return Double.NaN;
		if (values.length==1)
			return values[0];

		double median = new Percentile().evaluate(values, 50);
		
		if (Double.isNaN(median)) {
			// Check if all values are positive/negative infinity
			int numPosInf = 0, numNegInf = 0;

			for (Double d : values) {
				if (!Double.isInfinite(d)) {
					break;
				}
				if (d >0)
					numPosInf++;
				else if (d < 0)
					numNegInf++;
			}
			if (numPosInf > 0 && numNegInf == 0)
				return Double.POSITIVE_INFINITY;
			else if (numNegInf > 0 && numPosInf == 0)
				return Double.NEGATIVE_INFINITY;
		}
		
		return median;
	}

	public static <T> List<T> filterNull(List<T> list) {
		List<T> filtered = new ArrayList<>();
		for(T elem: list){
			if(elem != null)
				filtered.add(elem);
		}
		return filtered;
	}

	public static int sumInts(Collection<Integer> values) {
		return values.stream().mapToInt(i->i).sum();
	}

	public static double sumDoubles(Collection<Double> values) {
		return values.stream().mapToDouble(i->i).sum();
	}

	/**
	 * Calculate the average of a collection of values, using an iterative
	 * approach
	 * @param <T> the type 
	 * @param values values to average
	 * @return the average
	 * @see <a href="https://www.heikohoffmann.de/htmlthesis/node134.html">Iterative mean reference</a>
	 */
	public static <T extends Number> double mean(Collection<T> values) {
		Objects.requireNonNull(values, "cannot calculate mean on null");
		if (values.isEmpty())
			return Double.NaN;

		double avg = 0d;

		int t = 1;
		for (T v : values){
			double dv = v.doubleValue();
			if (Double.isInfinite(dv) || Double.isNaN(dv)) {
				return calcMeanWithInfsOrNaN(values.stream().mapToDouble(d -> d.doubleValue()).toArray());
			}
			avg += (v.doubleValue() - avg) / t;
			t++;
		}
		
		return avg;
	}

	// Utility method in case there are any Infs or NaN values in the input
	private static double calcMeanWithInfsOrNaN(double[] values){
		boolean containsPosInf = false;
		boolean containsNegInf = false;
		for (double v : values){
			if (Double.isNaN(v)){
				return Double.NaN;
			} else if (Double.isInfinite(v)){
				if (v>0)
					containsPosInf = true;
				else
					containsNegInf = true;
			}
		}
		if (containsNegInf && containsPosInf){
			return Double.NaN;
		} else if (containsNegInf){
			return Double.NEGATIVE_INFINITY;
		} 
		return Double.POSITIVE_INFINITY;

	}

	public static double mean(double... values) {
		Objects.requireNonNull(values, "cannot calculate mean on null");
		if (values.length == 0)
			return Double.NaN;
		
		double avg = 0d;
		int t = 1;
		for (double v : values){
			if (Double.isNaN(v) || Double.isInfinite(v)){
				return calcMeanWithInfsOrNaN(values);
			}
			avg += (v - avg) / t;
			t++;
		}

		return avg;
	}


	public static <T extends Comparable<T>> T min(Collection<T> values) {
		if (values.isEmpty())
			return null;
		Iterator<T> iterator = values.iterator();
		T currMin = iterator.next();
		while(iterator.hasNext()) {
			T val = iterator.next();
			if (val.compareTo(currMin) < 0)
				currMin = val;
		}
		return currMin;
	}

	public static <T extends Comparable<T>> T max(Collection<T> values) {
		if (values.isEmpty())
			return null;
		Iterator<T> iterator = values.iterator();
		T currMax = iterator.next();
		while(iterator.hasNext()) {
			T val = iterator.next();
			if (val.compareTo(currMax) > 0)
				currMax = val;
		}
		return currMax;
	}


	public static int max(int[] values) throws IllegalArgumentException {
		if (values==null || values.length==0)
			throw new IllegalArgumentException("No values given");
		int tmpMax = values[0];
		for (int i=1; i<values.length; i++){
			if (values[i]>tmpMax)
				tmpMax = values[i];
		}
		return tmpMax;
	}

	public static double truncate(double val, double min, double max) {
		return Math.max(min, Math.min(max, val));
	}


	public static double geometricMean(Collection<Double> values){
		Optional<Double> avgLog = avgLogs(values);
		if (avgLog.isEmpty())
			return 0;
		return Math.exp(avgLog.get());
	}

	/**
	 * Helper method for {@link #geometricMean(Collection)}, to make a single
	 * pass over all values and compute the average of the logarithm of the values.
	 * Calculates the average using an iterative approach to avoid overflow/underflow issues.
	 * Note that computing when one value is ==0 the log is not defined, then {@code Optional.empty()} is returned
	 * @param values values to average
	 * @return the average of the log of each value
	 */
	protected static Optional<Double> avgLogs(Collection<Double> values){
		Objects.requireNonNull(values, "cannot calculate mean on null");
		if (values.isEmpty())
			return Optional.empty();

		double avg = 0d;

		int t = 1;
		for (double v : values){
			if (v == 0)
				return Optional.empty();
			avg += (Math.log(v) - avg) / t;
			t++;
		}
		return Optional.of(avg);
	}

	public static int multiplyAllTogetherInt(Collection<Integer> values){
		int res = 1;
		for (double v : values){
			res *= v;
		}
		return res;
	}

	public static Map<Object, Number> roundValues(Map<?, Double> inputMap){
		Map<Object, Number> resultMap = new HashMap<>();

		for(Entry<?, Double> entry: inputMap.entrySet()){
			resultMap.put(entry.getKey(), roundTo3significantFigures(entry.getValue()));
		}

		return resultMap;
	}

	public static <K,V> Map<K,Object> roundAllValues(Map<K, V> inputMap){
		Map<K, Object> resultMap = new LinkedHashMap<>();

		for (Map.Entry<K, V> entry: inputMap.entrySet()){
			V val = entry.getValue();
			if (val instanceof Double || val instanceof Float)
				resultMap.put(entry.getKey(), roundTo3significantFigures((Double)val));
			else if (val instanceof Map<?, ?>)
				resultMap.put(entry.getKey(), roundAllValues((Map<?,?>)val));
			else if (val instanceof List<?>) {
				resultMap.put(entry.getKey(), roundAllValues((List<?>)val));
			} else
				resultMap.put(entry.getKey(), val);
		}

		return resultMap;
	}
	
	public static Map<String,Double> roundAll(Map<String,Double> input){
		Map<String,Double> result = new LinkedHashMap<>();;
		for (Map.Entry<String,Double> kv: input.entrySet()){
			result.put(kv.getKey(), roundTo3significantFigures(kv.getValue()));
		}
		
		return result;
	}

	public static <T> List<Object> roundAllValues(List<T> list){
		List<Object> resList = new ArrayList<>();
		for (T val: list) {
			if (val instanceof Double)
				resList.add(roundTo3significantFigures((Double)val));
			else if (val instanceof Float) 
				resList.add(roundTo3significantFigures((Double)val));
			else if (val instanceof Map<?, ?>)
				resList.add(roundAllValues((Map<?,?>)val));
			else if (val instanceof List<?>)
				resList.add(roundAllValues((List<?>)val));
			else
				resList.add(val);
		}

		return resList;
	}


	public static <T> Map<T, Double> normalizeMap(Map<T, Double> colorMap, double rangeStart, double rangeEnd){
		if(rangeStart >= rangeEnd)
			throw new IllegalArgumentException("The lower range cannot be larger or equal to the upper range");
		double posInterval = (rangeStart>0? rangeEnd-rangeStart : Math.abs(rangeEnd));
		double negInterval = (rangeEnd < 0? Math.abs(rangeEnd) + Math.abs(rangeStart): Math.abs(rangeStart));

		for(Entry<T, Double> entry: colorMap.entrySet()){
			double value = entry.getValue();
			double newVal;

			if(value > rangeEnd)
				newVal = 1.0; // Cap upper
			else if(value < rangeStart)
				newVal= -1.0; // Cap lower
			else if(value>= 0 && rangeStart>0)
				newVal = (value-rangeStart)/posInterval;
			else if(value>= 0)
				newVal= value/posInterval;
			else if(value < 0 && rangeEnd <0)
				newVal = (value-rangeEnd)/negInterval;
			else
				newVal = value/negInterval;
			colorMap.put(entry.getKey(), newVal);
		}

		return colorMap;
	}

	public static <K> boolean equals(Map<K,Double> m, Map<K,Double> m2, double delta){
		if(m.size() != m2.size())
			return false;
		for(K key: m.keySet()){
			if(!m2.containsKey(key))
				return false;
			if(Math.abs(m.get(key)-m2.get(key))>delta)
				return false;
		}
		return true;
	}

	public static boolean keepFalse(boolean a, boolean b){
		return ! (!a || !b);
	}

}

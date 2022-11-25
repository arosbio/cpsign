/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.transform.feature_selection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.rank.Median;

import com.arosbio.commons.CollectionUtils;

public class FeatureSelectUtils {
	
	/**
	 * Sorts and returns the smallest values, but keeping <code>n</code> in the list. 
	 * The returned indices are thus the once that should be removed by the {@link FeatureSelector}
	 * @param values the values for all features
	 * @param n the number to return
	 * @return the {@code n} indices with the smallest values
	 */
	public static List<Integer> getSmallestKeepingN(List<CollectionUtils.IndexedValue> values, int n){
		if (n >= values.size()) {
			// Return empty list - we cannot remove any more if we should keep n
			return new ArrayList<>();
		} else if (n == 0) {
			// remove ALL
			return extractIndicesAndSort(values);
		}
		
		// Sort descending 
		Collections.sort(values, Comparator.reverseOrder());
		
		// take from n->last index (the smallest values)
		List<Integer> smallest = extractIndicesAndSort(values.subList(n, values.size()));
		
		// Check that there's still no 0's in the part that should be kept
		if (values.get(n-1).value < 1e-10) {
			// there's still 0os in the N we should keep,
			smallest.addAll(getSmallerThanThreshold(values.subList(0, n), 1e-10));
			Collections.sort(smallest);
		}
		
		return smallest;
	}
	
	public static List<Integer> getSmallerThanThreshold(List<CollectionUtils.IndexedValue> values, double threshold) {
		List<Integer> filteredIndices = new ArrayList<>();
		for (CollectionUtils.IndexedValue iv : values) {
			if (iv.value <= threshold) {
				filteredIndices.add(iv.index);
			}
		}
		Collections.sort(filteredIndices);
		
		return filteredIndices;
	}
	
	public static List<Integer> getSmallerThanMean(List<CollectionUtils.IndexedValue> values){
		Mean mean = new Mean();
		
		for (CollectionUtils.IndexedValue iv : values) {
			mean.increment(iv.value);
		}
		return getSmallerThanThreshold(values, mean.getResult());
	}
	
	public static List<Integer> getSmallerThanMedian(List<CollectionUtils.IndexedValue> values){
		Median median = new Median();
		
		double[] valArray = new double[values.size()];
		for (int i=0; i<valArray.length;i++) {
			valArray[i] = values.get(i).value;
		}
		return getSmallerThanThreshold(values, median.evaluate(valArray));
	}
	
	public static List<Integer> extractIndicesAndSort(List<CollectionUtils.IndexedValue> values){
		List<Integer> res = new ArrayList<>();
		for (CollectionUtils.IndexedValue v : values) {
			res.add(v.index);
		}
		Collections.sort(res);
		return res;
	}

}

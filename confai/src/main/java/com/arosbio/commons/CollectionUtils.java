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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import com.google.common.collect.Range;

public class CollectionUtils {
	
	/**
	 * IndexedValues are sorted descending depending on their value. 
	 * Used e.g. by the FeatureSelectors for keeping the indices with most variance/importance 
	 * @author staffan
	 *
	 */
	public static class IndexedValue implements Cloneable, Comparable<IndexedValue> {
		public final int index;
		public final double value;
	
		public IndexedValue(int index, double value) {
			this.index = index;
			this.value = value;
		}
	
		public IndexedValue withValue(double newValue){
			return new IndexedValue(index, newValue);
		}
	
		@Override
		public int compareTo(IndexedValue o) {
			int cmp = Double.compare(this.value, o.value);
			return cmp != 0 ? cmp :  this.index - o.index ;
		}
	
		public String toString() {
			return String.format("%d:%s",index,value);
		}
		
		public IndexedValue clone() {
			return new IndexedValue(index, value);
		}
	
	}

	public static int countTrueValues(Collection<Boolean> values) {
		int sum = 0;
		for (boolean b : values) {
			if (b)
				sum++;
		}
		return sum;
	}

	/**
	 * Uses the {@link #listRange(double, double, double)} using default {@code step} of 1
	 * @param start the start value
	 * @param end the end value
	 * @return a List of values between start and end
	 */
	public static List<Double> listRange(double start, double end){
		return listRange(start,end, 1d);
	}

	public static List<Double> listRange(double start, double end, double step){
		// Special treat if start and end equals
		if (Math.abs(start-end)<0.00000001){
			return Arrays.asList(start);
		}
		
		// validate input
		if (Math.abs(step) <= 0.0001){
			throw new IllegalArgumentException("step parameter cannot be 0");
		}
		if (start > end && step >= 0){
			throw new IllegalArgumentException(String.format("Invalid range enumeration {start=%s,end=%s,step=%s}", start,end,step));
		} else if (start < end && step <= 0){
			throw new IllegalArgumentException(String.format("Invalid range enumeration {start=%s,end=%s,step=%s}", start,end,step));
		}

		if (start < end){

			if ( (end-start) / step > 1000 ) {
				throw new IllegalArgumentException("Not allowed to create a list range with more than 1000 entries");
			}

			List<Double> result = new ArrayList<>((int) ((end-start)/step));

			int i=0;
			double nextValue = start;
			while (i < 1001 && (nextValue < end || MathUtils.equals(nextValue, end))) {
				result.add(nextValue);
				i++;
				nextValue = start + i*step;
			}
			return result;
		} else {
			// start > end (equals treated before)
			if ( (start-end) / step > 1000 ) {
				throw new IllegalArgumentException("Not allowed to create a list range with more than 1000 entries");
			}

			List<Double> result = new ArrayList<>((int) ((end-start)/step));

			int i=0;
			double nextValue = start;
			while (i < 1001 && (nextValue > end || MathUtils.equals(nextValue, end))) {
				result.add(nextValue);
				i++;
				nextValue = start + i*step;
			}
			return result;

		}

	}
	
	public static List<Double> listRange(double start, double stop, double step, double base){
		return listRange(start,stop,step)
				.stream()
				.map(e -> Math.pow(base, e))
				.collect(Collectors.toList());
	}

	public static List<Integer> listRange(int start, int end){
		return listRange(start, end, 1);
	}

	public static List<Integer> listRange(int start, int end, int step){
		// Special treat if start and end equals
		if (start==end){
			return Arrays.asList(start);
		}
		// validate input
		if (Math.abs(step) <= 0.0001){
			throw new IllegalArgumentException("step parameter cannot be 0");
		}
		if (start > end && step >= 0){
			throw new IllegalArgumentException(String.format("Invalid range enumeration {start=%s,end=%s,step=%s}", start,end,step));
		} else if (start < end && step <= 0){
			throw new IllegalArgumentException(String.format("Invalid range enumeration {start=%s,end=%s,step=%s}", start,end,step));
		}

		if (start < end){

			if ( (double)(end-start) / step > 1000 ) {
				throw new IllegalArgumentException("Not allowed to create a list range with more than 1000 entries");
			}

			List<Integer> result = new ArrayList<>((int) ((end-start)/step));

			for (int i=start; i<=end; i+= step) {
				result.add(i);
			}

			return result;
		} else {
			// start > end
			if ( (double)(start-end) / step > 1000 ) {
				throw new IllegalArgumentException("Not allowed to create a list range with more than 1000 entries");
			}

			List<Integer> result = new ArrayList<>((int) ((end-start)/step));
			for (int i=start; i>=end; i+= step) {
				result.add(i);
			}
			
			return result;

		}

	}

	public static <T> Pair<List<T>,List<T>> splitRandomly(List<T> input, int numInSecond, long seed){
		List<T> first = new ArrayList<>(input), // Shallow copy all to the first
			second = new ArrayList<>(numInSecond);
		
		Random rng = new Random(seed);
		for (int i=0; i<numInSecond; i++) {
			int pickIX = rng.nextInt(first.size());
			second.add(first.remove(pickIX));
		}
		return Pair.of(first,second);
	}
	
	public static boolean containsNullOrNaN(Collection<Double> input) {
		for (Double n : input) {
			if (n == null || n.isNaN()) {
				return true;
			}
		}
		return false;
	}
	
	public static <T> boolean containsNull(Collection<T> input) {
		for (T t : input) {
			if (t == null)
				return true;
		}
		return false;
	}
	
	public static List<Double> filterNullOrNaN(Collection<Double> input) {
		List<Double> res = new ArrayList<>();
		for (Double n : input) {
			if (n != null && Double.isFinite(n))
				res.add(n);
		}
		return res;
	}
	
	public static double[] toArray(List<Double> input) {
		if (input == null || input.isEmpty())
			return new double[0];
		double[] arr = new double[input.size()];
		for (int i=0; i<arr.length; i++)
			arr[i] = input.get(i);
		return arr;
	}
	
	public static int[] toIntArray(List<Integer> input) {
		if (input == null || input.isEmpty())
			return new int[0];
		int[] arr = new int[input.size()];
		for (int i=0; i<arr.length; i++)
			arr[i] = input.get(i);
		return arr;
	}


	public static <T> Map<T,Integer> countFrequencies(Collection<T> input){
		Map<T,Integer> freqs = new HashMap<>();

		for (T t : input) {
			freqs.put(t, freqs.getOrDefault(t, 0)+1);
		}

		return freqs;
	}
	
	public static int countValuesSmallerThan(Collection<Integer> vals, int threshold) {
		int count=0;
		for (int v : vals) {
			if (v < threshold)
				count++;
		}
		return count;
	}

	public static <T extends Comparable<T>> List<T> getUnique(Collection<T> input){
		Set<T> asSet = new LinkedHashSet<>(input); // Keep ordering using linked hash set
		List<T> asList = new ArrayList<>(asSet);
		return asList;
	}

	public static <T extends Comparable<T>> List<T> getUniqueAndSorted(Collection<T> input){
		return input.stream()
			.distinct()
			.sorted()
			.collect(Collectors.toCollection(ArrayList::new));
	}

	public static <T extends Comparable<? super T>>
	boolean isSorted(Iterable<T> iterable) {
		Iterator<T> iter = iterable.iterator();
		if (!iter.hasNext()) {
			return true;
		}
		T t = iter.next();
		while (iter.hasNext()) {
			T t2 = iter.next();
			if (t.compareTo(t2) > 0) {
				return false;
			}
			t = t2;
		}
		return true;
	}

	public static <T extends Comparable<T>> List<T> sort(Collection<T> c){
		List<T> list = new ArrayList<>(c);
		Collections.sort(list);
		return list;
	}
	
	public static <T> List<Object> toObjectList(List<T> list){
		List<Object> resList = new ArrayList<>();
		for (T element: list) {
			resList.add((Object)element);
		}
		return resList;
	}

	@SuppressWarnings("unchecked")
	public static <T, T2> List<T2> toList(List<T> l) {
		List<T2> r = new ArrayList<>();
		for (T e: l) {
			r.add((T2) e);
		}
		return r;
	}

	public static<T> List<Number> toNumberList(List<T> list){
		List<Number> resList = new ArrayList<>();
		for (T elem: list) {
			resList.add((Number) elem);
		}
		return resList;
	}

	public static List<Double> arrToList(double[] arr){
		List<Double> lst =  new ArrayList<>(arr.length);
		for(int i=0; i<arr.length;i++)
			lst.add(arr[i]);
		return lst;
	}

	public static List<Integer> arrToList(int[] arr){
		List<Integer> lst = new ArrayList<>(arr.length);
		for(int i=0; i<arr.length;i++)
			lst.add(arr[i]);
		return lst;
	}
	
	public static double sum(Collection<Double> c) {
		double sum = 0;
		for (Double d : c) {
			sum+= d;
		}
		return sum;
	}
	
	public static int sumInts(Collection<Integer> c) {
		int sum = 0;
		for (int val : c) {
			sum+= val;
		}
		return sum;
	}

	public static <T> Set<T> getUniqueFromFirstSet(Set<T> s1, Set<T> s2){
		Set<T> uniques = new HashSet<>();
		Iterator<T> iter = s1.iterator();
		while(iter.hasNext()){
			T item = iter.next();
			if(! s2.contains(item)){
				uniques.add(item);
			}
		}
		return uniques;
	}


	public static <T> List<List<T>> partitionStatic(List<T> input, int numberPerList) {
		if (input == null || input.isEmpty())
			throw new IllegalArgumentException("empty list cannot be partitioned");
		if (numberPerList <= 0)
			throw new IllegalArgumentException("Number per list cannot be 0 or less");

		List<List<T>> result = new ArrayList<>();
		int currIndex = 0;
		while (currIndex + numberPerList < input.size()) {
			result.add(input.subList(currIndex, currIndex+numberPerList));
			currIndex+=numberPerList;
		}
		// Add the rest (if any)
		if (currIndex < input.size())
			result.add(input.subList(currIndex, input.size()));

		return result;
	}

	public static <T> List<List<T>> partition(List<T> input, int folds) {
		if (input == null || input.isEmpty())
			throw new IllegalArgumentException("empty list cannot be partitioned");
		if (folds <= 0)
			throw new IllegalArgumentException("Number of folds cannot be 0 or less");

		List<Integer> indexes = getFoldSplits(input, folds);
		List<List<T>> partitions = new ArrayList<>();

		for (int i=1; i<indexes.size(); i++) {
			partitions.add(input.subList(indexes.get(i-1), indexes.get(i)));
		}

		return partitions;
	}

	public static <T> List<Integer> getFoldSplits(List<T> list, int folds){
		// Decide the start and end-indexes 
		int defaultFoldSize = (int) Math.floor(((double)list.size())/folds);
		if (defaultFoldSize < 1)
			throw new IllegalArgumentException("Using " + folds + " folds on a list of size " + list.size() + " give less than 1 record per list");
		int recordsLeftToAssign = list.size() - folds*defaultFoldSize;

		int currentSplitIndex = 0;
		List<Integer> indexSplits = new ArrayList<>();
		indexSplits.add(currentSplitIndex);
		for(int i=0; i<folds-1; i++){
			currentSplitIndex += defaultFoldSize;
			if (recordsLeftToAssign>0) {
				currentSplitIndex++;
				recordsLeftToAssign--;
			}
			indexSplits.add(currentSplitIndex);
		}
		indexSplits.add(list.size());

		return indexSplits;
	}

	

	public static <T> List<List<T>> getDisjunctSets(List<T> list, int splits, boolean allowEmptySets){
		if (splits < 2)
			throw new IllegalArgumentException("Number of folds must be >=2");
		if (! allowEmptySets && splits > list.size())
			throw new IllegalArgumentException("Cannot create " + splits + " out of " + list.size() + " records");

		int defaultFoldSize = (int) Math.floor(((double)list.size())/splits);
		int recordsLeftToAssign = list.size() - splits*defaultFoldSize;

		List<List<T>> sets = new ArrayList<>();

		int start = 0, end = defaultFoldSize;
		for (int i=0; i<splits-1; i++) {
			end = start + defaultFoldSize;
			if (recordsLeftToAssign > 0) {
				end++;
				recordsLeftToAssign--;
			}
			sets.add(list.subList(start, end));
			start = end;
		}
		// The last fold is simply the remaining indices
		sets.add(list.subList(start, list.size()));

		return sets;

	}

	public static <C extends Collection<?>> List<Integer> getSortedIndicesBySize(List<C> lst, boolean ascending){
		List<IndexedValue> indices = new ArrayList<>(lst.size());

		for (int i=0; i<lst.size(); i++){
			indices.add(new IndexedValue(i, lst.get(i) != null ? lst.get(i).size() : 0));
		}

		if (ascending){
			Collections.sort(indices);
		} else {
			Collections.sort(indices, Comparator.reverseOrder());
		}

		return indices.stream().map(i -> i.index).collect(Collectors.toList());
	}


	public static <T> List<T> sortBy(List<T> input, List<Integer> indices) 
		throws IllegalArgumentException, NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(input);
		Objects.requireNonNull(indices);
		if (input.size() != indices.size())
			throw new IllegalArgumentException("List to sort and indices must have same length");
		List<T> sorted = new ArrayList<>();
		
		for (int index : indices){
			sorted.add(input.get(index));
		}

		return sorted;
	}

	public static <T> List<T> getIndices(List<T> input, List<Integer> indices) 
		throws IllegalArgumentException, NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(input);
		Objects.requireNonNull(indices);

		List<T> sorted = new ArrayList<>();		
		for (int index : indices){
			sorted.add(input.get(index));
		}

		return sorted;
	}


	public static Map<String,Object> toStringKeys(Map<Object,Object> map){
		Map<String,Object> asStr = new HashMap<>();
		for (Map.Entry<Object, Object> ent: map.entrySet())
			if (ent.getKey() instanceof String)
				asStr.put((String)ent.getKey(), ent.getValue());
		return asStr;
	}

	public static Object getArbitratyDepth(Map<?, ?> map, Object key, Object defaultValue) {
		Object res = getArbitratyDepth(map, key);
		return (res!=null? res : defaultValue);
	}
	/**
	 * Get the Value stored for a given Key, or <code>null</code> if not present
	 * @param map a map, possibly with multiple levels
	 * @param key key to look for
	 * @return The value associated with the key, or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public static Object getArbitratyDepth(Map<?, ?> map, Object key) {
		List<Map<?, ?>> nextLevels = new ArrayList<>(); 
		for (Map.Entry<?, ?> entry: map.entrySet()) {
			if (entry.getKey().equals(key))
				return entry.getValue();
			else if (entry.getValue() instanceof Map)
				nextLevels.add((Map<? extends Object, ? extends Object>)entry.getValue());
		}

		// Check next levels
		for (Map<? extends Object, ? extends Object> m : nextLevels) {
			Object value = getArbitratyDepth(m, key);
			if (value != null)
				return value;
		}

		return null;
	}
	
	@SuppressWarnings("unchecked")
	public static boolean containsKeyArbitraryDepth(Map<? extends Object, ?> map, Object key) {
		for (Map.Entry<? extends Object, ?> entry : map.entrySet()) {
			if (entry.getKey().equals(key)) {
				return true;
			} else if (entry.getValue() instanceof Map) {
				boolean childContains = containsKeyArbitraryDepth((Map<? extends Object, ?>) entry.getValue(), key);
				if (childContains)
					return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	public static void removeAtArbitraryDepth(Map<? extends Object, ? extends Object> map, Object key) {
		// remove all at this level
		map.remove(key);
		// go deeper
		for (Object value: map.values()) {
			if (value instanceof Map)
				removeAtArbitraryDepth((Map<? extends Object, ? extends Object>)value, key);
		}
	}

	public static <T> List<T> toList(Iterable<T> iter){
		List<T> result = new ArrayList<>();
		for(T i : iter){
			result.add(i);
		}
		return result;
	}

	public static <T> List<T> toList(Iterator<T> iter){
		List<T> result = new ArrayList<>();
		while(iter.hasNext()){
			result.add(iter.next());
		}
		return result;
	}

	/**
	 * Performs a shallow replication, the same object in all 
	 * @param obj object to replicate
	 * @param num number of repeats
	 * @param <T> the type of {@code obj}
	 * @return a list with {@code obj} replicated {@code num} times
	 */
	@SuppressWarnings("unchecked")
	public static <T> List<T> rep(T obj, int num){
		List<T> list = new ArrayList<>();

		if (obj instanceof Double)
			for (int i=0; i<num; i++)
				list.add((T) Double.valueOf((Double)obj));
		else if (obj instanceof Integer)
			for (int i=0; i<num; i++)
				list.add((T) Integer.valueOf((Integer)obj));
		else if (obj instanceof String)
			for (int i=0; i<num; i++)
				list.add((T) new String((String)obj));
		else if (obj instanceof Long)
			for (int i=0; i<num; i++)
				list.add((T) Long.valueOf((Long)obj));
		else
			for (int i=0; i<num; i++)
				list.add(obj);
		return list;
	}

	public static int count(Iterator<?> iter) {
		int len = 0;
		while(iter.hasNext()) {
			iter.next();
			len++;
		}
		return len;
	}

	public static <K,V> Map<K,V> dropNullValues(Map<K,V> map){
		if (map == null || map.isEmpty())
			return map;
		
		Map<K,V> noNull = new HashMap<>(map.size());

		for (Map.Entry<K,V> kv : map.entrySet()){
			if (kv.getValue() != null && ! "null".equalsIgnoreCase(kv.getValue().toString())) {
				noNull.put(kv.getKey(), kv.getValue());
			}
		}

		return noNull;
	}
	
	public static<T extends Number> SummaryStatistics getStatistics(Collection<T> col) {
		SummaryStatistics ss = new SummaryStatistics();
		for (Number n: col) {
			ss.addValue(n.doubleValue());
		}
		return ss;
	}
	
	public static <T extends Comparable<T>>  boolean rangeHasNoBounds(Range<T> range) {
		return ! range.hasLowerBound() && ! range.hasUpperBound();
	}
	
	public static boolean containsIgnoreCase(String[] list, String key) {
		return containsIgnoreCase(Arrays.asList(list), key);
	}
	
	public static boolean containsIgnoreCase(Collection<String> list, String key) {
		for (String s : list) {
			if (s.equalsIgnoreCase(key))
				return true;
		}
		return false;
	}

}

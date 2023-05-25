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
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.FuzzyMatcher;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.config.EnumConfig;
import com.arosbio.commons.config.IntegerConfig;
import com.arosbio.commons.config.NumericConfig;

public class SelectionCriterion implements Configurable {

	private final static String FILTER_CRITERION_PARAM_NAME = "criterion";
	private final static String KEEP_MAXIMUM_NUMBER_PARAM_NAME = "n";
	private final static String THRESHOLD_PARAM_NAME = "threshold";
	private final static double DEFAULT_THRESHOLD = 1e-10;

	public enum Criterion {
		KEEP_LARGER_THAN_MEAN("mean",1), 
		KEEP_N("keepN",2), 
		KEEP_LARGER_THAN_THRESHOLD("threshold",3),
		REMOVE_ZEROS("removeZeros",4),
		KEEP_LARGER_THAN_MEDIAN("median",5);

		private String text;
		private final int id;

		private Criterion(String text, int id) {
			this.text=text;
			this.id = id;
		}

		private static Criterion get(int id) {
			for (Criterion c : values()) {
				if (c.id == id)
					return c;
			}
			throw new IllegalArgumentException("Criterion " + id + " not valid");
		}

		private static Criterion get(String txt) {
			// Try as an id first
			try {
				return get(TypeUtils.asInt(txt));
			} catch (NumberFormatException e) {}

			List<Pair<List<String>,Criterion>> possibleOnes = new ArrayList<>();
			for (Criterion c : values()) {
				possibleOnes.add(ImmutablePair.of(Arrays.asList(c.text), c));
			}

			try {
				return new FuzzyMatcher().matchPairs(possibleOnes, txt);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("No criterion found for argument " + txt ); 
			}

		}

		public String toString() {
			return String.format("(%d) %s",id, text);
		}
	}

	private Criterion crit;
	private double threshold = DEFAULT_THRESHOLD;
	private int numToKeep = -1;

	public SelectionCriterion(Criterion crit) {
		this.crit = crit;
	}

	public static SelectionCriterion keepN(int n) {
		return new SelectionCriterion(Criterion.KEEP_N).withN(n);
	}

	public SelectionCriterion withThreshold(double threshold) {
		if (threshold < 0)
			throw new IllegalArgumentException("Threshold cannot be smaller than 0");
		this.threshold = threshold;
		return this;
	}

	public SelectionCriterion withN(int n) {
		if (n <= 0)
			throw new IllegalArgumentException("N must be > 0");
		this.numToKeep = n;
		return this;
	}

	public double getThreshold() {
		return threshold;
	}

	public int getN() {
		return numToKeep;
	}
	
	public Criterion getCriterion() {
		return crit;
	}

	public SelectionCriterion clone() {
		SelectionCriterion sc = new SelectionCriterion(crit);
		sc.threshold = threshold;
		sc.numToKeep = numToKeep;
		return sc;
	}

	public List<Integer> getIndicesToRemove(List<CollectionUtils.IndexedValue> vals){
		validateState();

		switch (crit) {
		case KEEP_LARGER_THAN_MEAN:
			return FeatureSelectUtils.getSmallerThanMean(vals);
		case KEEP_LARGER_THAN_MEDIAN:
			return FeatureSelectUtils.getSmallerThanMedian(vals);
		case KEEP_LARGER_THAN_THRESHOLD:
			return FeatureSelectUtils.getSmallerThanThreshold(vals, threshold);
		case KEEP_N:
			return FeatureSelectUtils.getSmallestKeepingN(vals, numToKeep);
		case REMOVE_ZEROS:
			return FeatureSelectUtils.getSmallerThanThreshold(vals, DEFAULT_THRESHOLD);
		default:
			throw new IllegalArgumentException("Selection criterion not valid: " + crit);
		}

	}
	
	public String toString() {
		String critStr = "SelectionCriterion:";
		switch (crit) {
		case KEEP_LARGER_THAN_MEAN:
			return critStr + " Keep larger than mean";
		case KEEP_LARGER_THAN_MEDIAN:
			return critStr + " Keep larger than median";
		case KEEP_LARGER_THAN_THRESHOLD:
			return critStr + " Keep larger than variance threshold " + threshold;
		case KEEP_N:
			return critStr + " Keep at most " + numToKeep;
		case REMOVE_ZEROS:
			return critStr + " Remove 0 variance features";
		default:
			throw new IllegalArgumentException("Selection criterion not valid: " + crit);
		}
	}


	@Override
	public List<ConfigParameter> getConfigParameters() {
		return Arrays.asList(new EnumConfig.Builder<>(Arrays.asList(FILTER_CRITERION_PARAM_NAME),EnumSet.allOf(Criterion.class),crit)
				.description("Selection criterion to use").build(),
			new IntegerConfig.Builder(Arrays.asList(KEEP_MAXIMUM_NUMBER_PARAM_NAME), -1)
				.description("Fixed number of features that should be kept (most important ones), the final number can in some cases be fewer than these due to importances equal to zero")
				.build(),
			new NumericConfig.Builder(Arrays.asList(THRESHOLD_PARAM_NAME), DEFAULT_THRESHOLD)
				.description("A fixed threshold to use").build());
	}

	/**
	 * Note: this may leave this object in a non-valid state. Use a clone and update only when successful
	 */
	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
		for (Map.Entry<String, Object> p : params.entrySet()) {
			if (p.getKey().equalsIgnoreCase(FILTER_CRITERION_PARAM_NAME)) {
				crit = Criterion.get(p.getValue().toString());
			} else if (p.getKey().equalsIgnoreCase(KEEP_MAXIMUM_NUMBER_PARAM_NAME)) {
				numToKeep = TypeUtils.asInt(p.getValue());
			} else if (p.getKey().equalsIgnoreCase(THRESHOLD_PARAM_NAME)) {
				threshold = TypeUtils.asDouble(p.getValue());
			}
		}
		
		validateState();
	}
	
	private void validateState() throws IllegalStateException {
		switch (crit) {
		case KEEP_N:
			if (numToKeep <= 0)
				throw new IllegalStateException("Selection-criteria " + Criterion.KEEP_N + " requires N>0");
			break;
		case KEEP_LARGER_THAN_THRESHOLD:
			if (threshold < 0)
				throw new IllegalStateException("Selection-criteria " + Criterion.KEEP_LARGER_THAN_THRESHOLD + " requires threshold >= 0");
			break;
		default:
			break;
		}
	}



}

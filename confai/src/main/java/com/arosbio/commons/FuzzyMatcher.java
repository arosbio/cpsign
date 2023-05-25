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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import com.arosbio.commons.mixins.Aliased;
import com.arosbio.commons.mixins.Named;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.LevenshteinDistance;

public class FuzzyMatcher {

	private ScoreThreshold threshold = new ScoreThreshold();
	private boolean ignoreCase = true;
	
	private static class ScoreThreshold{
		private Integer maxDistance;
		private Double maxRatio;
		
		private ScoreThreshold() {
			this(1./3);
		}
		private ScoreThreshold(double maxRatio) {
			this.maxRatio = maxRatio;
		}
		private ScoreThreshold(int maxDistance) {
			this.maxDistance = maxDistance;
		}
		private ScoreThreshold withDistance(int distance) {
			this.maxRatio = null;
			this.maxDistance = distance;
			return this;
		}
		private ScoreThreshold withRatio(double ratio) {
			this.maxRatio = ratio;
			this.maxDistance = null;
			return this;
		}
		
		public int getAllowedChanges(String template, String query) {
			if (maxRatio != null) {
				return (int) Math.ceil(template.length()*maxRatio);
			} else if (maxDistance != null) {
				return maxDistance;
			}
			throw new RuntimeException("Coding error in fuzzy matcher code"); 
				
		}
	}
	

	/**
	 * Use a dynamic 1/3 threshold based on the query length 
	 */
	public FuzzyMatcher() {}

	public FuzzyMatcher withIgnoreCase(boolean ignore){
		this.ignoreCase = ignore;
		return this;
	}
	
	public FuzzyMatcher withDynamicThreshold(double maxAllowedRatio) {
		this.threshold.withRatio(maxAllowedRatio);
		return this;
	}
	public FuzzyMatcher withFixedThreshold(int numAllowedChanges) {
		this.threshold.withDistance(numAllowedChanges);
		return this;
	}

	private String standardize(String input){
		return ignoreCase ? 
			StringUtils.standardizeTextSplits(input).toLowerCase(Locale.ENGLISH) :
			StringUtils.standardizeTextSplits(input);
	}

	public <T> T match(Collection<T> values, final String query) throws NoMatchException {
		if (query == null || query.length()<1)
			throw new IllegalArgumentException("Query argument cannot be an empty text");

		// Standardize the query (i.e. use lower case or the given case)
		final String stdQuery = standardize(query);

		int lowestFoundDistance = Integer.MAX_VALUE;
		List<T> bestMatches = new ArrayList<>();
		List<String> bestMatchesNames = new ArrayList<>();
		
		for (T value : values) {

			int lowestDistanceForObject = Integer.MAX_VALUE;
			String bestObjectNameMatch = "";

			for (String s : getNames(value)) {
				final String stdTemplate = standardize(s);

				int d = getDistance(stdTemplate, stdQuery);
				if (d == 0)
					return value; // Exactly the same, return it!
				
				if (d>0 && d < lowestDistanceForObject) {
					lowestDistanceForObject = d;
					bestObjectNameMatch = s;
				}

			}

			// If there was a valid match for the current value
			if (lowestDistanceForObject < Integer.MAX_VALUE) {

				if (lowestDistanceForObject < lowestFoundDistance) {
					// New best match
					lowestFoundDistance = lowestDistanceForObject;
					bestMatches.clear();
					bestMatches.add(value);

					bestMatchesNames.clear();
					bestMatchesNames.add(bestObjectNameMatch);
				} else if (lowestDistanceForObject == lowestFoundDistance) {
					// Tied score-wise
					bestMatches.add(value);
					bestMatchesNames.add(bestObjectNameMatch);
				}
			}
		}
		
		if (lowestFoundDistance >= Integer.MAX_VALUE) {
			throw new NoMatchException('"' + query + "\" not matching any of the possible values");
		}

		if (bestMatches.size() > 1) {
			throw new NoMatchException('"' + query + "\" matches the following options: " + StringUtils.toStringNoBrackets(bestMatchesNames));
		}

		return bestMatches.get(0);
		
	}

	private static <T> List<String> getNames(T value){
		List<String> names = new ArrayList<>();
		if (value instanceof Named) {
			names.add(((Named) value).getName());
		}
		if (value instanceof Aliased) {
			names.addAll(Arrays.asList(((Aliased) value).getAliases()));
		}
		if (value instanceof String){
			names.add((String)value);
		}
		if (names.isEmpty() & value instanceof Enum){
			names.add(((Enum<?>)value).name());
		}
		return names;
	}
	
	public <T extends Enum<T>> T match(EnumSet<T> clz, final String query) throws NoMatchException{
		Iterator<T> iter = clz.iterator();
		Collection<Pair<List<String>,T>> mapping = new HashSet<>();
		while (iter.hasNext()) {
			T e = iter.next();
			mapping.add(ImmutablePair.of(getNames(e), e));
		}
		return matchPairs(mapping, query);
	}

	public <T> T matchPairs(Collection<Pair<List<String>,T>> objects, final String query) 
			throws NoMatchException {

		if (query == null || query.length()<1)
			throw new IllegalArgumentException("Query argument cannot be an empty text");

		final String stdQuery = standardize(query);

		int lowestFoundDistance = Integer.MAX_VALUE;
		List<T> bestMatches =new ArrayList<>();
		List<String> bestMatchesNames = new ArrayList<>();

		for (Pair<List<String>,T> obj : objects) {

			int lowestDistanceForObject = Integer.MAX_VALUE;
			String bestObjectNameMatch = "";

			for (String s : obj.getLeft()) {
				String stdTemplate = standardize(s);
				int d = getDistance(stdTemplate, stdQuery);
				if (d == 0)
					return obj.getRight();
				
				if (d>0 && d < lowestDistanceForObject) {
					lowestDistanceForObject = d;
					bestObjectNameMatch = s;
				}

			}
			// If there was a valid match
			if (lowestDistanceForObject < Integer.MAX_VALUE) {

				if (lowestDistanceForObject < lowestFoundDistance) {
					// New best match
					lowestFoundDistance = lowestDistanceForObject;
					bestMatches.clear();
					bestMatches.add(obj.getRight());

					bestMatchesNames.clear();
					bestMatchesNames.add(bestObjectNameMatch);
				} else if (lowestDistanceForObject == lowestFoundDistance) {
					// Tied score-wise
					bestMatches.add(obj.getRight());
					bestMatchesNames.add(bestObjectNameMatch);
				}
			}
		}
		
		if (lowestFoundDistance >= Integer.MAX_VALUE) {
			throw new NoMatchException('"' + query + "\" not matching any of the possible values");
		}

		if (bestMatches.size() > 1) {
			throw new NoMatchException('"' + query + "\" matches the following options: " + StringUtils.toStringNoBrackets(bestMatchesNames));
		}

		return bestMatches.get(0);
	}
	
	private int getDistance(String template, String query) {
		int d = new LevenshteinDistance(threshold.getAllowedChanges(template, query))
				.apply(template, query);
		if (d==0)
			return 0;
		
		// If the query is a substring of the template, we penalize the miss-match less
		// Instead give it half the edit distance
		if (template.contains(query)) {
			if (d < 0)
				d = (int)Math.ceil(0.5*(template.length()-query.length()));
			else
				d = Math.min(d, (int)Math.ceil(0.5*(template.length()-query.length())));
		}
		return d;
	}

	public static class NoMatchException extends IllegalArgumentException {
		
		private static final long serialVersionUID = -1524442219231614457L;

		public NoMatchException(String message){
			super(message);
		}
		
	}
	
}

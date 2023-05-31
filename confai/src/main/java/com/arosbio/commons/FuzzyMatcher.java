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

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.LevenshteinDistance;

import com.arosbio.commons.mixins.Aliased;
import com.arosbio.commons.mixins.Named;

public class FuzzyMatcher {

	private ScoreThreshold threshold = new ScoreThreshold();
	private boolean ignoreCase = true;

	public static class NoMatchException extends IllegalArgumentException {
		
		private static final long serialVersionUID = -1524442219231614457L;

		public NoMatchException(String message){
			super(message);
		}
		
	}
	
	private static class ScoreThreshold {
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
				return (int) (template.length()*maxRatio + .5);
			} else if (maxDistance != null) {
				return maxDistance;
			}
			throw new RuntimeException("Coding error in fuzzy matcher code"); 
				
		}
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

	String standardize(String input){
		return ignoreCase ? 
			StringUtils.standardizeTextSplits(input).toLowerCase(Locale.ENGLISH) :
			StringUtils.standardizeTextSplits(input);
	}

	static class MatchScore implements Comparable<MatchScore> {

		private final int startMatch;
		private final int editDistance;
		private final boolean exactMatch;

		private MatchScore(int start, int edit, boolean isExactMatch){
			this.startMatch = start;
			this.editDistance = edit;
			this.exactMatch = isExactMatch;
		}
		
		static MatchScore startMatch(int numMatch, int editDistance){
			return new MatchScore(numMatch, editDistance, false);
		}
		static MatchScore editMatch(int editDistance){
			return new MatchScore(-1, editDistance, false);
		}
		static MatchScore noMatch(){
			return new MatchScore(-1000,-1, false);
		}
		static MatchScore exactMatch(){
			return new MatchScore(-1, -1, true);
		}

		public String toString(){
			if (exactMatch){
				return "FuzzyMatchScore: Exact Match";
			} else if (startMatch >0){
				return "FuzzyMatchScore: startMatch="+startMatch; 
			} else if (editDistance>0){
				return "FuzzyMatchScore: editMatch="+editDistance; 
			} else {
				return "FuzzyMatchScore: No Match";
			}
		}

		public boolean foundMatch(){
			return startMatch>0 || editDistance>0;
		}

		public boolean isExactMatch(){
			return exactMatch;
		}

		public boolean isBetterThan(MatchScore o){
			return compareTo(o) < 0;
		}

		public boolean equals(Object o){
			if (!(o instanceof MatchScore))
				return false;
			MatchScore ms = (MatchScore)o;
			return startMatch == ms.startMatch && editDistance == ms.editDistance && exactMatch == ms.exactMatch;
		}
		

		@Override
		public int compareTo(MatchScore o) {
			// special treat of exact matches
			if (exactMatch && o.exactMatch)
				return 0;
			if (exactMatch)
				return -1;
			if (o.exactMatch)
				return 1;
			// startMatch is "positive" (longer is better) and editDistance is "negative" (larger is worse match)
			return (o.startMatch - o.editDistance) - (startMatch - editDistance);
		}

	}

	public <T> T match(Collection<T> values, final String query) throws NoMatchException {
		if (query == null || query.length()<1)
			throw new IllegalArgumentException("Query argument cannot be an empty text");

		// Standardize the query (i.e. use lower case or the given case)
		final String stdQuery = standardize(query);
		final String stdQueryNoSpace = stdQuery.replaceAll("\\s", "");

		MatchScore overallBestScore = MatchScore.noMatch();
		List<T> bestMatches = new ArrayList<>();
		List<String> bestMatchesNames = new ArrayList<>();
		
		for (T value : values) {

			MatchScore bestForThisObject = MatchScore.noMatch();
			String bestObjectNameMatch = "";

			for (String s : getNames(value)) {

				// Perform using spaces in standardized text
				final String stdTemplate = standardize(s);
				MatchScore score = score(stdTemplate, stdQuery);
				if (score.isExactMatch()){
					// Short circuit exact match
					return value;
				}
				if (score.isBetterThan(bestForThisObject)){
					bestForThisObject = score;
					bestObjectNameMatch = s;
				}

				// Perform using no spaces in standardized text
				final String stdTemplateNoSpace = stdTemplate.replaceAll("\\s", "");
				MatchScore scoreNoSpace = score(stdTemplateNoSpace, stdQueryNoSpace);
				if (scoreNoSpace.isExactMatch()){
					// Short circuit exact match
					return value;
				}
				if (scoreNoSpace.isBetterThan(bestForThisObject)){
					bestForThisObject = scoreNoSpace;
					bestObjectNameMatch = s;
				}

			}

			// If there was a valid match
			if (bestForThisObject.foundMatch()){

				if (bestForThisObject.isBetterThan(overallBestScore)) {
					// New best match
					overallBestScore = bestForThisObject;
					bestMatches.clear();
					bestMatches.add(value);

					bestMatchesNames.clear();
					bestMatchesNames.add(bestObjectNameMatch);
				} else if (bestForThisObject.equals(overallBestScore)) {
					// Tied score-wise
					bestMatches.add(value);
					bestMatchesNames.add(bestObjectNameMatch);
				}
			}
			
		}
		
		if (!overallBestScore.foundMatch()) {
			throw new NoMatchException('"' + query + "\" not matching any of the possible values");
		}

		if (bestMatches.size() > 1) {
			throw new NoMatchException('"' + query + "\" matches the following options: " + StringUtils.toStringNoBrackets(bestMatchesNames));
		}

		return bestMatches.get(0);
		
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

	public <C extends Collection<String>, T> T matchPairs(Collection<Pair<C,T>> objects, final String query) 
			throws NoMatchException {

		if (query == null || query.length()<1)
			throw new IllegalArgumentException("Query argument cannot be an empty text");

		final String stdQuery = standardize(query);
		final String stdQueryNoSpace = stdQuery.replaceAll("\\s", "");

		MatchScore overallBestScore = MatchScore.noMatch();
		List<T> bestMatches = new ArrayList<>();
		List<String> bestMatchesNames = new ArrayList<>();

		for (Pair<C,T> obj : objects) {

			MatchScore bestForThisObject = MatchScore.noMatch();
			String bestObjectNameMatch = "";

			for (String s : obj.getLeft()) {

				// Perform using spaces in standardized text
				final String stdTemplate = standardize(s);
				MatchScore score = score(stdTemplate, stdQuery);
				if (score.isExactMatch()){
					// Short circuit exact match
					return obj.getRight();
				}
				if (score.isBetterThan(bestForThisObject)){
					bestForThisObject = score;
					bestObjectNameMatch = s;
				}

				// Perform using no spaces in standardized text
				final String stdTemplateNoSpace = stdTemplate.replaceAll("\\s", "");
				MatchScore scoreNoSpace = score(stdTemplateNoSpace, stdQueryNoSpace);
				if (scoreNoSpace.isExactMatch()){
					// Short circuit exact match
					return obj.getRight();
				}
				if (scoreNoSpace.isBetterThan(bestForThisObject)){
					bestForThisObject = scoreNoSpace;
					bestObjectNameMatch = s;
				}

			}
			// If there was a valid match
			if (bestForThisObject.foundMatch()){

				if (bestForThisObject.isBetterThan(overallBestScore)) {
					// New best match
					overallBestScore = bestForThisObject;
					bestMatches.clear();
					bestMatches.add(obj.getRight());

					bestMatchesNames.clear();
					bestMatchesNames.add(bestObjectNameMatch);
				} else if (bestForThisObject.equals(overallBestScore)) {
					// Tied score-wise
					bestMatches.add(obj.getRight());
					bestMatchesNames.add(bestObjectNameMatch);
				}
			}
		}

		if (!overallBestScore.foundMatch()) {
			throw new NoMatchException('"' + query + "\" not matching any of the possible values");
		}

		if (bestMatches.size() > 1) {
			throw new NoMatchException('"' + query + "\" matches the following options: " + StringUtils.toStringNoBrackets(bestMatchesNames));
		}

		return bestMatches.get(0);
	}
	
	

	MatchScore score(String template, String query){
		// Prefer a start match
		if (template.startsWith(query)) {
			// Special treat if identical match
			if (template.length() == query.length())
				return MatchScore.exactMatch();
			return MatchScore.startMatch(query.length(), template.length()-query.length());
		}

		// otherwise calculate the edit distance
		int editDistance = new LevenshteinDistance(threshold.getAllowedChanges(template, query))
				.apply(template, query);
		if (editDistance < 0){
			return MatchScore.noMatch();
		}
		return MatchScore.editMatch(editDistance);
	}

	
}

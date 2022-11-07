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

	private Integer maximumDistance = null;

	/**
	 * Use a dynamic 2/3 threshold 
	 */
	public FuzzyMatcher() {}
	
	public <T extends Enum<T>> T match(EnumSet<T> clz, final String query) {
		Iterator<T> iter = clz.iterator();
		Collection<Pair<List<String>,T>> mapping = new HashSet<>();
		while (iter.hasNext()) {
			T e = iter.next();
			List<String> names = new ArrayList<>();
			if (e instanceof Named) {
				names.add(((Named) e).getName());
			}
			if (e instanceof Aliased) {
				names.addAll(Arrays.asList(((Aliased) e).getAliases()));
			}
			if (names.isEmpty())
				names.add(e.name());
			mapping.add(ImmutablePair.of(names, e));
		}
		return match(mapping, query);
	}

	public <T> T match(Collection<Pair<List<String>,T>> objects, final String query) 
			throws IllegalArgumentException {

		if (query == null || query.length()<1)
			throw new IllegalArgumentException("Argument cannot be an empty text");

		final String queryLC = StringUtils.standardizeTextSplits(query).toLowerCase(Locale.ENGLISH);

		int lowestFoundDistance = Integer.MAX_VALUE;
		List<T> bestMatches =new ArrayList<>();
		List<String> bestMatchesNames = new ArrayList<>();

		for (Pair<List<String>,T> obj: objects) {

			int lowestDistanceForObject = Integer.MAX_VALUE;
			String bestObjectNameMatch = "";

			for (String s : obj.getLeft()) {
				String sLC = StringUtils.standardizeTextSplits(s).toLowerCase(Locale.ENGLISH);
				int d = new LevenshteinDistance(getThreshold(sLC, queryLC))
						.apply(sLC, queryLC);
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
			throw new IllegalArgumentException('"' + query + "\" not maching any of the possible values");
		}

		if (bestMatches.size() > 1) {
			throw new IllegalArgumentException('"' + query + "\" matches the following options: " + StringUtils.toStringNoBrackets(bestMatchesNames));
		}

		return bestMatches.get(0);
	}
	
	private int getThreshold(final String s1, final String s2) {
		if (maximumDistance != null && maximumDistance>0)
			return maximumDistance;
		return (int) Math.ceil(Math.max(s1.length(), s2.length())/3.);
	}
	
}

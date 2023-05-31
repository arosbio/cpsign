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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.FuzzyScore;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.FuzzyMatcher.MatchScore;
import com.arosbio.commons.FuzzyMatcher.NoMatchException;
import com.arosbio.data.transform.Transformer;
import com.arosbio.ml.algorithms.MLAlgorithm;
import com.arosbio.ml.algorithms.svm.PlattScaledC_SVC;
import com.arosbio.ml.cp.nonconf.calc.LinearInterpolationPValue;
import com.arosbio.ml.cp.nonconf.calc.PValueCalculator;
import com.arosbio.ml.cp.nonconf.calc.SplineInterpolatedPValue;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.sampling.SamplingStrategy;
import com.arosbio.tests.suites.UnitTest;
import com.google.common.collect.Sets;

@Category(UnitTest.class)
public class TestFuzzyStuff {

	@Test
	public void testPValMatching() {
		Assert.assertTrue(FuzzyServiceLoader.load(PValueCalculator.class, "Spline_interpolation") instanceof SplineInterpolatedPValue);
		Assert.assertTrue(FuzzyServiceLoader.load(PValueCalculator.class, "Spline_interpola") instanceof SplineInterpolatedPValue);
		Assert.assertTrue(FuzzyServiceLoader.load(PValueCalculator.class, "linearInterpol") instanceof LinearInterpolationPValue);
	}

	 @Test
	 public void testFuzzyWithCaseSensitive() {
	 	FuzzyMatcher matcher = new FuzzyMatcher().withIgnoreCase(false);
	 	try {
	 		matcher.match(Arrays.asList("some-text", "some-other-text"), "SOME_TEXT");
	 		Assert.fail("Should be no match if case-sensitive");
	 	} catch (NoMatchException e) {}
	 	
	 	// Some case is the same:
	 	Assert.assertEquals("Some-Text", matcher.withIgnoreCase(true).match(Arrays.asList("Some-Text", "some-other-text"), "SOME_TEXT"));
	 	
	 	
	 	// Case insensitive - we should match 
	 	Assert.assertEquals("some-text", matcher.withIgnoreCase(true).match(Arrays.asList("some-text", "some-other-text"), "SOME_TEXT"));
	 	
	 }

	
	@Test
	public void testFuzzyScorer() {
		
		
		fs("abc", "a");
		fs("abc", "ab");
		fs("ABC", "ab");
		fs("num samples", "numSamples");
		fs("num_samples", "numSamples");
		fs("num", "numSamples");
		fs("numSamples", "num");
		fs("numSamples", "samples");
	}
	
	@Test
	public void testFuzzyMatcher2() {
		Assert.assertTrue(FuzzyServiceLoader.load(MLAlgorithm.class, "platt-scaled-c-svc") instanceof PlattScaledC_SVC);
	}

	@Test
	public void testFilterLabelFuzz() {
		try {
			FuzzyServiceLoader.load(Transformer.class, "range");
			Assert.fail("range should not match any of the implementations (too bad score)");
		} catch (IllegalArgumentException e){

		}
	}

	@Test
	public void testFuzzyMatcher(){
		FuzzyMatcher m = new FuzzyMatcher();
		// System.err.println("Std temlpate: " + m.standardize("UserSuppliedDescriptor"));
		// System.err.println("Std query: " + m.standardize("usersupplied"));
		String result = m.match(Arrays.asList("UserSuppliedDescriptor"), "usersupplied");
		Assert.assertEquals("UserSuppliedDescriptor", result);
		MatchScore score = new FuzzyMatcher().score("UserSuppliedDescriptor","usersupplied");
		Assert.assertTrue(!score.foundMatch());
		// System.err.println(score);
	}

	@Test
	public void testSameBeginnings(){
		SamplingStrategy strat = FuzzyServiceLoader.load(SamplingStrategy.class,"random");
		Assert.assertTrue(strat instanceof RandomSampling);
		MatchScore score = new FuzzyMatcher().score(RandomSampling.NAME, "Random");
		Assert.assertTrue(score.isExactMatch());
	}
	
	
	@Test
	public void testC_SVC() {
		Collection<Pair<Collection<String>,String>> vals = new HashSet<>();
		vals.add(ImmutablePair.of(Arrays.asList("C_SVC"), "C_SVC"));
		vals.add(ImmutablePair.of(Sets.newHashSet("EpsilonSVC"), "C_SVC"));
		vals.add(ImmutablePair.of(Arrays.asList("NuSVC"), "NuSVC"));
		
		String match = new FuzzyMatcher().matchPairs(vals, "c-svc");
		Assert.assertEquals("C_SVC", match);
//		System.err.println(match);
		
	}
	
	private void fs(String a, String b) {
		FuzzyScore fs = new FuzzyScore(Locale.ENGLISH);
		fs.fuzzyScore(a,b);
//		System.err.println(s);
	}
	

	@Test
	public void testMatchScore(){
		MatchScore edit5 = MatchScore.editMatch(5);
		MatchScore edit10 = MatchScore.editMatch(10);
		MatchScore same5 = MatchScore.startMatch(5,6);
		MatchScore same10 = MatchScore.startMatch(10,1);

		List<MatchScore> scoreList = new ArrayList<>();
		scoreList.add(same10);
		scoreList.add(same5);
		scoreList.add(edit10);
		scoreList.add(MatchScore.exactMatch());
		scoreList.add(MatchScore.noMatch());
		scoreList.add(edit5);
		scoreList.add(MatchScore.startMatch(7,3));
		scoreList.add(MatchScore.editMatch(7));
		Collections.sort(scoreList);
		Assert.assertEquals(MatchScore.exactMatch(), scoreList.get(0)); // should be the exact match in the top
		// Then the comes the start-matches (were longer matches is prefered)
		Assert.assertEquals(same10, scoreList.get(1));
		Assert.assertEquals(MatchScore.startMatch(7,3), scoreList.get(2));
		// In the end there are the edit distances
		Assert.assertEquals(same10, scoreList.get(1));
		Assert.assertEquals(edit10, scoreList.get(scoreList.size()-2));
		Assert.assertEquals(MatchScore.noMatch(), scoreList.get(scoreList.size()-1)); // should be no-match in the end


		Assert.assertTrue(edit5.isBetterThan(edit10));
		Assert.assertTrue(! edit10.isBetterThan(edit5));
		Assert.assertTrue(edit5.equals(MatchScore.editMatch(5)));
		Assert.assertTrue(edit5.isBetterThan(MatchScore.noMatch()));
		Assert.assertTrue(MatchScore.exactMatch().isBetterThan(MatchScore.noMatch()));
	}
	
	
}

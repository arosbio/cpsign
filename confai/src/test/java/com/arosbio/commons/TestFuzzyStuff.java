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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.FuzzyScore;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.ml.algorithms.MLAlgorithm;
import com.arosbio.ml.algorithms.svm.PlattScaledC_SVC;
import com.arosbio.ml.cp.nonconf.calc.LinearInterpolationPValue;
import com.arosbio.ml.cp.nonconf.calc.PValueCalculator;
import com.arosbio.ml.cp.nonconf.calc.SplineInterpolatedPValue;
import com.arosbio.tests.suites.UnitTest;

@Category(UnitTest.class)
public class TestFuzzyStuff {

	@Test
	public void testPValMatching() {
		Assert.assertTrue(FuzzyServiceLoader.load(PValueCalculator.class, "Spline_interpolation") instanceof SplineInterpolatedPValue);
		Assert.assertTrue(FuzzyServiceLoader.load(PValueCalculator.class, "Spline_interpola") instanceof SplineInterpolatedPValue);
		Assert.assertTrue(FuzzyServiceLoader.load(PValueCalculator.class, "linearInterpol") instanceof LinearInterpolationPValue);
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
	public void testC_SVC() {
		Collection<Pair<List<String>,String>> vals = new HashSet<>();
		vals.add(ImmutablePair.of(Arrays.asList("C_SVC"), "C_SVC"));
		vals.add(ImmutablePair.of(Arrays.asList("NuSVC"), "NuSVC"));
		
		String match = new FuzzyMatcher().match(vals, "c-svc");
		Assert.assertEquals("C_SVC", match);
//		System.err.println(match);
		
	}
	
	private void fs(String a, String b) {
		FuzzyScore fs = new FuzzyScore(Locale.ENGLISH);
		fs.fuzzyScore(a,b);
//		System.err.println(s);
	}
	
	
	
}

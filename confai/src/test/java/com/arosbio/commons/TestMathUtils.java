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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.TestUtils;


@Category(UnitTest.class)
public class TestMathUtils {
	
	@Rule
	public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
	@Rule
	public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();  

	@Test
	public void testMedianWithOnlyInf() {
		// only pos inf
		Assert.assertEquals(Double.POSITIVE_INFINITY, MathUtils.median(Arrays.asList(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)), 0.00001);
		// mix
		Assert.assertEquals(Double.NaN, MathUtils.median(Arrays.asList(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)), 0.00001);
		// only neg inf
		Assert.assertEquals(Double.NEGATIVE_INFINITY, MathUtils.median(Arrays.asList(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY)), 0.00001);
	}
	
	@Test
	public void testGeometricMean() throws Exception {
		Assert.assertEquals(1d,MathUtils.geometricMean(Arrays.asList(1d,1d,1d,1d,1d)),0.0001);
		Assert.assertEquals(Math.sqrt(2d),MathUtils.geometricMean(Arrays.asList(2d,2d,1d,1d)),0.0001);
		
	}
	
	@Test
	public void testRoundAllValues(){
		Map<String,Object> res = new HashMap<>();
		System.out.println(MathUtils.roundAllValues(res));
//		System.out.println(MathUtils.roundAllValues(new CVResult(0.02145125).toMap()));
//		System.out.println(MathUtils.roundAllValues(new CVResult(0,214151254241.01285912, 0.124141212521, 0.125125125, 0.12512663476436, null).toMap()));
	}
	
	@Test
	public void testRoundBigDecimal() {
		BigDecimal bd = new BigDecimal(280.124112512412521);
		System.out.println(bd.round(new MathContext(0)));
	}
	
	@Test
	public void testRoundToNSignFigs() {
		double old = 4765.0;
		Number n = MathUtils.roundTo3significantFigures(old);
		Assert.assertTrue(n instanceof Double);
		Assert.assertEquals("4770.0", n.toString());
		double original = 0.1251212159128519241299866;
		for (int i=1; i<10; i++) {
			// System.err.println(MathUtils.roundToNSignificantFigures(original, i));
			String rounded = ""+MathUtils.roundToNSignificantFigures(original, i);
			// System.out.println(""+ i + " : " + rounded);
			String decimals = rounded.split("\\.")[1];
			Assert.assertEquals(i,decimals.length());
		}
	}
	
	@Test
	public void testMedian(){
		Assert.assertEquals(1.0, MathUtils.median(Arrays.asList(1.0)),0.0001);
		Assert.assertEquals(1.25, MathUtils.median(Arrays.asList(1.0, 1.5)),0.0001);
		Assert.assertEquals(1.5, MathUtils.median(Arrays.asList(1.0, 1.5,2.0)),0.0001);
		Assert.assertEquals(1.75, MathUtils.median(Arrays.asList(5.0, 1.0, 1.5,2.0)),0.0001);
		Assert.assertEquals(3.5, MathUtils.median(Arrays.asList(10.0, 7.3, 5.0, 1.0, 1.5,2.0)),0.0001);
		Assert.assertEquals(1.75, MathUtils.median(Arrays.asList(Double.NaN, Double.NaN, 5.0, 1.0, 1.5,2.0)),0.0001);
	}
	
	@Test
	public void testMin() {
		Assert.assertEquals(1.0, MathUtils.min(Arrays.asList(1.0, 2.0, 3.0, 4.5)),0.0001);
		Assert.assertEquals(1.0, MathUtils.min(Arrays.asList(1.0)),0.0001);
	}
	
	@Test
	public void testMax() {
		Assert.assertEquals(4.5, MathUtils.max(Arrays.asList(1.0, 2.0, 3.0, 4.5)),0.0001);
		Assert.assertEquals(1.0, MathUtils.max(Arrays.asList(1.0)),0.0001);
	}
	
	@Test
	public void testNormalizeValues() {
		
		List<Integer> keys = CollectionUtils.listRange(1, 15);

		Map<Integer, Double> startMap = new HashMap<>();
		double start=(Math.random() > 0.5? -1 : 1)*20*Math.random(), stop=start+5+50*Math.random();
		Map<Integer,Double> correctValues = new HashMap<>();
		List<Double> predictedValues = new ArrayList<>();
		for(Integer atom : keys){
			double val = Math.random(), newVal = start + (stop-start)*val;
			correctValues.put(atom,val);
			predictedValues.add(newVal);
			startMap.put(atom, newVal);
		}

		//		System.out.println(predictedValues);
		Collections.sort(predictedValues);
		double rangeStart = predictedValues.get((int) (0.1*predictedValues.size()));
		double rangeStop = predictedValues.get((int) (0.9*predictedValues.size()));
		//		System.out.println("range start: " + rangeStart + ", rangeStop: " + rangeStop);

		Map<Integer, Double> normalized = MathUtils.normalizeMap(startMap, rangeStart, rangeStop);
		//		System.out.println(normalized.values());
		Assert.assertEquals(startMap.size(), normalized.size());
		Assert.assertEquals(startMap.keySet(), normalized.keySet());

		for(Entry<Integer, Double> entry : normalized.entrySet()){
			Assert.assertTrue(entry.getValue() <= 1.0);
			Assert.assertTrue(entry.getValue() >= -1.0);
		}
	}

	@Test
	public void testNormalizeValuesFakeData() {
		
		List<Integer> keys = CollectionUtils.listRange(-5, 11);

		Map<Integer, Double> startMap = new HashMap<>();
		double start=10.0, stop=15.0;
		Map<Integer,Double> correctValues = new HashMap<>();

		for(Integer k : keys){
			double val = Math.random(), newVal = start + (stop-start)*val;
			correctValues.put(k,val);
			startMap.put(k, newVal);
		}
		System.out.println("StartMap: " + startMap);
		Map<Integer, Double> normalized = MathUtils.normalizeMap(startMap, start,stop);
		Assert.assertEquals(startMap.size(), normalized.size());
		Assert.assertEquals(startMap.keySet(), normalized.keySet());
		
		System.out.println("correct: " + correctValues);
		System.out.println("Norm: " + normalized);
		for (Integer k : correctValues.keySet()){
			Assert.assertEquals(correctValues.get(k), normalized.get(k), 0.000001);
		}
	}

	public static enum EnumType {
		
	}

	@Test
	public void testMeanValCalc(){
		double meanList = MathUtils.mean(Arrays.asList(Double.MAX_VALUE/2, Double.MAX_VALUE/2, Double.MAX_VALUE/2));
		double meanArray = MathUtils.mean(Double.MAX_VALUE/2, Double.MAX_VALUE/2, Double.MAX_VALUE/2);

		Assert.assertEquals(Double.MAX_VALUE/2, meanList,0.0000001);
		Assert.assertEquals(Double.MAX_VALUE/2, meanArray,0.0000001);



		// System.err.println(Double.NEGATIVE_INFINITY + Double.POSITIVE_INFINITY);
		Assert.assertEquals("Only positive Inf -> mean is pos inf",Double.POSITIVE_INFINITY, MathUtils.mean(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY),0.0001);
		Assert.assertEquals("Only neg Inf -> mean is pos inf",Double.NEGATIVE_INFINITY, MathUtils.mean(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY),0.0001);

		Assert.assertEquals("Both neg and pos Inf -> NaN",Double.NaN, MathUtils.mean(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY),0.00001);

	}


	@Test
	public void checkIterativeGMCalc(){
		Collection<Double> values = Arrays.asList(87., 9.,1.,0.5);
		Assert.assertEquals(oldGeometricMeanCalc(values), MathUtils.geometricMean(values),0.00001);

		// Check when it comes to overflow
		List<Double> largeValues = Arrays.asList(Double.MAX_VALUE/2, Double.MAX_VALUE/2, Double.MAX_VALUE/2);
		Assert.assertTrue(Double.isInfinite(oldGeometricMeanCalc(largeValues)));
		TestUtils.assertSimilar(Double.MAX_VALUE/2, MathUtils.geometricMean(largeValues),0.001);
		// Assert.assertEquals();
		
	}

	@Test
	public void testCalcLogSumIterative(){
		doCompare(Arrays.asList(0.2,0.5,0.8));
		doCompare(Arrays.asList(20.,10.5,0.8));
		doCompare(Arrays.asList(0.26,100.5,8.));
	}

	private static void doCompare(Collection<Double> values){
		Assert.assertEquals(naiveAvgLogs(values), MathUtils.avgLogs(values).get(),0.000001);
	}

	// without an iterative approach - which may lead to numerical overflow issues
	private static double naiveAvgLogs(Collection<Double> values){
		Objects.requireNonNull(values, "cannot calculate mean on null");
		if (values.isEmpty())
			return Double.NaN;

		double logSum = 0d;

		for (double v : values){
			logSum += Math.log(v);
		}
		
		return logSum /values.size();
	}

	public static double oldGeometricMeanCalc(Collection<Double> values){
		return Math.pow(multiplyAllTogether(values), 1d/values.size());
	}

	public static double multiplyAllTogether(Collection<Double> values){
		double res = 1d;
		for (double v : values){
			res *= v;
		}
		return res;
	}


}

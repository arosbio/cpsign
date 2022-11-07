/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp.acp;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.ml.IntervalUtils;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.UnitTestInitializer;
import com.google.common.collect.Range;

/**
 * 
 * @author ola
 *
 */
@Category(UnitTest.class)
public class TestIntervalCapping extends UnitTestInitializer{

	double minObs = 1, maxObs = 5;
	
//	@Test
//	public void TestCapOutsideRange(){
//
//		//midpoint, interval, minobs, maxobs
//		Range<Double> res = IntervalUtils.getCappedInterval(6, 2, -1.5, 4.5);
//		System.out.println(res);
//		assertTrue(res.lowerEndpoint()==4);
//		assertTrue(res.upperEndpoint()==4.5);
//
//		try {
//			res = IntervalUtils.getCappedInterval(6, 1, -1.5, 4.5);
//			System.out.println(res);
//			assertTrue(res.lowerEndpoint()==4.5);
//			assertTrue(res.upperEndpoint()==4.5);
//			Assert.fail();
//		} catch (IllegalArgumentException e) {}
//
//		try {
//			res = IntervalUtils.getCappedInterval(-3, 1, -1.5, 4.5);
//			System.out.println(res);
//			assertTrue(res.lowerEndpoint()==-1.5);
//			assertTrue(res.upperEndpoint()==-1.5);
//		} catch (IllegalArgumentException e) {}
//
//		res = IntervalUtils.getCappedInterval(-3, 2, -1.5, 4.5);
//		System.out.println(res);
//		assertTrue(res.lowerEndpoint()==-1.5);
//		assertTrue(res.upperEndpoint()==-1);
//
//
//	}

	@Test
	public void testValidCapplings() {
		// Cap both sides
		Range<Double> interval = IntervalUtils.getCappedInterval(3, 10, minObs, maxObs);
		Assert.assertEquals(minObs,interval.lowerEndpoint(), 0.0001);
		Assert.assertEquals(maxObs,interval.upperEndpoint(), 0.0001);
		Range<Double> interval2 = IntervalUtils.capInterval(IntervalUtils.getInterval(3, 10), minObs, maxObs);
		Assert.assertEquals(minObs,interval2.lowerEndpoint(), 0.0001);
		Assert.assertEquals(maxObs,interval2.upperEndpoint(), 0.0001);
		

		// Cap lower
		Range<Double> intervalLower = IntervalUtils.getCappedInterval(2, 1.5, minObs, maxObs);
		Assert.assertEquals(minObs,intervalLower.lowerEndpoint(), 0.0001);
		Assert.assertEquals(2+1.5,intervalLower.upperEndpoint(), 0.0001);
		Range<Double> intervalLower2 = IntervalUtils.capInterval(IntervalUtils.getInterval(2, 1.5), minObs, maxObs);
		Assert.assertEquals(minObs,intervalLower2.lowerEndpoint(), 0.0001);
		Assert.assertEquals(2+1.5,intervalLower2.upperEndpoint(), 0.0001);

		// Cap upper
		Range<Double> intervalUpper = IntervalUtils.getCappedInterval(4, 1.5, minObs, maxObs);
		Assert.assertEquals(4-1.5,intervalUpper.lowerEndpoint(), 0.0001);
		Assert.assertEquals(maxObs,intervalUpper.upperEndpoint(), 0.0001);
		Range<Double> intervalUpper2 = IntervalUtils.capInterval(IntervalUtils.getInterval(4, 1.5), minObs, maxObs);
		Assert.assertEquals(4-1.5,intervalUpper2.lowerEndpoint(), 0.0001);
		Assert.assertEquals(maxObs,intervalUpper2.upperEndpoint(), 0.0001);

	}

	@Test
	public void testBelowCappingInterval() {
		// Interval will be only the lowest point in the capped interval
		Range<Double> interval = IntervalUtils.getCappedInterval(-1, 1.5, minObs, maxObs);
		Assert.assertEquals(minObs,interval.lowerEndpoint(), 0.0001);
		Assert.assertEquals(minObs,interval.upperEndpoint(), 0.0001);

		Range<Double> interval2 = IntervalUtils.capInterval(IntervalUtils.getInterval(-1, 1.5), minObs, maxObs);
		Assert.assertEquals(minObs,interval2.lowerEndpoint(), 0.0001);
		Assert.assertEquals(minObs,interval2.upperEndpoint(), 0.0001);
	}

	@Test
	public void testAboveCappingInterval() {
		// Interval will be only the lowest point in the capped interval
		Range<Double> interval = IntervalUtils.getCappedInterval(5.5, .1, minObs, maxObs);
		Assert.assertEquals(maxObs,interval.lowerEndpoint(), 0.0001);
		Assert.assertEquals(maxObs,interval.upperEndpoint(), 0.0001);

		Range<Double> interval2 = IntervalUtils.capInterval(IntervalUtils.getInterval(5.5, .1), minObs, maxObs);
		Assert.assertEquals(maxObs,interval2.lowerEndpoint(), 0.0001);
		Assert.assertEquals(maxObs,interval2.upperEndpoint(), 0.0001);
	}
	
	@Test
	public void test_NaN_distance() {
		Range<Double> interval = IntervalUtils.getCappedInterval(3, Double.NaN, minObs, maxObs);
		Assert.assertEquals(minObs,interval.lowerEndpoint(), 0.0001);
		Assert.assertEquals(maxObs,interval.upperEndpoint(), 0.0001);
		
		Range<Double> interval2 = IntervalUtils.capInterval(IntervalUtils.getInterval(3, Double.NaN), minObs, maxObs);
		Assert.assertEquals(minObs,interval2.lowerEndpoint(), 0.0001);
		Assert.assertEquals(maxObs,interval2.upperEndpoint(), 0.0001);
	}

	@Test
	public void test_Inf_distance() {
		Range<Double> interval = IntervalUtils.getCappedInterval(3, Double.POSITIVE_INFINITY, minObs, maxObs);
		Assert.assertEquals(minObs,interval.lowerEndpoint(), 0.0001);
		Assert.assertEquals(maxObs,interval.upperEndpoint(), 0.0001);
		
		Range<Double> interval2 = IntervalUtils.capInterval(IntervalUtils.getInterval(3, Double.POSITIVE_INFINITY), minObs, maxObs);
		Assert.assertEquals(minObs,interval2.lowerEndpoint(), 0.0001);
		Assert.assertEquals(maxObs,interval2.upperEndpoint(), 0.0001);
	}
}


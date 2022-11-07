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

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.tests.suites.PerformanceTest;

public class TestStopwatch {

	@Test
	public void testToString() throws Exception {
		Assert.assertEquals("100 ms", Stopwatch.toNiceString(100));
		Assert.assertEquals("1.100 s", Stopwatch.toNiceString(1100));
		Assert.assertEquals("1 min 1 s", Stopwatch.toNiceString(61000));
		Assert.assertEquals("4 min 20 s", Stopwatch.toNiceString(260100)); // 240,000 for 4min then 20.1 seconds
		Assert.assertEquals("17 min 42 s", Stopwatch.toNiceString(1061600)); // 1 020 000 for 17 min, 41.6 seconds more (rounded to 42)
	}

	@Test
	@Category(PerformanceTest.class)
	public void smallTest() throws Exception{

		for (int sleep : new int[]{200,500,1500,3000}){
			runTimerCheck(sleep);
		}
	}

	private static void runTimerCheck(int sleep) throws InterruptedException {
		Stopwatch sw = new Stopwatch().start();
		// time it
		Thread.sleep(sleep);
		sw.stop();
		Assert.assertEquals((double)sleep, (double)sw.elapsedTimeMillis(),sleep*.15);
	}
	

}

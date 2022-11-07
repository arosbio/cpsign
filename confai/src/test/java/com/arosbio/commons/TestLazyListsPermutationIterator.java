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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.tests.suites.UnitTest;

@Category(UnitTest.class)
public class TestLazyListsPermutationIterator {

	public static final boolean PRINT = false;
	@Test
	public void testIterator() {
		int numberOfPermutations = printAll(new LazyListsPermutationIterator(Arrays.asList(2, 2)));
		Assert.assertEquals(2*2,numberOfPermutations);
	}

	@Test
	public void testIterator3_3() {
		int numberOfPermutations = printAll(new LazyListsPermutationIterator(Arrays.asList(3, 3,3)));
		Assert.assertEquals(3*3*3,numberOfPermutations);
	}

	@Test
	public void testIteratorManyCombos() {
		List<Integer> sizes = Arrays.asList(1,2,3,4,5,6);
		int numberOfPermutations = printAll(new LazyListsPermutationIterator(sizes));
		Assert.assertEquals(MathUtils.multiplyAllTogetherInt(sizes),numberOfPermutations);
	}
	
	@Test
	public void testOutsideList() {
		Iterator<List<Integer>> it = new LazyListsPermutationIterator(Arrays.asList(0));
		try {
			Assert.assertFalse(it.hasNext());
			it.next();
			Assert.fail();
		} catch(NoSuchElementException e) {}
	}


	public static int printAll(LazyListsPermutationIterator iterator) {
		int num = 0;
		while (iterator.hasNext()) {
			List<Integer> l = iterator.next();
			if (PRINT)
				System.out.println(l);
			num++;
		}
		return num;
	}

}

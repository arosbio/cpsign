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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.tests.suites.UnitTest;
import com.google.common.collect.Comparators;
import com.google.common.collect.Sets;

@Category(UnitTest.class)
public class TestCollectionUtils {

	@Test
	public void testNegativeStep() {
		List<Integer> intList = CollectionUtils.listRange(0, -8, -1);
		Assert.assertEquals(9, intList.size());
		Assert.assertEquals(0, (int) intList.get(0));
		Assert.assertEquals(-8, (int) intList.get(8));
		
		List<Double> doubleList = CollectionUtils.listRange(0, -8, -1, 2);
		Assert.assertEquals(9, doubleList.size());
		Assert.assertEquals(Math.pow(2, 0), (double) doubleList.get(0),0.00001);
		Assert.assertEquals(Math.pow(2, -8), (double) doubleList.get(8),0.00001);

		// System.err.println(CollectionUtils.listRange(0, -8, -1));
		// System.err.println(CollectionUtils.listRange(0, -8, -1, 2));
	}

	@Test
	public void testStartAndEndTheSame(){
		// int
		List<Integer> intList = CollectionUtils.listRange(-8, -8, 1);
		Assert.assertEquals(1, intList.size());
		Assert.assertEquals(-8, (int)intList.get(0));
		intList = CollectionUtils.listRange(-8, -8, -1);
		Assert.assertEquals(1, intList.size());
		Assert.assertEquals(-8, (int)intList.get(0));

		// double (positive step)
		List<Double> dList = CollectionUtils.listRange(-8.001, -8, 1);
		Assert.assertEquals(1, dList.size());
		Assert.assertEquals(-8.001, (double)dList.get(0),0.00001);
		dList= CollectionUtils.listRange(-8, -8d, 1);
		Assert.assertEquals(1, dList.size());
		Assert.assertEquals(-8d, (double)dList.get(0),0.00001);
		// Double (neg step)
		dList= CollectionUtils.listRange(-8, -8, -.5);
		Assert.assertEquals(1, dList.size());
		Assert.assertEquals(-8d, (double)dList.get(0),0.00001);
		dList= CollectionUtils.listRange(-8, -8.1, -.5);
		Assert.assertEquals(1, dList.size());
		Assert.assertEquals(-8, (double)dList.get(0),0.00001);

		// Same start and end, with step == 0
		dList = CollectionUtils.listRange(-8d, -8d, 0);
		Assert.assertEquals(1, dList.size());
		Assert.assertEquals(-8d, (double)dList.get(0),0.00001);

		// with base
		List<Double> doubleList = CollectionUtils.listRange(0d, 0d, -1, 2);
		Assert.assertEquals(1, doubleList.size());
		Assert.assertEquals(Math.pow(2, 0), (double) doubleList.get(0),0.00001);

	}
	
	@Test
	public void testListRangeInteger() {
		Assert.assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
				CollectionUtils.listRange(1, 10, 1));
		//		System.err.println(CollectionUtils.listRange(1, 10, 1));

		try {
			CollectionUtils.listRange(0, 1001);
			Assert.fail();
		} catch(IllegalArgumentException e) {}
	}

	@Test
	public void testCountValuesLessThan() {
		Assert.assertEquals(0, CollectionUtils.countValuesSmallerThan(new HashSet<>(), -1));
		Assert.assertEquals(0, CollectionUtils.countValuesSmallerThan(new ArrayList<>(), -1));
		
		Assert.assertEquals(0, CollectionUtils.countValuesSmallerThan(Arrays.asList(1,2,3,4,5,6,7), -1));
		Assert.assertEquals(4, CollectionUtils.countValuesSmallerThan(Arrays.asList(1,2,3,4,5,6,7), 5));
	}

	@Test
	public void testListRangeFailing() {
		List<Double> shouldBe = Arrays.asList(0.05, 0.1, 0.15, 0.2, 0.25, 0.3, 0.35, 0.4, 0.45, 0.5, 0.55, 0.6, 0.65, 0.7000, 0.750, 0.8, 0.8500, 0.900,0.95);
		List<Double> generated = CollectionUtils.listRange(.05, .95, .05);
		Assert.assertEquals(shouldBe.size(), generated.size());
		for (int i=0; i<shouldBe.size(); i++) {
			Assert.assertEquals(shouldBe.get(i), generated.get(i), 0.00001);
		}
	}
	
	@Test
	public void testListRangeWithBase() {
		Assert.assertEquals(Arrays.asList(2.0, 4.0, 8.0),
				CollectionUtils.listRange(1, 3, 1, 2));
		Assert.assertEquals(Arrays.asList(3.0, 9.0, 27.0),
				CollectionUtils.listRange(1, 3, 1, 3));
		Assert.assertEquals(Arrays.asList(0.03125, 0.125, .5),
				CollectionUtils.listRange(-5, 0, 2, 2));
	}

	@Test
	public void testListRange() {
		//		System.out.println(CollectionUtils.listRange(0, 1,.01));

		Assert.assertEquals(Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0), 
				CollectionUtils.listRange(1., 10));
		Assert.assertEquals(Arrays.asList(1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0), 
				CollectionUtils.listRange(1, 10,.5));
		Assert.assertEquals(Arrays.asList(-5.0, -4.5, -4.0, -3.5, -3.0, -2.5, -2.0, -1.5, -1.0, -0.5, 0.0), 
				CollectionUtils.listRange(-5, 0,.5));
		Assert.assertEquals(Arrays.asList(-2.0, -1.5, -1.0, -0.5, 0.0), 
				CollectionUtils.listRange(-2, 0.4,.5));

		List<Double> longList = CollectionUtils.listRange(0, 1,.01);
		int ind = 0;
		for (double i=0; i<=1d; i+=0.01) {
			Assert.assertEquals(i, longList.get(ind), 0.00001);
			ind++;
		}

		try {
			CollectionUtils.listRange(0, 1001, 0);
			Assert.fail();
		} catch(IllegalArgumentException e) {}

		try {
			CollectionUtils.listRange(0, 1001,.5);
			Assert.fail();
		} catch(IllegalArgumentException e) {}
		//		System.err.println();
		//		System.err.println(CollectionUtils.listRange(1, 10, 0.5));
	}

	@Test
	public void testPartitionStatic() {
		// odd
		List<Integer> theList = Arrays.asList(1, 2, 3, 4, 5, 6, 7);
		doTest(theList, 2);
		doTest(theList, 3);
		doTest(theList, 5);

		// even
		theList = Arrays.asList(1, 2, 3, 4, 5, 6);
		doTest(theList, 2);
		doTest(theList, 3);
		doTest(theList, 5);

		try {
			doTest(theList, 0);
			Assert.fail();
		} catch(IllegalArgumentException e) {}

	}


	private <T> void doTest(List<T> theList, int numPerList) {
		List<List<T>> parts = CollectionUtils.partitionStatic(theList, numPerList);
		Assert.assertEquals((int)Math.ceil(((double)theList.size())/numPerList), parts.size());

		int failing = 0;
		for (List<T> part: parts) {
			if (part.size() != numPerList)
				failing++;
		}
		Assert.assertTrue(failing <= 1); // one could always be failing, when stuff does not add up in the end
	}

	@Test
	public void testGetAtArbitraryDepth() {
		Map<Object,Object> map = new HashMap<>();
		map.put("hej", 124);
		map.put("da", "dgdasg");
		Map<Object,Object> nested = new HashMap<>();
		nested.put("sdga", "dsga");
		map.put("nested", nested);
		nested.put("toRem", 215.0);
		Map<Object,Object> nested2 = new HashMap<>();
		nested.put("deeper", nested2);
		nested2.put("secret", "hello");

		Assert.assertEquals("dsga", CollectionUtils.getArbitratyDepth(map, "sdga"));
		Assert.assertEquals("hello", CollectionUtils.getArbitratyDepth(map, "secret"));

		CollectionUtils.removeAtArbitraryDepth(map, "secret"); // remove the secret!
		Assert.assertNull(CollectionUtils.getArbitratyDepth(map, "secret"));
	}

	@Test
	public void testSplitToDisjointSets() {

		//		int numTests = 0;
		for (int n=10; n<=1000; n+=100) {
			doTestDisjointSets(IntStream.range(0, n).boxed().collect(Collectors.toList()), 5);
			doTestDisjointSets(IntStream.range(0, n).boxed().collect(Collectors.toList()), 10);
			//			numTests +=2;
		}
		//		System.out.println("Num tests: " + numTests);
	}

	public <T> void doTestDisjointSets(List<T> originalList, int numSets) {
		List<List<T>> sets = CollectionUtils.getDisjunctSets(originalList, numSets, false);

		Assert.assertEquals(numSets, sets.size());

		List<T> allRecords = new ArrayList<>();
		int maxNumRec=sets.get(0).size(); 
		int minNumRec = maxNumRec;

		for (List<T> s: sets) {
			maxNumRec = Math.max(maxNumRec, s.size());
			minNumRec = Math.min(minNumRec, s.size());
			allRecords.addAll(s);
		}

		Assert.assertTrue("There should not be more than 1 record difference from smallest to largest set",maxNumRec - minNumRec < 2);

		Assert.assertEquals(originalList, allRecords);
	}

	@Test
	public void testGetSortIndices() {
		List<Collection<?>> input = new ArrayList<>();
		input.add(new HashSet<>());
		input.add(Arrays.asList(1,2));
		input.add(Sets.newHashSet(4.2,9.2,6.));
		Assert.assertEquals(Arrays.asList(2,1,0), CollectionUtils.getSortedIndicesBySize(input, false));
		Assert.assertEquals(Arrays.asList(0,1,2), CollectionUtils.getSortedIndicesBySize(input, true));

		// add a null instance - should be of "0" length
		input.add(null);
		// sizes are now; 0,2,3,0: we know what the largest ones are - the other ones should be sorted in the order they appear
		Assert.assertEquals(Arrays.asList(0,3,1, 2), CollectionUtils.getSortedIndicesBySize(input, true));
		Assert.assertEquals(Arrays.asList(2,1,3,0), CollectionUtils.getSortedIndicesBySize(input, false));

		List<List<?>> inputLst = new ArrayList<>();
		inputLst.add(Arrays.asList(1,2,3));
		inputLst.add(null);
		inputLst.add(Arrays.asList(1));
		Assert.assertEquals(Arrays.asList(1,2,0), CollectionUtils.getSortedIndicesBySize(inputLst, true));
		Assert.assertEquals(Arrays.asList(0,2,1), CollectionUtils.getSortedIndicesBySize(inputLst, false));

		inputLst.clear();

		inputLst.add(Arrays.asList(1,2,2));
		inputLst.add(Arrays.asList(1,2));
		inputLst.add(Arrays.asList(1,2));
		inputLst.add(Arrays.asList(1,2));
		inputLst.add(Arrays.asList(1,2));

		Assert.assertEquals(Arrays.asList(1,2,3,4,0), CollectionUtils.getSortedIndicesBySize(inputLst, true));
	}

	@Test
	public void testSortBy(){
		List<Collection<Integer>> input = new ArrayList<>();
		input.add(new HashSet<>());
		input.add(Arrays.asList(1,2));
		input.add(Sets.newHashSet(4,9,6));
		List<Integer> indices = CollectionUtils.getSortedIndicesBySize(input, false);
		List<Collection<Integer>> sorted = CollectionUtils.sortBy(input, indices);
		Assert.assertTrue(Comparators.isInOrder(sorted, new SizeComparator(false)));
	}

	private static class SizeComparator implements Comparator<Collection<?>> {

		private final boolean ascending;
		public SizeComparator(boolean ascending){
			this.ascending = ascending;
		}

		@Override
		public int compare(Collection<?> o1, Collection<?> o2) {
			int cmp = o1.size() - o2.size();
			return ascending ? cmp : - cmp;
		}

	}

	@Test
	public void testGetIndices(){
		List<Object> values = Arrays.asList(1, 4, 8.3, 5);
		Assert.assertEquals(Arrays.asList(5,8.3),CollectionUtils.getIndices(values, Arrays.asList(3,2)));
		try{
			CollectionUtils.getIndices(values, Arrays.asList(-1,2));
			Assert.fail("cannot take index -1");
		} catch (IndexOutOfBoundsException e){
			// -1 is out of bounds
		}

		try{
			CollectionUtils.getIndices(values, Arrays.asList(1,values.size()));
			Assert.fail("cannot take index outsize range");
		} catch (IndexOutOfBoundsException e){
			// values.size() is out of bounds
		}

		Assert.assertTrue("empty indices should return an empty list",CollectionUtils.getIndices(values, new ArrayList<>()).isEmpty());
	}

/*
	@Test
	public void testCloneRange() {

		// inf, lim)
		Range<Double> oneSide = Range.upTo(5.321, BoundType.OPEN);
//		System.err.println(oneSide);
		Range<Double> clonedOneSide = CollectionUtils.clone(oneSide);
//		System.err.println(clonedOneSide);
		Assert.assertEquals(oneSide, clonedOneSide);

		// [lim, inf 
		Range<Integer> otherSide = Range.downTo(-50, BoundType.CLOSED);
//		System.err.println(otherSide);
		Range<Integer> clonedOtherSide = CollectionUtils.clone(otherSide);
//		System.err.println(clonedOtherSide);
		Assert.assertEquals(otherSide, clonedOtherSide);

		// ALL
		Range<BigInteger> all = Range.all();
//		System.err.println(all);
		Range<BigInteger> cpyAll = CollectionUtils.clone(all);
//		System.err.println(cpyAll);
		Assert.assertEquals(all, cpyAll);

		// Single
		Range<Integer> single = Range.singleton(928392836);
//		System.err.println(single);
		Range<Integer> cpySingle = CollectionUtils.clone(single);
//		System.err.println(cpySingle);
		Assert.assertEquals(single, cpySingle);

		// [ ]
		Range<Double> closed = Range.closed(-1.5, 4.3);
//		System.err.println(closed);
		Range<Double> cpyClosed = CollectionUtils.clone(closed);
//		System.err.println(cpyClosed);
		Assert.assertEquals(closed, cpyClosed);

	}
	*/
}

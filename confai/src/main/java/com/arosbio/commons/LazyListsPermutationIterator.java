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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class LazyListsPermutationIterator implements Iterator<List<Integer>> {
	
	private final Integer[] maxIndexes;
	private Integer[] currentIndex;
	
	private int numMaxPermutations, currentPermutationNumber=0;
	
	
	public LazyListsPermutationIterator(List<Integer> list) {
		this.maxIndexes = new Integer[list.size()];
		for (int i=0; i< maxIndexes.length; i++) {
			maxIndexes[i] = list.get(i);
		}
		
		// calculate the maximum number of permutations
		numMaxPermutations = MathUtils.multiplyAllTogetherInt(list);
		currentIndex = new Integer[list.size()]; 
		Arrays.fill(currentIndex, 0);// all 0 to begin with!
		
	}

	@Override
	public boolean hasNext() {
		return currentPermutationNumber < numMaxPermutations;
	}

	@Override
	public List<Integer> next() throws NoSuchElementException {
		if (! hasNext())
			throw new NoSuchElementException("No more permutations");
		// Get the thing to return!
		List<Integer> toReturn = new ArrayList<>(Arrays.asList(currentIndex));
		currentPermutationNumber++;
		
		// bump to next permutation!
		findNext(currentIndex.length-1);
		return toReturn;
	}
	
	private boolean findNext(int i) {
		if (i < 0) {
			// Base case in the recursion (should never ever happen though)
			// NO MORE PERMUTATIONS FOUND!! 
			return false; 
		} 
		
		if (currentIndex[i] + 1 < maxIndexes[i]) {
			// If there's another bump possible at this index
			currentIndex[i]++;
			setAllLowerToZero(i);
			return true;
		} else {
			// Not possible at the current index - go up!
			return findNext(--i);
		}
	}
	
	private void setAllLowerToZero(int index) {
		for (int i=index+1; i<currentIndex.length; i++) {
			currentIndex[i] = 0;
		}
	}

}

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.sampling;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.arosbio.data.Dataset;

public interface TrainSplitIterator extends Iterator<TrainSplit>{

	/**
	 * Get a specific {@link com.arosbio.ml.sampling.TrainSplit TrainSplit} for a given index, should <b>not</b> interfere with the {@link #hasNext()} or {@link #next()} methods 
	 * @param index The index <code>[0,max num indexes)</code>
	 * @return The {@link com.arosbio.ml.sampling.TrainSplit TrainSplit} for the given <code>index</code>, i.e. starts with index 0
	 * @throws NoSuchElementException If index was out of accepted limits
	 */
	public TrainSplit get(int index) throws NoSuchElementException;
	
	/**
	 * Get the {@link com.arosbio.data.Dataset Dataset} used for generating the {@link com.arosbio.ml.sampling.TrainSplit TrainSplit}
	 * @return {@link com.arosbio.data.Dataset Dataset} used for deriving the splits
	 */
	public Dataset getProblem();
	
	public int getMaximumSplitIndex();
	
	public int getMinimumSplitIndex();
}

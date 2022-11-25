/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.splitting;

import java.util.List;

import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.google.common.collect.Range;

public class DataSplit {

	private final Dataset trainSet;
	private final List<DataRecord> testSet;
	private final long seed;
	private final Range<Double> observedLabelSpace;
	
	public DataSplit(Dataset first, List<DataRecord> second, long seed) {
		this.trainSet = first;
		this.testSet = second;
		this.seed = seed;
		this.observedLabelSpace = null;
	}

	public DataSplit(Dataset first, List<DataRecord> second, long seed, Range<Double> observedLabelSpace){
		this.trainSet = first;
		this.testSet = second;
		this.seed = seed;
		this.observedLabelSpace = observedLabelSpace;
	}
	
	public Dataset getFirst() {
		return trainSet;
	}
	
	public List<DataRecord> getSecond(){
		return testSet;
	}

	public Range<Double> getObservedLabelSpace(){
		return observedLabelSpace;
	}

	/**
	 * Get the seed used. So the same seed can be re-used if needed further down in the pipeline
	 * @return the RNG seed used 
	 */
	public long getSeed(){
		return seed;
	}

}

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import com.arosbio.data.DataRecord;

/**
 * Utility methods for SamplingStrategies implementations
 * 
 * @author ola
 * @author staffan
 *
 */
public class CalibrationSetUtils {

	public static List<DataRecord> shuffleList(List<DataRecord> records, long seed){
		List<DataRecord> shuffledList = new ArrayList<>(records.size());

		//randomly draw from old to new
		Random generator = new Random(seed);
		while (records.size()>0){
			int ix = generator.nextInt(records.size());
			shuffledList.add(records.remove(ix));
		}
		// Set the shuffled list back again
		return shuffledList;
	}


	/**
	 * Requires that the DataRecord is sorted on the index (which they should be!!!)
	 * @param dataset dataset to look through
	 * @return the highest feature-number
	 */
	public static int findMaxAttributeIndex(Collection<DataRecord> dataset) {
		int maxIndex=0;
		for (DataRecord rec: dataset){
			maxIndex = Math.max(maxIndex, rec.getMaxFeatureIndex());
		}
		return maxIndex;
	}


}

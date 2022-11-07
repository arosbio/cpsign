/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cheminf.descriptors.fp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openscience.cdk.fingerprint.IBitFingerprint;
import org.openscience.cdk.fingerprint.ICountFingerprint;

import com.arosbio.data.SparseFeature;
import com.arosbio.data.SparseFeatureImpl;

public class FPUtils {
	
	
	public static List<SparseFeature> convert(ICountFingerprint fp, int length){
		
		List<SparseFeature> features = new ArrayList<>();
		// Apply the same hashing as used in CDK CircularFingerprinter implementation:
		Map<Integer,Integer> indexToCount = new HashMap<>();
		for (int n = 0; n < fp.numOfPopulatedbins(); n++) {
            int i = fp.getHash(n);
            long b = i >= 0 ? i : ((i & 0x7FFFFFFF) | (1L << 31));
            int index = (int) (b % length);
            indexToCount.put(index, (indexToCount.getOrDefault(index, 0)+1));
        }
		for (Map.Entry<Integer, Integer> kv : indexToCount.entrySet()) {
			features.add(new SparseFeatureImpl(kv.getKey(), kv.getValue().shortValue()));
		}
		Collections.sort(features);
		return features;
	}
	
	public static List<SparseFeature> convert(IBitFingerprint fp){
		List<SparseFeature> features = new ArrayList<>();
		fp.asBitSet().stream().forEach( b -> features.add(new SparseFeatureImpl(b, (short)1)));
		return features;
	}

}

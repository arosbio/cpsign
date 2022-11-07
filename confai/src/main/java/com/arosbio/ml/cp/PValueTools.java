/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PValueTools {

	public static Set<Integer> getPredictionSet(Map<Integer,Double> pvals, double confidence){
		Set<Integer> predictionSet = new HashSet<>();
		for (Map.Entry<Integer, Double> pval : pvals.entrySet())
			if (pval.getValue()> (1-confidence))
				predictionSet.add(pval.getKey());
		return predictionSet;
	}
	
	public static int getPredictionSetSize(Map<Integer,Double> pvals, double confidence){
		int numInPredSet = 0;
		for (double pval : pvals.values())
			if (pval > (1-confidence))
				numInPredSet++;
		return numInPredSet;
	}
	

}

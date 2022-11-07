/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp.nonconf.calc;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;



public class NCSInterpolationHelper {
	
	public static Pair<double[], double[]> getConfidence2NCS(List<Double> scores){
		double [] confidence = new double[scores.size()];
		double [] nc = new double[scores.size()];
		
		for (int i=0; i<scores.size(); i++) {
			confidence[i] = (1d+i)/(scores.size()+1);
			nc[i] = scores.get(i);
		}
		
		return ImmutablePair.of(confidence, nc);
	}

	public static Pair<double[], double[]> getNCS2Pvalue(List<Double> scores){
		double[] pValues = new double[scores.size()];
		double[] ncs = new double[scores.size()];
		
		Double lastNC = Double.NaN;
		int index = 0;
		
		// First index
		for (int i=0; i<scores.size(); i++) {
			if (i != 0 && !lastNC.equals(scores.get(i))) {
				index++; // Update only if we have a new NCS 
			}
			pValues[index] = ((double)scores.size() - i)/(scores.size()+1);
			ncs[index] = scores.get(i);
			lastNC = scores.get(i);
		}
		index++;
		
		// Get only the parts that was used in the arrays
		double[] ncsFinal = Arrays.copyOfRange(ncs, 0, index);
		double[] pValuesFinal = Arrays.copyOfRange(pValues, 0, index);
		
		return ImmutablePair.of(ncsFinal, pValuesFinal);
	}
}

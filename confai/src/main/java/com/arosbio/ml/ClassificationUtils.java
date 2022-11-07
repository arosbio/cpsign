/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class ClassificationUtils {

	public static List<String> getPredictedLabels(double[] pvals, List<String> labels, double confidence) throws IllegalArgumentException {
		if(pvals.length != labels.size())
			throw new IllegalArgumentException("Number of pvalues and labels must be equal");
		List<String> predictedLabels = new ArrayList<>();
		for(int i=0; i<pvals.length; i++){
			if(pvals[i]>(1-confidence))
				predictedLabels.add(labels.get(i));
		}
		return predictedLabels;
	}

	public static Set<String> getPredictedLabels(Map<String, Double> pvals, double confidence) throws IllegalArgumentException {

		Set<String> predictedLabels = new HashSet<>();
		for (Entry<String,Double>prediction: pvals.entrySet()){
			if (prediction.getValue()>(1-confidence))
				predictedLabels.add(prediction.getKey());
		}
		return predictedLabels;
	}

	public static List<Integer> getPredictedClasses(double[] pvals, double confidence) {
		List<Integer> predictedClasses = new ArrayList<>();
		for(int i=0; i<pvals.length; i++){
			if(pvals[i]>(1-confidence))
				predictedClasses.add(i);
		}
		return predictedClasses;
	}

	public static double getNextHighestPvalue(Map<String, Double> pvals){
		if(pvals.size() == 2){
			return Collections.min(pvals.values()); // return the smallest value
		}
		double highestVal=-1d, nextHighestVal=-1d;

		for(String label: pvals.keySet()){
			if(pvals.get(label)>highestVal){
				nextHighestVal = highestVal;
				highestVal = pvals.get(label);
			} else if(pvals.get(label)> nextHighestVal)
				nextHighestVal = pvals.get(label);
		}

		return nextHighestVal;
	}

	public static double max(Map<String, Double> vals){
		double max=-1d;
		for(Entry<String,Double> val: vals.entrySet())
			if(val.getValue()>max)
				max = val.getValue();

		return max;
	}

	/**
	 * Computes the predicted class. Either for probabilities or some other "prediction score". Computes
	 * the "forced prediction" for CP classifiers 
	 * @param predictionScores prediction scores 
	 * @param <K> the type of the keys
	 * @return the most probable class
	 */
	public static <K> K getPredictedClass(Map<K,Double> predictionScores) {
		if (predictionScores == null || predictionScores.isEmpty())
			throw new IllegalArgumentException("Empty predictions not allowed");
		double maxProb = -1d;
		K predictedLabel = null;
		for (Map.Entry<K, Double> prob : predictionScores.entrySet()) {
			if (! Double.isFinite(prob.getValue())) {
				throw new IllegalArgumentException("Invalid prediction scores="+predictionScores);
			}
			if (prob.getValue() > maxProb) {
				maxProb = prob.getValue();
				predictedLabel = prob.getKey();
			}
		} 
		return predictedLabel;
	}
}

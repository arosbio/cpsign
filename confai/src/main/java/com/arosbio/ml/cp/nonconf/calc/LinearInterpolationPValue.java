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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;

public class LinearInterpolationPValue implements PValueCalculator {

	public static final String NAME = "LinearInterpolation";
	public static final int ID = 3;
	
	private double minConfidence, maxConfidence, minNCS, maxNCS;
	private UnivariateFunction conf2ncsFunction, ncs2pvalFunction;

	private int numCalibScores=0;
	private transient List<Double> ncsScores;

	@Override
	public LinearInterpolationPValue clone() {
		return new LinearInterpolationPValue();
	}
	
	@Override
	public String toString() {
		return NAME;
	}
	
	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public int getID() {
		return ID;
	}
	
	@Override
	public boolean isReady() {
		return ncs2pvalFunction != null && conf2ncsFunction != null;
	}

	@Override
	public void build(List<Double> scores) throws IllegalArgumentException {
		if (scores == null || scores.size() < 2)
			throw new IllegalArgumentException("LinearInterpolation requires at least 2 calibration instances");
		numCalibScores = scores.size();

		LinearInterpolator linearIP = new LinearInterpolator();
		this.ncsScores = new ArrayList<>(scores);
		Collections.sort(ncsScores);

		// Build confidence -> NCS function
		Pair<double[], double[]> conf2ncsLists = NCSInterpolationHelper.getConfidence2NCS(ncsScores);
		conf2ncsFunction = linearIP.interpolate(conf2ncsLists.getLeft(), conf2ncsLists.getRight());
		minConfidence = conf2ncsLists.getLeft()[0];
		maxConfidence = conf2ncsLists.getLeft()[conf2ncsLists.getLeft().length-1];

		// Build the NCS -> pValue
		Pair<double[], double[]> ncs2pvalLists = NCSInterpolationHelper.getNCS2Pvalue(ncsScores);
		ncs2pvalFunction = linearIP.interpolate(ncs2pvalLists.getLeft(), ncs2pvalLists.getRight());
		minNCS = ncsScores.get(0);
		maxNCS = ncsScores.get(ncsScores.size()-1); 
		
	}
	
	@Override
	public List<Double> getNCSscores(){
		return Collections.unmodifiableList(ncsScores);
	}

	@Override
	public double getNCScore(double confidence) throws IllegalStateException, IllegalArgumentException {
		if (! isReady())
			throw new IllegalStateException("NCS estimator not built yet");
		if (confidence < 0 || confidence > 1)
			throw new IllegalArgumentException("p-value cannot be less than 0 or greater than 1");
		if (confidence < minConfidence)
			return conf2ncsFunction.value(minConfidence);
		else if (confidence > maxConfidence)
			return Double.POSITIVE_INFINITY;
		return conf2ncsFunction.value(confidence);
	}

	@Override
	public double getPvalue(double ncs) throws IllegalStateException, IllegalArgumentException {
		if (! isReady())
			throw new IllegalStateException("NCS estimator not built yet");
		// Smaller than encountered before; pvalue = 1 (all others are larger)
		if (ncs <= minNCS) {
			return 1d;
		}
		// Larger than encountered before = 1/(#calib +1) 
		else if (ncs >= maxNCS) {
			return 1d/(numCalibScores+1);
		} 
		// Return the interpolation
		else {
			return ncs2pvalFunction.value(ncs);
		}
	}

	//	@Override
	//	public double getPvalue(double ncs) throws IllegalStateException, IllegalArgumentException {
	//		if (! isReady())
	//			throw new IllegalStateException("NCS estimator not built yet");
	//		// Smaller than encountered before; pvalue = 1 (all others are larger)
	//		if (ncs < scores.get(0))
	//			return 1d;
	//		// Larger than encountered before = 1/(#calib +1) 
	//		else if (ncs > scores.get(scores.size()-1))
	//			return 1d/(scores.size()+1);
	//
	//		// find the position in the list
	//		int numGreater = 0;
	//		int numEqual = 0;
	//		for (int i=scores.size()-1; i>=0; i++) {
	//			if (scores.get(i) > ncs) {
	//				numGreater++;
	//			} else if (MathUtils.equals(scores.get(i),ncs)) {
	//				numEqual++;
	//			} else {
	//				break;
	//			}
	//		}
	//
	//		// when we have an identical NCS in the records - no need to interpolate!
	//		if (numEqual > 0) {
	//			// return the smoothed p-value
	//			// +1 on the numEqual for the record itself
	//			return (numGreater + rng.nextDouble()*(numEqual+1)) / (scores.size()+1);
	//		}
	//		
	//		// Here we're making an interpolation between NCS-scores
	//		// here following the non-smoothed version of p-values
	//		double y0 = (numGreater+1d)/(scores.size()+1);
	//		double x0 = scores.get(scores.size()-numGreater);
	//		
	//		double y1 = ((double)numGreater) / (scores.size()+1);
	//		double x1 = scores.get(scores.size()-numGreater-1);
	//		
	//		// Linear interpolation:
	//		return y0 + (ncs-x0)*(y1-y0)/(x1-x0);
	//
	//	}

	/**
	 * This implementation does not use a seed
	 */
	@Override
	public void setRNGSeed(long seed) {
	}

	/**
	 * This implementation does not use a seed
	 */
	@Override
	public Long getRNGSeed() {
		return null;
	}

}

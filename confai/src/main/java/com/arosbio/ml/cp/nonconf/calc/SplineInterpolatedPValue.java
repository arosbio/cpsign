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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.arosbio.commons.MathUtils;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

/**
 * SplinesInterpolatedPValue fits cubic splines to the nonconformity scores. Note that a previous version of this
 * interpolation had issues with being non-monotonic in the nonconformity scores. Now care have been taken in order
 * to keep monotonic distribution of p-values and nonconformity outputs. 
 * @author staffan
 *
 */
public class SplineInterpolatedPValue implements PValueCalculator {
	
	public static final String NAME = "SplineInterpolation";
	public static final int ID = 4;
	
	private double minConfidence, maxConfidence, minNCS, maxNCS;
	private PolynomialSplineFunction ncs2pvalFunction;
	private PolynomialSplineFunction conf2ncsFunction;
	private int numCalibScores;
	
	private transient List<Double> ncsScores;
	
	@Override
	public SplineInterpolatedPValue clone() {
		return new SplineInterpolatedPValue();
	}
	
	public String toString() {
		return NAME;
	}
	
	public String getName() {
		return NAME;
	}

	public int getID() {
		return ID;
	}
	
	@Override
	public boolean isReady() {
		return ncs2pvalFunction != null && conf2ncsFunction != null;
	}

	@Override
	public void build(List<Double> scores) throws IllegalArgumentException {
		if (scores == null || scores.size() < 3)
			throw new IllegalArgumentException("SplinesInterpolation requires at least 3 calibration instances");
		numCalibScores = scores.size();
		
		this.ncsScores = new ArrayList<>(scores);
		Collections.sort(ncsScores);
		SplineInterpolator splinesIP = new SplineInterpolator();
		
		// Build the NCS -> pValue
		Pair<double[], double[]> ncs2pvalLists = NCSInterpolationHelper.getNCS2Pvalue(ncsScores);
		ncs2pvalFunction = splinesIP.interpolate(ncs2pvalLists.getLeft(), ncs2pvalLists.getRight());
		minNCS = ncsScores.get(0);
		maxNCS = ncsScores.get(ncsScores.size()-1);
		
		// Build pValue -> NCS
		Pair<double[], double[]> conf2ncsLists = NCSInterpolationHelper.getConfidence2NCS(ncsScores);
		conf2ncsFunction = splinesIP.interpolate(conf2ncsLists.getLeft(), conf2ncsLists.getRight());
		minConfidence = conf2ncsLists.getLeft()[0];
		maxConfidence = conf2ncsLists.getLeft()[numCalibScores-1];
	}
	
	@Override
	public List<Double> getNCSscores(){
		return Collections.unmodifiableList(ncsScores);
	}

	@Override
	public double getNCScore(double confidence) throws IllegalStateException,IllegalArgumentException {
		if (! isReady())
			throw new IllegalStateException("NCS estimator not trained yet");
		if (confidence < 0 || confidence > 1)
			throw new IllegalArgumentException("confidence cannot be less than 0 or greater than 1");
		if (confidence <= minConfidence)
			return conf2ncsFunction.value(minConfidence);
		else if (confidence > maxConfidence)
			return Double.POSITIVE_INFINITY;
		else if (MathUtils.equals(confidence, maxConfidence))
			return conf2ncsFunction.value(maxConfidence);

		double[] confs = conf2ncsFunction.getKnots();
		
		int ind = Arrays.binarySearch(confs, confidence);
		if (ind>=0){
			// we're at a knot (albeit unlikely due to equals on floating point value)
			return conf2ncsFunction.value(confidence);
		}
		int lowInd = -(ind+1)-1;
		double minThresh = conf2ncsFunction.value(confs[lowInd]);
		double maxThresh = conf2ncsFunction.value(confs[lowInd+1]);
		
		return MathUtils.truncate(conf2ncsFunction.value(confidence),minThresh,maxThresh);
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
			double[] knots = ncs2pvalFunction.getKnots();
		
			int ind = Arrays.binarySearch(knots, ncs);
			if (ind>=0){
				// we're at a knot (albeit unlikely due to equals on floating point value)
				return ncs2pvalFunction.value(ncs);
			}
			int lowInd = -(ind+1)-1;
			double maxThresh = ncs2pvalFunction.value(knots[lowInd]);
			double minThresh = ncs2pvalFunction.value(knots[lowInd+1]);
			
			return MathUtils.truncate(ncs2pvalFunction.value(ncs),minThresh,maxThresh);
		}
	}
	
	/**
	 * This implementation does not use a seed
	 * @param seed ignored
	 */
	@Override
	public void setRNGSeed(long seed) {
		// Do nothing
	}

	/**
	 * This implementation does not use a seed
	 */
	@Override
	public Long getRNGSeed() {
		return null;
	}

}

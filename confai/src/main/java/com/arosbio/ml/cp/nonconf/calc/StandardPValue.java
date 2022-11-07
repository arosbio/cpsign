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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StandardPValue implements PValueCalculator {

	private static final Logger LOGGER = LoggerFactory.getLogger(StandardPValue.class);
	public static final String NAME = "Standard";
	public static final int ID = 1;

	private List<Double> scores;
	private transient boolean hasDebuggedConfTooHigh = false;

	@Override
	public StandardPValue clone() {
		return new StandardPValue();
	}
	
	public String getName() {
		return NAME;
	}

	public int getID() {
		return ID;
	}

	@Override
	public boolean isReady() {
		return scores != null && !scores.isEmpty();
	}

	@Override
	public void build(List<Double> scores) throws IllegalArgumentException {
		if (scores == null || scores.isEmpty())
			throw new IllegalArgumentException("No NCS given");
		this.scores = new ArrayList<>(scores);
		Collections.sort(this.scores);
	}
	
	@Override
	public List<Double> getNCSscores(){
		return Collections.unmodifiableList(scores);
	}

	@Override
	public double getNCScore(double confidence) throws IllegalStateException {
		if (confidence < 0 || confidence > 1)
			throw new IllegalArgumentException("confidence cannot be less than 0 or greater than 1");
		if (! isReady())
			throw new IllegalStateException("NCS estimator not built yet");


		if (confidence > ((double)scores.size())/(scores.size()+1)){
			if (! hasDebuggedConfTooHigh){
				LOGGER.debug("A higher confidence ({}) than supported by the size of calibration set was requrested, the prediction will be [-Inf, Inf] for all predictions of this confidence level",confidence);
				hasDebuggedConfTooHigh = true;
			}
			return Double.POSITIVE_INFINITY;
		}

		int index = Math.max(
				0, // for when asking for confidence = 0
				(int)Math.ceil(confidence * (scores.size()+1))-1
				);

		return scores.get(index);

	}


	//	double significance = 1 - confidence;
	//
	//	if (significance < 1d/(scores.size()+1)) {
	//		LOGGER.debug("A higher confidence than supported by the size of calibration set was requrested, the prediction will be [-Inf, Inf]!");
	//		return Double.POSITIVE_INFINITY;
	//	}
	//
	//	int index = Math.min(
	//			scores.size()-1,
	//			(int) Math.floor(significance * (scores.size()+1))-1);
	//	return scores.get(index);

	@Override
	public double getPvalue(double ncs) throws IllegalStateException {
		if (! isReady())
			throw new IllegalStateException("NCS estimator not built yet");
		int greaterEqual = 0;

		// Count the instances that are greater or equal to the current ncs
		for (int i=scores.size()-1; i>=0; i--) {
			if (scores.get(i) >= ncs) {
				greaterEqual++;
			} else {
				break;
			}
		}

		return ((double) greaterEqual + 1)/(1+scores.size()); // We count the record itself as well!

	}

	/**
	 * This implementation does not use a seed
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
	
	public String toString() {
		return NAME;
	}

}

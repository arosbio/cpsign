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
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.MathUtils;

public class SmoothedPValue implements PValueCalculator {
	
	public static final String NAME = "Smoothed";
	public static final int ID = 2;

	private static final Logger LOGGER = LoggerFactory.getLogger(SmoothedPValue.class);

	private List<Double> scores;

	private long seed;
	private Random rng;
	private transient boolean hasDebuggedConfTooHigh = false;


	public SmoothedPValue() {
		this(GlobalConfig.getInstance().getRNGSeed());
	}

	public SmoothedPValue(long seed) {
		this.seed = seed;
	}
	
	@Override
	public List<Double> getNCSscores(){
		return Collections.unmodifiableList(scores);
	}

	@Override
	public SmoothedPValue clone() {
		return new SmoothedPValue(seed);
	}
	
	public String getName() {
		return NAME;
	}

	public int getID() {
		return ID;
	}
	
	public String toString() {
		return NAME;
	}

	@Override
	public boolean isReady() {
		return scores != null && !scores.isEmpty() && rng != null;
	}

	@Override
	public void build(List<Double> scores) throws IllegalArgumentException {
		if (scores == null || scores.isEmpty())
			throw new IllegalArgumentException("No NCS given");
		this.scores = new ArrayList<>(scores);
		Collections.sort(this.scores);
		rng = new Random(seed);
	}

	@Override
	public double getNCScore(double confidence) throws IllegalStateException, IllegalArgumentException {
		if (! isReady())
			throw new IllegalStateException("NCS estimator not built yet");
		if (confidence < 0 || confidence > 1)
			throw new IllegalArgumentException("confidence cannot be less than 0 or greater than 1");

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
	
	@Override
	public double getPvalue(double ncs) throws IllegalStateException {
		if (! isReady())
			throw new IllegalStateException("NCS estimator not built yet");
		int greaterThanCurrent = 0;
		int equalTooCurrent = 0;

		// Count the instances that are greater or equal to the current ncs
		for (int i=scores.size()-1; i>=0; i--) {
			if (scores.get(i) > ncs) {
				greaterThanCurrent++;
			} else if (MathUtils.equals(scores.get(i),ncs)){
				equalTooCurrent++;
			} else {
				break;
			}
		}

		equalTooCurrent++; // We count the record itself as well!

		return ((double)greaterThanCurrent + rng.nextDouble()*equalTooCurrent)/(scores.size() + 1); 
	}

	/**
	 * This implementation does not use a seed
	 */
	@Override
	public void setRNGSeed(long seed) {
		this.seed = seed;
		if (rng != null)
			rng = new Random(seed);
	}

	/**
	 * This implementation does not use a seed
	 */
	@Override
	public Long getRNGSeed() {
		return seed;
	}

}

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.testutils;

import java.util.List;
import java.util.Map;

import com.arosbio.data.DataRecord;
import com.arosbio.data.FeatureVector;
import com.arosbio.ml.algorithms.svm.LinearSVC;

public class MockFailingLinearSVC extends LinearSVC {
	
	public double probTrainError = 0.15, probTrainExcept=.15;
	public double probPredError = 0.005, probPredExcept = 0.005;

	private Error getErr(boolean train) {
		return new Error("Random error during: " + (train ? "training" : "predicting"));
	}
	
	private RuntimeException getExcpt(boolean train) {
		return new RuntimeException("Random exception during: " + (train ? "training" : "predicting"));
	}
	
	
	public void train(List<DataRecord> recs) {
		double p = Math.random();
		if (p < probTrainError) {
			throw getErr(true);
		} 
		if (p> (1-probTrainExcept)) {
			throw getExcpt(true);
		}
		super.train(recs);
	}
	
	private void checkPredictFail() {
		double p = Math.random();
		if (p < probPredError) {
			throw getErr(false);
		} 
		if (p> (1-probPredExcept)) {
			throw getExcpt(false);
		}
	}
	
	@Override
	public int predictClass(FeatureVector feature) throws IllegalStateException {
		checkPredictFail();
		return super.predictClass(feature);
	}

	@Override
	public Map<Integer, Double> predictScores(FeatureVector example) throws IllegalStateException {
		checkPredictFail();
		return super.predictScores(example);
	}

	@Override
	public Map<Integer, Double> predictDistanceToHyperplane(FeatureVector example) throws IllegalStateException {
		checkPredictFail();
		return super.predictDistanceToHyperplane(example);
	}
	
	/**
	 * Clones only the probability of errors/exception - not underlying SVC stuff
	 */
	public MockFailingLinearSVC clone() {
		MockFailingLinearSVC c = new MockFailingLinearSVC();
		c.probPredError = probPredError;
		c.probPredExcept = probPredExcept;
		c.probTrainError = probTrainError;
		c.probTrainExcept = c.probTrainExcept;
		return c;
	}
}

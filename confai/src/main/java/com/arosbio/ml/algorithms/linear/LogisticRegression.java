/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.algorithms.linear;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import com.arosbio.commons.config.EnumConfig;
import com.arosbio.data.DataRecord;
import com.arosbio.data.FeatureVector;
import com.arosbio.ml.algorithms.MultiLabelClassifier;
import com.arosbio.ml.algorithms.PseudoProbabilisticClassifier;
import com.arosbio.ml.algorithms.impl.DefaultMLParameterSettings;
import com.arosbio.ml.algorithms.impl.LibLinear;
import com.google.common.collect.ImmutableList;

import de.bwaldvogel.liblinear.Model;
import de.bwaldvogel.liblinear.Parameter;
import de.bwaldvogel.liblinear.SolverType;

public class LogisticRegression implements MultiLabelClassifier, PseudoProbabilisticClassifier {

	public static final String ALG_NAME = "LogisticRegression";
	public static final int ALG_ID = 16;
	public final static SolverType DEFAULT_SOLVER = SolverType.L2R_LR;
	public final static EnumSet<SolverType> ALLOWED_SOLVERS = EnumSet.of(
			SolverType.L1R_LR,
			SolverType.L2R_LR, 
			SolverType.L2R_LR_DUAL);

	/**
	 * Parameters that holds all info
	 */
	private Parameter parameters = LibLinear.getDefaultParams(DEFAULT_SOLVER);
	private Model svm;

	public static EnumSet<SolverType> getAllowedSolvers(){
		return ALLOWED_SOLVERS;
	}
	
	@Override
	public String getName() {
		return ALG_NAME;
	}

	@Override
	public int getID() {
		return ALG_ID;
	}
	
	@Override
	public String getDescription() {
		return "Logistic regression implemented in LIBLINEAR. Performs multi-class classification and outputs probability estimates";
	}

	public SolverType getSolverType() {
		return parameters.getSolverType();
	}

	public void setSolverType(SolverType type) {
		if (!ALLOWED_SOLVERS.contains(type)) {
			throw new IllegalArgumentException("SolverType not allowed for algorithm " + ALG_NAME);
		} 
		parameters.setSolverType(type);
	}

	public double getC() {
		return parameters.getC();
	}

	public void setC(double cost) {
		parameters.setC(cost);
	}

	public double getEpsilon() {
		return parameters.getEps();
	}

	public void setEpsilon(double eps) {
		parameters.setEps(eps);
	}
	
	public int getMaxNumIterations() {
		return parameters.getMaxIters();
	}
	
	public void setMaxNumIterations(int maxIterations) {
		if (maxIterations < 1)
			parameters.setMaxIters(LibLinear.DEFAULT_MAX_ITERATIONS);
		else
			parameters.setMaxIters(maxIterations);
	}
	
	/**
	 * Liblinear does not have any internal seed, this is ignored
	 */
	@Override
	public void setSeed(long seed) {
	}

	/**
	 * No internal seed used, will always return -1
	 */
	@Override
	public Long getSeed() {
		return null;
	}

	@Override
	public boolean isFitted() {
		return svm!=null;
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String,Object> prop = LibLinear.toProperties(parameters);
		prop.put(ML_NAME_PARAM_KEY, ALG_NAME);
		prop.put(ML_ID_PARAM_KEY, ALG_ID);
		return prop;
	}
	
	@Override
	public List<Integer> getLabels() {
		return LibLinear.getLabels(svm);
	}

	@Override
	public LogisticRegression clone() {
		LogisticRegression clone = new LogisticRegression();
		// Only copy the actual parameters 
		clone.parameters = parameters.clone();
		return clone;
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
		Parameter clone = parameters.clone();
		LibLinear.setConfigParameters(clone, ALLOWED_SOLVERS, params);
		parameters = clone; 
	}

	@Override
	public List<ConfigParameter> getConfigParameters(){
		return ImmutableList.of(
			DefaultMLParameterSettings.COST_CONFIG,
			DefaultMLParameterSettings.EPSILON_CONFIG,
			new EnumConfig.Builder<>(
				LibLinear.SOLVER_TYPE_PARAM_NAMES, 
				ALLOWED_SOLVERS,DEFAULT_SOLVER).build());
	}

	@Override
	public String toString() {
		return ALG_NAME;
	}
	
	/* 
	 * =================================================
	 * 			TRAIN
	 * =================================================
	 */
	
	@Override
	public void train(List<DataRecord> trainingset) throws IllegalArgumentException {
		svm = LibLinear.train(parameters, trainingset);
	}

	@Override
	public void fit(List<DataRecord> trainingset) throws IllegalArgumentException {
		train(trainingset);
	}
	
	/* 
	 * =================================================
	 * 			PREDICTIONS
	 * =================================================
	 */

	@Override
	public int predictClass(FeatureVector feature) throws IllegalStateException {
		return LibLinear.predictClass(svm,feature);
	}

	@Override
	public Map<Integer, Double> predictScores(FeatureVector example) throws IllegalStateException {
		return LibLinear.predictProbabilties(svm,example);
	}

	@Override
	public Map<Integer, Double> predictProbabilities(FeatureVector example) throws IllegalStateException {
		return LibLinear.predictProbabilties(svm,example);
	}
	
	/* 
	 * =================================================
	 * 			I/O
	 * =================================================
	 */

	@Override
	public void saveToStream(OutputStream ostream) throws IOException, IllegalStateException {
		LibLinear.saveToStream(svm, ostream);
	}

	@Override
	public void loadFromStream(InputStream istream) throws IOException {
		svm = LibLinear.loadFromStream(istream);
	}

}

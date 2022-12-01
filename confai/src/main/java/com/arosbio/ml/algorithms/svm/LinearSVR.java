/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.algorithms.svm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.config.EnumConfig;
import com.arosbio.data.DataRecord;
import com.arosbio.data.FeatureVector;
import com.arosbio.ml.algorithms.impl.DefaultMLParameterSettings;
import com.arosbio.ml.algorithms.impl.LibLinear;

import de.bwaldvogel.liblinear.Model;
import de.bwaldvogel.liblinear.Parameter;
import de.bwaldvogel.liblinear.SolverType;

public class LinearSVR implements SVR {

	public static final String ALG_NAME="LinearSVR";
	public static final int ALG_ID = 1;
	public final static EnumSet<SolverType> ALLOWED_SOLVERS = EnumSet.of(
			SolverType.L2R_L2LOSS_SVR,
			SolverType.L2R_L1LOSS_SVR_DUAL, 
			SolverType.L2R_L2LOSS_SVR_DUAL);
	public final static SolverType DEFAULT_SOLVER = SolverType.L2R_L2LOSS_SVR_DUAL;

	/**
	 * Parameters that holds all info
	 */
	private Parameter parameters = LibLinear.getDefaultParams(DEFAULT_SOLVER);
	private Model svm;

	public LinearSVR(){
		this.parameters.setRandom(new Random(GlobalConfig.getInstance().getRNGSeed()));
	}

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
	public String toString() {
		return ALG_NAME;
	}
	
	@Override
	public String getDescription() {
		return "Support Vector Regression (SVR) implemented in LIBLINEAR. Restricted to a linear kernel and optimized for fast training and predictions for linear kernel SVM. Should be prefered over LIBSVM implementation with a linear kernel.";
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
	
	public double getSVREpsilon() {
		return parameters.getP();
	}

	public void setSVREpsilon(double eps) {
		parameters.setP(eps);
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
	
	@Override
	public void setSeed(long seed) {
		this.parameters.setRandom(new Random(seed));
	}

	/**
	 * Cannot access LibLinear internal seed, this method returns {@code null}
	 * @return {@code null}
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
	public LinearSVR clone() {
		LinearSVR clone = new LinearSVR();
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
		return Arrays.asList(
			DefaultMLParameterSettings.COST_CONFIG,
			DefaultMLParameterSettings.EPSILON_CONFIG,
			DefaultMLParameterSettings.SVR_EPSILON_CONFIG,
			new EnumConfig.Builder<>(
				LibLinear.SOLVER_TYPE_PARAM_NAMES,
				ALLOWED_SOLVERS,DEFAULT_SOLVER).build());
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
		svm = LibLinear.train(parameters, trainingset);
	}
	
	/* 
	 * =================================================
	 * 			PREDICTIONS
	 * =================================================
	 */

	@Override
	public double predictValue(FeatureVector feature) throws IllegalStateException {
		return LibLinear.predictValue(svm,feature);
	}

	@Override
	public Map<Integer, Double> predictDistanceToHyperplane(FeatureVector example) throws IllegalStateException {
		return LibLinear.predictDistanceToHyperplane(svm,example);
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
		//		parameters.setParamsAndValidate(svm.getParameters()); //TODO
	}

}

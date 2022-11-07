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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.arosbio.commons.GlobalConfig;
import com.arosbio.data.DataRecord;
import com.arosbio.data.FeatureVector;
import com.arosbio.ml.algorithms.impl.DefaultMLParameterSettings;
import com.arosbio.ml.algorithms.impl.LibSvm;
import com.arosbio.ml.algorithms.impl.LibSvm.KernelType;
import com.arosbio.ml.algorithms.impl.LibSvm.SvmType;

import libsvm.svm_model;
import libsvm.svm_parameter;

public class EpsilonSVR implements SVR {

	public static final String ALG_NAME = "EpsilonSVR";
	public static final int ALG_ID = 2;

	/**
	 * Parameters that holds all info
	 */
	private svm_parameter parameters = LibSvm.getDefaultParams(SvmType.EPSILON_SVR);
	private svm_model svm;
	private long seed = GlobalConfig.getInstance().getRNGSeed();


	public double getC() {
		return parameters.C;
	}

	public void setC(double cost) {
		parameters.C = cost;
	}

	public double getEpsilon() {
		return parameters.eps;
	}

	public void setEpsilon(double eps) {
		parameters.eps = eps;
	}

	public double getSVREpsilon() {
		return parameters.p;
	}

	public void setSVREpsilon(double eps) {
		parameters.p = eps;
	}

	// Kernel type
	public void setKernel(KernelType kernel) {
		parameters.kernel_type = kernel.id;
	}

	public KernelType getKernel() {
		return KernelType.forID(parameters.kernel_type);
	}

	/// Gamma
	public void setGamma(double gamma){
		if (gamma < 0)
			throw new IllegalArgumentException("Parameter 'gamma' must be >=0");
		parameters.gamma = gamma;
	}

	public double getGamma() {
		return parameters.gamma;
	}

	// KERNEL DEGREE
	public void setDegree(int degree) {
		parameters.degree = degree;
	}

	public int getDegree() {
		return parameters.degree;
	}

	// KERNEL COEF0
	public void setCoef0(double coef0) {
		parameters.coef0=coef0;
	}

	public double getCoef0() {
		return parameters.coef0;
	}

	// CACHE SIZE
	public void setCacheSize(double cacheMB) {
		if (cacheMB < 100)
			throw new IllegalArgumentException("Parameter 'cache-size' must be >=100");
		parameters.cache_size = cacheMB;
	}

	public double getCacheSize() {
		return parameters.cache_size;
	}

	// SHRINKING
	public void setShrinking(boolean doShrinking) {
		parameters.shrinking = (doShrinking? 1 : 0);
	}

	public boolean getShrinking() {
		return parameters.shrinking == 0 ? false : true;
	}


	public List<ConfigParameter> getConfigParameters(){
		List<ConfigParameter> params = new ArrayList<>();
		params.add(DefaultMLParameterSettings.COST_CONFIG);
		params.add(DefaultMLParameterSettings.SVR_EPSILON_CONFIG);
		params.addAll(LibSvm.GENERAL_CONFIG_PARAMS);
		return params;
	}
	
	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
		svm_parameter clone = (svm_parameter)parameters.clone();
		LibSvm.setConfigParameters(clone, params);
		parameters = clone;
	}

	@Override
	public EpsilonSVR clone() {
		EpsilonSVR clone = new EpsilonSVR();
		// Only copy the actual parameters 
		clone.parameters = (svm_parameter) parameters.clone();
		return clone;
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
		return "Support Vector Regression (SVR) implemented in LIBSVM.";
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String,Object> prop = LibSvm.toProperties(parameters);
		prop.put(ML_NAME_PARAM_KEY, ALG_NAME);
		prop.put(ML_ID_PARAM_KEY, ALG_ID);
		return prop;
	}

	@Override
	public void setSeed(long seed) {
		this.seed = seed;
	}

	@Override
	public Long getSeed() {
		return seed;
	}

	@Override
	public boolean isFitted() {
		return svm!=null;
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
		svm = LibSvm.train(parameters, trainingset, seed);
	}

	@Override
	public void fit(List<DataRecord> trainingset) throws IllegalArgumentException {
		svm = LibSvm.train(parameters, trainingset, seed);
	}

	/* 
	 * =================================================
	 * 			PREDICTIONS
	 * =================================================
	 */

	@Override
	public double predictValue(FeatureVector example) throws IllegalStateException {
		return LibSvm.predictValue(svm, example);
	}

	@Override
	public Map<Integer, Double> predictDistanceToHyperplane(FeatureVector example) throws IllegalStateException {
		return LibSvm.predictDistanceToHyperplane(svm, example);
	}

	/* 
	 * =================================================
	 * 			I/O
	 * =================================================
	 */

	@Override
	public void saveToStream(OutputStream ostream) throws IOException, IllegalStateException {
		LibSvm.saveToStream(svm, ostream);
	}

	@Override
	public void loadFromStream(InputStream istream) throws IOException {
		svm = LibSvm.loadFromStream(istream);
		parameters = svm.param;
	}

}

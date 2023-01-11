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
import com.arosbio.ml.algorithms.MultiLabelClassifier;
import com.arosbio.ml.algorithms.PseudoProbabilisticClassifier;
import com.arosbio.ml.algorithms.impl.LibSvm;
import com.arosbio.ml.algorithms.impl.LibSvm.KernelType;
import com.arosbio.ml.algorithms.impl.LibSvm.SvmType;

import libsvm.svm_model;
import libsvm.svm_parameter;

public class PlattScaledNuSVC implements SVC, MultiLabelClassifier, PseudoProbabilisticClassifier {

	public static final String ALG_NAME = "PlattScaledNuSVC";
	public static final int ALG_ID = 15;

	/**
	 * Parameters that holds all info
	 */
	private svm_parameter parameters = LibSvm.getDefaultParams(SvmType.NU_SVC);
	private svm_model svm;
	private long seed = GlobalConfig.getInstance().getRNGSeed();

	public PlattScaledNuSVC() {
		parameters.probability = 1;
	}
	
	// Nu
	public double getNu() {
		return parameters.nu;
	}

	public void setNu(double nu) {
		parameters.nu = nu;
	}

	public PlattScaledNuSVC withNu(double nu){
		parameters.nu = nu;
		return this;
	}

	// Epsilon
	public double getEpsilon() {
		return parameters.eps;
	}

	public void setEpsilon(double eps) {
		parameters.eps = eps;
	}

	public PlattScaledNuSVC withEpsilon(double eps) {
		parameters.eps = eps;
		return this;
	}

	// Kernel type
	public KernelType getKernel() {
		return KernelType.forID(parameters.kernel_type);
	}

	public void setKernel(KernelType kernel) {
		parameters.kernel_type = kernel.id;
	}

	public PlattScaledNuSVC withKernel(KernelType kernel) {
		parameters.kernel_type = kernel.id;
		return this;
	}

	/// Gamma
	public double getGamma() {
		return parameters.gamma;
	}

	public void setGamma(double gamma){
		if (gamma < 0)
			throw new IllegalArgumentException("Parameter 'gamma' must be >=0");
		parameters.gamma = gamma;
	}

	public PlattScaledNuSVC withGamma(double gamma){
		setGamma(gamma);
		return this;
	}

	// KERNEL DEGREE
	public int getDegree() {
		return parameters.degree;
	}

	public void setDegree(int degree) {
		parameters.degree = degree;
	}

	public PlattScaledNuSVC withDegree(int degree) {
		parameters.degree = degree;
		return this;
	}

	// KERNEL COEF0
	public double getCoef0() {
		return parameters.coef0;
	}

	public void setCoef0(double coef0) {
		parameters.coef0=coef0;
	}

	public PlattScaledNuSVC withCoef0(double coef0) {
		parameters.coef0=coef0;
		return this;
	}

	// CACHE SIZE
	public double getCacheSize() {
		return parameters.cache_size;
	}

	public void setCacheSize(double cacheMB) {
		if (cacheMB < 100)
			throw new IllegalArgumentException("Parameter 'cache-size' must be >=100");
		parameters.cache_size = cacheMB;
	}

	public PlattScaledNuSVC withCacheSize(double cacheMB) {
		setCacheSize(cacheMB);
		return this;
	}
	

	// SHRINKING
	public boolean getShrinking() {
		return parameters.shrinking == 0 ? false : true;
	}

	public void setShrinking(boolean doShrinking) {
		parameters.shrinking = (doShrinking? 1 : 0);
	}

	public PlattScaledNuSVC withShrinking(boolean doShrinking) {
		setShrinking(doShrinking);
		return this;
	}

	// Seed
	@Override
	public Long getSeed() {
		return seed;
	}

	@Override
	public void setSeed(long seed) {
		this.seed = seed;
	}

	public PlattScaledNuSVC withSeed(long seed){
		this.seed = seed;
		return this;
	}

	@Override
	public List<ConfigParameter> getConfigParameters(){
		List<ConfigParameter> params = new ArrayList<>();
		params.add(LibSvm.NU_CONFIG);
		params.addAll(LibSvm.GENERAL_CONFIG_PARAMS);
		return params;
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
		svm_parameter clone = (svm_parameter) parameters.clone();
		LibSvm.setConfigParameters(clone, params);
		parameters = clone; // trick to not update unless everything passes
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String,Object> prop = LibSvm.toProperties(parameters);
		prop.put(ML_NAME_PARAM_KEY, ALG_NAME);
		prop.put(ML_ID_PARAM_KEY, ALG_ID);
		return prop;
	}

	@Override
	public boolean isFitted() {
		return svm != null;
	}

	@Override
	public List<Integer> getLabels() {
		return LibSvm.getLabels(svm);
	}

	@Override
	public PlattScaledNuSVC clone() {
		PlattScaledNuSVC clone = new PlattScaledNuSVC();
		// Only copy the actual parameters 
		clone.parameters = (svm_parameter) parameters.clone();
		clone.seed = seed;
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
	public String toString() {
		return ALG_NAME;
	}
	
	@Override
	public String getDescription() {
		return "Support Vector Classification (SVC) implemented in LIBSVM, using Platt scaling and an internal cross-validation to output probability-estimates. Uses the 'nu' parameter instead of 'C'.";
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
	public int predictClass(FeatureVector feature) throws IllegalStateException {
		return LibSvm.predictClass(svm, feature);
	}

	@Override
	public Map<Integer, Double> predictScores(FeatureVector example) throws IllegalStateException {
		return LibSvm.predictProbabilities(svm, example);
	}

	@Override
	public Map<Integer, Double> predictDistanceToHyperplane(FeatureVector example) throws IllegalStateException {
		return LibSvm.predictDistanceToHyperplane(svm, example);
	}
	
	@Override
	public Map<Integer, Double> predictProbabilities(FeatureVector example) throws IllegalStateException {
		return LibSvm.predictProbabilities(svm, example);
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

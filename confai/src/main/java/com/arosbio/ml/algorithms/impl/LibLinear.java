/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.algorithms.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.StringUtils;
import com.arosbio.commons.TypeUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.MissingDataException;
import com.arosbio.data.MissingValueFeature;

import de.bwaldvogel.liblinear.Feature;
import de.bwaldvogel.liblinear.FeatureNode;
import de.bwaldvogel.liblinear.Linear;
import de.bwaldvogel.liblinear.Model;
import de.bwaldvogel.liblinear.Parameter;
import de.bwaldvogel.liblinear.Problem;
import de.bwaldvogel.liblinear.SolverType;

/**
 * Wrapper class for LIBLINEAR (https://www.csie.ntu.edu.tw/~cjlin/liblinear/) 
 * 
 * @author staffan
 *
 */
public class LibLinear {

	private static final Logger LOGGER = LoggerFactory.getLogger(LibLinear.class);

	public final static int DEFAULT_MAX_ITERATIONS = 1000;
	private static final double bias = 1; // The bias term

	//	// TUNABLE PARAMETERS
	public static final List<String> SOLVER_TYPE_PARAM_NAMES = Arrays.asList("solverType", "solver");
	public static final List<String> MAX_ITERATIONS_PARAM_NAMES = Arrays.asList("maxIterations");

	// Remove logging
	static {
		Linear.setDebugOutput(null);
	}
	
	// Should never instantiate this class
	private LibLinear() {}


	public static Parameter getDefaultParams(SolverType type) {
		return new Parameter(type,
				DefaultMLParameterSettings.DEFAULT_C, 
				DefaultMLParameterSettings.DEFAULT_EPSILON, 
				DEFAULT_MAX_ITERATIONS, 
				DefaultMLParameterSettings.DEFAULT_SVR_EPSILON);
	}

	public static Map<String,Object> toProperties(Parameter p){
		Map<String,Object> props = new HashMap<>();
		props.put(DefaultMLParameterSettings.COST_PARAM_NAMES.get(0), p.getC());
		props.put(DefaultMLParameterSettings.EPSILON_PARAM_NAMES.get(0), p.getEps());
		props.put(DefaultMLParameterSettings.SVR_EPSILON_PARAM_NAMES.get(0), p.getP());
		props.put(SOLVER_TYPE_PARAM_NAMES.get(0), p.getSolverType().getId());
		props.put(MAX_ITERATIONS_PARAM_NAMES.get(0), p.getMaxIters());
		return props;
	}

	public static List<Integer> getLabels(Model model){
		if (model==null)
			return new ArrayList<>();
		try{
			List<Integer> labels = new ArrayList<>();
			for(int l: model.getLabels()) {
				labels.add(l);
			}
			return labels;
		} catch (NullPointerException npe){
			return new ArrayList<>();
		}
	}


	public static void setConfigParameters(Parameter original, EnumSet<SolverType> allowedSolvers, Map<String,Object> params) 
			throws IllegalArgumentException {
		
		for (Map.Entry<String, ? extends Object> p : params.entrySet()) {
			try {
				if (CollectionUtils.containsIgnoreCase(DefaultMLParameterSettings.COST_PARAM_NAMES, p.getKey())) {
					original.setC(TypeUtils.asDouble(p.getValue()));
				} else if (CollectionUtils.containsIgnoreCase(DefaultMLParameterSettings.EPSILON_PARAM_NAMES, p.getKey())) {
					original.setEps(TypeUtils.asDouble(p.getValue()));
				} else if (CollectionUtils.containsIgnoreCase(DefaultMLParameterSettings.SVR_EPSILON_PARAM_NAMES, p.getKey())) {
					original.setP(TypeUtils.asDouble(p.getValue()));
				} else if (CollectionUtils.containsIgnoreCase(SOLVER_TYPE_PARAM_NAMES, p.getKey())) {
					try {
						SolverType newType = null;
						if (TypeUtils.isInt(p.getValue())) {
							newType = SolverType.getById(TypeUtils.asInt(p.getValue()));
						} else if (p.getValue() instanceof String) {
							newType = SolverType.valueOf(p.getValue().toString());
						} else if (p.getValue() instanceof SolverType) {
							newType = (SolverType) p.getValue();
						} else {
							throw new IllegalArgumentException("Parameter '"+p.getKey()+"' cannot be interpreted as a proper SolverType, was: " + p.getValue());
						}
						// Verify that it's an allowed solvertype
						if (! allowedSolvers.contains(newType)) {
							throw new IllegalArgumentException("Parameter '"+p.getKey()+"' not allowed to take value: " + p.getValue());
						}
						original.setSolverType(newType);
					} catch (Exception e) {
						throw new IllegalArgumentException(e.getMessage());
					}
				}
				
				// Fall through on parameters that are not used
			} catch (Exception e) {
				LOGGER.debug("Failed setting parameter {} with value: {}",p.getKey(), p.getValue());
				throw new IllegalArgumentException("Invalid argument for parameter '" + p.getKey() + "': " + e.getMessage());
			}
		}
	}


	/* 
	 * =================================================
	 * 			TRAINING
	 * =================================================
	 */

	public static Model train(Parameter params, List<DataRecord> trainingSet) throws IllegalArgumentException{
		return train(params, createLibLinearTrainProblem(trainingSet));
	}

	public static Model train(Parameter params, Problem problem) throws IllegalArgumentException {
		if (problem.l == 0)
			throw new IllegalArgumentException("Training set cannot be empty");
		LOGGER.trace("Training liblinear model with #records={}, #attributes={}, using parameters={}",
				problem.l,problem.n,params.toString());

		// Do the training!
		Model model = Linear.train(problem, params);
		LOGGER.debug("Finished training the linear model");
		return model;
		
	}

	/* 
	 * =================================================
	 * 			UTILS
	 * =================================================
	 */

	public static Problem createLibLinearTrainProblem(
			List<DataRecord> trainingSet) {
		LOGGER.debug("trainingSet.size={}", trainingSet.size());
		int maxFeatIndex = DataUtils.getMaxFeatureIndex(trainingSet) + 1; // +1 for indices starting at 1 instead of 0
		int biasColumn = maxFeatIndex+1;
		Problem trainProblem = new Problem();
		trainProblem.l = trainingSet.size();
		trainProblem.n = biasColumn; // add 1 for bias term 
		trainProblem.x = new Feature[trainProblem.l][];
		trainProblem.y = new double[trainProblem.l];
		trainProblem.bias = bias;

		try {
			for (int ex=0; ex < trainProblem.l; ex++) {
				// Copy the target value
				trainProblem.y[ex] = trainingSet.get(ex).getLabel();
				// Convert the feature vector
				trainProblem.x[ex] = createFeatureArray(trainingSet.get(ex).getFeatures(),biasColumn);
			}
		} catch (MissingDataException e) {
			LOGGER.debug("Failed setting up LibLinear problem due to missing data: ",e);
			throw new MissingDataException("Failed training LibLinear model due to missing data - please revise your pre-processing");
		}

		LOGGER.trace("prob.l={}, prob.n={}, prob.x.len={}, prob.y.len={}",
				trainProblem.l, trainProblem.n, trainProblem.x.length, trainProblem.y.length);
		return trainProblem;
	}

	public static Problem clone(Problem problem){
		Problem clone = new Problem();
		clone.l = problem.l;
		clone.n = problem.n;
		clone.x = problem.x.clone();
		clone.y = problem.y.clone();
		clone.bias = problem.bias;

		return clone;
	}
	

	public static Feature[] createFeatureArray(FeatureVector feats, Model m){
		return createFeatureArray(feats, m.getNrFeature()+1);
	}
	public static Feature[] createFeatureArray(FeatureVector feats, int biasCol){
		Feature[] nodes = new Feature[feats.getNumExplicitFeatures()+1]; // Add one for the bias column

		int index = 0;
		List<Integer> missingDataIndices = new ArrayList<>();
		for (FeatureVector.Feature f : feats) {
			if (f instanceof MissingValueFeature || !Double.isFinite(f.getValue())) {
				missingDataIndices.add(f.getIndex());
			}
			nodes[index] = new FeatureNode(
					f.getIndex() +1 , // Need to add one as features starts at 0, liblinear requires start at 1!  
					f.getValue());
			index++;
		}
		nodes[index] = new FeatureNode(biasCol, bias);
		if (!missingDataIndices.isEmpty()) {
			throw new MissingDataException("Encountered feature(s) with missing data (index): " + StringUtils.toStringNoBrackets(missingDataIndices));
		}

		return nodes;
	}

	/* 
	 * =================================================
	 * 			PREDICTIONS
	 * =================================================
	 */

	private static void assertFittedModel(Model model) throws IllegalStateException {
		if (model == null)
			throw new IllegalStateException("Model not fitted");
	}


	public static double predictValue(Model model, FeatureVector example) throws IllegalStateException {
		return predictValue(model,createFeatureArray(example,model));
	}

	public static double predictValue(Model model, Feature[] instance) throws IllegalStateException {
		assertFittedModel(model);
		return Linear.predict(model, instance);
	}

	public static  int predictClass(Model model,FeatureVector example) 
			throws IllegalStateException {
		return predictClass(model,createFeatureArray(example,model));
	}

	public static int predictClass(Model model,Feature[] instance) 
			throws IllegalStateException {
		assertFittedModel(model);
		return (int) Linear.predict(model, instance);
	}

	public static Map<Integer, Double> predictDistanceToHyperplane(Model model,FeatureVector example) throws IllegalStateException {
		return predictDistanceToHyperplane(model,createFeatureArray(example, model));
	}

	public static Map<Integer, Double> predictDistanceToHyperplane(Model model,Feature[] example) throws IllegalStateException {
		assertFittedModel(model);

		int[] labels = model.getLabels();
		double decValues[] = new double[labels.length];
		Linear.predictValues(model, example, decValues);

		// Convert to the labels used
		Map<Integer,Double> prediction = new HashMap<>();
		if (model.getNrClass() ==2) {
			// Special treat binary classification - only gives a single value
			prediction.put(labels[0], decValues[0]);
			prediction.put(labels[1], -1*decValues[0]);
		} else {
			for (int i=0; i<decValues.length; i++) {
				prediction.put(labels[i], decValues[i]);
			}
		}

		return prediction;
		
	}

	public static Map<Integer,Double> predictProbabilities(Model model, FeatureVector example){
		assertFittedModel(model);
		return predictProbabilities(model,createFeatureArray(example, model.getNrFeature()+1));
	}

	public static Map<Integer,Double> predictProbabilities(Model model, Feature[] example){
		assertFittedModel(model);
		if (!model.isProbabilityModel()) {
			throw new IllegalStateException("The model was not trained for predicting probabilities");
		}

		int[] labels = model.getLabels();
		double[] probs = new double[labels.length];
		Linear.predictProbability(model, example, probs);


		Map<Integer,Double> prediction = new HashMap<>();
		for (int i=0; i<probs.length; i++) {
			prediction.put(labels[i], probs[i]);
		}

		return prediction;
		
	}

	/* 
	 * =================================================
	 * 			SAVE / LOAD
	 * =================================================
	 */

	public static void saveToStream(Model model, OutputStream ostream) throws IOException, IllegalStateException {
		if (model == null)
			throw new IllegalStateException("Model not trained");
		try(
				Writer writer = new OutputStreamWriter(ostream);	
				){
			model.save(writer);
		}
	}

	public static Model loadFromStream(InputStream istream) 
			throws IOException, IllegalArgumentException {
		Model model=null;
		try(
				Reader inputReader = new InputStreamReader(istream)
				){
			try{
				model = Model.load(inputReader);
			} catch (IOException e){
				LOGGER.debug("could not parse liblinear model");
				throw new IOException("Could not parse the given file as a LibLinear model");
			} catch (RuntimeException e){
				LOGGER.debug("could not parse liblinear model");
				throw new IllegalArgumentException("Could not parse the given file as a LibLinear model");
			}
		}

		if (model == null)
			throw new IllegalArgumentException("Could not parse the given file as a LibLinear-model");

		return model;
	}

}

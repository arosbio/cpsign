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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.StringUtils;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.BooleanConfig;
import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.commons.config.EnumConfig;
import com.arosbio.commons.config.IntegerConfig;
import com.arosbio.commons.config.NumericConfig;
import com.arosbio.commons.mixins.HasID;
import com.arosbio.data.DataRecord;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.FeatureVector.Feature;
import com.arosbio.data.MissingDataException;
import com.google.common.collect.Range;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;

/**
 * Wrapper class for LIBSVM (https://www.csie.ntu.edu.tw/~cjlin/libsvm/).
 * 
 * @author staffan
 *
 */
public class LibSvm {

	private static final Logger LOGGER = LoggerFactory.getLogger(LibSvm.class);
	
	public static final double DEFAULT_NU = .5;
	public static final int DEFAULT_KERNEL_DEGREE = 3;
	public static final double DEFAULT_COEF0 = 0;
	public static final int DEFAULT_SVM_MEM_SIZE = 512; // In MB.
	public static final double DEFAULT_GAMMA = 0.002;
	public static final int DEFAULT_SVM_KERNEL_TYPE = svm_parameter.RBF;

	public static final List<String> SHRINKAGE_PARAM_NAMES = Arrays.asList("shrink","shrinking");
	public static final List<String> COEF0_PARAM_NAMES = Arrays.asList("coef0");
	public static final List<String> DEGREE_PARAM_NAMES = Arrays.asList("degree");
	public static final List<String> CACHE_SIZE_PARAM_NAMES = Arrays.asList("cache");
	public static final List<String> KERNEL_PARAM_NAMES = Arrays.asList("kernel");
	public static final List<String> NU_PARAM_NAMES = Arrays.asList("nu");
	public static final List<String> GAMMA_PARAM_NAMES = Arrays.asList("gamma");
	public static final List<String> SOLVER_TYPE_PARAM_NAME = Arrays.asList("solver");
	public static final List<String> PROBABILITY_PARAM_NAME = Arrays.asList("probability");
	
	public static final ConfigParameter CACHE_SIZE_CONFIG = new NumericConfig.Builder(CACHE_SIZE_PARAM_NAMES,DEFAULT_SVM_MEM_SIZE)
		.range(Range.atLeast(100d))
		.description("Cache memory size in MB")
		.build();

	public static final ConfigParameter KERNEL_CONFIG = new EnumConfig.Builder<>(KERNEL_PARAM_NAMES,EnumSet.allOf(KernelType.class),KernelType.RBF)
		.build();
	
	public static final ConfigParameter NU_CONFIG = new NumericConfig.Builder(NU_PARAM_NAMES,DEFAULT_NU)
		.range(Range.openClosed(0d, 1d))
		.description("The nu parameter in nu-SVC and nu-SVR, allowed values are (0..1]")
		.build();

	public static final ConfigParameter GAMMA_CONFIG = new NumericConfig.Builder(GAMMA_PARAM_NAMES,DEFAULT_GAMMA)
		.range(Range.atLeast(0d))
		.defaultGrid(Arrays.asList(0.001, 0.01, 0.1))
		.description("Gamma used in poly/rbf/sigmoid kernel functions")
		.build();

	public static ConfigParameter DEGREE_CONFIG = new IntegerConfig.Builder(DEGREE_PARAM_NAMES,DEFAULT_KERNEL_DEGREE)
		.range(Range.atLeast(1))
		.description("Degree used in poly kernel function").build();

	public static final ConfigParameter COEF0_CONFIG = new NumericConfig.Builder(COEF0_PARAM_NAMES,DEFAULT_COEF0)
		.range(Range.atLeast(0d))
		.description("Coef0 used in poly/sigmoid kernel functions")
		.build();

	public static final ConfigParameter SHRINK_CONFIG = new BooleanConfig.Builder(SHRINKAGE_PARAM_NAMES, false)
		.description("Whether to use the shrinking heuristics. Shrinking *can* improve runtime but not always")
		.build();


	/**
	 * Epsilon + Kernel and kernel-parameters + shrink/cache size
	 */
	public static final List<ConfigParameter> GENERAL_CONFIG_PARAMS = Arrays.asList(
			DefaultMLParameterSettings.EPSILON_CONFIG,
			KERNEL_CONFIG, 
			GAMMA_CONFIG, 
			DEGREE_CONFIG,
			COEF0_CONFIG, 
			SHRINK_CONFIG, 
			CACHE_SIZE_CONFIG);

	// Disable all output
	static {
		svm.svm_set_print_string_function(new libsvm.svm_print_interface(){
			@Override public void print(String s) {} // Disables svm output
		});
	}
	
	// Should never instantiate this class
	private LibSvm() {}


	public static svm_parameter getDefaultParams(SvmType type) {
		svm_parameter parameters = new svm_parameter();
		parameters.svm_type = type.id;
		parameters.C = DefaultMLParameterSettings.DEFAULT_C;
		parameters.cache_size = DEFAULT_SVM_MEM_SIZE;
		parameters.coef0 = DEFAULT_COEF0;
		parameters.degree = DEFAULT_KERNEL_DEGREE;
		parameters.eps = DefaultMLParameterSettings.DEFAULT_EPSILON;
		parameters.gamma = DEFAULT_GAMMA;
		parameters.kernel_type = DEFAULT_SVM_KERNEL_TYPE;
		parameters.nu = DEFAULT_NU;
		parameters.p = DefaultMLParameterSettings.DEFAULT_SVR_EPSILON;
		return parameters;
	}

	public enum KernelType implements HasID {

		LINEAR (svm_parameter.LINEAR),
		POLY (svm_parameter.POLY),
		RBF (svm_parameter.RBF),
		SIGMOID (svm_parameter.SIGMOID);

		public final int id;

		private KernelType(int id) {
			this.id=id;
		}

		public static KernelType forID(int id) {
			for (KernelType v : values()) {
				if (v.id == id)
					return v;
			}
			throw new IllegalArgumentException("No KernelType for id " + id);
		}
		
		public static KernelType forID(String id) {
			try {
				return forID(TypeUtils.asInt(id));
			} catch (NumberFormatException ne) {
				// Not an int
			}
			
			for (KernelType v : values()) {
				if (v.name().equalsIgnoreCase(id)) {
					return v;
				}
			}
			throw new IllegalArgumentException("No KernelType for id " + id);
		}
		
		public String toString() {
			return "("+id+ ") "+name();
		}
		
		public int getID() {
			return id;
		}

	}

	public enum SvmType implements HasID {

		C_SVC(svm_parameter.C_SVC),
		NU_SVC(svm_parameter.NU_SVC),
		ONE_CLASS(svm_parameter.ONE_CLASS),
		EPSILON_SVR(svm_parameter.EPSILON_SVR),
		NU_SVR(svm_parameter.NU_SVR);

		public final int id;

		private SvmType(int id) {
			this.id = id;
		}

		public static SvmType forID(int id) {
			for (SvmType v : values()) {
				if (v.id == id)
					return v;
			}
			throw new IllegalArgumentException("No SvmType for id " + id);
		}

		@Override
		public int getID() {
			return id;
		}

	}

	/* 
	 * =================================================
	 * 			CONSTRUCTORS
	 * =================================================
	 */

	/* 
	 * =================================================
	 * 			GETTERS / SETTERS
	 * =================================================
	 */


	public static Map<String,Object> toProperties(svm_parameter p){
		Map<String,Object> props = new LinkedHashMap<String,Object>();
		// General
		props.put(DefaultMLParameterSettings.COST_PARAM_NAMES.get(0), p.C);
		props.put(NU_PARAM_NAMES.get(0), p.nu);
		props.put(DefaultMLParameterSettings.EPSILON_PARAM_NAMES.get(0),p.eps);
		props.put(DefaultMLParameterSettings.SVR_EPSILON_PARAM_NAMES.get(0), p.p);

		// KERNEL
		props.put(KERNEL_PARAM_NAMES.get(0), p.kernel_type);
		props.put(GAMMA_PARAM_NAMES.get(0), p.gamma);
		props.put(DEGREE_PARAM_NAMES.get(0), p.degree);
		props.put(COEF0_PARAM_NAMES.get(0), p.coef0);
		
		
		props.put(SOLVER_TYPE_PARAM_NAME.get(0), p.svm_type);
		props.put(PROBABILITY_PARAM_NAME.get(0), p.probability);
		props.put(CACHE_SIZE_PARAM_NAMES.get(0), p.cache_size);
		props.put(SHRINKAGE_PARAM_NAMES.get(0), (p.shrinking==0? false : true));
		return props;
	}

	public static void setConfigParameters(svm_parameter base, Map<String,Object> params) throws IllegalArgumentException {

		for (Map.Entry<String, ? extends Object> p: params.entrySet()) {
			try {
				String key = p.getKey();
				if (CollectionUtils.containsIgnoreCase(DefaultMLParameterSettings.COST_PARAM_NAMES,key)) {
					double c = TypeUtils.asDouble(p.getValue());
					if (c < 0)
						throw new IllegalArgumentException("Parameter "+key +" cannot be negative, got: " + c);
					base.C = c;
				} else if (CollectionUtils.containsIgnoreCase(DefaultMLParameterSettings.EPSILON_PARAM_NAMES,key)) {
					double eps = TypeUtils.asDouble(p.getValue());
					if (eps < 0)
						throw new IllegalArgumentException("Parameter "+key+" cannot be negative, got: " + eps);
					base.eps = eps;
				} else if (CollectionUtils.containsIgnoreCase(DefaultMLParameterSettings.SVR_EPSILON_PARAM_NAMES,key)) {
					double eps = TypeUtils.asDouble(p.getValue());
					if (eps < 0)
						throw new IllegalArgumentException("Parameter "+key+" cannot be negative, got: " + eps);
					base.p = eps;
				} else if (CollectionUtils.containsIgnoreCase(NU_PARAM_NAMES, p.getKey())){
					double nu = TypeUtils.asDouble(p.getValue());
					if (nu < 0 || nu>1)
						throw new IllegalArgumentException("Parameter "+key+" must be in the range [0,1], got: " + nu);
					base.nu = nu;
				} else if (CollectionUtils.containsIgnoreCase(KERNEL_PARAM_NAMES, p.getKey())){
					if (p.getValue() instanceof KernelType)
						base.kernel_type = ((KernelType) p.getValue()).id;
					else
						base.kernel_type =  KernelType.forID(p.getValue().toString()).id;
				} else if (CollectionUtils.containsIgnoreCase(GAMMA_PARAM_NAMES, p.getKey())){
					base.gamma = TypeUtils.asDouble(p.getValue());
				} else if (CollectionUtils.containsIgnoreCase(DEGREE_PARAM_NAMES, p.getKey())){
					base.degree = TypeUtils.asInt(p.getValue());
				} else if (CollectionUtils.containsIgnoreCase(COEF0_PARAM_NAMES, p.getKey())){
					base.coef0 = TypeUtils.asDouble(p.getValue());
				} else if (CollectionUtils.containsIgnoreCase(CACHE_SIZE_PARAM_NAMES, p.getKey())){
					base.cache_size = TypeUtils.asDouble(p.getValue());
				} else if (CollectionUtils.containsIgnoreCase(SHRINKAGE_PARAM_NAMES, p.getKey())){
					if (p.getValue() instanceof Boolean) {
						base.shrinking = ((Boolean)p.getValue() ? 1 : 0);
					} else {
						try {
							base.shrinking = (TypeUtils.asBoolean(p.getValue())? 1 : 0);
						} catch (NumberFormatException e) {
							throw new IllegalArgumentException("Parameter '"+key +"' must be boolean (true/false), was: " + p.getValue());
						}
					}
				} 
				// Fall through on parameters that are not used
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid argument for parameter '" + p.getKey() + "': " + p.getValue());
			}
		}
	}

	public static int getNumSupportVectors(svm_model model) {
		if (model!=null)
			return model.l;
		return 0;
	}

	public static List<Integer> getLabels(svm_model model){
		if (model==null) {
			return new ArrayList<>();
		}
		try {
			List<Integer> labels = new ArrayList<>();
			for (int l: model.label) {
				labels.add(l);
			}
			return labels;
		} catch (NullPointerException e) {}
		return new ArrayList<>();
	}

	/* 
	 * =================================================
	 * 			TRAINING
	 * =================================================
	 */

	//	@Override
	public static svm_model train(svm_parameter params, List<DataRecord> trainingset, long seed) {

		//Create the train problem
		svm_problem trainProblem = createLibSvmTrainProblem(trainingset);
		LOGGER.debug("Finished setting up the LibSVM training problem");

		return train(params, trainProblem, seed);
	}

	public static svm_model train(svm_parameter params, svm_problem problem, long seed){
		LOGGER.debug("Training libsvm model...");
		//Train the svm_model
		svm.rand.setSeed(seed); // we want the same result always!

		svm_model model = svm.svm_train(problem, params);
		LOGGER.debug("Finished training the svm-model");
		return model;
	}

	/* 
	 * =================================================
	 * 			UTILS
	 * =================================================
	 */

	public static svm_problem createLibSvmTrainProblem(List<DataRecord> trainingset) {
		LOGGER.debug("creating libsvm problem with {} records",trainingset.size());
		//Set up training problem on proper training set
		svm_problem trainProblem = new svm_problem();
		trainProblem.l = trainingset.size();
		trainProblem.x = new svm_node[trainProblem.l][];
		trainProblem.y = new double[trainProblem.l];

		try {
			for (int ex = 0; ex < trainProblem.l; ex++){
				// Target value
				trainProblem.y[ex] = trainingset.get(ex).getLabel();

				// features
				trainProblem.x[ex] = createFeatureArray(trainingset.get(ex).getFeatures());

			}
		} catch (MissingDataException e) {
			LOGGER.debug("Failed setting up LibSvm problem due to missing data: ",e);
			throw new MissingDataException("Failed training LibSvm model due to missing data - please revise your pre-processing");
		}

		return trainProblem;
	}

	public static svm_problem cloneProblem(svm_problem problem) {
		svm_problem clone = new svm_problem();
		clone.l = problem.l;
		clone.x = problem.x.clone();
		clone.y = problem.y.clone();
		return clone;
	}

	public static svm_node[] createFeatureArray(FeatureVector feats) throws MissingDataException {
		svm_node[] nodes = new svm_node[feats.getNumExplicitFeatures()];

		int index = 0;
		List<Integer> missingDataIndices = new ArrayList<>();
		for (Feature f : feats) {
			if (!Double.isFinite(f.getValue())) {
				missingDataIndices.add(f.getIndex());
			}
			svm_node node = new svm_node();
			node.index = f.getIndex()+1; // Need to add one as features starts at 0, libsvm requires start at 1! 
			node.value = f.getValue();
			nodes[index] = node;
			index++;
		}
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

	private static void assertFittedModel(svm_model model) throws IllegalStateException {
		if (model == null)
			throw new IllegalStateException("Model not fitted");
	}

	//	@Override
	public static double predictValue(svm_model model, FeatureVector feature) 
			throws IllegalStateException {
		return predictValue(model,createFeatureArray(feature));
	}

	public static double predictValue(svm_model model, svm_node[] instance) 
			throws IllegalStateException {
		assertFittedModel(model);

		double pred =svm.svm_predict(model, instance);
		//		LOGGER.trace("pred="+pred);
		return pred;
	}

	public static int predictClass(svm_model model, FeatureVector example) 
			throws IllegalStateException {
		return predictClass(model, createFeatureArray(example));
	}

	public static int predictClass(svm_model model, svm_node[] instance) 
			throws IllegalStateException {
		assertFittedModel(model);

		double pred = svm.svm_predict(model, instance);

		return (int)pred;
	}




	//	@Override
	public static Map<Integer, Double> predictDistanceToHyperplane(svm_model model, FeatureVector example) 
			throws IllegalStateException {
		return predictDistanceToHyperplane(model, createFeatureArray(example));
	}

	public static Map<Integer, Double> predictDistanceToHyperplane(svm_model model, svm_node[] example) 
			throws IllegalStateException {
		assertFittedModel(model);

		//		if (model.label.length != 2)
		//			throw new IllegalStateException("Can only predict distance to hyperplane for binary classification");
		int numValues = model.nr_class*(model.nr_class-1)/2;
		double decValues[] = new double[numValues];
		svm.svm_predict_values(model, example, decValues);

		// Convert to the labels used
		Map<Integer,Double> prediction = new HashMap<>();
		if (model.nr_class == 2) {
			// Special treat binary classification - only give a single value
			prediction.put(model.label[0], decValues[0]);
			prediction.put(model.label[1], -1*decValues[0]);
		} else {
			// Here I'm doing a bit of my own stuff, is this correct??
			int decValueIndex = 0;
			for (int i=0; i<model.nr_class; i++) {
				int iLabel = model.label[i];

				for (int k=i+1; k<model.nr_class; k++) {
					int kLabel = model.label[k];
					prediction.put(iLabel, prediction.getOrDefault(iLabel, 0d) + decValues[decValueIndex]);
					prediction.put(kLabel, prediction.getOrDefault(kLabel, 0d) - decValues[decValueIndex]);
					decValueIndex++;
				}
			}
		}

		return prediction;
	}

	public static Map<Integer,Double> predictProbabilities(svm_model model, FeatureVector feature) 
			throws IllegalStateException {
		return predictProbabilities(model,createFeatureArray(feature));
	}

	public static Map<Integer,Double> predictProbabilities(svm_model model, svm_node[] instance) 
			throws IllegalStateException {
		assertFittedModel(model);
		if (model.probA == null)
			throw new IllegalStateException("Model not fitted for probability prediction");

		// for probabilities - LibSVM returns what we expect! one prediction per label
		double probabilityValues[] = new double[model.nr_class];
		double pred = svm.svm_predict_probability(model, instance, probabilityValues);

		Map<Integer,Double> prediction = new HashMap<>();
		for (int i=0; i<probabilityValues.length; i++) {
			prediction.put(model.label[i], probabilityValues[i]);
		}

		LOGGER.trace("pred="+pred+", probabilities="+prediction);
		return prediction;
	}

	//	private static String toString(double[] dec) {
	//		String s = "[";
	//		for (double d: dec) {
	//			s+=d+", ";
	//		}
	//		return s.substring(0, s.length()-2)+']';
	//	}

	/* 
	 * =================================================
	 * 			SAVE / LOAD
	 * =================================================
	 */

	//	@Override
	public static void saveToStream(svm_model model, OutputStream ostream) throws IOException {
		// Copy from libsvm repo - it only writes output to a file, given by a String.. 
		try (DataOutputStream fp = new DataOutputStream(new BufferedOutputStream(ostream));){

			svm_parameter param = model.param;

			fp.writeBytes("svm_type "+svm_type_table[param.svm_type]+"\n");
			fp.writeBytes("kernel_type "+kernel_type_table[param.kernel_type]+"\n");

			if(param.kernel_type == svm_parameter.POLY)
				fp.writeBytes("degree "+param.degree+"\n");

			if(param.kernel_type == svm_parameter.POLY ||
					param.kernel_type == svm_parameter.RBF ||
					param.kernel_type == svm_parameter.SIGMOID)
				fp.writeBytes("gamma "+param.gamma+"\n");

			if(param.kernel_type == svm_parameter.POLY ||
					param.kernel_type == svm_parameter.SIGMOID)
				fp.writeBytes("coef0 "+param.coef0+"\n");

			int nr_class = model.nr_class;
			int l = model.l;
			fp.writeBytes("nr_class "+nr_class+"\n");
			fp.writeBytes("total_sv "+l+"\n");

			{
				fp.writeBytes("rho");
				for(int i=0;i<nr_class*(nr_class-1)/2;i++)
					fp.writeBytes(" "+model.rho[i]);
				fp.writeBytes("\n");
			}

			if(model.label != null)
			{
				fp.writeBytes("label");
				for(int i=0;i<nr_class;i++)
					fp.writeBytes(" "+model.label[i]);
				fp.writeBytes("\n");
			}

			if(model.probA != null) // regression has probA only
			{
				fp.writeBytes("probA");
				for(int i=0;i<nr_class*(nr_class-1)/2;i++)
					fp.writeBytes(" "+model.probA[i]);
				fp.writeBytes("\n");
			}
			if(model.probB != null) 
			{
				fp.writeBytes("probB");
				for(int i=0;i<nr_class*(nr_class-1)/2;i++)
					fp.writeBytes(" "+model.probB[i]);
				fp.writeBytes("\n");
			}

			if(model.nSV != null)
			{
				fp.writeBytes("nr_sv");
				for(int i=0;i<nr_class;i++)
					fp.writeBytes(" "+model.nSV[i]);
				fp.writeBytes("\n");
			}

			fp.writeBytes("SV\n");
			double[][] sv_coef = model.sv_coef;
			svm_node[][] SV = model.SV;

			for(int i=0;i<l;i++)
			{
				for(int j=0;j<nr_class-1;j++)
					fp.writeBytes(sv_coef[j][i]+" ");

				svm_node[] p = SV[i];
				if(param.kernel_type == svm_parameter.PRECOMPUTED)
					fp.writeBytes("0:"+(int)(p[0].value));
				else	
					for(int j=0;j<p.length;j++)
						fp.writeBytes(p[j].index+":"+p[j].value+" ");
				fp.writeBytes("\n");
			}
		}

	}

	public static svm_model loadFromStream(InputStream istream) throws IOException{
		svm_model model = null;
		try(
				BufferedReader buffReader = new BufferedReader(new InputStreamReader(istream));
				) {
			try{
				model = svm.svm_load_model(buffReader);
			} catch(IOException e){
				if(e.getMessage().contains("failed to read model"))
					throw new IllegalArgumentException("Could not parse the given file as a LibSVM-model");
			}
		}

		if (model == null)
			throw new IllegalArgumentException("Could not parse the given file as a LibSvm-model");
		return model;
	}

	/**
	 * Copy-paste from LibSVM v 3.21
	 */
	private static final String svm_type_table[] =
		{
				"c_svc","nu_svc","one_class","epsilon_svr","nu_svr",
		};
	/**
	 * Copy-paste from LibSVM v 3.21
	 */
	private static final String kernel_type_table[]=
		{
				"linear","polynomial","rbf","sigmoid","precomputed"
		};


}

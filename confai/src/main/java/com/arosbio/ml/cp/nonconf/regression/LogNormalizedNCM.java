/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp.nonconf.regression;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.Version;
import com.arosbio.commons.config.NumericConfig;
import com.arosbio.data.DataRecord;
import com.arosbio.data.FeatureVector;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.DataSink;
import com.arosbio.io.DataSource;
import com.arosbio.ml.algorithms.MLAlgorithm;
import com.arosbio.ml.algorithms.Regressor;
import com.arosbio.ml.algorithms.impl.AlgorithmUtils;
import com.arosbio.ml.cp.nonconf.utils.NCMUtils;
import com.arosbio.ml.io.MetaFileUtils;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.google.common.collect.Range;

/**
 * <h2>Regression</h2>
 * <p>
 * Computes nonconformity as:<br> 
 * nonconf = |<i>y</i> - <i>y_hat</i>|/(exp(<i>μ_hat</i>)+<i>β</i>) , <i>μ_hat</i> = predicted error by error model, <i>β</i> &gt;= 0 "smoothing factor"
 * <p>
 * Error model is trained by:<br>
 * <i>μ</i> = ln (|<i>y</i> - <i>y_hat</i>|)
 * 
 * @author staffan
 */
public class LogNormalizedNCM implements NCMRegression {

	private static final Version VERSION = new Version(1, 0, 0);
	private final static Logger LOGGER = LoggerFactory.getLogger(LogNormalizedNCM.class);
	private final static String BETA_PROPERTY_KEY = "ncm_beta"; 
	private final static String INVALID_BETA_ERR_MSG = "Beta value must be in range [0..+∞)";

	public final static String IDENTIFIER="LogNormalized";
	public final static int ID = 1;
	public final static double DEFAULT_BETA_VALUE = 0.01;
	public final static String DESCRIPTION = "Nonconformity measure for regression. Residuals found for the calibration "
			+ "set is used for training an error-model that will normalize the nonconformity scores for new objects. "
			+ "Thus making 'easier' objects get smaller prediction intervals and more difficult to get larger ones. "
			+ "The error-model is trained on the log of the residuals in contrast with the " + NormalizedNCM.IDENTIFIER + " NCM. "
			+ "The " + CONFIG_BETA_PARAM_NAMES.get(0) + " parameter 'smoothes' the importance of the normalization and a beta >0 "
			+ "remove the possibility of generating infinitly large scores due to division by 0.";


	private double beta=DEFAULT_BETA_VALUE;
	private Regressor model;
	private Regressor errorModel;

	////////////////////////////////////
	///////// CONSTRUCTORS
	////////////////////////////////////

	public LogNormalizedNCM() {
	}

	public LogNormalizedNCM(Regressor model) throws IllegalArgumentException {
		this(model, model.clone(), DEFAULT_BETA_VALUE);
	}
	
	public LogNormalizedNCM(Regressor model, double beta) throws IllegalArgumentException {
		this(model, model.clone(), beta);
	}

	public LogNormalizedNCM(Regressor model, Regressor errorModel, double beta) throws IllegalArgumentException{
		if (beta <0)
			throw new IllegalArgumentException(INVALID_BETA_ERR_MSG);
		this.beta = beta;
		Objects.requireNonNull(model, "Regressor model cannot be null");
		this.model = model;
		if (errorModel == null){
			LOGGER.debug("No explicit error model given, using the same settings as the target model");
			this.errorModel = model.clone();
		} else {
			this.errorModel = errorModel;
		}
	}

	public LogNormalizedNCM clone() {
		LogNormalizedNCM clone = new LogNormalizedNCM();
		clone.beta = beta;
		if (model != null )
			clone.model = model.clone();
		if (errorModel != null)
			clone.errorModel = errorModel.clone();
		return clone;
	}

	////////////////////////////////////
	///////// GETTERS AND SETTERS
	////////////////////////////////////

	public double getBeta() {
		return beta;
	}

	public void setBeta(double beta) {
		if (beta <0)
			throw new IllegalArgumentException(INVALID_BETA_ERR_MSG);
		this.beta = beta;
	}

	@Override
	public boolean requiresErrorModel() {
		return true;
	}

	@Override
	public boolean isFitted() {
		return model!=null && model.isFitted() && errorModel!=null && errorModel.isFitted();
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String,Object> props = new HashMap<>(model.getProperties());
		props.putAll(NCMUtils.addErrorModelPrefix(errorModel.getProperties()));
		
		props.put(PropertyNameSettings.NCM_NAME, IDENTIFIER);
		props.put(PropertyNameSettings.NCM_ID, ID);
		props.put(PropertyNameSettings.NCM_VERSION, VERSION.toString());

		props.put(PropertyNameSettings.NCM_SCORING_MODEL_ID, model.getID());
		props.put(PropertyNameSettings.NCM_SCORING_MODEL_NAME, model.getName());
		props.put(PropertyNameSettings.NCM_ERROR_MODEL_ID, errorModel.getID());
		props.put(PropertyNameSettings.NCM_ERROR_MODEL_NAME, errorModel.getName());

		props.put(BETA_PROPERTY_KEY, beta);
		return props;
	}

	@Override
	public MLAlgorithm getErrorModel() {
		return errorModel;
	}

	@Override
	public void setErrorModel(MLAlgorithm model) throws IllegalArgumentException {
		if (model instanceof Regressor)
			this.errorModel = (Regressor) model;
		else
			throw new IllegalArgumentException("NCM error model must be of type Regressor");
	}

	@Override
	public Regressor getModel() {
		return model;
	}

	@Override
	public void setModel(Regressor model) {
		this.model = model;
	}

	@Override
	public String getName() {
		return IDENTIFIER;
	}

	@Override
	public int getID() {
		return ID;
	}
	
	public String toString() {
		return IDENTIFIER;
	}
	
	public final String getDescription() {
		return DESCRIPTION;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		List<ConfigParameter> params =  new ArrayList<>();
		// Beta parameter
		params.add(new NumericConfig.Builder(CONFIG_BETA_PARAM_NAMES,DEFAULT_BETA_VALUE)
				.range(Range.closed(0d, 10d))
				.description("Smoothing term used for regulating the impact of the error model")
				.build());
		
		if (model==null || errorModel==null)
			return params;
		
		//Scoring
		params.addAll(model.getConfigParameters());

		// Error
		params.addAll(NCMUtils.addErrorModelPrefix(errorModel.getConfigParameters()));

		return params;
	}

	@Override
	public void setConfigParameters(Map<String,Object> params) throws IllegalArgumentException {
		// Scoring params
		if (model!=null)
			model.setConfigParameters(params);

		// Error params
		if (errorModel!=null)
			errorModel.setConfigParameters(NCMUtils.stripPrefixAndGetErrorModelParams(params));

		// BETA
		for (Map.Entry<String, Object> p : params.entrySet()) {
			if (CollectionUtils.containsIgnoreCase(CONFIG_BETA_PARAM_NAMES, p.getKey())) {
				try {
					setBeta(TypeUtils.asDouble(p.getValue()));
				} catch (Exception e) {
					throw new IllegalArgumentException("Invalid value for parameter "+ p.getKey() + ": value must be in range [0..+∞) but was '"+ p.getValue()+'\''); 
				}
			}
		}

	}

	////////////////////////////////////
	///////// TRAIN / CALCULATE
	////////////////////////////////////

	@Override
	public void trainNCM(List<DataRecord> data) {
		model.train(data);
		LOGGER.debug("Trained scoring model");

		List<DataRecord> errorRecs = new ArrayList<>();

		for (DataRecord rec : data) {
			double y_hat = model.predictValue(rec.getFeatures());
			double abs_residual = Math.abs(rec.getLabel()-y_hat);
			errorRecs.add(new DataRecord(
					Math.log( 
							Math.max(abs_residual, Double.MIN_VALUE) // we don't want to take log(0) 
							), 
					rec.getFeatures()));
		}

		errorModel.train(errorRecs);
		LOGGER.debug("Trained error model");

	}
	
	@Override
	public double calcNCS(DataRecord example) throws IllegalStateException {
		double y_hat = model.predictValue(example.getFeatures());
		return Math.abs(example.getLabel()-y_hat)/
				calcIntervalScaling(example.getFeatures()); // Avoid division by 0
	}

	@Override
	public double predictMidpoint(FeatureVector example) throws IllegalStateException {
		return model.predictValue(example);
	}

	@Override
	public double calcIntervalScaling(FeatureVector example) throws IllegalStateException {
		double e_hat = errorModel.predictValue(example);
		return Math.max(Math.exp(e_hat) + beta, Double.MIN_VALUE);
	}

	////////////////////////////////////
	///////// SAVE / LOAD
	////////////////////////////////////

	@Override
	public void saveToDataSink(DataSink sink, String path, EncryptionSpecification encryptSpec)
			throws IOException, InvalidKeyException, IllegalStateException {

		String scoreSavePath = null, scoreParamsSavePath = null;
		if (model != null) {

			if (model.isFitted()) {
				// For TCP - not saving an actual model - but only the parameters
				scoreSavePath = path + NCMUtils.SCORING_MODEL_FILE;
				AlgorithmUtils.saveModelToStream(sink, scoreSavePath, model, encryptSpec);
			}
		}

		String errorSavePath = null, errorParamsSavePath = null;
		if (model != null) {

			if (model.isFitted()) {
				errorSavePath = path + NCMUtils.ERROR_MODEL_FILE;
				// For TCP - not saving an actual model - but only the parameters
				AlgorithmUtils.saveModelToStream(sink, errorSavePath, errorModel, encryptSpec);
			}
		}


		try (OutputStream metaStream = sink.getOutputStream(path+NCMUtils.NCM_PARAMS_META_FILE);){
			Map<String,Object> props = getProperties();

			props.put(PropertyNameSettings.NCM_SCORING_MODEL_LOCATION, scoreSavePath);
			props.put(PropertyNameSettings.NCM_SCORING_PARAMETERS_LOCATION, scoreParamsSavePath);
			props.put(PropertyNameSettings.NCM_ERROR_MODEL_LOCATION, errorSavePath);
			props.put(PropertyNameSettings.NCM_ERROR_PARAMETERS_LOCATION, errorParamsSavePath);
			props.put(PropertyNameSettings.IS_ENCRYPTED, (encryptSpec != null));

			MetaFileUtils.writePropertiesToStream(metaStream, props);
		}
		LOGGER.debug("Saved NCM meta parameters, loc="+path+NCMUtils.NCM_PARAMS_META_FILE);
	}

	@Override
	public void loadFromDataSource(DataSource src, String path, EncryptionSpecification encryptSpec)
			throws IOException, IllegalArgumentException, InvalidKeyException {
		if (! src.hasEntry(path+NCMUtils.NCM_PARAMS_META_FILE))
			throw new IOException("No NCM saved at location=" + path);

		// Check that NCM matches
		NCMUtils.verifyNCMmatches(this, src, path+NCMUtils.NCM_PARAMS_META_FILE);

		Map<String,Object> params = null;
		try (InputStream istream = src.getInputStream(path + NCMUtils.NCM_PARAMS_META_FILE)){
			params = MetaFileUtils.readPropertiesFromStream(istream);
		}

		// Load beta value
		if (!params.containsKey(BETA_PROPERTY_KEY)) {
			throw new IOException("Could not retreive NCM properties correctly");
		}

		try {
			beta = TypeUtils.asDouble(params.get(BETA_PROPERTY_KEY));
			LOGGER.debug("Loaded beta="+beta+" from meta-file");
		} catch(NumberFormatException e) {
			throw new IOException("Could not load beta value properly from meta-property file");
		}

		if (!(boolean)params.get(PropertyNameSettings.IS_ENCRYPTED)) {
			encryptSpec = null; // set it to null
		}

		// SCORING MODEL
		MLAlgorithm scoreModel =  FuzzyServiceLoader.load(MLAlgorithm.class, params.get(PropertyNameSettings.NCM_SCORING_MODEL_ID).toString());

		if (scoreModel instanceof Regressor) {
			model = (Regressor) scoreModel;
		} else {
			LOGGER.debug("ML model was not of correct type for NCM of type " + IDENTIFIER);
			throw new IllegalArgumentException("ML scoring model was not of the correct algorithm type for NCM");
		}

		if (params.get(PropertyNameSettings.NCM_SCORING_MODEL_LOCATION) != null) {
			AlgorithmUtils.loadAlgorithm(src, params.get(PropertyNameSettings.NCM_SCORING_MODEL_LOCATION).toString(), model, encryptSpec);
			LOGGER.debug("loaded scoring model");
		}

//		if (params.get(PropertyFileSettings.NCM_SCORING_PARAMETERS_LOCATION) != null) {
//			AlgorithmUtils.loadAlgorithmParams(src, params.get(PropertyFileSettings.NCM_SCORING_PARAMETERS_LOCATION).toString(), model.getParameters());
//			LOGGER.debug("loaded scoring model parameters");
//		}

		// ERROR MODEL
		MLAlgorithm errorModelTmp =  FuzzyServiceLoader.load(MLAlgorithm.class, params.get(PropertyNameSettings.NCM_ERROR_MODEL_ID).toString());

		if (errorModelTmp instanceof Regressor) {
			errorModel = (Regressor) errorModelTmp;
		} else {
			LOGGER.debug("ML model was not of correct type for NCM of type " + IDENTIFIER);
			throw new IllegalArgumentException("ML error model was not of the correct algorithm type for NCM");
		}

		if (params.get(PropertyNameSettings.NCM_ERROR_MODEL_LOCATION) != null) {
			AlgorithmUtils.loadAlgorithm(src, params.get(PropertyNameSettings.NCM_ERROR_MODEL_LOCATION).toString(), errorModel, encryptSpec);
			LOGGER.debug("loaded error model");
		}

//		if (params.get(PropertyFileSettings.NCM_ERROR_PARAMETERS_LOCATION) != null) {
//			AlgorithmUtils.loadAlgorithmParams(src, params.get(PropertyFileSettings.NCM_ERROR_PARAMETERS_LOCATION).toString(), errorModel.getParameters());
//			LOGGER.debug("loaded error model parameters");
//		}
	}

}
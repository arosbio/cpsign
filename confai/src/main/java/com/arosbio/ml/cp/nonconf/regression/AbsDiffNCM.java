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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.commons.Version;
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

/**
 * <h2>Regression</h2>
 * <p>
 * Computes nonconformity as:<br> 
 * nonconf = |<i>y</i> - <i>ŷ</i>| 
 * <p>
 * This means that there is no need for any error model to be generated 
 * 
 * @author staffan
 */
public class AbsDiffNCM implements NCMRegression {

	private static final Version VERSION = new Version(1,0,0);
	private static final Logger LOGGER = LoggerFactory.getLogger(AbsDiffNCM.class);
	public static final String IDENTIFIER="AbsDiff";
	public static final int ID = 3;
	public final static String DESCRIPTION = "Nonconformity measure for regression. Residuals found for the calibration "
			+ "set is directly used for assigning prediction widths to new test objects. No normalization is performed "
			+ "for objects and all predictions for a given confidence will be assigned the same width.";


	private Regressor model;

	////////////////////////////////////
	///////// CONSTRUCTORS
	////////////////////////////////////
	public AbsDiffNCM() {
	}

	public AbsDiffNCM(Regressor model) {
		this.model = model;
	}

	public AbsDiffNCM clone() {
		if (model != null)
			return new AbsDiffNCM(model.clone());
		return new AbsDiffNCM();
	}

	////////////////////////////////////
	///////// GETTERS AND SETTERS
	////////////////////////////////////

	@Override
	public boolean requiresErrorModel() {
		return false;
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

	@Override
	public Version getVersion() {
		return VERSION;
	}
	
	@Override
	public String getDescription() {
		return DESCRIPTION;
	}

	@Override
	public boolean isFitted() {
		return model!= null && model.isFitted();
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String,Object> props = new HashMap<>(model.getProperties());
		
		props.put(PropertyNameSettings.NCM_NAME, IDENTIFIER);
		props.put(PropertyNameSettings.NCM_ID, ID);
		props.put(PropertyNameSettings.NCM_VERSION, VERSION.toString());

		props.put(PropertyNameSettings.NCM_SCORING_MODEL_ID, model.getID());
		props.put(PropertyNameSettings.NCM_SCORING_MODEL_NAME, model.getName());
		return props;
	}

	@Override
	public MLAlgorithm getErrorModel() {
		return null;
	}

	@Override
	public void setErrorModel(MLAlgorithm model) throws IllegalArgumentException {
		throw new IllegalArgumentException("Absolute difference NCM does not use an error model");
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
	public List<ConfigParameter> getConfigParameters() {
		if (model!=null)
			return  model.getConfigParameters();
		return new ArrayList<>();
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalArgumentException {
		if (model!=null)
			model.setConfigParameters(params);
	}

	////////////////////////////////////
	///////// TRAIN / CALCULATE
	////////////////////////////////////

	@Override
	public void trainNCM(List<DataRecord> data) throws IllegalArgumentException {
		model.train(data);
	}

	@Override
	public double calcNCS(DataRecord example) throws IllegalStateException {
		double y_hat = model.predictValue(example.getFeatures());
		return Math.abs(example.getLabel() - y_hat);
	}

	@Override
	public double predictMidpoint(FeatureVector example) throws IllegalStateException {
		return model.predictValue(example);
	}

	@Override
	public double calcIntervalScaling(FeatureVector example) throws IllegalStateException {
		return 1d;
	}

	////////////////////////////////////
	///////// SAVE / LOAD
	////////////////////////////////////

	@Override
	public void saveToDataSink(DataSink sink, String path, EncryptionSpecification encryptSpec)
			throws IOException, InvalidKeyException, IllegalStateException {

		String svmSavePath = null, svmParamsSavePath = null;
		if (model != null) {

			if (model.isFitted()) {
				// For TCP - not saving an actual model - but only the parameters
				svmSavePath = path + NCMUtils.SCORING_MODEL_FILE;
				AlgorithmUtils.saveModelToStream(sink, svmSavePath, model, encryptSpec);
			}

		}

		try (OutputStream metaStream = sink.getOutputStream(path+NCMUtils.NCM_PARAMS_META_FILE);){
			Map<String,Object> props = getProperties();

			props.put(PropertyNameSettings.NCM_SCORING_MODEL_LOCATION, svmSavePath);
			props.put(PropertyNameSettings.NCM_SCORING_PARAMETERS_LOCATION, svmParamsSavePath);
			props.put(PropertyNameSettings.IS_ENCRYPTED, (encryptSpec != null));

			MetaFileUtils.writePropertiesToStream(metaStream, props);
		}
		LOGGER.debug("Saved NCM meta parameters, loc={}{}",path,NCMUtils.NCM_PARAMS_META_FILE);
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

		if (!(boolean)params.get(PropertyNameSettings.IS_ENCRYPTED)) {
			encryptSpec = null; // set it to null
		}

		MLAlgorithm scoreModel =  FuzzyServiceLoader.load(MLAlgorithm.class, params.get(PropertyNameSettings.NCM_SCORING_MODEL_ID).toString());

		if (scoreModel instanceof Regressor) {
			model = (Regressor) scoreModel;
		} else {
			LOGGER.debug("ML model was not of correct type for NCM of type {}", IDENTIFIER);
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
	}

}
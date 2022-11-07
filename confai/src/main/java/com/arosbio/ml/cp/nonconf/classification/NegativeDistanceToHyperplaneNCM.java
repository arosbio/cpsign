/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp.nonconf.classification;

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
import com.arosbio.ml.algorithms.Classifier;
import com.arosbio.ml.algorithms.MLAlgorithm;
import com.arosbio.ml.algorithms.impl.AlgorithmUtils;
import com.arosbio.ml.algorithms.svm.SVC;
import com.arosbio.ml.cp.nonconf.utils.NCMUtils;
import com.arosbio.ml.io.MetaFileUtils;
import com.arosbio.ml.io.impl.PropertyNameSettings;

public class NegativeDistanceToHyperplaneNCM implements NCMMondrianClassification {

	private static final Logger LOGGER = LoggerFactory.getLogger(NegativeDistanceToHyperplaneNCM.class);
	private static final Version VERSION = new Version(1,0,0);
	
	public final static String IDENTIFIER="NegativeDistanceToHyperplane";
	public final static int ID = 11;
	public final static String DESCRIPTION = "Nonconformity score for classification, requing an SVC algorithm, using the negative distance to the decision hyperplane.";

	private SVC svm;


	////////////////////////////////////
	///////// CONSTRUCTORS
	////////////////////////////////////

	public NegativeDistanceToHyperplaneNCM() {}

	public NegativeDistanceToHyperplaneNCM(SVC svm) {
		this.svm = svm;
	}

	@Override
	public NegativeDistanceToHyperplaneNCM clone() {
		NegativeDistanceToHyperplaneNCM clone = new NegativeDistanceToHyperplaneNCM();
		if (svm != null)
			clone.svm = svm.clone();
		return clone;
	}

	////////////////////////////////////
	///////// GETTERS AND SETTERS
	////////////////////////////////////


	@Override
	public SVC getModel() {
		return svm;
	}

	@Override
	public void setModel(Classifier svm) throws IllegalArgumentException {
		if (svm instanceof SVC)
			this.svm = (SVC)svm;
		else
			throw new IllegalArgumentException("NegativeDistanceToHyperplane NCM requires a SVC model");
	}

	@Override
	public boolean isFitted() {
		return svm != null && svm.isFitted();
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String,Object> props = new HashMap<>(svm.getProperties());
		props.put(PropertyNameSettings.NCM_NAME, IDENTIFIER);
		props.put(PropertyNameSettings.NCM_ID, ID);
		props.put(PropertyNameSettings.NCM_VERSION, VERSION.toString());

		props.put(PropertyNameSettings.NCM_SCORING_MODEL_ID, svm.getID());
		props.put(PropertyNameSettings.NCM_SCORING_MODEL_NAME, svm.getName());
		return props;
	}

	@Override
	public String getName() {
		return IDENTIFIER;
	}

	@Override
	public int getID() {
		return ID;
	}
	
	public boolean hasDescription() {
		return true;
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
		if (svm!=null)
			return svm.getConfigParameters();
		return new ArrayList<>();
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalArgumentException {
		if (svm!=null)
			svm.setConfigParameters(params);
	}
	
	public String toString() {
		return IDENTIFIER;
	}

	////////////////////////////////////
	///////// TRAIN / CALCULATE
	////////////////////////////////////
	@Override
	public void trainNCM(List<DataRecord> data) throws IllegalArgumentException {
		svm.train(data);
	}

	@Override
	public Map<Integer,Double> calculateNCS(FeatureVector example) throws IllegalStateException {
		if (! isFitted())
			throw new IllegalStateException("NCM not trained");
		// Use the negative signed distance to the hyperplane - longer => more nonconforming 
		Map<Integer, Double> distances = svm.predictDistanceToHyperplane(example);
		Map<Integer, Double> ncs = new HashMap<>();
		for (Map.Entry<Integer, Double> ent: distances.entrySet()) {
			ncs.put(ent.getKey(), -1*ent.getValue());
		}
		return ncs; 
	}

	////////////////////////////////////
	///////// SAVE / LOAD
	////////////////////////////////////

	@Override
	public void saveToDataSink(DataSink sink, String path, EncryptionSpecification encryptSpec)
			throws IOException, InvalidKeyException, IllegalStateException {

		String svmSavePath = null, svmParamsSavePath = null;
		if (svm != null) {
			if (svm.isFitted()) {
				// For TCP - not saving an actual model - but only the parameters
				svmSavePath = path + NCMUtils.SCORING_MODEL_FILE;
				AlgorithmUtils.saveModelToStream(sink, svmSavePath, svm, encryptSpec);
			}

//			if (svm.getParameters() != null) {
//				svmParamsSavePath = path + NCMUtils.SCORING_MODEL_FILE + NCMUtils.MODEL_PARAMS_FILE_ENDING;
//				AlgorithmUtils.saveModelParamsToStream(sink, svmParamsSavePath, svm.getParameters());
//			}
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

		MLAlgorithm scoreModel = FuzzyServiceLoader.load(MLAlgorithm.class, params.get(PropertyNameSettings.NCM_SCORING_MODEL_ID).toString());

		if (scoreModel instanceof SVC) {
			svm = (SVC) scoreModel;
		} else {
			LOGGER.debug("ML model was not of correct type for NCM of type {}", IDENTIFIER);
			throw new IllegalArgumentException("ML scoring model was not of the correct algorithm type for NCM");
		}
		
		if (params.get(PropertyNameSettings.NCM_SCORING_MODEL_LOCATION) != null) {
			AlgorithmUtils.loadAlgorithm(src, params.get(PropertyNameSettings.NCM_SCORING_MODEL_LOCATION).toString(), svm, encryptSpec);
			LOGGER.debug("loaded scoring model");
		}

//		if (params.get(PropertyFileSettings.NCM_SCORING_PARAMETERS_LOCATION) != null) {
//			AlgorithmUtils.loadAlgorithmParams(src, params.get(PropertyFileSettings.NCM_SCORING_PARAMETERS_LOCATION).toString(), svm.getParameters());
//			LOGGER.debug("loaded scoring model parameters");
//		}
		
	}
}

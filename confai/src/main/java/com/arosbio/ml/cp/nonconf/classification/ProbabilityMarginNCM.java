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
import java.util.Collections;
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
import com.arosbio.ml.algorithms.PseudoProbabilisticClassifier;
import com.arosbio.ml.algorithms.impl.AlgorithmUtils;
import com.arosbio.ml.cp.nonconf.utils.NCMUtils;
import com.arosbio.ml.io.MetaFileUtils;
import com.arosbio.ml.io.impl.PropertyNameSettings;

/**
 * Uses the Margin of probabilities, i.e. nonconformity scores are calculated as the probability 
 * for the class, i, minus the largest probability (while excluding the the current class, i).
 * In binary problems this is equivalent with the {@link InverseProbabilityNCM}. 
 * 
 * @author staffan
 *
 */
public class ProbabilityMarginNCM implements NCMMondrianClassification {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProbabilityMarginNCM.class);
	private static final Version VERSION = new Version(1,0,0);
	public final static String IDENTIFIER="ProbabilityMargin";
	public final static int ID = 14;
	public final static String DESCRIPTION = "Nonconformity measure for classification. This NCM requires a scoring algorithm "
			+ "that can output probability-estimates. Uses the margin between the probability of the class compared to the "
			+ "highest probability (without considering the class itself). For binary problems it is equivalent to the "+ InverseProbabilityNCM.IDENTIFIER + " NCM.";


	private PseudoProbabilisticClassifier classifier;

	////////////////////////////////////
	///////// CONSTRUCTORS
	////////////////////////////////////

	public ProbabilityMarginNCM() {}

	public ProbabilityMarginNCM(PseudoProbabilisticClassifier classifier) {
		this.classifier = classifier;
	}

	@Override
	public ProbabilityMarginNCM clone() {
		if (classifier != null)
			return new ProbabilityMarginNCM(classifier.clone());
		return new ProbabilityMarginNCM();
	}

	@Override
	public boolean isFitted() {
		return classifier != null && classifier.isFitted();
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
	public String getDescription() {
		return DESCRIPTION;
	}
	
	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String,Object> props = new HashMap<>(classifier.getProperties());
		props.put(PropertyNameSettings.NCM_NAME, IDENTIFIER);
		props.put(PropertyNameSettings.NCM_ID, ID);
		props.put(PropertyNameSettings.NCM_VERSION, VERSION.toString());

		props.put(PropertyNameSettings.NCM_SCORING_MODEL_ID, classifier.getID());
		props.put(PropertyNameSettings.NCM_SCORING_MODEL_NAME, classifier.getName());
		return props;
	}

	@Override
	public PseudoProbabilisticClassifier getModel() {
		return classifier;
	}

	@Override
	public void setModel(Classifier probabilisticModel) throws IllegalArgumentException {
		if (probabilisticModel instanceof PseudoProbabilisticClassifier)
			this.classifier = (PseudoProbabilisticClassifier) probabilisticModel;
		else
			throw new IllegalArgumentException("Probability margin NCM requires a probablistic model");
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		if (classifier!=null)
			return classifier.getConfigParameters();
		return new ArrayList<>();
	}

	@Override
	public void setConfigParameters(Map<String,Object> params) throws IllegalArgumentException {
		if (classifier!=null)
			classifier.setConfigParameters(params);
	}

	////////////////////////////////////
	///////// TRAIN / CALCULATE
	////////////////////////////////////

	@Override
	public void trainNCM(List<DataRecord> data) throws IllegalArgumentException {
		classifier.train(data);
		LOGGER.debug("Finished training NCM underlying Probability model");
	}


	@Override
	public Map<Integer, Double> calculateNCS(FeatureVector example) 
			throws IllegalStateException {
		if (! isFitted())
			throw new IllegalStateException("NCM not trained");
 
		Map<Integer,Double> ncsScores = new HashMap<>();
		
		Map<Integer, Double> probs = classifier.predictProbabilities(example);
		for (int label: probs.keySet()) {
			
			Map<Integer, Double> probsCpy = new HashMap<>(probs);
			double classProb = probsCpy.remove(label);
			double highestOtherProb = Collections.max(probsCpy.values());
			
			double ncs = 0.5 - (classProb - highestOtherProb)/2;
			ncsScores.put(label, ncs);
		}

		return ncsScores;
	}

	////////////////////////////////////
	///////// SAVE / LOAD
	////////////////////////////////////

	@Override
	public void saveToDataSink(DataSink sink, String path, EncryptionSpecification encryptSpec)
			throws IOException, InvalidKeyException, IllegalStateException {

		String svmSavePath = null, svmParamsSavePath = null;
		if (classifier != null) {

			if (classifier.isFitted()) {
				svmSavePath = path + NCMUtils.SCORING_MODEL_FILE;
				// For TCP - not saving an actual model - but only the parameters
				AlgorithmUtils.saveModelToStream(sink, svmSavePath, classifier, encryptSpec);
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

		if (scoreModel instanceof PseudoProbabilisticClassifier) {
			classifier = (PseudoProbabilisticClassifier) scoreModel;
		} else {
			LOGGER.debug("ML model was not of correct type for NCM of type {}", IDENTIFIER);
			throw new IllegalArgumentException("ML scoring model was not of the correct algorithm type for NCM");
		}

		if (params.get(PropertyNameSettings.NCM_SCORING_MODEL_LOCATION) != null) {
			AlgorithmUtils.loadAlgorithm(src, params.get(PropertyNameSettings.NCM_SCORING_MODEL_LOCATION).toString(), classifier, encryptSpec);
			LOGGER.debug("loaded scoring model");
		}

	}

}

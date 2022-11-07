/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.io.impl;

import com.arosbio.ml.io.impl.PropertyFileStructure.InfoSection;

/**
 * Utility class for how to write property files (labels, percentiles, number of models, predictor type etc)
 * @author staffan
 *
 */
public class PropertyNameSettings {
	
	// Model properties (classification)
	public final static String IS_CLASSIFICATION_KEY = "isClassification";
	public final static String CLASS_LABELS_LIST_KEY = "classLabels";
	
	// Model properties (general)
	public static final String SAMPLING_STRATEGY_KEY = "samplingStrategy";
	public final static String SAMPLING_STRATEGY_NR_MODELS_KEY = "samplingStrategyNrModels";
	public final static String SAMPLING_STRATEGY_CALIB_RATIO_KEY = "samplingStrategyCalibrationRatio";
	
	// Nesting 
	public static final String PREDICTOR_NESTING_KEY = "predictor";
	public static final String DATA_NESTING_KEY = "data";
	public static final String PREDICTOR_ML_ALG_INFO_KEY = "targetModelInfo";
	public static final String PREDICTOR_ERROR_MODEL_ML_ALG_INFO_KEY = "errorModelInfo";
	
	
	public final static String LOW_PERCENTILE_KEY = "lowPercentile";
	public final static String HIGH_PERCENTILE_KEY = "highPercentile";
	
	public static final String DESCRIPTOR_NAMES_KEY = "descriptorName";
	public static final String DESCRIPTOR_ID_KEY = "descriptorID";
	public static final String DESCRIPTORS_LIST_KEY = "descriptorsList";
	public final static String SIGNATURES_START_HEIGHT_KEY = "signaturesStartHeight";
	public final static String SIGNATURES_END_HEIGHT_KEY = "signaturesEndHeight";
	public final static String SIGNATURES_GENERATOR_KEY = "signaturesGeneratorName";
	public final static String MODELING_ENDPOINT_KEY = "modelingEndpoint";
	
	public final static String NUM_FEATURES_KEY = "nrFeatures";
	public final static String NUM_OBSERVATIONS_KEY = "nrObservations";
	
	// Model naming and version control
	public final static String MODEL_NAME_KEY = InfoSection.MODEL_NAME_KEY;
	public final static String MODEL_VERSION_KEY = InfoSection.MODEL_VERSION_KEY;
	public final static String MODEL_CATEGORY_KEY = InfoSection.MODEL_CATEGORY_KEY;
	
	public final static String NCM_NAME = "nonconformityMeasureName";
	public final static String NCM_ID = "nonconformityMeasureID";
	public final static String NCM_VERSION = "nonconformityMeasureVersion";
	
	// public final static String PVALUE_CALCULATOR_NAME_KEY = "pValueCalculatorName";
	// public final static String PVALUE_CALCULATOR_ID_KEY = "pValueCalculatorID";
	// public final static String PVALUE_CALCULATOR_SEED_KEY = "pValueCalculatorSeed";
	
	public final static String NCM_SCORING_MODEL_NAME = "ncmScoringModelName";
	public final static String NCM_SCORING_MODEL_ID = "ncmScoringModelID";
	public final static String NCM_ERROR_MODEL_NAME = "ncmErrorModelName";
	public final static String NCM_ERROR_MODEL_ID = "ncmErrorModelID";
	
	public final static String NCM_SCORING_MODEL_LOCATION = "ncmScoringModelLocation";
	public final static String NCM_SCORING_PARAMETERS_LOCATION = "ncmScoringModelPropertiesLocation";
	public final static String NCM_ERROR_MODEL_LOCATION = "ncmErrorModelLocation";
	public final static String NCM_ERROR_PARAMETERS_LOCATION = "ncmErrorModelPropertiesLocation";
	
	public final static String IS_ENCRYPTED = "isEncrypted";
	
	
	// ML Settings
	public final static String ML_TYPE_KEY = "mlPredictorType";
	public final static String ML_TYPE_NAME_KEY = "mlPredictorTypeName";
	/** Should be saved as a Long */
	public static final String ML_SEED_VALUE_KEY = "seedValue";
	
	


}

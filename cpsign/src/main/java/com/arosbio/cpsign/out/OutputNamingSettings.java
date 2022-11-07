/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.out;

import java.util.Collection;

import org.apache.commons.math.util.MathUtils;

import com.arosbio.chem.CPSignMolProperties;

public class OutputNamingSettings {

	/* ===========================================================
	 * 		PROGRESS BAR
	 * ===========================================================*/

	/**
	 * Progress Bar naming conventions
	 * @author staffan
	 *
	 */
	public static class PB {

		public static final String INIT_PROGRAM_PROGRESS = "Init";
		public static final String VALIDATE_PARAMS_PROGRESS = "Validating";
		public static final String RESULTS_WRITING_PROGRESS = "Writing";
		public static final String CROSSVALIDATING_PROGRESS = "CV";
		public static final String SAVING_JAR_PROGRESS = "Saving";
		public static final String TRAINING_PROGRESS = "Training";
		public static final String PARSING_FILE_OR_MODEL_PROGRESS = "Parsing";
		public static final String LOADING_FILE_OR_MODEL_PROGRESS = "Loading";
		public static final String AGGREGATING_MODELS_PROGRESS = "Aggregating";
		public static final String COMPUTING_PERCENTILES_PROGRESS = "Computing";
		public static final String PREDICTING_PROGRESS = "Predicting";
		public static final String FINISHED_PROGRAM_PROGRESS = "Finished";
		public static final String FAILED_PROGRAM_PROGRESS = "Failed";

	}
	
	public static class ProgressInfoTexts {
		public static final String VALIDATING_ARGS = "Validating arguments... ";
		public static final String COMPUTING_PREDICTIONS = "Computing predictions... ";
		public static final String COMPUTING_PERCENTILES = "Computing percentiles... ";
		public static final String LOADING_MODEL = "Loading model... ";
		public static final String LOADING_PRECOMP_DATA = "Loading precomputed data set... ";
		public static final String FAILED_TAG = "[failed]";
		public static final String SKIPPED_TAG = "[skipped]";
		public static final String DONE_TAG = "[done]";
		public static final String ELIPSES = "... ";
		public static final String SPACE_ELIPSES = "\u00A0... ";
	}


	/* ===========================================================
	 * 		JSON
	 * ===========================================================*/

	/**
	 * Names for prediction output in JSON
	 * @author staffan
	 *
	 */
	public static class JSON {

		public static final String GENERATED_SIGNATURES_SECTION_KEY = "generatedSignatures";
		public static final String GRADIENT_RESULTS_SECTION_KEY = "gradientResult";
		public static final String MOLECULE_SECTION_KEY = "molecule";
		public static final String PREDICTING_SECTION_KEY = "prediction";
		// GRADIENT
		public static final String ATOM_VALS_KEY = "atomValues";
		public static final String SIGNIFICANT_SIGNATURE_KEY = "significantSignature";
		public static final String SIGNIFICANT_SIGNATURE_HEIGHT_KEY = "significantSignatureHeight";
		public static final String FEATURES_GRADIENT_KEY = "featuresGradient";
		// MOLECULE
		public static final String SMILES_KEY = "SMILES";
		public static final String INCHI_KEY = "InChI";
		public static final String INCHIKEY_KEY = "InChIKey";
		// PREDICTION
		public static final String CONFIDENCE_KEY = "confidence";
		public static final String REG_INTERVALS_SECTION_KEY = "intervals";
		public static final String REG_WIDTH_KEY = "predictionWidth";
		public static final String REG_MIDPOINT_KEY = "midpoint";
		public static final String REG_RANGE_UPPER_KEY = "rangeUpper";
		public static final String REG_RANGE_UPPER_CAPPED_KEY = "rangeUpperCapped";
		public static final String REG_RANGE_LOWER_KEY = "rangeLower";
		public static final String REG_RANGE_LOWER_CAPPED_KEY = "rangeLowerCapped";
		public static final String CLASS_PVALS_KEY = "pValues";
		public static final String CLASS_PROBABILITIES_KEY = "probabilities";
		public static final String CLASS_LABELS_KEY = "labels";
		public static final String CLASS_PREDICTED_LABELS_KEY = "predictedLabels";
		public static final String CVAP_PROBABILITY_INTERVAL_MEAN = "p0p1IntervalMeanWidth";
		public static final String CVAP_PROBABILITY_INTERVAL_MEDIAN = "p0p1IntervalMedianWidth";
		
		// Metrics output
		public static final String PLOT_SECTION = "plot";
		public static final String ROC_PLOT_SECTION = "ROC";
	}
	

	/* ===========================================================
	 * 		SMILES/PLAIN & SDF
	 * ===========================================================*/

	/**
	 * Names for prediction output in CSV / SDF format
	 * @author staffan
	 *
	 */
	public static class PredictionOutput {

		public static final String SIGNIFICANT_SIGNATURE_PROPERTY = "Significant Signature";
		public static final String SIGNIFICANT_SIGNATURE_HEIGHT_PROPERTY = "Significant Signature Height";
		public static final String SIGNIFICANT_SIGNATURE_GRADIENT_PROPERTY = "Atom values";
		public static final String REGRESSION_Y_HAT_PREDICTION_PROPERTY = "Predicted value (ŷ)";
		public static final String GENERATED_SIGNATURES_PROPERTY = "Generated Signatures";
		public static final String CVAP_PROBABILITY_INTERVAL_MEAN_PROPERTY = "p0 p1 Interval mean width";
		public static final String CVAP_PROBABILITY_INTERVAL_MEDIAN_PROPERTY = "p0 p1 Interval median width";
		
	}
	/* ===========================================================
	 * 		GENERAL STUFF
	 * ===========================================================
	 */
	public static final String RECORD_INDEX_PROPERTY = "Record Index";
	public static final String SMILES_PROPERTY = "SMILES";
	
	private static String round(double val) {
		return "" + MathUtils.round(val, 3);
	}

	// CP CLASSIFICATION
	public static final String getPredictedLabelsProperty(double conf){
		return "Predicted labels (confidence="+round(conf)+")";
	}
	public static final String getPvalueForLabelProperty(String label) {
		return "p-value [label="+label+"]";
	}

	// CP REGRESSION
	public static final String getPredictionIntervalLowerBoundProperty(double conf){
		return "Prediction interval lower bound (confidence="+round(conf)+")";
	}
	public static final String getPredictionIntervalUpperBoundProperty(double conf){
		return "Prediction interval upper bound (confidence="+round(conf)+")";
	}

	public static final String getCappedPredictionIntervalLowerBoundProperty(double conf){
		return "Capped prediction interval lower bound (confidence="+round(conf)+")";
	}
	public static final String getCappedPredictionIntervalUpperBoundProperty(double conf){
		return "Capped prediction interval upper bound (confidence="+round(conf)+")";
	}

	// Gradient stuff
	public static final String getExtraFeatureGradientProperty(String featureName) {
		return "\u2207("+featureName+')';
	}
	public static final String getConfGivenWidthProperty(double width){
		return "Confidence (prediction width="+width+")";
	}

	// CVAP CLASSIFICATION
	public static final String getProbabilityProperty(String label) {
		return "Probability [label="+label+"]";
	}

	/**
	 * Get a custom name for smiles, or <code>null</code> if no such one 
	 * is found (then use the default one in the output instead)
	 * @param properties properties found in input
	 * @return a custom name or <code>null</code>
	 */
	public static final String getCustomSmilesProperty(Collection<? extends Object> properties) {
		for (Object prop : CPSignMolProperties.stripInteralProperties(properties)) {
			String lowCase = prop.toString().toLowerCase();
			if (lowCase.contains("smiles")) {
				return prop.toString();
			}
		}
		return null;
	}
	

}

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cheminf;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.data.FeatureVector;
import com.arosbio.ml.cp.CPRegressionPrediction;
import com.arosbio.ml.cp.ConformalRegressor;
import com.arosbio.ml.io.impl.PropertyNameSettings;

public final class ChemCPRegressor extends ChemRegressorImpl {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ChemCPRegressor.class);
	private static final String DEFAULT_MODEL_NAME = "conformal regression predictor";

	private ConformalRegressor predictorImpl;

	// ---------------------------------------------------------------------
	// CONSTRUCTORS
	// ---------------------------------------------------------------------

	public ChemCPRegressor(){
		super();
	}
	
	public <T extends ConformalRegressor> ChemCPRegressor(T predictor) {
		super();
		Objects.requireNonNull(predictor, "Predictor should not be null");
		this.predictorImpl = predictor;
	}

	public <T extends ConformalRegressor> ChemCPRegressor(T predictor, int startHeight, int stopHeight) {
		this(predictor);
		setDataset(new ChemDataset(startHeight, stopHeight));
	}

	// ---------------------------------------------------------------------
	// GETTERS AND SETTERS
	// ---------------------------------------------------------------------

	@Override
	public ConformalRegressor getPredictor() {
		return predictorImpl;
	}

	public void setPredictor(ConformalRegressor model) {
		predictorImpl = model;
	}

	@Override
	public String getDefaultModelName() {
		return DEFAULT_MODEL_NAME;
	}

	@Override
	public Map<String,Object> getProperties() {
		Map<String,Object> props = super.getProperties();
		props.put(PropertyNameSettings.IS_CLASSIFICATION_KEY, false);
		return props;
	}

	// ---------------------------------------------------------------------
	// PREDICTIONS
	// ---------------------------------------------------------------------

	/**
	 * Predict confidence interval using a single confidence value
	 * @param mol Molecule to predict
	 * @param confidence The confidence
	 * @return A {@link com.arosbio.ml.cp.CPRegressionPrediction} 
	 * @throws CDKException In case of a <code>CDKException</code> when configuring the <code>IAtomContainer</code>
	 * @throws IllegalArgumentException If {@code mol} is null
	 * @throws IllegalStateException If predictor is not trained 
	 */
	public CPRegressionPrediction predict(IAtomContainer mol, double confidence) 
			throws CDKException, NullPointerException, IllegalStateException {
		return predict(mol, Arrays.asList(confidence));
	}

	/**
	 * Predict confidence interval using multiple confidence values
	 * @param mol Molecule to predict
	 * @param confidences The confidences
	 * @return A {@link java.util.List List} of {@link com.arosbio.ml.cp.CPRegressionPrediction CPRegressionPrediction} 
	 * @throws CDKException In case of a <code>CDKException</code> when configuring the <code>IAtomContainer</code>
	 * @throws NullPointerException If {@code mol} is null
	 * @throws IllegalStateException If predictor is not trained 
	 */
	public CPRegressionPrediction predict(IAtomContainer mol, List<Double> confidences) 
			throws CDKException, NullPointerException, IllegalStateException {
		Objects.requireNonNull(mol, "Mol cannot be null");
		if (predictorImpl==null)
			throw new IllegalStateException("Predictor is not set");
		if (!predictorImpl.isTrained())
			throw new IllegalStateException("Predictor is not trained");
		return predictorImpl.predict(convertToFeatureVector(mol), confidences);
	}

	/**
	 * Predict the confidence and interval for a given prediction interval width (i.e. get the confidence needed for a given prediction interval width)
	 * @param mol Molecule to predict
	 * @param width The prediction interval width
	 * @return A {@link com.arosbio.ml.cp.CPRegressionPrediction CPRegressionPrediction} 
	 * @throws CDKException In case of a <code>CDKException</code> when configuring the <code>IAtomContainer</code>
	 * @throws IllegalStateException If predictor is not trained
	 * @throws NullPointerException If {@code mol} is null
	 */
	public CPRegressionPrediction predictConfidence(IAtomContainer mol, double width) 
			throws IllegalStateException, NullPointerException, CDKException {
		return predictConfidence(mol, Arrays.asList(width));
	}

	/**
	 * <b>Predict mode</b> <br>
	 * Predict the confidence and interval for a list of given prediction interval widths (i.e. get the confidence needed for a given prediction interval width)
	 * @param mol Molecule to predict
	 * @param widths The prediction interval widths
	 * @return A {@link java.util.List List} of {@link com.arosbio.ml.cp.CPRegressionPrediction CPRegressionPrediction} 
	 * @throws CDKException In case of a <code>CDKException</code> when configuring the <code>IAtomContainer</code>
	 * @throws NullPointerException If {@code mol} is null
	 * @throws IllegalStateException If predictor is not trained 
	 */
	public CPRegressionPrediction predictConfidence(IAtomContainer mol, List<Double> widths) 
			throws CDKException, NullPointerException, IllegalStateException {
		if (predictorImpl==null || !predictorImpl.isTrained())
			throw new IllegalStateException("Predictor is not trained");
		return predictorImpl.predictConfidence(convertToFeatureVector(mol), widths);
	}

	@Override
	public SignificantSignature predictSignificantSignature(IAtomContainer mol)
			throws IllegalStateException, NullPointerException, CDKException {

		FeatureVector features = convertToFeatureVector(mol);

		// Calculate gradient
		return convertRawGradientToSS(mol, predictorImpl.calculateGradient(features));

	}

	

}

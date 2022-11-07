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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.SparseFeature;
import com.arosbio.ml.cp.ConformalClassifier;

public final class ChemCPClassifier extends ChemClassifierImpl {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ChemCPClassifier.class);
	private static final String DEFAULT_MODEL_NAME = "conformal classification predictor";

	private ConformalClassifier predictorImpl;

	// ---------------------------------------------------------------------
	// CONSTRUCTORS
	// ---------------------------------------------------------------------

	public <T extends ConformalClassifier> ChemCPClassifier(T predictor) {
		super();
		Objects.nonNull(predictor);
		this.predictorImpl = predictor;
	}

	public <T extends ConformalClassifier> ChemCPClassifier(T predictor, int startHeight, int stopHeight) {
		this(predictor);
		setDataset(new ChemDataset(startHeight, stopHeight));
	}

	// ---------------------------------------------------------------------
	// GETTER AND SETTER METHODS
	// ---------------------------------------------------------------------

	@Override
	public ConformalClassifier getPredictor() {
		return predictorImpl;
	}

	public void setPredictor(ConformalClassifier predictor) {
		this.predictorImpl = predictor;
	}

	public Map<String,Object> getProperties() {
		Map<String,Object> props = super.getProperties();
		return props;
	}

	@Override
	public String getDefaultModelName() {
		return DEFAULT_MODEL_NAME;
	}


	// ---------------------------------------------------------------------
	// PREDICTIONS
	// ---------------------------------------------------------------------

	/**
	 * Predict the p-values for the classes 
	 * @param mol The new molecule to predict
	 * @return A {@link java.util.Map Map} linking label to p-value for the prediction 
	 * @throws CDKException In case of a {@link org.openscience.cdk.exception.CDKException CDKException} when configuring the {@link org.openscience.cdk.interfaces.IAtomContainer IAtomContainer}
	 * @throws IllegalStateException If predictor is not trained or no labels set
	 * @throws NullPointerException If {@code mol} is null
	 */
	public Map<String, Double> predict(IAtomContainer mol) 
			throws CDKException, NullPointerException, IllegalStateException {
		if (predictorImpl == null)
			throw new IllegalStateException("No CP-implementation set!");
		if (! predictorImpl.isTrained())
			throw new IllegalStateException("Predictor is not trained");
		if (getLabelsSet()==null || getLabelsSet().isEmpty())
			throw new IllegalStateException("No valid labels set");
		if (getLabelsSet().size() != ((ConformalClassifier)predictorImpl).getNumClasses()) 
			throw new IllegalStateException("Number of labels not the same as number of classes");
		Map<Integer, Double> pvals = predictorImpl.predict(convertToFeatureVector(mol));
		Map<String, Double> result = new HashMap<>();
		for (Map.Entry<Integer, String> label : getLabels().entrySet())
			result.put(label.getValue(), pvals.get(label.getKey()));
		return result;
	}

	@Override
	public SignificantSignature predictSignificantSignature(IAtomContainer mol) 
			throws IllegalStateException, NullPointerException, CDKException {
		return predictSignificantSignature(mol, null);
	}

	public SignificantSignature predictSignificantSignature(IAtomContainer mol, String label) 
			throws IllegalStateException, NullPointerException, CDKException {

		FeatureVector features = convertToFeatureVector(mol);

		// Calculate gradient
		List<SparseFeature> gradient = null;
		if (label==null)
			gradient = predictorImpl.calculateGradient(features);
		else {
			gradient = predictorImpl.calculateGradient(features, getNamedLabels().getValue(label));
		}

		return convertRawGradientToSS(mol, gradient);

	}


}

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

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.NamedLabels;
import com.arosbio.data.SparseFeature;
import com.arosbio.ml.vap.avap.AVAPClassifier;
import com.arosbio.ml.vap.avap.CVAPPrediction;

/**
 * Venn ABERS Predictor classifier
 * @author staffan
 *
 */
public final class ChemVAPClassifier extends ChemClassifierImpl {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ChemVAPClassifier.class);

	private static final String DEFAULT_MODEL_NAME = "cross Venn-ABERS classification predictor";

	private AVAPClassifier predictorImpl;

	// ---------------------------------------------------------------------
	// CONSTRUCTORS
	// ---------------------------------------------------------------------

	public ChemVAPClassifier(AVAPClassifier impl) {
		super();
		Objects.requireNonNull(impl, "Predictor should not be null");
		this.predictorImpl = impl; 
	}

	public ChemVAPClassifier(AVAPClassifier impl,int startHeight, int stopHeight) {
		this(impl);
		setDataset(new ChemDataset(startHeight, stopHeight));
	}

	// ---------------------------------------------------------------------
	// GETTERS AND SETTERS
	// ---------------------------------------------------------------------

	@Override
	public AVAPClassifier getPredictor() {
		return predictorImpl;
	}

	@Override
	public String getDefaultModelName() {
		return DEFAULT_MODEL_NAME;
	}


	// ---------------------------------------------------------------------
	// PREDICTIONS
	// ---------------------------------------------------------------------

	/**
	 * Predict the probability for the two classes 
	 * @param mol The molecule to predict
	 * @return A {@link CVAPPrediction} with all results from the prediction 
	 * @throws IllegalStateException If predictor is not trained or no labels set
	 * @throws NullPointerException If {@code mol} is null
	 * @throws CDKException Errors when configuring the molecule
	 */
	public CVAPPrediction<String> predict(IAtomContainer mol)
			throws IllegalStateException, NullPointerException, CDKException {
		if (predictorImpl==null)
			throw new IllegalStateException("No CVAP implementation set");
		if (!predictorImpl.isTrained())
			throw new IllegalStateException("The CVAP predictor is not trained");
		if (getLabelsSet()== null || getLabelsSet().size() < 2)
			throw new IllegalStateException("No labels set");
		if (mol==null)
			throw new IllegalArgumentException("The molecule cannot be null");
		CVAPPrediction<Integer> res = predictorImpl.predict(convertToFeatureVector(mol));
		NamedLabels conv = getNamedLabels();
		return new CVAPPrediction<>(
			res.getP0List(),
			res.getP1List(),
			conv.getLabel(res.getLabel0()),
			conv.getLabel(res.getLabel1()),
			conv.convert(res.getProbabilities()),
			res.getMeanP0P1Width(),
			res.getMedianP0P1Width());
	}

	/**
	 * Predict the probability for the two classes 
	 * @param mol The molecule to predict
	 * @return A <code>Map</code> from label to probability 
	 * @throws IllegalStateException If predictor is not trained or no labels set
	 * @throws NullPointerException If {@code mol} is null
	 * @throws CDKException Errors when configuring the molecule
	 */
	public Map<String, Double> predictProbabilities(IAtomContainer mol) 
			throws IllegalStateException, NullPointerException, CDKException {
		return predict(mol).getProbabilities();
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
		if (label==null) {
			gradient = predictorImpl.calculateGradient(features);
		} else {
			gradient = predictorImpl.calculateGradient(features, getNamedLabels().getValue(label));
		}

		return convertRawGradientToSS(mol, gradient);

	}


}

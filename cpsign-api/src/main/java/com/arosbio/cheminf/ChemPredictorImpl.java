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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.CDKConfigureAtomContainer;
import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.descriptors.ChemDescriptor;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor.SignatureInfo;
import com.arosbio.commons.MathUtils;
import com.arosbio.data.Dataset.RecordType;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.SparseFeature;
import com.arosbio.ml.io.ModelInfo;
import com.arosbio.ml.io.impl.PropertyNameSettings;

public abstract class ChemPredictorImpl implements ChemPredictor {

	private static final Logger LOGGER = LoggerFactory.getLogger(ChemPredictorImpl.class);
	private static final int DEFAULT_MAX_MOL_PERCENTILES = 1000;

	private ChemDataset data = new ChemDataset();
	private ModelInfo info = new ModelInfo(getDefaultModelName());
	private boolean notifiedUserAboutPercentilesNotSet = false;

	protected Double lowPercentile;
	protected Double highPercentile;


	// ---------------------------------------------------------------------
	// GETTER AND SETTER METHODS
	// ---------------------------------------------------------------------

	@Override
	public ChemPredictor withModelInfo(ModelInfo info){
		this.info = info;
		return this;
	}

	@Override
	public ModelInfo getModelInfo(){
		return info;
	}

	@Override
	public ChemDataset getDataset() {
		return data;
	}

	@Override
	public void setDataset(ChemDataset data) {
		this.data = data;
	}

	@Override
	public Double getLowPercentile() {
		return lowPercentile;
	}

	@Override
	public void setLowPercentile(Double low){
		this.lowPercentile = low;
	}

	@Override
	public Double getHighPercentile() {
		return highPercentile;
	}

	@Override
	public void setHighPercentile(Double high){
		this.highPercentile = high;
	}
	
	/**
	 * Get a list of feature names, that counters for potential transformations 
	 * performed on this problem.
	 * @param includeSignatures If signatures should be included, which can potentially lead to a very large list
	 * @return a list of all feature names
	 */
	public List<String> getFeatureNames(boolean includeSignatures){
		return data.getFeatureNames(includeSignatures);
	}
	
	public boolean usesSignaturesDescriptor() {
		for (ChemDescriptor d : data.getDescriptors()) {
			if (d instanceof SignaturesDescriptor)
				return true;
		}
		return false;
	}
	
	public boolean checkIfDatasetsContainMissingFeatures() {
		return data.getDataset().containsMissingFeatures() 
				|| data.getCalibrationExclusiveDataset().containsMissingFeatures() 
				|| data.getModelingExclusiveDataset().containsMissingFeatures();
	}

	public String getProperty() {
		return data.getProperty();
	}

	public void initializeDescriptors() {
		this.data.initializeDescriptors();
	}

	public boolean hasValidPercentiles() {
		if (lowPercentile == null || highPercentile == null)
			return false;
		if (lowPercentile.equals(highPercentile)) {
			return false;
		} else if (lowPercentile > highPercentile) {
			return false;
		}
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Map<String,Object> getProperties() {
		Map<String,Object> props = new HashMap<>();
		Map<String,Object> dataProps =  data.getProperties();
		props.putAll(info.getProperties());

		if (getPredictor() != null){
			Map<String,Object> predictorProps = getPredictor().getProperties();
			props.put(PropertyNameSettings.PREDICTOR_NESTING_KEY, predictorProps);
			int numObs = getPredictor().getNumObservationsUsed();
			if (numObs > 0 && (int)dataProps.get(PropertyNameSettings.NUM_OBSERVATIONS_KEY) == 0){
				dataProps.put(PropertyNameSettings.NUM_OBSERVATIONS_KEY, numObs);
			}
		}
		props.put(PropertyNameSettings.DATA_NESTING_KEY, dataProps);
		props.put(PropertyNameSettings.LOW_PERCENTILE_KEY, lowPercentile);
		props.put(PropertyNameSettings.HIGH_PERCENTILE_KEY, highPercentile);
		return props;
	}

	// ---------------------------------------------------------------------
	// LOADING DATA
	// ---------------------------------------------------------------------

	@Override
	public void addRecords(Iterator<Pair<IAtomContainer, Double>> data) 
			throws IllegalArgumentException {
		this.data.add(data);	
	}

	/**
	 * Load molecules and compute descriptors from an iterator that give a {@link Pair} of <code>IAtomContainer</code> and the corresponding
	 * regression or classification value.
	 * <h3> IF CLASSIFICATION:</h3>
	 * Use unique integer numbers for each class. Also make sure to set labels in the {@link com.arosbio.cheminf.ChemClassifier ChemClassifier} instance by
	 * calling the method {@link com.arosbio.cheminf.ChemClassifier#setLabels(Map)}. 
	 * @param data An {@link Iterator} with a {@link Pair} of a molecule and corresponding value
	 * @param type The dataset that the records should be added to
	 * @throws IllegalArgumentException If no molecules could be loaded successfully from the iterator
	 */
	public void addRecords(Iterator<Pair<IAtomContainer, Double>> data, RecordType type) 
			throws IllegalArgumentException {
		this.data.add(data, type);	
	}


	// ---------------------------------------------------------------------
	// TRAIN PREDICTOR
	// ---------------------------------------------------------------------

	@Override
	public void train() throws IllegalArgumentException, IllegalStateException {
		if (data == null || data.isEmpty())
			throw new IllegalStateException("No data loaded to train the predictor on");
		getPredictor().train(data);
	}

	// ---------------------------------------------------------------------
	// UTILITY METHODS
	// ---------------------------------------------------------------------


	/**
	 * Converts the atom container to a FeatureVector using the descriptors and transformations
	 * of the current {@link ChemDataset}.
	 * @param mol An {@link IAtomContainer} to convert to numerical vector
	 * @return A {@link com.arosbio.data.FeatureVector FeatureVector} with the numerical representation of the molecule
	 * @throws CDKException Issues when configuring the atom container using CDK
	 * @throws NullPointerException If mol is {@code null}
	 * @throws IllegalStateException If no descriptors are loaded or initialized correctly
	 */
	public FeatureVector convertToFeatureVector(IAtomContainer mol) 
			throws CDKException, NullPointerException, IllegalStateException {
		Objects.requireNonNull(mol, "Mol cannot be null");
		
		if (data == null)
			throw new IllegalStateException("No descriptors configured");
		
		// Convert ac to list of FeatureVector - using the descriptors / transformers for this problem
		return data.convertToFeatureVector(mol);
	}

	@Override
	public void computePercentiles(Iterator<IAtomContainer> molIterator) 
			throws IllegalStateException, IOException {
		computePercentiles(molIterator, DEFAULT_MAX_MOL_PERCENTILES);
	}

	public void computePercentiles(Iterator<IAtomContainer> molIterator, int maxNumMolecules) 
			throws IllegalStateException, IOException {
		if (! getPredictor().isTrained()){
			throw new IllegalStateException("Predictor model not trained");
		}
		
		// Check that we have signatures descriptor - otherwise this is useless
		SignaturesDescriptor desc = null;
		for (ChemDescriptor d : data.getDescriptors()) {
			if (d instanceof SignaturesDescriptor){
				desc = (SignaturesDescriptor) d;
				break;
			}
		}
		if (desc == null) {
			LOGGER.debug("Called the computePercentiles method when not having signatures-descriptor, failing");
			throw new IllegalStateException("No Signatures descriptor in use, computing percentiles should not be called!");
		}

		LOGGER.debug("running computePercentiles");
		List<Double> derivatives = new ArrayList<>();
		int numOKMols=0, numFails=0;
		try {
			IAtomContainer mol;
			List<String> fNames = getFeatureNames(true);
			int signStartIndex = getFeatureNames(false).size();
			

			while (molIterator.hasNext() && numOKMols < maxNumMolecules) {
				mol = molIterator.next();
				try {
					CDKConfigureAtomContainer.configMolecule(mol);
					List<SparseFeature> fullGradient = getPredictor().calculateGradient(getDataset().convertToFeatureVector(mol));

					Map<Integer,Double> molGradient = getAtomContributions(fullGradient, desc.generateSignaturesExtended(mol),fNames, signStartIndex, false, mol.getAtomCount());
					// Add all contributions to the list
					derivatives.addAll(molGradient.values());

					numOKMols++;
				} catch (Exception e) {
					if (e instanceof CDKException)
						LOGGER.trace("CDKException in predicting molecule: {}", e.getMessage());
					else
						LOGGER.debug("failed predicting molecule when computing percentiles", e);
					numFails++;
				}
			}

			if (numFails >0){
				LOGGER.debug("Failed computing percentiles for {} molecules",numFails);
			}
			
			if (derivatives.isEmpty()) {
				throw new RuntimeException("Failed computing percentiles: no molecules were successfully predicted");
			}

			// Deduce 10% and 90% of Y values to set the range
			Collections.sort(derivatives);
			lowPercentile = derivatives.get((int) (derivatives.size()*0.1));
			highPercentile = derivatives.get((int) (derivatives.size()*0.9));
			
			if (lowPercentile.equals(highPercentile)) {
				LOGGER.warn("The calculated low and high percentiles are equal - meaning that the gradient will be non-informative");
			}
			
		} finally {

		}
		LOGGER.debug("finished computePercentiles, used {} molecules to estimate lower ({}) and higher ({}) percentiles",
			numOKMols,lowPercentile,highPercentile);
	}


	/* =========================================================
	 * 
	 * 
	 * HERE COMES CODE USED FOR PREDICTING SIGNIFICANT SIGNATURE
	 * 
	 * 
	 * =========================================================
	 */

	/**
	 * Convert the raw gradient {@code gradient} from underlying predictor, into the {@link SignificantSignature}
	 * object. Getting the significant signature, the atom contributions and any extra gradient
	 * @param mol The predicted molecule
	 * @param gradient The raw gradient from underlying predictor
	 * @return the {@link SignificantSignature} of the gradient prediction
	 * @throws IllegalStateException Not trained yet
	 * @throws IllegalArgumentException Invalid arguments 
	 * @throws CDKException Issues generating signatures for the {@link IAtomContainer} instance
	 */
	SignificantSignature convertRawGradientToSS(IAtomContainer mol, List<SparseFeature> gradient) 
	 	throws IllegalStateException, IllegalArgumentException, CDKException {

		SignificantSignature.Builder builder = new SignificantSignature.Builder(gradient);

		// end early if no gradient
		if (gradient.isEmpty()) {
			return builder.build();
		}
		LOGGER.trace("input gradient: {}", gradient);

		// Get the additional features
		builder.featureGradient(getAdditionalGrad(gradient));

		
		SignaturesDescriptor descriptor = getSignDescriptor(false);
		if (descriptor != null){
			Map<String, SignatureInfo> sigs = descriptor.generateSignaturesExtended(mol);
			int signStartIndex = getFeatureNames(false).size();
			SignatureInfo highestImpact = getSignSignature(gradient, signStartIndex, sigs);

			Map<Integer, Double> moleculeGradient = getAtomContributions(gradient, sigs, getFeatureNames(true),signStartIndex,true, mol.getAtomCount());

			Set<Integer> significantSignatureAtoms = getAtomsPartOfSignificantSignature(
					mol, 
					highestImpact.getCenterAtoms(), 
					highestImpact.getHeight());

			builder.signature(highestImpact.getSignature())
				.height(highestImpact.getHeight())
				.atoms(significantSignatureAtoms)
				.atomContributions(moleculeGradient);
		}

		return builder.build();
	}

	 private SignaturesDescriptor getSignDescriptor(boolean failIfNone){
		for (ChemDescriptor d : data.getDescriptors()){
			if (d instanceof SignaturesDescriptor)
				return (SignaturesDescriptor) d;
		}
		if (failIfNone){
			throw new IllegalStateException("No SignaturesDescriptor used - method not allowed");
		}
		return null;
	}

	private Map<String,Double> getAdditionalGrad(List<SparseFeature> grad){
		Map<String,Double> gradient = new HashMap<>();

		List<String> featNames = getFeatureNames(false);
		if (!featNames.isEmpty()){
			for (SparseFeature sf : grad){
				if (sf.getIndex()>=featNames.size()){
					break;
				}
				gradient.put(featNames.get(sf.getIndex()), sf.getValue());
			}
		}
		return gradient;

	}

	private SignatureInfo getSignSignature(List<SparseFeature> gradient, int signatureStartIndex, Map<String, SignatureInfo> computedSignatures){
		// find the maximum feature
		SparseFeature maxFeat = null;
		for (SparseFeature f : gradient){
			if (f.getIndex()>=signatureStartIndex){
				if (maxFeat == null)
					maxFeat = f;
				else if (Math.abs(f.getValue())>Math.abs(maxFeat.getValue()))
					maxFeat = f;
			}
		}

		// No feature found 
		if (maxFeat == null){
			throw new IllegalArgumentException("No gradient for signature features");
		}		
		return computedSignatures.get(getFeatureNames(true).get(maxFeat.getIndex()));		
	}

	/**
	 * Utility method for getting the highest contribution feature, from a given {@code startIndex}. 
	 * Used for finding the most important Signature 
	 * @param gradient
	 * @param startIndex
	 * @return
	 */
	SparseFeature getLargestGradientIndex(List<SparseFeature> gradient, int startIndex) {
		SparseFeature maxFeat = null;
		for (SparseFeature f : gradient) {
			if (f.getIndex()>= startIndex) {
				if (maxFeat == null)
					maxFeat = f;
				else if (Math.abs(f.getValue())>Math.abs(maxFeat.getValue()))
					maxFeat = f;
			}
		}
		// return this index
		return maxFeat;
	}

	private Map<Integer,Double> getAtomContributions(
		List<SparseFeature> gradient, 
		Map<String,SignatureInfo> signatures, 
		List<String> featureNames,
		int signatureStartIndex,
		boolean normalize,
		int numAtoms) {
		
		// Init all atoms to have 0 contribution
		Map<Integer, Double> atomGradients = new HashMap<>();
		for (int atom=0; atom<numAtoms; atom++){
			atomGradients.put(atom, 0d);
		}

		// Loop through the gradient 
		for (SparseFeature sf : gradient){
			// use the info from the signatures only
			if (sf.getIndex() < signatureStartIndex){
				continue;
			}

			// this feature is from the SignaturesDescriptor 
			String sign = featureNames.get(sf.getIndex());
			SignatureInfo info = signatures.get(sign);
			if (info == null){
				// Current signature not used in feature space of the model - ignore it
				continue;
			}

			// Add the contribution to all center atoms that the signature originates from
			for (int atom : info.getCenterAtoms()) {
				atomGradients.put(atom, atomGradients.get(atom)+sf.getValue());
			}
		}

		LOGGER.trace("atom gradient:{}", atomGradients);

		if (normalize){
			// Normalize the map if we have lowPercentile and highPercentile
			if (highPercentile != null && highPercentile != null && lowPercentile < highPercentile){
				atomGradients = MathUtils.normalizeMap(atomGradients, lowPercentile, highPercentile);
				LOGGER.trace("normalized gradient: {}", atomGradients);
			} else if ((lowPercentile == null || highPercentile == null) && ! notifiedUserAboutPercentilesNotSet ){
				// Otherwise notify the user of invalid percentiles for computing the normalized output
				LOGGER.warn("low and high percentiles not set (or not with OK values), atom-gradients in output will _not_ be normalized.");
				notifiedUserAboutPercentilesNotSet = true;
			}
		}

		return atomGradients;
		
	}

	private Set<Integer> getAtomsPartOfSignificantSignature(IAtomContainer mol, Set<Integer> centerAtoms, int height){
		Set<Integer> atomValues = new HashSet<>(); 		

		//Get atom numbers from center atoms by height
		for (int centerAtom : centerAtoms){

			int currentHeight=0;
			List<Integer> lastNeighbors = new ArrayList<>();
			lastNeighbors.add( centerAtom );
			atomValues.add( centerAtom );

			while (currentHeight<height){

				List<Integer> newNeighbors=new ArrayList<>();

				//for all lastNeighbors, get new neighbors
				for (Integer neighbor : lastNeighbors){
					for (IAtom nbr : mol.getConnectedAtomsList(
							mol.getAtom( neighbor )) ){

						//Set each neighbor atom to overall match classification
						int nbrAtomNr = mol.indexOf(nbr);
						atomValues.add( nbrAtomNr );
						newNeighbors.add(nbrAtomNr);

					}
				}

				lastNeighbors=newNeighbors;

				currentHeight++;
			}

		}
		return atomValues;
	}


}

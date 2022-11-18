/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cheminf.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.CDKConfigureAtomContainer;
import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.chem.io.in.ChemFileIterator.EarlyLoadingStopException;
import com.arosbio.chem.io.in.FailedRecord;
import com.arosbio.chem.io.in.MolAndActivityConverter;
import com.arosbio.cheminf.descriptors.ChemDescriptor;
import com.arosbio.cheminf.descriptors.DescriptorCalcException;
import com.arosbio.cheminf.descriptors.DescriptorFactory;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.TypeUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.MissingValueFeature;
import com.arosbio.data.NamedLabels;
import com.arosbio.data.SparseFeature;
import com.arosbio.data.SparseVector;
import com.arosbio.data.transform.Transformer;
import com.arosbio.data.transform.feature_selection.FeatureSelector;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.DataIOUtils;
import com.arosbio.io.DataSink;
import com.arosbio.io.DataSource;
import com.arosbio.ml.io.MetaFileUtils;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.google.common.collect.ImmutableList;

/**
 * A {@link ChemDataset} wrapps a {@link com.arosbio.data.Dataset Dataset} and a list of {@link com.arosbio.cheminf.descriptors.ChemDescriptor ChemDescriptors} 
 * that link a specific attribute to a specific descriptor. The {@link ChemDataset} adds utility functionality for cheminformatics, such as converting {@link IAtomContainer}
 * instances into numerical records using the descriptors. Adding new records is done using one of the <code>add(..)</code>
 * methods, which can either add a single molecule at a time or multiple ones.
 * 
 * <p>
 * <b>Note:</b> The {@link com.arosbio.cheminf.descriptors.ChemDescriptor ChemDescriptor} instances need to be initialized prior
 * to calling the {@code calculateDescriptors(..)} method, which can be performed through the {@link ChemDataset} using 
 * the {@link #initializeDescriptors()} method.
 * @author staffan
 *
 */
public final class ChemDataset extends Dataset {

	// ---------------------------------------------------------------------
	// STATIC DATA
	// ---------------------------------------------------------------------
	private static final Logger LOGGER = LoggerFactory.getLogger(ChemDataset.class);

	private static final String META_FILE_NAME = "meta.json";

	private static final String DESCRIPTORS_DIRECTORY_NAME = "descriptors";
	private static final String DESCRIPTORS_META_FILE_NAME = "meta.json";
	private static final String DESCRIPTOR_INDIVIDUAL_FILE_NAME = "descriptor";

	// ---------------------------------------------------------------------
	// INSTANCE VARIABLES
	// ---------------------------------------------------------------------
	/** The minimum heavy atom count*/
	private int minimumHAC = 5;
	/** The maximum number of parsing failures that is allowed, before failing (applies for each call to add(..) methods) */
	private int numParsingFailuresAllowed=-1;

	private List<ChemDescriptor> descriptors = new ArrayList<>();
	private List<String> featureNamesExcludingSignatures;
	private List<String> featureNamesIncludingSignatures;

	private boolean keepMolRef = false;

	/**
	 * NamedLabels only for classification problems
	 */
	private NamedLabels textualLabels;
	private String property;
	private Boolean requires3D = null;

	// ---------------------------------------------------------------------
	// STATIC STUFF
	// ---------------------------------------------------------------------

	private static String getShortErrMsg(Exception e){
		// If no message was provided - give the name of the exception class instead - might provide some insight
		if (e.getMessage()==null)
			return e.getClass().getSimpleName();
		return e.getMessage().substring(0, Math.min(e.getMessage().length(), 20));
	}

	/**
	 * Class for returning information about molecules added or failed to be added. 
	 * Note that there can be failures earlier in your pipeline of code, from file IO, to parsing into 
	 * {@link org.openscience.cdk.interfaces.IAtomContainer IAtomContainers} or 
	 * other issues that is not included in these numbers. 
	 * @author staffan
	 *
	 */
	public class DescriptorCalcInfo {
		private int numOK, cdkFailed, hacFailed, numDescriptorCalcFail;
		private List<FailedRecord> failedRecords = new ArrayList<>();

		private DescriptorCalcInfo(int numOK, int cdkFailed, int hacFailed, int numDescriptorFail) {
			this.numOK = numOK;
			this.cdkFailed = cdkFailed;
			this.hacFailed = hacFailed;
			this.numDescriptorCalcFail = numDescriptorFail;
		}
		public int getNumSuccessfullyAdded() {
			return numOK;
		}

		public int getNumCDKFailedCompounds() {
			return cdkFailed;
		}

		public int getNumHeavyAtomCountFailed() {
			return hacFailed;
		}

		public int getNumDescriptorCalcFailed() {
			return numDescriptorCalcFail;
		}

		public DescriptorCalcInfo setFailedRecords(List<FailedRecord> recs) {
			this.failedRecords = recs;
			return this;
		}

		public List<FailedRecord> getFailedRecords(){
			return failedRecords;
		}

		public String toString() {
			StringBuilder txt = new StringBuilder();
			if (numOK > 0) {
				txt.append("Successfully added ")
				.append(numOK)
				.append(" molecules.");
			} 
			if (cdkFailed > 0) {
				if (txt.length()>0)
					txt.append(' ');
				txt.append("Failed ").append(cdkFailed).append(" molecules due to CDK exception handling aromaticity.");
			}

			if (hacFailed > 0) {
				if (txt.length()>0)
					txt.append(' ');
				txt.append("Failed ")
				.append(hacFailed )
				.append(" molecules due to beeing too small (min Heavy Atom Count is ")
				.append(minimumHAC).append(").");
			}
			if (numDescriptorCalcFail>0) {
				if (txt.length()>0)
					txt.append(' ');
				txt.append("Failed ").append(numDescriptorCalcFail).append(" during descriptor calculation.");
			}

			return txt.toString();
		}
	}


	// ---------------------------------------------------------------------
	// CONSTRUCTORS
	// ---------------------------------------------------------------------

	/**
	 * Default constructor that uses the {@link com.arosbio.cheminf.descriptors.SignaturesDescriptor SignaturesDescriptor} 
	 * with all its default parameters
	 */
	public ChemDataset() {
		super();
		this.descriptors.add(new SignaturesDescriptor());
	}

	/**
	 * Create a {@link ChemDataset} with the {@link com.arosbio.cheminf.descriptors.SignaturesDescriptor SignaturesDescriptor}
	 * but with custom start and end heights
	 * @param signaturesStartHeight start height for {@link com.arosbio.cheminf.descriptors.SignaturesDescriptor SignaturesDescriptor}
	 * @param signaturesEndHeight end height for {@link com.arosbio.cheminf.descriptors.SignaturesDescriptor SignaturesDescriptor}
	 */
	public ChemDataset(int signaturesStartHeight, int signaturesEndHeight) {
		super();
		this.descriptors.add(new SignaturesDescriptor(signaturesStartHeight, signaturesEndHeight));
	}

	/**
	 * Create a {@link ChemDataset} with a custom list of Descriptors. The order of the descriptors will be preserved 
	 * <b>unless</b> there is a descriptor of non-fixed length (e.g. the SignaturesDescriptor, 
	 * see {@link com.arosbio.cheminf.descriptors.ChemDescriptor#hasFixedLength() hasFixedLength()}) - in which case that descriptor will be placed
	 * last in the list. <b>Note:</b> it is not allowed to have more than one descriptor of non-fixed length.
	 * @param descriptors List of {@link com.arosbio.cheminf.descriptors.ChemDescriptor Descriptors}
	 */
	public ChemDataset(ChemDescriptor... descriptors) {
		this(Arrays.asList(descriptors));
	}

	/**
	 * Create a {@link ChemDataset} with a custom collection of Descriptors. The order of the descriptors will be preserved 
	 * (given the iterator of the collection) <b>unless</b> there is a descriptor of non-fixed length (e.g. the SignaturesDescriptor, 
	 * see {@link com.arosbio.cheminf.descriptors.ChemDescriptor#hasFixedLength() hasFixedLength()}) - in which case that descriptor will be placed
	 * last in the list. <b>Note:</b> it is not allowed to have more than one descriptor of non-fixed length. 
	 * @param descriptors Collection of {@link com.arosbio.cheminf.descriptors.ChemDescriptor Descriptors}
	 */
	public ChemDataset(Collection<ChemDescriptor> descriptors) {
		super();
		setDescriptorsCorrectingOrdering(descriptors);
	}

	public ChemDataset clone(){
		LOGGER.debug("Cloning ChemDataset");
		// Clone descriptors  
		List<ChemDescriptor> desc = new ArrayList<>();
		for (ChemDescriptor d : descriptors) {
			LOGGER.debug("Cloning descriptor of type: {}", d.getName());
			desc.add(d.clone());
		}
		ChemDataset clone = new ChemDataset(desc);

		// Init descriptors and clone data if not empty
		if (!isEmpty()) {
			// Initialize descriptors in case data is there - so the features will not be messed up
			clone.initializeDescriptors();
			// Deep copy of data 
			clone.setData(super.clone());
		}

		// Copy settings
		clone.minimumHAC = minimumHAC;
		clone.numParsingFailuresAllowed = numParsingFailuresAllowed;
		clone.keepMolRef = keepMolRef;
		if (textualLabels != null)
			clone.textualLabels = textualLabels.clone();
		clone.property = property;
		clone.requires3D = requires3D;

		return clone;
	}

	// ---------------------------------------------------------------------
	// GETTERS AND SETTERS
	// ---------------------------------------------------------------------

	/**
	 * Get a list of feature names, that counters for potential transformations 
	 * performed on this problem.
	 * @param includeSignatures If signatures should be included, which can potentially lead to a very large list
	 * @return a list of all feature names
	 */
	public List<String> getFeatureNames(boolean includeSignatures){
		if (descriptors.isEmpty())
			return new ArrayList<>();

		if (includeSignatures) {
			if (featureNamesIncludingSignatures == null) {
				// First time this has been called - set up the list!
				forceRecalculateFeatureNamesIncludingSignatures();
			}
			return featureNamesIncludingSignatures;
		} else {
			if (featureNamesExcludingSignatures == null) {
				// First time this has been called - set up the list!
				forceRecalculateFeatureNames();
			}
			return featureNamesExcludingSignatures;
		}
	}

	private void forceRecalculateFeatureNamesIncludingSignatures() {
		List<String> tmp = new ArrayList<>();

		for (ChemDescriptor d : descriptors) {
			tmp.addAll(d.getFeatureNames());
		}
		if (tmp.isEmpty()) {
			featureNamesIncludingSignatures = ImmutableList.of();
			return;
		}

		// Update indices based on transformers
		List<Transformer> trans = getTransformers();
		if (trans != null && !trans.isEmpty()) {
			for (Transformer t : trans) {
				if (t instanceof FeatureSelector) {
					List<Integer> toRm =new ArrayList<>(((FeatureSelector) t).getFeatureIndicesToRemove());
					if (toRm.isEmpty())
						continue;
					// Sort it in reverse order - to remove from the end->start (otherwise indices change)
					Collections.sort(toRm, Collections.reverseOrder());
					for (int index : toRm) {
						tmp.remove(index);
					}
				}
			}
		}
		featureNamesIncludingSignatures = ImmutableList.copyOf(tmp);
	}

	private void forceRecalculateFeatureNames() {
		List<String> tmp = new ArrayList<>();

		for (ChemDescriptor d : descriptors) {
			if (d instanceof SignaturesDescriptor)
				continue; // Skip the signatures!
			tmp.addAll(d.getFeatureNames());
		}

		if (tmp.isEmpty()) {
			featureNamesExcludingSignatures = new ArrayList<>();
			return;
		}


		// Update indices based on transformers
		List<Transformer> trans = getTransformers();
		if (trans != null && !trans.isEmpty()) {
			for (Transformer t : trans) {
				if (t instanceof FeatureSelector) {
					List<Integer> toRm =new ArrayList<>(((FeatureSelector) t).getFeatureIndicesToRemove());
					if (toRm.isEmpty())
						continue;
					// Sort it in reverse order - to remove from the end->start (otherwise indices change)
					Collections.sort(toRm, Collections.reverseOrder());
					for (int index : toRm) {
						if (index < tmp.size())
							tmp.remove(index);
					}
				}
			}
		}
		featureNamesExcludingSignatures = ImmutableList.copyOf(tmp);
	}

	/**
	 * Get the starting feature index for the signatures descriptor (used when generating 
	 * images that only use signatures info for the depiction). Returns -1 if no signatures descriptor
	 * is present
	 * @return the starting feature index of the signatures descriptor, or -1 if no signatures descriptor
	 */
	public int getSignaturesDescriptorStartIndex() {
		if (descriptors.size() == 1 && descriptors.get(0) instanceof SignaturesDescriptor)
			return 0; // fast return to skip computations for the standard case
		int index = 0;
		boolean hasSignDescr = false;
		for (ChemDescriptor d : descriptors) {
			if (d instanceof SignaturesDescriptor) {
				hasSignDescr = true;
				break;
			}
			else 
				index += d.getLength();
		}
		if (!hasSignDescr)
			return -1; // No signatures descriptor

		List<Transformer> trans = getTransformers();
		if (trans != null && !trans.isEmpty()) {
			for (Transformer t : trans) {
				if (t instanceof FeatureSelector) {
					index -= CollectionUtils.countValuesSmallerThan(((FeatureSelector) t).getFeatureIndicesToRemove(), index);
				}
			}
		}
		return index;
	}



	public List<ChemDescriptor> getDescriptors(){
		return descriptors;
	}

	public ChemDataset setDescriptors(Collection<ChemDescriptor> descriptors) 
			throws IllegalArgumentException {
		setDescriptorsCorrectingOrdering(descriptors);
		return this;
	}

	public ChemDataset setDescriptors(ChemDescriptor...descriptors) {
		setDescriptors(Arrays.asList(descriptors));
		return this;
	}

	private void setDescriptorsCorrectingOrdering(Collection<ChemDescriptor> descriptors) {
		this.descriptors = new ArrayList<>();
		ChemDescriptor variableLengthDesc = null;
		for (ChemDescriptor d : descriptors) {
			// If variable length
			if (! d.hasFixedLength()) {
				if (variableLengthDesc != null) {
					throw new IllegalArgumentException("Only allowed to have a single descriptor that generates a non-fixed number of attributes");
				} else {
					variableLengthDesc = d;
				}
			} else {
				// Otherwise just add it to the list
				this.descriptors.add(d);
			}
		}

		// put the variable length descriptor in the end!
		if (variableLengthDesc != null) {
			this.descriptors.add(variableLengthDesc);
		}

		LOGGER.debug("Updated ChemDataset descriptors with following ones: {}", this.descriptors);

	}

	/**
	 * setter for setting the Minimum Heavy Atom Count (HAC) required
	 * for molecules to be added to the dataset. The default is 5.
	 * @param hac the new HAC to use
	 * @return The reference to the current ChemDataset
	 */
	public ChemDataset setMinHAC(int hac) {
		this.minimumHAC = hac;
		return this;
	}

	/**
	 * Getter for the Minium Heavy Atom Count (HAC) required
	 * for molecules to be added to the dataset. The default is 5.
	 * @return the current minimum HAC
	 */
	public int getMinHAC() {
		return minimumHAC;
	}

	public ChemDataset setKeepMolRef(boolean keep) {
		this.keepMolRef = keep;
		return this;
	}
	/**
	 * Controls the number of allowed failures when loading data using any of the <code>add(..)</code> methods. 
	 * These failures can be due to CDK configuration issues, {@link #setMinHAC(int) HAC} or descriptor calculation issues.
	 * Note that this only applies to the current level of processing, any up-stream processing (i.e. reading molecules from
	 * storage) needs to be handled up-stream. The parsing will stop with an {@link EarlyLoadingStopException} once passed the 
	 * <code>numAllowed</code> numbers of failures. If a number less than 0 is given,
	 * there is no early termination and the loading will continue until completion. 
	 * @param numAllowed The maximum allowed number of failures before terminating parsing of new molecules. Or <code>-1</code> for no early stopping
	 */
	public void setNumLoadingFailuresAllowed(int numAllowed) {
		this.numParsingFailuresAllowed = numAllowed;
	}


	public boolean isReady() {
		// Must have at least one descriptor
		if (descriptors == null && descriptors.isEmpty())
			return false;

		for (ChemDescriptor d: descriptors) {
			if (!d.isReady())
				return false;
		}

		if (requires3D == null) {
			initializeDescriptors();
		}

		return true;
	}

	public void initializeDescriptors() {
		boolean tmp3D = false;
		for (ChemDescriptor d: descriptors) {
			d.initialize();
			if ( !tmp3D )
				tmp3D = d.requires3DCoordinates();
		}
		requires3D = tmp3D;
		LOGGER.debug("Initialized all descriptors");
	}

	public boolean isInitialized() {
		return requires3D != null;
	}

	public int getNumAttributes() {
		// If the list is not empty - we need to take the transformations into account
		if (!getTransformers().isEmpty()) {
			return super.getNumAttributes();
		}
		// Else we simply sum up the the lengths of all descriptors 
		int count = 0;
		for (ChemDescriptor d : descriptors) {
			count += d.getLength();
		}
		return count;
	}

	public Map<Double, Integer> getLabelFrequencies(){
		Map<Double, Integer> map = new HashMap<>();
		if (!getDataset().isEmpty())
			update(map, getDataset().getLabelFrequencies());
		if (!getModelingExclusiveDataset().isEmpty())
			update(map, getModelingExclusiveDataset().getLabelFrequencies());
		if (!getCalibrationExclusiveDataset().isEmpty())
			update(map, getCalibrationExclusiveDataset().getLabelFrequencies());
		return map;
	}

	/**
	 * Update <code>freq</code> with the data in <code>freq2</code>, this is done in-place
	 * @param freq Map to be updated
	 * @param freq2 Data to add to <code>freq</code>
	 */
	private static void update(Map<Double,Integer> freq, Map<Double,Integer> freq2) {
		for (Map.Entry<Double, Integer> ent: freq2.entrySet()) {
			freq.put(ent.getKey(), freq.getOrDefault(ent.getKey(), 0) + ent.getValue());
		}
	}

	/**
	 * The property that is/should be modeled, e.g. the response value
	 * @return the property or <code>null</code> if not set
	 */
	public String getProperty() {
		return property;
	}
	/**
	 * Set the property that should be modeled, this is done automatically
	 * by the {@code append(..)} methods that takes a parameter {@code property}
	 * in which case the current value is overwritten
	 * @param property the property that should be modeled
	 */
	public void setProperty(String property) {
		this.property = property;
	}

	public void setData(Dataset data) throws IllegalArgumentException {
		if (data == null)
			throw new IllegalArgumentException("The new problem cannot be null");
		withDataset(data.getDataset());
		withModelingExclusiveDataset(data.getModelingExclusiveDataset());
		withCalibrationExclusiveDataset(data.getCalibrationExclusiveDataset());
		if (!data.getTransformers().isEmpty())
			setTransformers(data.getTransformers());
	}

	public NamedLabels getTextualLabels() {
		return textualLabels;
	}

	public void setTextualLabels(NamedLabels textualLabels) {
		this.textualLabels = textualLabels;
	}

	public void apply(Transformer transformer) {
		featureNamesExcludingSignatures = null;
		featureNamesIncludingSignatures = null;
		super.apply(transformer);
	}

	public void apply(Transformer transformer, RecordType fitUsing) {
		featureNamesExcludingSignatures = null;
		featureNamesIncludingSignatures = null;
		super.apply(transformer, fitUsing);
	}

	public void apply(Transformer... transformers) {
		featureNamesExcludingSignatures = null;
		featureNamesIncludingSignatures = null;
		for (Transformer t : transformers)
			super.apply(t);
	}

	public void apply(List<Transformer> transformers) {
		featureNamesExcludingSignatures = null;
		featureNamesIncludingSignatures = null;
		super.apply(transformers);
	}

	public void apply(List<Transformer> transformers, RecordType fitUsing) {
		featureNamesExcludingSignatures = null;
		featureNamesIncludingSignatures = null;
		super.apply(transformers,fitUsing);
	}

	// ---------------------------------------------------------------------
	// UTILITY METHODS
	// ---------------------------------------------------------------------

	/**
	 * {@link calculateDescriptorsAndUpdate} will call calculate all descriptors, potentially updating any variable arity descriptor generators underneath
	 * and return a FeatureVector that should make up the next row in the dataset (together with it's label)
	 * @param mol molecule - should have been processed using {@link #prepareMol(IAtomContainer)} before
	 * @return a FeatureVector for the molecule
	 * @throws CDKException
	 */
	private FeatureVector calculateDescriptorsAndUpdate(IAtomContainer mol) 
			throws IllegalStateException, DescriptorCalcException {

		List<SparseFeature> features = new ArrayList<>();

		int featIndex = 0;
		boolean addedAtLeastOne = false;
		for (ChemDescriptor d : descriptors) {
			try {
				List<SparseFeature> currDesc = d.calculateDescriptorsAndUpdate(mol);

				for (SparseFeature f: currDesc) {
					f.shiftIndex(featIndex);
				}
				features.addAll(currDesc);
				addedAtLeastOne = true;
			} catch (Exception e) {
				LOGGER.debug("Failed computing attributes using descriptor {} - will set these to MissingValueFeature and check if others succeed",
						d.getName(),e);
				// Fill up with the correct number of missing-value-features
				for (int i=0; i<d.getLength(); i++) {
					features.add(new MissingValueFeature(i+featIndex));
				}
			}

			featIndex += d.getLength();
		}

		if (!addedAtLeastOne) {
			LOGGER.debug("Could not calculate any descriptors for molecule - failing it");
			throw new DescriptorCalcException("Failed computing descriptors");
		}

		return transform(new SparseVector(features));
	}

	/**
	 * Uses no validation - common logic for processing a single input molecule, including the following steps;
	 * HAC-check, {@link #prepareMol(IAtomContainer)}, descriptor-calc, transformation(s). Corresponds to the 
	 * {@link #doAdd(IAtomContainer, double, RecordType)} which does the same steps but includes the record in the
	 * ChemDataset instance and uses the {@link ChemDescriptor#calculateDescriptorsAndUpdate(IAtomContainer)} instead
	 * of the {@link ChemDescriptor#calculateDescriptors(IAtomContainer)} which this method uses
	 * @param mol molecule, not yet processed
	 * @return the {@link FeatureVector} of the mol, after all transformations 
	 */
	private FeatureVector convert2FeatVector(IAtomContainer mol) 
			throws NullPointerException, HACTooLowFail, CDKException, DescriptorCalcException {
		Objects.requireNonNull(mol, "Molecule cannot be null");

		if (mol.getAtomCount()<minimumHAC) {
			throw new HACTooLowFail(mol.getAtomCount(), minimumHAC);
		}

		// Perceive aromaticity
		mol = prepareMol(mol);

		List<SparseFeature> features = new ArrayList<>();

		int featIndexOffset = 0; 
		boolean addedAtLeastOne = false;
		for (ChemDescriptor d : descriptors) {
			try {
				List<SparseFeature> currDesc = d.calculateDescriptors(mol);

				for (SparseFeature f: currDesc) {
					f.shiftIndex(featIndexOffset);
				}
				features.addAll(currDesc);
				addedAtLeastOne = true;
			} catch (Exception e) {
				LOGGER.debug("Failed computing attributes using descriptor {} - will set these to MissingValueFeature and check if others succeed",
						d.getName(),e);
				// Fill up with the correct number of missing-value-features
				for (int i=0; i<d.getLength(); i++) {
					features.add(new MissingValueFeature(i+featIndexOffset));
				}
			}
			featIndexOffset += d.getLength();
		}

		if (!addedAtLeastOne) {
			LOGGER.debug("Could not calculate any descriptors for molecule - failing it");
			throw new DescriptorCalcException("Failed computing descriptors");
		}

		// Apply transformers if any
		return transform(new SparseVector(features));

	}

	/**
	 * Convert an {@link IAtomContainer} to a {@link FeatureVector} using the current state of this ChemDataset (descriptors + transformers)
	 * @param mol the molecule to convert
	 * @return A FeatureVector for the given molecule
	 * @throws CDKException If there is a problem configuring the molecule
	 * @throws IllegalStateException In case the {@link ChemDataset} do not contain any descriptors
	 */
	public FeatureVector convertToFeatureVector(IAtomContainer mol) 
			throws CDKException, IllegalStateException {
		if (! isReady()) {
			throw new IllegalStateException("Descriptors not initialized");
		}

		return convert2FeatVector(mol);
	}

	public Pair<List<FeatureVector>,DescriptorCalcInfo> convertToFeatureVector(List<IAtomContainer> mol) 
			throws CDKException, IllegalStateException {
		LOGGER.trace("Converting a list of mols into a List of FeatureVectors");

		return convertToFeatureVector(mol.iterator());
	}

	public Pair<List<FeatureVector>,DescriptorCalcInfo> convertToFeatureVector(Iterator<IAtomContainer> mols)
			throws CDKException, IllegalStateException {
		if (! isReady()) {
			throw new IllegalStateException("Descriptors not initialized");
		}

		LOGGER.trace("Converting an iterator of mols into a List of FeatureVectors");
		List<FeatureVector> matrix = new ArrayList<>();

		List<FailedRecord> failures = new ArrayList<>();

		int cdkFail=0, hacFail=0, descFail=0;
		int index=0;
		IAtomContainer mol = null;
		int i=0; // loop count (i.e. index based on this iterator)
		while (mols.hasNext()){
			mol = mols.next();

			index = i; // If no previous index found (set on the IAtomContainer) - use the loop-count

			// Figure out the record index - use predefined or the new index in this method
			if (CPSignMolProperties.hasRecordIndex(mol)) {
				try {
					index = TypeUtils.asInt(CPSignMolProperties.getRecordIndex(mol));
				} catch (Exception e) {}
			}

			try {
				matrix.add(convert2FeatVector(mol));
			}
			catch (HACTooLowFail hacExcept) {
				LOGGER.debug("Skipped molecule due to less than {} HAC, had {}",
						minimumHAC, hacExcept.count);
				hacFail++;
				failures.add(new FailedRecord(index).setID(mol.getID()).setReason(hacExcept.getMessage()));
			} catch (DescriptorCalcException e) {
				LOGGER.debug("Failed molecule with index {} while computing descriptors", index, e);
				descFail++;
				failures.add(new FailedRecord(index).setReason("Failed computing descriptors: " + getShortErrMsg(e)));
			} catch (CDKException e) {
				LOGGER.debug("Failed record at index {} when configuring using CDK",index, e);
				cdkFail++;
				failures.add(new FailedRecord(index).setReason("Could not configure molecule: " + getShortErrMsg(e)));
			}

			i++;
		}

		LOGGER.debug("Successfully converted {} molecules to FeatureVectors", matrix.size());

		if (! failures.isEmpty()){
			LOGGER.debug("Failed converting the following molecules to Features Vectors (starting from index 0): {}", failures);
		}

		return ImmutablePair.of(matrix,new DescriptorCalcInfo(matrix.size(), cdkFail, hacFail, descFail).setFailedRecords(failures));

	}

	/**
	 * Checks if two {@link ChemDataset} are identical, down to the order of 
	 * individual records and list of signatures, start/end height etc
	 * @param o the {@link Object} to compare current object with
	 * @return <code>true</code> if they are identical, otherwise <code>false</code> 
	 */
	@Override
	public boolean equals(Object o){
		LOGGER.debug("Comparing two ChemDatasets");

		if(o == this){ // Same object!
			return true;
		}

		if(!( o instanceof ChemDataset)){
			LOGGER.debug("The other object is not a ChemDataset");
			return false;
		}

		ChemDataset other = (ChemDataset) o;

		// Compare descriptors
		if (! equalDescriptors(other))
			return false;

		// Lastly - Check the Dataset (dataset + y-values)
		return super.equals(other);

	}

	private static boolean equals(ChemDescriptor d1, ChemDescriptor d2) {
		if (d1 == d2)
			return true;
		if (TypeUtils.overridesEquals(d1)) {
			return d1.equals(d2);
		} else if (TypeUtils.overridesEquals(d2)) {
			return d2.equals(d1);
		} else {
			return d1.getClass() == d2.getClass() && d1.getProperties().equals(d2.getProperties());
		}
	}

	public boolean equalDescriptors(ChemDataset other){
		if (other == this)
			return true;

		if (other.descriptors.size() != descriptors.size()) {
			LOGGER.debug("Different number of descriptors");
			return false;
		}

		for (int i=0; i< descriptors.size(); i++) {
			if (! equals(descriptors.get(i), other.descriptors.get(i))){
				LOGGER.debug("ChemDescriptor %s did not match",i);
				return false;
			}
		}

		return true;
	}




	// ---------------------------------------------------------------------
	// LOADING DATA
	// ---------------------------------------------------------------------

	public DescriptorCalcInfo add(Iterator<IAtomContainer> data, 
			String property, 
			NamedLabels labels) 
					throws IllegalStateException, IllegalArgumentException, EarlyLoadingStopException {
		DescriptorCalcInfo info = add(MolAndActivityConverter.classificationConverter(data, property, labels));
		this.textualLabels = labels.clone();
		this.property = property;
		return info;
	}

	public DescriptorCalcInfo add(Iterator<IAtomContainer> data, 
			String property) 
					throws IllegalStateException, IllegalArgumentException, EarlyLoadingStopException {
		DescriptorCalcInfo info = add(MolAndActivityConverter.regressionConverter(data, property));
		this.property = property;
		return info;
	}

	public DescriptorCalcInfo add(Iterator<IAtomContainer> data, 
			String property, 
			NamedLabels labels, 
			RecordType type) 
					throws IllegalStateException, IllegalArgumentException, EarlyLoadingStopException {
		DescriptorCalcInfo info = add(MolAndActivityConverter.classificationConverter(data, property, labels), type);
		this.textualLabels = labels.clone();
		this.property = property;
		return info;
	}

	public DescriptorCalcInfo add(Iterator<IAtomContainer> data, 
			String property, RecordType type) 
					throws IllegalStateException, IllegalArgumentException, EarlyLoadingStopException {
		DescriptorCalcInfo info = add(MolAndActivityConverter.regressionConverter(data, property), type);
		this.property = property;
		return info;
	}

	public DescriptorCalcInfo add(Iterator<Pair<IAtomContainer, Double>> data) 
			throws IllegalStateException, IllegalArgumentException, EarlyLoadingStopException {
		return add(data, Dataset.RecordType.NORMAL);
	}


	/**
	 * Adds data to the {@link ChemDataset} using an {@link Iterator} over a {@link Pair} with {@link IAtomContainer} and the corresponding <code>Double</code> value.
	 * If it's a classification problem, the <code>Double</code> values should be one of two values (one value for each class).
	 * @param data An {@link Iterator} with molecules and activities
	 * @param type Specifies which dataset the records should be added to (default is model and calibrate)
	 * @return DescriptorCalcInfo with information about how many compounds was added and possibly failed compounds
	 * @throws IllegalStateException If the Descriptors are not initialized yet
	 * @throws IllegalArgumentException If there was no valid molecules that could be added to this {@link ChemDataset}
	 * @throws EarlyLoadingStopException Stops parsing once failed too many recods, {@link #setNumLoadingFailuresAllowed(int)}
	 */
	public DescriptorCalcInfo add(Iterator<Pair<IAtomContainer, Double>> data, 
			RecordType type) 
					throws IllegalStateException, IllegalArgumentException, EarlyLoadingStopException {
		return add(data, type, 0);
	}

	/**
	 * Builds up the {@link ChemDataset} using an {@link Iterator} over a {@link Pair} with {@link IAtomContainer} and the corresponding <code>Double</code> value.
	 * If it's a classification problem, the <code>Double</code> values should be one of two values (one value for each class).
	 * @param data An {@link Iterator} with molecules and activities
	 * @param type Specifies which dataset the records should be added to (default is model and calibrate)
	 * @param recordStartIndex a specific index to start giving the record (default is 0). Ignored if molecules already has a property cpsign:record-index
	 * @return DescriptorCalcInfo with information about how many compounds was added and possibly failed compounds
	 * @throws IllegalStateException If the Descriptors are not initialized yet
	 * @throws IllegalArgumentException If there was no valid molecules that could be added to this {@link ChemDataset}
	 * @throws EarlyLoadingStopException Stops parsing once failed too many records, {@link #setNumLoadingFailuresAllowed(int)}
	 */
	public DescriptorCalcInfo add(Iterator<Pair<IAtomContainer, Double>> data, 
			RecordType type, int recordStartIndex) 
					throws IllegalStateException, IllegalArgumentException, EarlyLoadingStopException {

		if (!isReady()) {
			throw new IllegalStateException("Descriptors not initialized yet!");
		}

		int numAdded=0, skippedMolsCDKErr=0, skippedMolsHAC = 0, skippedMolsDescriptorCalc=0;
		int recIndex = recordStartIndex-1; // -1 to start at 0 in the while-loop
		// Failures encountered at this level of processing
		List<FailedRecord> failures = new ArrayList<>();

		while (data.hasNext()) {

			if (numParsingFailuresAllowed >=0 && (skippedMolsCDKErr+skippedMolsDescriptorCalc+skippedMolsHAC) > numParsingFailuresAllowed) {
				LOGGER.debug("Early stopping due to encountered too many failed records: cdk-err: {}, HAC: {}, desc-calc: {}",
						skippedMolsCDKErr,skippedMolsHAC,skippedMolsDescriptorCalc);

				throw new EarlyLoadingStopException(failures);
			}

			recIndex ++;
			int index = recIndex;
			Pair<IAtomContainer, Double> molAndActivity = data.next();
			IAtomContainer mol = molAndActivity.getLeft();
			Double activityValue = molAndActivity.getRight();

			try {
				// Figure out the record index - use predefined or the new index in this method
				if (CPSignMolProperties.hasRecordIndex(mol)) {
					try {
						index = TypeUtils.asInt(CPSignMolProperties.getRecordIndex(mol));
					} catch (Exception e) {}
				}

				doAdd(mol,activityValue,type);

				numAdded++;
			} catch (HACTooLowFail hacExcept) {
				LOGGER.debug("Skipped molecule due to less than {} HAC, had {}",
						minimumHAC, hacExcept.count);
				skippedMolsHAC++;
				failures.add(new FailedRecord(index).setID(mol.getID()).setReason(hacExcept.getMessage()));
			} catch (DescriptorCalcException e) {
				LOGGER.debug("Failed molecule with index {} while computing descriptors", index, e);
				skippedMolsDescriptorCalc++;
				failures.add(new FailedRecord(index).setReason("Failed computing descriptors: " + getShortErrMsg(e)));
			} catch (CDKException e) {
				LOGGER.debug("Failed record at index {} when configuring using CDK",index, e);
				skippedMolsCDKErr++;
				failures.add(new FailedRecord(index).setReason("Could not configure molecule: " + getShortErrMsg(e)));
			}

		}

		if (numAdded <= 0) {
			LOGGER.debug("No molecules successfully parsed from iterator, skipped {} records", skippedMolsCDKErr);
			throw new IllegalArgumentException("No molecules was successfully added");
		}

		// Log some info
		StringBuilder output = new StringBuilder();
		output.append("Parse of molsIterator done - added ");
		output.append(numAdded);
		output.append(" new records");

		if (skippedMolsCDKErr > 0){
			output.append(". Skipped ");
			output.append(skippedMolsCDKErr); 
			output.append(" molecules due to CDK-error applying aromaticity.");
		}
		if (skippedMolsHAC > 0) {
			output.append(". Skipped ");
			output.append(skippedMolsHAC);
			output.append(" molecules due to too small molecules, required min HAC: " + minimumHAC);
		}
		if (skippedMolsDescriptorCalc > 0) {
			output.append(". Skipped ");
			output.append(skippedMolsDescriptorCalc);
			output.append(" molecules due to too failures when computing descriptors");
		}
		LOGGER.debug(output.toString());

		if (data instanceof MolAndActivityConverter) {
			if (property == null || property.isEmpty()) {
				this.property = ((MolAndActivityConverter) data).getPropertyNameForActivity();
			}
			if ( ((MolAndActivityConverter)data).getClassLabels() != null) {
				this.textualLabels = ((MolAndActivityConverter)data).getClassLabels();
			}
		}

		if (data instanceof AutoCloseable) {
			try {
				((AutoCloseable) data).close();
			} catch(Exception e) {
				LOGGER.debug("Failed closing molsIterator though instanceof AutoClosable",e);
			}
		}
		return new DescriptorCalcInfo(numAdded, skippedMolsCDKErr, skippedMolsHAC, skippedMolsDescriptorCalc).setFailedRecords(failures);
	}

	public void add(IAtomContainer molecule, double label) 
			throws IllegalArgumentException, CDKException {
		add(molecule, label, RecordType.NORMAL);
	}

	public void add(IAtomContainer molecule, double label, RecordType type) 
			throws IllegalArgumentException, CDKException {

		if (!isReady()) {
			throw new IllegalStateException("Descriptors not initialized yet!");
		}
		doAdd(molecule,label,type);

	}
	private void doAdd(IAtomContainer mol, double label, RecordType type) 
			throws HACTooLowFail, CDKException, DescriptorCalcException {
		Objects.requireNonNull(mol, "Mol cannot be null");

		if (mol == null || mol.getAtomCount()<minimumHAC) {
			throw new HACTooLowFail(mol.getAtomCount(), minimumHAC);
		}

		IAtomContainer preppedMol = prepareMol(mol);

		FeatureVector features = null;
		try {
			features = calculateDescriptorsAndUpdate(preppedMol);
		} catch (DescriptorCalcException descErr) {
			// Pass this along
			throw descErr;
		} catch (Exception e) {
			// wrap generic exception in descriptor-calculation-exception
			throw new DescriptorCalcException(e);
		}

		if (keepMolRef) {
			getDataset(type).add(new DataRecordWithRef(label, features, preppedMol));
		} else {
			getDataset(type).add(new DataRecord(label, features));
		}
	}

	static class HACTooLowFail extends IllegalArgumentException {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		private final int count;

		public HACTooLowFail(int count, int minAllowed) {
			super(String.format("Heavy Atom Count {%s} too low, min required {%s}",count,minAllowed));
			this.count = count;
		}
		public HACTooLowFail(int count) {
			super(String.format("Heavy Atom Count {%s} too low",count));
			this.count = count;
		}
		public int getHAC() {
			return count;
		}
	}

	private IAtomContainer prepareMol(IAtomContainer mol) 
			throws CDKException {
		CDKConfigureAtomContainer.configMolecule(mol);
		if (requires3D) {
			try {
				return CDKConfigureAtomContainer.calculate3DCoordinates(mol, false);
			} catch (CDKException e) {
				LOGGER.debug("Failed generating 3D coordinates for molecule");
				throw new CDKException("Molecule did not have 3D coordinates - and these were not supplied and/or could not be generated automatically");
			}
		} else {
			return mol;
		}
	}

	@Override
	public Map<String,Object> getProperties() {
		// Do not use the getProperties in super (Dataset) class - faster to check num-features using size of signatures-list
		Map<String,Object> props = new HashMap<>();
		props.put(PropertyNameSettings.NUM_FEATURES_KEY, getNumAttributes());
		props.put(PropertyNameSettings.NUM_OBSERVATIONS_KEY, getNumRecords());
		props.put(PropertyNameSettings.MODELING_ENDPOINT_KEY, property);
		if (textualLabels != null && textualLabels.size() > 0){
			props.put(PropertyNameSettings.CLASS_LABELS_LIST_KEY, textualLabels.getReverseLabels());
			props.put(PropertyNameSettings.IS_CLASSIFICATION_KEY, true);
		}else {
			props.put(PropertyNameSettings.IS_CLASSIFICATION_KEY, false);
		}

		if (!descriptors.isEmpty()) {
			List<String> descListString = new ArrayList<>();
			for (ChemDescriptor d : descriptors) {
				descListString.add(d.getName());
			}
			props.put(PropertyNameSettings.DESCRIPTORS_LIST_KEY, descListString);
		}

		return props;
	}


	public String toString() {
		return String.format("Chemical Dataset with %s descriptors and %s records", 
				getNumAttributes(),getNumRecords());
	}

	// ---------------------------------------------------------------------
	// SAVING DATA
	// ---------------------------------------------------------------------

	private void saveDescriptorsToSink(DataSink sink, String basePath, EncryptionSpecification spec) 
			throws IOException, InvalidKeyException, IllegalStateException {

		if (descriptors==null || descriptors.isEmpty())
			throw new IllegalStateException("Chem dataset is empty - no descriptors to write");

		// create the directory
		String baseDir = DataIOUtils.appendTrailingDash(
				DataIOUtils.createBaseDirectory(sink, basePath, DESCRIPTORS_DIRECTORY_NAME));
		LOGGER.debug("Saving descriptors to path={}",baseDir);

		// write meta.params
		Map<String, Object> params = getProperties();
		try (OutputStream metaStream = sink.getOutputStream(baseDir+DESCRIPTORS_META_FILE_NAME)){
			MetaFileUtils.writePropertiesToStream(metaStream, params);
		}
		sink.closeEntry();
		LOGGER.debug("written descriptor properties to jar: {}", params);

		// write all descriptors
		for (int i=0; i<descriptors.size(); i++) {
			ChemDescriptor d = descriptors.get(i);
			d.saveDescriptorToSink(sink, baseDir + DESCRIPTOR_INDIVIDUAL_FILE_NAME+i, spec);
			LOGGER.debug("Saved descriptor {}", d.getName());
		}

		LOGGER.debug("written all descriptors to jar");

	}

	/**
	 * Saves descriptors, transformations and meta data/settings used for this instance.
	 * Does not save the data/records - use {@link #saveToDataSink(DataSink, String, EncryptionSpecification)} for that
	 * @param sink Where to save data
	 * @param path a path within the {@code sink}
	 * @param spec Encryption spec or {@code null}
	 * @throws IOException Issues with reading from the <code>sink</code>
	 * @throws InvalidKeyException In case descriptors cannot be decrypted from serialized format
	 * @throws IllegalStateException If no descriptors set in the current object - i.e. nothing to save
	 */
	public void saveStateExceptRecords(DataSink sink, String path, EncryptionSpecification spec) 
			throws IOException, InvalidKeyException, IllegalStateException {
		LOGGER.debug("Saving complete ChemDataset (except records) to sink - starting with descriptors");
		saveDescriptorsToSink(sink, path, spec);

		LOGGER.debug("Saving transformations to sink");
		super.saveTransformersToSink(sink, path);

		saveMetaData(sink, path);
		LOGGER.debug("Finished saving ChemDataset state");
	}

	/**
	 * Saves the entire data set, comprised of data, transformers and meta data
	 * @param sink Where to save data
	 * @param path a path within the {@code sink}
	 * @param spec Encryption spec or {@code null}
	 */
	@Override
	public void saveToDataSink(DataSink sink, String path, EncryptionSpecification spec) 
		throws IOException, InvalidKeyException, IllegalStateException{
		LOGGER.debug("Saving complete ChemDataset to sink - starting with descriptors");
		saveDescriptorsToSink(sink, path, spec);

		LOGGER.debug("Saving data + transformations to sink");
		super.saveToDataSink(sink, path, spec);

		saveMetaData(sink, path);

		LOGGER.debug("Finished saving ChemDataset to sink");
	}

	private static class MetaDataProps {
		private static final String MIN_HAC_PROP = "minHAC";
		private static final String MODEL_PROPERTY_PROP = "property";
		private static final String NUM_PARSE_FAIL_ALLOWED_PROP = "numParseFailAllowed";
		private static final String KEEP_REFS_PROP = "keepRefs";
		private static final String LABELS_PROP = "labels";
		private static final String REQ_3D_PROP = "require3D";
	}

	private void saveMetaData(DataSink sink, String basePath) 
			throws IOException {
		LOGGER.debug("Saving meta data");
		Map<String,Object> metaProps = new HashMap<>();
		metaProps.put(MetaDataProps.MIN_HAC_PROP, minimumHAC);
		metaProps.put(MetaDataProps.MODEL_PROPERTY_PROP, property);
		metaProps.put(MetaDataProps.NUM_PARSE_FAIL_ALLOWED_PROP, numParsingFailuresAllowed);
		metaProps.put(MetaDataProps.KEEP_REFS_PROP, keepMolRef);
		metaProps.put(MetaDataProps.LABELS_PROP, (textualLabels!=null? textualLabels.getReverseLabels() : null));
		if (requires3D != null)
			metaProps.put(MetaDataProps.REQ_3D_PROP, requires3D.booleanValue());
		try (OutputStream metaStream = sink.getOutputStream(DataIOUtils.appendTrailingDash(basePath)+META_FILE_NAME)){
			MetaFileUtils.writePropertiesToStream(metaStream, metaProps);
		}
		sink.closeEntry();
		LOGGER.debug("written ChemDataset properties to sink: {}", metaProps);
	}

	// ---------------------------------------------------------------------
	// LOADING DATA
	// ---------------------------------------------------------------------

	@Override
	public void loadFromDataSource(DataSource src, String path, EncryptionSpecification spec)
			throws IOException, IllegalArgumentException, InvalidKeyException {

		LOGGER.debug("Loading entire ChemDataset from path={}", path);
		loadDescriptorsFromSource(src, path, spec);

		LOGGER.debug("Trying to load data");
		try {
			super.loadFromDataSource(src, path, spec);
		} catch (Exception e) {
			LOGGER.debug("Failed loading data from source",e);
			throw new IOException(e.getMessage());
		}

		loadMetaData(src, path);
	}

	public void loadStateExceptRecords(DataSource src, String path, EncryptionSpecification spec)
		throws IOException, IllegalArgumentException, InvalidKeyException {
		LOGGER.debug("Loading ChemDataset state from path={}", path);
		loadDescriptorsFromSource(src, path, spec);
		
		super.loadTransformersFromSource(src);

		loadMetaData(src, path);
	}

	protected void loadDescriptorsFromSource(DataSource source, EncryptionSpecification spec) 
			throws IOException, InvalidKeyException {
		loadDescriptorsFromSource(source, null, spec);
	}

	protected void loadDescriptorsFromSource(DataSource source, String path, EncryptionSpecification spec) 
			throws IOException, InvalidKeyException {

		descriptors = new ArrayList<>();

		// Find the base directory for the Descriptors
		String descriptorsDir = DataIOUtils.appendTrailingDash(
				DataIOUtils.locateBasePath(source, path, DESCRIPTORS_DIRECTORY_NAME)); 
		LOGGER.debug("loading descriptors from path={}",descriptorsDir);

		// load the meta-data
		Map<String,Object> properties = null;
		try(
				InputStream metaDataStream = source.getInputStream(descriptorsDir+DESCRIPTORS_META_FILE_NAME);
				){
			properties = MetaFileUtils.readPropertiesFromStream(metaDataStream);
			LOGGER.debug("Props for ChemDataset={}",properties);

			if (properties.containsKey(PropertyNameSettings.MODELING_ENDPOINT_KEY))
				property = (String) properties.get(PropertyNameSettings.MODELING_ENDPOINT_KEY);

		} catch (IOException e){
			LOGGER.debug("Could not read the ChemDataset meta-file",e);
			throw e;
		}
		LOGGER.debug("Loaded descriptors meta-file");

		// Init the descriptors
		if (!properties.containsKey(PropertyNameSettings.DESCRIPTORS_LIST_KEY)) {
			LOGGER.debug("No descriptors list found in properties file, something is wronng");
			throw new IOException("Chemial dataset meta-file could not be read properly");
		}
		@SuppressWarnings("unchecked")
		List<String> descriptorsList = (List<String>) properties.get(PropertyNameSettings.DESCRIPTORS_LIST_KEY);

		for (int i=0; i<descriptorsList.size(); i++) {
			try {
				LOGGER.debug("Trying to load descriptor with index: {}", i);
				ChemDescriptor d = DescriptorFactory.getInstance().getDescriptorFuzzyMatch(descriptorsList.get(i));
				d.loadDescriptorFromSource(source, descriptorsDir + DESCRIPTOR_INDIVIDUAL_FILE_NAME+i, spec);
				descriptors.add(d);
				LOGGER.debug("Successfully loaded descriptor {}", d.getName());
			} catch (Exception e) {
				LOGGER.debug("failed loading descriptor!");
				throw new IOException("Failed loading descriptor with name: " + descriptorsList.get(i));
			}
		}

		LOGGER.debug("Loaded all descriptors from source");
	}

	@SuppressWarnings("unchecked")
	private static <T> T getOrDefault(Map<String,Object> map,String key, T defaultValue){
		try {
			Object v =  map.getOrDefault(key, defaultValue);
			if (defaultValue instanceof Integer){
				return (T) Integer.valueOf(TypeUtils.asInt(v));
			} else if (defaultValue instanceof Double){
				return (T) Double.valueOf(TypeUtils.asDouble(v));
			} else if (defaultValue instanceof Boolean){
				return (T) Boolean.valueOf(TypeUtils.asBoolean(v));
			}
		} catch (Exception e){}
		return defaultValue;
		
	}

	private void loadMetaData(DataSource src, String basePath) throws IOException {
		String metaPath = DataIOUtils.appendTrailingDash(basePath)+META_FILE_NAME;
		LOGGER.debug("Attempting to load meta data, from path={}",metaPath);

		if (src.hasEntry(metaPath)){
			try (InputStream metaStream = src.getInputStream(metaPath)){
				Map<String,Object> props = MetaFileUtils.readPropertiesFromStream(metaStream);
				LOGGER.debug("Loaded ChemDataset properties from stream: {}",props);
				minimumHAC = getOrDefault(props, MetaDataProps.MIN_HAC_PROP, minimumHAC); //key, defaultValue).asInt(props.getOrDefault(MetaDataProps.MIN_HAC_PROP, minimumHAC));
				property = getOrDefault(props, MetaDataProps.MODEL_PROPERTY_PROP, property); //props.getOrDefault(MetaDataProps.MODEL_PROPERTY_PROP, minimumHAC).toString();
				numParsingFailuresAllowed = getOrDefault(props, MetaDataProps.NUM_PARSE_FAIL_ALLOWED_PROP, numParsingFailuresAllowed); //TypeUtils.asInt(props.getOrDefault(MetaDataProps.NUM_PARSE_FAIL_ALLOWED_PROP, numParsingFailuresAllowed));
				keepMolRef = getOrDefault(props, MetaDataProps.KEEP_REFS_PROP, keepMolRef);
				if (textualLabels == null){
					Map<String,Integer> storedLabels = getOrDefault(props, MetaDataProps.LABELS_PROP, (Map<String,Integer>) null);
					if (storedLabels!=null){
						textualLabels = NamedLabels.fromReversedLabels(storedLabels);
					}
				}
				if (requires3D == null)
					requires3D = getOrDefault(props, MetaDataProps.REQ_3D_PROP, (Boolean)null);
				LOGGER.debug("Settings loaded from meta file");
			}
		} else {
			LOGGER.debug("No meta file existing - using default settings");
		}
		
	}

	// ---------------------------------------------------------------------
	// JOINING PROBLEMS
	// ---------------------------------------------------------------------

	public void join(ChemDataset other) throws IllegalArgumentException {
		if (! equalDescriptors(other)) {
			throw new IllegalArgumentException("Cannot join ChemDatasets, they have different descriptors");
		}

		super.join(other);
	}


	public void joinShallow(ChemDataset other) throws IllegalArgumentException {
		if (! equalDescriptors(other)) {
			throw new IllegalArgumentException("Cannot join ChemDatasets, they have different descriptors");
		}

		super.joinShallow(other);
	}
}

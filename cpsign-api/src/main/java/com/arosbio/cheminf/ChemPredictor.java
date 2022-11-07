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
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.commons.mixins.HasProperties;
import com.arosbio.data.Dataset.RecordType;
import com.arosbio.data.MissingDataException;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.io.ModelInfo;

public interface ChemPredictor extends HasProperties {
	
	public ChemPredictor withModelInfo(ModelInfo info);

	public ModelInfo getModelInfo();

	public String getDefaultModelName();

	/**
	 * Getter for the {@link com.arosbio.ml.interfaces.Predictor Predictor} used by this <code>ChemPredictor</code>
	 * @return the current {@link com.arosbio.ml.interfaces.Predictor Predictor}
	 */
	public Predictor getPredictor();
	
	/**
	 * Convenience method for checking if {@link com.arosbio.cheminf.descriptors.SignaturesDescriptor SignaturesDescriptor} is used by
	 * this instance
	 * @return {@code true} if signatures are used
	 */
	public boolean usesSignaturesDescriptor();
	
	/**
	 * Get a list of feature names, that counters for potential transformations 
	 * performed on this problem.
	 * @param includeSignatures If signatures should be included, which can potentially lead to a very large list
	 * @return a list of all feature names
	 */
	public List<String> getFeatureNames(boolean includeSignatures);
	
	public boolean checkIfDatasetsContainMissingFeatures();
	
	/**
	 * Getter for the {@link com.arosbio.cheminf.data.ChemDataset ChemDataset}
	 * @return the current {@link com.arosbio.cheminf.data.ChemDataset ChemDataset}
	 */
	public ChemDataset getDataset();
	
	/**
	 * Setter for the {@link com.arosbio.cheminf.data.ChemDataset ChemDataset}
	 * @param data the new {@link com.arosbio.cheminf.data.ChemDataset ChemDataset} 
	 */
	public void setDataset(ChemDataset data);
	
	/**
	 * Descriptors need initializations, this will be done automatically when using any of the loading methods, but
	 * here it can be done explicitly. Initialization freezes the descriptor parameters so things are not accidentally changed
	 * during the usage of a class and thus leading to errors in your workflow. 
	 */
	public void initializeDescriptors();
	
	/**
	 * Train the predictor with the loaded data
	 * @throws MissingDataException If there are features with missing values in the input data
	 * @throws IllegalArgumentException Invalid arguments set for the predictor
	 * @throws IllegalStateException Predictor not configured properly
	 */
	public void train() 
			throws MissingDataException, IllegalArgumentException, IllegalStateException;

	
	/**
	 * Predict the {@link com.arosbio.cheminf.SignificantSignature SignificantSignature} of a molecule
	 * @param mol The new molecule to predict 
	 * @return A {@link com.arosbio.cheminf.SignificantSignature SignificantSignature} with the result
	 * @throws CDKException if there is a problem configuring the <code>IAtomContainer</code> to apply aromaticity etc
	 * @throws IllegalArgumentException If {@code mol} is {@code null}
	 * @throws IllegalStateException If no signatures descriptor is used or models not trained
	 */
	public SignificantSignature predictSignificantSignature(IAtomContainer mol) 
			throws CDKException, IllegalStateException;
	
	/**
	 * Load molecules and compute signatures descriptors from an iterator that give a {@link org.apache.commons.lang3.tuple.Pair Pair} of <code>IAtomContainer</code> and the corresponding
	 * regression or classification value.
	 * <p>
	 * <b>IF CLASSIFICATION</b><br>
	 * Use two numeric values, i.e. <code>0.0</code> and <code>1.0</code>. Also make sure to set labels in the 
	 * {@link com.arosbio.cheminf.ChemClassifier ChemClassifier}
	 * or the {@link com.arosbio.cheminf.data.ChemDataset ChemDataset} classes by
	 * calling the method <code>setLabels</code>. 
	 * @param molsIterator An {@link java.util.Iterator Iterator} with a {@link org.apache.commons.lang3.tuple.Pair Pair} of a molecule and corresponding value
	 * @throws IllegalArgumentException If no molecules could be loaded successfully from the iterator
	 */
	public void addRecords(Iterator<Pair<IAtomContainer, Double>> molsIterator) 
			throws IllegalArgumentException;
	
	/**
	 * Load molecules and compute signatures descriptors from an iterator that give a {@link org.apache.commons.lang3.tuple.Pair Pair} of <code>IAtomContainer</code> and the corresponding
	 * regression or classification value.
	 * <p>
	 * <b>IF CLASSIFICATION</b><br>
	 * Use two numeric values, i.e. <code>0.0</code> and <code>1.0</code>. Also make sure to set labels in the 
	 * {@link com.arosbio.cheminf.ChemClassifier ChemClassifier}
	 * or the {@link com.arosbio.cheminf.data.ChemDataset ChemDataset} classes by
	 * calling the method <code>setLabels</code>. 
	 * @param molsIterator An {@link java.util.Iterator Iterator} with a {@link org.apache.commons.lang3.tuple.Pair Pair} of a molecule and corresponding value
	 * @param recordType A {@link com.arosbio.data.Dataset.RecordType RecordType} indicating what dataset the records should be added to
	 * @throws IllegalArgumentException If no molecules could be loaded successfully from the iterator
	 */
	public void addRecords(Iterator<Pair<IAtomContainer, Double>> molsIterator,
			RecordType recordType) 
			throws IllegalArgumentException;
	
	/**
	 * Getter for the low percentile (for computation of molecule gradients)
	 * @return the low percentile value
	 */
	public Double getLowPercentile();

	/**
	 * Setter for the low percentile (for computation of molecule gradients)
	 * @param low new low percentile value
	 */
	public void setLowPercentile(Double low);
	
	/**
	 * Getter for the high percentile (for computation of molecule gradients)
	 * @return the high percentile value
	 */
	public Double getHighPercentile();

	/**
	 * Setter for the high percentile (for computation of molecule gradients)
	 * @param high new high percentile value
	 */
	public void setHighPercentile(Double high);

	
	/**
	 * Checks whether the percentiles are valid, meaning both present and that the low and high
	 * value differ. This method can thus return <code>false</code> both in cases when 
	 * percentiles has not been computed at all and when the low and high values are the same.
	 * @return <code>true</code> in case computed and valid, <code>false</code> otherwise
	 */
	public boolean hasValidPercentiles();

	/**
	 * Computes the low and high percentiles (for computation of molecule gradients).
	 * This method is <b>very</b> time consuming, limit the number of molecules used 
	 * for the calculation by the <code>maxNumberMolsForPercentiles</code> parameter
	 * @param molIterator An iterator of <code>IAtomContainers</code>
	 * @param maxNumberMolsForPercentiles limit for number of molecules 
	 * @throws IllegalAccessException If predictor is not yet trained 
	 * @throws IOException Dataset reading the file
	 */
	public void computePercentiles(Iterator<IAtomContainer> molIterator, int maxNumberMolsForPercentiles) 
			throws IllegalAccessException, IOException;
	
	/**
	 * Computes the low and high percentiles (for computation of molecule gradients).
	 * This method is <b>very</b> time consuming. Uses the default number of molecules
	 * set to 1000. If you wish to save some computing time, use {@link #computePercentiles(Iterator, int)}
	 * to set a custom number of molecules to use.
	 * @param molIterator An iterator of <code>IAtomContainers</code>
	 * @throws IllegalAccessException If predictor is not yet trained 
	 * @throws IOException Dataset reading the file
	 */
	public void computePercentiles(Iterator<IAtomContainer> molIterator) 
			throws IllegalAccessException, IOException;

	/**
	 * Get the endpoint used either when loading data or for the trained predictor
	 * @return The endpoint used
	 */
	public String getProperty();
	
}

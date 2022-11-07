/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cheminf.descriptors;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.List;

import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.mixins.HasProperties;
import com.arosbio.commons.mixins.Named;
import com.arosbio.data.SparseFeature;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.DataSink;
import com.arosbio.io.DataSource;

public interface ChemDescriptor extends Named, HasProperties, Configurable {
	
	/**
	 * Potentially initializes underlying requirements and "freezes"
	 * the descriptor-parameters so no more changes are allowed - this to make sure
	 * that parameters are not tweaked midway which would lead to errors
	 */
	public void initialize();
	
	
	/**
	 * Check if the descriptor is ready - i.e. the {@link #initialize()} method
	 * has been called when that is required.
	 * @return {@code true} if the descriptor is ready, {@code false} otherwise
	 */
	public boolean isReady();
	
	/**
	 * Get the names for the ChemDescriptor. Note this is the names used for retrieving the
	 * correct descriptor-implementation, not the feature names that are produced
	 * @return the descriptor name(s)
	 */
	public String getName();
	
	/**
	 * Get the name of the feature(s) produced by this descriptor. 
	 * @return the feature name(s)
	 */
	public List<String> getFeatureNames();
	
	/**
	 * Specifies if the descriptor needs 3D coordinates for the molecules,
	 * in some cases CDK can generate these but will fail sometimes 
	 * @return {@code true} if 3D coordinates are required for this descriptor, {@code false} otherwise
	 */
	public boolean requires3DCoordinates();
	
	/**
	 * Create a shallow clone (i.e. no underlying data should be copied) of the current instance
	 * @return a shallow copy of the ChemDescriptor
	 */
	public ChemDescriptor clone();
	
	/**
	 * Check if the descriptor has a fixed number of features
	 * @return If the descriptor has a fixed number of features
	 */
	public boolean hasFixedLength();
	
	/**
	 * Get the number of descriptor attributes
	 * @return number of descriptor attributes
	 */
	public int getLength();
	
	/**
	 * Generate the descriptors for the given molecule, should return a list of
	 * sparse features with the first one with index 0. This method should <b>not make
	 * any changes to the descriptor</b>. I.e. this method is for generating descriptors
	 * for a molecule that should only be predicted, not generate any potential new descriptors.
	 * This is only relevant for descriptors like e.g. the Signatures descriptor, when the number of attributes/features
	 * will change when finding new descriptors. For fixed-numbered of attributes descriptors 
	 * this will not have any difference compared to the {@link #calculateDescriptorsAndUpdate(IAtomContainer)} method 
	 * @param molecule the molecule to compute descriptors for
	 * @return A List of SparseFeatures, should start with index 0!
	 * @throws IllegalStateException If the ChemDescriptor has not been initialized through {@link #initialize()} or loaded properly
	 * @throws DescriptorCalcException If there were an Exception when computing the descriptor 
	 */
	public List<SparseFeature> calculateDescriptors(IAtomContainer molecule) 
			throws DescriptorCalcException, IllegalStateException;
	
	/**
	 * Generate the descriptors for the given molecule and make potential updates 
	 * (e.g. when finding additional signatures for a non fixed-length descriptor)
	 * @param molecule the molecule to compute descriptors for
	 * @return A List of SparseFeatures, should start with index 0!
	 * @throws IllegalStateException If the ChemDescriptor has not been initialized through {@link #initialize()} or loaded properly
	 * @throws DescriptorCalcException If there were an Exception when computing the descriptor 
	 */
	public List<SparseFeature> calculateDescriptorsAndUpdate(IAtomContainer molecule) 
			throws DescriptorCalcException, IllegalStateException;
	
	public void saveDescriptorToSink(DataSink sink, String basePath, EncryptionSpecification spec) 
			throws IOException, InvalidKeyException, IllegalStateException;
	
	public void loadDescriptorFromSource(DataSource source, String path, EncryptionSpecification spec) 
			throws IOException, InvalidKeyException;
}

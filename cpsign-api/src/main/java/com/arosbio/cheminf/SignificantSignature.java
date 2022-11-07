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
import java.util.Set;

import com.arosbio.data.SparseFeature;

/**
 * The <code>SignificantSignature</code> object holds the result of predicting the gradient for a molecule.
 * This results in a significant signature if the SignaturesDescriptor is used. If other descriptors are used (or in addition to the SignaturesDescriptor)
 * the {@link #getAdditionalFeaturesGradient()} will return the gradient for all other features used.  
 * 
 * @author ola
 * @author staffan
 */
public class SignificantSignature {

	// Signature dependent results
	private final String signature;
	private final int height;
	private final Map<Integer, Double> molGradient;
	private final Set<Integer> atomsMatchingSignature;

	// Generic results
	private final Map<String, Double> additionalFeaturesGradient;
	private final List<SparseFeature> fullGradient;

	private SignificantSignature(
		String signature,
		int height,
		Map<Integer, Double> molGradient,
		Set<Integer> atomsMatchingSignature,
		Map<String, Double> additionalFeaturesGradient,
		List<SparseFeature> fullGradient){
		this.signature = signature;
		this.height = height;
		this.molGradient = molGradient;
		this.atomsMatchingSignature = atomsMatchingSignature;
		this.additionalFeaturesGradient = additionalFeaturesGradient;
		this.fullGradient = fullGradient;
	}

	public static class Builder {
		// Signature dependent results
		private String signature;
		private int height;
		private Map<Integer, Double> molGradient;
		private Set<Integer> atomsMatchingSignature;

		// Generic results
		private Map<String, Double> additionalFeaturesGradient;
		private List<SparseFeature> fullGradient;

		public Builder(List<SparseFeature> fullGradient){
			this.fullGradient = fullGradient;
		}

		public Builder signature(String signature){
			this.signature = signature;
			return this;
		}

		public Builder height(int h){
			this.height = h;
			return this;
		}

		public Builder atomContributions(Map<Integer,Double> gradient){
			this.molGradient = gradient;
			return this;
		}

		public Builder atoms(Set<Integer> atoms){
			this.atomsMatchingSignature = atoms;
			return this;
		}

		public Builder featureGradient(Map<String,Double> gradient){
			this.additionalFeaturesGradient = gradient;
			return this;
		}

		public SignificantSignature build(){
			return new SignificantSignature(
				signature, 
				height, 
				molGradient, 
				atomsMatchingSignature, 
				additionalFeaturesGradient, 
				fullGradient);
		}

	}

	/**
	 * Get the most significant signature of the prediction, if using the {@link com.arosbio.cheminf.descriptors.SignaturesDescriptor SignaturesDescriptor},
	 * or <code>null</code> if no {@link com.arosbio.cheminf.descriptors.SignaturesDescriptor SignaturesDescriptor} is used.
	 * @return The most significant signature or <code>null</code>
	 */
	public String getSignature(){
		return signature;
	}
	
	/**
	 * Get the signature hight
	 * @return The height of the most significant signature, or -1 in case no signatures 
	 */
	public int getHeight(){
		return height;
	}
	
	/**
	 * Get the contributions made by each atom on the prediction. The key of the
	 * map is the <code>IAtom</code> index in the <code>IAtomContainer</code> object.
	 * @return Gradient for the molecule, "atom index" -&gt; contribution
	 */
	public Map<Integer, Double> getAtomContributions(){
		return molGradient;
	}
	
	/**
	 * The atoms that matches the Significant Signature
	 * @return The set of atoms that match the signature
	 */
	public Set<Integer> getAtoms(){
		return atomsMatchingSignature;
	}
	
	/**
	 * <b>If</b> additional descriptors has been used, their gradient will be returned here (or an empty map).
	 * These features cannot be attributed to a single atom and will thus serve as an additional gradient apart from
	 * the one returned from the {@link #getAtomContributions()} method.
	 * @return a mapping with "feature name" -&gt; contribution
	 */
	public Map<String, Double> getAdditionalFeaturesGradient(){
		if (additionalFeaturesGradient != null)
			return additionalFeaturesGradient;
		return new HashMap<>();
	}
	
	/**
	 * The full gradient that is computed numerically, without connecting indices to feature names
	 * @return The gradient for all individual features
	 */
	public List<SparseFeature> getFullGradient(){
		return fullGradient;
	}

	public String toString(){
		return String.format("Significant signature \"%s\" of height %d",signature,height);
	}
	
}

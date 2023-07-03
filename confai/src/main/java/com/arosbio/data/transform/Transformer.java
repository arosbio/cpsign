/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.transform;

import java.io.Serializable;
import java.util.Collection;

import com.arosbio.commons.Experimental;
import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.mixins.Described;
import com.arosbio.commons.mixins.Named;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;

/**
 * The <code>Transformer</code> interface groups pre-processing activities under a common 
 * interface. The intended usage is either running {@link #fit(Collection)} followed by {@link #transform(SubSet)}
 * or simply {@link #fitAndTransform(SubSet)} right away. The transformations can be done either 'in place',
 * by altering the calling instances or by creating new instances, the default should be to perform the operations
 * in place but this can be queried using {@link #isTransformInPlace()} and altered using {@link #transformInPlace(boolean)}.
 *  
 * @author staffan
 *
 */
@Experimental
public interface Transformer extends Described, Named, Configurable, Cloneable, Serializable {

    public static class TransformationException extends RuntimeException {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;
    
        public TransformationException(String msg) {
            super(msg);
        }
        
        public TransformationException(RuntimeException e) {
            super(e);
        }
    }
	
	public static class TransformInfo {
		private final int numRemoved;
		private final int numAltered;
		private final int numFeaturesRemoved;
		
		public TransformInfo(int numRm, int numAlt) {
			this(numRm, numAlt,0);
		}
		
		public TransformInfo(int numRm, int numAlt, int featuresRm) {
			this.numAltered = numAlt;
			this.numRemoved = numRm;
			this.numFeaturesRemoved = featuresRm;
		}
		
		public int getNumberOfRemovedRecords() {
			return numRemoved;
		}
		
		public int getNumberOfAlteredRecords() {
			return numAltered;
		}

        public int getNumberOfRemovedFeatures(){
            return numFeaturesRemoved;
        }
		
		public String toString() {
			if (numRemoved == 0 && numAltered == 0)
				return "No records altered";
			
			StringBuilder bldr = new StringBuilder();
			if (numRemoved>0) {
				bldr.append(numRemoved);
				bldr.append(" record(s) removed");
			}
			if (numAltered > 0) {
				if (bldr.length()>0)
					bldr.append(" & ");
				bldr.append(numAltered);
				bldr.append(" record(s) altered");
			}
			if (numFeaturesRemoved > 0) {
				if (bldr.length()>0)
					bldr.append(" & ");
				bldr.append(numFeaturesRemoved);
				bldr.append(" feature(s) removed");
			}
		
			return bldr.toString();
		}
	}
	
	/**
	 * Check if transformations are set to be performed in place or not
	 * 
	 * @return <code>true</code> if transformations are done directly on the calling instances
	 * @see #transformInPlace(boolean)
	 */
	public boolean isTransformInPlace();
	
	/**
	 * Decide if the transformations should be performed in place, i.e. update the instances
	 * that is given as input, or if the transformation should create a new instance that is returned.
	 * @param inPlace if the transformation should be done directly on the input instance
	 * @return The transformer itself, for a fluent API
	 */
	public Transformer transformInPlace(boolean inPlace);
	
	/**
	 * Optionally fits parameters after the given data, such as calculating mean, min and max values
	 * for later transformations.
	 * @param data A list of records that the transformer should be fitted for
	 * @return should return the same instance, for a fluent API
	 * @throws TransformationException If an exception occurred during the fitting
	 */
	public Transformer fit(Collection<DataRecord> data) throws TransformationException;

	/**
	 * Checks if the <code>Transformer</code> is ready to transform data or
	 * of the {@link #fit(Collection)} method must be called before. Note that 
	 * this method does not require that the {@link #fit(Collection)} method has been called,
	 * but instead if the transform method is safe to call.
	 * @return if any of the <code>transform</code> methods are safe to be called
	 */
	public boolean isFitted();
	
	/**
	 * Both fits the Transformer and performs the transformation on the given SubSet
	 * @param data The dataset that the transformer should be fitted after and then transform it after 
	 * @return The reference to the dataset (the same as sent as parameter)
	 * @throws TransformationException If an exception occurred during fitting or transformation of the dataset
	 */
	public SubSet fitAndTransform(SubSet data) throws TransformationException;	
	
	/**
	 * Transforms the data, requires that the {@link #fit(Collection)} or {@link #fitAndTransform(SubSet)} has been called prior to this call
	 * @param dataset A list of records to apply the transformation on
	 * @return the transformed records, may be the same reference that was sent3,
	 * @throws IllegalStateException If the {@link Transformer} is not ready - i.e. that the {@link #fit(Collection)} method has been prior to this call
	 */
	public SubSet transform(SubSet dataset) throws IllegalStateException;
	
	/**
	 * Transform a single {@link FeatureVector} using this Transformation.
	 * @param object The vector to transform
	 * @return The result of the transformation (a new instance or changes performed in place)
	 * @throws IllegalStateException If the neither {@link #fit(Collection)} nor {@link #fitAndTransform(SubSet)} has been called prior to this method 
	 * @throws TransformationException If another exception occurs during the transformation
	 */
	public FeatureVector transform(FeatureVector object) throws IllegalStateException, TransformationException;
	
	/**
	 * Whether the transformation should be applied to new test objects or is only applicable
	 * at the training-phase. Some transformations, e.g. filtration of duplicates, are not need to apply to later
	 * test objects. But others, such as feature selection or scaling of features are needed
	 * and thus must be stored for later usage. If this method returns true - i.e. are important to be stored,
	 * the class should implement the {@link com.arosbio.io.Saveable Saveable} interface
	 * @return <code>true</code> if needed, <code>false</code> 
	 */
	public boolean appliesToNewObjects();
	
	/**
	 * Check if this Transformation is applicable to classification data
	 * @return <code>true</code> if this Transformation is useful for classification data
	 */
	public boolean applicableToClassificationData();

	/**
	 * Check if this Transformation is applicable to regression data
	 * @return <code>true</code> if this Transformation is useful for regression data
	 */
	public boolean applicableToRegressionData();
	
	/**
	 * Returns information about the last transformation that occurred. This is not
	 * required to be stored at save/load of the Transformer.
	 * @return {@link TransformInfo} about the last transformation that occurred
	 */
	public TransformInfo getLastInfo();

	/**
	 * Creates a identical Transformer object, with the same setting/parameters, but without being "fitted" 
	 * @return an identical {@link Transformer} instance
	 */
	public Transformer clone();

}

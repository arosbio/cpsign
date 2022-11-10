/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data;

import java.util.List;

import com.arosbio.commons.MathUtils;

/**
 * Internal representation of a data record in a modeling problem. Each record consist of 
 * a list of features (values in a N-dimensional space) and the label of that record.  
 * 
 * @author staffan
 *
 */
public class DataRecord implements Cloneable {

	private FeatureVector features;
	private Double label;
	
	// Custom stuff - good for checking things later on in the pipeline
	private int recordIndex;
	private String recordID;

	private DataRecord() {
		features = new SparseVector();
	}

	public DataRecord(Double label, List<SparseFeature> features) {
		this(label, new SparseVector(features));
	}
	
	public DataRecord(Double label, FeatureVector features) {
		this.features = features;
		this.label = label;
	}

	/**
	 * Make a deep clone of the current DataRecord
	 */
	public DataRecord clone(){
		DataRecord clone = new DataRecord();
		clone.label = Double.valueOf(label);
		clone.features = features.clone();
		clone.recordIndex = recordIndex;
		clone.recordID = recordID;
		
		return clone;
	}

	public double getLabel() {
		return label;
	}
	public void setLabel(double label) {
		this.label = label;
	}
	public DataRecord withLabel(int label){
		this.label = (double) label;
		return this;
	}
	public DataRecord withLabel(double label){
		this.label = label;
		return this;
	}
	public FeatureVector getFeatures() {
		return features;
	}
	public void setFeatures(FeatureVector features) {
		this.features = features;
	}
	public DataRecord withFeatures(FeatureVector features){
		this.features = features;
		return this;
	}

	public int getMinFeatureIndex() {
		return features.getSmallestFeatureIndex();
	}
	
	public int getMaxFeatureIndex(){
		return features.getLargestFeatureIndex();
	}
	
	/**
	 * sets the ID of the record (either some textual name or a numerical index stored as string)
	 * @param id an ID for the record
	 * @return the reference of {@code this} object
	 */
	public DataRecord withRecordID(String id){
		this.recordID = id;
		return this;
	}
	
	public String getRecordID() {
		return recordID;
	}
	
	public DataRecord withRecordIndex(int index) {
		this.recordIndex = index;
		return this;
	}
	
	public int getRecordIndex() {
		return recordIndex;
	}
	
	public String toString(){
		return label + " " + features.toString();
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((features == null) ? 0 : features.getNumExplicitFeatures());
		result = prime * result + ((label == null) ? 0 : label.intValue());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null)
			return false;
		
		if (! (obj instanceof DataRecord)) {
			return false;
		}
		DataRecord other = (DataRecord) obj;
		
		if (!MathUtils.equals(label,other.label))
			return false;
		if (features == null && other.features == null)
			return true;
		
		return features.equals(other.features);
		
	}

}

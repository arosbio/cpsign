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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.data.Dataset.RecordType;
import com.arosbio.data.NamedLabels;
import com.arosbio.ml.io.impl.PropertyNameSettings;

abstract class ChemClassifierImpl extends ChemPredictorImpl implements ChemClassifier {

	// ---------------------------------------------------------------------
	// GETTER AND SETTER METHODS
	// ---------------------------------------------------------------------
	
	public NamedLabels getNamedLabels() {
		return getDataset().getTextualLabels();
	}
	
	public Map<Integer,String> getLabels(){
		return getDataset().getTextualLabels().getLabels();
	}
	
	public Set<String> getLabelsSet(){
		return getDataset().getTextualLabels().getLabelsSet();
	}
	
	public void setLabels(Map<Integer, String> labels) throws IllegalArgumentException{
		getDataset().setTextualLabels(new NamedLabels(labels));
	}
	
	public void updateLabels(Map<String,String> newLabels) throws IllegalArgumentException{
		getDataset().getTextualLabels().updateLabels(newLabels);
	}
	
	Map<String,Double> convertToLabels(Map<Integer,Double> pVals){
		Map<String,Double> pValsWithLabels = new HashMap<>();
		NamedLabels labels = getDataset().getTextualLabels();
		for (Map.Entry<Integer,Double> pval: pVals.entrySet())
			pValsWithLabels.put(labels.getLabel(pval.getKey()), pval.getValue());
		return pValsWithLabels;
	}
	
	
	@Override
	public Map<String,Object> getProperties(){
		Map<String,Object> props = super.getProperties();
		props.put(PropertyNameSettings.IS_CLASSIFICATION_KEY, true);
		return props;
	}
	
	// ---------------------------------------------------------------------
	// READ IN DATA
	// ---------------------------------------------------------------------
	
	public void addRecords(Iterator<IAtomContainer> data,
			String property,
			NamedLabels labels) 
			throws IllegalArgumentException {
		if (!getDataset().isReady())
			getDataset().initializeDescriptors();
		getDataset().add(data, property, labels);
	}
	
	public void addRecords(Iterator<IAtomContainer> data,
			String property,
			NamedLabels labels,
			RecordType recordType) 
			throws IllegalArgumentException {
		if (!getDataset().isReady())
			getDataset().initializeDescriptors();
		getDataset().add(data, property, labels,recordType);
	}
	
}

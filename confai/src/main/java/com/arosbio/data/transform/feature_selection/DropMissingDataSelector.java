/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.transform.feature_selection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.MissingValueFeature;
import com.arosbio.data.transform.Transformer;

/**
 * Drop features that has missing data  
 * @author staffan
 *
 */
public class DropMissingDataSelector implements FeatureSelector {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(DropMissingDataSelector.class);
	private static final long serialVersionUID = 7701714694494548968L;
	
	public static final String DESCRIPTION = "Drop features that contain missing data - an alternative to using imputation or filtering out records containing missing data. "
			+ "Note: only of interest if non-signatures descriptors are used.";
	public static final String NAME = "DropMissingDataFeatures";
	
	private transient TransformInfo info;
	private List<Integer> toRemove;
	private boolean inPlace = true;

	@Override
	public boolean isTransformInPlace() {
		return inPlace;
	}

	@Override
	public DropMissingDataSelector transformInPlace(boolean inPlace) {
		this.inPlace = inPlace;
		return this;
	}
	
	@Override
	public String getName() {
		return NAME;
	}
	
	@Override
	public String getDescription() {
		return DESCRIPTION;
	}

	@Override
	public DropMissingDataSelector fit(Collection<DataRecord> data) throws TransformationException {
		LOGGER.debug("Fitting feature-selector {}", this);
		
		Set<Integer> indicesToRm = new HashSet<>();
		
		for (DataRecord r : data) {
			for (FeatureVector.Feature f : r.getFeatures()) {
				if (f instanceof MissingValueFeature || ! Double.isFinite(f.getValue())) {
					indicesToRm.add(f.getIndex());
				}
			}
		}
		
		toRemove = new ArrayList<>(indicesToRm);
		Collections.sort(toRemove);
		LOGGER.debug("Finished fitting transformer, removing features: {}", toRemove);

		return this;
	}
	
	@Override
	public boolean applicableToClassificationData() {
		return true;
	}
	@Override
	public boolean applicableToRegressionData() {
		return true;
	}
	
	@Override
	public boolean isFitted() {
		return toRemove != null;
	}

	@Override
	public SubSet fitAndTransform(SubSet data) throws TransformationException {
		fit(data);

		return transform(data);
	}

	@Override
	public SubSet transform(SubSet data) throws IllegalStateException {
		if (! isFitted())
			throw new IllegalStateException("Transformer " + NAME + " not fitted yet");
		LOGGER.debug("Applying transformer {}", this);
		
		if (toRemove.isEmpty()) {
			info = new TransformInfo(0, 0, 0);
			return inPlace ? data : data.clone();
		}
		
		if (inPlace) {
		for (DataRecord r : data) {
			transform(r.getFeatures());
		}
		
		info = new TransformInfo(0, data.size(), toRemove.size());
		
		return data;
		} else {
			SubSet transformed = new SubSet(data.size());
			for (DataRecord r: data) {
				transformed.add(new DataRecord(r.getLabel(), transform(r.getFeatures())));
			}
			info = new TransformInfo(0, transformed.size(), toRemove.size());

			return transformed;
		}
	}

	@Override
	public FeatureVector transform(FeatureVector object) throws IllegalStateException, TransformationException {
		if (! isFitted()) {
			throw new IllegalStateException("Transformer " + NAME + " not fitted yet");
		}
		
		FeatureVector v = inPlace ? object : object.clone();
		
		if (toRemove.isEmpty())
			return v;

		try {
			v.removeFeatureIndices(toRemove);
			return v;
		} catch (IndexOutOfBoundsException e) {
			throw new TransformationException("Failed applying transformation: " + NAME);
		}
	}

	@Override
	public boolean appliesToNewObjects() {
		return true;
	}

	@Override
	public TransformInfo getLastInfo() {
		return info;
	}

	@Override
	public Transformer clone() {
		DropMissingDataSelector c = new DropMissingDataSelector();
		if (toRemove != null)
			c.toRemove = new ArrayList<>(toRemove);
		c.inPlace = inPlace;
		
		return c;
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		return new ArrayList<>();
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
		if (params != null && !params.isEmpty())
			throw new IllegalArgumentException("Transformer " + NAME + " has no configurable parameters");
	}

	@Override
	public List<Integer> getFeatureIndicesToRemove() throws IllegalStateException {
		return new ArrayList<>(toRemove);
	}
	
	public String toString() {
		return NAME;
	}

}

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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.IntegerConfig;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.FeatureVector.Feature;
import com.arosbio.data.SparseVector;
import com.arosbio.data.transform.ColumnTransformer;

/**
 * The {@link NumNonZeroSelector} is intended to be used with the Signatures ChemDescriptor, to remove 
 * features that are not found in enough compounds. The threshold signifies the smallest number of 
 * times a feature needs to be non-zero to be accepted to keep. E.g. threshold = 2, needs 2 records
 * that has a non-zero feature value to be keep (i.e. removes all features that has only been encountered once)
 * @author staffan
 *
 */
public class NumNonZeroSelector extends ColumnTransformer implements FeatureSelector {

	public static final String NAME = "NumNonzeroSelector";

	/**
	 * 
	 */
	private static final long serialVersionUID = 767673001418843752L;
	private static final Logger LOGGER = LoggerFactory.getLogger(NumNonZeroSelector.class);
	private static final int DEFAULT_MIN_OCC_THRESHOLD = 2;

	private int minOccuranceThreshold = DEFAULT_MIN_OCC_THRESHOLD;

	private List<Integer> toRemove;
	private boolean inPlace = true;
	private transient TransformInfo info;

	/**
	 * Uses 2 as <code>minNumOccurance</code>, i.e. removing features that is only encountered in a single record
	 */
	public NumNonZeroSelector() {
	}

	/**
	 * {@link NumNonZeroSelector} removes columns that has less than <code>threshold</code> number
	 * of non-zero features
	 * @param minNumOccurance min number of occurences for a single feature 
	 */
	public NumNonZeroSelector(int minNumOccurance) {
		if (minNumOccurance < 1)
			this.minOccuranceThreshold = DEFAULT_MIN_OCC_THRESHOLD;
		else
			this.minOccuranceThreshold = minNumOccurance;
	}

	@Override
	public boolean isTransformInPlace() {
		return inPlace;
	}

	@Override
	public NumNonZeroSelector transformInPlace(boolean inPlace) {
		this.inPlace = inPlace;
		return this;
	}

	@Override
	public String getName() {
		return NAME;
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
	public String getDescription() {
		return "Feature selection intended for the Signatures ChemDescriptor, which removes features that has not been found in "
				+ "enough compounds. The default threshold is 2, i.e. keeps features that has been encountered in at least 2 records. "
				+ "Note that this must be performed before e.g. standardization - which will remove the sparseness of the data.";
	}

	public String toString() {
		return NAME;
	}

	public int getThreshold() {
		return minOccuranceThreshold;
	}

	@Override
	public void fit(Collection<DataRecord> data) throws TransformationException {
		LOGGER.debug("Fitting transformer {}", this);
		toRemove = new ArrayList<>();

		if (data.iterator().next().getFeatures() instanceof SparseVector) {
			fitForSparse(data);
		} else {
			fitForDense(data);
		}
		Collections.sort(toRemove);

		LOGGER.debug("Finished fitting {} removing columns: {}",this, toRemove);
	}

	private void fitForSparse(Collection<DataRecord> recs) {
		if (! getColumns().useAll()) {
			LOGGER.debug("Fitting transformer for a subset of features");
			fitForSparseSubset(recs);
			return;
		}
		int maxFeatIndex = DataUtils.getMaxFeatureIndex(recs);
		// Loop through all of them and make the counts
		int[] counts = new int[maxFeatIndex+1];
		for (DataRecord r : recs) {
			for (Feature f : r.getFeatures()){
				counts[f.getIndex()]++;
			}
		}

		for (int col : getColumns().getColumns(maxFeatIndex) ) {
			if (counts[col]< minOccuranceThreshold)
				toRemove.add(col);
		}
	}

	private void fitForSparseSubset(Collection<DataRecord> recs) {
		int maxFeatIndex = DataUtils.getMaxFeatureIndex(recs);

		// The indices to compute for
		List<Integer> indices = getColumns().getColumns(maxFeatIndex);
		int maxIndex = indices.get(indices.size()-1); // This list should always be sorted!Collections.max(indices);

		// Loop through all of them and make the counts
		Map<Integer,Integer> counts = new HashMap<>();
		for (DataRecord r : recs) {
			for (Feature f : r.getFeatures()){
				int index = f.getIndex();
				if (index>maxIndex)
					break;
				if (indices.contains(index))
					counts.put(index, 1 + counts.getOrDefault(index, 0));
			}
		}

		for (Map.Entry<Integer, Integer> colCount : counts.entrySet() ) {
			if (colCount.getValue()< minOccuranceThreshold)
				toRemove.add(colCount.getKey());
		}
	}

	private void fitForDense(Collection<DataRecord> recs) {

		for (int col: getColumns().getColumns(DataUtils.getMaxFeatureIndex(recs))) {
			if (checkRemoveFeature(recs, col))
				toRemove.add(col);
		}
	}

	private boolean checkRemoveFeature(Collection<DataRecord> recs, int col) {
		List<Double> featureValues = DataUtils.extractColumn(recs, col);

		int numNonZero = 0;
		for (Double d : featureValues) {
			if (d != null && !d.isNaN() && ! d.equals(0d))
				numNonZero++;
			if (numNonZero>= minOccuranceThreshold)
				return false;
		}

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
		LOGGER.debug("Applying transformer {}", this);
		if (!isFitted())
			throw new IllegalStateException("Transformer " + NAME + " not fitted yet");

		if (toRemove.isEmpty()) {
			info = new TransformInfo(0,0);
			return inPlace ? data : data.clone();
		}
		if (inPlace) {
			for (DataRecord r: data) {
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
	public FeatureVector transform(FeatureVector object) throws IllegalStateException {
		if (!isFitted())
			throw new IllegalStateException("Transformer " + NAME + " not fitted yet");

		FeatureVector v = inPlace ? object : object.clone();
		try {
			v.removeFeatureIndices(toRemove);
			return v;
		} catch(IndexOutOfBoundsException e) {
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
	public NumNonZeroSelector clone() {
		NumNonZeroSelector clone = new NumNonZeroSelector(minOccuranceThreshold);
		clone.setColumns(getColumns().clone());
		if (toRemove != null)
			clone.toRemove = new ArrayList<>(toRemove);
		clone.inPlace = inPlace;
		return clone;
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		List<ConfigParameter> params = new ArrayList<>();
		params.add(new IntegerConfig.Builder(Arrays.asList("threshold"), 2).build());
		params.addAll(super.getConfigParameters());
		return params;
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
		Map<String,Object> unUsedParams = new HashMap<>();
		for (Map.Entry<String, Object> p : params.entrySet()) {
			if (p.getKey().equalsIgnoreCase("threshold")) {
				minOccuranceThreshold = TypeUtils.asInt(p.getValue());
			} else {
				unUsedParams.put(p.getKey(), p.getValue());
			}
		}
		if (!unUsedParams.isEmpty())
			super.setConfigParameters(unUsedParams);
	}

	@Override
	public List<Integer> getFeatureIndicesToRemove() throws IllegalStateException {
		if (toRemove == null)
			throw new IllegalStateException("Transformer not fitted yet");
		return new ArrayList<>(toRemove);
	}

}

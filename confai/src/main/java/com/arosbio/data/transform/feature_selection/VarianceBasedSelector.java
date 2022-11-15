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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.moment.Variance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.FeatureVector.Feature;
import com.arosbio.data.SparseVector;
import com.arosbio.data.transform.ColumnTransformer;
import com.arosbio.data.transform.feature_selection.FeatureSelectUtils.IndexedValue;
import com.arosbio.data.transform.feature_selection.SelectionCriterion.Criterion;

public class VarianceBasedSelector extends ColumnTransformer implements FeatureSelector {

	public static final String NAME = "VarianceBasedSelector";

	private static final long serialVersionUID = 767673001418843752L;

	private static final Logger LOGGER = LoggerFactory.getLogger(VarianceBasedSelector.class);

	// Selection - criteria
	private transient SelectionCriterion criterion = new SelectionCriterion(Criterion.REMOVE_ZEROS);

	private List<Integer> toRemove;
	private boolean inPlace = true;

	private transient TransformInfo info;

	public VarianceBasedSelector() {
	}

	public VarianceBasedSelector(double threshold) {
		criterion = new SelectionCriterion(Criterion.KEEP_LARGER_THAN_THRESHOLD).withThreshold(threshold);
	}

	public VarianceBasedSelector(SelectionCriterion criterion) {
		this.criterion = criterion;
	}

	/**
	 * Keep the 'N' features with the maximum variance 
	 * @param keepMaxN maximum features to keep
	 */
	public VarianceBasedSelector(int keepMaxN) {
		criterion = SelectionCriterion.keepN(keepMaxN);
	}

	@Override
	public boolean isTransformInPlace() {
		return inPlace;
	}

	@Override
	public VarianceBasedSelector transformInPlace(boolean inPlace) {
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
		return "Removes features that has variance less than a specified threshold. The default setting is to remove all zero-variance features, but "
				+ "can be configured to e.g. remove all but the N highest variance features or all remove all features less than the mean variance of "
				+ "all the features. Note that this feature selector is scale-dependent as the variance is affected by the scale of the features.";
	}

	public String toString() {
		return NAME;
	}

	public void setSelectionCriterion(SelectionCriterion crit) {
		this.criterion = crit;
	}

	public SelectionCriterion getSelectionCriterion() {
		return criterion;
	}

	public List<Integer> getIndicesToRemove(){
		return toRemove;
	}

	@Override
	public VarianceBasedSelector fit(Collection<DataRecord> data) throws TransformationException {
		if (data == null || data.isEmpty())
			throw new TransformationException("Cannot fit Transformer without data");
		LOGGER.debug("Fitting transformer {}", this);

		if (data.iterator().next().getFeatures() instanceof SparseVector) {
			fitSparseData(data);
		} else {
			fitDenseData(data);
		}

		LOGGER.debug("Finished fitting {}, removing columns: {}", this, toRemove);

		return this;
	}

	private void fitSparseData(Collection<DataRecord> recs) {

		int maxFeatIndex = DataUtils.getMaxFeatureIndex(recs);
		// Instantiate all variance-instances
		Map<Integer, Variance> counts = new HashMap<>();
		for (int column : getColumns().getColumns(maxFeatIndex)){
			counts.put(column, new Variance());
		}
		// Go through all data and update the variance instances
		for (DataRecord r : recs) {
			for (Feature f : r.getFeatures()){
				if (counts.containsKey(f.getIndex()) && Double.isFinite(f.getValue())) {
					counts.get(f.getIndex()).increment(f.getValue());
				}
			}
		}

		int nRecs = recs.size();


		List<IndexedValue> vals = new ArrayList<>();
		for (Map.Entry<Integer,Variance> kv : counts.entrySet()){
			Variance colVar = kv.getValue();
			if (colVar.getN() == 0){
				// this was never encountered - 0 variance!
				vals.add(new IndexedValue(kv.getKey(), 0d));
				continue;
			}
			int zerosToAdd = nRecs - (int) colVar.getN();
			for (int i=0; i<zerosToAdd; i++) {
				colVar.increment(0d);
			}
			vals.add(new IndexedValue(kv.getKey(), colVar.getResult()));

		}

		toRemove = criterion.getIndicesToRemove(vals);
		Collections.sort(toRemove);
	}

	private void fitDenseData(Collection<DataRecord> data) {
		List<IndexedValue> variances = new ArrayList<>();
		for (int col : getColumns().getColumns(DataUtils.getMaxFeatureIndex(data))) {
			variances.add(new IndexedValue(col, getVariance(data, col)));
		}
		toRemove = criterion.getIndicesToRemove(variances);
		Collections.sort(toRemove);
	}

	private double getVariance(Collection<DataRecord> recs, int col) {
		List<Double> featureValues = DataUtils.extractColumnExcludeMissingFeatures(recs, col);
		Variance var = new Variance();
		for (double v : featureValues)
			var.increment(v);
		return var.getResult();
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
		if (!isFitted())
			throw new IllegalStateException("Transformer " + NAME + " not fitted yet");
		LOGGER.debug("Applying transformer {}", this);

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
	public VarianceBasedSelector clone() {
		VarianceBasedSelector clone = new VarianceBasedSelector();
		if (criterion != null)
			clone.criterion = criterion.clone();
		clone.setColumns(getColumns().clone());
		clone.inPlace = inPlace;
		if (toRemove != null)
			clone.toRemove = new ArrayList<>(toRemove);
		return clone;
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		List<ConfigParameter> params = new ArrayList<>();
		params.addAll(criterion.getConfigParameters());
		params.addAll(super.getConfigParameters());
		return params;
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
		try {
			SelectionCriterion critClone = criterion.clone();
			critClone.setConfigParameters(params);
			criterion = critClone;
		} catch(IllegalStateException e) {
			throw new IllegalArgumentException(e.getMessage());
		}

		super.setConfigParameters(params);
	}

	@Override
	public List<Integer> getFeatureIndicesToRemove() throws IllegalStateException {
		if (toRemove == null)
			throw new IllegalStateException("Transformer not fitted yet");
		return new ArrayList<>(toRemove);
	}

}

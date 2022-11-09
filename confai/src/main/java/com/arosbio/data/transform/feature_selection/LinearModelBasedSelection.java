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
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.NumericConfig;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.transform.feature_selection.FeatureSelectUtils.IndexedValue;
import com.arosbio.data.transform.feature_selection.SelectionCriterion.Criterion;
import com.arosbio.ml.algorithms.impl.LibLinear;
import com.google.common.collect.Range;

import de.bwaldvogel.liblinear.Linear;
import de.bwaldvogel.liblinear.Model;
import de.bwaldvogel.liblinear.Parameter;
import de.bwaldvogel.liblinear.SolverType;

public abstract class LinearModelBasedSelection implements FeatureSelector {

	private static final long serialVersionUID = 1443016245932748597L;

	private static final Logger LOGGER = LoggerFactory.getLogger(LinearModelBasedSelection.class);

	private static final List<String> COST_PARAMS = Arrays.asList("c","cost");
	private static final List<String> EPSILON_PARAMS = Arrays.asList("eps","epsilon");
	private static final double DEFAULT_C = 0.01;
	private static final double DEFAULT_EPS = 0.001;

	// Selection - criterion
	private transient SelectionCriterion criterion;

	private List<Integer> toRemove;
	private boolean inPlace = true;

	private transient List<IndexedValue> weights;
	private transient TransformInfo info;
	private transient Parameter linearParameter;

	public LinearModelBasedSelection(SolverType st) {
		this(new Parameter(st, DEFAULT_C, DEFAULT_EPS));
	}

	public LinearModelBasedSelection(Parameter param) {
		this.linearParameter = param;
		if (isL1regularization(param.getSolverType()))
			criterion = new SelectionCriterion(Criterion.REMOVE_ZEROS);
		else 
			criterion = new SelectionCriterion(Criterion.KEEP_LARGER_THAN_MEAN);
	}

	@Override
	public boolean isTransformInPlace() {
		return inPlace;
	}

	@Override
	public LinearModelBasedSelection transformInPlace(boolean inPlace) {
		this.inPlace = inPlace;
		return this;
	}

	Parameter getParameters() {
		return linearParameter;
	}

	void setParameters(Parameter p) {
		this.linearParameter = p;
	}

	public void setC(double c) {
		linearParameter.setC(c);
	}

	public void setEpsilon(double epsilon) {
		linearParameter.setEps(epsilon);
	}

	public void setSelectionCriterion(SelectionCriterion criterion) {
		this.criterion = criterion;
	}

	public SelectionCriterion getSelectionCriterion() {
		return criterion;
	}

	public List<IndexedValue> getWeights(){
		return weights;
	}

	@Override
	public LinearModelBasedSelection fit(Collection<DataRecord> data) throws TransformationException {
		LOGGER.debug("Fitting transformer {}", this);
		if (linearParameter == null) {
			throw new TransformationException("No parameters set for feature selector using LinearModelBasedSelection");
		}

		int maxFeatIndex = DataUtils.getMaxFeatureIndex(data);
		if (criterion.getN() >= (maxFeatIndex+1)) {
			LOGGER.debug("Feature selector {} instructed to keep {} features which is more than already present. Skipping this selection.",getName(),criterion.getN());
			toRemove = new ArrayList<>();
			return this;
		}
		// Fit the linear model
		
		Model m = null;
		if (data instanceof List){
			m = Linear.train(
				LibLinear.createLibLinearTrainProblem((List<DataRecord>)data), 
				linearParameter);
		} else {
			m = Linear.train(
				LibLinear.createLibLinearTrainProblem(new ArrayList<>(data)), 
				linearParameter);
		}
			

		weights = null;
		if (m.getSolverType().isSupportVectorRegression()) {
			// Regression
			weights = getWeights(m.getFeatureWeights(), m.getBias()>= 0);
		} else if (m.getNrClass() == 2) {
			// Binary classification
			weights = getWeights(m.getFeatureWeights(), m.getBias()>= 0);
		} else {
			// Multiclass one-vs-rest classification
			weights = new ArrayList<>();
			if (m.getFeatureWeights().length != (m.getNrClass()*(m.getNrFeature()+(m.getBias()>=0? 1 :0)))){
				throw new TransformationException("Error computing feature selection for multi-class problem");
			}
			// Special case first iteration, class 0
			double[] modelWts = m.getFeatureWeights();
			for (int j=0; j<m.getNrFeature(); j++) {
				weights.add(new IndexedValue(j, Math.abs(modelWts[j])));
			}
			int biasOffset = (m.getBias()>= 0? 1 : 0);
			for (int i=1; i<m.getNrClass(); i++) {
				for (int j=0; j<m.getNrFeature(); j++) {
					IndexedValue iv = weights.get(j);
					iv.value += Math.abs(modelWts[j + i*(m.getNrFeature()+biasOffset)]) ;
				}
			}

		}

		LOGGER.debug("Feature-selector found the following weights: {}", weights);

		// Now we have a list of weights only (excluding pot. bias), in absolute numbers including the feature indices
		toRemove = criterion.getIndicesToRemove(weights);

		// Sort the list of indices to remove
		Collections.sort(toRemove); 

		LOGGER.debug("Finished fitting Feature selector {} filtering out indices: {}", getName(), toRemove);

		return this;
	}

	private boolean isL1regularization(SolverType t) {
		return t == SolverType.L1R_L2LOSS_SVC || t == SolverType.L1R_LR;
	}

	private List<IndexedValue> getWeights(double[] inp, boolean rmLast){
		List<IndexedValue> wts = new ArrayList<>();
		int index = 0;
		for (double d : inp) {
			wts.add(new IndexedValue(index, Math.abs(d)));
			index++;
		}
		if (rmLast)
			wts.remove(wts.size()-1);

		return wts;
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

		if (! isFitted())
			throw new IllegalStateException("Transformer " + getName() + " not fitted yet");

		if (toRemove.isEmpty()) {
			// Nothing should be removed - simply return
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
	public FeatureVector transform(FeatureVector object) 
			throws IllegalStateException, TransformationException {
		if (! isFitted())
			throw new IllegalStateException("Transformer " + getName() + " not fitted yet");

		FeatureVector v = inPlace ? object : object.clone();

		if (toRemove.isEmpty())
			return v;

		try {
			v.removeFeatureIndices(toRemove);
			return v;
		} catch (IndexOutOfBoundsException e) {
			LOGGER.debug("Failed removing feature indicies", e);
			throw new TransformationException("Failed applying transformation: " + getName());
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
	public abstract LinearModelBasedSelection clone();

	@Override
	public List<ConfigParameter> getConfigParameters() {
		List<ConfigParameter> ps = new ArrayList<>();
		ps.addAll(criterion.getConfigParameters());
		ps.add(new NumericConfig.Builder(COST_PARAMS, DEFAULT_C).range(Range.atLeast(0d)).build());
		ps.add(new NumericConfig.Builder(EPSILON_PARAMS, DEFAULT_EPS).range(Range.atLeast(0d)).build());
		return ps;
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {

		for (Map.Entry<String, Object> p : params.entrySet()) {
			if (match(COST_PARAMS, p.getKey())) {
				setC(TypeUtils.asDouble(p.getValue()));
			} else if (match(EPSILON_PARAMS, p.getKey())) {
				setEpsilon(TypeUtils.asDouble(p.getValue()));
			}
		}

		try {
			SelectionCriterion critClone = criterion.clone();
			critClone.setConfigParameters(params);
			criterion = critClone;
		} catch(IllegalStateException e) {
			throw new IllegalArgumentException(e.getMessage());
		}
	}

	private boolean match(List<String> against, String toMatch) {
		for (String s : against) {
			if (s.equalsIgnoreCase(toMatch))
				return true;
		}
		return false;
	}

	@Override
	public List<Integer> getFeatureIndicesToRemove() throws IllegalStateException {
		return new ArrayList<>(toRemove);
	}


	protected void copyStateToClone(LinearModelBasedSelection clone){
		if (toRemove!=null)
			clone.toRemove = new ArrayList<>(toRemove);
		clone.inPlace = inPlace;
	}

}

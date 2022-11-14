/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.transform.scale;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.NumericConfig;
import com.arosbio.commons.mixins.Aliased;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.FeatureVector.Feature;
import com.arosbio.data.MissingValueFeature;
import com.arosbio.data.SparseVector;
import com.arosbio.data.transform.ColumnSpec;
import com.arosbio.data.transform.ColumnTransformer;

/**
 * The <code>ZeroMaxScaler</code> scales features linearly to the interval [0, max] using either
 * all or a subset of features. N.B. this is intended to be used only for SparseFeature data 
 * to not disrupt the sparsity and is written more efficiently for this data type. 
 * @author staffan
 *
 */
public class ZeroMaxScaler extends ColumnTransformer implements FeatureScaler, Aliased {

	public static final String NAME = "ZeroMaxScaler";
	public static final String[] ALIASES = new String[] {"ZeroMaxNormalizer"};
	private static final Logger LOGGER = LoggerFactory.getLogger(ZeroMaxScaler.class);
	/**
	 * 
	 */
	private static final long serialVersionUID = 3700234928131767391L;

	private double max = 1;

	// The scale factors
	private Map<Integer,Double> scaleFactors;
	private boolean inPlace = true;
	private transient TransformInfo info;

	public ZeroMaxScaler() {
	}

	public ZeroMaxScaler(double max) {
		this.max = max;
		assertMaxIsOK();
	}

	private void assertMaxIsOK() {
		if (max <= 0)
			throw new IllegalArgumentException("Parameter max must be >0 for Transformer " + NAME);

		if (Double.isNaN(max)){
			throw new IllegalArgumentException("Parameter max must be valid number for Transformer " + NAME);
		}
	}

	public ZeroMaxScaler(ColumnSpec columns) {
		super(columns);
	}

	@Override
	public boolean isTransformInPlace() {
		return inPlace;
	}

	@Override
	public ZeroMaxScaler transformInPlace(boolean inPlace) {
		this.inPlace = inPlace;
		return this;
	}

	@Override
	public String getDescription() {
		return "Linear feature scaling to the interval [0, max]. The input features *must* start from 0 to begin with. "
				+ "This scaler is used for keeping sparsity of data where features with 0 are not explicitly stored, e.g. "
				+ "when using signatures descriptor with high sparsity."; 
	}

	public ZeroMaxScaler setMax(double max) {
		this.max = max;
		return this;
	}

	public double getMax() {
		return max;
	}


	public ZeroMaxScaler clone() {
		ZeroMaxScaler clone = new ZeroMaxScaler(max);
		if (scaleFactors != null)
			clone.scaleFactors = new HashMap<>(scaleFactors);
		clone.setColumns(getColumns().clone());
		return clone;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String[] getAliases() {
		return ALIASES;
	}

	@Override
	public boolean isFitted() {
		return scaleFactors != null;
	}

	@Override
	public ZeroMaxScaler fit(Collection<DataRecord> data) throws TransformationException {
		LOGGER.debug("Fitting transformer {}", this);

		if (data.iterator().next().getFeatures() instanceof SparseVector) {
			fitSparseData(data);
		} else {
			fitDenseData(data);
		}

		LOGGER.debug("Finished fitting transformer");

		return this;
	}

	private <C extends Collection<DataRecord>> void fitSparseData(C data) throws TransformationException {

		if (! getColumns().useAll()) {
			LOGGER.debug("Fitting using only a subset of features");
			fitSparseDataSubset(data);
			return;
		} 
		LOGGER.debug("Fitting using all features");

		int maxFeatIndex = DataUtils.getMaxFeatureIndex(data);
		double[] colMaxVector = new double[maxFeatIndex+1];

		// Go through the data
		for (DataRecord r : data) {
			for (Feature f : r.getFeatures()) {
				double val = f.getValue();
				if (f instanceof MissingValueFeature)
					throw new TransformationException("Transformation using " + NAME + " not possible on missing-data features");
				else if (val < 0) {
					throw new TransformationException("Transformer " + NAME + " not allowed for features with values smaller than 0 - use the " + MinMaxScaler.NAME + " instead");
				}
				// Max
				else if (val > colMaxVector[f.getIndex()])
					colMaxVector[f.getIndex()] = val;
			}
		}

		scaleFactors = new HashMap<>();
		for (int col: getColumns().getColumns(maxFeatIndex)) {
			double factor = max / colMaxVector[col];
			// If the max value of features is 0 - then division by 0 -> cap this to make all 0 in output as well
			if (Double.isInfinite(factor))
				factor = 0;
			scaleFactors.put(col, factor);
		}

	}

	private void fitSparseDataSubset(Collection<DataRecord> data) throws TransformationException {
		int maxFeatIndex = DataUtils.getMaxFeatureIndex(data);
		
		List<Integer> indices = getColumns().getColumns(maxFeatIndex);
		int maxIndex = indices.get(indices.size()-1); // This list should always be sorted! Collections.max(indices);

		// High-jacking the ScaleFactors to keep the maximum value, then compute the scale factors instead
		scaleFactors = new HashMap<>();

		// Go through the data
		for (DataRecord r : data) {
			for (Feature f : r.getFeatures()) {
				int index = f.getIndex();
				if (index > maxIndex)
					break;

				if (indices.contains(index)) {
					double val = f.getValue();

					if (f instanceof MissingValueFeature)
						throw new TransformationException("Transformation using " + NAME + " not possible on missing-data features");
					else if (val < 0) {
						throw new TransformationException("Transformer " + NAME + " not allowed for features with values smaller than 0 - use the " + MinMaxScaler.NAME + " instead");
					}

					if (! scaleFactors.containsKey(index)) {
						scaleFactors.put(index, val);
					} else {
						scaleFactors.put(index, Math.max(val, scaleFactors.get(index)));
					}
				}
			}
		}

		// Compute the scale factors using the maximum values
		for (int col: scaleFactors.keySet()) {
			double maxFeatVal = scaleFactors.getOrDefault(col, 0d);

			double factor = max / maxFeatVal;
			// If the max value of features is 0 - then division by 0 -> cap this to make all 0 in output as well
			if (Double.isInfinite(factor))
				factor = 0;
			// Update the value
			scaleFactors.put(col, factor);
		}

	}

	private void fitDenseData(Collection<DataRecord> data) throws TransformationException {
		scaleFactors = new HashMap<>();
		for (int col: getColumns().getColumns(DataUtils.getMaxFeatureIndex(data))) {
			scaleFactors.put(col, fitOneFeature(data, col));
		}
	}

	private double fitOneFeature(Collection<DataRecord> recs, int index){
		List<Double> column = DataUtils.extractColumn(recs, index);

		// Verify no null or NaN
		if (CollectionUtils.containsNullOrNaN(column)) {
			throw new TransformationException("Transformation using " + NAME + " not possible on missing-data features");
		}

		if (Collections.min(column) < 0) {
			throw new TransformationException("Transformation using " + NAME + " not possible when input has features with values less than 0");
		}

		double factor = max / Collections.max(column);
		// If the max value of features is 0 - then division by 0 -> cap this to make all 0 in output as well
		if (Double.isInfinite(factor))
			factor = 0;

		return factor;
	}

	@Override
	public SubSet fitAndTransform(SubSet data) throws TransformationException {
		fit(data);

		return transform(data);
	}

	@Override
	public SubSet transform(SubSet data) throws IllegalStateException {
		LOGGER.debug("Applying transformer " + this);
		if (scaleFactors == null || scaleFactors.isEmpty()) {
			throw new IllegalStateException("Transformer " + NAME + " not fit yet");
		}

		LOGGER.debug("Applying scaler transformer {}", this);
		SubSet transformed = data; 
		if (inPlace) {
			for (DataRecord r : data) {
				transform(r.getFeatures());
			}
		} else {
			transformed = new SubSet(data.size());
			for (DataRecord r : data) {
				transformed.add(new DataRecord(r.getLabel(), transform(r.getFeatures())));
			}
		}

		info = new TransformInfo(0, data.size());

		return transformed;
	}

	@Override
	public FeatureVector transform(FeatureVector object) throws IllegalStateException {
		if (scaleFactors == null || scaleFactors.isEmpty()) {
			throw new IllegalStateException("Transformer " + this + " not fitted yet");
		}
		FeatureVector transformed = inPlace ? object : object.clone();
		for (Feature f : object) {
			Double scaleFac = scaleFactors.get(f.getIndex());
			if (scaleFac != null) {
				transformed.withFeature(f.getIndex(),scaleFac * f.getValue());
			}
		}
		return transformed;

	}

	@Override
	public boolean appliesToNewObjects() {
		return true;
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
	public TransformInfo getLastInfo() {
		return info;
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		List<ConfigParameter> params = new ArrayList<>();
		params.add(new NumericConfig.Builder(Arrays.asList("max"), 1d).build());
		params.addAll(super.getConfigParameters());
		return params;
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) 
			throws IllegalStateException, IllegalArgumentException {
		Map<String,Object> unUsedParams = new HashMap<>();
		for (Map.Entry<String, Object> p: params.entrySet()) {
			if (p.getKey().equalsIgnoreCase("max")) {
				max = TypeUtils.asDouble(p.getValue());
			} else {
				unUsedParams.put(p.getKey(), p.getValue());
			}
		}

		if (!unUsedParams.isEmpty())
			super.setConfigParameters(unUsedParams);

		assertMaxIsOK();
	}

	public String toString() {
		return NAME;
	}

}

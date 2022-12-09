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
 * The MinMaxScaler scales features linearly to the interval [min, max] to either
 * all or a subset of features
 * @author staffan
 *
 */
public class MinMaxScaler extends ColumnTransformer implements FeatureScaler, Aliased {

	private static final Logger LOGGER = LoggerFactory.getLogger(MinMaxScaler.class);
	private static final long serialVersionUID = -6257754746784935088L;


	public static final String NAME = "MinMaxScaler";
	public static final String[] ALIASES = new String[] {"MinMaxNormalizer"};
	
	private double min = 0, max = 1;
	private boolean inPlace = true;

	/**
	 * The scale factors (col -&gt; Pair&lt;min(col), max(col)&gt;)
	 */
	private Map<Integer,double[]> scaleFactors;

	private transient TransformInfo info;

	public MinMaxScaler() {
	}

	public MinMaxScaler(double min, double max) {
		this.min = min;
		this.max = max;
		assertMinMaxIsOK();
	}

	public MinMaxScaler(ColumnSpec features) {
		super(features);
	}

	public String getDescription() {
		return "Linear feature scaling to the interval [min, max]. " + CONVERTING_SPARSE_TO_DENSE_WARNING_MSG; 
	}

	public boolean isTransformInPlace() {
		return inPlace;
	}

	public MinMaxScaler transformInPlace(boolean inPlace) {
		this.inPlace = inPlace;
		return this;
	}

	public MinMaxScaler setMin(double min) {
		if (!Double.isFinite(min))
			throw new IllegalArgumentException("Parameter min must be a finite number");
		this.min = min;
		return this;
	}
	public double getMin() {
		return min;
	}
	public MinMaxScaler setMax(double max) {
		if (!Double.isFinite(max))
			throw new IllegalArgumentException("Parameter max must be a finite number");
		this.max = max;
		return this;
	}
	public double getMax() {
		return max;
	}

	private void assertMinMaxIsOK() {
		if (min>=max)
			throw new IllegalArgumentException("Parameters min must be less than max for Transformer " + getName());

		if (Double.isNaN(max) || Double.isNaN(min)){
			throw new IllegalArgumentException("Parameters min and max must be valid numbers");
		}

	}

	public MinMaxScaler clone() {
		MinMaxScaler clone = new MinMaxScaler(min,max);
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
	public MinMaxScaler fit(Collection<DataRecord> data) throws TransformationException {
		if (data == null || data.isEmpty())
			throw new TransformationException("Cannot fit Transformer "+NAME+" without data");
		LOGGER.debug("Fitting transformer {}", this);

		if (data.iterator().next().getFeatures() instanceof SparseVector) {
			fitSparseData(data);
		} else {
			fitDenseData(data);
		}

		LOGGER.debug("Finished fitting transformer");
		
		return this;
	}

	private void fitSparseData(Collection<DataRecord> data) throws TransformationException {

		if (!getColumns().useAll()) {
			LOGGER.debug("Fitting transformer using a subset of features");
			fitSparseDataSubset(data);
			return;
		}

		int maxFeatIndex = DataUtils.getMaxFeatureIndex(data);

		double[][] colMinMaxMatrix = new double[maxFeatIndex+1][];
		int[] counts = new int [maxFeatIndex+1];


		// Go through the data
		for (DataRecord r : data) {

			for (Feature f : r.getFeatures()) {
				if (f instanceof MissingValueFeature)
					throw new TransformationException("Transformation using " + this + " not possible on missing-data features");

				int ind = f.getIndex();
				double val = f.getValue();
				counts[ind] ++;

				double[] minMax = colMinMaxMatrix[ind];
				if (minMax == null) {
					colMinMaxMatrix[ind] = new double[] {val,val};
				} else {

					// Min
					if (val < minMax[0])
						minMax[0] = val;
					// Max
					else if (val > minMax[1])
						minMax[1] = val;
				}
			}
		}
		int nRecs = data.size();
		scaleFactors = new HashMap<>();
		for (int col=0; col<colMinMaxMatrix.length; col++) {
			double[] minMax=colMinMaxMatrix[col];
			if (counts[col]< 1) {
				// Skip the scale factors for this one
			} else if (counts[col] < nRecs) {
				// Some sparse (0) values
				scaleFactors.put(col, new double[] {Math.min(0, minMax[0]), Math.max(0, minMax[1])});
			} else {
				// only explicit data
				scaleFactors.put(col, new double[] {minMax[0], minMax[1]});
			}

		}

	}

	private void fitSparseDataSubset(Collection<DataRecord> data) throws TransformationException {
		int maxFeatIndex = DataUtils.getMaxFeatureIndex(data);
		List<Integer> indices = getColumns().getColumns(maxFeatIndex);
		int maxIndex = indices.get(indices.size()-1); // This list should always be sorted!Collections.max(indices);

		scaleFactors = new HashMap<>();
		Map<Integer,Integer> counts = new HashMap<>();

		// Go through the data
		for (DataRecord r : data) {
			for (Feature f : r.getFeatures()) {
				int index = f.getIndex();
				if (index > maxIndex)
					break;

				if (indices.contains(index)) {
					if (f instanceof MissingValueFeature)
						throw new TransformationException("Transformation using " + NAME + " not possible on missing-data features");
					// Update count of this feature
					counts.put(index, 1+counts.getOrDefault(index, 0));

					if (!scaleFactors.containsKey(index)) {
						scaleFactors.put(index, new double[]{f.getValue(), f.getValue()});
					} else {
						double[] minMax = scaleFactors.get(index);

						// Min
						if (f.getValue() < minMax[0])
							minMax[0] = f.getValue();
						// Max
						else if (f.getValue() > minMax[1])
							minMax[1] = f.getValue();
					}
				}
			}
		}

		int nRec = data.size();
		for (Map.Entry<Integer, Integer> colCount : counts.entrySet()) {
			if (colCount.getValue() < nRec) {
				double[] minMax = scaleFactors.get(colCount.getKey());
				minMax[0] = Math.min(0, minMax[0]);
				minMax[1] = Math.max(0, minMax[1]);
			}
		}

	}


	private void fitDenseData(Collection<DataRecord> data) throws TransformationException {
		scaleFactors = new HashMap<>();
		for (int col: getColumns().getColumns(DataUtils.getMaxFeatureIndex(data))) {
			scaleFactors.put(col, fitOneFeature(data, col));
		}
	}

	private double[] fitOneFeature(Collection<DataRecord> recs, int index){
		List<Double> column = DataUtils.extractColumn(recs, index);

		// Verify no null or NaN
		if (CollectionUtils.containsNullOrNaN(column)) {
			throw new TransformationException("Transformation using " + NAME+ " not possible on missing-data features");
		}

		return new double[]{ Collections.min(column),Collections.max(column)};
	}


	@Override
	public SubSet fitAndTransform(SubSet data) throws TransformationException {
		fit(data);

		return transform(data);
	}

	@Override
	public SubSet transform(SubSet data) throws IllegalStateException {
		if (scaleFactors == null) {
			throw new IllegalStateException("Transformer " + this + " not fit yet");
		}
		if (data.isEmpty())
			return inPlace ? data : new SubSet(data.getDataType());

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

		LOGGER.debug("Finished transformer: {}", info);

		return transformed;
	}

	@Override
	public FeatureVector transform(FeatureVector object) throws IllegalStateException {
		if (scaleFactors == null) {
			throw new IllegalStateException("Transformer " + this + " not fit yet");
		}

		FeatureVector toReturn = (inPlace ? object : object.clone());

		for (Map.Entry<Integer, double[]> column : scaleFactors.entrySet()) {
			toReturn.withFeature(column.getKey(), 
					transformOneFeature(
							object.getFeature(column.getKey()), 
							column.getValue()[0], 
							column.getValue()[1]
							)
					);
		}
		return object;
	}

	private double transformOneFeature(double old, double xMin, double xMax) {
		// If there's no variance in the data - return it unchanged
		if (xMin == xMax)
			return old;
		return min + (old - xMin)*(max-min) / (xMax - xMin);
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
		params.add(new NumericConfig.Builder(Arrays.asList("min"), 0d).build());
		params.add(new NumericConfig.Builder(Arrays.asList("max"), 1d).build());
		params.addAll(super.getConfigParameters());
		return params;
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) 
			throws IllegalStateException, IllegalArgumentException {
		Map<String,Object> unUsedParams = new HashMap<>();
		for (Map.Entry<String, Object> p : params.entrySet()) {
			if (p.getKey().equalsIgnoreCase("min")) {
				min = TypeUtils.asDouble(p.getValue());
			} else if (p.getKey().equalsIgnoreCase("max")) {
				max = TypeUtils.asDouble(p.getValue());
			} else {
				unUsedParams.put(p.getKey(), p.getValue());
			}
		}
		if (!unUsedParams.isEmpty())
			super.setConfigParameters(unUsedParams);

		assertMinMaxIsOK();
	}

	public String toString() {
		return NAME;
	}

}

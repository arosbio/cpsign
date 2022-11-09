/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.transform.impute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.MathUtils;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.EnumConfig;
import com.arosbio.commons.config.NumericConfig;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.FeatureVector.Feature;
import com.arosbio.data.MissingValueFeature;
import com.arosbio.data.SparseVector;
import com.arosbio.data.transform.ColumnSpec;
import com.arosbio.data.transform.ColumnTransformer;
import com.arosbio.data.transform.scale.MinMaxScaler;

public class SingleFeatureImputer extends ColumnTransformer implements Imputer {

	public static final String NAME = "SingleFeatImputer";

	public static final String DESCRIPTION = "Imputes values based on each feature individually by substituting missing features "
			+ "with the mean, median, min, max or a manually fixed value for that feature. Note that this can be used also when training data have "
			+ "all values but test data might not, and the imputation will be performed at predict time. Note that 'median' strategy requires "
			+ "slightly more memory and could cause issues for really big problems with many features.";

	private static final String CONFIG_FIXED_IMPUTE_VALUE_PARAM_NAME = "fixedImputeValue";
	private static final String CONFIG_IMPUTE_STRATEGY_PARAM_NAME = "imputeStrategy";

	public static enum ImputationStrategy {
		MEAN, MEDIAN, MIN, MAX, FIXED;
	}


	private static final Logger LOGGER = LoggerFactory.getLogger(MinMaxScaler.class);

	/**
	 * 
	 */
	private static final long serialVersionUID = -8322248785608017577L;

	private ImputationStrategy strategy = ImputationStrategy.MEAN;
	private Map<Integer,Double> substitutions;
	private Double fixedValue = 0d;
	private boolean inPlace = true;

	private transient TransformInfo info;

	public SingleFeatureImputer() {}

	public SingleFeatureImputer(ColumnSpec spec) {
		super(spec);
	}

	@Override
	public boolean isTransformInPlace() {
		return inPlace;
	}

	@Override
	public SingleFeatureImputer transformInPlace(boolean inPlace) {
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

	public SingleFeatureImputer setStrategy(ImputationStrategy strategy) {
		this.strategy = strategy;
		return this;
	}

	public ImputationStrategy getStrategy() {
		return strategy;
	}

	@Override
	public SingleFeatureImputer fit(Collection<DataRecord> data) throws TransformationException {
		if (data == null || data.isEmpty())
			throw new TransformationException("Cannot fit Transformer without data");
		LOGGER.debug("Fitting transformer {}", this);

		if (strategy == ImputationStrategy.FIXED) {
			fitFixed(data);
		} else if (data.iterator().next().getFeatures() instanceof SparseVector) {
			fitSparseData(data);
		} else {
			fitDenseData(data);
		}

		LOGGER.debug("Finished fitting {}", this);

		return this;

	}

	private void fitFixed(Collection<DataRecord> data) {
		if (fixedValue == null || !Double.isFinite(fixedValue)) {
			throw new TransformationException("Transformer "+NAME + " cannot fit to the fixed value: " + fixedValue);
		}
		substitutions = new HashMap<>();
		for (int col: getColumns().getColumns(DataUtils.getMaxFeatureIndex(data))) {
			substitutions.put(col, fixedValue);
		}
	}

	private void fitSparseData(Collection<DataRecord> data) throws TransformationException {

		if (strategy == ImputationStrategy.MEDIAN) {
			fitSparseDataMedian(data);
			return;
		}

		// Here we fit using the updating statistics instead, not having to keep all values in memory
		int maxFeatIndex = DataUtils.getMaxFeatureIndex(data);
		// The indices to compute for
		List<Integer> indices = getColumns().getColumns(maxFeatIndex);
		int maxIndex = indices.get(indices.size()-1); // This list should always be sorted! Collections.max(indices);

		Map<Integer, SummaryStatistics> colStats = new HashMap<>();

		// Go through the data
		for (DataRecord r : data) {
			for (Feature f : r.getFeatures()) {
				if (f instanceof MissingValueFeature)
					continue;
				int index = f.getIndex();
				if (index > maxIndex)
					break;

				if (indices.contains(index)) {
					if (!colStats.containsKey(index))
						colStats.put(index, new SummaryStatistics());

					if (Double.isFinite(f.getValue()))
						colStats.get(index).addValue(f.getValue());
				}
			}
		}

		int nRecs = data.size();
		// Compute the values
		substitutions = new HashMap<>();
		for (int col : colStats.keySet() ) {

			SummaryStatistics colVar = colStats.get(col);

			int zerosToAdd = nRecs - (int) colVar.getN();
			for (int i=0; i<zerosToAdd; i++) {
				colVar.addValue(0d);
			}
			double sub = Double.NaN;
			switch (strategy) {
			case MAX:
				sub = colVar.getMax();
				break;
			case MEAN:
				sub = colVar.getMean();
				break;
			case MIN:
				sub = colVar.getMin();
				break;

			default:
				LOGGER.debug("Encountered impute-strategy of non-supported type: {}", strategy);
				throw new TransformationException("Encountered impute-strategy of non-supported type: " + strategy);
			}
			if (Double.isFinite(sub))
				substitutions.put(col, sub);
			else {
				LOGGER.debug("Encountered non-finite substitution value for feature index {}: {}", col, sub);
				throw new TransformationException("Encountered feature for which a substition value could not be computed - "
						+ "consider removing features with all missing values prior to using the " + NAME + " transformation");
			}

		}

	}

	private void fitSparseDataMedian(Collection<DataRecord> data) throws TransformationException {
		LOGGER.debug("Fitting for median (which requires to create a (potentially) huge matrix");
		try {
			int maxFeatIndex = DataUtils.getMaxFeatureIndex(data);
			// The indices to compute for
			List<Integer> indices = getColumns().getColumns(maxFeatIndex);
			int maxIndex = indices.get(indices.size()-1); // This list should always be sorted!Collections.max(indices);

			Map<Integer, List<Double>> colLists = new HashMap<>();

			for (DataRecord r : data) {
				for (Feature f : r.getFeatures()) {
					if (f instanceof MissingValueFeature)
						continue;
					int index = f.getIndex();
					if (index > maxIndex)
						break;

					if (indices.contains(index)) {
						if (!colLists.containsKey(index))
							colLists.put(index, new ArrayList<>());

						if (Double.isFinite(f.getValue()))
							colLists.get(index).add(f.getValue());
					}
				}
			}

			int numZero=0;

			// Get the values
			int nRec = data.size();
			substitutions = new HashMap<>();
			for (int col : colLists.keySet()) { 
				List<Double> vals = colLists.get(col);
				if (vals == null || vals.isEmpty()) {
					substitutions.put(col, 0d);
					numZero++;
					continue;
				}
				double[] colValues = new double[nRec];
				fillArray(colValues, vals); 
				substitutions.put(col, new Percentile(50).evaluate(colValues));
				vals.clear(); // might be pre-optimization, but try to clear up some memory
			}
			LOGGER.debug("Found {} features that were not present in the dataset - perhaps these should be removed prior to this step?",numZero);
		} catch (OutOfMemoryError e) {
			LOGGER.debug("Failed with an OutOfMemory exception");
			throw new TransformationException("Failed fitting transformer " + NAME + " using 'median' strategy, please use any of the other strategies for larger problems");
		}
	}

	private static void fillArray(double[] arr, List<Double> listVals) {
		for (int i=0; i<listVals.size(); i++) {
			arr[i] = listVals.get(i);
		}
	}

	private void fitDenseData(Collection<DataRecord> data) throws TransformationException {
		substitutions = new HashMap<>();
		for (int col: getColumns().getColumns(DataUtils.getMaxFeatureIndex(data))) {
			substitutions.put(col, fitOneFeature(data, col));
		}
	}

	private double fitOneFeature(Collection<DataRecord> recs, int index){
		if (strategy == ImputationStrategy.FIXED)
			return fixedValue;

		List<Double> column = DataUtils.extractColumn(recs, index);

		List<Double> colVals = CollectionUtils.filterNullOrNaN(column);

		switch (strategy) {
		case MIN:
			return Collections.min(colVals);
		case FIXED:
			LOGGER.debug("Setting FIXED value in the fitDense-version of imputer, this doesn't require the data to be traversed - something is bad");
			return fixedValue; 
		case MAX:
			return Collections.max(colVals);
		case MEDIAN:
			return MathUtils.median(colVals);
		case MEAN:
			return MathUtils.mean(colVals);
		default:
			throw new TransformationException("No imputation strategy set for imputer " + NAME);
		}
	}


	@Override
	public boolean isFitted() {
		return substitutions != null;
	}

	@Override
	public SubSet fitAndTransform(SubSet data) throws TransformationException {
		fit(data);

		return transform(data);
	}

	@Override
	public SubSet transform(SubSet data) throws IllegalStateException {
		LOGGER.debug("Applying transformer {}", this);
		if (substitutions == null)
			throw new IllegalStateException("Transformer " + NAME + " not fitted yet");
		if (substitutions.isEmpty())
			return inPlace ? data : data.clone();

		SubSet transformed = inPlace ? data : data.clone();

		int numAlt = 0;
		for (DataRecord r : transformed) {
			boolean alt = transformGetIfAltered(r.getFeatures());
			numAlt += (alt? 1 : 0);
		}

		info = new TransformInfo(0, numAlt);

		return transformed;
	}

	/**
	 * Makes the transformation <i>in place</i>, clone the FeatureVector prior to this method in case
	 * the transformation should yield a new instance
	 * @param vector The vector to transform
	 * @return returns <code>true</code> if a change was made to the vector, otherwise <code>false</code>
	 * @throws IllegalStateException In case the transformer was not fitted yet
	 * @throws TransformationException If an error occurs during execution
	 */
	public boolean transformGetIfAltered(FeatureVector vector) 
			throws IllegalStateException, TransformationException {

		if (substitutions== null)
			throw new IllegalStateException("Transformer " + NAME + " not fitted yet");
		if (substitutions.isEmpty())
			return false;

		if (!vector.containsMissingFeatures())
			return false;
		boolean altered = false;
		try {
			for (Map.Entry<Integer, Double> sub : substitutions.entrySet()) {
				Double currVal = vector.getFeature(sub.getKey());
				if (currVal == null || currVal.isNaN()) {
					vector.setFeature(sub.getKey(), sub.getValue());
					altered = true;
				}
			}
		} catch (RuntimeException e) {
			LOGGER.debug("Failed imputing vector",e);
			throw new TransformationException(e);
		}
		return altered;
	}

	@Override
	public FeatureVector transform(FeatureVector object) throws IllegalStateException, TransformationException {
		FeatureVector transformed = inPlace ? object : object.clone();
		transformGetIfAltered(transformed);
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
	public SingleFeatureImputer clone() {
		SingleFeatureImputer cl = new SingleFeatureImputer();
		cl.setColumns(getColumns());
		if (substitutions != null)
			cl.substitutions = new HashMap<>(substitutions);
		if (fixedValue != null)
			cl.fixedValue = Double.valueOf(fixedValue);
		cl.strategy = strategy;
		return cl;
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		List<ConfigParameter> params = new ArrayList<>();
		params.add(new EnumConfig.Builder<>(Arrays.asList(CONFIG_IMPUTE_STRATEGY_PARAM_NAME),EnumSet.allOf(ImputationStrategy.class),ImputationStrategy.MEAN).build());
		params.add(new NumericConfig.Builder(Arrays.asList(CONFIG_FIXED_IMPUTE_VALUE_PARAM_NAME), 0).build());

		params.addAll(super.getConfigParameters());
		return params;
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
		Map<String,Object> unUsedParams = new HashMap<>();
		for (Map.Entry<String, Object> p : params.entrySet()) {
			if (p.getKey().equalsIgnoreCase(CONFIG_IMPUTE_STRATEGY_PARAM_NAME)) {
				strategy = ImputationStrategy.valueOf(p.getValue().toString().toUpperCase());
			} else if (p.getKey().equalsIgnoreCase(CONFIG_FIXED_IMPUTE_VALUE_PARAM_NAME)) {
				fixedValue = TypeUtils.asDouble(p.getValue());
			} else {
				unUsedParams.put(p.getKey(), p.getValue());
			}
		}
		if (!unUsedParams.isEmpty())
			super.setConfigParameters(unUsedParams);

	}

	public String toString() {
		return NAME;
	}

}

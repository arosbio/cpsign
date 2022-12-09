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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.rank.Percentile;
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
import com.google.common.collect.Range;

/**
 * The <code>Standardizer</code> applies Gaussian standard normalization to normalize/scale data
 * to zero mean and unit variance. 
 *  
 * @author staffan
 *
 */
public class RobustScaler extends ColumnTransformer implements FeatureScaler, Aliased {

	public static final String NAME = "RobustScaler";
	public static final String[] ALIASES = new String[]{"QuantileScaler"};

	private static final List<String> UPPER_QUANT_PARAM_NAMES = Arrays.asList("upperQuantile", "maxQuantile");
	private static final List<String> LOWER_QUANT_PARAM_NAMES = Arrays.asList("lowerQuantile", "minQuantile");
	private static final Logger LOGGER = LoggerFactory.getLogger(RobustScaler.class);
	private static final Range<Double> ALLOWED_LOWER_QUANTILE = Range.openClosed(0d, 50d);
	private static final Range<Double> ALLOWED_UPPER_QUANTILE = Range.closedOpen(50d, 100d);
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -5896649582508058298L;

	private double lowerQuantile = 25;
	private double upperQuantile = 75;
	private boolean inPlace = true;

	/*
	 *  The scale factors (column -> {median, pUpper-pLower})
	 */
	private Map<Integer,double[]> scaleFactors;

	private transient TransformInfo info;

	/**
	 * Default scaler applies to all features and using default quantiles ( 25% to 75% ), or quartile Q1-Q3
	 */
	public RobustScaler() {
	}

	public RobustScaler(double lowerQuantile, double upperQuantile) {
		this();
		assertValidRange(lowerQuantile, upperQuantile);
		this.lowerQuantile = lowerQuantile;
		this.upperQuantile = upperQuantile;
	}

	public RobustScaler(ColumnSpec columns) {
		super(columns);
	}

	public RobustScaler(ColumnSpec columns, double lowerQuantile, double upperQuantile) {
		this(columns);
		assertValidRange(lowerQuantile, upperQuantile);
		this.lowerQuantile = lowerQuantile;
		this.upperQuantile = upperQuantile;
	}

	public Map<Integer,double[]> getScaleFactors(){
		return scaleFactors;
	}

	public String getDescription() {
		return "The robust scaler normalize features based on interquartile range (IQR). It behaves similarly to the standardizer but instead subtracts the median and scales the size based on percentiles/quantiles "
				+ "(default is scaling to the 25 - 75 percentile range). By changing the lower and upper quantile the scaling becomes more or less sensitive to outliers. " + CONVERTING_SPARSE_TO_DENSE_WARNING_MSG; 
	}

	public String toString() {
		return NAME;
	}

	@Override
	public boolean isTransformInPlace() {
		return inPlace;
	}

	@Override
	public RobustScaler transformInPlace(boolean inPlace) {
		this.inPlace = inPlace;
		return this;
	}

	public RobustScaler setQuantileRange(double lower, double upper) {
		assertValidRange(lower, upper);
		this.lowerQuantile=lower;
		this.upperQuantile = upper;
		return this;
	}

	public double getLowerQuantile() {
		return lowerQuantile;
	}

	public double getUpperQuantile() {
		return upperQuantile;
	}

	private void assertValidRange(double lower, double upper) {
		if (upper <= lower)
			throw new IllegalArgumentException("Lower quantile must be a smaller number than the upper, got invalid quantile range ["+lower + ".."+upper+']');
		if (! ALLOWED_LOWER_QUANTILE.contains(lower)) {
			throw new IllegalArgumentException("Invalid "+LOWER_QUANT_PARAM_NAMES.get(0) +" '" + lower + "' - value must be in the range "+ALLOWED_LOWER_QUANTILE);
		}
		if (! ALLOWED_UPPER_QUANTILE.contains(upper)) {
			throw new IllegalArgumentException("Invalid "+UPPER_QUANT_PARAM_NAMES.get(0) + " '" + upper + "' - value must be in the range "+ ALLOWED_UPPER_QUANTILE);
		}
	}

	public RobustScaler clone() {
		RobustScaler clone = new RobustScaler();
		if (scaleFactors != null)
			clone.scaleFactors = new HashMap<>(scaleFactors);
		clone.setColumns(getColumns().clone());
		clone.lowerQuantile = lowerQuantile;
		clone.upperQuantile = upperQuantile;
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
	public RobustScaler fit(Collection<DataRecord> data) throws TransformationException {
		LOGGER.debug("Fitting transformer {}", this);

		assertValidRange(lowerQuantile, upperQuantile);

		if (data.iterator().next().getFeatures() instanceof SparseVector) {
			fitSparseData(data);
		} else {
			fitNonSparseData(data);
		}

		LOGGER.debug("Finished fitting transformer");

		return this;
	}

	@SuppressWarnings("unchecked")
	private void fitSparseData(Collection<DataRecord> data) throws TransformationException {
		int maxFeatIndex = DataUtils.getMaxFeatureIndex(data);

		if (!getColumns().useAll()) {
			LOGGER.debug("Fitting transformer using a subset of features");
			fitSparseDataSubsetFeatures(data);
			return;
		}

		int nRec = data.size();
		List<Double>[] colLists = new List[maxFeatIndex+1];
		// for (int i=0; i<nRec; i++) {
		for (DataRecord r : data){
			FeatureVector v = r.getFeatures();
			for (Feature f : v) {
				List<Double> col = colLists[f.getIndex()];
				if (col == null) {
					colLists[f.getIndex()] = new ArrayList<>();
					col = colLists[f.getIndex()];
				}
				col.add(f.getValue());
			}
		}

		// Get the values
		scaleFactors = new HashMap<>();
		for (int col : getColumns().getColumns(maxFeatIndex) ) {
			List<Double> vals = colLists[col];
			if (vals == null || vals.isEmpty()) {
				scaleFactors.put(col, new double[] {0,1});
				continue;
			}
			double[] colValues = new double[nRec];
			fillArray(colValues, vals); 
			scaleFactors.put(col, fitOneFeature(colValues));
			colLists[col].clear(); // might be pre-optimization, but try to clear up some memory
		}

	}

	private void fitSparseDataSubsetFeatures(Collection<DataRecord> data) throws TransformationException {
		int maxFeatIndex = DataUtils.getMaxFeatureIndex(data);

		List<Integer> indices = getColumns().getColumns(maxFeatIndex);
		int maxIndex = indices.get(indices.size()-1); // This list should always be sorted! Collections.max(indices);

		Map<Integer,List<Double>> featuresMap = new HashMap<>();
		int nRec = data.size();

		for (DataRecord r : data) {
			for (Feature f : r.getFeatures()) {
				int index = f.getIndex();
				if (index > maxIndex)
					break;

				if (indices.contains(index)) {
					if (f instanceof MissingValueFeature)
						throw new TransformationException("Transformation using " + NAME + " not possible on missing-data features");

					if (!featuresMap.containsKey(index))
						featuresMap.put(index, new ArrayList<>());

					featuresMap.get(index).add(f.getValue());

				}
			}

		}

		// Compute the values
		scaleFactors = new HashMap<>();
		for (int col : featuresMap.keySet()) {
			double[] colValues = new double[nRec];
			fillArray(colValues, featuresMap.get(col)); 
			scaleFactors.put(col, fitOneFeature(colValues));
		}

	}

	private static void fillArray(double[] arr, List<Double> listVals) {
		for (int i=0; i<listVals.size(); i++) {
			arr[i] = listVals.get(i);
		}
	}


	private void fitNonSparseData(Collection<DataRecord> data) {
		scaleFactors = new HashMap<>();
		for (int col: getColumns().getColumns(DataUtils.getMaxFeatureIndex(data))) {
			scaleFactors.put(col, fitOneFeature(data, col));
		}
	}

	private double[] fitOneFeature(Collection<DataRecord> recs, int index){
		List<Double> column = DataUtils.extractColumn(recs, index);

		// Verify no null or NaN
		if (CollectionUtils.containsNullOrNaN(column)) {
			throw new TransformationException("Transformation using " + NAME + " not possible on missing-data features");
		}
		return fitOneFeature(CollectionUtils.toArray(column));

	}

	private double[] fitOneFeature(double[] vals) {
		Percentile calc = new Percentile();
		calc.setData(vals);

		double median = calc.evaluate(50);
		double low = calc.evaluate(lowerQuantile);
		double high = calc.evaluate(upperQuantile);	

		// do nothing with 0 "variance" features, avoid division with 0
		double scale = high-low;
		if (scale < 1e-10) {
			scale=1d;
		}

		return new double[] {median, scale}; 
	}

	private double transformOneFeature(double old, double median, double scale) {
		return (old - median) / scale;
	}


	@Override
	public SubSet fitAndTransform(SubSet data) throws TransformationException {
		fit(data);

		return transform(data);
	}

	@Override
	public SubSet transform(SubSet data) throws IllegalStateException {
		if (scaleFactors == null || scaleFactors.isEmpty()) {
			throw new IllegalStateException("Transformer " + NAME + " not fitted yet");
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
		if (scaleFactors == null || scaleFactors.isEmpty()) {
			throw new IllegalStateException("Transformer " + NAME + " not fit yet");
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
		return toReturn;
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
		params.add(new NumericConfig.Builder(LOWER_QUANT_PARAM_NAMES, 25)
			.range(ALLOWED_LOWER_QUANTILE)
			.description("The percentile to use as lower bound (i.e. compute a measure of variance based on)")
			.build());
		params.add(new NumericConfig.Builder(UPPER_QUANT_PARAM_NAMES, 75)
			.range(ALLOWED_UPPER_QUANTILE)
			.description("The percentile to use as upper bound (i.e. compute a measure of variance based on)")
			.build());
		params.addAll(super.getConfigParameters());
		return params;
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
		Map<String,Object> toPassOn = new HashMap<>();

		Double min = null, max = null; 
		for (Map.Entry<String, Object> kv: params.entrySet()) {

			if (CollectionUtils.containsIgnoreCase(LOWER_QUANT_PARAM_NAMES, kv.getKey())) {
				min = TypeUtils.asDouble(kv.getValue());
			} else if (CollectionUtils.containsIgnoreCase(UPPER_QUANT_PARAM_NAMES, kv.getKey())) {
				max = TypeUtils.asDouble(kv.getValue());
			} else {
				toPassOn.put(kv.getKey(), kv.getValue());
			}

		}

		// pass along stuff to super-type before making any change to internal state
		if (!toPassOn.isEmpty())
			super.setConfigParameters(toPassOn);

		assertValidRange((min!=null? min : lowerQuantile), (max!=null? max : upperQuantile));
		if (min != null)
			lowerQuantile = min;
		if (max != null)
			upperQuantile = max;


		LOGGER.debug("Updated config parameters, now using lowerQuantile: {}, and upperQuantile: {}",
				lowerQuantile, upperQuantile);
	}

}

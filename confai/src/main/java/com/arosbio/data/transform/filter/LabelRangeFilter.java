/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.transform.filter;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.BooleanConfig;
import com.arosbio.commons.config.NumericConfig;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;
import com.google.common.collect.BoundType;
import com.google.common.collect.Range;

public class LabelRangeFilter implements Filter {

	private static final Logger LOGGER = LoggerFactory.getLogger(LabelRangeFilter.class);
	private static final String CONFIG_MIN_PARAM_NAME = "min", 
			CONFIG_MAX_PARAM_NAME="max", 
			CONFIG_MIN_INCL_PARAM_NAME="minInclusive", 
			CONFIG_MAX_INCL_PARAM_NAME="maxInclusive";
	
	public static final String DESCRIPTION = "Filter that removes records with a target/label outside of a given range. Note that the range can be one-sided by only supplying min or max.";
	public static final String NAME = "FilterLabelRange";

	private transient Range<Double> range = Range.all();
	private transient TransformInfo info;
	private boolean inPlace = true;

	public LabelRangeFilter() {}

	public LabelRangeFilter(Range<Double> range) {
		this.range = range;
	}

	public LabelRangeFilter(double min, double max) throws IllegalArgumentException {
		if (max < min)
			throw new IllegalArgumentException("range min:" + min + ", max:" + max + " not allowed");
		this.range = Range.closed(min, max);
	}

	@Override
	public boolean isTransformInPlace() {
		return inPlace;
	}

	@Override
	public LabelRangeFilter transformInPlace(boolean inPlace) {
		this.inPlace = inPlace;
		return this;
	}

	@Override
	public String getDescription() {
		return DESCRIPTION; 
	}

	@Override
	public String getName() {
		return NAME;
	}

	public String toString() {
		return NAME;
	}

	public LabelRangeFilter clone() {
		LabelRangeFilter clone = new LabelRangeFilter();
		if (range != null)
			clone.range = range;
		return clone;
	}

	public LabelRangeFilter setRange(Range<Double> range) {
		this.range = range;
		return this;
	}

	public Range<Double> getRange() {
		return range;
	}

	@Override
	public boolean isFitted() {
		return true;
	}

	@Override
	public LabelRangeFilter fit(Collection<DataRecord> data) {
		// Do nothing
		return this;
	}


	@Override
	public SubSet fitAndTransform(SubSet data) throws TransformationException {
		return transform(data);
	}


	@Override
	public SubSet transform(SubSet data) throws IllegalStateException {
		if (range == null) {
			LOGGER.debug("Failed applying Filter transformer {} no range given",this);
			throw new IllegalStateException("Transformer not configured properly");
		}

		LOGGER.debug("Applying Filter transformer {} with range: {}",this, range);

		int initialSize = data.size();
		if (inPlace) {
			for (int i = data.size()-1; i >= 0; i--) {
				if (! range.contains( data.get(i).getLabel() ))
					data.remove(i);
			}
			info = new TransformInfo(initialSize - data.size(), 0);

			LOGGER.debug("Finished transformer: {}", info);

			return data;
		} else {
			SubSet transformed = new SubSet(initialSize);
			for (DataRecord r : data) {
				if (range.contains( r.getLabel() ))
					transformed.add(r.clone());
			}
			info = new TransformInfo(initialSize - transformed.size(), 0);

			LOGGER.debug("Finished transformer: {}", info);

			return transformed;
		}
	}


	@Override
	public FeatureVector transform(FeatureVector object) throws IllegalStateException {
		return object;
	}


	@Override
	public boolean appliesToNewObjects() {
		return false;
	}

	@Override
	public boolean applicableToClassificationData() {
		return false;
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
		return Arrays.asList(new NumericConfig.Builder(Arrays.asList(CONFIG_MIN_PARAM_NAME), Double.NEGATIVE_INFINITY).build(),
			new NumericConfig.Builder(Arrays.asList(CONFIG_MAX_PARAM_NAME), Double.POSITIVE_INFINITY).build(),
			new BooleanConfig.Builder(Arrays.asList(CONFIG_MIN_INCL_PARAM_NAME), true).build(),
			new BooleanConfig.Builder(Arrays.asList(CONFIG_MAX_INCL_PARAM_NAME), true).build());
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {

		Double min = null, max=null;
		BoundType lowerType=BoundType.CLOSED, upperType=BoundType.CLOSED;

		// If we have something to start from
		if (range != null) {
			if (range.hasUpperBound()) {
				max = range.upperEndpoint();
				upperType = range.upperBoundType();
			}
			if (range.hasLowerBound()) {
				min = range.lowerEndpoint();
				lowerType = range.lowerBoundType();
			}

		}

		for (Map.Entry<String, Object> p : params.entrySet()) {
			if (p.getKey().toUpperCase().equals(CONFIG_MIN_PARAM_NAME.toUpperCase())) {
				min = TypeUtils.asDouble(p.getValue());
			} else if (p.getKey().toUpperCase().equals(CONFIG_MAX_PARAM_NAME.toUpperCase())) {
				max = TypeUtils.asDouble(p.getValue());
			} else if (p.getKey().toUpperCase().equals(CONFIG_MIN_INCL_PARAM_NAME.toUpperCase())) {
				lowerType = asBound(p.getKey(), p.getValue());
			} else if (p.getKey().toUpperCase().equals(CONFIG_MAX_INCL_PARAM_NAME.toUpperCase())) {
				upperType = asBound(p.getKey(), p.getValue());
			} else {
				throw new IllegalArgumentException("Parameter " + p.getKey() + " not allowed for Transformer " + NAME);
			}

		}
		if (min != null && max != null)
			range = Range.range(min, lowerType, max, upperType);
		else if (min != null)
			range = Range.downTo(min, lowerType);
		else if (max != null)
			range = Range.upTo(max, upperType);
		else
			range = Range.all();

		LOGGER.debug("Allowed label range set to: {}", range);
	}

	private BoundType asBound(String paramName, Object input) {
		try {
			boolean isIncl = TypeUtils.asBoolean(input);
			return (isIncl ? BoundType.CLOSED : BoundType.OPEN);
		} catch (Exception e) {
			throw new IllegalArgumentException("Parameter " + paramName + " should be either true or false, got: " + input);
		}
	}

}

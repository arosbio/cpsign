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

import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;

/**
 * A Filter that removes all records that contain one or more missing features
 * @author staffan
 *
 */
public class MissingDataFilter implements Filter {

	private static final String NAME = "FilterMissingData";

	private static final Logger LOGGER = LoggerFactory.getLogger(MissingDataFilter.class);

	private boolean inPlace = true;
	private transient TransformInfo info;

	@Override
	public boolean isTransformInPlace() {
		return inPlace;
	}

	@Override
	public MissingDataFilter transformInPlace(boolean inPlace) {
		this.inPlace = inPlace;
		return this;
	}

	@Override
	public String getDescription() {
		return "Filter that removes all records that have missing data for any feature."; 
	}

	@Override
	public String getName() {
		return NAME;
	}

	public String toString() {
		return NAME;
	}

	public MissingDataFilter clone() {
		return new MissingDataFilter();
	}

	@Override
	public boolean isFitted() {
		return true;
	}

	@Override
	public MissingDataFilter fit(Collection<DataRecord> data) {
		return this;
	}

	@Override
	public SubSet fitAndTransform(SubSet data) throws TransformationException {
		return transform(data);
	}

	@Override
	public SubSet transform(SubSet data) throws IllegalStateException {
		if (data.isEmpty())
			return inPlace ? data : new SubSet(data.getDataType());
		LOGGER.debug("Applying Filter transformer {}" + this);

		int initialSize = data.size();

		if (inPlace) {
			for (int i=data.size()-1; i>=0; i--) {
				if (data.get(i).getFeatures().containsMissingFeatures())
					data.remove(i);
			}

			info = new TransformInfo(initialSize-data.size(), 0);

			LOGGER.debug("Finished transformer: {}" + info);

			return data;
		} else {
			SubSet transformed = new SubSet(data.size());
			for (DataRecord r : data) {
				if (! r.getFeatures().containsMissingFeatures())
					transformed.add(r.clone());
			}
			info = new TransformInfo(initialSize-transformed.size(), 0);

			LOGGER.debug("Finished transformer: {}" + info);

			return transformed;
		}
	}

	/**
	 * Does nothing
	 */
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
		return Arrays.asList();
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) 
		throws IllegalStateException, IllegalArgumentException {}

}

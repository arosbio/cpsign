/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.transform.duplicates;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.MathUtils;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.NumericConfig;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.transform.duplicates.DuplicateResolvingUtils.DuplicateEntry;
import com.google.common.collect.ImmutableList;

public class RemoveContradictoryRecords implements DuplicatesResolverTransformer {

	public static final String DESCRIPTION = "Both classification and regression - remove all contradictory records where the target/label differs. "
			+ "For regression it's convenient to include an allowed-difference as "
			+ "floating point arithmetic and difference in number of decimal numbers can cause issues otherwise.";
	public static final String NAME = "RemoveContradictory";
	private static final Logger LOGGER = LoggerFactory.getLogger(RemoveContradictoryRecords.class);
	private static final String PARAM_ALLOWED_DIFFERENCE = "allowedDiff";
	
	private double maxDiff = 0.000001;
	private boolean inPlace = true;
	private transient TransformInfo info;
	

	public RemoveContradictoryRecords() {}

	public RemoveContradictoryRecords(double maximumDifference) {
		this.maxDiff = Math.max(maximumDifference, 0);
	}
	
	@Override
	public boolean isTransformInPlace() {
		return inPlace;
	}

	@Override
	public RemoveContradictoryRecords transformInPlace(boolean inPlace) {
		this.inPlace = inPlace;
		return this;
	}
	
	@Override
	public String getDescription() {
		return DESCRIPTION; 
	}
	
	public RemoveContradictoryRecords clone() {
		return new RemoveContradictoryRecords(maxDiff);
	}

	@Override
	public String getName() {
		return NAME;
	}
	
	public String toString() {
		return NAME;
	}
	
	@Override
	public boolean isFitted() {
		return true;
	}

	@Override
	public RemoveContradictoryRecords fit(Collection<DataRecord> data) {
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
		LOGGER.debug("Applying Duplicate-resolving transformer {}", this);
		
		SubSet transformed = inPlace ? data : data.clone();
		
		int initialSize = transformed.size();
		int numAltered = 0;

		Set<DuplicateEntry> dups = DuplicateResolvingUtils.findDuplicates(transformed);


		for (DuplicateEntry entry: dups) {
			double min=Collections.min(entry.getLabels()); 
			double max=Collections.max(entry.getLabels());

			if (max - min > maxDiff) {
				// Remove the record completely
				transformed.remove(entry.getRemainingRecord());
			} else {
				// Use the mean value
				numAltered++;
				entry.getRemainingRecord().setLabel(MathUtils.mean(entry.getLabels()));
			}
		}

		info = new TransformInfo(initialSize-transformed.size(), numAltered);
		
		LOGGER.debug("Finished transformer: {}", info);

		return transformed;
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
		return ImmutableList.of(new NumericConfig.Builder(Arrays.asList(PARAM_ALLOWED_DIFFERENCE), 0d).build());
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) 
			throws IllegalStateException, IllegalArgumentException {
		
		for (Map.Entry<String, Object> p : params.entrySet()) {
			if (p.getKey().equalsIgnoreCase(PARAM_ALLOWED_DIFFERENCE)) {
				try {
					maxDiff = Math.max(TypeUtils.asDouble(p.getValue()),0);
				} catch(Exception e) {
					throw new IllegalArgumentException("Parameter " + PARAM_ALLOWED_DIFFERENCE + " must be numerical value");
				}
			} else {
				throw new IllegalArgumentException("Parameter " + p.getKey() + " not allowed for transformer " + NAME);
			}
		}
	}
}
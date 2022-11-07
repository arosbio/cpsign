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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.MathUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.transform.duplicates.DuplicateResolvingUtils.DuplicateEntry;

public class KeepMedianLabel implements DuplicatesResolverTransformer {

	public static final String DESCRIPTION = "For regression problems - use the median of the duplicate records.";
	public static final String NAME = "KeepMedianLabel";

	private static final Logger LOGGER = LoggerFactory.getLogger(KeepMedianLabel.class);

	private transient TransformInfo info;
	private boolean inPlace = true;

	@Override
	public boolean isTransformInPlace() {
		return inPlace;
	}

	@Override
	public KeepMedianLabel transformInPlace(boolean inPlace) {
		this.inPlace = inPlace;
		return this;
	}
	
	public KeepMedianLabel clone() {
		return new KeepMedianLabel();
	}

	@Override
	public String getDescription() {
		return DESCRIPTION;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public boolean isFitted() {
		return true;
	}

	@Override
	public void fit(Collection<DataRecord> data) {
	}

	@Override
	public SubSet fitAndTransform(SubSet data) throws TransformationException {
		return transform(data);
	}

	@Override
	public SubSet transform(SubSet data) throws IllegalStateException {
		LOGGER.debug("Applying Duplicate-resolving transformer {}", this);
		
		SubSet transformed = inPlace ? data : data.clone();
		
		int initalSize = transformed.size();

		Set<DuplicateEntry> dups = DuplicateResolvingUtils.findDuplicates(transformed);

		for (DuplicateEntry entry : dups) {
			entry.getRemainingRecord().setLabel(MathUtils.median(entry.getLabels()));
		}

		info = new TransformInfo(initalSize - transformed.size(), dups.size());

		LOGGER.debug("Finished transformer: " + info);

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
		return new ArrayList<>();
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
		// do nothing
	}
	
	public String toString() {
		return NAME;
	}
}
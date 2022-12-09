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
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.transform.ColumnSpec;
import com.google.common.collect.Range;

/**
 * The {@link DropColumnSelector} is mainly intended to be used for testing purposes, 
 * although it can be used after listing of features to remove specific ones manually. 
 * @author staffan
 *
 */
public class DropColumnSelector implements FeatureSelector {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1305724239301688987L;
	public static final String DESCRIPTION = "Manual dropping of specific column indices.";
	public static final String TRANSFORMER_NAME = "ManualDropColumns";

	private static final Logger LOGGER = LoggerFactory.getLogger(DropColumnSelector.class);

	private ColumnSpec toRemove;
	private int maxColIndex = -1;
	private boolean inPlace = true;

	private transient TransformInfo info;

	public DropColumnSelector() {}
	
	public DropColumnSelector(int... indices) {
		this.toRemove = new ColumnSpec(indices);
	}
	
	public DropColumnSelector(Collection<Integer> indices) {
		this.toRemove = new ColumnSpec(indices);
	}
	
	public DropColumnSelector(ColumnSpec toRemove) {
		this.toRemove = toRemove;
	}
	
	@Override
	public boolean isTransformInPlace() {
		return inPlace;
	}

	@Override
	public DropColumnSelector transformInPlace(boolean inPlace) {
		this.inPlace = inPlace;
		return this;
	}
	
	@Override
	public String getName() {
		return TRANSFORMER_NAME;
	}
	
	@Override
	public String getDescription() {
		return DESCRIPTION;
	}
	
	public String toString() {
		return TRANSFORMER_NAME;
	}
	
	@Override
	public boolean applicableToClassificationData() {
		return true;
	}
	@Override
	public boolean applicableToRegressionData() {
		return true;
	}

	public void setColumnsToRemove(Collection<Integer> toRemove) {
		this.toRemove = new ColumnSpec(toRemove);
	}


	@Override
	public DropColumnSelector fit(Collection<DataRecord> data) throws TransformationException {
		if (toRemove == null)
			throw new TransformationException("Cannot fit transformer " + TRANSFORMER_NAME + ": No columns specified to remove");
		maxColIndex = DataUtils.getMaxFeatureIndex(data);
		
		if (toRemove.isRangeBased()) {
			// Cap it to the maximum index 
			Range<Integer> range = toRemove.getRange();
			toRemove = new ColumnSpec(range.intersection(Range.closed(0,maxColIndex)));
		} 
		LOGGER.debug("Finished fitting feature selector {}: will remove column indices {}",TRANSFORMER_NAME, toRemove);
		return this;
	}

	@Override
	public boolean isFitted() {
		return maxColIndex > -1 && toRemove != null;
	}

	@Override
	public SubSet fitAndTransform(SubSet data) throws TransformationException {
		fit(data);

		return transform(data);
	}

	@Override
	public SubSet transform(SubSet data) throws IllegalStateException {
		if (!isFitted())
			throw new IllegalStateException("Transformer " + TRANSFORMER_NAME + " not fitted yet");
		if (data.isEmpty())
			return inPlace ? data : new SubSet(data.getDataType());
		LOGGER.debug("Applying transformer {}", this);
		
		
		if (inPlace) {
			for (DataRecord r : data) {
				transform(r.getFeatures());
			}

			info = new TransformInfo(0, data.size(), toRemove.getNumColumns(maxColIndex));

			return data;
		} else {
			SubSet transformed = new SubSet(data.size());
			for (DataRecord r : data) {
				transformed.add(new DataRecord(r.getLabel(), transform(r.getFeatures())));
			}
			info = new TransformInfo(0, data.size(), toRemove.getNumColumns(maxColIndex));
			return transformed;
		}
		
	}

	@Override
	public FeatureVector transform(FeatureVector object) throws IllegalStateException {
		if (!isFitted())
			throw new IllegalStateException("Transformer " + TRANSFORMER_NAME + " not fitted yet");

		FeatureVector v = inPlace ? object : object.clone();
		try {
			if (toRemove.isRangeBased())
				v.removeFeatureIndices(toRemove.getRange());
			else
				v.removeFeatureIndices(toRemove.getColumns(maxColIndex));
			return object;
		} catch (IndexOutOfBoundsException e) {
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
	public DropColumnSelector clone() {
		DropColumnSelector clone = new DropColumnSelector();
		if (toRemove != null)
			clone.toRemove = toRemove.clone(); 
		clone.inPlace = inPlace;
		clone.maxColIndex = maxColIndex;
		return clone;
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		return new ArrayList<>();
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
	}

	@Override
	public List<Integer> getFeatureIndicesToRemove() throws IllegalStateException {
		if (! isFitted())
			throw new IllegalStateException("Transformer not fitted yet");
		return new ArrayList<>(toRemove.getColumns(maxColIndex));
	}

}

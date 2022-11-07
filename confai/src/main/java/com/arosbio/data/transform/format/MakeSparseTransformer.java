/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.transform.format;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.SparseVector;
import com.arosbio.data.transform.Transformer;

public class MakeSparseTransformer implements FormatTransformer{

	private static final String NAME = "MakeSparse";
	private transient TransformInfo info;
	private boolean inPlace = true;

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String getDescription() {
		return "Transforms data to a Sparse representation (i.e. not storing features with a value equal to 0). Note that this is the default "
				+ "format of data in CPSign.";
	}

	public String toString() {
		return NAME;
	}
	
	@Override
	public boolean isTransformInPlace() {
		return inPlace;
	}
	
	@Override
	public MakeSparseTransformer transformInPlace(boolean inPlace) {
		this.inPlace = inPlace;
		return this;
	}
	
	/**
	 * Doesn't do anything - pass through only
	 */
	@Override
	public void fit(Collection<DataRecord> data) throws TransformationException {}

	@Override
	public boolean isFitted() {
		return true;
	}

	@Override
	public SubSet fitAndTransform(SubSet data) throws TransformationException {
		return transform(data);
	}

	@Override
	public SubSet transform(SubSet data) throws IllegalStateException {

		if (inPlace) {
			for (DataRecord r : data) {
				r.setFeatures(transform(r.getFeatures()));
			}
			info = new TransformInfo(0, data.size());
			return data;
		} else {
			List<DataRecord> newRecords = new ArrayList<>(data.size());
			for (DataRecord r : data) {
				newRecords.add(new DataRecord(r.getLabel(), transform(r.getFeatures())));
			}

			info = new TransformInfo(0, newRecords.size());

			return new SubSet(newRecords);
		}
	}

	@Override
	public SparseVector transform(FeatureVector object) throws IllegalStateException {
		if (inPlace && object instanceof SparseVector) {
			return (SparseVector) object;
		}
		return new SparseVector(object);
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
	public Transformer clone() {
		return new MakeSparseTransformer();
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		return new ArrayList<>();
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
		if (!params.isEmpty()) {
			throw new IllegalArgumentException("Transformer " + NAME + " does not take any parameters");
		}
	}

}

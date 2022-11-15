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

import com.arosbio.commons.GlobalConfig;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.DenseFloatVector;
import com.arosbio.data.DenseVector;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.transform.Transformer;

public class MakeDenseTransformer implements FormatTransformer {

	private static final String NAME = "MakeDense";

	private int maxFeatureIndex = -1;
	private transient TransformInfo info;
	private boolean useDoublePrecision = ! GlobalConfig.getInstance().isMemSaveMode();
	private boolean inPlace = true;

	@Override
	public boolean isTransformInPlace() {
		return inPlace;
	}
	
	@Override
	public MakeDenseTransformer transformInPlace(boolean inPlace) {
		this.inPlace = inPlace;
		return this;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String getDescription() {
		return "Transforms data from to a dense representation (which could improve memory/runtime when dealing with non-sparse data). Note that "
				+ "this is an experimental feature with limited amount of testing.";
	}

	public String toString() {
		return NAME;
	}

	public MakeDenseTransformer useDoublePrecision(boolean useDouble) {
		this.useDoublePrecision = useDouble;
		return this;
	}

	public boolean useDoublePrecision(){
		return useDoublePrecision;
	}

	@Override
	public MakeDenseTransformer fit(Collection<DataRecord> data) throws TransformationException {
		maxFeatureIndex = DataUtils.getMaxFeatureIndex(data);

		return this;
	}

	@Override
	public boolean isFitted() {
		return maxFeatureIndex > -1;
	}

	@Override
	public SubSet fitAndTransform(SubSet data) throws TransformationException {
		fit(data);

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
	public FeatureVector transform(FeatureVector object) throws IllegalStateException {
		if (! isFitted()) {
			throw new IllegalStateException("Transformer " + NAME + " not fitted yet");
		}
		
		if (useDoublePrecision) {
			if (inPlace && object instanceof DenseVector)
				return object;
			return new DenseVector(object, maxFeatureIndex);
		} else {
			if (inPlace && object instanceof DenseFloatVector)
				return object;
			return new DenseFloatVector(object, maxFeatureIndex);
		}
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
		MakeDenseTransformer clone = new MakeDenseTransformer();
		clone.maxFeatureIndex = maxFeatureIndex;
		return clone;
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

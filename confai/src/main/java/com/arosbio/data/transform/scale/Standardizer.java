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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
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
 * The <code>Standardizer</code> applies Gaussian standard normalization to normalize/scale data
 * to zero mean and unit variance, for <b>each column individually</b>. 
 *  
 * @author staffan
 *
 */
public class Standardizer extends ColumnTransformer implements FeatureScaler, Aliased {

	public static final String NAME = "Standardizer";
	public static final String[] ALIASES = new String[] {"Normalizer"};
	private static final Logger LOGGER = LoggerFactory.getLogger(Standardizer.class);
	/**
	 * 
	 */
	private static final long serialVersionUID = -5896649582508058298L;

	/**
	 * The scale factors (column -&gt; {mean, standard deviation})
	 */
	private Map<Integer,double[]> scaleFactors;
	private transient TransformInfo info;
	private boolean inPlace = true;
	
	/**
	 * Standardize all columns (features / attributes)
	 */
	public Standardizer() {}
	
	public Standardizer(ColumnSpec columns) {
		super(columns);
	}
	
	@Override
	public boolean isTransformInPlace() {
		return inPlace;
	}

	@Override
	public Standardizer transformInPlace(boolean inPlace) {
		this.inPlace = inPlace;
		return this;
	}
	
	@Override
	public String getDescription() {
		return "Standardization/normalization of features to standard gaussian form (zero-mean and unit-variance). "+ CONVERTING_SPARSE_TO_DENSE_WARNING_MSG; 
	}
	
	public Standardizer clone() {
		Standardizer clone = new Standardizer();
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
	public Standardizer fit(Collection<DataRecord> data) throws TransformationException {
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
		int maxFeatIndex = DataUtils.getMaxFeatureIndex(data);
		if (! getColumns().useAll()) {
			LOGGER.debug("Fittning using a subset of feature indicies");
			fitSparseDataOnlySomeFeats(data, maxFeatIndex);
			return;
		} 
		
		SummaryStatistics[] colMaxVector = new SummaryStatistics[maxFeatIndex+1];
		
		// Go through the data
		for (DataRecord r : data) {
			for (Feature f : r.getFeatures()) {
				if (f instanceof MissingValueFeature)
					throw new TransformationException("Transformation using " + NAME + " not possible on missing-data features");
				
				if (colMaxVector[f.getIndex()] == null) {
					colMaxVector[f.getIndex()] = new SummaryStatistics();
				}
				
				colMaxVector[f.getIndex()].addValue(f.getValue());
			}
		}
		
		int nRec = data.size();
		
		scaleFactors = new HashMap<>();
		for (int col: getColumns().getColumns(maxFeatIndex)) {
			SummaryStatistics ss = colMaxVector[col];
			if (ss == null) {
				scaleFactors.put(col, new double[] {0,1});
				continue;
			}
			
			// Add 0s corresponding to the sparse features
			int toAdd = nRec - (int) ss.getN();
			for (int i=0; i<toAdd; i++) {
				ss.addValue(0);
			}
			
			double mean = ss.getMean();
			double std = ss.getStandardDeviation();
			// Cannot use standard deviation of 0, use same strategy as in Sklearn - use 1 instead (the result will be 0 anyways)
			if (std == 0)
				std = 1d;
			
			scaleFactors.put(col, new double[] {mean, std});
		}
		
	}
	
	private void fitSparseDataOnlySomeFeats(Collection<DataRecord> data, int maxFeatIndex) throws TransformationException {
		
		List<Integer> indices = getColumns().getColumns(maxFeatIndex);
		int maxIndex = indices.get(indices.size()-1); // This list should always be sorted! Collections.max(indices);
		
		
		Map<Integer, SummaryStatistics> statsMap = new HashMap<>();
		
		// Go through the data
		for (DataRecord r : data) {
			for (Feature f : r.getFeatures()) {
				int index = f.getIndex();
				if (index > maxIndex)
					break;
				
				if (indices.contains(index)) {
					if (f instanceof MissingValueFeature)
						throw new TransformationException("Transformation using " + NAME + " not possible on missing-data features");
					
					// Init SS object for this feature
					if (!statsMap.containsKey(index))
						statsMap.put(index, new SummaryStatistics());
					
					// Update the stats
					statsMap.get(index).addValue(f.getValue());
					
				}
				
			}
		}
		
		int nRec = data.size();
		
		scaleFactors = new HashMap<>();
		for (int col: getColumns().getColumns(maxFeatIndex)) {
			SummaryStatistics ss = statsMap.get(col);
			if (ss == null) {
				// If no statistics - no scaling
				scaleFactors.put(col, new double[] {0,1});
				continue;
			}
			
			// Add 0s corresponding to the sparse features
			int toAdd = nRec - (int) ss.getN();
			for (int i=0; i<toAdd; i++) {
				ss.addValue(0);
			}
			
			double mean = ss.getMean();
			double std = ss.getStandardDeviation();
			// Cannot use standard deviation of 0, use same strategy as in Sklearn - use 1 instead (the result will be 0 anyways)
			if (std == 0)
				std = 1d;
			
			scaleFactors.put(col, new double[] {mean, std});
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
			throw new TransformationException("Transformation using " + NAME + " not possible on missing-data features");
		}
		SummaryStatistics ss = new SummaryStatistics();
		for (double v : column) {
			ss.addValue(v);
		}
		double mean = ss.getMean();
		double std = ss.getStandardDeviation();
		// Cannot use standard deviation of 0, use same strategy as in Sklearn - use 1 instead (the result will be 0 anyways)
		if (std == 0)
			std = 1d;
		
		return new double[] {mean, std};
	}
	
	private double transformOneFeature(double old, double mean, double std) {
		return (old - mean) / std;
	}
	

	@Override
	public SubSet fitAndTransform(SubSet data) throws TransformationException {
		fit(data);
		
		return transform(data);
	}

	@Override
	public SubSet transform(SubSet data) throws IllegalStateException {
		if (scaleFactors == null || scaleFactors.isEmpty()) {
			throw new IllegalStateException("Transformer " + NAME + " not fit yet");
		}
		if (data.isEmpty())
			return inPlace ? data : new SubSet(data.getDataType());
		LOGGER.debug("Applying scaler transformer {}", this);
		
		SubSet toReturn = data;
		if (inPlace) {
			LOGGER.debug("Transforming in place");
			for (DataRecord r : data) {
				transform(r.getFeatures());
			}
		} else {
			LOGGER.debug("Generating a new SubSet from transformer - i.e. not in place");
			toReturn = new SubSet(data.size());
			for (DataRecord r : data) {
				toReturn.add(new DataRecord(r.getLabel(), transform(r.getFeatures())));
			}
		}
		
		info = new TransformInfo(0, data.size());
		
		LOGGER.debug("Finished transformer: {}", info);
		
		return toReturn;
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
	public String toString() {
		return NAME;
	}

}

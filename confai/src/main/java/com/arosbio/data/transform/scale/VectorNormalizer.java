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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.config.EnumConfig;
import com.arosbio.commons.mixins.Aliased;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.FeatureVector.Feature;

/**
 * The <code>Standardizer</code> applies Gaussian standard normalization to normalize/scale data
 * to zero mean and unit variance, for <b>each column individually</b>. 
 *  
 * @author staffan
 *
 */
public class VectorNormalizer implements VectorScaler, Aliased {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3383872273053895194L;
	private static final Logger LOGGER = LoggerFactory.getLogger(VectorNormalizer.class);
	
	public static final String NAME = "VectorNormalizer";
	public static final String[] ALIASES = new String[]{"RowNormalizer"};

	private transient TransformInfo info;
	private Norm norm = Norm.L2;
	private boolean inPlace = true;
	private boolean isFitted = false;
	
	public static enum Norm {
		L1, L2, L_INF;
	}
	
	@Override
	public boolean isTransformInPlace() {
		return inPlace;
	}

	@Override
	public VectorNormalizer transformInPlace(boolean inPlace) {
		this.inPlace = inPlace;
		return this;
	}
	
	public VectorNormalizer useNorm(Norm norm) 
			throws IllegalStateException {
		if (isFitted)
			throw new IllegalStateException("Transformer "+NAME + " already fitted, cannot change it");
		this.norm = norm;
		return this;
	}
	
	@Override
	public String getDescription() {
		return "Normalizes feature vectors to unit norm, using norm l1, l2 or l-inf/l-max."; 
	}
	
	public VectorNormalizer clone() {
		return new VectorNormalizer()
				.useNorm(norm)
				.transformInPlace(inPlace);
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
		return true;
	}

	@Override
	public VectorNormalizer fit(Collection<DataRecord> data) throws TransformationException {
		LOGGER.debug("Fitting transformer {} - nothing to do", this);
		isFitted = true;

		return this;
	}
	
//	private void fitSparseData(List<DataRecord> data) throws TransformationException {
//		int maxFeatIndex = DataUtils.getMaxFeatureIndex(data);
//		if (! getColumns().useAll()) {
//			LOGGER.debug("Fittning using a subset of feature indicies");
//			fitSparseDataOnlySomeFeats(data, maxFeatIndex);
//			return;
//		} 
//		
//		SummaryStatistics[] colMaxVector = new SummaryStatistics[maxFeatIndex+1];
//		
//		// Go through the data
//		for (DataRecord r : data) {
//			for (Feature f : r.getFeatures()) {
//				if (f instanceof MissingValueFeature)
//					throw new TransformationException("Transformation using " + NAME + " not possible on missing-data features");
//				
//				if (colMaxVector[f.getIndex()] == null) {
//					colMaxVector[f.getIndex()] = new SummaryStatistics();
//				}
//				
//				colMaxVector[f.getIndex()].addValue(f.getValue());
//			}
//		}
//		
//		int nRec = data.size();
//		
//		scaleFactors = new HashMap<>();
//		for (int col: getColumns().getColumns(maxFeatIndex)) {
//			SummaryStatistics ss = colMaxVector[col];
//			if (ss == null) {
//				scaleFactors.put(col, new double[] {0,1});
//				continue;
//			}
//			
//			// Add 0s corresponding to the sparse features
//			int toAdd = nRec - (int) ss.getN();
//			for (int i=0; i<toAdd; i++) {
//				ss.addValue(0);
//			}
//			
//			double mean = ss.getMean();
//			double std = ss.getStandardDeviation();
//			// Cannot use standard deviation of 0, use same strategy as in Sklearn - use 1 instead (the result will be 0 anyways)
//			if (std == 0)
//				std = 1d;
//			
//			scaleFactors.put(col, new double[] {mean, std});
//		}
//		
//	}
//	
//	private void fitSparseDataOnlySomeFeats(List<DataRecord> data, int maxFeatIndex) throws TransformationException {
//		
//		List<Integer> indices = getColumns().getColumns(maxFeatIndex);
//		int maxIndex = indices.get(indices.size()-1); // This list should always be sorted! Collections.max(indices);
//		
//		
//		Map<Integer, SummaryStatistics> statsMap = new HashMap<>();
//		
//		// Go through the data
//		for (DataRecord r : data) {
//			for (Feature f : r.getFeatures()) {
//				int index = f.getIndex();
//				if (index > maxIndex)
//					break;
//				
//				if (indices.contains(index)) {
//					if (f instanceof MissingValueFeature)
//						throw new TransformationException("Transformation using " + NAME + " not possible on missing-data features");
//					
//					// Init SS object for this feature
//					if (!statsMap.containsKey(index))
//						statsMap.put(index, new SummaryStatistics());
//					
//					// Update the stats
//					statsMap.get(index).addValue(f.getValue());
//					
//				}
//				
//			}
//		}
//		
//		int nRec = data.size();
//		
//		scaleFactors = new HashMap<>();
//		for (int col: getColumns().getColumns(maxFeatIndex)) {
//			SummaryStatistics ss = statsMap.get(col);
//			if (ss == null) {
//				// If no statistics - no scaling
//				scaleFactors.put(col, new double[] {0,1});
//				continue;
//			}
//			
//			// Add 0s corresponding to the sparse features
//			int toAdd = nRec - (int) ss.getN();
//			for (int i=0; i<toAdd; i++) {
//				ss.addValue(0);
//			}
//			
//			double mean = ss.getMean();
//			double std = ss.getStandardDeviation();
//			// Cannot use standard deviation of 0, use same strategy as in Sklearn - use 1 instead (the result will be 0 anyways)
//			if (std == 0)
//				std = 1d;
//			
//			scaleFactors.put(col, new double[] {mean, std});
//		}
//		
//	}
//	
//	private void fitDenseData(List<DataRecord> data) throws TransformationException {
//		scaleFactors = new HashMap<>();
//		for (int col: getColumns().getColumns(DataUtils.getMaxFeatureIndex(data))) {
//			scaleFactors.put(col, fitOneFeature(data, col));
//		}
//	}
//	
//	private double[] fitOneFeature(List<DataRecord> recs, int index){
//		List<Double> column = DataUtils.extractColumn(recs, index);
//		
//		// Verify no null or NaN
//		if (CollectionUtils.containsNullOrNaN(column)) {
//			throw new TransformationException("Transformation using " + NAME + " not possible on missing-data features");
//		}
//		SummaryStatistics ss = new SummaryStatistics();
//		for (double v : column) {
//			ss.addValue(v);
//		}
//		double mean = ss.getMean();
//		double std = ss.getStandardDeviation();
//		// Cannot use standard deviation of 0, use same strategy as in Sklearn - use 1 instead (the result will be 0 anyways)
//		if (std == 0)
//			std = 1d;
//		
//		return new double[] {mean, std};
//	}
//	
//	private double transformOneFeature(double old, double mean, double std) {
//		return (old - mean) / std;
//	}
	

	@Override
	public SubSet fitAndTransform(SubSet data) throws TransformationException {
//		fit(data);
		
		return transform(data);
	}

	@Override
	public SubSet transform(SubSet data) throws IllegalStateException {
//		if (scaleFactors == null || scaleFactors.isEmpty()) {
//			throw new IllegalStateException("Transformer " + NAME + " not fit yet");
//		}
		LOGGER.debug("Applying scaler transformer " + this);
		
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
		
		LOGGER.debug("Finished transformer: " + info);
		
		return toReturn;
	}

	@Override
	public FeatureVector transform(FeatureVector object) throws IllegalStateException {
		// First pass - compute the norm
		double norm = computeNorm(object);
		
		// Second pass - divide all feats
		FeatureVector toReturn = (inPlace ? object : object.clone());
		
		for (Feature f : toReturn) {
			toReturn.setFeature(f.getIndex(), f.getValue()/norm);
		}
		return toReturn;
	}
	
	private double computeNorm(FeatureVector v) {
		if (norm == Norm.L1) {
			return DataUtils.l1_norm(v);
		} else if (norm == Norm.L2) {
			return DataUtils.l2_norm(v);
		} else {
			return DataUtils.l_inf_norm(v);
		}
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

	
	private static final List<String> NORM_CONF = Arrays.asList("norm", "vectorNorm");
	
	@Override
	public List<ConfigParameter> getConfigParameters() {
		List<ConfigParameter> confs = new ArrayList<>();
		confs.add(new EnumConfig.Builder<>(NORM_CONF,EnumSet.allOf(Norm.class),Norm.L2).build());
		return confs;
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) 
			throws IllegalStateException, IllegalArgumentException {
		if (params == null)
			return;
		for (Map.Entry<String, Object> kv : params.entrySet()) {
			if (CollectionUtils.containsIgnoreCase(NORM_CONF, kv.getKey())) {
				try {
					if (kv.getValue() instanceof Norm) {
						norm = (Norm) kv.getValue();
					} else {
						norm = Norm.valueOf(kv.getValue().toString());
					}
				} catch (Exception e) {
					LOGGER.debug("Tried to set vector norm in {} but got incorrect input {}",this,kv.getValue());
					throw new IllegalArgumentException("Invalid config value for "+NORM_CONF.get(0) + " for transformer " + NAME);
				}
			}
		}
		
	}

}

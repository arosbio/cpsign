/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.MathUtils;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector.Feature;

public class DataUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(DataUtils.class);

	public static boolean equals(List<SparseFeature> x1, List<SparseFeature> x2){
		if (x1.size() != x2.size())
			return false;

		return x1.equals(x2); // normal list-equals
	}

	/**
	 * Does an expensive check for equality between two datasets, converts {@link FeatureVector}s into
	 * the same type. Uses 1e-10 as allowed difference between two values to be considered equal
	 * @param d1 data 1
	 * @param d2 data 2
	 * @return {@code true} if they are equal, {@code false} otherwise
	 */
	public static boolean equals(SubSet d1, SubSet d2) {
		return equals(d1,d2,1e-10);
	}

	public static boolean equals(final SubSet d1, final SubSet d2, final double tol) {
		if (d1 == d2){
			LOGGER.debug("checking equality on same reference - always the same!");
			return true;
		}
		// make sure the allowed diff is positive
		double allowedDiff = Math.max(0, tol);

		if (d1.size() != d2.size())
			return false;

		for (int i=0; i<d1.size(); i++) {
			DataRecord r1 = d1.get(i);
			DataRecord r2 = d2.get(i);

			if (Math.abs(r1.getLabel() - r2.getLabel()) > allowedDiff) {
				LOGGER.debug("Labels at index {} not matching: {} vs. {}",i,r1.getLabel(),r2.getLabel());
				return false;
			}

			if (! equals(r1.getFeatures(), r2.getFeatures(), allowedDiff)) {
				LOGGER.debug("FeatureVectors at index {} not matching",i);
				return false;
			}

		}

		return true;
	}

	public static boolean equals(FeatureVector v1, FeatureVector v2, double tol) {
		double allowedDiff = Math.max(0, tol);

		int maxIndex = Math.max(v1.getLargestFeatureIndex(), v2.getLargestFeatureIndex());
		try {
			for (int j=0; j<=maxIndex; j++) {
				// This could lead to IndexOutOfBounds - but that means they are not equal 
				if (Math.abs(v1.getFeature(j)-v2.getFeature(j)) > allowedDiff) {
					LOGGER.debug("Vectors not equal, index {} not equal: {} vs. {}",j,v1.getFeature(j),v2.getFeature(j));
					return false;
				}
			}
		} catch (IndexOutOfBoundsException e) {
			LOGGER.debug("IndexOutOfBounds when comparing two feature vectors, i.e. they are not of the same number of attributes, msg: {}", 
				e.getMessage());
			return false;
		}
		return true;
	}

	/**
	 * Generate a {@link SparseVector} from a text string. 
	 * Should be formatted like: <code>"[index]:[value] [index]:[value] .."</code>
	 *  
	 * @param vector Vector as a String
	 * @return a {@link SparseVector} for the given vector
	 * @throws IllegalArgumentException If the String could not be parsed correctly or was <code>null</code>
	 */
	public static SparseVector getSparseVector(String vector) throws IllegalArgumentException{
		LOGGER.debug("Generating SparseVector from String");
		if (vector == null)
			throw new IllegalArgumentException("Cannot send null as vector");

		List<SparseFeature> features = new ArrayList<>();
		if (vector.isEmpty())
			return new SparseVector();
		String [] nodes = vector.split("\\s");

		String currentNode;
		for (int i=0; i<nodes.length; i++){
			currentNode = nodes[i];
			String[] nodeParts = currentNode.split(":");
			if (nodeParts.length != 2)
				throw new IllegalArgumentException("Vector string not formatted properly");
			try{
				int index = Integer.parseInt(nodeParts[0].trim());
				if(index == 0)
					throw new IllegalArgumentException("Index must start at 1");
				double value = Double.parseDouble(nodeParts[1].trim());
				features.add(new SparseFeatureImpl(index, value));
			} catch(NumberFormatException e){
				throw new IllegalArgumentException("Vector string not formatted properly (could not parse values)");
			}
		}

		Collections.sort(features);

		LOGGER.debug("Generated SparseVector: {}", features);
		return new SparseVector(features);
	}

	public static Map<Double,Integer> countLabels(Dataset data){
		Map<Double, Integer> labels = new HashMap<>();
		updateMap(labels, countLabels(data.getDataset()));
		updateMap(labels, countLabels(data.getModelingExclusiveDataset()));
		updateMap(labels, countLabels(data.getCalibrationExclusiveDataset()));
		return labels;
	}

	public static Map<Double,Integer> countLabels(List<DataRecord> records){
		Map<Double, Integer> labels = new HashMap<>();
		for (DataRecord rec : records) {
			double label = rec.getLabel();
			labels.put(label, labels.getOrDefault(label,0)+1);
		}
		return labels;
	}

	private static void updateMap(Map<Double,Integer> target, Map<Double,Integer> update){
		if (update == null || update.isEmpty())
			return;
		
		Set<Double> keys = new HashSet<>(target.keySet());
		keys.addAll(update.keySet());
		for (Double k : keys){
			target.put(k, target.getOrDefault(k, 0) + update.getOrDefault(k,0));
		}
	}

	/**
	 * Find all labels in a classification data set, will stop if encountering more labels than
	 * {@link GlobalConfig#getMaxNumClasses()} and throw an exception
	 * @param records the data 
	 * @return All found labels
	 */
	public static Set<Double> findLabels(Collection<DataRecord> records){
		Set<Double> labels = new HashSet<>();
		int maxNumCls = GlobalConfig.getInstance().getMaxNumClasses();
		for (DataRecord rec : records) {
			labels.add(rec.getLabel());
			if (labels.size()>maxNumCls){
				throw new IllegalArgumentException("Finding labels only possible for classification data sets");
			}
		}
		return labels;
	}

	public static int getMaxFeatureIndex(Dataset data) {
		int max = -1;
		if (!data.getDataset().isEmpty())
			max = Math.max(getMaxFeatureIndex(data.getDataset()),max);
		if (!data.getCalibrationExclusiveDataset().isEmpty())
			max = Math.max(getMaxFeatureIndex(data.getCalibrationExclusiveDataset()),max);
		if (!data.getModelingExclusiveDataset().isEmpty())
			max = Math.max(getMaxFeatureIndex(data.getModelingExclusiveDataset()),max);
		return max;
	}
	
	/**
	 * Get the maximum feature index in the set of records
	 * @param recs Records to check
	 * @return -1 if no records, or empty {@link com.arosbio.data.FeatureVector FeatureVectors} 
	 */
	public static int getMaxFeatureIndex(Collection<DataRecord> recs) {
		int max = -1;
		for (DataRecord r : recs) {
			max = Math.max(max, r.getMaxFeatureIndex());
		}
		return max;
	}

	public static List<Double> extractColumn(Collection<DataRecord> data, int col){
		List<Double> columnValues = new ArrayList<>(data.size());
		for (DataRecord r : data) {
			columnValues.add(r.getFeatures().getFeature(col));
		}
		return columnValues;
	}

	public static List<Double> extractColumnExcludeMissingFeatures(Collection<DataRecord> data, int col){
		List<Double> columnValues = new ArrayList<>(data.size());
		for (DataRecord r: data) {
			Double f = r.getFeatures().getFeature(col);
			if (f !=null && Double.isFinite(f))
				columnValues.add(f);
		}
		return columnValues;
	}

	public static List<List<DataRecord>> stratify(List<DataRecord> data){
		Map<Double, List<DataRecord>> stratas = new HashMap<>();
		int maxNumCls = GlobalConfig.getInstance().getMaxNumClasses();
		for (DataRecord rec : data) {
			if (! stratas.containsKey(rec.getLabel())) {
				stratas.put(rec.getLabel(), new ArrayList<>());
			}
			stratas.get(rec.getLabel()).add(rec);

			if (stratas.size() > maxNumCls) {
				throw new IllegalArgumentException("Stratifying a dataset is only possible for classification datasets");
			}
		}
		return new ArrayList<>(stratas.values());
	}

	public static enum DataType {
		SINGLE_CLASS, BINARY_CLASS, MULTI_CLASS, REGRESSION;
	}
	
	public static DataType checkDataType(Dataset data) {
		return checkDataType(data.getDataset());
	}
	
	public static DataType checkDataType(Dataset.SubSet data) {
		Set<Double> labels = new HashSet<>();
		
		if (data.size() <= 100) {
			// Check all
			for (DataRecord r : data) {
				labels.add(r.getLabel());
			}
		} else {
			// Check 100 random records
			Random rand = new Random();
			for (int i=0; i<100; i++) {
				labels.add(data.get(rand.nextInt(data.size())).getLabel());
			}
		}
		
		if (labels.size()>GlobalConfig.getInstance().getMaxNumClasses())
			return DataType.REGRESSION;
		if (labels.size()>2)
			return DataType.MULTI_CLASS;
		if (labels.size() == 2)
			return DataType.BINARY_CLASS;
		return DataType.SINGLE_CLASS;
	}

	public static boolean containsMissingFeatures(Dataset data) {
		return containsMissingFeatures(data.getDataset()) || 
				containsMissingFeatures(data.getCalibrationExclusiveDataset()) ||
				containsMissingFeatures(data.getModelingExclusiveDataset());
	}
	
	public static boolean containsMissingFeatures(List<DataRecord> data) {
		for (DataRecord r : data) {
			if (r.getFeatures().containsMissingFeatures())
				return true;
		}
		return false;
	}
	
	public static boolean containMissingFeatures(List<FeatureVector> data) {
		for (FeatureVector fv : data) {
			if (fv.containsMissingFeatures())
				return true;
		}
		return false;
	}

	public static double l1_norm(FeatureVector v) {
		double sum = 0;
		for (Feature f : v) {
			sum += Math.abs(f.getValue());
		}
		return sum;
	}

	public static double l2_norm(FeatureVector v) {
		double sum = 0;
		for (Feature f : v) {
			sum += f.getValue()*f.getValue();
		}
		return Math.sqrt( sum );
	}

	public static double l_inf_norm(FeatureVector v) {
		double max = 0;
		for (Feature f : v) {
			max = Math.max(f.getValue(), max);
		}
		return max;
	}

	/**
	 * Average a collection of vectors (in the form of {@code List<SparseFeature>}) using their median
	 * feature values. 
	 * <b>NOTE</b> this function requires that all vectors have the same length
	 * and the same indices, otherwise the method will produce erroneous results or throw an exception.
	 * @param vectors a collection of vectors, each in the form of {@code List<SparseFeature>}
	 * @return the median vector 
	 */
	public static List<SparseFeature> averageIdenticalIndices(Collection<List<SparseFeature>> vectors){
		Objects.requireNonNull(vectors, "Vectors cannot be empty");
		if (vectors.isEmpty())
			throw new IllegalArgumentException("Vectors cannot be empty");
		List<SparseFeature> template = vectors.iterator().next();

		int numIndices = template.size();
		List<SparseFeature> medianGradient = new ArrayList<>(numIndices);

		for (int i=0; i<numIndices; i++){
			List<Double> values = new ArrayList<>();
			for (List<SparseFeature> v : vectors){
				values.add(v.get(i).getValue());
			}
			// Note: all _should_ have same index for a value
			medianGradient.add(new SparseFeatureImpl(template.get(i).getIndex(), MathUtils.median(values)));
		}
        return medianGradient;
	}



}

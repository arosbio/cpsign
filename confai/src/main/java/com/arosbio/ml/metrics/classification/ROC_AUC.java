/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics.classification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.arosbio.commons.MathUtils;
import com.arosbio.commons.mixins.Aliased;
import com.arosbio.commons.mixins.Described;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.plots.Plot;
import com.arosbio.ml.metrics.plots.Plot2D;
import com.arosbio.ml.metrics.plots.Plot2D.X_Axis;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.google.common.collect.ImmutableMap;

public class ROC_AUC implements ScoringClassifierMetric, SingleValuedMetric, LabelDependent, Aliased, Described {

	public final static String METRIC_NAME = "ROC AUC";
	public final static String METRIC_DESCRIPTION = "The area under curve (AUC) of the receiver operating characteristic (ROC). This metric can both calculate the area and give the full ROC curve as a plot. Only available for binary classification problems.";
	public final static String METRIC_ALIAS = "ROC_AUC";
	public final static String ROC_CURVE_MAPPING = "ROC";
	public final static String ROC_AUC_MAPPING = "Area Under Curve (AUC)";

	/**
	 * Y axis label
	 */
	public final static String TRUE_POSITIVE_RATE = "TPR";
	/**
	 * X axis label
	 */
	public final static String FALSE_POSITIVE_RATE = "FPR";
	/**
	 * Score for each given point on the curve, i.e. the threshold applied, also Y axis label
	 */
	public final static String SCORE = "score";

	private transient boolean labelSet = false;
	private int label;
	private List<Pair<Integer, Double>> labelsAndScores = new ArrayList<>();
	private transient ComputedROC computedROC;

	private static final double ADDED_SCORE_VAL = 1;
	
	public static ComputedROC calculateCurves(List<Integer> labels, List<Double> scores, int positiveLabel){
		List<Pair<Integer,Double>> pairs = new ArrayList<>();
		for (int i = 0; i<labels.size(); i++){
			pairs.add(Pair.of(labels.get(i), scores.get(i)));
		}
		return calculateCurves(pairs, positiveLabel);
	}

	public static ComputedROC calculateCurves(List<Pair<Integer,Double>> scores, int positiveLabel){

		// Sort the scores
		// List<Pair<Integer,Double>> copy = new ArrayList<>(scores);
		Collections.sort(scores,new Comparator<Pair<Integer,Double>>() {
			@Override
			public int compare(Pair<Integer,Double> o1, Pair<Integer,Double> o2) {
				// Note o2.compareTo(o1) -> descending order
				return o2.getRight().compareTo(o1.getRight());
			}
		});
		// Calculate the total number for each class
		int numNeg = 0, numPos = 0;
		for (Pair<Integer,Double> p : scores) {
			if (p.getKey() == positiveLabel)
				numPos++;
			else
				numNeg++;
		}
		// Compute the curves and AUC
		List<Number> thresholds = new ArrayList<>();
		List<Number> fpr = new ArrayList<>();
		List<Number> tpr = new ArrayList<>();
		
		// Add endpoint - with a higher threshold than encountered
		thresholds.add(scores.get(0).getRight() + ADDED_SCORE_VAL);
		fpr.add(0d);
		tpr.add(0d);
		
		double x0=0, y0=0, x1=0, y1=0, auc=0;
		for (Pair<Integer,Double> point : scores) {
			if (point.getLeft() == positiveLabel) {
				y1 = y0 + 1d/numPos;
			} else {
				x1 = x0 + 1d/numNeg;
			}
			tpr.add(y1);
			fpr.add(x1);
			thresholds.add(point.getRight());
			// AUC
			auc += (x1-x0)*(y0 + (y1-y0)*.5);

			// update
			x0 = x1;
			y0 = y1;
		}
		
		Map<String,List<Number>> plot = new LinkedHashMap<>();
		plot.put(TRUE_POSITIVE_RATE, tpr);
		plot.put(FALSE_POSITIVE_RATE, fpr);
		plot.put(SCORE, thresholds);

		return new ComputedROC(plot, positiveLabel, auc, numPos+numNeg);
	}

	public static class ComputedROC {

		private final int posLabel;
		private final int numRecordsUsed;
		private final Plot2D roc;
		private final double auc;

		private ComputedROC(final Map<String,List<Number>> roc, final int positiveLabel, final double auc, final int numRecords){
			this.posLabel = positiveLabel;
			this.roc = new Plot(roc, X_Axis.FALSE_POSITIVE_RATE);
			this.auc = auc;
			this.numRecordsUsed = numRecords;
		}

		public int getPositiveLabel(){
			return posLabel;
		}

		public double auc() {
			return auc;
		}

		public Plot2D getROC(){
			return roc;
		}
		
		public JsonObject rocAsJSON() {
			return new JsonObject(roc.getCurves()); 
		}

		public String rocAsCSV(char delim) {
			final String recSep = System.lineSeparator();
			StringBuilder sb = new StringBuilder();
			// Header
			sb.append(FALSE_POSITIVE_RATE);
			sb.append(delim);
			sb.append(TRUE_POSITIVE_RATE);
			sb.append(delim);
			sb.append(SCORE);
			sb.append(recSep);

			// Data
			Map<String,List<Number>> curves = roc.getCurves();
			int numPoints = roc.getXvalues().size();
			
			for (int i=0; i< numPoints; i++) {
				sb.append(curves.get(FALSE_POSITIVE_RATE).get(i));
				sb.append(delim);
				sb.append(curves.get(TRUE_POSITIVE_RATE).get(i));
				sb.append(delim);
				sb.append(curves.get(SCORE).get(i));
				sb.append(recSep);
			}

			return sb.toString();
		}
		
		public int getNumExamplesUsed() {
			return numRecordsUsed;
		}
	}
	
	/**
	 * Use a random label 
	 */
	public ROC_AUC() {
	}
	
	/**
	 * Use a specific label for computing the ROC curve for
	 * @param labelToPlot label to compute ROC with respect to
	 */
	public ROC_AUC(int labelToPlot) {
		this.label = labelToPlot;
		this.labelSet = true;
	}
	
	@Override
	public String getName() {
		return METRIC_NAME;
	}
	
	@Override
	public String[] getAliases() {
		return new String[] {METRIC_ALIAS};
	}
	
	@Override
	public void setPositiveLabel(int positive) throws IllegalStateException {
		if (!labelsAndScores.isEmpty())
			throw new IllegalStateException("Cannot change the positive label once predictions have been added");
		this.label = positive;
	}

	@Override
	public int getPositiveLabel() {
		return label;
	}
	
	@Override
	public String getDescription() {
		return METRIC_DESCRIPTION;
	}
	
	private ComputedROC getROC() {
		if (computedROC == null) {
			computedROC = calculateCurves(labelsAndScores, label);
		} else if (getNumExamples() != computedROC.getNumExamplesUsed()) {
			// If new examples has been added, we need to re-compute the ROC 
			computedROC = calculateCurves(labelsAndScores, label);
		}
			
		return computedROC;
	}

	public String toString() {
		return SingleValuedMetric.toString(this);
	}
	
	@Override
	public boolean supportsMulticlass() {
		return false;
	}

	public JsonObject rocAsJSON() {
		return getROC().rocAsJSON();
	}
	
	public String rocAsCSV(char delim) {
		return getROC().rocAsCSV(delim);
	}
	

	public Map<String,Object> asMap(){
		return ImmutableMap.of(METRIC_NAME, getScore());
	}

	@Override
	public double getScore() {
		if (labelsAndScores.isEmpty())
			return Double.NaN;
		return getROC().auc();
	}

	public void addPrediction(int trueLabel, Map<Integer, Double> scores) {
		if (scores.size()>2) {
			throw new UnsupportedOperationException("Metric " + METRIC_NAME + " not supported for multiclass problems");
		}
		if (!labelSet){
			label = MathUtils.max(scores.keySet());
			labelSet = true;
		}
		if (!scores.containsKey(label))
			throw new IllegalArgumentException(String.format("The specified label (%s) was not in the given scores: %s",label,scores));
		labelsAndScores.add(ImmutablePair.of(trueLabel,scores.get(label)));
	}

	@Override
	public int getNumExamples() {
		return labelsAndScores.size();
	}

	public ROC_AUC clone() {
		return new ROC_AUC(label);
	}
	
	public void clear() {
		labelsAndScores.clear();
		computedROC = null;
	}

	@Override
	public boolean goalIsMinimization() {
		return false;
	}

}

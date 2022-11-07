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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

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
	 * Score for each given point on the curve, also Y axis label
	 */
	public final static String SCORE = "score";

	private int label = LabelDependent.DEFAULT_POS_LABEL;
	private List<Pair<Integer, Double>> labelsAndScores = new ArrayList<>();
	private transient ComputedROC computedROC;


	private static class ComputedROC {

		private final List<Pair<Integer,Double>> scores;
		private final int posLabel;
		private final Map<Integer,Integer> labelFreqs= new HashMap<>();
		// computed when needed
		private Plot2D roc;
		private Double auc;

		private ComputedROC(final List<Pair<Integer,Double>> scores, final int positiveLabel) {
			this.scores = new ArrayList<>(scores);
			this.posLabel = positiveLabel;
			for (Pair<Integer,Double> p : scores) {
				labelFreqs.put(p.getLeft(),1+labelFreqs.getOrDefault(p.getLeft(), 0));
			}
		}

		public Plot2D roc() {
			if (roc!=null)
				return roc;
			
			List<Number> sc = new ArrayList<>();
			List<Number> fpr = new ArrayList<>();
			List<Number> tpr = new ArrayList<>();
			
			// Add endpoint
			sc.add(scores.get(0).getRight());
			fpr.add(0d);
			tpr.add(0d);
			
			double x=0, y=0;
			for (Pair<Integer,Double> point: scores) {
				if (point.getLeft() == posLabel) {
					y += 1d/labelFreqs.get(point.getLeft());
				} else {
					x += 1d/labelFreqs.get(point.getLeft());
				}
				tpr.add(y);
				fpr.add(x);
				sc.add(point.getRight());
			}
			
			// last object
			fpr.add(1d);
			tpr.add(1d);
			sc.add(scores.get(scores.size()-1).getRight()); // Use same score as last one
			
			Map<String,List<Number>> plot = new LinkedHashMap<>();
			plot.put(TRUE_POSITIVE_RATE, tpr);
			plot.put(FALSE_POSITIVE_RATE, fpr);
			plot.put(SCORE, sc);
			
			roc = new Plot(plot, X_Axis.FALSE_POSITIVE_RATE);
			return roc;
		}

		public double auc() {
			// return if computed before
			if (auc != null)
				return auc;
			// compute the ROC curve if not done before
			if (roc == null)
				roc();

			// compute the AUC
			double x0 = 0d, y0 = 0d, x1, y1, a = 0d;
			
			Map<String,List<Number>> curves = roc.getCurves();
			int numPoints = roc.getXvalues().size();
			
			for (int i=0; i< numPoints; i++) {
				x1 = curves.get(FALSE_POSITIVE_RATE).get(i).doubleValue();
				y1 = curves.get(TRUE_POSITIVE_RATE).get(i).doubleValue();
				

				a += (x1-x0)*(y0 + (y1-y0)*.5);

				x0 = x1;
				y0 = y1;
			}

			auc = a;
			
			return auc;
		}
		
		public JsonObject rocAsJSON() {
			if (roc == null)
				roc();
			return new JsonObject(roc.getCurves()); 
		}

		public String rocAsCSV(char delim) {
			if (roc == null)
				roc();
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
			return scores.size();
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
			
			// SORT DESCENDING ORDER!
			Collections.sort(labelsAndScores,new Comparator<Pair<Integer,Double>>() {
				@Override
				public int compare(Pair<Integer,Double> o1, Pair<Integer,Double> o2) {
					// Note o2.compareTo(o1) -> descending order
					return o2.getRight().compareTo(o1.getRight());
				}
			});
			
			computedROC = new ComputedROC(labelsAndScores, label);
		}
		if (getNumExamples() != computedROC.getNumExamplesUsed()) {
			// If new examples has been added, we need to re-compute the ROC 
			computedROC = null;
			return getROC();
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

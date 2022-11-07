/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics.cp.classification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.arosbio.data.NamedLabels;
import com.arosbio.ml.cp.PValueTools;
import com.arosbio.ml.metrics.LabelsMixin;
import com.arosbio.ml.metrics.cp.CalibrationPlot;
import com.arosbio.ml.metrics.plots.Plot2D.X_Axis;
import com.arosbio.ml.metrics.plots.PlotMetric;
import com.google.common.collect.ImmutableList;

public class CPClassificationCalibrationPlotBuilder implements PlotMetric, LabelsMixin, CPClassificationMetric {

	public static final String METRIC_NAME = "Calibration plot";

	private static final X_Axis X_AXIS = X_Axis.CONFIDENCE;
	private static final String Y_AXIS = "Accuracy";

	private List<Double> confidences = new ArrayList<>();
	// Overall counter
	private Map<Double, Counter> counters = new HashMap<>();
	// Mondrian counters - counting for each class
	private Map<Double, Map<Integer,Counter>> mondrianCounters = new HashMap<>();
	private Set<Integer> encounteredLabels = new HashSet<>();
	private int numExamples = 0;

	private NamedLabels labels;

	public CPClassificationCalibrationPlotBuilder() {
		setEvaluationPoints(DEFAULT_EVALUATION_POINTS);
	}

	public CPClassificationCalibrationPlotBuilder(List<Double> confidenceLevels) {
		setEvaluationPoints(confidenceLevels);
	}

	@Override
	public boolean supportsMulticlass() {
		return true;
	}

	private static class Counter {

		private final Integer label;
		private int numExamples, numCorrects;

		public static Counter overall(){
			return new Counter(null);
		}
		public static Counter forLabel(int label){
			return new Counter(label);
		}

		private Counter(Integer label) {
			this.label = label;
		}

		public void addPrediction(Integer trueLabel, Collection<Integer> predictedLabels) {
			if (
					label == null  // Overall counter
					|| 
					label.equals(trueLabel) // counter for this specific class
					) {
				// Overall counter
				numExamples++;
				numCorrects += (predictedLabels.contains(trueLabel)? 1 : 0);
			} 
		}

		public double getAccuracy() {
			if (numExamples ==0)
				return Double.NaN;
			return ((double)numCorrects)/numExamples;
		}
	}

	@Override
	public CalibrationPlot buildPlot() {

		if (numExamples <= 0)
			throw new IllegalStateException("No predictions added - cannot generate a calibration plot");

		List<Number> overallAccuracies = new ArrayList<>();

		// Overall accuracies
		for (double conf : confidences) {
			overallAccuracies.add(counters.get(conf).getAccuracy());
		}

		// Mondrian accuracies
		Map<Integer, List<Number>> mondrianAccuracies = new HashMap<>();
		for (int lab : encounteredLabels) {
			mondrianAccuracies.put(lab, new ArrayList<>());
		}
		for (double conf : confidences) {
			
			for (int lab : encounteredLabels) {
				mondrianAccuracies.get(lab)
				.add(
						mondrianCounters.get(conf).get(lab).getAccuracy()
						);
			}

		}

		Map<String,List<Number>> plotValues = new LinkedHashMap<>();
		List<Number> confAsNumber = new ArrayList<>(confidences);
		plotValues.put(X_AXIS.label(), confAsNumber);
		plotValues.put(Y_AXIS, overallAccuracies);
		for (Map.Entry<Integer, List<Number>> label : mondrianAccuracies.entrySet()) {
			String lab = (labels!=null? labels.getLabel(label.getKey()): ""+label.getKey());
			plotValues.put(String.format("%s(%s)", Y_AXIS,lab), 
					label.getValue());
		}

		CalibrationPlot plot = new CalibrationPlot(plotValues,X_AXIS, Y_AXIS);
		plot.setNumExamplesUsed(numExamples); // all of them has the same number of examples
		return plot;
	}

	@Override
	public void addPrediction(int trueLabel, Map<Integer, Double> pValues) {
		encounteredLabels.add(trueLabel);

		for (double conf : confidences) {
			Set<Integer> predSet = PValueTools.getPredictionSet(pValues, conf);
			
			// Overall stats
			counters.get(conf).addPrediction(trueLabel, predSet);

			// Mondrian stats
			if (!mondrianCounters.get(conf).containsKey(trueLabel)) {
				mondrianCounters.get(conf).put(trueLabel, Counter.forLabel(trueLabel));
			}
			mondrianCounters.get(conf).get(trueLabel).addPrediction(trueLabel, predSet);
		}

		numExamples++;

	}


	@Override
	public int getNumExamples() {
		if (counters.isEmpty())
			return 0;

		return counters.values().iterator().next().numExamples;
	}

	@Override
	public String getName() {
		return METRIC_NAME;
	}
	
	@Override
	public CPClassificationCalibrationPlotBuilder clone() {
		CPClassificationCalibrationPlotBuilder clone = new CPClassificationCalibrationPlotBuilder(confidences);
		if (labels != null)
			clone.labels = labels.clone();
		return clone;
	}

	@Override
	public void clear() {
		setEvaluationPoints(confidences);
		encounteredLabels.clear();
		numExamples=0;
	}

	@Override
	public void setLabels(NamedLabels labels) {
		this.labels = labels;
	}

	@Override
	public boolean goalIsMinimization() {
		return false;
	}

	@Override
	public void setEvaluationPoints(List<Double> points) {
		confidences = PlotMetric.sortAndValidateList(points);

		mondrianCounters = new HashMap<>();
		counters = new HashMap<>();
		for (double c : confidences) {
			mondrianCounters.put(c, new HashMap<>());
			counters.put(c, Counter.overall());
		}

	}

	@Override
	public List<Double> getEvaluationPoints() {
		return ImmutableList.copyOf(confidences);
	}

	public String toString() {
		return PlotMetric.toString(this);
	}

}

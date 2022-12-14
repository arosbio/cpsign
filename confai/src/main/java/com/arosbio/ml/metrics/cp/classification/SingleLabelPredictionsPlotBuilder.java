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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.arosbio.ml.cp.PValueTools;
import com.arosbio.ml.metrics.cp.EfficiencyPlot;
import com.arosbio.ml.metrics.plots.Plot2D.X_Axis;
import com.arosbio.ml.metrics.plots.PlotMetric;

public class SingleLabelPredictionsPlotBuilder implements PlotMetric, CPClassificationMetric {
	
	public static final X_Axis X_AXIS = X_Axis.CONFIDENCE;
	public static final String Y_AXIS = "Proportion single-label prediction sets";
	
	public static final String METRIC_NAME = Y_AXIS;


	private Map<Double, Integer> numExamples = new HashMap<>();
	private Map<Double, Integer> numSingleLabelPredictions = new HashMap<>();
	
	public SingleLabelPredictionsPlotBuilder() {
		setEvaluationPoints(DEFAULT_EVALUATION_POINTS);
	}
	
	public SingleLabelPredictionsPlotBuilder(List<Double> evaluationPoints) {
		setEvaluationPoints(evaluationPoints);
	}
	
	@Override
	public boolean supportsMulticlass() {
		return true;
	}

	@Override
	public EfficiencyPlot buildPlot() {

		List<Double> confs = new ArrayList<>(numExamples.keySet());
		
		List<Number> fraqSingleLabel = new ArrayList<>();
		Collections.sort(confs);
		for (Double conf: confs) {
			fraqSingleLabel.add( ((double)numSingleLabelPredictions.getOrDefault(conf,0))/numExamples.get(conf));
		}
		
		Map<String,List<Number>> plotValues = new HashMap<>();
		plotValues.put(X_AXIS.label(), new ArrayList<>(confs));
		plotValues.put(Y_AXIS, fraqSingleLabel);
		
		EfficiencyPlot plot = new EfficiencyPlot(plotValues, X_AXIS, Y_AXIS);
		plot.setNumExamplesUsed(numExamples.values().iterator().next()); // all should have the same number
		return plot;
	}

	@Override
	public void addPrediction(int trueLabel, Map<Integer, Double> pValues) {
		for (double confidence : numExamples.keySet()) {
			numExamples.put(confidence, numExamples.getOrDefault(confidence,0)+1);
			if (PValueTools.getPredictionSetSize(pValues, confidence)==1)
				numSingleLabelPredictions.put(
						confidence, 
						numSingleLabelPredictions.getOrDefault(confidence,0)+1);
			
		}
	}
	
	@Override
	public int getNumExamples() {
		return (numExamples.isEmpty() ? 0 : numExamples.values().iterator().next());
	}

	@Override
	public String getName() {
		return METRIC_NAME;
	}

	@Override
	public SingleLabelPredictionsPlotBuilder clone() {
		return new SingleLabelPredictionsPlotBuilder(new ArrayList<>(numExamples.keySet()));
	}

	@Override
	public void clear() {
		if (numExamples.isEmpty())
			setEvaluationPoints(DEFAULT_EVALUATION_POINTS);
		else
			setEvaluationPoints(new ArrayList<>(numExamples.keySet()));
	}

	@Override
	public boolean goalIsMinimization() {
		return false;
	}

	@Override
	public void setEvaluationPoints(List<Double> points) {
		List<Double> sortedPoints = PlotMetric.sortAndValidateList(points);
		
		numExamples = new HashMap<>();
		numSingleLabelPredictions = new HashMap<>();
		for (double p: sortedPoints) {
			numExamples.put(p, 0);
			numSingleLabelPredictions.put(p, 0);
		}
	}

	@Override
	public List<Double> getEvaluationPoints() {
		return new ArrayList<>(numExamples.keySet());
	}
	
	public String toString() {
		return PlotMetric.toString(this);
	}

}

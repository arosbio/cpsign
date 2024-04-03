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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.arosbio.commons.mixins.Aliased;
import com.arosbio.ml.cp.PValueTools;
import com.arosbio.ml.metrics.cp.EfficiencyPlot;
import com.arosbio.ml.metrics.plots.Plot2D.X_Axis;
import com.arosbio.ml.metrics.plots.PlotMetric;

public class ProportionMultiLabelPredictionSets implements PlotMetric, CPClassifierMetric, Aliased {
	
	public static final X_Axis X_AXIS = X_Axis.CONFIDENCE;
	public static final String Y_AXIS = "Proportion multi-label prediction sets";
	public final static String METRIC_DESCRIPTION = "The fraction of multi-label prediction sets (i.e. 2 or more labels) for each confidence level";
	public static final String METRIC_NAME = Y_AXIS;
	public static final String METRIC_ALIAS = "PropMultiLabel";

	private Map<Double, Integer> numExamples = new HashMap<>();
	private Map<Double, Integer> numMultiLabelPredictions = new HashMap<>();

	public ProportionMultiLabelPredictionSets() {
		setEvaluationPoints(DEFAULT_EVALUATION_POINTS);
	}
	
	public ProportionMultiLabelPredictionSets(List<Double> evaluationPoints) {
		setEvaluationPoints(evaluationPoints);
	}

	@Override
	public String getName() {
		return METRIC_NAME;
	}

	@Override
	public String getDescription(){
		return METRIC_DESCRIPTION;
	}

	@Override
	public String[] getAliases() {
		return new String[]{METRIC_ALIAS};
	}
	
	@Override
	public boolean supportsMulticlass() {
		return true;
	}

	@Override
	public String getPrimaryMetricName() {
		return Y_AXIS;
	}

	public String toString() {
		return PlotMetric.toString(this);
	}

	@Override
	public Set<String> getYLabels() {
		return Set.of(METRIC_NAME);
	}

	@Override
	public boolean goalIsMinimization() {
		return true;
	}

	@Override
	public void setEvaluationPoints(List<Double> points) {
		List<Double> sortedPoints = PlotMetric.sortAndValidateList(points);
		
		numExamples = new LinkedHashMap<>();
		numMultiLabelPredictions = new LinkedHashMap<>();
		for (double p: sortedPoints) {
			numExamples.put(p, 0);
			numMultiLabelPredictions.put(p, 0);
		}
	}

	@Override
	public List<Double> getEvaluationPoints() {
		return new ArrayList<>(numExamples.keySet());
	}
	
	
	
	@Override
	public void addPrediction(int trueLabel, Map<Integer, Double> pValues) {
		for (double confidence : numExamples.keySet()) {
			numExamples.put(confidence, numExamples.getOrDefault(confidence,0)+1);
			if (PValueTools.getPredictionSetSize(pValues, confidence)>1)
				numMultiLabelPredictions.put(
						confidence, 
						numMultiLabelPredictions.getOrDefault(confidence,0)+1);
			
		}
	}

	@Override
	public int getNumExamples() {
		if (numExamples.isEmpty())
			return 0;
		return numExamples.values().iterator().next();
	}

	@Override
	public ProportionMultiLabelPredictionSets clone() {
		return new ProportionMultiLabelPredictionSets(new ArrayList<>(numExamples.keySet()));
	}

	@Override
	public void clear() {
		setEvaluationPoints(new ArrayList<>(numExamples.keySet()));
	}
	
	@Override
	public EfficiencyPlot buildPlot() throws IllegalStateException {

		if (getNumExamples()<=0){
			throw new IllegalStateException("Cannot build plot without evaluation data");
		}
				
		List<Double> confs = new ArrayList<>(numExamples.keySet());
		
		List<Number> efficiencies = new ArrayList<>();
		Collections.sort(confs);

		for (Double conf: confs) {
			efficiencies.add(((double)numMultiLabelPredictions.getOrDefault(conf,0))/numExamples.get(conf));
		}
		
		Map<String,List<Number>> plotValues = new HashMap<>();
		plotValues.put(X_AXIS.label(), new ArrayList<>(confs));
		plotValues.put(Y_AXIS, efficiencies);
		
		EfficiencyPlot plot = new EfficiencyPlot(plotValues, X_AXIS, Y_AXIS);
		plot.setNumExamplesUsed(numExamples.values().iterator().next());
		return plot;
	}
	
}

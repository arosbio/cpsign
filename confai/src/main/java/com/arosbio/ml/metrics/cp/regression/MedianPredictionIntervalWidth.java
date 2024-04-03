/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics.cp.regression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.arosbio.commons.MathUtils;
import com.arosbio.ml.metrics.cp.EfficiencyPlot;
import com.arosbio.ml.metrics.plots.Plot2D.X_Axis;
import com.arosbio.ml.metrics.plots.PlotMetric;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;

public class MedianPredictionIntervalWidth implements CPRegressionMultiMetric {

	
	public static final X_Axis X_AXIS = X_Axis.CONFIDENCE;
	public static final String Y_AXIS = "Efficiency (Median prediction interval width)";
	public static final String METRIC_ALIAS = "MedianPredWidth";
	public final static String METRIC_DESCRIPTION = "The median interval width computed for each confidence level";
	public static final String METRIC_NAME = Y_AXIS;

	private Map<Double, List<Double>> intervalWidths = new HashMap<>();
	private int numExamples = 0;

	public MedianPredictionIntervalWidth() {
		setEvaluationPoints(DEFAULT_EVALUATION_POINTS);
	}

	public MedianPredictionIntervalWidth(List<Double> confidences) {
		setEvaluationPoints(confidences);
	}

	@Override
	public void addPrediction(double trueLabel, Map<Double, Range<Double>> predictedIntervals) {
		for (double conf : intervalWidths.keySet()){
			try {
				Range<Double> interval = predictedIntervals.get(conf);
				double width = interval.upperEndpoint() - interval.lowerEndpoint();
				intervalWidths.get(conf). // For this confidence
					add(width);
			} catch (NullPointerException npe){
				throw new IllegalArgumentException("prediction did not contain an interval for confidence: " + conf);
			}
		}
		numExamples++;		
	}

	@Override
	public int getNumExamples() {
		return numExamples;
	}

	@Override
	public String getName() {
		return METRIC_NAME;
	}

	@Override
	public String getPrimaryMetricName() {
		return Y_AXIS;
	}

	@Override
	public MedianPredictionIntervalWidth clone() {
		return new MedianPredictionIntervalWidth(new ArrayList<>(intervalWidths.keySet()));
	}

	@Override
	public void clear() {
		setEvaluationPoints(new ArrayList<>(intervalWidths.keySet()));
	}

	@Override
	public boolean goalIsMinimization() {
		return true;
	}

	@Override
	public void setEvaluationPoints(List<Double> points) {
		List<Double> sortedPoints = PlotMetric.sortAndValidateList(points);
		
		intervalWidths = new LinkedHashMap<>();
		for (double p: sortedPoints) {
			intervalWidths.put(p, new ArrayList<>());
		}
		numExamples=0;
	}

	@Override
	public List<Double> getEvaluationPoints() {
		return ImmutableList.copyOf(intervalWidths.keySet());
	}

	public String toString() {
		return PlotMetric.toString(this);
	}

	@Override
	public String getDescription() {
		return METRIC_DESCRIPTION;
	}

	@Override
	public Set<String> getYLabels() {
		return Set.of(METRIC_NAME);
	}

	@Override
	public EfficiencyPlot buildPlot() {
		
		PlotMetric.validateExamplesAdded(this);

		List<Number> means = new ArrayList<>();
		List<Double> confs = new ArrayList<>(intervalWidths.keySet());
		Collections.sort(confs);

		for (double conf : confs) {
			means.add( MathUtils.median(intervalWidths.get(conf)) );
		}

		Map<String,List<Number>> curves = new HashMap<>();
		curves.put(X_AXIS.label(), new ArrayList<>(confs));
		curves.put(Y_AXIS, means);

		EfficiencyPlot plot = new EfficiencyPlot(curves, X_AXIS, Y_AXIS);
		plot.setNumExamplesUsed(numExamples);
		return plot;
	}


}

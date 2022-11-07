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

import com.arosbio.commons.MathUtils;
import com.arosbio.ml.metrics.cp.EfficiencyPlot;
import com.arosbio.ml.metrics.plots.Plot2D.X_Axis;
import com.arosbio.ml.metrics.plots.PlotMetric;
import com.google.common.collect.Range;

public class CPRegressionEfficiencyPlotBuilder implements CPRegressionMultiMetric {

	public static final String METRIC_NAME = "Efficiency plot";


	public static final X_Axis X_AXIS = X_Axis.CONFIDENCE;
	public static final String Y_AXIS = "Efficiency (median prediction interval width)";
	public static final String MEAN_INTERVAL_WIDTH = "Mean prediction interval width";

	private Map<Double, List<Double>> intervalWidths = new HashMap<>();

	public CPRegressionEfficiencyPlotBuilder() {
		setEvaluationPoints(DEFAULT_EVALUATION_POINTS);
	}

	public CPRegressionEfficiencyPlotBuilder(List<Double> confidences) {
		setEvaluationPoints(confidences);
	}

	@Override
	public void addPrediction(double trueLabel, Map<Double, Range<Double>> predictedIntervals) {
		for (Map.Entry<Double, Range<Double>> confAndInterval : predictedIntervals.entrySet()) {
			Range<Double> interval = confAndInterval.getValue();
			double width= interval.upperEndpoint() - interval.lowerEndpoint();
			intervalWidths.get(confAndInterval.getKey()). // For this confidence
				add(width);
		}			
	}

	@Override
	public EfficiencyPlot buildPlot() {
		List<Number> effsMedian = new ArrayList<>(),
				effsMean = new ArrayList<>();
		List<Double> confs = new ArrayList<>(intervalWidths.keySet());
		Collections.sort(confs);

		for (double conf : confs) {
			effsMedian.add( MathUtils.median(intervalWidths.get(conf)) );
			effsMean.add( MathUtils.mean(intervalWidths.get(conf)) );
		}

		Map<String,List<Number>> curves = new HashMap<>();
		curves.put(X_AXIS.label(), new ArrayList<>(confs));
		curves.put(Y_AXIS, effsMedian);
		curves.put(MEAN_INTERVAL_WIDTH, effsMean);

		EfficiencyPlot plot = new EfficiencyPlot(curves, X_AXIS, Y_AXIS);
		plot.setNumExamplesUsed(intervalWidths.values().iterator().next().size());
		return plot;
	}

	@Override
	public int getNumExamples() {
		return intervalWidths.values().iterator().next().size();
	}

	@Override
	public String getName() {
		return METRIC_NAME;
	}

	@Override
	public CPRegressionEfficiencyPlotBuilder clone() {
		return new CPRegressionEfficiencyPlotBuilder(new ArrayList<>(intervalWidths.keySet()));
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
	}

	@Override
	public List<Double> getEvaluationPoints() {
		return new ArrayList<>(intervalWidths.keySet());
	}

	public String toString() {
		return PlotMetric.toString(this);
	}

}

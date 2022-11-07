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

import com.arosbio.ml.metrics.cp.CalibrationPlot;
import com.arosbio.ml.metrics.plots.Plot2D.X_Axis;
import com.arosbio.ml.metrics.plots.PlotMetric;
import com.google.common.collect.Range;

public class CPRegressionCalibrationPlotBuilder implements CPRegressionMultiMetric {

	public static final String METRIC_NAME = "Calibration plot";

	private static final X_Axis X_AXIS = X_Axis.CONFIDENCE;
	private static final String Y_AXIS = "Accuracy";

	private Map<Double, Integer> numIncorrects = new HashMap<>();
	private Map<Double, Integer> numExamples = new HashMap<>();
	
	public CPRegressionCalibrationPlotBuilder() {
		setEvaluationPoints(DEFAULT_EVALUATION_POINTS);
	}
	
	public CPRegressionCalibrationPlotBuilder(List<Double> confidences) {
		setEvaluationPoints(confidences);
	}

	@Override
	public void addPrediction(double trueLabel, Map<Double, Range<Double>> predictedIntervals) {
		for (Map.Entry<Double, Range<Double>> confAndInterval : predictedIntervals.entrySet()) {
			// Num examples
			numExamples.put(confAndInterval.getKey(), numExamples.getOrDefault(confAndInterval.getKey(), 0)+1);
				
			// Incorrects
			if (!confAndInterval.getValue().contains(trueLabel)) 
				numIncorrects.put(confAndInterval.getKey(), numIncorrects.getOrDefault(confAndInterval.getKey(),0)+1);
		}
	}
	
	@Override
	public CalibrationPlot buildPlot() {
		List<Number> accs = new ArrayList<>();

		List<Double> confs = new ArrayList<>(numExamples.keySet());
		Collections.sort(confs);

		for (double conf : confs) {
			accs.add(1d-(((double)numIncorrects.getOrDefault(conf,0))/numExamples.get(conf)));
		}

		Map<String,List<Number>> curves = new HashMap<>();
		curves.put(X_AXIS.label(), new ArrayList<>(confs));
		curves.put(Y_AXIS, accs);

		CalibrationPlot plot = new CalibrationPlot(curves,X_AXIS,Y_AXIS);
		plot.setNumExamplesUsed(numExamples.values().iterator().next());
		return plot;
	}

	@Override
	public int getNumExamples() {
		return numExamples.values().iterator().next();
	}
	
	@Override
	public String getName() {
		return METRIC_NAME;
	}

	@Override
	public CPRegressionCalibrationPlotBuilder clone() {
		return new CPRegressionCalibrationPlotBuilder(new ArrayList<>(numExamples.keySet()));
	}

	@Override
	public void clear() {
		setEvaluationPoints(new ArrayList<>(numIncorrects.keySet()));
	}	

	@Override
	public boolean goalIsMinimization() {
		return false;
	}

	@Override
	public void setEvaluationPoints(List<Double> points) {
		List<Double> sortedPoints = PlotMetric.sortAndValidateList(points);
		
		numExamples = new LinkedHashMap<>();
		numIncorrects = new LinkedHashMap<>();
		for (double p : sortedPoints) {
			numExamples.put(p, 0);
			numIncorrects.put(p, 0);
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

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics.cp;

import java.util.List;
import java.util.Map;

import com.arosbio.ml.metrics.plots.Plot;

public class CalibrationPlot extends Plot {
	public static final String DEFAULT_PLOT_NAME = "Calibration Plot";
	
	private int label;
	private final String accuracyLabel;
	
	/**
	 * For regression
	 * @param curve The curves, with mapping curve-name -&gt;list of values
	 * @param xLabel The label for the x-values
	 * @param accuracyLabel The label for the y-axes
	 */
	public CalibrationPlot(Map<String,List<Number>> curve, X_Axis xLabel, String accuracyLabel) {
		super(curve,xLabel);
		this.accuracyLabel = accuracyLabel;
		setPlotName(DEFAULT_PLOT_NAME);
	}
	
	/**
	 * For classification
	 * @param curve The curves, with mapping curve-name -&gt;list of values
	 * @param xLabel The label for the x-values
	 * @param accuracyLabel The label for the y-axes 
	 * @param label the label used for for the calculations
	 */
	public CalibrationPlot(Map<String,List<Number>> curve, X_Axis xLabel,String accuracyLabel,int label) {
		super(curve,xLabel);
		this.label = label;
		this.accuracyLabel = accuracyLabel;
		setPlotName(DEFAULT_PLOT_NAME);
	}
	
	public String getAccuracyLabel() {
		return accuracyLabel;
	}
	
	public double getAccuracy(double confidence) throws IllegalArgumentException {
		int indx = getXvalues().indexOf(confidence);
		if (indx < 0)
			throw new IllegalArgumentException("Confidence not found in confidence plot: " + confidence);
		return getCurves().get(accuracyLabel).get(indx).doubleValue();
	}
	
	/**
	 * Getter for which label was used when calculating the calibration-curve. Only
	 * for classification problems
	 * @return Label used for the curve
	 */
	public int getLabel() {
		return label;
	}
	
	public String toString() {
		return DEFAULT_PLOT_NAME;
	}

	public String getName() {
		return accuracyLabel;
	}
	
	/**
	 * Goes through all confidence levels and checks if the following expression holds true: {@code accuracy >= confidence - tolerance}.
	 * If the expression is not true for any of the confidence levels the method will return {@code false}, otherwise {@code true}.
	 * @param tolerance the allowed deviation of accuracy vs confidence, &gt;0
	 * @return {@code true} if the calibration is not deviating more than {@code tolerance} for any confidence level, {@code false} otherwise
	 * @throws IllegalArgumentException if {@code tolerance} is &lt;0
	 */
	public boolean isValidWithinTolerance(double tolerance) throws IllegalArgumentException {
		if (tolerance < 0) {
			throw new IllegalArgumentException("Tolerance must be >=0");
		}
		List<Number> confidences = getXvalues();
		List<Number> accuracies = getPoints(accuracyLabel);
		
		for (int i=0; i<confidences.size(); i++) {
			if (accuracies.get(i).doubleValue() < confidences.get(i).doubleValue() - tolerance)
				return false;
		}
		return true;
	}
}

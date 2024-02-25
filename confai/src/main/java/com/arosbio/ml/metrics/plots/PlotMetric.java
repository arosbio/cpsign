/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics.plots;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.arosbio.ml.metrics.Metric;
import com.google.common.collect.ImmutableList;

public interface PlotMetric extends Metric {
	
	public static final List<Double> DEFAULT_EVALUATION_POINTS = ImmutableList.of(0.,.1,.2,.3,.4,.5,.6,.7,.8,.9,.99);

	public void setEvaluationPoints(List<Double> points);

	public List<Double> getEvaluationPoints();

	/**
	 * Get the name of the primary metric, i.e. in case 
	 * the PlotMetric generates several y-labels in the plot
	 * @return the label of the primary metric
	 */
	public String getPrimaryMetricName();

	/**
	 * Get the y-labels when the plot is generated
	 * @return a set of labels
	 */
	public Set<String> getYLabels();

	/**
	 * Build the plot for this metric
	 * @return The {@link com.arosbio.ml.metrics.plots.Plot2D Plot2D} base on the given data
	 * @throws IllegalStateException If no evaluation examples have been given yet
	 */
	public Plot2D buildPlot() throws IllegalStateException;
	
	public PlotMetric clone();
	
	static String toString(PlotMetric m){
		String n = m.getName();
		if (n.toLowerCase().contains("plot"))
			return String.format("%s builder", n);
		return String.format("%s plot builder",n);
	}

	static void validateExamplesAdded(PlotMetric m) throws IllegalStateException {
		if (m.getNumExamples() <= 0){
			throw new IllegalStateException("No predictions added - cannot generate a metric plot");
		}
	}
	
	public static List<Double> sortAndValidateList(List<Double> points){
		if (points==null || points.isEmpty())
			throw new IllegalArgumentException("Evaluation points cannot be empty");
		List<Double> sortedList = new ArrayList<>(points);
		Collections.sort(sortedList);
		if (sortedList.get(0) < 0 || sortedList.get(sortedList.size()-1)>1)
			throw new IllegalArgumentException("Evaluation points must be in the range [0..1]");
		return sortedList;
	}
	
}

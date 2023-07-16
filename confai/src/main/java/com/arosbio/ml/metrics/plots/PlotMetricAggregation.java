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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.MathUtils;
import com.arosbio.ml.metrics.plots.Plot2D.X_Axis;
import com.arosbio.ml.metrics.vap.VAPCalibrationPlotBuilder;

/**
 * This class handles aggregation of several metrics of a single type, e.g.
 * computed by k-fold CV, and computes
 * the mean and standard deviation of these. This aggregation wrapper is for
 * metrics of {@link com.arosbio.ml.metrics.plots.PlotMetric
 * PlotMetric} type
 * 
 * @author staffan
 *
 * 
 */
public class PlotMetricAggregation implements PlotMetric {

	private static final Logger LOGGER = LoggerFactory.getLogger(PlotMetricAggregation.class);

	public final static String STANDARD_DEVIATION_NAME_SUFFIX = "_SD";

	private List<Plot2D> results = new ArrayList<>();
	private int totalCount = 0;

	private PlotMetric type;
	private boolean addAllIndividualResults = false;

	public PlotMetricAggregation(PlotMetric metric) {
		type = metric.clone();
	}

	/**
	 * Whether all plots should be returned, or if only the {@code mean} and
	 * {@code standard deviation}
	 * should be returned
	 * 
	 * @param addAll {@code true} of all individual plots should be saved
	 * @return the calling instance
	 */
	public PlotMetricAggregation returnAllPlots(boolean addAll) {
		this.addAllIndividualResults = addAll;
		return this;
	}

	public void addSplitEval(PlotMetric metric) {
		results.add(metric.buildPlot());
		totalCount += metric.getNumExamples();
	}

	@Override
	public int getNumExamples() {
		return totalCount;
	}

	@Override
	public void clear() {
		results.clear();
		totalCount = 0;
	}

	@Override
	public boolean goalIsMinimization() {
		return type.goalIsMinimization();
	}

	@Override
	public String getName() {
		return type.getName();
	}

	public PlotMetric spawnNewMetricInstance() {
		return type.clone();
	}

	@Override
	public PlotMetricAggregation clone() {
		return new PlotMetricAggregation(type);
	}

	@Override
	public Plot2D buildPlot() {
		if (results.isEmpty())
			throw new IllegalStateException("No results added yet");
		if (addAllIndividualResults)
			return buildPlotWithAllSplits();
		else
			return buildPlotWithAvgAndStdOnly();
	}

	private static class NumberComparator implements Comparator<Number> {

		@Override
		public int compare(Number o1, Number o2) {
			return Double.compare(o1.doubleValue(), o2.doubleValue());
		}

	}

	public Plot2D buildPlotWithAvgAndStdOnly() {
		Map<String, Map<Number, DescriptiveStatistics>> sums = new LinkedHashMap<>();

		X_Axis xLabel = results.get(0).getXlabel();

		// Find all x-ticks and y-labels first
		Set<Number> allTicks = new HashSet<>();
		Set<String> yLabels = new HashSet<>();
		for (Plot2D p : results) {
			allTicks.addAll(p.getXvalues());
			yLabels.addAll(p.getYlabels());
		}
		List<Number> xTicks = new ArrayList<>(allTicks);
		Collections.sort(xTicks, new NumberComparator());

		// Set up summary-statistic for every y-label
		for (String yLabel : yLabels) {

			Map<Number, DescriptiveStatistics> x2sum = new LinkedHashMap<>();
			for (Number x : xTicks) {
				x2sum.put(x, new DescriptiveStatistics());
			}
			sums.put(yLabel, x2sum);
		}

		// For every plot
		for (Plot2D p : results) {

			List<Number> currXTicks = p.getXvalues();
			// For every y-label
			for (Map.Entry<String, List<Number>> kv : p.getCurves().entrySet()) {
				if (kv.getKey().equals(xLabel.label())) {
					// Skip if it is the x-ticks
					continue;
				}

				Map<Number, DescriptiveStatistics> ySS = sums.get(kv.getKey());

				for (int i = 0; i < currXTicks.size(); i++) {
					try {
						ySS.get(currXTicks.get(i)).addValue(kv.getValue().get(i).doubleValue());
					} catch(Exception e){
						LOGGER.error("Failed compiling metric {} for x-tick {} index {} out of list of length {}",
							kv.getKey(), currXTicks.get(i),i,kv.getValue().size());

					}
				}

			}

		}

		// Put together the plot
		Map<String, List<Number>> curves = new LinkedHashMap<>();
		// X-axes first
		curves.put(xLabel.label(), xTicks);
		// Go through remaining y-labels and compute mean and std
		for (Map.Entry<String, Map<Number, DescriptiveStatistics>> kvSum : sums.entrySet()) {
			if (kvSum.getKey().startsWith(VAPCalibrationPlotBuilder.NUM_EX_PER_BIN_LABEL)){
				// This is the number of examples per bin for CVAP calibration - use sum instead of mean+/-std
				List<Number> numExamples = new ArrayList<>();
				for (DescriptiveStatistics kv : kvSum.getValue().values()) {
					numExamples.add(kv.getSum());
				}
				curves.put(kvSum.getKey(), numExamples);
			} else {
				List<Number> means = new ArrayList<>();
				List<Number> std = new ArrayList<>();
				// Go through them, they are in correct order already due to usage of
				// LinkedHashMap
				for (DescriptiveStatistics kv : kvSum.getValue().values()) {
					double mean = kv.getMean();
					// Special-treat mean value if NaN - could be only [POS|NEG]_INF values
					if (Double.isNaN(mean)){
						mean = MathUtils.mean(kv.getValues());
					}
					means.add(mean);
					std.add(kv.getStandardDeviation());
				}

				curves.put(kvSum.getKey(), means);
				curves.put(kvSum.getKey() + STANDARD_DEVIATION_NAME_SUFFIX, std);
			}
		}

		if (LOGGER.isTraceEnabled()) {
			for (Map.Entry<String, List<Number>> ent : curves.entrySet()) {
				LOGGER.trace("label={}, length={}", ent.getKey(), ent.getValue().size());
			}
		}

		return new Plot(curves, xLabel);
	}

	public Plot2D buildPlotWithAllSplits() {
		Plot2D x_mean_std_plot = buildPlotWithAvgAndStdOnly();

		X_Axis xLab = x_mean_std_plot.getXlabel();

		Map<String, List<Number>> curves = x_mean_std_plot.getCurves();
		List<Number> xTicks = curves.get(xLab.label());
		// Simply add all individual ones, appending an index to each
		for (int i = 0; i < results.size(); i++) {
			Plot2D current = results.get(i);
			List<Number> currentXticks = current.getXvalues();
			
			for (Map.Entry<String, List<Number>> kv : current.getCurves().entrySet()) {
				if (kv.getKey().equals(xLab.label())) {
					continue; // Skip x-ticks
				}
				// Make sure the list is of the correct length
				List<Number> values = kv.getValue();
				if (! xTicks.equals(currentXticks)){
					if (kv.getKey().startsWith(VAPCalibrationPlotBuilder.NUM_EX_PER_BIN_LABEL)){
						// For numbers - fill with 0 occurrences, makes more sense than NaN
						values = fillGaps(xTicks, currentXticks, values, 0);
					} else {
						values = fillGaps(xTicks, currentXticks, values, Double.NaN);
					}
				} 
				
				curves.put(String.format("%s_%d", kv.getKey(), i), values);
			}
		}

		return new Plot(curves, xLab);
	}


	static List<Number> fillGaps(List<Number> allXs, List<Number> currXs, List<Number> values, double fallback){
		List<Number> out = new ArrayList<>(allXs.size());
		int j = 0;
		for (Number x : allXs){
			boolean added = false;
			for (; j<currXs.size() && ! added; ){
				if (MathUtils.equals(x.doubleValue(), currXs.get(j).doubleValue(), 0.00001)){
					// Had the value - add it as well as step the j index
					out.add(values.get(j));
					added = true;
					j++;
				} else if (x.doubleValue()<currXs.get(j).doubleValue()){
					// current x-ticks did not contain a value, add NaN
					out.add(fallback);
					added = true;
				} else {
					// shouldn't happen in theory.. only in case the currXs has a value not in allXs
					LOGGER.debug("One metric had an x-value not part of the previously found Xs: {}",currXs);
					j++;
				}
			}
			if (! added){
				out.add(fallback);
			}
		}
		return out;
	}

	@Override
	public void setEvaluationPoints(List<Double> points) {
		if (type.getEvaluationPoints() != points)
			throw new IllegalArgumentException("New points do not match the old ones!");
		// Do nothing
	}

	@Override
	public List<Double> getEvaluationPoints() {
		return type.getEvaluationPoints();
	}

	public String toString(){
		return PlotMetric.toString(this);
	}

}

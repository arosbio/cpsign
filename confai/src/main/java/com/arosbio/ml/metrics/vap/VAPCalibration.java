/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics.vap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.MathUtils;
import com.arosbio.ml.metrics.cp.CalibrationPlot;
import com.arosbio.ml.metrics.plots.Plot2D.X_Axis;
import com.arosbio.ml.metrics.plots.PlotMetric;
import com.google.common.collect.Range;

public class VAPCalibration implements PlotMetric, VAPMetric {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(VAPCalibration.class);
	private static final X_Axis X_AXIS = X_Axis.EXPECTED_PROB;
	private static final String Y_AXIS = "Observed frequency";
	private static final int DEFAULT_NUM_BINS = 10;
	private static final int MIN_NUM_BINS = 5;
	private static final int MAX_NUM_BINS = 100;

	public static final String METRIC_NAME = "VAP calibration plot";
	public static final String METRIC_DESCRIPTION = "Model calibration for a probabilistic model, uses binning in order to calculate the frequency of classes for each bin of expected probability (i.e. predicted probability)";
	public static final String NUM_EX_PER_BIN_LABEL = "Num examples in bin";

	// Settings 
	private Integer label;

	/**
	 * Bins both contains their range and the test-data found in them
	 * at clone-time, need to copy the ranges but not the state
	 */
	private List<CalibBin> bins = new ArrayList<>();
	private int numTestInstances = 0;

	/**
 	 * Internal class that keeps track of the range of the bin, the total number
	 * of test-instances that has been part of this bin and the number of the studied label
 	 */	
	private static class CalibBin {
		final Range<Double> range;
		int numTotal=0, numOfClass=0;
		public CalibBin(Range<Double> range){
			this.range = range;
		}

		public double getObservedProbability(){
			return ((double)numOfClass)/numTotal;
		}

		public double getMidpoint(){
			return 0.5* (range.lowerEndpoint() + range.upperEndpoint());
		}
	}
	
	/**
	 * Creates a default calibration points with 10 separate <i>bins</i>
	 * in which the observed probability is calculated. These are created 
	 * as non-overlapping bins
	 */
	public VAPCalibration() {
		this(DEFAULT_NUM_BINS,1d/DEFAULT_NUM_BINS);
	}
	
	/**
	 * Creates a {@code numBins} calibration points
	 * in which the observed probability is calculated. These are created 
	 * as non-overlapping bins
	 * @param numBins Number of bins to use, allowed range [5..100]
	 * @throws IllegalArgumentException In case {@code numBins} is outside of allowed range
	 */
	public VAPCalibration(int numBins) throws IllegalArgumentException {
		this(numBins, 1d/numBins);
	}
	
	/**
	 * Create custom calibration bins given by as arguments, if {@code numBins} and {@code binWidth}
	 * covers the complete range of possible values (i.e. {@code numBins * binWidth == 1}) the bins will
	 * be created as non-overlapping. Otherwise all bins will be closed and may either overlap or miss parts of 
	 * possible range of probabilities.
	 * @param numBins Number of bins to use, allowed range [5..100]
	 * @param binWidth Width of each bin, (0..inf)
	 * @throws IllegalArgumentException In case {@code numBins} or {@code binWidth} is outside of their allowed range
	 */
	public VAPCalibration(int numBins, double binWidth) throws IllegalArgumentException {
		setupEvalBins(numBins, binWidth);
	}
	
	/**
	 * Creates custom bins to use, each with the specified {@code binWidth}. These may be overlapping.
	 * Each bin will be centered around the midpoint and have the same width
	 * @param binMindpoints Midpoints of all calibration bins
	 * @param binWidth Width of each bin
	 */
	public VAPCalibration(List<Double> binMindpoints, double binWidth) {
		setupEvalBins(binMindpoints, binWidth);
	}
	
	/**
	 * Custom bins to use. Note that all Ranges must have finite endpoints at both sides.
	 * @param calibrationBins Bins to use
	 */
	public VAPCalibration(List<Range<Double>> calibrationBins) {
		setupEvalBins(calibrationBins);
	}

	private void setupEvalBins(List<Range<Double>> calibBins){
		if (calibBins == null || calibBins.isEmpty())
			throw new IllegalArgumentException("No bins given");
		
		List<CalibBin> tmp = new ArrayList<>(calibBins.size());
		for (Range<Double> r : calibBins){
			if (! r.hasLowerBound() || ! r.hasUpperBound()){
				throw new IllegalArgumentException("Calibration bins must have upper and lower bounds");
			}

			tmp.add(new CalibBin(r));
		}

		// Everything OK - update bins
		this.bins = tmp;
	}

	private void setupEvalBins(int numBins, double width){
		if (width < 0)
			throw new IllegalArgumentException("Argument width must be positive, was="+width);
		if (numBins < MIN_NUM_BINS)
			throw new IllegalArgumentException("Number of evaluation points must be >=" + MIN_NUM_BINS);
		if (numBins > MAX_NUM_BINS)
			throw new IllegalArgumentException("Number of evaluation points must be <=" + MAX_NUM_BINS);

		// Deduce if the bins should be closed-open (non-overlapping) or closed-closed (overlapping)
		boolean coBins = Math.abs(numBins*width - 1) < 0.001;
		
		List<CalibBin> tmp = new ArrayList<>(numBins);
		final double spacing = 1d/numBins;
		double midP = 0;
		// add all except last bin
		for (int i=0;i<numBins-1; i++) {
			midP = spacing*(i+0.5);
			if (coBins)
				tmp.add(new CalibBin(Range.closedOpen(midP-width*.5, midP+width*.5)));
			else
				tmp.add(new CalibBin(Range.closed(midP-width*.5, midP+width*.5)));
		}	
		// Add last bin (closed-closed)
		midP = spacing*(numBins-.5);
		tmp.add(new CalibBin(Range.closed(midP-width*.5, midP+width*.5)));

		// Everything OK - update bins field
		bins = tmp;
	}

	private void setupEvalBins(List<Double> points, double w){
		if (w < 0)
			throw new IllegalArgumentException("Argument width must be positive, was="+w);
		if (points== null || points.isEmpty())
			throw new IllegalArgumentException("Argument points must be a non-empty list");

		List<Double> checkedPoints = PlotMetric.sortAndValidateList(points);

		List<CalibBin> tmp = new ArrayList<>(checkedPoints.size());

		for (Double p : points){
			tmp.add(new CalibBin(Range.closed(p-.5*w, p+.5*w)));
		}
		
		bins = tmp;
	}

	@Override
	public boolean supportsMulticlass() {
		return false;
	}

	@Override
	public String getDescription(){
		return METRIC_DESCRIPTION;
	}

	public Set<String> getYLabels(){
		return Set.of(Y_AXIS,NUM_EX_PER_BIN_LABEL);
	}
	
	@Override
	public String getPrimaryMetricName(){
		return Y_AXIS;
	}
	
	public void setLabel(int label) throws IllegalStateException {
		assertChangesAllowed();
		this.label = label;
	}
	
	public void addPrediction(int trueLabel, Map<Integer,Double> probabilities) {
		if (label == null){
			label = MathUtils.max(probabilities.keySet());
			LOGGER.debug("Label not set for {}, using the maximum label={} for calculation",METRIC_NAME,label);
		}
		// Get the probability for the label we're using
		double prob = probabilities.get(label);

		// Loop through and update all bins that the prediction falls into
		for (CalibBin b : bins){
			if (b.range.contains(prob)){
				// part of this range
				if (trueLabel == label){
					b.numOfClass++;
				}
				b.numTotal++;
			}
		}
		numTestInstances++;
	}
	
	@Override
	public void addPrediction(int trueLabel, 
			Map<Integer, Double> probabilities, 
			double meanIntervalWidth,
			double medianIntervalWidth) {
		addPrediction(trueLabel, probabilities);
	}
	
	@Override
	public CalibrationPlot buildPlot() throws IllegalArgumentException, IllegalStateException {
		if (getNumExamples() <= 0){
			throw new IllegalStateException("Cannot build plot without evaluation data");
		}

		List<Number> expected = new ArrayList<>(),
				obs = new ArrayList<>(),
				examples = new ArrayList<>();

		for (CalibBin b : bins){
			if (b.numTotal <= 0){
				LOGGER.debug("No test instances found in bin of range {} - thus skipped", b.range);
				continue;
			}

			expected.add(MathUtils.roundTo3significantFigures(b.getMidpoint()));
			examples.add(b.numTotal);
			obs.add(b.getObservedProbability());
		}
		
		Map<String,List<Number>> curves = new HashMap<>();
		curves.put(X_AXIS.label(), expected);
		curves.put(Y_AXIS, obs);
		curves.put(NUM_EX_PER_BIN_LABEL, examples);
		
		CalibrationPlot plot = new CalibrationPlot(curves,X_AXIS,Y_AXIS,label);
		plot.setNumExamplesUsed(numTestInstances);
				
		return plot;
		
	}
	@Override
	public int getNumExamples() {
		return numTestInstances;
	}

	@Override
	public String getName() {
		return METRIC_NAME;
	}

	@Override
	public VAPCalibration clone() {
		VAPCalibration clone = new VAPCalibration(getBins());
		clone.label = label;
		return clone;
	}

	@Override
	public void clear() {
		for (CalibBin b : bins){
			b.numOfClass=0;
			b.numTotal = 0;
		}
		numTestInstances = 0;
	}

	@Override
	public boolean goalIsMinimization() {
		return false;
	}

	private void assertChangesAllowed(){
		if (numTestInstances>0){
			throw new IllegalStateException("Cannot change settings once test-instances has been added");
		}
	}

	@Override
	public void setEvaluationPoints(List<Double> points) {
		if (points == null || points.isEmpty()){
			throw new IllegalStateException("Cannot set empty evaluation points");
		}

		List<Double> checkedPoints = PlotMetric.sortAndValidateList(points);

		double w = 1;
		if (points.size()>1){
			List<Double> diffs = new ArrayList<>();
			for (int i=0; i<checkedPoints.size()-1; i++){
				diffs.add(checkedPoints.get(i+1) - checkedPoints.get(i));
			}
			w = MathUtils.mean(diffs);
		}

		LOGGER.debug("Using width {} based on evaluation points: {}",w, points);
		setEvaluationPoints(points, w);
	}

	public void setEvaluationPoints(List<Double> midpoints, double binWidth){
		assertChangesAllowed();

		setupEvalBins(midpoints, binWidth);
	}

	@Override
	public List<Double> getEvaluationPoints() {
		List<Double> midPoints = new ArrayList<>();
		for (CalibBin b : bins){
			midPoints.add(b.getMidpoint());
		}
		return midPoints;
	}
	
	List<Range<Double>> getBins(){
		List<Range<Double>> ranges = new ArrayList<>();
		for (CalibBin b : bins){
			ranges.add(b.range);
		}
		return ranges;
	}
	
	public String toString() {
		return PlotMetric.toString(this);
	}
}

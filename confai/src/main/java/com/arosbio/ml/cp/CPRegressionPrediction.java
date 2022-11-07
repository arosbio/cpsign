/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.arosbio.ml.IntervalUtils;
import com.google.common.collect.Range;
import com.google.common.math.DoubleMath;

/**
 * The <code>CPRegressionPrediction</code> holds the result of a Conformal Regressor prediction. 
 * 
 * @author staffan
 */
public class CPRegressionPrediction {

	private static final double EQUIV_TOL=1e-5;

	private double y_hat;  //The midpoint
	private double intervalScaling;
	private double minObs;
	private double maxObs;
	
	/** Confidence dependent prediction intervals */ 
	private Map<Double,PredictedInterval> intervals = new HashMap<>();
	
	/** Width dependent prediction intervals */
	private Map<Double,PredictedInterval> predictionWidthBasedIntervals = new HashMap<>();
	
	public class PredictedInterval implements Comparable<PredictedInterval>{
		private double predictedHalfIntervalWidth;
		private double confidence;
		
		public PredictedInterval(double confidence, double intervalHalfWidth) {
			this.confidence = confidence;
			if (intervalHalfWidth < 0)
				throw new IllegalArgumentException("Interval widths must be positive, got: " + intervalHalfWidth);
			this.predictedHalfIntervalWidth=intervalHalfWidth; // Width must be positive
		}
		
		public double getIntervalHalfWidth() {
			return predictedHalfIntervalWidth;
		}
		
		public double getIntervalWidth() {
			return predictedHalfIntervalWidth*2;
		}
		
		public Range<Double> getInterval(){
			return IntervalUtils.getInterval(y_hat, predictedHalfIntervalWidth);
		}
		
		public Range<Double> getCappedInterval(){
			return IntervalUtils.getCappedInterval(y_hat, predictedHalfIntervalWidth, minObs, maxObs);
		}
		
		public double getConfidence() {
			return confidence;
		}
		
		public Map<Object,Object> asMap(){
			Map<Object,Object> map = new HashMap<>();
			map.put("confidence", confidence);
			map.put("interval", getInterval());
			map.put("cappedInterval", getCappedInterval());
			map.put("intervalWidth", predictedHalfIntervalWidth*2);
			return map;
		}
		
		public String toString() {
			return asMap().toString();
		}

		@Override
		public int compareTo(PredictedInterval o) {
			return Double.compare(this.confidence, o.confidence);
		}

		public boolean equals(Object o){
			if (! (o instanceof PredictedInterval))
				return false;
			return equals((PredictedInterval)o);
		}

		public boolean equals(PredictedInterval o){
			return (DoubleMath.fuzzyEquals(o.confidence, confidence, EQUIV_TOL)) && 
				(DoubleMath.fuzzyEquals(o.predictedHalfIntervalWidth, predictedHalfIntervalWidth, EQUIV_TOL));
		}
	}
	

	/* ========================================
	 *  CONSTRUCTORS
	 * ========================================
	 */
	
	public CPRegressionPrediction(double y_hat, double intervalScaling, double minObservation, double maxObservation) {
		this.y_hat = y_hat;
		this.intervalScaling = intervalScaling;
		this.minObs = minObservation;
		this.maxObs = maxObservation;
	}
	
	public CPRegressionPrediction(double y_hat, double intervalScaling, double minObservation, double maxObservation, Map<Double,PredictedInterval> intervals) {
		this(y_hat, intervalScaling, minObservation, maxObservation);
		this.intervals = intervals;
	}
	
	
	/**
	 * y_hat is the midpoint of the prediction
	 * @return The midpoint of the prediction
	 */
	public double getY_hat() {
		return y_hat;
	}
	
	public double getMinObs() {
		return minObs;
	}

	public double getMaxObs() {
		return maxObs;
	}

	/**
	 * Interval scaling is the value that the NCS should be multiplied with to yield the +/- interval for 
	 * a given confidence
	 * @return the scaling
	 */
	public double getIntervalScaling() {
		return intervalScaling;
	}
	
	public void setPredictedIntervals(Map<Double,PredictedInterval> intervals) {
		this.intervals = intervals;
	}
	
	public void setWidthBasedIntervals(Map<Double,PredictedInterval> intervals) {
		this.predictionWidthBasedIntervals = intervals;
	}
	
	public Map<Double,PredictedInterval> getIntervals(){
		return intervals;
	}
	
	public Map<Double,PredictedInterval> getWidthToConfidenceBasedIntervals(){
		return predictionWidthBasedIntervals;
	}
	
	public List<Double> getConfidences(){
		List<Double> confs = new ArrayList<>(intervals.keySet());
		Collections.sort(confs);
		return confs;
	}
	
	/**
	 * Get the widths used for predicted widths -&gt; confidence 
	 * @return A list of the widths used
	 */
	public List<Double> getPredictedWidths(){
		if (predictionWidthBasedIntervals==null || predictionWidthBasedIntervals.isEmpty())
			return new ArrayList<>();
		List<Double> dists = new ArrayList<>(predictionWidthBasedIntervals.keySet());
		Collections.sort(dists);
		return dists;
	}
	
	public PredictedInterval getInterval(double confidence) {
		if (intervals != null)
			return intervals.get(confidence);
		return null;
	}
	
	public Map<String, Object> asMap(){
		Map<String, Object> map = new HashMap<>();
		map.put("y_hat", y_hat);
		map.put("intervalScaling", intervalScaling);
		map.put("minObs", minObs);
		map.put("maxObs", maxObs);
		if (intervals != null && ! intervals.isEmpty()) {
			Map<Double, Map<Object,Object>> inters = new HashMap<>();
			for (double conf: intervals.keySet()) {
				inters.put(conf, intervals.get(conf).asMap());
			}
			map.put("intervals", inters);
		}
		if (predictionWidthBasedIntervals != null && ! predictionWidthBasedIntervals.isEmpty()) {
			Map<Double, Map<Object,Object>> inters = new HashMap<>();
			for (double dist: predictionWidthBasedIntervals.keySet()) {
				inters.put(dist, predictionWidthBasedIntervals.get(dist).asMap());
			}
			map.put("widthBasedIntervals", inters);
		}
		return map;
	}

	public String toString(){
		return "CPRegressionPrediction: "+ asMap().toString();
	}
	
	public boolean equals(Object o){
		if (! (o instanceof CPRegressionPrediction))
			return false;
		CPRegressionPrediction other = (CPRegressionPrediction)o;
		// Check the scalar values first
		if (! DoubleMath.fuzzyEquals(y_hat, other.y_hat, EQUIV_TOL) &&
			DoubleMath.fuzzyEquals(intervalScaling, other.intervalScaling, EQUIV_TOL) &&
			DoubleMath.fuzzyEquals(minObs, other.minObs, EQUIV_TOL) &&
			DoubleMath.fuzzyEquals(maxObs, other.maxObs, EQUIV_TOL))
			return false;
		// Check the standard CIs after
		if (intervals.size() != other.intervals.size())
			return false;
		if (!intervals.isEmpty()){
			// Should have the same keys
			if (!intervals.keySet().equals(other.intervals.keySet()))
				return false;
			// Check each CI
			for (double c : intervals.keySet()){
				if (!intervals.get(c).equals(other.intervals.get(c)))
					return false;
			}
		}

		// Check the CI width -> conf predictions
		if (predictionWidthBasedIntervals.size() != other.predictionWidthBasedIntervals.size())
			return false;
		if (!predictionWidthBasedIntervals.isEmpty()){
			// Should have the same keys - going width->conf this is the only requirement
			if (!predictionWidthBasedIntervals.keySet().equals(other.predictionWidthBasedIntervals.keySet()))
				return false;
		}
		
		// Everything equals
		return true;
	}


}

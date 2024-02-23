package com.arosbio.ml.metrics.cp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.arosbio.commons.mixins.Aliased;
import com.arosbio.data.NamedLabels;
import com.arosbio.ml.cp.PValueTools;
import com.arosbio.ml.metrics.LabelsMixin;
import com.arosbio.ml.metrics.cp.classification.CPClassifierMetric;
import com.arosbio.ml.metrics.cp.regression.CPRegressionMultiMetric;
import com.arosbio.ml.metrics.plots.Plot2D.X_Axis;
import com.arosbio.ml.metrics.plots.PlotMetric;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;

public class ModelCalibration implements LabelsMixin, CPClassifierMetric, CPRegressionMultiMetric, Aliased {

	public static final String METRIC_NAME = "Calibration plot";
    public static final String METRIC_ALIAS = "Model calibration";

	private static final X_Axis X_AXIS = X_Axis.CONFIDENCE;
	private static final String Y_AXIS = "Accuracy";

	private List<Double> confidences = new ArrayList<>();
	/** Counter for overall and regression */
	private Map<Double, Counter> counters = new HashMap<>();
	/** Mondrian counters - counting for each class (for classification only) */
	private Map<Double, Map<Integer,Counter>> mondrianCounters = new HashMap<>();
	private Set<Integer> encounteredLabels = new HashSet<>();
	private int numAddedPredictions = 0;

	private NamedLabels labels;

	
	public ModelCalibration() {
		setEvaluationPoints(DEFAULT_EVALUATION_POINTS);
	}

	public ModelCalibration(List<Double> confidenceLevels) {
		setEvaluationPoints(confidenceLevels);
	}

    private static class Counter {

		private final Integer label;
		private int numExamples, numCorrects;

		public static Counter overall(){
			return new Counter(null);
		}
		public static Counter forLabel(int label){
			return new Counter(label);
		}

		private Counter(Integer label) {
			this.label = label;
		}

		private void addClassificationPrediction(Integer trueLabel, Collection<Integer> predictedLabels) {
			if (
					label == null  // Overall counter
					|| 
					label.equals(trueLabel) // counter for this specific class
					) {
				// Overall counter
				numExamples++;
				numCorrects += (predictedLabels.contains(trueLabel)? 1 : 0);
			} 
		}

        private void addRegressionPrediction(double trueLabel,Range<Double> ci){
            numExamples++;
            if (ci.contains(trueLabel)){
                numCorrects++;
            }
        }

		public double getAccuracy() {
			if (numExamples ==0)
				return Double.NaN;
			return ((double)numCorrects)/numExamples;
		}
	}

    @Override
	public String getName() {
		return METRIC_NAME;
	}

    @Override
    public String[] getAliases(){
        return new String[]{METRIC_ALIAS};
    }
	@Override
	public boolean supportsMulticlass() {
		return true;
	}

	public String getPrimaryMetricName(){
		return Y_AXIS;
	}

	public Set<String> getYLabels(){
		Set<String> yLabels = new HashSet<>();
		yLabels.add(Y_AXIS);
		if (encounteredLabels != null && !encounteredLabels.isEmpty()){
			for (int label : encounteredLabels){
				yLabels.add(fmtYLabelMondrian(label));
			}
		} else if (labels != null){
			for (int label : labels.getLabels().keySet()){
				yLabels.add(fmtYLabelMondrian(label));
			}
		}
		return yLabels;
	}
	
	private String fmtYLabelMondrian(Integer label){
		String lab = (labels!=null? labels.getLabel(label): ""+label);
		return String.format("%s(%s)", Y_AXIS,lab);
	}

	@Override
	public CalibrationPlot buildPlot() throws IllegalStateException {

		if (numAddedPredictions <= 0)
			throw new IllegalStateException("No predictions added - cannot generate a calibration plot");

		List<Number> overallAccuracies = new ArrayList<>();

		// Overall accuracies
		for (double conf : confidences) {
			overallAccuracies.add(counters.get(conf).getAccuracy());
		}		

		Map<String,List<Number>> plotValues = new LinkedHashMap<>();
		List<Number> confAsNumber = new ArrayList<>(confidences);
		plotValues.put(X_AXIS.label(), confAsNumber);
		plotValues.put(Y_AXIS, overallAccuracies);

        // Mondrian accuracies
        if (!mondrianCounters.values().iterator().next().isEmpty()){
            Map<Integer, List<Number>> mondrianAccuracies = new HashMap<>();
            for (int lab : encounteredLabels) {
                mondrianAccuracies.put(lab, new ArrayList<>());
            }
            for (double conf : confidences) {
                for (int lab : encounteredLabels) {
                    mondrianAccuracies.get(lab)
                    .add(
                            mondrianCounters.get(conf).get(lab).getAccuracy()
                            );
                }
            }
            for (Map.Entry<Integer, List<Number>> label : mondrianAccuracies.entrySet()) {
                plotValues.put(fmtYLabelMondrian(label.getKey()), 
                        label.getValue());
            }
        }

		CalibrationPlot plot = new CalibrationPlot(plotValues,X_AXIS,Y_AXIS);
		plot.setNumExamplesUsed(numAddedPredictions); // all of them has the same number of examples
		return plot;
	}

    // REGRESSION 
    @Override
	public void addPrediction(double trueLabel, Map<Double, Range<Double>> predictedIntervals) {
        for (double conf : confidences){
            Counter c = counters.get(conf);
            c.addRegressionPrediction(trueLabel, predictedIntervals.get(conf));
        }
        numAddedPredictions++;
	}

    // CLASSIFICATION
	@Override
	public void addPrediction(int trueLabel, Map<Integer, Double> pValues) {
		encounteredLabels.add(trueLabel);

		for (double conf : confidences) {
			Set<Integer> predSet = PValueTools.getPredictionSet(pValues, conf);
			
			// Overall stats
			counters.get(conf).addClassificationPrediction(trueLabel, predSet);

			// Mondrian stats
			if (!mondrianCounters.get(conf).containsKey(trueLabel)) {
				mondrianCounters.get(conf).put(trueLabel, Counter.forLabel(trueLabel));
			}
			mondrianCounters.get(conf).get(trueLabel).addClassificationPrediction(trueLabel, predSet);
		}

		numAddedPredictions++;

	}


	@Override
	public int getNumExamples() {
        return numAddedPredictions;
	}

	
	
	@Override
	public ModelCalibration clone() {
		ModelCalibration clone = new ModelCalibration(confidences);
		if (labels != null)
			clone.labels = labels.clone();
		return clone;
	}

	@Override
	public void clear() {
		setEvaluationPoints(confidences);
		encounteredLabels.clear();
		numAddedPredictions=0;
	}

	@Override
	public void setLabels(NamedLabels labels) {
		this.labels = labels;
	}

	@Override
	public boolean goalIsMinimization() {
		return false;
	}

	@Override
	public void setEvaluationPoints(List<Double> points) {
		confidences = PlotMetric.sortAndValidateList(points);

		mondrianCounters = new HashMap<>();
		counters = new HashMap<>();
		for (double c : confidences) {
			mondrianCounters.put(c, new HashMap<>());
			counters.put(c, Counter.overall());
		}

	}

	@Override
	public List<Double> getEvaluationPoints() {
		return ImmutableList.copyOf(confidences);
	}

	public String toString() {
		return PlotMetric.toString(this);
	}

}

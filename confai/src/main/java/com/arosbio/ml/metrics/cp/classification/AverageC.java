package com.arosbio.ml.metrics.cp.classification;

import java.util.Map;

import org.apache.commons.math3.stat.descriptive.moment.Mean;

import com.arosbio.commons.mixins.Aliased;
import com.arosbio.commons.mixins.Described;
import com.arosbio.ml.cp.PValueTools;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.cp.ConfidenceDependentMetric;

public class AverageC implements SingleValuedMetric, CPClassificationMetric, ConfidenceDependentMetric, Aliased, Described {

    public final static String METRIC_NAME = "AverageC";
    public final static String METRIC_ALIAS = "AvgC";
    public final static String METRIC_DESCRIPTION = "The average number of classes in the prediction set, given a specific confidence level.";


    private double confidence = ConfidenceDependentMetric.DEFAULT_CONFIDENCE;
    private Mean counter = new Mean();

    public AverageC(){}

    public AverageC(double confidence){
        setConfidence(confidence);
    }

    @Override
    public int getNumExamples() {
        return (int) counter.getN();
    }

    @Override
    public double getConfidence() {
        return confidence;
    }

    @Override
    public void setConfidence(double confidence) throws IllegalArgumentException, IllegalStateException {
        if (confidence>1 || confidence <0)
            throw new IllegalArgumentException("confidence must be in the range [0..1]");
        if (counter.getN() > 0)
            throw new IllegalStateException("Already started to add predictions - cannot change confidence at this point");
        this.confidence = confidence;
    }

    @Override
    public void clear() {
        counter.clear();
    }

    @Override
    public boolean goalIsMinimization() {
        return true;
    }

    @Override
    public String getName() {
        return METRIC_NAME;
    }

    @Override
    public boolean supportsMulticlass() {
        return true;
    }

    @Override
    public String getDescription() {
        return METRIC_DESCRIPTION;
    }

    @Override
    public String[] getAliases() {
        return new String[]{METRIC_ALIAS};
    }

    @Override
    public void addPrediction(int observedLabel, Map<Integer, Double> pValues) {
        counter.increment(PValueTools.getPredictionSetSize(pValues, confidence));
    }

    @Override
    public double getScore() {
        if (counter.getN() == 0)
            return Double.NaN;
        return counter.getResult();
    }

    @Override
    public Map<String, ? extends Object> asMap() {
        return Map.of(METRIC_NAME, getScore());
    }

    public AverageC clone(){
        return new AverageC(confidence);
    }


    
}

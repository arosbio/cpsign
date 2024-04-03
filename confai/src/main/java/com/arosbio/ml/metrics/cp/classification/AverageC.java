package com.arosbio.ml.metrics.cp.classification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.mixins.Aliased;
import com.arosbio.ml.cp.PValueTools;
import com.arosbio.ml.metrics.plots.Plot;
import com.arosbio.ml.metrics.plots.Plot2D;
import com.arosbio.ml.metrics.plots.Plot2D.X_Axis;
import com.arosbio.ml.metrics.plots.PlotMetric;
import com.google.common.collect.ImmutableList;

public class AverageC implements PlotMetric, CPClassifierMetric, Aliased {

    private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(AverageC.class);

    public final static String METRIC_NAME = "AverageC";
    public final static String METRIC_ALIAS = "AvgC";
    public final static String METRIC_DESCRIPTION = "The average number of classes in the prediction set for each confidence level.";


    // private List<Double> confidenceLevels = new ArrayList<>();
    private Map<Double,Mean> counters = new HashMap<>();
    private int numEx = 0;

    public AverageC(){
        setEvaluationPoints(DEFAULT_EVALUATION_POINTS);
    }

    public AverageC(List<Double> confidences){
        setEvaluationPoints(confidences);
    }

    @Override
    public int getNumExamples() {
        return numEx;
    }


    public String toString(){
        return PlotMetric.toString(this);
    }

    @Override
    public void clear() {
        // This method clears everything
        setEvaluationPoints(new ArrayList<>(counters.keySet()));
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
	public String getPrimaryMetricName() {
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
        for (Map.Entry<Double,Mean> kv : counters.entrySet()){
            kv.getValue().increment(PValueTools.getPredictionSetSize(pValues, kv.getKey()));
        }
        numEx++;
    }


    public AverageC clone(){
        return new AverageC(new ArrayList<>(counters.keySet()));
    }

    @Override
    public Plot2D buildPlot() throws IllegalStateException {
        if (numEx == 0){
            LOGGER.debug("Attempted building plot when no evaluation data given");
            throw new IllegalStateException("Cannot build plot before evaluation data has been given");
        }
        List<Number> avgCs = new ArrayList<>();
		List<Double> confs = new ArrayList<>(counters.keySet());
		Collections.sort(confs);

		for (Double conf : confs) {
            Mean counter = counters.get(conf);
            if (counter.getN() == 0){
                avgCs.add(Double.NaN);
            } else {
                avgCs.add(counter.getResult());
            }
		}
		
		Map<String,List<Number>> plotValues = new HashMap<>();
		plotValues.put(X_Axis.CONFIDENCE.label(), new ArrayList<>(confs));
		plotValues.put(METRIC_NAME, avgCs);
		
		Plot plot = new Plot(plotValues, X_Axis.CONFIDENCE);
        plot.setNumExamplesUsed(numEx);
        plot.setPlotName(METRIC_NAME);
		
		return plot;
    }

    @Override
    public void setEvaluationPoints(List<Double> points) {
        List<Double> confidenceLevels = PlotMetric.sortAndValidateList(points);
        counters.clear();
        for (double conf : confidenceLevels){
            counters.put(conf, new Mean());
        }
        numEx=0;
    }

    @Override
    public List<Double> getEvaluationPoints() {
        List<Double> confidenceLevels = new ArrayList<>(counters.keySet());
        Collections.sort(confidenceLevels);
        return ImmutableList.copyOf(confidenceLevels);
    }

    @Override
    public Set<String> getYLabels() {
        return Set.of(METRIC_NAME);
    }


    
}

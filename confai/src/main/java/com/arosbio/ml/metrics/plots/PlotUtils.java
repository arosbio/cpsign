package com.arosbio.ml.metrics.plots;

import java.util.ArrayList;
import java.util.List;

import com.arosbio.ml.metrics.Metric;

public class PlotUtils {
    

    public static MergedPlot mergePlots(List<Metric> metrics) throws IllegalArgumentException {
        return mergePlots(metrics, false);
    }

    public static MergedPlot mergePlots(List<Metric> metrics, boolean useAll) throws IllegalArgumentException {
        List<Plot2D> plots = new ArrayList<>(metrics.size());
        for (Metric m : metrics){
            if (m instanceof PlotMetricAggregation && useAll){
                plots.add(((PlotMetricAggregation)m).buildPlotWithAllSplits());
            } else if (m instanceof PlotMetric) {
                plots.add(((PlotMetric)m).buildPlot());
            }
        }
        if (plots.isEmpty()){
            throw new IllegalArgumentException("No plot metrics given");
        }
        return new MergedPlot(plots);
    }
}

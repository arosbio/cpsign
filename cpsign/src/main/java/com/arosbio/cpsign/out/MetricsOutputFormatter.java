/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.out;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.arosbio.commons.MathUtils;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.metrics.classification.ROC_AUC;
import com.arosbio.ml.metrics.cp.classification.ObservedFuzziness;
import com.arosbio.ml.metrics.cp.regression.MeanPredictionIntervalWidth;
import com.arosbio.ml.metrics.plots.MergedPlot;
import com.arosbio.ml.metrics.plots.Plot2D;
import com.arosbio.ml.metrics.plots.PlotMetric;
import com.github.cliftonlabs.json_simple.Jsoner;

public class MetricsOutputFormatter {

	private final List<SingleValuedMetric> singleMetrics = new ArrayList<>();
	private final MergedPlot plot;

	public MetricsOutputFormatter(List<? extends Metric> metrics) {
		List<Plot2D> tmpPlots = new ArrayList<>();
		
		for (Metric m : metrics) {
			if (m instanceof SingleValuedMetric)
				singleMetrics.add((SingleValuedMetric) m);
			else if (m instanceof PlotMetric)
				tmpPlots.add(((PlotMetric) m).buildPlot());
			else
				throw new RuntimeException("Metric " + m.getClass() + " not recognized - it must implement SingleValuedMetric or PlotMetric!");
		}
		if (!tmpPlots.isEmpty()){
			plot = new MergedPlot(tmpPlots);
		} else {
			plot = null;
		}
	}

	public int getNumEvaluationPoints(){
		if (plot == null)
			return 0;
		else {
			return plot.getXvalues().size();
		}
		
	}

	public String getTextOnlySingleValueMetrics() {
		StringBuilder sb = new StringBuilder();
		Map<String,Object> singleMetrics = compileSingleMetrics();
		// Find the widest text string
		int widest = -1;
		for (Object key: singleMetrics.keySet()) {
			if (key.toString().length()>widest)
				widest = key.toString().length();
		}
		final String lineFormat = " - %-"+widest+"s : %s%n";
		Formatter f = new Formatter(sb);
		for (String key : singleMetrics.keySet()) {
			f.format(lineFormat, key, singleMetrics.get(key));
		}
		f.close();

		return sb.toString();
	}

	/**
	 * This method assumes that we only have a single confidence level set
	 * so we do not have a calibration plot, instead we list them and add a row
	 * with information about the confidence level
	 * @return the text
	 */
	public String getTextOnlyOneEvaluationPoint(){
		if (plot.getXvalues().size() != 1){
			throw new IllegalStateException("Plot metrics do not have a single x-value, this method should not be called!");
		}
		// Add the single-value stuff (not relying on a confidence level, or a different one than the plot)
		StringBuilder sb = new StringBuilder(getTextOnlySingleValueMetrics());
		sb.append("Confidence dependent metrics, using confidence of ").append(plot.getXvalues().get(0)).append(":%n");

		Map<String,Number> values = new LinkedHashMap<>(plot.getYlabels().size());
		// Find the widest text string and aggregate all values
		int widest = -1;
		for (Map.Entry<String,List<Number>> label2Value : plot.getCurves().entrySet()) {
			if (label2Value.getKey() == plot.getXlabel().label()){
				// Skip the x-value, included as a line above 
				continue;
			}
			widest = Math.max(widest, label2Value.getKey().length());
			Number n = label2Value.getValue().get(0);
			if (n instanceof Float || n instanceof Double){
				values.put(label2Value.getKey(), MathUtils.roundTo3significantFigures(n.doubleValue()));
			} else {
				values.put(label2Value.getKey(),n);
			}
			
		}
		final String lineFormat = " - %-"+widest+"s : %s%n";
		Formatter f = new Formatter(sb);
		for (String key : values.keySet()) {
			f.format(lineFormat, key, values.get(key));
		}
		f.close();

		return sb.toString();
	}

	public String getTextOnlyPlots(char delim) throws IOException {
		if (plot == null)
			return "";
		return plot.toCSV(delim,null);
	}

	public String getCSV(char delim) throws IOException {
		Map<String, Object> nonPlotData = compileSingleMetrics();
		return plot.toCSV(delim, nonPlotData);
	}

	public String getJSON(boolean addROC) {
		// Single valued stuff
		Map<String,Object> mets = compileSingleMetrics();

		// ROC
		if (addROC) {
			for (SingleValuedMetric m : singleMetrics) {
				if (m instanceof ROC_AUC)
					mets.put(OutputNamingSettings.JSON.ROC_PLOT_SECTION,((ROC_AUC)m).rocAsJSON());
			}
		}
		
		mets.put(OutputNamingSettings.JSON.PLOT_SECTION, plot.getCurves());

		// Round everything and convert to pretty-print
		return Jsoner.prettyPrint(JSONFormattingHelper.toJSON(mets).toJson());
	}

	public String getROCAsTSV(char delim) {
		for (SingleValuedMetric m : singleMetrics) {
			if (m instanceof ROC_AUC)
				return ((ROC_AUC) m).rocAsCSV(delim);
		}
		return "";
	}


	private Map<String, Object> compileSingleMetrics(){
		Map<String,Object> metrics = new LinkedHashMap<>();
		for (SingleValuedMetric m : singleMetrics) {
			try {
				metrics.putAll(m.asMap());
			} catch (Exception e) {}
		}

		return MathUtils.roundAllValues(metrics);
	}

	public boolean isConformalResult(){
		for (Metric m : singleMetrics){
			if (m instanceof ObservedFuzziness || m instanceof MeanPredictionIntervalWidth){
				return true;
			}
		}
		return false;
	}

}

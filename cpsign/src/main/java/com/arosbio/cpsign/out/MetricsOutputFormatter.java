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
import com.arosbio.ml.metrics.plots.MergedPlot;
import com.arosbio.ml.metrics.plots.Plot2D;
import com.arosbio.ml.metrics.plots.PlotMetric;
import com.github.cliftonlabs.json_simple.Jsoner;

public class MetricsOutputFormatter {

	private List<SingleValuedMetric> singleMetric = new ArrayList<>();
	private List<PlotMetric> plotMetrics = new ArrayList<>();

	public MetricsOutputFormatter(List<? extends Metric> metrics) {
		for (Metric m : metrics) {
			if (m instanceof SingleValuedMetric)
				singleMetric.add((SingleValuedMetric) m);
			else if (m instanceof PlotMetric)
				plotMetrics.add((PlotMetric) m);
			else
				throw new RuntimeException("Metric " + m.getClass() + " not recognized - it must implement SingleValuedMetric or PlotMetric!");
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

	public String getTextOnlyPlots(char delim) throws IOException {
		if (plotMetrics.isEmpty())
			return "";
		List<Plot2D> plots = new ArrayList<>();
		for (PlotMetric pm : plotMetrics) {
			plots.add(pm.buildPlot());
		}
		MergedPlot plot = new MergedPlot(plots);
		return plot.toCSV(delim,null);
	}

	public String getCSV(char delim) throws IOException {
		Map<String, Object> nonPlotData = compileSingleMetrics();

		List<Plot2D> plots = new ArrayList<>();
		for (PlotMetric pm : plotMetrics) {
			plots.add(pm.buildPlot());
		}
		return new MergedPlot(plots).toCSV(delim, nonPlotData);
	}

	public String getJSON(boolean addROC) {
		// Single valued stuff
		Map<String,Object> mets = compileSingleMetrics();

		// ROC
		if (addROC) {
			for (SingleValuedMetric m : singleMetric) {
				if (m instanceof ROC_AUC)
					mets.put(OutputNamingSettings.JSON.ROC_PLOT_SECTION,((ROC_AUC)m).rocAsJSON());
			}
		}
		
		// Plot
		List<Plot2D> plots = new ArrayList<>();
		for (PlotMetric pm : plotMetrics) {
			plots.add(pm.buildPlot());
		}
		mets.put(OutputNamingSettings.JSON.PLOT_SECTION, new MergedPlot(plots).getCurves());

		// Round everything and convert to pretty-print
		return Jsoner.prettyPrint(JSONFormattingHelper.toJSON(mets).toJson());
	}

	public String getROCAsTSV(char delim) {
		for (SingleValuedMetric m: singleMetric) {
			if (m instanceof ROC_AUC)
				return ((ROC_AUC) m).rocAsCSV(delim);
		}
		return "";
	}


	private Map<String, Object> compileSingleMetrics(){
		Map<String,Object> metrics = new LinkedHashMap<>();
		for (SingleValuedMetric m : singleMetric) {
			try {
				metrics.putAll(m.asMap());
			} catch (Exception e) {}
		}

		return MathUtils.roundAllValues(metrics);
	}

}

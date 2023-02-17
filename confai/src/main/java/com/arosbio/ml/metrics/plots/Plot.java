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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cliftonlabs.json_simple.JsonObject;

public class Plot implements Plot2D {

	private static final Logger LOGGER = LoggerFactory.getLogger(Plot.class);

	private X_Axis xAxisName = X_Axis.CONFIDENCE;
	private String plotName = "plot";
	private int numExamples;

	private Map<String, List<Number>> plot;

	/**
	 * Creates an empty plot
	 */
	public Plot() {
		this.plot = new LinkedHashMap<>();
	}

	public Plot(Map<String, List<Number>> values, X_Axis xLabel) {
		if (xLabel == null)
			throw new IllegalArgumentException("x-label must be given");
		if (values == null)
			throw new IllegalArgumentException("values must be given when creating a plot");
		if (!values.isEmpty()) {
			if (!values.containsKey(xLabel.toString()))
				throw new IllegalArgumentException("xLabel: " + xLabel + " not among values given");
			this.plot = new LinkedHashMap<>(values);
			this.xAxisName = xLabel;
			int listLength = values.get(xLabel.toString()).size();
			for (List<Number> points : values.values()) {
				if (points.size() != listLength)
					throw new IllegalArgumentException("All lists must be of the same length");
			}
		} else {
			// Create an empty plot
			this.plot = new LinkedHashMap<>();
		}

	}

	/**
	 * @param xValues A list of x-values, matching the <code>values</code> parameter
	 * @param values  The curves, with the names specified as keys in the map
	 */
	public Plot(List<Number> xValues, Map<String, List<Number>> values) {
		if (xValues == null || xValues.isEmpty())
			throw new IllegalArgumentException("x-values must be given");
		plot = new LinkedHashMap<>(values);
		for (List<Number> points : values.values())
			if (points.size() != xValues.size())
				throw new IllegalArgumentException("All lists must be of the same length");
		plot.put(xAxisName.toString(), xValues);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.arosbio.ml.metrics.Plot2D#setPlotName(java.lang.String)
	 */
	public void setPlotName(String name) {
		this.plotName = name;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.arosbio.ml.metrics.Plot2D#getPlotName()
	 */
	@Override
	public String getPlotName() {
		return this.plotName;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.arosbio.ml.metrics.Plot2D#getCurve()
	 */
	@Override
	public Map<String, List<Number>> getCurves() {
		return plot;
	}

	protected void setCurves(Map<String, List<Number>> plot){
		this.plot = plot;
	}

	public void setNumExamplesUsed(int examples) {
		this.numExamples = examples;
	}
	
	public int getNumExamples() {
		return numExamples;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.arosbio.ml.metrics.Plot2D#curveAsJSONString()
	 */
	@Override
	public String curvesAsJSONString() {
		return new JsonObject(plot).toJson();
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (!plot.isEmpty()) {
			sb.append(plotName).append('{');
			for (String curveName : plot.keySet()) {
				if (curveName.equals(xAxisName.toString()))
					continue;
				sb.append(curveName).append(',');
			}
			sb.append('}');
		}

		return sb.toString();
	}

	public Map<String, Object> asMap() {
		Map<String, Object> res = new HashMap<>();
		res.putAll(plot);
		return res;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.arosbio.ml.metrics.Plot2D#setXlabel(java.lang.String)
	 */
	@Override
	public void setXlabel(X_Axis label) {
		if (!plot.isEmpty())
			this.plot.put(label.toString(), plot.remove(xAxisName.toString()));
		this.xAxisName = label;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.arosbio.ml.metrics.Plot2D#getXlabel()
	 */
	@Override
	public X_Axis getXlabel() {
		return xAxisName;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.arosbio.ml.metrics.Plot2D#getXvalues()
	 */
	@Override
	public List<Number> getXvalues() {
		return plot.get(xAxisName.toString());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.arosbio.ml.metrics.Plot2D#getYlabels()
	 */
	@Override
	public Set<String> getYlabels() {
		Set<String> labels = new LinkedHashSet<>();
		labels.addAll(plot.keySet());
		labels.remove(xAxisName.toString());
		return labels;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.arosbio.ml.metrics.Plot2D#getPoints(java.lang.String)
	 */
	@Override
	public List<Number> getPoints(String label) {
		return plot.get(label);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.arosbio.ml.metrics.Plot2D#getAsCSV()
	 */
	@Override
	public String getAsCSV() throws Exception {
		return getAsCSV(',');
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.arosbio.ml.metrics.Plot2D#getAsCSV(char)
	 */
	@Override
	public String getAsCSV(char delim) throws Exception {
		StringBuilder sb = new StringBuilder();
		try (
				CSVPrinter printer = new CSVPrinter(sb, CSVFormat.DEFAULT.builder().setDelimiter(delim).setRecordSeparator(System.lineSeparator()).build())) {
			printToCSV(printer);
		} catch (IOException e) {
			LOGGER.debug("Failed printing to CSV stringbuilder", e);
			throw new RuntimeException("Failed writing plot to CSV format");
		}

		return sb.toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.arosbio.ml.metrics.Plot2D#printToCSV(org.apache.commons.csv.
	 * CSVPrinter)
	 */
	@Override
	public void printToCSV(CSVPrinter printer) throws IOException {

		// Header first
		List<String> headers = new ArrayList<>();
		headers.add(xAxisName.label());
		headers.addAll(getYlabels());

		for (String head : headers)
			printer.print(head);
		printer.println();

		// Rest of the rows
		int nCols = plot.get(xAxisName.label()).size();
		for (int i = 0; i < nCols; i++) {
			for (String h : headers)
				printer.print(plot.get(h).get(i));
			printer.println();
		}
		printer.flush();
	}

}

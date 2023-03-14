/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.gridsearch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.MathUtils;
import com.arosbio.commons.Stopwatch;
import com.arosbio.commons.TypeUtils;
import com.arosbio.ml.gridsearch.GridSearch.EvalStatus;
import com.arosbio.ml.gridsearch.GridSearch.GSResult;
import com.arosbio.ml.metrics.SingleValuedMetric;

public class GridResultCSVWriter implements AutoCloseable {

	private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(GridResultCSVWriter.class);

	private static final int NUM_SIGNIFICANT_FIGURES = 5;
	private static final String CSV_RANK_HEADER = "Rank";
	private static final String CSV_STATUS_HEADER = "Status";
	private static final String CSV_RUNTIME_HEADER = "Runtime";
	private static final String CSV_RUNTIME_MS_HEADER = "Runtime (ms)";
	private static final String CSV_SET_CONFIDENCE_HEADER = "Chosen confidence";
	private static final String CSV_ERROR_MSG_HEADER = "Comment";
	private static final char NO_RESULT_INDICATOR = '-';

	// Prior to initialization
	private final Builder settings;

	// Once the setupHeader.. method has been called
	private CSVPrinter printer;
	private List<String> headers;

	// Keeping track of Rank
	private double previousScoure = Double.NaN;
	private int rank = 0;

	public static class Builder {
		private Double conf = null;
		private boolean useRanking = false;
		private CSVFormat.Builder format = CSVFormat.DEFAULT.builder().setRecordSeparator(System.lineSeparator());
		private List<String> params;
		private Appendable output;

		/**
		 * If a confidence is used (a value in [0..1]) or not <code>null</code>
		 * @param conf a value in [0..1] or <code>null</code>
		 * @return the Builder
		 */
		public Builder confidence(Double conf) {
			this.conf = conf;
			return this;
		}
		public Builder skipConfidence() {
			this.conf = null;
			return this;
		}
		public Builder rank(boolean on) {
			this.useRanking = on;
			return this;
		}
		public Builder format(CSVFormat.Builder format) {
			this.format = format;
			return this;
		}
		public Builder format(CSVFormat format){
			this.format = format.builder();
			return this;
		}
		public Builder params(Collection<String> paramNames) {
			if (paramNames==null || paramNames.isEmpty())
				this.params = new ArrayList<>();
			else
				this.params = new ArrayList<>(paramNames);
			return this;
		}
		public Builder log(Appendable out) {
			this.output = out;
			return this;
		}

		public Builder out(Appendable out) {
			this.output = out;
			return this;
		}

		private Builder getCopy() {
			return new Builder().confidence(conf).rank(useRanking).format(format).params(params).log(output);
		}

		public GridResultCSVWriter build(){
			return new GridResultCSVWriter(this);
		}

	}

	private GridResultCSVWriter(Builder builder) {
		this.settings = builder.getCopy();
	}


	/**
	 * Sets up the header and the  
	 */
	private void setupHeaderAndInitPrinter(GSResult res) throws IOException {
		// Set up the header
		headers = new ArrayList<>();
		if (settings.useRanking) {
			// First the Rank
			headers.add(CSV_RANK_HEADER);
		}

		// Optimization metric first
		headers.addAll(res.getOptimizationMetric().asMap().keySet());

		// Secondary metrics

		if (res.getSecondaryMetrics()!=null && !res.getSecondaryMetrics().isEmpty()) {
			for (SingleValuedMetric m: res.getSecondaryMetrics()) {
				headers.addAll(m.asMap().keySet());
			}
		}
		if (settings.conf != null) {
			headers.add(CSV_SET_CONFIDENCE_HEADER);
		}

		headers.addAll(settings.params); // Then the parameters
		headers.add(CSV_RUNTIME_HEADER);
		headers.add(CSV_RUNTIME_MS_HEADER);
		headers.add(CSV_STATUS_HEADER);
		headers.add(CSV_ERROR_MSG_HEADER);

		printer = new CSVPrinter(settings.output, settings.format.setHeader(headers.toArray(new String[0])).build());
	}

	public void printRecord(GSResult res) throws IOException {
		if (printer == null) {
			LOGGER.debug("Setting up GS CSV printer");
			setupHeaderAndInitPrinter(res);
		}
		// Print the current parameters and the metrics
		List<Object> record = new ArrayList<>();

		double roundedScore = MathUtils.roundToNSignificantFigures(res.getResult(), NUM_SIGNIFICANT_FIGURES);
		
		// Build up a Map of results
		Map<String,Object> name2scores = new HashMap<>();
		addMappings(name2scores, res.getOptimizationMetric());
		if (res.getSecondaryMetrics() != null && !res.getSecondaryMetrics().isEmpty()) {
			for (SingleValuedMetric m : res.getSecondaryMetrics()) {
				addMappings(name2scores, m);
			}
		}

		for (String h : headers) {
			if (res.getParams().containsKey(h)) {
				record.add(res.getParams().get(h).toString());
			} else if (h.equals(CSV_SET_CONFIDENCE_HEADER)){
				record.add(settings.conf);
			} else if (h.equals(CSV_STATUS_HEADER)) {
				record.add(res.getStatus().toString());
			} else if (h.equals(CSV_ERROR_MSG_HEADER)){
				String msg = res.getErrorMessage();
				if (msg != null)
					record.add(msg);
				else
					record.add(NO_RESULT_INDICATOR);
			} 
			// Note: the headers are ordered, a failed combo should not contain any more than
			// the above columns, and a '-' for the remaining cols
			else if (res.getStatus() == EvalStatus.FAILED) {
				record.add(NO_RESULT_INDICATOR);
			} else if (h.equals(CSV_RANK_HEADER)) {
				if (res.getStatus() != EvalStatus.VALID || ! Double.isFinite(roundedScore)) {
					record.add(NO_RESULT_INDICATOR);
				} else {
					if (roundedScore != previousScoure) {
						// Update rank
						previousScoure = roundedScore;
						rank++;
					}
					record.add(rank);
				}

			} else if (h.equals(CSV_RUNTIME_HEADER)) {
				record.add(Stopwatch.toNiceString(res.getRuntime()));
			} else if (h.equals(CSV_RUNTIME_MS_HEADER)){
				record.add(res.getRuntime());
			} else {
				// Here - go through the map of results
				boolean foundRec = false;
				for (Map.Entry<String, Object> kv : name2scores.entrySet()) {
					if (h.equals(kv.getKey())) {
						record.add(kv.getValue());
						foundRec = true;
						break;
					}
				}
				if (! foundRec) {
					LOGGER.debug("Could not locate metric for header: {}",h);
					record.add(NO_RESULT_INDICATOR);
				}

			} 
		}

		printer.printRecord(record.toArray());
		printer.flush();
	}

	private static void addMappings(Map<String,Object> target, SingleValuedMetric m) {
		for (Map.Entry<String, ?> keyVal : m.asMap().entrySet()) {
			Object val = keyVal.getValue();
			if (val instanceof Double || val instanceof Float) {
				val = MathUtils.roundToNSignificantFigures(TypeUtils.asDouble(keyVal.getValue()),NUM_SIGNIFICANT_FIGURES);
			}
			target.put(keyVal.getKey(), val);
		}
	}

	@Override
	public void close() throws IOException {
		if (printer != null)
			printer.close(true);

	}
	
}

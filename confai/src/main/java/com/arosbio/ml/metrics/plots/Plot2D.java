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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVPrinter;

public interface Plot2D {

	public enum X_Axis {
		CONFIDENCE("Confidence"), EXPECTED_PROB("Expected probability"), FALSE_POSITIVE_RATE("FPR");

		private final String axisName;

		private X_Axis(String name){
			this.axisName = name;
		}

		public String toString(){
			return axisName;
		}

		public String label(){
			return axisName;
		}
	}

	String getPlotName();

	Map<String, List<Number>> getCurves();

	String curvesAsJSONString();

	void setXlabel(X_Axis label);

	X_Axis getXlabel();

	List<Number> getXvalues();

	/**
	 * All y-labels in the plot, i.e. excluding the x-label 
	 * @return all y-labels
	 */
	Set<String> getYlabels();

	List<Number> getPoints(String label);

	String getAsCSV() throws Exception;

	String getAsCSV(char delim) throws Exception;

	void printToCSV(CSVPrinter printer) throws IOException;

}
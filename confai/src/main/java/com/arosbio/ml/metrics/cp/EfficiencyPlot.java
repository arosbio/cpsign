/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics.cp;

import java.util.List;
import java.util.Map;

import com.arosbio.ml.metrics.plots.Plot;

public class EfficiencyPlot extends Plot {
	
	public static final String DEFAULT_PLOT_NAME = "Efficiency Plot";

	private String efficiencyLabel;
	
	/**
	 * 
	 * @param curve The curves, with mapping curve-name -&gt;list of values
	 * @param xLabel The label for the x-values
	 * @param efficiencyLabel The label for the y-axis
	 */
	public EfficiencyPlot(Map<String,List<Number>> curve, X_Axis xLabel, String efficiencyLabel) {
		super(curve,xLabel);
		this.efficiencyLabel = efficiencyLabel;
		this.setPlotName(DEFAULT_PLOT_NAME);
	}
	
	public String toString() {
		return DEFAULT_PLOT_NAME;
	}
	
	
	public String getEfficiencyLabel() {
		return efficiencyLabel;
	}
	
	public double getEfficiency(double confidence) throws IllegalArgumentException {
		int indx = getXvalues().indexOf(confidence);
		if (indx < 0)
			throw new IllegalArgumentException("Confidence not found in efficiency plot: " + confidence);
		return getCurves().get(efficiencyLabel).get(indx).doubleValue();
	}
	

}

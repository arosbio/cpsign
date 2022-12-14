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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.arosbio.commons.CSVTable;

public class MergedPlot extends Plot {

	private X_Axis xLabel;
	private Set<String> yLabels = new LinkedHashSet<>();

	/**
	 * Merge multiple plots, having the same x-axis and the same x-ticks!
	 * @param plots plots to merge
	 */
	public MergedPlot(List<Plot2D> plots) {
		if (plots==null || plots.isEmpty())
			throw new IllegalArgumentException("Cannot create merged plots of empty input");
		
		// Take the values from the first plot
		xLabel = plots.get(0).getXlabel();
		Set<Double> allTicks = new HashSet<>(plots.get(0).getXvalues().size());

		for (Plot2D p : plots) {
			for (Number tick: p.getXvalues())
				allTicks.add(tick.doubleValue());
			yLabels.addAll(p.getYlabels());
			// Validate remaining x-labels are the same
			if (p.getXlabel() != xLabel)
				throw new IllegalArgumentException("Cannot merge plots of different x-label");
		}
		List<Double> xValues = new ArrayList<>(allTicks);
		Collections.sort(xValues);

		// Build merged plot - init
		Map<String,List<Number>> mergedCurves = new LinkedHashMap<>();
		// Add x-axes first
		mergedCurves.put(xLabel.label(), new ArrayList<>(xValues));
		for (String ylabel : yLabels)
			mergedCurves.put(ylabel, new ArrayList<>());
		
		// Add all data
		for (double tick : xValues) {
			
			for (String ylabel : yLabels) {
				Plot2D p = null;
				for (Plot2D tmp : plots)
					if (tmp.getYlabels().contains(ylabel) && tmp.getXvalues().contains(tick))
						p = tmp;
				
				if (p != null)
					mergedCurves.get(ylabel).add(p.getCurves().get(ylabel).get(p.getXvalues().indexOf(tick)));
				else
					mergedCurves.get(ylabel).add(null);
			}
		}
		
		setXlabel(xLabel);
		setCurves(mergedCurves);
	}
	
	public String toCSV(char delim, Map<String, Object> extraFields) {
		Map<String,List<? extends Object>> theCols = new LinkedHashMap<>(getCurves());
		CSVTable table = new CSVTable(theCols,extraFields);
		 
		return table.toCSV(delim);
		
	}

}
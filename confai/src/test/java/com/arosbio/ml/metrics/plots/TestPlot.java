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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.ml.metrics.plots.Plot2D.X_Axis;
import com.arosbio.tests.suites.UnitTest;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

@Category(UnitTest.class)
public class TestPlot {

	@Test
	public void testPlots() throws Exception {


		Map<String,List<Number>> thePlot = new HashMap<>();


		try {
			new Plot(thePlot, null);
			Assert.fail();
		} catch(IllegalArgumentException e) {}


		thePlot.put(X_Axis.CONFIDENCE.label(), Arrays.asList(0.1, 0.2, 0.3));
		thePlot.put("y-label", Arrays.asList(0.1));

		// Different lengths of the curves
		try {
			new Plot(thePlot, X_Axis.CONFIDENCE);
			Assert.fail();
		} catch(IllegalArgumentException e) {}

		thePlot.put("y-label", Arrays.asList(0.1, 0.2, 0.3));

		// Wrong x-axis label (that's not in the map of curves)
		try {
			new Plot(thePlot, X_Axis.EXPECTED_PROB);
			Assert.fail();
		} catch(IllegalArgumentException e) {}

		Plot p = new Plot(thePlot, X_Axis.CONFIDENCE);

		String json = p.curvesAsJSONString();
		Map<String,Object> map = (JsonObject) Jsoner.deserialize(json);
//		Map<String,Double>map = (Map<String,Double>)new JSONParser().parse(json);
		Assert.assertEquals(2,map.keySet().size());
		
		for (String key: thePlot.keySet())
			Assert.assertTrue(p.asMap().containsKey(key));

		Assert.assertEquals(4, p.getAsCSV().split("\n").length);
		Assert.assertTrue(p.getAsCSV().startsWith(p.getXlabel().label()));
	}

	
	@Test
	public void testPlotsSecondConstructor() throws Exception {


		Map<String,List<Number>> thePlot = new HashMap<>();
		List<Number> xVals = new ArrayList<>();


		try {
			new Plot(xVals, thePlot);
			Assert.fail();
		} catch(IllegalArgumentException e) {}


		thePlot.put("x-label", Arrays.asList(0.1, 0.2, 0.3));
		thePlot.put("y-label", Arrays.asList(0.1));

		xVals.add(0.2);
		xVals.add(0.3);
		xVals.add(0.4);
		try {
			new Plot(xVals, thePlot);
			Assert.fail();
		} catch(IllegalArgumentException e) {}

		thePlot.put("y-label", Arrays.asList(0.1, 0.2, 0.3));

		Plot p = new Plot(xVals, thePlot);

		String json = p.curvesAsJSONString();
		Map<String,Object> map = (JsonObject) Jsoner.deserialize(json);
//		Map<String,Double>map = (Map<String,Double>)new JSONParser().parse(json);
		Assert.assertEquals(map.keySet().size(),3);
		
		Assert.assertTrue(p.asMap().containsKey("x-label"));
		for (String key: thePlot.keySet())
			Assert.assertTrue(p.asMap().containsKey(key));

		Assert.assertEquals(4, p.getAsCSV().split("\n").length);
	}

	@Test
	public void testFillGaps(){
		List<Number> allXs = Arrays.asList(.1,.2,.3,.4,.5,.6);
		List<Number> availableXs = Arrays.asList(.2,.5);
		List<Number> availableVals = Arrays.asList(3,5);

		List<Number> filled = PlotMetricAggregation.fillGaps(allXs, availableXs, availableVals, Double.NaN);
		Assert.assertEquals(Arrays.asList(Double.NaN,3,Double.NaN,Double.NaN,5, Double.NaN), filled);

		// When all values are there
		availableXs = new ArrayList<>(allXs);
		availableVals = Arrays.asList(1,2,3,4,5,6);
		filled = PlotMetricAggregation.fillGaps(allXs, availableXs, availableVals, -10);
		Assert.assertEquals(Arrays.asList(1,2,3,4,5,6), filled);
	}
}

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.testutils;

import java.util.List;

import org.junit.Assert;

import com.arosbio.ml.metrics.cp.CalibrationPlot;

public class ConfaiTestUtils {
	
	
	public static void assertValidModel(CalibrationPlot cp, double tol) {
		List<Number> xvals = cp.getXvalues(); 
		
		List<Number> accs = cp.getCurves().get(cp.getAccuracyLabel());
		
		for (int i=0; i<xvals.size(); i++) {
			Assert.assertTrue("Calibration plot diverged more than  " + tol + ": "+accs.get(i) + " vs " + xvals.get(i), 
					accs.get(i).doubleValue()>= xvals.get(i).doubleValue()-tol);
		}
	}
	
}

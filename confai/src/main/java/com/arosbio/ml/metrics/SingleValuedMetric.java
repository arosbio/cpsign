/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics;

import java.util.Locale;
import java.util.Map;

public interface SingleValuedMetric extends Metric {

	static String toString(SingleValuedMetric m){
		if (m.getNumExamples()>0)
			return String.format(Locale.ENGLISH,"%s: %.3f",m.getName(),m.getScore());
		return Metric.toString(m);
	}
	
	public double getScore();
	public Map<String,? extends Object> asMap();
	public SingleValuedMetric clone();
	
}

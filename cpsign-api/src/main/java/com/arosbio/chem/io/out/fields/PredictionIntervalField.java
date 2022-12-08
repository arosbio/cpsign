/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem.io.out.fields;

import java.awt.font.TextAttribute;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;

import com.arosbio.commons.MathUtils;
import com.google.common.collect.Range;

public class PredictionIntervalField extends TextField {
	
	public PredictionIntervalField( 
			Range<Double> interval, 
			double conf, 
			String modelVariable ) {
		super(getText(interval, conf, modelVariable));
	}
	
	public PredictionIntervalField(Range<Double> interval, double conf) {
		this(interval,conf, null);
	}

	private static List<AttributedString> getText(Range<Double> interval, double conf, String modelVariable){
		List<AttributedString> lines = new ArrayList<>();
		AttributedString firstLine = new AttributedString("Prediction (conf="+conf+"):");
		firstLine.addAttribute(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE,12,16);
		lines.add(firstLine);
		lines.add(new AttributedString((modelVariable!=null? modelVariable+" ":"")+"("+MathUtils.roundTo3significantFigures(interval.lowerEndpoint())+" ; " + MathUtils.roundTo3significantFigures(interval.upperEndpoint())+")"));
		return lines;
	}

}

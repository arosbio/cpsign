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

import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.arosbio.commons.MathUtils;
import com.arosbio.commons.SimpleHTMLTagTokenizer;

public class ProbabilityField extends TextField {
	
	public ProbabilityField(Map<String, Double> probs) {
		super(getStrings(probs));
	}
	
	private static List<AttributedString> getStrings(Map<String, Double> probs){
		List<AttributedString> lines = new ArrayList<>();
		for(Map.Entry<String, Double> pval: probs.entrySet())
			lines.add(new SimpleHTMLTagTokenizer("<i>P</i>("+pval.getKey()+")="+MathUtils.roundTo3significantFigures(pval.getValue())).toAttributedString());
		return lines;
	}

}

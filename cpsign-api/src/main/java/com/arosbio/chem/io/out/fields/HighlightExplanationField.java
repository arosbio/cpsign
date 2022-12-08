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

import java.awt.Color;
import java.awt.font.TextAttribute;
import java.text.AttributedString;

public class HighlightExplanationField extends ColoredBoxField {
	
	private static final String DEFAULT_TEXT = "Largest impact on the prediction";
		
	public HighlightExplanationField(Color highlightColor) {
		super(highlightColor, new AttributedString(DEFAULT_TEXT));
	}
	
	public HighlightExplanationField(Color highlightColor, String label) {
		super(highlightColor, getString(label));
	}
	
	private static AttributedString getString(String label){
		AttributedString str = new AttributedString("Largest impact on p["+label+"]");
		str.addAttribute(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE,18,19);
		return str;
	}

}

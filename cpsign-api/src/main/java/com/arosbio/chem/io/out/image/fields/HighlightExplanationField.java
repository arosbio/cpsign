/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem.io.out.image.fields;

import java.awt.Color;
import java.awt.font.TextAttribute;
import java.text.AttributedString;

public class HighlightExplanationField extends ColoredBoxField {
	
	private static final String DEFAULT_TEXT = "Largest impact on the prediction";

    public static class Builder extends ColoredBoxField.Builder {

        public Builder(Color highlight){
            this(highlight, DEFAULT_TEXT);
        }
        public Builder(Color highlight, String text){
            super(highlight, text);
        }
        @Override
        public Builder getThis(){
            return this;
        }

        public Builder basedOnPValueOfClass(String label){
            text(getPValueText(label));
            return this;
        }

        public Builder basedOnProbabilityOfClass(String label){
            text(getProbabilityText(label));
            return this;
        }

        public HighlightExplanationField build(){
            return new HighlightExplanationField(this);
        }
    }

    private HighlightExplanationField(Builder b){
        super(b);

    }

    private static AttributedString getProbabilityText(String label){
		AttributedString str = new AttributedString("Largest impact on P["+label+"]");
		str.addAttribute(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE,18,19);
		return str;
	}
	
	private static AttributedString getPValueText(String label){
		AttributedString str = new AttributedString("Largest impact on p["+label+"]");
		str.addAttribute(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE,18,19);
		return str;
	}

}

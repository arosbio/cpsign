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

import java.awt.font.TextAttribute;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.arosbio.chem.io.out.image.RendererTemplate.RenderInfo;
import com.arosbio.commons.MathUtils;
import com.google.common.collect.Range;

public class PredictionIntervalField extends TextField.GenericMutable { 

    public static class CIReplacer implements MutableTextReplacer {
        private final static double WIDE_NUMBER = -8.08080808080;
        private final int precision;
        private final double conf;
        private final boolean split;
        private final String property;
        private CIReplacer(int precision, double conf, boolean splitInto2, String property){
            this.conf = conf;
            this.precision = precision;
            this.split = splitInto2;
            this.property = property;
        }

        private String formatRange(Range<Double> ci){
            return new StringBuilder("(")
                .append(MathUtils.roundToNSignificantFigures(ci.lowerEndpoint(), precision))
                .append(" ; ")
                .append(MathUtils.roundToNSignificantFigures(ci.upperEndpoint(), precision)).append(")")
                .toString();

        }

        private List<AttributedString> getLines(Range<Double> ci){
            String firstPart = String.format(Locale.ENGLISH,"Prediction (conf=%.2f)", conf); // this part never changes
            StringBuilder secondPart = new StringBuilder();
            if (property != null)
                secondPart.append(property).append(": ");
            secondPart.append(formatRange(ci));
            // Put it all together
            List<AttributedString> result = new ArrayList<>();
            if (split){
                AttributedString first = new AttributedString(firstPart+":");
                first.addAttribute(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE,12,16);
                result.add(first);
                result.add(new AttributedString(secondPart.toString()));
            } else {
                AttributedString first = new AttributedString(firstPart+", " + secondPart.toString());
                first.addAttribute(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE,12,16);
                result.add(first);
            }
            return result;
        }

        @Override
        public List<AttributedString> getBase() {
            return getLines(Range.closed(WIDE_NUMBER, WIDE_NUMBER));
        }

        @Override
        public List<AttributedString> mutate(RenderInfo info) throws IllegalArgumentException {
            return getLines(info.getConfidenceInterval());
        }

    }

    public static class Builder extends TextField.GenericMutable.Builder<PredictionIntervalField, Builder> {

        private int numberPrecision = 3;
        private double confidenceLevel;
        private boolean splitIntoTwoLines = true;
        private String property;

        @Override
        public Builder getThis() {
            return this;
        }

        public Builder(double confLevel){
            this.confidenceLevel = confLevel;
        }

        public Builder precision(int numDigits){
            this.numberPrecision = numDigits;
            return getThis();
        }

        public Builder splitInto2Lines(boolean use2){
            this.splitIntoTwoLines = use2;
            return getThis();
        }

        public Builder property(String property){
            this.property = property;
            return getThis();
        }

        @Override
        public PredictionIntervalField build() {
            // update the "replacer" text with the configurations we have
            replacer(new CIReplacer(numberPrecision, confidenceLevel, splitIntoTwoLines, property));
            return new PredictionIntervalField(this);
        }

    }

    PredictionIntervalField(Builder b){
        super(b);
    }
	
}

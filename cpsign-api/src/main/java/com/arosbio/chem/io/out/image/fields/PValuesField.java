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

import java.text.AttributedString;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.arosbio.chem.io.out.image.RendererTemplate.RenderInfo;

public class PValuesField extends TextField.GenericMutable {

    private static class PValueReplacer implements MutableTextReplacer {
        private final List<String> labels;
        private final int precision;

        public PValueReplacer(List<String> labels, int precision){
            this.labels = labels;
            this.precision = precision;
        }

        private List<AttributedString> getLines(Map<String,Double> pvals){
            List<AttributedString> lines = new ArrayList<>();
            for (String l : labels){
                AttributedString line = new AttributedString(
                    String.format(Locale.ENGLISH,"p[%s]=%."+precision+"f", l, pvals.get(l)));
                line.addAttribute(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE,0,1);
                lines.add(line);
            }
            return lines;
        }

        @Override
        public List<AttributedString> getBase() {
            Map<String,Double> fake = new HashMap<>();
            for(String l : labels){
                fake.put(l, 0d);
            }
            return getLines(fake);
        }

        @Override
        public List<AttributedString> mutate(RenderInfo info) throws IllegalArgumentException {
            return getLines(info.getPValues());
        }

    }

    public static class Builder extends GenericMutable.Builder<PValuesField,Builder> {
        private int precision = 3;
        private List<String> labels;

        public Builder(Collection<String> labels){
            this.labels = new ArrayList<>(labels);
        }
        @Override
        public Builder getThis() {
            return this;
        }

        @Override
        public PValuesField build() {
            replacer(new PValueReplacer(labels, precision));
            return new PValuesField(this);
        }

    }

    private PValuesField(Builder b){
        super(b);
    }

}

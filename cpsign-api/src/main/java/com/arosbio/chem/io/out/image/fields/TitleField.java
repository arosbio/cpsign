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

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.text.AttributedString;
import java.util.Arrays;
import java.util.List;

import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.chem.io.out.image.RendererTemplate.Context;
import com.arosbio.chem.io.out.image.RendererTemplate.RenderInfo;
import com.arosbio.chem.io.out.image.layout.Position;
import com.arosbio.chem.io.out.image.layout.Position.Vertical;
import com.arosbio.commons.StringUtils;

public class TitleField extends TextField.GenericMutable {
	
    private static final Position.Vertical DEFAULT_TEXT_ALIGNMENT = Vertical.CENTERED;
	private final double titleFontScale;

    private static class TitleText implements MutableTextReplacer {
        private final String exampleTitle;
        private final boolean underline;
        public TitleText(String exTitle, boolean underline){
            this.exampleTitle = exTitle;
            this.underline = underline;
        }
        @Override
        public List<AttributedString> getBase() {
            // make up a bogus title ~20% longer than what was given
            return Arrays.asList(new AttributedString(exampleTitle + StringUtils.replicate('-', (int) (exampleTitle.length()*1.2))));
        }

        @Override
        public List<AttributedString> mutate(RenderInfo info) throws IllegalArgumentException {
            String title = info.getMol().getTitle();
            if (title == null){
                // Fallback to use the SMILES
                title = CPSignMolProperties.getSMILES(info.getMol());
            }
            AttributedString txt = new AttributedString(title);
            if (underline)
                txt.addAttribute(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
                
            return Arrays.asList(txt);
        }

    }

    public static class Builder extends GenericMutable.Builder<TitleField,Builder> {
        private double fontScale = 1.2;
        private String typicalTitle = StringUtils.replicate('-', 20);
        private boolean underlineText = false;

        public Builder(){
            alignment(DEFAULT_TEXT_ALIGNMENT);
        }

        public Builder fontScale(double scale){
            if (scale < 0.2 || scale >5){
                throw new IllegalArgumentException("Title font scale must be reasonable - allowed range [0.2...5]");
            }
            this.fontScale = scale;
            return this;
        }

        public Builder exampleTitle(final String title){
            this.typicalTitle = title;
            return this;
        }

        public Builder underline(boolean underlineTitle){
            this.underlineText = underlineTitle;
            return this;
        }
        
        @Override
        public Builder getThis() {
            return this;
        }

        @Override
        public TitleField build() {
            replacer(new TitleText(typicalTitle, underlineText));
            return new TitleField(this);
        }

    }

	TitleField(Builder b) {
        super(b);
        this.titleFontScale = b.fontScale;
    }
	
    @Override
    public Font getFont(Context context){
        // If an explicit font was set
        if (super.hasCustomFont())
            return super.getFont(context);
        // Calculate a new font
        return context.defaultFont.deriveFont((float) (context.defaultFont.getSize()*titleFontScale));

    }
}

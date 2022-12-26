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

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.arosbio.chem.io.out.image.ImageUtils;
import com.arosbio.chem.io.out.image.RendererTemplate.Context;
import com.arosbio.chem.io.out.image.RendererTemplate.RenderInfo;
import com.arosbio.commons.SimpleHTMLTagTokenizer; 

public abstract class TextField extends FieldWithText {

    TextField(Builder<?, ?> b) {
        super(b);
    }

    // static abstract class Builder<F extends TextField, B extends Builder<F,B>> extends FieldWithText.Builder<F,Builder<F,B>> {}

    /**
     * An immutable text field (i.e. will not change between depictions)
     */
    public static class Immutable extends TextField {

        private final List<AttributedString> text;

        private Context context;

        public static class Builder extends TextField.Builder<TextField, Builder> {

            private List<AttributedString> texts;

            @Override
            public Builder getThis() {
                return this;
            }

            public Builder(AttributedString... texts){
                this.texts = Arrays.asList(texts);
            }

            public Builder(List<AttributedString> texts){
                this.texts = texts;
            }

            public Builder(String... texts){
                this.texts = new ArrayList<>();
                for (String line : texts){
                    this.texts.add(new SimpleHTMLTagTokenizer(line).toAttributedString());
                }
            }

            @Override
            public TextField build() {
                return new Immutable(this);
            }

        }

        Immutable(Builder b){
            super(b);
            this.text = b.texts;
        }

        @Override
        public Dimension2D calculateDim(Context context) {
            this.context = context;
            
            Dimension2D textDim = ImageUtils.calculateRequiredSpace(context.imageFullWidth, getFont(context), text);
            return new Dimension(
                (int)Math.ceil(textDim.getWidth()) + calculateAddedLayoutWidth(),
                (int)Math.ceil(textDim.getHeight()) + calculateAddedLayoutHeight());
        }

        @Override
        public void render(Graphics2D graphics, Rectangle2D area, RenderInfo info) throws IllegalStateException {
            if (context == null){
                throw new IllegalStateException("Field not initialized yet");
            }
            
            // Add any potential layouts
            Rectangle2D currentArea = addLayouts(graphics, area);

            ImageUtils.drawText(graphics, text, currentArea, getAlignment(), getFont(context), getTextColor(context));
        }

    }

    public static abstract class GenericMutable extends TextField {
        
        public static interface MutableTextReplacer {

            public List<AttributedString> getBase();

            public List<AttributedString> mutate(RenderInfo info) throws IllegalArgumentException;

        }

        private final MutableTextReplacer replacer;

        private Context context;
        
        public static abstract class Builder<F extends GenericMutable, B extends Builder<F,B>> extends FieldWithText.Builder<F, B> {

            private MutableTextReplacer replacer;

            public Builder(){}

            public Builder(MutableTextReplacer replacer){
                this.replacer = replacer;
            }

            public B replacer(MutableTextReplacer replacer){
                this.replacer = replacer;
                return getThis();
            }

        }

        GenericMutable(Builder<?,?> b){
            super(b);
            this.replacer = b.replacer;
        }

        @Override
        public Dimension2D calculateDim(Context context) {
            this.context = context;
            
            Dimension2D textDim = ImageUtils.calculateRequiredSpace(context.imageFullWidth, getFont(context), replacer.getBase());
            // System.err.printf("Num lines: %d size: %s from field %s%n",replacer.getBase().size(),textDim, this.getClass().getSimpleName());
            return new Dimension(
                (int)Math.ceil(textDim.getWidth()) + calculateAddedLayoutWidth(),
                (int)Math.ceil(textDim.getHeight()) + calculateAddedLayoutHeight());
        }

        @Override
        public void render(Graphics2D graphics, Rectangle2D area, RenderInfo info) throws IllegalStateException {
            if (context == null){
                throw new IllegalStateException("Field not initialized yet");
            }
            
            // Add any potential layouts
            Rectangle2D currentArea = addLayouts(graphics, area);

            ImageUtils.drawText(graphics, replacer.mutate(info), currentArea, getAlignment(), getFont(context), getTextColor(context));
        }

    }
    


     /**
     * A mutable text field that should be updated based on the {@link RenderInfo} object when called
     */ 
    public static class Mutable extends GenericMutable {

        public static class Builder extends GenericMutable.Builder<Mutable,Builder> {

            @Override
            public Builder getThis() {
                return this;
            }

            @Override
            public Mutable build() {
                return new Mutable(this);
            }

        }

        private Mutable(Builder b){
            super(b);
        }

    }
    

}

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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;

import com.arosbio.chem.io.out.image.RendererTemplate.Context;
import com.arosbio.chem.io.out.image.RendererTemplate.RenderInfo;
import com.arosbio.color.gradient.ColorGradient;

public class ColorGradientField extends AbstractField {

	// 20% bigger font-size on +/- signs compared to all other text
	private static final double PLUS_MINUS_SCALE_FACTOR = 1.2;
	private static final double GRADIENT_HEIGHT_SCALE_FACTOR = 0.5;
	private static final double PADDING_BETWEEN_GRADIENT_AND_TEXT_SCALE_FACTOR = 0.1;

	private final ColorGradient gradient;
	private final boolean displayPlusMinus;
    private final Color plusMinusColor;
	
    private Font plusMinusFont;
    private Integer gradientHeight;
    private Context context;
    private int padding;

    public static class Builder extends AbstractField.Builder<ColorGradientField, Builder> {
        
        private ColorGradient gradient;
        private Color txtColor = Color.BLACK;
        private boolean displayPlusMinus = true;
        private Integer gradientHeight;

        public Builder(ColorGradient scheme){
            this.gradient = scheme;
        }

        @Override
        public Builder getThis() {
            return this;
        }

        public Builder gradientHeight(int height){
            this.gradientHeight = height;
            return this;
        }
    
        public Builder displayPlusMinus(boolean display){
            this.displayPlusMinus = display;
            return this;
        }

        @Override
        public ColorGradientField build() {
            return new ColorGradientField(this);
        }
    
    }


	private ColorGradientField(Builder b) {
        super(b);
		this.gradient = b.gradient;
        this.displayPlusMinus = b.displayPlusMinus;
        this.gradientHeight = b.gradientHeight;
        this.plusMinusColor = b.txtColor;
	}

	
	private Font getPlusMinusFont(Context context){
		Font font=context.defaultFont;
		return font.deriveFont((float)(font.getSize()*PLUS_MINUS_SCALE_FACTOR));
	}

	private void drawGradient(Graphics2D g, Rectangle2D area){
        Color original = g.getColor();
        Stroke originalStroke = g.getStroke();
        g.setStroke(new BasicStroke(1f));
        int w = (int)area.getWidth();
        int startX = (int) area.getMinX();
        int startY = (int) area.getMinY();
        int endY = (int) area.getMaxY();
		for (int i=0; i<w; i++) {
			double val = -1d + 2*((double)i)/w;
			g.setColor(gradient.getColor(val));
            g.drawLine(startX+i, startY, startX+i, endY);
		}
        g.setStroke(originalStroke);
        g.setColor(original); 
	}

    @Override
    public Dimension2D calculateDim(Context context) {
        this.context = context;

        // make sure we have a OK gradient height
        if (gradientHeight == null || gradientHeight<=0){
            gradientHeight = (int) Math.round(context.defaultFont.getSize()*GRADIENT_HEIGHT_SCALE_FACTOR);
        }
        plusMinusFont = getPlusMinusFont(context);
        padding = (int)(context.defaultFont.getSize()*PADDING_BETWEEN_GRADIENT_AND_TEXT_SCALE_FACTOR);

        int legendHeight = (displayPlusMinus? padding+context.defaultFont.getSize() : 0) +
            gradientHeight + calculateAddedLayoutHeight();

        return new Dimension(
            context.imageFullWidth,
            legendHeight);
    }

    @Override
    public void render(Graphics2D graphics, Rectangle2D area, RenderInfo info) throws IllegalStateException {
        if (context == null){
            throw new IllegalStateException("Field not initialized yet");
        }

        Rectangle2D currentArea = area;
        
        // Add any potential layouts
        currentArea = addLayouts(graphics, area);

        // here we write the range - 0 + and show the gradient
		if (displayPlusMinus){
            Color tmp = graphics.getColor();
            // Get the metrics for the default font (which is smaller)
            FontMetrics metrics = graphics.getFontMetrics();
            int textY = metrics.getAscent() + (int) currentArea.getMinY();
			
            graphics.setFont(plusMinusFont);
			graphics.setColor(plusMinusColor);
			
			// -
			String minus = "\u2212";
			graphics.setFont(plusMinusFont); 
			FontMetrics plusMinusMetrics = graphics.getFontMetrics();
			graphics.drawString(minus, (int) currentArea.getMinX() + padding, textY);

			// +
			String plus = "+";
			graphics.drawString(plus, 
					(int)currentArea.getMaxX() - plusMinusMetrics.stringWidth(plus)-padding, textY);

			// 0
			String zero = "0";
			graphics.setFont(context.defaultFont); // Set back the default font
			graphics.drawString(zero, 
					(int) currentArea.getCenterX() - metrics.stringWidth(zero)/2, textY);
            
            graphics.setColor(tmp);

            Rectangle2D gradientArea = new Rectangle2D.Double(
                currentArea.getMinX(), // 
                currentArea.getMaxY()-gradientHeight.doubleValue(),
                currentArea.getWidth(),
                gradientHeight.doubleValue());
            drawGradient(graphics, gradientArea);
		} else {
            // Fill the 
            drawGradient(graphics, currentArea);
        }

        
    }

}

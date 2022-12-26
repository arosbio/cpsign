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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.BasicStroke;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.text.AttributedString;
import java.util.Arrays;

import com.arosbio.chem.io.out.image.FontUtils;
import com.arosbio.chem.io.out.image.ImageUtils;
import com.arosbio.chem.io.out.image.RendererTemplate.Context;
import com.arosbio.chem.io.out.image.RendererTemplate.RenderInfo;
import com.arosbio.commons.SimpleHTMLTagTokenizer;

public class ColoredBoxField extends FieldWithText {
	
	public enum BoxLocation {
		LEFT, RIGHT;
	}
	
	public enum BoxShape {
		SQUARE, CIRCLE, TRIANGLE;
	}

	private final Color boxColor;
	private final BoxLocation location;
	private final BoxShape shape;
	private final AttributedString text;
	private final double boxToTextPaddingScaleFactor;
    
    // Settings that should have been configured once the calculateBounds have been called
    private Integer boxSize;
    private int textHeight;
    /** padding between the box and the text, in pixels */
    private int boxPadding;
    private Context context;

    public static class Builder extends FieldWithText.Builder<ColoredBoxField, Builder> {
        private Color boxColor;
	    private BoxLocation location = BoxLocation.LEFT;
	    private BoxShape shape = BoxShape.SQUARE;
	    private AttributedString text;
	    private double boxToTextPaddingScaleFactor = 0.2;
        private Integer boxSize;

        @Override
        public Builder getThis() {
            return this;
        }

        public Builder(Color boxColor, AttributedString text) {
            this.boxColor = boxColor;
            this.text = text;
        }

        public Builder(Color boxColor, String text) {
            this(boxColor, new SimpleHTMLTagTokenizer(text).toAttributedString());
        }

        public Builder boxLocation(BoxLocation location){
            this.location = location;
            return getThis();
        }
        
        public Builder boxShape(BoxShape shape){
            this.shape = shape;
            return getThis();
        }
        
        public Builder boxSize(int size){
            this.boxSize = size;
            return getThis();
        }

        public Builder paddingBetweenBoxAndText(double padding){
            this.boxToTextPaddingScaleFactor = padding;
            return getThis();
        }

        public Builder text(String text){
            this.text = new SimpleHTMLTagTokenizer(text).toAttributedString();
            return getThis();
        }

        public Builder text(AttributedString text){
            this.text = text;
            return getThis();
        }

        public ColoredBoxField build(){
            return new ColoredBoxField(this);
        }
        
    }

    ColoredBoxField(Builder b){
        super(b);
        this.boxColor = b.boxColor;
	    this.location = b.location;
        this.shape = b.shape;
        this.text = b.text;
        this.boxToTextPaddingScaleFactor = b.boxToTextPaddingScaleFactor;
        this.boxSize = b.boxSize;
    }



    @Override
    public Dimension2D calculateDim(Context context) {
        this.context = context;
        Font font = getFont(context);
        // Calculate the box-size (if not already explicitly set)
        boxSize = boxSize!=null ? boxSize : FontUtils.getHeight(font);
        // padding between box and text
        boxPadding = Math.min(5, (int)(boxSize*boxToTextPaddingScaleFactor));

        int nonTextWidth = calculateAddedLayoutWidth() + boxSize + boxPadding;

        Dimension2D textBounds = ImageUtils.calculateRequiredSpace(context.imageFullWidth - nonTextWidth, font, text);
        textHeight = (int) Math.round(textBounds.getHeight());
        // Add the box-space to the bounds
        Dimension d = new Dimension(
            calculateAddedLayoutWidth() + (int)Math.ceil(textBounds.getWidth()) + nonTextWidth, 
            calculateAddedLayoutHeight() + Math.max((int)textBounds.getHeight(), boxSize+2*boxPadding));
        return d;
    }



    @Override
    public void render(Graphics2D graphics, Rectangle2D area, RenderInfo info) throws IllegalStateException {
        if (context == null){
            throw new IllegalStateException("Field not initialized yet");
        }
        Color txtColor = getTextColor(context);
        Rectangle2D currentArea = area;
        
        // Add any potential layouts
        currentArea = addLayouts(graphics, area);
        

        // ======= add the box
        int boxStartX = location == BoxLocation.RIGHT ? (int) currentArea.getMaxX() - boxPadding - boxSize : (int)currentArea.getMinX() + boxPadding;
        int boxStartY = (int) (Math.round(currentArea.getCenterY() - boxSize/2d));
        Color tmpColor = graphics.getColor();
        graphics.setColor(boxColor);
        graphics.setStroke(new BasicStroke(1f));
        if (shape == BoxShape.CIRCLE){
            graphics.fillOval(boxStartX, boxStartY, boxSize-1, boxSize-1);
            graphics.setColor(txtColor);
            graphics.drawOval(boxStartX, boxStartY, boxSize-1, boxSize-1);
        } else if (shape == BoxShape.TRIANGLE){
            Polygon triangle = new Polygon(
                new int[]{boxStartX, boxStartX+(int)Math.round((boxSize-1)/2), boxStartX+boxSize-1}, 
                new int[]{boxStartY+boxSize-1, boxStartY, boxStartY+boxSize-1}, 3);
            graphics.fillPolygon(triangle);
            graphics.setColor(txtColor);
            graphics.drawPolygon(triangle); 
        } else {
            // default is the SQUARE
            graphics.fillRect(boxStartX, boxStartY, boxSize-1, boxSize-1);
            graphics.setColor(txtColor);
            graphics.drawRect(boxStartX, boxStartY, boxSize-1, boxSize-1);
        }
        // Reset the previous color
        graphics.setColor(tmpColor);
        // ======= Finished adding the box

        // ------- Add the text
        int startXPos = (int) (location == BoxLocation.LEFT ? 
            currentArea.getMinX() + boxPadding*2 + boxSize : 
            currentArea.getMinX());
        int w = (int) currentArea.getWidth() - (boxPadding*2 + boxSize);
       
        // find the text area 
        Rectangle2D textArea = null;
        if (textHeight < boxPadding*2 + boxSize){
            // here need to center the text
            textArea = new Rectangle(
                startXPos, 
                (int) (currentArea.getMinY()+.5*(currentArea.getHeight()-textHeight)), // (currentArea.get(int) Math.ceil(currentArea.getMinY()), 
                w, 
                textHeight);
        } else {
            // text is larger than the box, use the full height
            textArea = new Rectangle(
                startXPos, 
                (int) Math.ceil(currentArea.getMinY()), 
                w, 
                (int) currentArea.getHeight());
        }
        ImageUtils.drawText(graphics, Arrays.asList(text), textArea, getAlignment(), getFont(context));
        
        // ------- Finished adding the text 
       
    }

}
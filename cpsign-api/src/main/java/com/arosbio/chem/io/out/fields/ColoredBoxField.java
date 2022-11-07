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
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.text.AttributedString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.io.out.image.FontUtils;
import com.arosbio.chem.io.out.image.ImageUtils;
import com.arosbio.chem.io.out.image.Layout;
import com.arosbio.commons.SimpleHTMLTagTokenizer;
import com.arosbio.io.IOSettings;

public class ColoredBoxField extends OrnamentField {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ColoredBoxField.class);
	
	public enum BoxLocation{
		LEFT, RIGHT;
	}
	
	public enum BoxShape{
		SQUARE, CIRCLE, TRIANGLE;
	}

	private Color boxColor;
	private BoxLocation location = BoxLocation.LEFT;
	private BoxShape shape = BoxShape.SQUARE;
	private AttributedString text;
	private Integer boxSize;
	private final double boxToTextPaddingScaleFactor = 0.2;
	
	public ColoredBoxField(Color boxColor, String text) {
		this(boxColor, new SimpleHTMLTagTokenizer(text).toAttributedString());
	}

	public ColoredBoxField(Color boxColor, AttributedString text) {
		this.boxColor = boxColor;
		this.text = text;
	}
	
	public void setBoxLocation(BoxLocation location){
		this.location = location;
	}
	
	public void setBoxShape(BoxShape shape){
		this.shape = shape;
	}
	
	public void setBoxSize(int size){
		this.boxSize = size;
	}
	
	
	@Override
	public BufferedImage depictInternal(final int width) {
		
		int maxWidth = width;
		if (!getLayouts().isEmpty()){
			for (Layout frame: getLayouts())
				maxWidth -= frame.getAdditionalWidth();
		}
		LOGGER.debug("total width={}, with layouts maxWidth={}",width,maxWidth);
		// Calculate the box-size
		int size = 0;
		if (boxSize!=null)
			size = boxSize;
		else
			size = FontUtils.getHeight(getFont());
		
		// padding between box and text
		int padding = Math.min(10, (int)(size*boxToTextPaddingScaleFactor));
		
		LOGGER.debug("Place to write text (removed space for box + padding)={}",(maxWidth-padding-size));
		// The text
		BufferedImage textImg = null;
		if (text !=null) {
			textImg = ImageUtils.drawString(text, maxWidth-padding-size, getFont(), getTextColor());
		}
		LOGGER.debug("Text-img.width={}", textImg.getWidth());

		// Final img
		BufferedImage img = new BufferedImage(textImg.getWidth()+padding+size, 
				Math.max(size, (textImg!=null? textImg.getHeight():0)), 
				IOSettings.BUFFERED_IMAGE_TYPE);
		Graphics2D graphics = img.createGraphics();
		
		// Add the colored box
		int boxStartX = (location == BoxLocation.RIGHT? img.getWidth()-size: 0);
		int boxStartY = (int) (Math.round(img.getHeight()/2d - size/2d));
		
		graphics.setColor(boxColor);
		if (shape == BoxShape.CIRCLE){
			graphics.fillOval(boxStartX, boxStartY, size-1, size-1);
			graphics.setColor(getTextColor());
			graphics.drawOval(boxStartX, boxStartY, size-1, size-1);
		} else if (shape == BoxShape.TRIANGLE){
			Polygon triangle = new Polygon(
					new int[]{boxStartX, boxStartX+(int)Math.round((size-1)/2), boxStartX+size-1}, 
					new int[]{boxStartY+size-1, boxStartY, boxStartY+size-1}, 3);
			graphics.fillPolygon(triangle);
			graphics.setColor(getTextColor());
			graphics.drawPolygon(triangle);
		} else {
			// default is the SQUARE
			graphics.fillRect(boxStartX, boxStartY, size-1, size-1);
			graphics.setColor(getTextColor());
			graphics.drawRect(boxStartX, boxStartY, size-1, size-1);
		}
		
		// Add the text-img
		int textStartY = (int) (Math.round(img.getHeight()/2d - textImg.getHeight()/2d));
		if (textImg != null && location == BoxLocation.LEFT)
			graphics.drawImage(textImg, padding+size, textStartY, null);
		else if (textImg != null && location == BoxLocation.RIGHT)
			graphics.drawImage(textImg, 0, textStartY, null);
		
		graphics.dispose();

		return img;
	}

}
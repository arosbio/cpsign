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
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import com.arosbio.chem.io.out.depictors.MoleculeGradientDepictor;
import com.arosbio.chem.io.out.image.FontUtils;
import com.arosbio.chem.io.out.image.ImageUtils;
import com.arosbio.chem.io.out.image.Position.Vertical;
import com.arosbio.color.gradient.ColorGradient;
import com.arosbio.io.IOSettings;

public class ColorGradientField extends OrnamentField {

	// 20% bigger font-size on +/- signs compared to all other text
	private static final double PLUS_MINUS_SCALE_FACTOR = 1.2;
	private static final double GRADIENT_HEIGHT_SCALE_FACTOR = 0.5;
	private static final double PADDING_BETWEEN_GRADIENT_AND_TEXT_SCALE_FACTOR = 0.1;

	private ColorGradient gradient;
	private Integer gradientHeight;
	private boolean displayPlusMinus=true;
	
	public ColorGradientField(MoleculeGradientDepictor depictor){
		this(depictor.getColorGradient());
	}

	public ColorGradientField(ColorGradient gradient) {
		this.gradient = gradient;
		super.setAlignment(Vertical.CENTERED);
	}

	public void setGradientHeight(int height){
		this.gradientHeight = height;
	}

	public void setDisplayPlusMinus(boolean display){
		this.displayPlusMinus = display;
	}

	public BufferedImage depictInternal(int width) {
		
		int legendFontSizeTrueSizePix = FontUtils.getHeight(getFont());
		int padd = (int)(legendFontSizeTrueSizePix*PADDING_BETWEEN_GRADIENT_AND_TEXT_SCALE_FACTOR);
		if (gradientHeight == null || gradientHeight<=0)
			gradientHeight = (int) Math.round(legendFontSizeTrueSizePix*GRADIENT_HEIGHT_SCALE_FACTOR);
		int legendHeight = (displayPlusMinus?legendFontSizeTrueSizePix+padd:0) + gradientHeight;

		BufferedImage gradientLegend = new BufferedImage(width, 
				legendHeight, IOSettings.BUFFERED_IMAGE_TYPE);
		Graphics2D graphics = ImageUtils.getGraphicsForText(gradientLegend);
		

		// here we write the range - 0 + and show the gradient
		if(displayPlusMinus){
			graphics.setFont(getFont());
			graphics.setColor(getTextColor());
			FontMetrics metrics = graphics.getFontMetrics();
			int textY = metrics.getAscent();
			// -
			String startText = "\u2212";
			graphics.setFont(getPlusMinusFont()); 
			FontMetrics plusMinusMetrics = graphics.getFontMetrics();
			graphics.drawString(startText, padd, textY);

			// +
			String endText = "+";
			graphics.drawString(endText, 
					gradientLegend.getWidth() - plusMinusMetrics.stringWidth(endText)-padd, textY);

			// 0
			String middText = "0";
			graphics.setFont(getFont());
			graphics.drawString(middText, 
					gradientLegend.getWidth()/2 - metrics.stringWidth(middText)/2, textY);
		}

		// Add gradient
		BufferedImage gradient = drawGradient(gradientLegend.getWidth());
		graphics.drawImage(gradient, 
				0, 
				gradientLegend.getHeight()-gradientHeight, 
				gradient.getWidth(), 
				gradientHeight, 
				null); // add the gradient to the result label

		graphics.dispose();

		return gradientLegend;

	}
	
	private Font getPlusMinusFont(){
		Font font=getFont();
		return font.deriveFont((float)(font.getSize()*PLUS_MINUS_SCALE_FACTOR));
	}

	private BufferedImage drawGradient(int width){
		BufferedImage image = new BufferedImage(width, 1, IOSettings.BUFFERED_IMAGE_TYPE);
		for(int i=0;i<width;i++) {
			double val = -1d + 2*((double)i)/width;
			Color color = Color.MAGENTA;
			if (gradient!=null) 
				color = gradient.getColor(val);
			image.setRGB(i, 0, color.getRGB());
		}
		return image;
	}

}

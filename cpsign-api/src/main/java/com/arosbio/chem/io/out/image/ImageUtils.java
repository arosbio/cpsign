/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem.io.out.image;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.AttributedCharacterIterator;
import java.text.AttributedCharacterIterator.Attribute;
import java.text.AttributedString;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.io.out.image.Position.Vertical;
import com.arosbio.commons.SimpleHTMLTagTokenizer;
import com.arosbio.commons.SimpleHTMLTagTokenizer.FontType;
import com.arosbio.commons.SimpleHTMLTagTokenizer.TextSection;
import com.arosbio.io.IOSettings;

public class ImageUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(ImageUtils.class);

	private static final Base64.Encoder ENCODER = Base64.getEncoder();

	public static final Map<RenderingHints.Key,Object> DEFAULT_RENDERING_HINTS = new HashMap<>();
	static{
		DEFAULT_RENDERING_HINTS.put(
				RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		DEFAULT_RENDERING_HINTS.put(
				RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		DEFAULT_RENDERING_HINTS.put(
				RenderingHints.KEY_RENDERING, 
				RenderingHints.VALUE_RENDER_QUALITY);
		DEFAULT_RENDERING_HINTS.put(
				RenderingHints.KEY_INTERPOLATION, 
				RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		DEFAULT_RENDERING_HINTS.put(
				RenderingHints.KEY_ALPHA_INTERPOLATION, 
				RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
		DEFAULT_RENDERING_HINTS.put(
				RenderingHints.KEY_FRACTIONALMETRICS, 
				RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		System.setProperty("awt.useSystemAAFontSettings", "lcd");
		System.setProperty("swing.aatext", "true");
	}

	public static String convertToBase64(BufferedImage img) throws IllegalArgumentException {
		if (img==null)
			throw new IllegalArgumentException("Image was null");
		
		try (ByteArrayOutputStream os = new ByteArrayOutputStream();){
			ImageIO.write(img, "png", os);
			return ENCODER.encodeToString(os.toByteArray());
		} catch(IOException e){
			throw new IllegalArgumentException("Could not convert BufferedImage to Base64 String");
		}
	}

	public static BufferedImage drawString(
			final AttributedString text, 
			int maxWidth, 
			Font font,
			Color textColor) {
		return drawString(Arrays.asList(text), maxWidth, Position.Vertical.LEFT_ADJUSTED, font, textColor);
	}

	public static BufferedImage drawString(
			final List<AttributedString> textLines, 
			int maxWidth, 
			Font font,
			Color textColor) {
		return drawString(textLines, maxWidth, Position.Vertical.LEFT_ADJUSTED, font, textColor);
	}

	public static BufferedImage drawString(
			final AttributedString text, 
			int maxWidth, 
			Position.Vertical alignment,
			Font font,
			Color textColor){
		return drawString(Arrays.asList(text), maxWidth, alignment, font, textColor);
	}

	public static BufferedImage drawString(
			final List<AttributedString> textLines, 
			int maxWidth,
			Position.Vertical alignment,
			Font font,
			Color textColor){

		LOGGER.debug("maxWidth in drawString={}, using font={}", maxWidth, font);

		BufferedImage imgTmp_ = new BufferedImage(maxWidth,Math.min(maxWidth, 1000), IOSettings.BUFFERED_IMAGE_TYPE);
		Graphics2D g2d = getGraphicsForText(imgTmp_);
		g2d.setColor(textColor);
		g2d.setFont(font);
		float y = 0;
		float actualMaxWidth= -1;

		for (AttributedString text: textLines){
			AttributedCharacterIterator characterIterator = addDefaultFont(text, font).getIterator();

			FontRenderContext fontRenderContext = g2d.getFontRenderContext();
			LineBreakMeasurer measurer = new LineBreakMeasurer(
					characterIterator,
					fontRenderContext);

			while (measurer.getPosition() < characterIterator.getEndIndex()) {
				TextLayout textLayout = measurer.nextLayout(maxWidth*0.98f);
				y += textLayout.getAscent();
				textLayout.draw(g2d, getStartPosition(alignment, maxWidth, textLayout.getAdvance()), y);
				y += textLayout.getDescent() + textLayout.getLeading();
				actualMaxWidth = (float) Math.max(actualMaxWidth, textLayout.getAdvance());
				LOGGER.debug("text-advance={}",textLayout.getAdvance());
			}
		}
		g2d.dispose();

		//make box as tight as possible!
		int finalWidth = Math.round(actualMaxWidth);
		LOGGER.debug("FinalWidth={}",finalWidth);
		BufferedImage res = new BufferedImage(
				finalWidth, 
				(int)Math.ceil(y), IOSettings.BUFFERED_IMAGE_TYPE);
		Graphics2D graphics2d = getGraphicsForText(res);
		int xStart = -1;
		if (alignment==Vertical.LEFT_ADJUSTED)
			xStart = 0;
		else if (alignment == Vertical.RIGHT_ADJUSTED)
			xStart = finalWidth-maxWidth;
		else // centered
			xStart = (int) Math.round(((double)finalWidth-maxWidth)/2);

		graphics2d.drawImage(imgTmp_, xStart, 0, null);
		graphics2d.dispose();

		return res;
	}

	private static float getStartPosition(Position.Vertical alignment, int totalWidth, double textWidth){
		float x = 0f; // default is LEFT_ADJUSTED 
		switch (alignment) {
		case CENTERED:
			x = Math.round(totalWidth/2d - textWidth/2d);
			break;
		case RIGHT_ADJUSTED:
			x= (float) (totalWidth - textWidth);
			break;
		case LEFT_ADJUSTED:
			x = 0;
		default:
			x = 0; // Default is LEFT_ADJUSTED
		}
		return x;
	}

	@SuppressWarnings("null")
	public static BufferedImage drawStringToBufferedImage(
			final String formattedText, 
			final Font font, 
			Color textColor, 
			final int imageType,
			final int imgMaxWidth) 
					throws IllegalArgumentException{
		SimpleHTMLTagTokenizer tokenizer = null;
		try {
			tokenizer = new SimpleHTMLTagTokenizer(formattedText);
		} catch(Exception e){
			throw new IllegalArgumentException("Text not formatted properly: " + e.getMessage());
		}
		List<TextSection> sections = tokenizer.getSections();

		// Get font-metrics and find how big BufferedImage we need to create
		BufferedImage tmp_ = new BufferedImage(1, 1, imageType);
		Graphics2D graphicsTmp_ = tmp_.createGraphics();
		int width=0, height=0;
		FontMetrics fm = null;
		for (TextSection section : sections){
			fm = graphicsTmp_.getFontMetrics(getFont(font, section.getFontTypes()));
			width+=fm.stringWidth(section.getText());
			height = Math.max(fm.getHeight(), height);
		}
		// Add an extra space in the end in case last char is a italic one (can be chopped of otherwise)
		if (sections.get(sections.size()-1).getFontTypes().contains(FontType.ITALIC))
			width += fm.stringWidth(" "); 

		graphicsTmp_.dispose();

		// Create real img
		BufferedImage img = new BufferedImage(width, height, imageType);
		Graphics2D graphics = getGraphicsForText(img);
		if(textColor == null)
			textColor = Color.BLACK;
		graphics.setColor(textColor);
		int xCoord=0;
		final int yCoord = fm.getAscent();
		for (TextSection section: sections){
			graphics.setFont(getFont(font, section.getFontTypes()));
			graphics.drawString(section.getText(), xCoord, yCoord);
			xCoord += fm.stringWidth(section.getText());
		}
		graphics.dispose();

		return img;
	}

	private static Font getFont(Font originalFont, Set<FontType> types){
		if(types == null || types.isEmpty())
			return originalFont;
		Font resultFont = originalFont;
		int style = 0;
		for (FontType type: types)
			style += type.getStyle();
		return resultFont.deriveFont(style);
	}

	public static Graphics2D getGraphicsForText(BufferedImage img){
		Graphics2D graphics = img.createGraphics();

		boolean setRenderingHints = false;
		// try to use optimal rendering hints
		try{
			Map<?, ?> desktopHints = 
					(Map<?, ?>) Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
			graphics.setRenderingHints(desktopHints);
			setRenderingHints = true;
		} catch(Exception | Error e){
			LOGGER.debug("Failed setting rendering hints from awt.font.desktophints");
		}

		if (!setRenderingHints){
			// else use the default ones
			if(DEFAULT_RENDERING_HINTS == null || DEFAULT_RENDERING_HINTS.isEmpty())
				LOGGER.error("Rendering hints was null or empty: {}", DEFAULT_RENDERING_HINTS);
			try{
				graphics.setRenderingHints(DEFAULT_RENDERING_HINTS);
				setRenderingHints=true;
			} catch(Exception | Error e){
				LOGGER.debug("Failed setting rendering hints from DEFAULT_RENDERING_HINTS");
			}
		}

		return graphics;
	}

	public static AttributedString addDefaultFont(AttributedString str, Font defaultFont){
		AttributedString resulting = new AttributedString(str.getIterator());
		AttributedCharacterIterator iterator = str.getIterator();
		while(iterator.getIndex() < iterator.getEndIndex()){
			Map<Attribute, Object> attr = iterator.getAttributes();
			if (!attr.containsKey(TextAttribute.FONT)){
				Font charFont = defaultFont;
				boolean italic = attr.containsKey(TextAttribute.POSTURE) && (float)attr.get(TextAttribute.POSTURE)>=TextAttribute.POSTURE_OBLIQUE;
				boolean bold = attr.containsKey(TextAttribute.WEIGHT) && (float)attr.get(TextAttribute.WEIGHT)>=TextAttribute.WEIGHT_BOLD;
				if (italic && bold){
					charFont = charFont.deriveFont(Font.ITALIC + Font.BOLD);
				} else if (italic){
					charFont = charFont.deriveFont(Font.ITALIC);
				} else if (bold){
					charFont = charFont.deriveFont(Font.BOLD);
				}
				resulting.addAttribute(TextAttribute.FONT, charFont, iterator.getIndex(), iterator.getIndex()+1);
			}
			iterator.next();
		}

		return resulting;
	}


}
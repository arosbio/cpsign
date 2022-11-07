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
import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FontFactory {
	private static final Logger LOGGER = LoggerFactory.getLogger(FontFactory.class);
	private static final int DEFAULT_FONT_SIZE = 13;
	
	public static Font plain(){
		return plain(DEFAULT_FONT_SIZE);
	}
	
	public static Font plain(int size){
		Font regular = tryLoadFont("resources/fonts/texgyreheros-regular.otf",size);
		if (regular == null) // fall back if not found
			regular = new Font(Font.SANS_SERIF, Font.PLAIN, size);
		return regular;
	}
	
	public static Font italic(){
		return italic(DEFAULT_FONT_SIZE);
	}
	
	public static Font italic(int size){
		Font italics = tryLoadFont("resources/fonts/texgyreheros-italic.otf",size);
		if (italics == null)
			italics = new Font(Font.SANS_SERIF, Font.ITALIC, size);
		
		return italics;
	}
	
	public static Font bold(){
		return bold(DEFAULT_FONT_SIZE);
	}
	
	public static Font bold(int size){
		Font bold = tryLoadFont("resources/fonts/texgyreheros-bold.otf",size);
		if (bold == null)
			bold = new Font(Font.SANS_SERIF, Font.BOLD, size);
		return bold;
	}
	
	public static Font boldItalic(){
		return boldItalic(DEFAULT_FONT_SIZE);
	}
	
	public static Font boldItalic(int size){
		Font boldItalic = tryLoadFont("resources/fonts/texgyreheros-bolditalic.otf",size);
		if (boldItalic == null)
			boldItalic = new Font(Font.SANS_SERIF, Font.BOLD+Font.ITALIC, size);
		return boldItalic;
	}
	
//	private static Font tryLoadFont(String otf){
//		return tryLoadFont(otf, DEFAULT_FONT_SIZE);
//	}
	
	private static Font tryLoadFont(String otf, int size){
		try(InputStream fontStream = FontFactory.class.getClassLoader().getResourceAsStream(otf)){
			return Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont((float)size);
		} catch (IOException | FontFormatException e) {
			LOGGER.debug("could not load font from "+otf,e);
		}
		return null;
	}

}

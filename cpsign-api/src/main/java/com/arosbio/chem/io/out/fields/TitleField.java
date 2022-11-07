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

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.text.AttributedString;

import com.arosbio.chem.io.out.image.Position;
import com.arosbio.chem.io.out.image.Position.Vertical;

public class TitleField extends TextField {
	
	private static final Position.Vertical DEFAULT_TEXT_ALIGNMENT = Vertical.CENTERED;
	private double TITLE_FONT_SIZE_SCALE_FACTOR = 1.2;
	
	
	public TitleField(AttributedString text) {
		super(text);
		super.setAlignment(DEFAULT_TEXT_ALIGNMENT);
	}
	
	public TitleField(String text){
		super(text);
		super.setAlignment(DEFAULT_TEXT_ALIGNMENT);
	}
	
	public void underlineText(){
		for(AttributedString tex : getTexts())
			tex.addAttribute(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
	}
	
	@Override
	public void setFont(Font font) {
		super.setFont(font.deriveFont((float) (font.getSize()*TITLE_FONT_SIZE_SCALE_FACTOR)));
	}
	
}

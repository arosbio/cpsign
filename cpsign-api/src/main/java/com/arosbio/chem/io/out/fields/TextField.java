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

import java.awt.image.BufferedImage;
import java.text.AttributedString;
import java.util.Arrays;
import java.util.List;

import com.arosbio.chem.io.out.image.ImageUtils;
import com.arosbio.chem.io.out.image.Position.Vertical;
import com.arosbio.commons.SimpleHTMLTagTokenizer;

public class TextField extends OrnamentField {

	private List<AttributedString> texts;
	
	public TextField(List<AttributedString> texts){
		this.texts = texts;
		setAlignment(Vertical.LEFT_ADJUSTED); // default is left-adjusted
	}
	
	public TextField(AttributedString text) {
		this(Arrays.asList(text));
	}

	public TextField(String text) {
		this(new SimpleHTMLTagTokenizer(text).toAttributedString());
	}
	
	List<AttributedString> getTexts(){
		return texts;
	}


	@Override
	public BufferedImage depictInternal(int width) {
		return ImageUtils.drawString(texts, width, getAlignment(), getFont(), getTextColor());
	}

}

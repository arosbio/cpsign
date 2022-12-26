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
import java.awt.Font;

import com.arosbio.chem.io.out.image.RendererTemplate; 

public abstract class FieldWithText extends AbstractField {

	private final Font customFont;
	private final Color customTextColor;

	FieldWithText(Builder<?,?> b){
		super(b);
		this.customFont = b.customFont;
		this.customTextColor = b.customTextColor;
	}

	public boolean hasCustomFont(){
		return customFont != null;
	}
	
	public Font getFont(RendererTemplate.Context context){
		return customFont != null ? customFont : context.defaultFont;
	}

	public Color getTextColor(RendererTemplate.Context context){
		return customTextColor != null ? customTextColor : context.defaultTextColor;
	}

	public static abstract class Builder<F extends FieldWithText, B extends Builder<F,B>> extends AbstractField.Builder<F, B> {
		private Font customFont;
		private Color customTextColor;

		// public abstract B getThis();

		public B font(Font f){
			this.customFont = f;
			return getThis();
		}

		public B textColor(Color c){
			this.customTextColor = c;
			return getThis();
		}

	}



}

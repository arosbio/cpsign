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
import java.util.ArrayList;
import java.util.List;

import com.arosbio.chem.io.out.image.FontFactory;
import com.arosbio.chem.io.out.image.Layout;
import com.arosbio.chem.io.out.image.Position.Vertical;

public abstract class AbstractField implements FigureField {
	
	private static final Font DEFAULT_FONT = FontFactory.plain();
	private static final Color DEFAULT_TEXT_COLOR = Color.BLACK;
	
	private Vertical alignment = Vertical.LEFT_ADJUSTED; // default is left-adjusted
	private Font font;
	private Color textColor;
	private List<Layout> layouts = new ArrayList<>();
	
	public void addLayout(Layout ornament){
		this.layouts.add(ornament);
	}
	
	public void setLayouts(List<Layout> layouts){
		if (layouts== null)
			layouts = new ArrayList<>();
		else
			this.layouts = layouts;
	}
	
	public List<Layout> getLayouts(){
		return layouts;
	}
	
	public Vertical getAlignment(){
		return alignment;
	}
	
	public void setAlignment(Vertical alignment){
		this.alignment = alignment;
	}
	
	public void setFont(Font font){
		this.font = font;
	}
	
	public Font getFont(){
		return (font!=null?font:DEFAULT_FONT);
	}
	
	public boolean isFontSet(){
		return font != null;
	}
	
	public Color getTextColor(){
		return (textColor!=null? textColor:DEFAULT_TEXT_COLOR);
	}
	
	public void setTextColor(Color color){
		this.textColor = color;
	}
	
	public boolean isTextColorSet(){
		return textColor != null;
	}
	

}
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
import java.util.List;

import com.arosbio.chem.io.out.image.Layout;
import com.arosbio.chem.io.out.image.Position.Vertical;

public interface FigureField {
	
	public Vertical getAlignment();
	
	public void setAlignment(Vertical alignment);
		
	public Font getFont();
	
	public void setFont(Font font);
	
	public boolean isFontSet();
	
	public Color getTextColor();
	
	public void setTextColor(Color color);
	
	public boolean isTextColorSet();
	
	public List<Layout> getLayouts();
	
	public void addLayout(Layout layout);
	
	public void setLayouts(List<Layout> layout);
	
}

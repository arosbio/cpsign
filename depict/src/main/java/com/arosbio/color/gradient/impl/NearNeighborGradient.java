/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.color.gradient.impl;

import java.awt.Color;

import com.arosbio.color.gradient.ColorGradient;


public class NearNeighborGradient implements ColorGradient {

	@Override
	public Color getColor(double value) {
		value = (value+1)/2;
		// base color (220,220,220) 0xDCDCDC
		float red = (float) (((0.0-220.0/255.0)/(0.0-1.0))*(value-1.0)+220.0/255.0);
		float blue = (float) (((255.0/255.0-220.0/255.0)/(0.0-1.0))*(value-1.0)+220.0/255.0);
		float green = (float) (((0.0-220.0/255.0)/(0.0-1.0))*(value-1.0)+220.0/255.0);
		Color c = new Color(red, green, blue);		
		return c;
	}

}

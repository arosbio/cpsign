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

public class StandardGradient implements ColorGradient {

	@Override
	public Color getColor(double value) {
		value = (value+1)/2;
			float green = 0.0f;
			float red = 0.0f;
			float blue= 0.0f;
			if (value<0.25){
				red = 0.0f;
				blue = 254;
				green = (float) (((0-255.0/255.0)/(0.0-0.25))*(value-0.25)+255.0/255.0);
			}
			else if ( (value<0.5) && (value>=0.25) ){
				red = 0.0f;
				blue = (float) (((255.0/255.0-0.0/255.0)/(0.25-0.5))*(value-0.5)+0.0/255.0);
				green = 255.0f;
			}
			else if ( (value<0.75) && (value>=0.5) ){
				red = (float) (((0.0/255.0-255.0/255.0)/(0.5-0.75))*(value-0.75)+255.0/255.0);
				blue = 0.0f;
				green = 255.0f;
			}
			else{
				red = 255.0f;
				blue = 0.0f;
				green = (float) (((255.0-0.0/255.0)/(0.75-1.0))*(value-1.0)+0.0/255.0);						
			}
			return new Color((int)red, (int)green, (int)blue);
		}
}

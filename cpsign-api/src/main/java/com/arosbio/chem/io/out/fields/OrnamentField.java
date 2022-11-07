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

import com.arosbio.chem.io.out.image.Layout;

public abstract class OrnamentField extends AbstractField {
	
	abstract BufferedImage depictInternal(int width);
	
	public BufferedImage depict(int width){
		if (getLayouts().isEmpty())
			return depictInternal(width);
		int totalFrameWidth = 0;
		for (Layout frame: getLayouts()){
			totalFrameWidth += frame.getAdditionalWidth();
		}
		// internal img to add frames to
		BufferedImage img = depictInternal(width-totalFrameWidth);
		
		for (Layout frame: getLayouts()){
			img = frame.addLayout(img);
		}
		return img;
	}

}

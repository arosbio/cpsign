/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem.io.out;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.arosbio.chem.io.out.image.ImageUtils;

public class MoleculeFigure {

	private BufferedImage depiction;

	MoleculeFigure(BufferedImage depiction) {
		this.depiction = depiction;
	}

	public BufferedImage getImage(){
		return depiction;
	}

	public String getBase64(){
		return ImageUtils.convertToBase64(depiction);
	}

	public void saveToFile(File imgFile) throws IOException{
		ImageIO.write(depiction, "png", imgFile);
	}
	
}
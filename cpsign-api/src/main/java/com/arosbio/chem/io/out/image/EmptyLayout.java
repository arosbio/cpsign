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

import java.awt.image.BufferedImage;

/**
 * A completely empty {@link Layout} with <b>no padding</b>, <b>no boarder</b> and <b>no margin</b>.
 * @author staffan
 *
 */
public class EmptyLayout implements Layout {

	public EmptyLayout() {}
	
	public BufferedImage addLayout(BufferedImage img){
		return img;
	}

	@Override
	public int getAdditionalWidth() {
		return 0;
	}

	@Override
	public int getAdditionalHeight() {
		return 0;
	}

}

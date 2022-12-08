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

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import com.arosbio.chem.io.out.MoleculeFigureBuilder;


/**
 * Default {@link CustomLayout} that decides on padding based on the Figure's size
 * using the {@link MoleculeFigureBuilder} reference. Only uses padding 
 * - <b>neither margin</b> or <b>boarder</b> added.
 * 
 * @author staffan
 *
 */
public class DefaultLayout implements Layout {
	 
	private MoleculeFigureBuilder<?> builder;

	public DefaultLayout(MoleculeFigureBuilder<?> builder) {
		this.builder = builder;
	}
	
	@Override
	public int getAdditionalWidth() {
		return builder.getDefaultPadding()*2;
	}

	@Override
	public int getAdditionalHeight() {
		return builder.getDefaultPadding()*2;
	}

	@Override
	public BufferedImage addLayout(BufferedImage img) {
		int padding = builder.getDefaultPadding();
		BufferedImage resultImg = new BufferedImage(img.getWidth()+padding*2, 
				img.getHeight()+padding*2, img.getType());
		Graphics2D g2d = resultImg.createGraphics();
		g2d.drawImage(img, padding, padding, img.getWidth(), img.getHeight(), null);
		g2d.dispose();
		return resultImg;
	}


}

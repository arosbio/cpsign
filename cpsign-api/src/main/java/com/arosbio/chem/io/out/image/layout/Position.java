/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem.io.out.image.layout;

public class Position {
	
	public static enum Vertical {
		LEFT_ADJUSTED, CENTERED, RIGHT_ADJUSTED;
	}
	
	public static enum Horizontal {
		TOP, CENTER, BOTTOM;
	}
	
	private Vertical x;
	private Horizontal y;
	
	public Position(Vertical vertical, Horizontal horizontal) {
		this.x = vertical;
		this.y = horizontal;
	}

	public Vertical getVertical(){
		return x;
	}
	
	public void setVertical(Vertical vertical){
		this.x = vertical;
	}
	
	public Horizontal getHorizontal(){
		return y;
	}
	
	public void setHorizontal(Horizontal horizontal){
		this.y = horizontal;
	}
}

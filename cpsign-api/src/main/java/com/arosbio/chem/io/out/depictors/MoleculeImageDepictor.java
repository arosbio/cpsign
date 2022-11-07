/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem.io.out.depictors;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.Map;

import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.chem.io.out.fields.AbstractField;
import com.arosbio.chem.io.out.image.Layout;
import com.arosbio.color.gradient.ColorGradient;
import com.arosbio.depict.MoleculeDepictor;


public abstract class MoleculeImageDepictor extends AbstractField {
	
	private MoleculeDepictor.Builder builder;
	private MoleculeDepictor depictor;
	
	MoleculeImageDepictor(ColorGradient gradient) {
		builder = new MoleculeDepictor.Builder().color(gradient);
	}
	
	// GETTERS AND SETTERS
	
	public void setFont(Font font){
		builder.font(font);
	}
	
	public int getImageWidth(){
		return (depictor != null? depictor.getImageWidth() : builder.width());
	}
	
	public void setImageWidth(int width){
		builder.w(width);
		depictor=null;
	}
	
	public int getImageHeight(){
		return (depictor!=null ? depictor.getImageHeight() : builder.height());
	}
	
	public void setImageHeight(int height){
		builder.h(height);
		depictor=null;
	}
	
	public void toggleDisplayAtomNums(boolean displayNums){
		builder.showAtomNumbers(displayNums);
		depictor=null;
	}
	
	public void setDepictAtomNumbers(boolean depictAtomNumbers){
		builder.showAtomNumbers(depictAtomNumbers);
		depictor=null;
	}
	
	public void setAtomNumColor(Color numberColor){
		builder.numberColor(numberColor);
		depictor=null;
	}
	
	public void setAtomNumScaleFactor(double scaleFactor){
		builder.scaleAtomNumbers(scaleFactor);
		depictor=null;
	}
	
	public void setForceRecalculate2DCoords(boolean calculate2D) {
		builder.recalc2D(calculate2D);
		depictor=null;
	}
	
	// PROTECTED STUFF
	
	public void setColorGradient(ColorGradient gradient){
		builder.color(gradient);
		depictor=null;
	}
	
	public ColorGradient getColorGradient(){
		return depictor!=null? depictor.getColorGradient() : builder.colorScheme();
	}
	
	protected BufferedImage doDepict(IAtomContainer mol, Map<IAtom, Double> colors){
		if (depictor==null){
			depictor = builder.build();
		}
		BufferedImage molImg = depictor.depict(mol, colors);
		if (getLayouts().isEmpty())
			return molImg;
		// we have frames we need to add
		for (Layout frame : getLayouts()){
			molImg = frame.addLayout(molImg);
		}
		
		return molImg;
	}
	
	

}

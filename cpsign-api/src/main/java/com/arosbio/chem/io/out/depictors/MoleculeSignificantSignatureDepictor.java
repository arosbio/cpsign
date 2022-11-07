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
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.beryx.awt.color.ColorFactory;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.color.gradient.ColorGradient;
import com.arosbio.color.gradient.impl.GradientPoint;
import com.arosbio.color.gradient.impl.MultipointGradient;

public class MoleculeSignificantSignatureDepictor extends MoleculeImageDepictor {
	
	private static final Color DEFAULT_BACKGROUND_COLOR = ColorFactory.web("#E4E4E4"); // Grayish
	private static final Color DEFAULT_HIGHLIGHT_COLOR = Color.BLUE; 
	
	private Color backgroundColor, highlightColor;
	
	public MoleculeSignificantSignatureDepictor() {
		this(DEFAULT_BACKGROUND_COLOR, DEFAULT_HIGHLIGHT_COLOR);
	}
	
	public MoleculeSignificantSignatureDepictor(Color highlightColor){
		this(highlightColor, DEFAULT_BACKGROUND_COLOR);
	}
	
	public MoleculeSignificantSignatureDepictor(Color highlightColor, Color moleculeBackgroundColor) {
		super(generateGradient(moleculeBackgroundColor, highlightColor));
		this.backgroundColor = moleculeBackgroundColor;
		this.highlightColor = highlightColor;
	}
	
	public void setHighlightColor(Color highlightColor){
		this.highlightColor = highlightColor;
		updateGradient();
	}
	
	public Color getHighlightColor() {
		return highlightColor;
	}
	
	public void setBackgroundColor(Color backgroundColor){
		this.backgroundColor = backgroundColor;
		updateGradient();
	}
	
	public Color getBackgroundColor() {
		return backgroundColor;
	}
	
	private void updateGradient(){
		super.setColorGradient(generateGradient(backgroundColor, highlightColor));
	}
	
	private static ColorGradient generateGradient(Color background, Color highlight){
		return new MultipointGradient(
				Arrays.asList(GradientPoint.of(background, 0.1f),
				GradientPoint.of(ColorFactory.web("#E4E4E4"), 0.2f),
				GradientPoint.of(highlight, 0.4f)));
	}
	
	
	public BufferedImage depict(IAtomContainer mol, Set<IAtom> atomsToHighlight){
		Map<IAtom, Double> gradient = new HashMap<>();
		for(IAtom atom: atomsToHighlight)
			gradient.put(atom, 1.0);
		return super.doDepict(mol, gradient);
	}

}

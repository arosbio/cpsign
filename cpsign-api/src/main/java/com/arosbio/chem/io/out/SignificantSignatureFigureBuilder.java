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

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.chem.io.out.depictors.MoleculeSignificantSignatureDepictor;
import com.arosbio.chem.io.out.image.Layout;


public class SignificantSignatureFigureBuilder extends MoleculeFigureBuilder {

	private MoleculeSignificantSignatureDepictor depictor;

	public SignificantSignatureFigureBuilder(MoleculeSignificantSignatureDepictor depictor) {
		this.depictor = depictor;
	}

	public MoleculeSignificantSignatureDepictor getDepictor(){
		return depictor;
	}

	public MoleculeFigure build(IAtomContainer mol, Set<?> atoms){
		Set<IAtom> iatoms = new HashSet<>();
		for(Object atom: atoms){
			if(atom instanceof IAtom)
				iatoms.add((IAtom) atom);
			else if (atom instanceof Integer || atom instanceof Long)
				iatoms.add(mol.getAtom((int) atom));
			else
				throw new IllegalArgumentException("Atoms must be given either as Set of Integers or IAtoms");
		}
		super.computeMetrics();
		
		// Render images above and below molecule field
		List<BufferedImage> above = renderFieldsAboveMolecule();
		List<BufferedImage> below = renderFieldsBelowMolecule();
		
		// Calculate the final size of the full image
		int extraFieldsAddedHeight = 0;
		for (BufferedImage a: above)
			extraFieldsAddedHeight +=a.getHeight();
		for (BufferedImage b: below)
			extraFieldsAddedHeight +=b.getHeight();
		
		
		int finalFigureWidth=0, finalFigureHeight=0; //, molBoarderAddedHeight=0, molBoarderAddedWidth;
		if (explicitFigureHeight != null) {
			finalFigureHeight = explicitFigureHeight;
		} else {
			// calculate the height 
			finalFigureHeight = depictor.getImageHeight() + extraFieldsAddedHeight;
			for (Layout l: depictor.getLayouts())
				finalFigureHeight += l.getAdditionalHeight();
		}
		
		if (explicitFigureWidth != null) {
			finalFigureWidth = explicitFigureWidth;
		} else {
			finalFigureWidth = depictor.getImageWidth();
			for (Layout l: depictor.getLayouts())
				finalFigureWidth += l.getAdditionalWidth();
		}
		
		// Render the image
		BufferedImage molImg = null;
		if (explicitFigureSizeBeenSet()) {
			// Here we have to re-set the size of the molecule-image to be generated
			if (explicitFigureHeight != null) {
				int molImgHeight = explicitFigureHeight - extraFieldsAddedHeight;
				for (Layout l: depictor.getLayouts())
					molImgHeight -= l.getAdditionalHeight();
				depictor.setImageHeight(molImgHeight);
			} if (explicitFigureWidth != null) {
				int molImgWidth = explicitFigureWidth;
				for (Layout l: depictor.getLayouts())
					molImgWidth -= l.getAdditionalWidth();
				depictor.setImageWidth(molImgWidth);
			}
			molImg = depictor.depict(mol, iatoms);
		} else {
			// here we just use the settings of the depictor
			molImg = depictor.depict(mol, iatoms); 
		}

		// create the final img
		BufferedImage finalImg = new BufferedImage(finalFigureWidth, finalFigureHeight, molImg.getType());
		Graphics2D g2d = finalImg.createGraphics();

		if (getBackgroundColor()!=null){
			g2d.setColor(getBackgroundColor());
			g2d.fillRect(0, 0, finalFigureWidth, finalFigureHeight);
		}

		int yCoord = 0;
		// fields before
		for (BufferedImage img: above){
			g2d.drawImage(img, 0, yCoord, null);
			yCoord+=img.getHeight();
		}
		// mol
		g2d.drawImage(molImg, 0, yCoord, null);
		yCoord+=molImg.getHeight();
		// fields after
		for (BufferedImage img: below){
			g2d.drawImage(img, 0, yCoord, null);
			yCoord+=img.getHeight();
		}

		g2d.dispose();

		return new MoleculeFigure(finalImg);
	}

}

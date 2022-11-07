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

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.io.out.depictors.MoleculeImageDepictor;
import com.arosbio.chem.io.out.fields.FigureField;
import com.arosbio.chem.io.out.fields.OrnamentField;
import com.arosbio.chem.io.out.image.DefaultLayout;
import com.arosbio.chem.io.out.image.FontFactory;
import com.arosbio.chem.io.out.image.Layout;
import com.arosbio.chem.io.out.image.Position.Vertical;
import com.arosbio.io.IOSettings;

/**
 * Builder class for the {@link MoleculeFigure} class
 * @author staffan
 *
 */
public abstract class MoleculeFigureBuilder {

	private static final Logger LOGGER = LoggerFactory.getLogger(MoleculeFigureBuilder.class);

	private static final double PADDING_SCALE_FACTOR = 0.3;

	private List<OrnamentField> fieldsOverMol = new ArrayList<>();
	private List<OrnamentField> fieldsUnderMol = new ArrayList<>();

	private Font font;
	private int defaultPadding;
	private Color backgroundColor=Color.WHITE;
	private Color textColor = Color.BLACK;
	protected Integer explicitFigureHeight, explicitFigureWidth;

	MoleculeFigureBuilder() {}

	private void configField(FigureField field){
		if (!field.isFontSet())
			field.setFont(font);
		if (!field.isTextColorSet())
			field.setTextColor(textColor);
		if (field.getLayouts().isEmpty())
			field.addLayout(new DefaultLayout(this));
	}

	// SETTERS / GETTERS
	public void setFieldsOverImg(List<OrnamentField> fields){
		this.fieldsOverMol = fields;
	}

	public List<OrnamentField> getFieldsOverImg(){
		return fieldsOverMol;
	}

	public void setFieldsUnderImg(List<OrnamentField> fields){
		this.fieldsUnderMol = fields;
	}

	public List<OrnamentField> getFieldsUnderImg(){
		return fieldsUnderMol;
	}

	public void addFieldOverImg(OrnamentField field){
		this.fieldsOverMol.add(field);
	}

	public void addFieldUnderImg(OrnamentField field){
		this.fieldsUnderMol.add(field);
	}

	public abstract MoleculeImageDepictor getDepictor();

	public Font getFont() {
		return font;
	}

	public void setFont(Font font) {
		this.font = font;
	}
	
	public void setBackgroundColor(Color background) {
		this.backgroundColor = background;
	}
	
	public Color getBackgroundColor() {
		return this.backgroundColor;
	}

	public int getDefaultPadding(){
		computeMetrics();
		return defaultPadding;
	}

	public void setFigureHeight(int height) {
		explicitFigureHeight = height;
	}

	public void setFigureWidth(int width) {
		explicitFigureWidth = width;
	}

	public boolean explicitFigureSizeBeenSet() {
		return explicitFigureHeight!=null || explicitFigureWidth!=null;
	}

	List<BufferedImage> renderFieldsAboveMolecule(){
		if (fieldsOverMol.isEmpty())
			return new ArrayList<>();

		int totalWidth;
		if (explicitFigureWidth !=null)
			totalWidth = explicitFigureWidth;
		else {
			totalWidth = getDepictor().getImageWidth();
			for (Layout l: getDepictor().getLayouts()) {
				totalWidth += l.getAdditionalWidth();
			}
		}

		// For all fields above the mol-img
		BufferedImage pointer=null;
		List<BufferedImage> above = new ArrayList<>();
		for (OrnamentField field: fieldsOverMol) {
			pointer = depictField(field, totalWidth);
			above.add(pointer);
		}
		return above;
	}

	List<BufferedImage> renderFieldsBelowMolecule(){
		if (fieldsUnderMol.isEmpty())
			return new ArrayList<>();

		int totalWidth;
		if (explicitFigureWidth !=null)
			totalWidth = explicitFigureWidth;
		else {
			totalWidth = getDepictor().getImageWidth();
			for (Layout l: getDepictor().getLayouts()) {
				totalWidth += l.getAdditionalWidth();
			}
		}

		// For all fields above the mol-img
		BufferedImage pointer=null;
		List<BufferedImage> below = new ArrayList<>();
		for (OrnamentField field: fieldsUnderMol) {
			pointer = depictField(field, totalWidth);
			below.add(pointer);
		}
		return below;
	}

	private BufferedImage depictField(OrnamentField field, int totalWidth){
		configField(field);
		LOGGER.debug("Adding depiction field:{}",field);
		// create the field
		BufferedImage img = field.depict(totalWidth);

		// the field is in most cases not wide enough
		if (img.getWidth() == totalWidth)
			return img;
		else {
			BufferedImage resImg = new BufferedImage(totalWidth, img.getHeight(), img.getType());
			Graphics2D g = resImg.createGraphics();
			if (field.getAlignment() == Vertical.RIGHT_ADJUSTED)
				g.drawImage(img, totalWidth-img.getWidth(), 0, null);
			else if (field.getAlignment() == Vertical.CENTERED)
				g.drawImage(img, (int)(totalWidth/2d - img.getWidth()/2d), 0, null);
			else
				g.drawImage(img, 0, 0, null);
			g.dispose();
			return resImg;
		}

	}

	void computeMetrics() {
		if (font == null) {
			MoleculeImageDepictor depictor = getDepictor();
			int shortestSide = Math.min(depictor.getImageHeight(), depictor.getImageWidth());
			int newFontSize = Math.max(11, (int) (7 + shortestSide/40)); //*(1+0.0005*Math.abs(400-smallestSide))));
			font = FontFactory.plain(newFontSize);
		}
		// Set good font
		if (!getDepictor().isFontSet()){
			getDepictor().setFont(font.deriveFont(18f));
			LOGGER.debug("using standard font, pt=18");
		}

		// Compute the default padding
		BufferedImage tmp_ = new BufferedImage(1,1,IOSettings.BUFFERED_IMAGE_TYPE);
		Graphics2D g2d = tmp_.createGraphics();
		FontMetrics fm = g2d.getFontMetrics(font);
		defaultPadding = (int)(PADDING_SCALE_FACTOR*fm.getHeight());
		g2d.dispose();
		LOGGER.debug("computed metrics for MoleculeFigure, using font: " + font + ", defaultPadding="+defaultPadding);
	}

	// BUILD
//	private MoleculeFigure build(BufferedImage molImg){
//		computeMetrics();
//
//		int totalHeight = molImg.getHeight();
//		final int totalWidth = molImg.getWidth();
//		BufferedImage pointer=null;
//
//		// For all fields before the mol-img
//		List<BufferedImage> before = new ArrayList<>();
//		for (OrnamentField field: fieldsOverMol) {
//			pointer = depictField(field, totalWidth);
//			before.add(pointer);
//			totalHeight += pointer.getHeight();
//		}
//
//		// For all fields after the mol-img
//		List<BufferedImage> after = new ArrayList<>();
//		for(OrnamentField field: fieldsUnderMol) {
//			pointer = depictField(field, totalWidth);
//			after.add(pointer);
//			totalHeight += pointer.getHeight();
//		}
//
//		// create the final img
//		BufferedImage finalImg = new BufferedImage(totalWidth, totalHeight, molImg.getType());
//		Graphics2D g2d = finalImg.createGraphics();
//
//		if (backgroundColor!=null){
//			g2d.setColor(backgroundColor);
//			g2d.fillRect(0, 0, totalWidth, totalHeight);
//		}
//
//		int yCoord = 0;
//		// fields before
//		for(BufferedImage img: before){
//			g2d.drawImage(img, 0, yCoord, null);
//			yCoord+=img.getHeight();
//		}
//		// mol
//		g2d.drawImage(molImg, 0, yCoord, null);
//		yCoord+=molImg.getHeight();
//		// fields after
//		for(BufferedImage img: after){
//			g2d.drawImage(img, 0, yCoord, null);
//			yCoord+=img.getHeight();
//		}
//
//		g2d.dispose();
//
//		return new MoleculeFigure(finalImg);
//	}

}
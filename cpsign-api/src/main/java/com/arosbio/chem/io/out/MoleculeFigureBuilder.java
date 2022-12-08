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
public abstract class MoleculeFigureBuilder<T> {

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

	protected abstract T getThis();
	protected abstract MoleculeImageDepictor getDepictor();

	// SETTERS / GETTERS
	/**
	 * Replaces any existing fields with new ones
	 * @param fields new fields to use
	 */
	public T fieldsOverImg(List<OrnamentField> fields){
		this.fieldsOverMol = fields;
		if (this.fieldsOverMol == null)
			this.fieldsOverMol = new ArrayList<>();
		return getThis();
	}

	public List<OrnamentField> getFieldsOverImg(){
		return fieldsOverMol;
	}

	public T fieldsUnderImg(List<OrnamentField> fields){
		this.fieldsUnderMol = fields;
		if (this.fieldsUnderMol == null)
			this.fieldsUnderMol = new ArrayList<>();
		return getThis();
	}

	public List<OrnamentField> getFieldsUnderImg(){
		return fieldsUnderMol;
	}

	public T addFieldOverImg(OrnamentField field){
		this.fieldsOverMol.add(field);
		return getThis();
	}

	public T addFieldUnderImg(OrnamentField field){
		this.fieldsUnderMol.add(field);
		return getThis();
	}

	public Font getFont() {
		return font;
	}

	public T font(Font font) {
		this.font = font;
		return getThis();
	}

	public T bg(Color background) {
		this.backgroundColor = background;
		return getThis();
	}
	
	public T background(Color background) {
		this.backgroundColor = background;
		return getThis();
	}
	
	public Color getBackgroundColor() {
		return this.backgroundColor;
	}

	public int getDefaultPadding(){
		computeMetrics();
		return defaultPadding;
	}

	public T figureHeight(int height) {
		explicitFigureHeight = height;
		return getThis();
	}

	public T figureWidth(int width) {
		explicitFigureWidth = width;
		return getThis();
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
			for (Layout l : getDepictor().getLayouts()) {
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
		LOGGER.debug("computed metrics for MoleculeFigure, using font: {}, defaultPadding={}",
			font, defaultPadding);
	}


}
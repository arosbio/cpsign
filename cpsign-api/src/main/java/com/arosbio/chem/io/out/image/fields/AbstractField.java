/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem.io.out.image.fields;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.io.out.image.layout.DefaultLayout;
import com.arosbio.chem.io.out.image.layout.Layout;
import com.arosbio.chem.io.out.image.layout.Position.Vertical;

public abstract class AbstractField implements FigureField {
	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractField.class);
	
	private final Vertical alignment; 
	private final List<Layout> layouts;

	AbstractField(Builder<?,?> b){
		alignment = b.alignment;
		layouts = b.layouts; 
	}

	public static abstract class Builder<F extends AbstractField, B extends Builder<F,B>> {
		private Vertical alignment = Vertical.LEFT_ADJUSTED;
		private List<Layout> layouts = new ArrayList<>();
		
		public abstract B getThis();
		public abstract F build();

		Builder(){
			layouts.add(new DefaultLayout());
		}

		public B alignment(Vertical align){
			this.alignment = align;
			return getThis();
		}

		public B layout(Layout layout){
			if (!layouts.isEmpty()){
				layouts = new ArrayList<>();
			}
			if (layout!=null){
				layouts.add(layout);
			}
			return getThis();
		}

		public B layouts(List<Layout> layouts){
			this.layouts = layouts;
			return getThis();
		}
	}
	
	public List<Layout> getLayouts(){
		return layouts;
	}
	
	public Vertical getAlignment(){
		return alignment;
	}

	public int calculateAddedLayoutWidth(){
		int totalFrameWidth = 0;
		for (Layout frame : getLayouts()){
			totalFrameWidth += frame.getAdditionalWidth();
		}
		return totalFrameWidth;
	}

	public int calculateAddedLayoutHeight(){
		int totalFrameHeight = 0;
		for (Layout f : getLayouts()){
			totalFrameHeight += f.getAdditionalHeight();
		}
		return totalFrameHeight;
	}

	public Rectangle2D addLayouts(Graphics2D g, Rectangle2D inputArea){
		if (layouts.isEmpty())
			return inputArea;
		
		Rectangle2D currentArea = inputArea;
		LOGGER.debug("Adding {} layouts, input area: {}", getLayouts().size(), inputArea);
		for (Layout l : getLayouts()){
			currentArea = l.addLayout(g, currentArea);
		}
		LOGGER.debug("Output area after layouts: {}", currentArea);
		return currentArea;
	}
}
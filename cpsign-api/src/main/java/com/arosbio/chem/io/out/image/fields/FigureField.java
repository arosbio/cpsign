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
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import com.arosbio.chem.io.out.image.RendererTemplate;
import com.arosbio.chem.io.out.image.RendererTemplate.RenderInfo;
import com.arosbio.chem.io.out.image.layout.Layout;
import com.arosbio.chem.io.out.image.layout.Position.Vertical;

public interface FigureField {
	
	public Vertical getAlignment();
	
	public List<Layout> getLayouts();

	public Dimension2D calculateDim(RendererTemplate.Context context);

	public void render(Graphics2D graphics, Rectangle2D area, RenderInfo info) throws IllegalStateException;
	
}

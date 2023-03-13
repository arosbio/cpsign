/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.depict;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import com.arosbio.color.gradient.ColorGradient;
import com.arosbio.color.gradient.GradientFactory;
import com.arosbio.color.gradient.impl.DefaultGradient;
import com.arosbio.color.gradient.impl.GradientPoint;
import com.arosbio.color.gradient.impl.MultipointGradient;
import com.arosbio.color.gradient.impl.NearNeighborGradient;
import com.arosbio.color.gradient.impl.StandardGradient;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class GradientPaletteTest extends BaseTestClass {
	
	public static boolean RENDER_GRADIENTS_FOR_CPSIGN_DOCS = true;
	
	@Rule public TestName name = new TestName();

	@DataPoint public static ColorGradient rainbow = GradientFactory.getRainbowGradient();
	@DataPoint public static ColorGradient bloomGradient = GradientFactory.getDefaultBloomGradient();

	@DataPoints public static ColorGradient[] gradients = {new DefaultGradient(), new NearNeighborGradient(), new StandardGradient()};

	@Theory
	public void generatePalette(ColorGradient gradient) throws IOException {
		generatePalette(gradient,new File(TEST_OUTPUT_DIR, gradient.getClass().getSimpleName()+".png"));
	}

	int gradientHeight=50,
			gradientWidth=1000, 
			padding=0; 


	@Test
	public void generateStandardGradients() throws IOException {

		List<ColorGradient> gradients = new ArrayList<>();
		gradients.add(GradientFactory.getBlueRedGradient());
		gradients.add(GradientFactory.getCustom2Points(Color.BLUE, Color.RED));
		gradients.add(GradientFactory.getRedBlueGradient());
		gradients.add(GradientFactory.getCustom2Points(Color.RED, Color.BLUE));
		gradients.add(GradientFactory.getRedBlueRedGradient());
		gradients.add(GradientFactory.getCustom3Points(Color.RED, Color.BLUE, Color.RED));
		gradients.add(GradientFactory.getDefaultBloomGradient());
		gradients.add(GradientFactory.getRainbowGradient());
		gradients.add(GradientFactory.getCyanMagenta());
		gradients.add(GradientFactory.getCustom2Points(Color.CYAN, Color.MAGENTA));

		BufferedImage pageImage = new BufferedImage(gradientWidth, gradientHeight*gradients.size(), BufferedImage.TYPE_4BYTE_ABGR);
		Graphics2D graphics = pageImage.createGraphics();
		graphics.setColor(Color.WHITE);
		graphics.drawRect(0, 0, gradientWidth, gradientHeight*gradients.size());

		


		for(int i=0; i<gradients.size(); i++)
			graphics.drawImage(GenerateDepictionsTest.drawGradient(gradientWidth, gradients.get(i)),
					0,gradientHeight*i,gradientWidth,gradientHeight,null);

		graphics.dispose();
		ImageIO.write(pageImage, "png", new File(TEST_OUTPUT_DIR,"defaultGradients.png"));	

		
		if (RENDER_GRADIENTS_FOR_CPSIGN_DOCS) // for cpsign-docs
			for(int i=0; i<gradients.size(); i++){
				pageImage = new BufferedImage(gradientWidth, gradientHeight, BufferedImage.TYPE_4BYTE_ABGR);
				Graphics2D graphics2d = pageImage.createGraphics();
				graphics2d.drawImage(GenerateDepictionsTest.drawGradient(gradientWidth, gradients.get(i)),0,0,pageImage.getWidth(), pageImage.getHeight(),null);
				ImageIO.write(pageImage,"png", new File(TEST_OUTPUT_DIR,"gradient_"+i+".png"));
				graphics2d.dispose();
			}
	}
	
	@Test
	public void generateCustomGradient() throws IOException{
		List<GradientPoint> points = new ArrayList<>();
		points.add(GradientPoint.of(Color.RED, -1f));
		points.add(GradientPoint.of(Color.GRAY, -.2f));
		points.add(GradientPoint.of(Color.BLACK, 0f));
		points.add(GradientPoint.of(Color.GRAY, .2f));
		points.add(GradientPoint.of(Color.BLUE, 1f));
		
		generatePalette(new MultipointGradient(points),new File(TEST_OUTPUT_DIR,"custom.png"));
	}


	private void generatePalette(ColorGradient gradient, File file) throws IOException{

		BufferedImage pageImage = new BufferedImage(gradientWidth, gradientHeight, BufferedImage.TYPE_4BYTE_ABGR);
		Graphics2D graphics = pageImage.createGraphics();
		graphics.setColor(Color.WHITE);
		graphics.drawRect(0, 0, gradientWidth, gradientHeight);

		graphics.drawImage(GenerateDepictionsTest.drawGradient(gradientWidth, gradient),
				padding,padding,gradientWidth,gradientHeight,null);

		graphics.dispose();
		ImageIO.write(pageImage, "png", file);
	}


}

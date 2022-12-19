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
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import com.arosbio.color.gradient.ColorGradient;
import com.arosbio.color.gradient.GradientFactory;
import com.arosbio.color.gradient.impl.DefaultGradient;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.jhlabs.image.ContrastFilter;
import com.jhlabs.image.UnsharpFilter;

public class GenerateDepictionsTest extends BaseTestClass {
	
	private static final ColorGradient BLUE_RED_LIGHT = GradientFactory.getBlueRedGradient();

	IAtomContainer mol1, mol2;
	Map<IAtom, Double> coloringMap1, coloringMap2;
	int colorInMol1 = 9, colorInMol2 = 20;
	
	int imageSize = 400; // in px
	int padding = 10; // in px
	int pageWidth = imageSize*3 + 4*padding;
	int pageHeight = imageSize*4 + 5*padding;
	
	@Before
	public void generateTestIAtomContainers() throws Exception{
		SmilesParser sp = new SmilesParser(SilentChemObjectBuilder.getInstance());
		mol1 = sp.parseSmiles("[C-]CC(C(=O)NC1=CC=C(C=C1)C(F)(F)F)N(C)CC(=O)N2CCN(CC2)CC3=CC=CC=C3"); 
		mol2 = sp.parseSmiles("CC(C(=O)NC1=CC=C(C=C1)C(F)(F)F)N(C)CC(=O)N2CCN(CC2)CC3=CC=CC=C3"); // 39 atoms
		CDKConfigureAtomContainer.configMolecule(mol1);
		
		coloringMap1 = new HashMap<>();
		
		coloringMap1.put(mol1.getAtom(0), -1.5); // Check what happens outside of the ranges
		coloringMap1.put(mol1.getAtom(1), 15d);
		
		for(int i=2; i< colorInMol1; i++){
			IAtom atom = mol1.getAtom(i);
			coloringMap1.put(atom, (((double)i)/colorInMol1)*2-1);
		}
		
		
		coloringMap2 = new HashMap<>();
		for(int i=0; i< colorInMol2; i++){
			IAtom atom = mol2.getAtom(i);
			coloringMap2.put(atom, (((double)i)/colorInMol2)*2-1);
		}
		
	}
	
	
	@Test
	public void generateVanillaImgs() throws IOException{
		List<MoleculeDepictor.Builder> builders = new ArrayList<>();
		builders.add(new MoleculeDepictor.Builder()
			.color(new DefaultGradient())
			.font(new Font(Font.SERIF,Font.BOLD,13))
			.showAtomNumbers(true)); 
		builders.add(new MoleculeDepictor.Builder().color(BLUE_RED_LIGHT).showAtomNumbers(true));
		builders.add(new MoleculeDepictor.Builder()); // Default 
		builders.add(new MoleculeDepictor.Builder().color(BLUE_RED_LIGHT));

		// Set common image-size and get MoleculeDepictors
		List<MoleculeDepictor> depictors = new ArrayList<>();
		for (MoleculeDepictor.Builder b : builders){
			depictors.add(b.w(imageSize).h(imageSize).build());
		}
		
		int pageWidth=imageSize + padding*2;
		BufferedImage pageImage = new BufferedImage(pageWidth, pageHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = pageImage.createGraphics();
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, pageWidth, pageHeight);

		BufferedImage imgToAdd1; 
		for (int i=0; i<depictors.size(); i++){ //
			
			imgToAdd1 = depictors.get(i).depict(mol1, coloringMap1);
			graphics.drawImage(imgToAdd1, padding, (i+1)*padding + i*imageSize, null);
			
		}

		ImageIO.write(pageImage, "png", new File(BaseTestClass.TEST_OUTPUT_DIR, "vanilla.png"));	
	}
	
	
	@Test
	public void generateTestImgsStuffs() throws IOException{
		MoleculeDepictor.Builder [] depictors = new MoleculeDepictor.Builder[]{
			
		new MoleculeDepictor.Builder()
			.color(GradientFactory.getCustomGradient("[{\"color\":\"#E4E4E4\",\"pos\":0.20000005},{\"color\":\"#FF0004\",\"pos\":0.6},{\"color\":\"#E4E4E4\",\"pos\":-0.19999999},{\"color\":\"#0000FF\",\"pos\":-0.6}]"))
			
		,new MoleculeDepictor.Builder()
			.color(GradientFactory.getCustomGradient("[{\"color\":\"#E4E4E4\",\"pos\":0.41999996},{\"color\":\"#FF0004\",\"pos\":0.6},{\"color\":\"#E4E4E4\",\"pos\":-0.39999998},{\"color\":\"#FF0004\",\"pos\":-0.6},{\"color\":\"#0000FF\",\"pos\":0.006109953}]"))
			
		,new MoleculeDepictor.Builder()
			.color(GradientFactory.getCustomGradient("[{\"color\":\"#FF0004\",\"pos\":-0.58000004},{\"color\":\"#E5E5E5\",\"pos\":0.20000005},{\"color\":\"#0000FF\",\"pos\":0.6},{\"color\":\"#E6E6E6\",\"pos\":-0.19999999}]"))
			
		, new MoleculeDepictor.Builder()
			.color(GradientFactory.getCustomGradient("[{\"color\":\"#00FFFF\",\"pos\":-0.58000004},{\"color\":\"#F2F2F2\",\"pos\":0.20000005},{\"color\":\"#FF00FF\",\"pos\":0.6},{\"color\":\"#F1F1F1\",\"pos\":-0.18}]"))
		};

		AffineTransform at = new AffineTransform();
    	at.rotate(Math.toRadians(90), imageSize / 2, imageSize / 2);
   	 	BufferedImageOp bio = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
		
		BufferedImageOp[] ops = new BufferedImageOp[] {
			new ContrastFilter(),
			new UnsharpFilter(),
			bio
		};
		// Local width 
		int pageWidth = imageSize*6 + 12*padding;
		
		BufferedImage pageImage = new BufferedImage(pageWidth, pageHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = pageImage.createGraphics();
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, pageWidth, pageHeight);
		
		BufferedImage imgToAdd;
		for(int i=0; i< depictors.length; i++){
			// First column - no filters
			imgToAdd = depictors[i].build().depict(mol1, coloringMap1);
			graphics.drawImage(imgToAdd, padding, (i+1)*padding + i*imageSize, null);
			// Second column - apply operation after 'depict' method
			imgToAdd = depictors[i].build().depict(mol1, coloringMap1);
			graphics.drawImage(imgToAdd, ops[0], padding*3 + imageSize, (i+1)*padding + i*imageSize);
			// Third - apply operation within 'depict'
			imgToAdd = depictors[i].imageOp(ops[0]).build().depict(mol1, coloringMap1);
			graphics.drawImage(imgToAdd, padding*5 + 2*imageSize, (i+1)*padding + (i*1)*imageSize, null);
			// Forth column - apply operation after 'depict' method
			imgToAdd = depictors[i].build().depict(mol1, coloringMap1);
			graphics.drawImage(imgToAdd, ops[1], padding*7 + 3*imageSize, (i+1)*padding + i*imageSize);
			// Fifth - apply operation within 'depict'
			imgToAdd = depictors[i].imageOp(ops[1]).build().depict(mol1, coloringMap1);
			graphics.drawImage(imgToAdd, padding*9 + 4*imageSize, (i+1)*padding + (i*1)*imageSize, null);
			// Sixth - apply both image ops
			imgToAdd = depictors[i].imageOp(ops).build().depict(mol1, coloringMap1);
			graphics.drawImage(imgToAdd, padding*11 + 5*imageSize, (i+1)*padding + (i*1)*imageSize, null);
		}
		
        ImageIO.write(pageImage, "png", new File(BaseTestClass.TEST_OUTPUT_DIR,"imageGrid.png"));	
	}

	
	@Test
	public void generateTestImgsWithGlow() throws IOException{
		MoleculeDepictor [] depictors = new MoleculeDepictor[4];
		depictors[0] = new MoleculeDepictor.Builder().color(GradientFactory.getRainbowGradient()).build();
		depictors[1] = new MoleculeDepictor.Builder().color(GradientFactory.getRedBlueRedGradient()).build();
		depictors[2] = new MoleculeDepictor.Builder().color(BLUE_RED_LIGHT).build();
		depictors[3] = new MoleculeDepictor.Builder().color(GradientFactory.getRedBlueGradient()).build();
		
		BufferedImage pageImage = new BufferedImage(pageWidth, pageHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = pageImage.createGraphics();
		graphics.fillRect(0, 0, pageWidth, pageHeight);
		BufferedImage imgToAdd1, imgToAdd2;
		for(int i=0; i< depictors.length; i++){
			imgToAdd1 = depictors[i].depict(mol1, coloringMap1);
			graphics.fillRect(padding, (i+1)*padding + i*imageSize,imageSize,imageSize);
			graphics.drawImage(imgToAdd1, padding, (i+1)*padding + i*imageSize, null);
			
			
			imgToAdd2 = depictors[i].depict(mol2, coloringMap2);
			graphics.fillRect(padding*2 + imageSize, (i+1)*padding + i*imageSize, imageSize,imageSize);
			graphics.drawImage(imgToAdd2, padding*2 + imageSize, (i+1)*padding + i*imageSize, null);
			
			BufferedImage gradient = drawGradient(imageSize, depictors[i].getColorGradient());
			graphics.drawImage(gradient, 
					padding, (i+1)*padding + (i+1)*imageSize,imageSize,padding,
					null);
			graphics.drawImage(gradient, 
					padding*2+imageSize, (i+1)*padding + (i+1)*imageSize,imageSize,padding,
					null);
		}
		
        ImageIO.write(pageImage, "png", new File(BaseTestClass.TEST_OUTPUT_DIR, "outputWithGlow.png"));	
	}
	
	@Test
	public void generateLargerImages() throws IOException {
		int height = 700, width=600;
		MoleculeDepictor mp = new MoleculeDepictor.Builder()
			// .color(GradientFactory.getRedBlueRedGradient())
			.color(GradientFactory.getRainbowGradient())
			.w(width)
			.h(height)
			.bg(Color.LIGHT_GRAY)
			.build();
		
		BufferedImage fullImg = new BufferedImage(width+padding*2,height+padding*4, MoleculeDepictor.getImageType());
		Graphics2D graphics = fullImg.createGraphics();
		
		// Add depiction
		BufferedImage img = mp.depict(mol1, coloringMap1);
		graphics.drawImage(img, padding, padding, width, height,null);

		// Add gradient
		BufferedImage gradient = drawGradient(width, mp.getColorGradient());
		graphics.drawImage(gradient, 
			padding, 2*padding + height, width, padding,
			null);
		
        ImageIO.write(fullImg, "png", new File(BaseTestClass.TEST_OUTPUT_DIR, "large.png"));
	}
	
	@Test
	public void generateSmallerImages() throws IOException {
		int height = 200, width=200;
		MoleculeDepictor mp = new MoleculeDepictor.Builder()
			.w(width)
			.h(height)
			.color(GradientFactory.getRainbowGradient()).build();
		
		
		BufferedImage pageImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = pageImage.createGraphics();
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, width, height);
		
		BufferedImage img = mp.depict(mol1, coloringMap1);

		
		graphics.drawImage(img, 0,0,null);
		
        ImageIO.write(pageImage, "png", new File(BaseTestClass.TEST_OUTPUT_DIR, "small.png"));
	}

	@Test
	public void testIncorrectColorMap(){
		
		int height = 200, width=200;
		MoleculeDepictor mp = new MoleculeDepictor.Builder()
			.w(width)
			.h(height)
			.color(GradientFactory.getRainbowGradient()).build();
		
		// Incorrect keys
		try{
			mp.depict(mol1, ImmutableMap.of("1",1d,"2",5d));
			Assert.fail();
		} catch (IllegalArgumentException e){}

		// out of range keys
		try{
			mp.depict(mol1, ImmutableMap.of(-1,1d,100,5d));
			Assert.fail();
		} catch (IllegalArgumentException e){}


	}
	
	@Test
	public void generateStdImgs() throws IOException{
		MoleculeDepictor [] depictors = new MoleculeDepictor[4];
		depictors[0] = new MoleculeDepictor.Builder().showAtomNumbers(true).build(); 
		depictors[1] = new MoleculeDepictor.Builder().color(GradientFactory.getCustomGradient("[ { \"color\": \"#FF0004\", \"pos\": 0.306796 }, { \"color\": \"#00FF00\", \"pos\": 0.502913 }, { \"color\": \"#0000FF\", \"pos\": 0.702913 }]")).build(); 
		depictors[2] = new MoleculeDepictor.Builder().color(GradientFactory.getCustomGradient("[ { \"color\": \"#00FFFF\", \"pos\": 0.306796 }, { \"color\": \"#F2F2FF\", \"pos\": 0.495146 }, { \"color\": \"#FF00FF\", \"pos\": 0.708738 }]")).build(); 
		depictors[3] = new MoleculeDepictor.Builder().color(GradientFactory.getCustomGradient("[ { \"color\": \"#FB877D\", \"pos\": 0.000000 }, { \"color\": \"#FF0004\", \"pos\": 0.300000 }, { \"color\": \"#FFFF00\", \"pos\": 0.400000 }, { \"color\": \"#00FF00\", \"pos\": 0.506796 }, { \"color\": \"#00FFFF\", \"pos\": 0.601923 }, {\"color\": \"#0000FF\", \"pos\": 0.707692 }, { \"color\": \"#8080FF\", \"pos\": 1.000000 }]")).build(); 
		
		BufferedImage pageImage = new BufferedImage(pageWidth, pageHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = pageImage.createGraphics();
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, pageWidth, pageHeight);
		
		BufferedImage imgToAdd1, imgToAdd2;
		for(int i=0; i< depictors.length; i++){
			imgToAdd1 = depictors[i].depict(mol1, coloringMap1);
			graphics.drawImage(imgToAdd1, padding, (i+1)*padding + i*imageSize, null);
			
			imgToAdd2 = depictors[i].depict(mol1, coloringMap1);
			graphics.drawImage(imgToAdd2, padding*2 + imageSize, (i+1)*padding + i*imageSize, null);
			
			imgToAdd2 = depictors[i].depict(mol1, coloringMap1);
			graphics.drawImage(imgToAdd2, padding*3 + 2*imageSize, (i+1)*padding + (i*1)*imageSize, null);
		}
		
        ImageIO.write(pageImage, "png", new File(TEST_OUTPUT_DIR, "outputStd.png"));	
	}
	
	@Test
	public void generateStdFactoryImgs() throws IOException{
		MoleculeDepictor [] depictors = new MoleculeDepictor[4];
		depictors[0] = new MoleculeDepictor.Builder()
			.color(GradientFactory.getCustomGradient("[ { \"color\": \"#FF8082\", \"pos\": 0.000000 }, { \"color\":\"#FF0004\", \"pos\": 0.296367 }, { \"color\": \"#E9E7E9\", \"pos\": 0.500000 }, { \"color\": \"#0000FF\", \"pos\": 0.720000 }, { \"color\": \"#7F7FFF\", \"pos\": 1.000000 }]"))
			.showAtomNumbers(true)
			.scaleAtomNumbers(1.5)
			.build();
		depictors[1] = new MoleculeDepictor.Builder()
			.color(GradientFactory.getCustomGradient("[ { \"color\": \"#FF0004\", \"pos\": 0.306796 }, { \"color\": \"#00FF00\", \"pos\": 0.502913 }, { \"color\": \"#0000FF\", \"pos\": 0.702913 }]"))
			.build();
		depictors[2] = new MoleculeDepictor.Builder()
			.build(); 
		depictors[3] = new MoleculeDepictor.Builder()
			.color(GradientFactory.getCustomGradient("[ { \"color\": \"#FB877D\", \"pos\": 0.000000 }, { \"color\": \"#FF0004\", \"pos\": 0.300000 }, { \"color\": \"#FFFF00\", \"pos\": 0.400000 }, { \"color\": \"#00FF00\", \"pos\": 0.506796 }, { \"color\": \"#00FFFF\", \"pos\": 0.601923 }, { \"color\": \"#0000FF\", \"pos\": 0.707692 }, { \"color\": \"#8080FF\", \"pos\": 1.000000 }]"))
			.build(); 

		// OLD - get the bufferedImage and re-paint it in a new image
		
		Stopwatch watch = Stopwatch.createStarted();
		
		BufferedImage pageImage = new BufferedImage(pageWidth /3, pageHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = pageImage.createGraphics();
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, pageWidth /3, pageHeight);
		
		BufferedImage imgToAdd1;
		for(int i=0; i< depictors.length; i++){
			imgToAdd1 = depictors[i].depict(mol1, coloringMap1);
			graphics.drawImage(imgToAdd1, padding, (i+1)*padding + i*imageSize, null);
		}
		graphics.dispose();
		watch.stop();
		
        ImageIO.write(pageImage, "png", new File(BaseTestClass.TEST_OUTPUT_DIR, "outputStdFactory.png"));

		// NEW version
		Stopwatch watch2 = Stopwatch.createStarted();
		
		BufferedImage pageImageNew = new BufferedImage(pageWidth /3, pageHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics2 = pageImageNew.createGraphics();
		graphics2.setColor(Color.WHITE);
		graphics2.fillRect(0, 0, pageWidth /3, pageHeight);
		
		// BufferedImage imgToAdd1;
		for(int i=0; i< depictors.length; i++){
			Rectangle2D drawArea = new Rectangle2D.Double(padding,(i+1)*padding + i*imageSize,imageSize,imageSize);
			imgToAdd1 = depictors[i].depict(mol1, coloringMap1, pageImageNew, graphics2, drawArea);
		}
		watch2.stop();
		
        ImageIO.write(pageImage, "png", new File(BaseTestClass.TEST_OUTPUT_DIR, "outputStdFactory_new.png"));
	}
	
	public static BufferedImage drawGradient(int width, ColorGradient gradient) {
		BufferedImage image = new BufferedImage(width, 1, BufferedImage.TYPE_INT_ARGB);
		for(int i=0;i<width;i++) {
			double val = -1d + 2*((double)i)/width;
			Color color = Color.MAGENTA;
			if (gradient!=null) 
				color = gradient.getColor(val);
			image.setRGB(i, 0, color.getRGB());
		}
		return image;
	}


}

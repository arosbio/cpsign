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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.chem.io.out.depictors.MoleculeGradientDepictor;
import com.arosbio.chem.io.out.depictors.MoleculeSignificantSignatureDepictor;
import com.arosbio.chem.io.out.fields.ColorGradientField;
import com.arosbio.chem.io.out.fields.ColoredBoxField;
import com.arosbio.chem.io.out.fields.ColoredBoxField.BoxLocation;
import com.arosbio.chem.io.out.fields.ColoredBoxField.BoxShape;
import com.arosbio.chem.io.out.fields.HighlightExplanationField;
import com.arosbio.chem.io.out.fields.OrnamentField;
import com.arosbio.chem.io.out.fields.PValuesField;
import com.arosbio.chem.io.out.fields.PredictionIntervalField;
import com.arosbio.chem.io.out.fields.TitleField;
import com.arosbio.chem.io.out.image.CustomLayout;
import com.arosbio.chem.io.out.image.CustomLayout.Boarder;
import com.arosbio.chem.io.out.image.CustomLayout.Boarder.BoarderShape;
import com.arosbio.chem.io.out.image.CustomLayout.Margin;
import com.arosbio.chem.io.out.image.CustomLayout.Padding;
import com.arosbio.chem.io.out.image.Position.Vertical;
import com.arosbio.cheminf.ChemVAPClassifier;
import com.arosbio.cheminf.SignificantSignature;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.io.IOSettings;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.TestChemDataLoader;
import com.arosbio.testutils.UnitTestBase;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;

@Category(UnitTest.class)
public class TestMoleculeDepiction extends UnitTestBase{

	private static Map<String, Double> pvals;
	private static Map<Integer, Double> gradient;
	private static Set<Integer> sigSign;
	private final static int pageSize = 400;

	private static boolean SAVE_IN_TMP_DIR = false;
	static String imageOutputFolder = null;

	@BeforeClass
	public static void setup() throws IOException {
		// Set up the output directory
		if (SAVE_IN_TMP_DIR){
			Path dir = Files.createTempDirectory("image-dir");
			imageOutputFolder = dir.toAbsolutePath().toString();
		} else {
			// Create a directory 
			File dir = new File(new File("").getAbsoluteFile(), "testoutput");
			imageOutputFolder = dir.getPath();
			Files.createDirectories(dir.toPath());
		}
		

		pvals = new HashMap<>();
		pvals.put("mutagen", 0.8);
		pvals.put("nonmutagen", 0.7);

		sigSign = Sets.newHashSet(1, 2);
		gradient = new HashMap<>();
		gradient.put(1, 0.2);
		gradient.put(2, 0.3);
		gradient.put(3, 0.7);
		gradient.put(6, -0.9);
		gradient.put(7, -0.6);
	}

	// @Test
	public void generateExampleImageForDepict() throws Exception {
		MoleculeGradientDepictor gradDep = new MoleculeGradientDepictor();
		GradientFigureBuilder builder = new GradientFigureBuilder(gradDep);
		// Changed for generating a GitHub repo-image of the preferred size 
		builder.figureHeight(640);
		builder.figureWidth(1500);
		// OrnamentField grad = new ColorGradientField(((MoleculeGradientDepictor)builder.getDepictor()).getColorGradient());
		// builder.addFieldUnderImg(grad);


		ChemVAPClassifier predictor = (ChemVAPClassifier) ModelSerializer.loadChemPredictor(TestChemDataLoader.PreTrainedModels.CVAP_LIBLINEAR.toURI(), null);
		// ChemCPClassifier predictor = (ChemCPClassifier) ModelSerializer.loadChemPredictor(TestChemDataLoader.PreTrainedModels.ACP_CLF_LIBLINEAR.toURI(), null);
		IAtomContainer mol = sp.parseSmiles(TEST_SMILES_2); // getTestMol();
		SignificantSignature signature =  predictor.predictSignificantSignature(mol);

		builder.build(mol, signature.getAtomContributions()).saveToFile(new File(imageOutputFolder, "molGrad_test_2.png"));


	}
	
	@Test
	public void testSignSignCustomLegendWithPvals() throws IOException{
		SignificantSignatureFigureBuilder builder = new SignificantSignatureFigureBuilder(new MoleculeSignificantSignatureDepictor());
//		builder.addFieldOverImg(new TitleField("Some title.."));
		builder.addFieldUnderImg(new PValuesField(pvals));
		builder.addFieldUnderImg(new HighlightExplanationField(Color.BLUE, "this is the label"));
		builder.figureHeight(pageSize);
		builder.figureWidth(pageSize);
		builder.build(getTestMol(), sigSign).saveToFile(new File(imageOutputFolder, "signSignCustomWithPvals.png"));
	}
	
	@Test
	public void testMolGradCustomLegendWithPvals() throws IOException{
		GradientFigureBuilder builder = new GradientFigureBuilder(new MoleculeGradientDepictor());
		builder.addFieldOverImg(new TitleField("Some title.."));
		builder.addFieldUnderImg(new HighlightExplanationField(Color.BLUE, "this is the label"));
		builder.addFieldUnderImg(new PValuesField(pvals));
		builder.figureHeight(pageSize+100);
		builder.figureWidth(pageSize);
		Boarder molBoarder = new Boarder.Builder().shape(BoarderShape.RECTANGLE).stroke(new BasicStroke(3f)).color(Color.BLUE).build();
		builder.getDepictor().addLayout(new CustomLayout.Builder().padding(new Padding(10)).boarder(molBoarder).build());
		builder.build(getTestMol(), gradient).saveToFile(new File(imageOutputFolder, "molGradCustomWithPvals.png"));
	}
	
	@Test
	public void testNewMolDepictionBuilder_GRADIENT() throws Exception {
		GradientFigureBuilder builder = new GradientFigureBuilder(new MoleculeGradientDepictor());
		builder.getDepictor().setImageHeight(100);
		builder.getDepictor().setImageWidth(1000);
		Boarder molBoarder = new Boarder.Builder()
			.shape(BoarderShape.RECTANGLE)
			.stroke(new BasicStroke(3f))
			.color(Color.BLUE)
			.build();
		builder.getDepictor().addLayout(
			new CustomLayout.Builder()
				.padding(new Padding(10))
				.boarder(molBoarder)
				.margin(new Margin(0)).build());
		Boarder molBoarder2 = new Boarder.Builder().shape(BoarderShape.RECTANGLE).stroke(new BasicStroke(4f)).color(Color.PINK).build();
		builder.getDepictor().addLayout(new CustomLayout.Builder().boarder(molBoarder2).build());
		Boarder molBoarder3 = new Boarder.Builder().shape(BoarderShape.RECTANGLE).stroke(new BasicStroke(5f)).color(Color.GREEN).build();
		builder.getDepictor().addLayout(new CustomLayout.Builder().boarder(molBoarder3).build());
		TitleField tit = new TitleField("<b>TITLE</b>");
		tit.setAlignment(Vertical.LEFT_ADJUSTED);
		tit.underlineText();
		builder.addFieldOverImg(tit);
		OrnamentField pvalsF = new PValuesField(pvals);
		CustomLayout pvalsFLayout = new CustomLayout.Builder()
			.padding(new Padding(10))
			.boarder(new Boarder.Builder()
				.shape(BoarderShape.ROUNDED_RECTANGLE)
				.color(Color.CYAN)
				.stroke(new BasicStroke(2f))
				.build())
			.build();
		pvalsF.addLayout(pvalsFLayout);
		
		// pvalsFLayout.setBoarder(new Boarder(BoarderShape.ROUNDED_RECTANGLE, new BasicStroke(2f)));
		// pvalsFLayout.getBoarder().setColor(Color.CYAN);
		pvalsF.setAlignment(Vertical.CENTERED);
		builder.addFieldUnderImg(pvalsF);
		OrnamentField region = new PredictionIntervalField(Range.closed(-.35, 14.3),0.8,"Activity");
		region.setAlignment(Vertical.RIGHT_ADJUSTED);
		builder.addFieldUnderImg(region);
		OrnamentField region2 = new PredictionIntervalField(Range.closed(-.35, 14.3),0.8);
		region2.addLayout(new CustomLayout.Builder()
			.boarder(new Boarder.Builder().stroke(new BasicStroke(3.f)).build())
			.build());
		
		builder.addFieldUnderImg(new HighlightExplanationField(Color.BLUE));
		HighlightExplanationField field = new HighlightExplanationField(Color.CYAN,"mutagen");
		field.setBoxLocation(BoxLocation.RIGHT);
		field.setAlignment(Vertical.RIGHT_ADJUSTED);
		field.setBoxShape(BoxShape.TRIANGLE);
		builder.addFieldUnderImg(field);
		ColoredBoxField circleLARGE = new ColoredBoxField(Color.MAGENTA, "This is a very long long <b>looooong</b> text lorem ipsum ipsum dsagadgalkn sdad sdf");
//		HighlightExplanationField circle = new HighlightExplanationField(Color.CYAN,"nonmutagen");
		circleLARGE.setBoxShape(BoxShape.CIRCLE);
		circleLARGE.setBoxSize(200);
		builder.addFieldOverImg(circleLARGE);
		HighlightExplanationField circleSmall = new HighlightExplanationField(Color.CYAN,"nonmutagen");
		circleSmall.setBoxShape(BoxShape.CIRCLE);
		circleSmall.setBoxSize(6);
		builder.addFieldOverImg(circleSmall);
		builder.addFieldUnderImg(region2);
		OrnamentField grad = new ColorGradientField(((MoleculeGradientDepictor)builder.getDepictor()).getColorGradient());
		
		builder.addFieldUnderImg(grad);
		LoggerUtils.setDebugMode();
		MoleculeFigure depiction = builder.build(getTestMol(), gradient);
		depiction.saveToFile(new File(imageOutputFolder, "gradientDepiction.png"));
	}

	@Test
	public void testRoundedBoarder() throws Exception {
		
		
		final float dash1[] = {6.0f, 3.0f, 2.0f, 3.0f}; 
	    final BasicStroke dashed =
	        new BasicStroke(4.0f,
	                        BasicStroke.CAP_BUTT,
	                        BasicStroke.JOIN_MITER,
	                        10.0f, dash1, 0.0f);
		Boarder boarder = new Boarder.Builder()
			.color(Color.RED)
			.shape(BoarderShape.ROUNDED_RECTANGLE)
			.stroke(dashed)
			.build();
		
		CustomLayout orn = new CustomLayout.Builder()
			.padding(new Padding(3))
			.boarder(boarder)
			.margin(new Margin(1)).build();
		BufferedImage imgToEnclose = new BufferedImage(10, 10, IOSettings.BUFFERED_IMAGE_TYPE);
		Graphics2D g2d = imgToEnclose.createGraphics();
		g2d.setColor(Color.GRAY);
		g2d.fillRect(0, 0, imgToEnclose.getWidth(), imgToEnclose.getHeight());
		g2d.dispose();
		BufferedImage withBoarder = orn.addLayout(imgToEnclose);
		ImageIO.write(withBoarder, "png", new File(imageOutputFolder,"withRoundedBoarder.png"));
	}
	
	@Test
	public void testRectangleBoarder() throws Exception {
		CustomLayout orn = new CustomLayout.Builder()
			.padding(new Padding(1))
			.boarder(new Boarder.Builder().build())
			.margin(new Margin(1)).build();

		BufferedImage imgToEnclose = new BufferedImage(7, 7, IOSettings.BUFFERED_IMAGE_TYPE);
		Graphics2D g2d = imgToEnclose.createGraphics();
		g2d.setColor(Color.WHITE);
		g2d.fillRect(1, 1, imgToEnclose.getWidth()-2, imgToEnclose.getHeight()-2);
		g2d.dispose();
		BufferedImage withBoarder = orn.addLayout(imgToEnclose);
		ImageIO.write(withBoarder, "png", new File(imageOutputFolder,"withRectangleBoarder.png"));
	}
	
	@Test
	public void testLine() throws Exception {
		BufferedImage img = new BufferedImage(5, 5, IOSettings.BUFFERED_IMAGE_TYPE);
		Graphics2D g = img.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, 10, 10);
		g.setColor(Color.RED);
		g.setStroke(new BasicStroke(3f));
		g.drawLine(3, 3, 3, 15);
		g.dispose();
		ImageIO.write(img, "png", new File(imageOutputFolder, "line.png"));
	}
	

}
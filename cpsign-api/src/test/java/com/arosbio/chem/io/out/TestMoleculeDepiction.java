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
import com.arosbio.cheminf.ChemCPClassifier;
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

	private static boolean SAVE_IN_TMP_DIR = true;
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

//	@Test
//	public void testSetup() throws Exception {
//		//		try{
//		//			MolImageDepictor.getGradientDepictor(null);
//		//			Assert.fail("Should fail when no gradient given");
//		//		} catch(IllegalArgumentException e){
//		//			Assert.assertTrue(e.getMessage().contains("gradient"));
//		//		}
//
//		new GradientDepictorOld(GradientFactory.getDefaultBloomGradient());
//
//		Logger imageLogger = (Logger) LoggerFactory.getLogger(MolImageWriter.class);
//		imageLogger.setLevel(Level.DEBUG);
//		LoggerUtils.addStreamAppenderToLogger(imageLogger, System.out, "%m%n");
//
//		new SignificantSignatureDepictorOld();
//	}

	// String imageFolder =new File(new File("").getAbsoluteFile(), "test/testoutput/generatedImages").getAbsolutePath();

//	@Test
//	public void testGenerateNormalLegend() throws IOException{
//		GradientDepictorOld writer = new GradientDepictorOld(null); //.getGradientDepictor(GradientFactory.getDefaultBloomGradient());
//		writer.setImageWidth(pageSize);
//		writer.setImageHeight(pageSize);
//		//		writer.generateLegend();
//		LoggerUtils.setDebugMode();
//		BufferedImage img = writer.getGradientLegend();
//		ImageIO.write(img, "png", new File(imageFolder, "colorLegend.png"));
//		original.println(systemOutRule.getLog());
//	}
//
//	private IAtomContainer getMolecule() throws Exception {
//		SmilesParser sp = new SmilesParser(SilentChemObjectBuilder.getInstance());
//		return sp.parseSmiles("CC1=CN=C(C(=C1OC)C)CS(=O)C2=NC3=C(N2)C=C(C=C3)OC");
//	}
//
//	// ===========================================
//	//
//	// GRADIENT DEPICTOR
//	//
//	// ===========================================
//
////	@Test
////	public void testGenerateGradientWithLegend() throws Exception{
////		LoggerUtils.setDebugMode();
////		IAtomContainer mol = getMolecule();
////
////		GradientDepictor writer = new GradientDepictor();
////
////		writer.setImageWidth(pageSize);
////		writer.setImageHeight(pageSize);
////		ImageIO.write(writer.depict(mol, gradient, pvals, "This is my custom text"), 
////				"png", new File(imageFolder, "gradientWithLegendCustomText.png"));
//////		original.println(systemOutRule.getLog());
////	}
//
//	@Test
//	public void testGradientDepictorNoLabel() throws Exception{
//		IAtomContainer mol = getMolecule();
//
//		GradientDepictorOld writer = new GradientDepictorOld();
//		writer.setUseLegend(false);
//		writer.setUseVerboseLegend(false);
//		writer.setImageWidth(pageSize);
//		writer.setImageHeight(pageSize);
//		LoggerUtils.setDebugMode();
//		ImageIO.write(
//				writer.depict(mol, gradient, pvals, "This is my custom <b>Leg<i>end</i></b>"),  
//				"png", 
//				new File(imageFolder, "gradient_NoLabel.png"));
//	}
//
//	@Test
//	public void testGradientDepictorCustomLabel() throws Exception{
//		IAtomContainer mol = getMolecule();
//
//		GradientDepictorOld writer = new GradientDepictorOld();
//		writer.setUseLegend(true);
//		writer.setUseVerboseLegend(false);
//		writer.setImageWidth(pageSize);
//		writer.setImageHeight(pageSize);
//		LoggerUtils.setDebugMode();
//		ImageIO.write(
//				writer.depict(mol, gradient, pvals, "This is my custom <b>Leg<i>end</i></b>"), //"This is my <b>Leg<i>end</i></b> which is custom " 
//				"png", 
//				new File(imageFolder, "gradient_CustomLegend.png"));
//		//		original.println(systemOutRule.getLog());
//	}
//
//	@Test
//	public void testGradientDepictorCustomLabelWithPVals() throws Exception{
//		IAtomContainer mol = getMolecule();
//
//		GradientDepictorOld writer = new GradientDepictorOld();
//		writer.setUseLegend(true);
//		writer.setUseVerboseLegend(true);
//		writer.setImageWidth(pageSize);
//		writer.setImageHeight(pageSize);
//		LoggerUtils.setDebugMode();
//		ImageIO.write(
//				writer.depict(mol, gradient, pvals, "This is my custom <b>Leg<i>end</i></b>"), //"This is my <b>Leg<i>end</i></b> which is custom " 
//				"png", 
//				new File(imageFolder, "gradient_PvalsAndCustomLegend.png"));
//	}
//
//	@Test
//	public void testGradientDepictorStandardWithPVals() throws Exception{
//		IAtomContainer mol = getMolecule();
//
//		GradientDepictorOld writer = new GradientDepictorOld();
//		writer.setUseLegend(true);
//		writer.setUseVerboseLegend(true);
//		writer.setImageWidth(pageSize);
//		writer.setImageHeight(pageSize);
//		LoggerUtils.setDebugMode();
//		ImageIO.write(
//				writer.depict(mol, gradient, pvals), //"This is my <b>Leg<i>end</i></b> which is custom " 
//				"png", 
//				new File(imageFolder, "gradient_StandardLabelPvals.png"));
//	}
//
//	// ===========================================
//	//
//	// SIGNIFICANT SIGNATURE
//	//
//	// ===========================================
//
//	@Test
//	public void testSignSignNoLegend() throws IOException{
//		SignificantSignatureDepictorOld writer = new SignificantSignatureDepictorOld();
//		writer.setUseLegend(false);
//		writer.centerLegend = false;
//		writer.setImageHeight(pageSize);
//		writer.setImageWidth(pageSize);
//		BufferedImage img = writer.depict(getTestMol(), sigSign, pvals);
//		ImageIO.write(img, "png", new File(imageFolder, "significant_NoLegend.png"));
//		
//		//		original.println(systemOutRule.getLog());
//	}
//	
//	@Test
//	public void testSignSignCustomLegend() throws IOException{
//		SignificantSignatureDepictorOld writer = new SignificantSignatureDepictorOld();
//		writer.setUseLegend(true);
//		writer.centerLegend = false;
//		writer.setImageHeight(pageSize);
//		writer.setImageWidth(pageSize);
//		BufferedImage img = writer.depict(getTestMol(), sigSign, pvals);
//		ImageIO.write(img, "png", new File(imageFolder, "significant_NoLegend.png"));
//	}

	// @Test
	public void generateImageForDepict() throws Exception {
		MoleculeGradientDepictor gradDep = new MoleculeGradientDepictor();
		gradDep.setImageWidth(1000);
		gradDep.setImageHeight(1000);
		GradientFigureBuilder builder = new GradientFigureBuilder(gradDep);
		OrnamentField grad = new ColorGradientField(((MoleculeGradientDepictor)builder.getDepictor()).getColorGradient());
		builder.addFieldUnderImg(grad);


		ChemCPClassifier predictor = (ChemCPClassifier) ModelSerializer.loadChemPredictor(TestChemDataLoader.PreTrainedModels.ACP_CLF_LIBLINEAR.toURI(), null);
		IAtomContainer mol = sp.parseSmiles(TEST_SMILES_2); // getTestMol();
		SignificantSignature signature =  predictor.predictSignificantSignature(mol);

		builder.build(mol, signature.getAtomContributions()).saveToFile(new File(imageOutputFolder, "molGrad_test_2.png"));;


	}
	
	@Test
	public void testSignSignCustomLegendWithPvals() throws IOException{
		SignificantSignatureFigureBuilder builder = new SignificantSignatureFigureBuilder(new MoleculeSignificantSignatureDepictor());
//		builder.addFieldOverImg(new TitleField("Some title.."));
		builder.addFieldUnderImg(new PValuesField(pvals));
		builder.addFieldUnderImg(new HighlightExplanationField(Color.BLUE, "this is the label"));
		builder.setFigureHeight(pageSize);
		builder.setFigureWidth(pageSize);
		builder.build(getTestMol(), sigSign).saveToFile(new File(imageOutputFolder, "signSignCustomWithPvals.png"));
	}
	
	@Test
	public void testMolGradCustomLegendWithPvals() throws IOException{
		GradientFigureBuilder builder = new GradientFigureBuilder(new MoleculeGradientDepictor());
		builder.addFieldOverImg(new TitleField("Some title.."));
		builder.addFieldUnderImg(new HighlightExplanationField(Color.BLUE, "this is the label"));
		builder.addFieldUnderImg(new PValuesField(pvals));
		builder.setFigureHeight(pageSize+100);
		builder.setFigureWidth(pageSize);
		Boarder molBoarder = new Boarder(BoarderShape.RECTANGLE, new BasicStroke(3f), Color.BLUE);
		builder.getDepictor().addLayout(new CustomLayout(new Padding(10), molBoarder, new Margin(0)));
		builder.build(getTestMol(), gradient).saveToFile(new File(imageOutputFolder, "molGradCustomWithPvals.png"));
	}
	
	@Test
	public void testSignSignStandardWithPvals() throws IOException{
		
	}
	
	@Test
	public void testNewMolDepictionBuilder_GRADIENT() throws Exception {
		GradientFigureBuilder builder = new GradientFigureBuilder(new MoleculeGradientDepictor());
		builder.getDepictor().setImageHeight(100);
		builder.getDepictor().setImageWidth(1000);
		Boarder molBoarder = new Boarder(BoarderShape.RECTANGLE, new BasicStroke(3f), Color.BLUE);
		builder.getDepictor().addLayout(new CustomLayout(new Padding(10), molBoarder, new Margin(0)));
		Boarder molBoarder2 = new Boarder(BoarderShape.RECTANGLE, new BasicStroke(4f), Color.PINK);
		builder.getDepictor().addLayout(new CustomLayout(new Padding(0), molBoarder2, new Margin(0)));
		Boarder molBoarder3 = new Boarder(BoarderShape.RECTANGLE, new BasicStroke(5f), Color.GREEN);
		builder.getDepictor().addLayout(new CustomLayout(new Padding(0), molBoarder3, new Margin(0)));
		TitleField tit = new TitleField("<b>TITLE</b>");
		tit.setAlignment(Vertical.LEFT_ADJUSTED);
		tit.underlineText();
		builder.addFieldOverImg(tit);
		OrnamentField pvalsF = new PValuesField(pvals);
		CustomLayout pvalsFLayout = new CustomLayout(new Padding(10), new Boarder(), new Margin(0));
		pvalsF.addLayout(pvalsFLayout);
		
		pvalsFLayout.setBoarder(new Boarder(BoarderShape.ROUNDED_RECTANGLE, new BasicStroke(2f)));
		pvalsFLayout.getBoarder().setColor(Color.CYAN);
		pvalsF.setAlignment(Vertical.CENTERED);
		builder.addFieldUnderImg(pvalsF);
		OrnamentField region = new PredictionIntervalField(Range.closed(-.35, 14.3),0.8,"Activity");
		region.setAlignment(Vertical.RIGHT_ADJUSTED);
		builder.addFieldUnderImg(region);
		OrnamentField region2 = new PredictionIntervalField(Range.closed(-.35, 14.3),0.8);
		region2.addLayout(new CustomLayout(new Padding(0), new Boarder(new BasicStroke(3.f)), new Margin(0)));
		
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
		
//		grad.setBoarder(new Boarder());
		builder.addFieldUnderImg(grad);
		LoggerUtils.setDebugMode();
		MoleculeFigure depiction = builder.build(getTestMol(), gradient);
		depiction.saveToFile(new File(imageOutputFolder, "gradientDepiction.png"));
//		original.println(systemOutRule.getLog());
	}

	@Test
	public void testRoundedBoarder() throws Exception {
		Boarder boarder = new Boarder();
		boarder.setColor(Color.RED);
		boarder.setShape(BoarderShape.ROUNDED_RECTANGLE);
		
		CustomLayout orn = new CustomLayout(new Padding(3),boarder, new Margin(1));
		
		final float dash1[] = {6.0f, 3.0f, 2.0f, 3.0f}; //, 13f, 1f
	    final BasicStroke dashed =
	        new BasicStroke(4.0f,
	                        BasicStroke.CAP_BUTT,
	                        BasicStroke.JOIN_MITER,
	                        10.0f, dash1, 0.0f);
		boarder.setStroke(dashed);
		BufferedImage imgToEnclose = new BufferedImage(10, 10, IOSettings.BUFFERED_IMAGE_TYPE);
		Graphics2D g2d = imgToEnclose.createGraphics();
		g2d.setColor(Color.GRAY);
		g2d.fillRect(0, 0, imgToEnclose.getWidth(), imgToEnclose.getHeight());
		g2d.dispose();
		BufferedImage withBoarder = orn.addLayout(imgToEnclose);
//		ImageIO.write(imgToEnclose, "png", new File(imageFolder,"noBoarder.png"));
		ImageIO.write(withBoarder, "png", new File(imageOutputFolder,"withRoundedBoarder.png"));
//		printLogs();
	}
	
	@Test
	public void testRectangleBoarder() throws Exception {
		CustomLayout orn = new CustomLayout(new Padding(1), new Boarder(), new Margin(1));
//		orn.setBoarder(new Boarder()); // default boarder
		
//		Boarder boarder = new Boarder();
//		boarder.setColor(Color.RED);
//		boarder.setMargin(2);
//		boarder.setStroke(new BasicStroke(4f));
//		boarder.setShape(BoarderShape.ROUNDED_REACTANGLE);
//		boarder.setMargin(-1);
//		final float dash1[] = {6.0f, 3.0f, 2.0f, 3.0f}; //, 13f, 1f
//	    final BasicStroke dashed =
//	        new BasicStroke(1.0f,
//	                        BasicStroke.CAP_BUTT,
//	                        BasicStroke.JOIN_MITER,
//	                        10.0f, dash1, 0.0f);
//		boarder.setStroke(dashed);
		BufferedImage imgToEnclose = new BufferedImage(7, 7, IOSettings.BUFFERED_IMAGE_TYPE);
		Graphics2D g2d = imgToEnclose.createGraphics();
		g2d.setColor(Color.WHITE);
		g2d.fillRect(1, 1, imgToEnclose.getWidth()-2, imgToEnclose.getHeight()-2);
		g2d.dispose();
		BufferedImage withBoarder = orn.addLayout(imgToEnclose);
//		ImageIO.write(imgToEnclose, "png", new File(imageFolder,"noBoarder.png"));
		ImageIO.write(withBoarder, "png", new File(imageOutputFolder,"withRectangleBoarder.png"));
//		printLogs();
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
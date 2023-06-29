/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem.io.out.image;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import com.arosbio.chem.io.out.image.RendererTemplate.MolRendering;
import com.arosbio.chem.io.out.image.RendererTemplate.RenderInfo;
import com.arosbio.chem.io.out.image.fields.ColorGradientField;
import com.arosbio.chem.io.out.image.fields.ColoredBoxField;
import com.arosbio.chem.io.out.image.fields.ColoredBoxField.BoxLocation;
import com.arosbio.chem.io.out.image.fields.ColoredBoxField.BoxShape;
import com.arosbio.chem.io.out.image.fields.HighlightExplanationField;
import com.arosbio.chem.io.out.image.fields.PValuesField;
import com.arosbio.chem.io.out.image.fields.PredictionIntervalField;
import com.arosbio.chem.io.out.image.fields.ProbabilityField;
import com.arosbio.chem.io.out.image.fields.TitleField;
import com.arosbio.chem.io.out.image.layout.CustomLayout;
import com.arosbio.chem.io.out.image.layout.CustomLayout.Boarder;
import com.arosbio.chem.io.out.image.layout.CustomLayout.Boarder.BoarderShape;
import com.arosbio.chem.io.out.image.layout.CustomLayout.Margin;
import com.arosbio.chem.io.out.image.layout.CustomLayout.Padding;
import com.arosbio.chem.io.out.image.layout.Layout;
import com.arosbio.chem.io.out.image.layout.Position.Vertical;
import com.arosbio.cheminf.ChemCPClassifier;
import com.arosbio.cheminf.ChemVAPClassifier;
import com.arosbio.cheminf.SignificantSignature;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.color.gradient.ColorGradient;
import com.arosbio.color.gradient.GradientFactory;
import com.arosbio.io.IOSettings;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.TestChemDataLoader;
import com.arosbio.testutils.TestChemDataLoader.PreTrainedModels;
import com.arosbio.testutils.UnitTestBase;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;

@Category(UnitTest.class)
public class TestMoleculeDepiction extends UnitTestBase {


	private static Map<String, Double> pValues;
	private static Map<Integer, Double> gradient;
	private static Set<Integer> sigSign;


	private final static int pageSize = 400;
	private static RenderInfo info;
	private static Set<String> modelLabels;

	private static boolean SAVE_IN_TMP_DIR = true;
	static String imageOutputFolder = null;

	@BeforeClass
	public static void setup() 
		throws IOException, InvalidKeyException, URISyntaxException, IllegalStateException, NullPointerException, CDKException {
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

		ChemCPClassifier acp = (ChemCPClassifier) ModelSerializer.loadChemPredictor(PreTrainedModels.ACP_CLF_LIBLINEAR.toURI(), null);
		modelLabels = acp.getLabelsSet();
		IAtomContainer mol = getTestMol();
		info = new RenderInfo.Builder(mol, acp.predictSignificantSignature(mol))
			.pValues(acp.predict(mol))
			.predictionInterval(Range.closed(-.35, 14.3), 0.8)
			.build();

		pValues = new HashMap<>();
		pValues.put("mutagen", 0.8);
		pValues.put("nonmutagen", 0.7);

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
		AtomContributionRenderer template = new AtomContributionRenderer.Builder().colorScheme(null)
			.height(640)
			.width(1500)
			.build();


		ChemVAPClassifier predictor = (ChemVAPClassifier) ModelSerializer.loadChemPredictor(TestChemDataLoader.PreTrainedModels.CVAP_LIBLINEAR.toURI(), null);
		IAtomContainer mol = sp.parseSmiles(TEST_SMILES_2);
		SignificantSignature signature =  predictor.predictSignificantSignature(mol);

		template.render(new RenderInfo.Builder(mol, signature).build())
		.saveToFile(new File(imageOutputFolder, "molGrad_test_2.png"));


	}

	int width = 300, height=600;

	@Test
	public void testAtomContribution() throws Exception {
		
		AtomContributionRenderer template = new AtomContributionRenderer.Builder()
			.width(width)
			.height(height)
			.defaultFont(FontFactory.plain(13))
			.addFieldOverMol(new TitleField.Builder().underline(true).build())
			.addFieldOverMol(new ColoredBoxField.Builder(Color.CYAN, "Here is some text above the field").layout(new CustomLayout.Builder().padding(new Padding(5)).boarder(new Boarder.Builder().color(Color.DARK_GRAY).stroke(new BasicStroke(2f)).build()).build()).build())
			.addFieldOverMol(new ColorGradientField.Builder(GradientFactory.getCyanMagenta()).displayPlusMinus(true).layout(new CustomLayout.Builder().padding(new Padding(5, 10)).build()).build())

			.molLayouts(new CustomLayout.Builder()
				.boarder(new Boarder.Builder().color(Color.BLUE).stroke(new BasicStroke(3f)).shape(BoarderShape.ROUNDED_RECTANGLE).build())
				.margin(new Margin(10)).build(),
				new CustomLayout.Builder()
				.boarder(new Boarder.Builder().color(Color.RED).stroke(new BasicStroke(2f)).shape(BoarderShape.RECTANGLE).build()).build()
				)

			
			.addFieldUnderMol(
				new ColoredBoxField
					.Builder(Color.PINK, "something below the molecule field, which has many rows so there might be several line-breaks, wonder how this may work - or possibly it cannot???")
					.alignment(Vertical.CENTERED)
					.layout(new CustomLayout.Builder().boarder(new Boarder.Builder().build()).build()) //color(Color.RED)
					.build()
					)
			.addFieldUnderMol(new HighlightExplanationField.Builder(Color.GREEN, "mutagen-label").build())
			
			.addFieldUnderMol(new PredictionIntervalField.Builder(0.8).alignment(Vertical.CENTERED).font(FontFactory.bold(10)).property("cLogD").precision(4).layout(new CustomLayout.Builder().boarder(new Boarder.Builder().color(Color.PINK).build()).build()).build())
			.addFieldUnderMol(new PredictionIntervalField.Builder(0.8)
				.alignment(Vertical.CENTERED)
				.font(FontFactory.bold(10))
				.splitInto2Lines(false)
				.precision(5)
				.layout(new CustomLayout.Builder().boarder(new Boarder.Builder().color(Color.PINK).build()).build())
				.build())
			//.padding(new Padding(5))
			.addFieldUnderMol(new ColorGradientField.Builder(GradientFactory.getBlueRedGradient()).displayPlusMinus(false).build())
			.addFieldUnderMol(new ProbabilityField.Builder(Arrays.asList("label0", "label-2-this-is-longer!","some-third-label")).build())
			.addFieldUnderMol(new PValuesField.Builder(pValues.keySet()).build())
			.build();
		IAtomContainer mol = getTestMol();
		// mol.setTitle("N-acetyl-p-aminophenol");
		MolRendering rendering = template.render(new RendererTemplate.RenderInfo.Builder(mol, 
			new SignificantSignature.Builder(new ArrayList<>())
			.atomContributions(gradient).build())
			.predictionInterval(Range.closed(-1.25312, 125.298761), .88)
			.pValues(pValues)
			.probabilities(Map.of("label0", .97, "label-2-this-is-longer!", .7889, "some-third-label",1.0))
			.build());
		
		rendering.saveToFile(new File(imageOutputFolder, "atom_contributions_new_api.png"));

		printLogs();
	}

	@Test
	public void testSignificantSign() throws Exception {
		
		RendererTemplate<?> template = new SignificantSignatureRenderer.Builder()
			.width(width)
			.height(height)
			.defaultFont(FontFactory.plain(13))
			.background(Color.WHITE)
			.addFieldOverMol(new TitleField.Builder().underline(true).build())
			.addFieldOverMol(new ColoredBoxField.Builder(Color.CYAN, "Here is some text above the field").build())
			.addFieldOverMol(new ColorGradientField.Builder(GradientFactory.getCyanMagenta()).displayPlusMinus(true).layout(new CustomLayout.Builder().padding(new Padding(5, 10)).build()).build())

			.molLayouts(new CustomLayout.Builder()
				.boarder(new Boarder.Builder().color(Color.BLUE).stroke(new BasicStroke(3f)).shape(BoarderShape.ROUNDED_RECTANGLE).build())
				.margin(new Margin(10)).build(),
				new CustomLayout.Builder()
				.boarder(new Boarder.Builder().color(Color.RED).stroke(new BasicStroke(2f)).shape(BoarderShape.RECTANGLE).build()).build()
				)

			.addFieldUnderMol(
				new ColoredBoxField
					.Builder(Color.PINK, "something below the molecule field, which has many rows so there might be several line-breaks, wonder how this may work - or possibly it cannot???")
					.alignment(Vertical.CENTERED)
					.layout(new CustomLayout.Builder().boarder(new Boarder.Builder().build()).build()) //color(Color.RED)
					.build()
					)
			.addFieldUnderMol(new HighlightExplanationField.Builder(Color.GREEN, "mutagen-label").build())
			
			.addFieldUnderMol(new PredictionIntervalField.Builder(0.8).alignment(Vertical.CENTERED).font(FontFactory.bold(10)).property("cLogD").precision(4).layout(new CustomLayout.Builder().boarder(new Boarder.Builder().color(Color.PINK).build()).build()).build())
			.addFieldUnderMol(new PredictionIntervalField.Builder(0.8).alignment(Vertical.CENTERED).font(FontFactory.bold(10)).splitInto2Lines(false).precision(5).layout(new CustomLayout.Builder().padding(new Padding(5)).boarder(new Boarder.Builder().color(Color.PINK).build()).build()).build())
			.addFieldUnderMol(new ColorGradientField.Builder(GradientFactory.getBlueRedGradient()).displayPlusMinus(false).build())
			.addFieldUnderMol(new ProbabilityField.Builder(Arrays.asList("label0", "label-2-this-is-longer!","some-third-label")).build())
			.addFieldUnderMol(new PValuesField.Builder(pValues.keySet()).build())
			.build();
		IAtomContainer mol = getTestMol();
		// mol.setTitle("N-acetyl-p-aminophenol");
		MolRendering rendering = template.render(new RendererTemplate.RenderInfo.Builder(mol, 
			new SignificantSignature.Builder(new ArrayList<>())
			.atomContributions(gradient).atoms(sigSign).build())
			.predictionInterval(Range.closed(-1.25312, 125.298761), .88)
			.pValues(pValues)
			.probabilities(Map.of("label0", .97, "label-2-this-is-longer!", .7889, "some-third-label",1.0))
			.build());
		
		rendering.saveToFile(new File(imageOutputFolder, "sign_sign_new_api.png"));

		printLogs();
	}
	
	@Test
	public void testSignSignCustomLegendWithPvals() throws IOException{
		new SignificantSignatureRenderer.Builder()
			.width(pageSize)
			.height(pageSize)
			.molLayout(new Padding(5))
			.addFieldUnderMol(new PValuesField.Builder(modelLabels).build())
			.addFieldUnderMol(new HighlightExplanationField.Builder(Color.BLUE, "this is the label").build())
			.build()
			.render(info)
			.saveToFile(new File(imageOutputFolder, "signSignCustomWithPvals.png"));
	}
	
	@Test
	public void testMolGradCustomLegendWithPvals() throws IOException{
		Boarder molBoarder = new Boarder.Builder().shape(BoarderShape.RECTANGLE).stroke(new BasicStroke(3f)).color(Color.BLUE).build();
		Layout l = new CustomLayout.Builder().padding(new Padding(10)).boarder(molBoarder).build();
		new AtomContributionRenderer.Builder()
			.width(pageSize)
			.height(pageSize+100)
			.addFieldOverMol(new TitleField.Builder().build())
			.addFieldUnderMol(new HighlightExplanationField.Builder(Color.BLUE, "this is the label").build())
			.addFieldUnderMol(new PValuesField.Builder(modelLabels).build())
			.molLayout(l)
			.build()
			.render(info)
			.saveToFile(new File(imageOutputFolder, "molGradCustomWithPvals.png"));
	}
	
	@Test
	public void testNewMolDepictionBuilder_GRADIENT() throws Exception {
		Boarder molBoarder = new Boarder.Builder()
			.shape(BoarderShape.RECTANGLE)
			.stroke(new BasicStroke(3f))
			.color(Color.BLUE)
			.build();
		Boarder molBoarder2 = new Boarder.Builder()
			.shape(BoarderShape.RECTANGLE)
			.stroke(new BasicStroke(4f))
			.color(Color.PINK)
			.build();
		Boarder molBoarder3 = new Boarder.Builder()
			.shape(BoarderShape.RECTANGLE)
			.stroke(new BasicStroke(5f))
			.color(Color.GREEN)
			.build();
		
		
		ColorGradient grad = GradientFactory.getDefaultBloomGradient();
		AtomContributionRenderer.Builder bldr = new AtomContributionRenderer.Builder()
			.colorScheme(grad)
			.width(1000)
			.height(1000)
			.molLayouts(new CustomLayout.Builder()
				.padding(new Padding(10))
				.boarder(molBoarder)
				.margin(new Margin(0)).build(),
				new CustomLayout.Builder().boarder(molBoarder2).build(),
				new CustomLayout.Builder().boarder(molBoarder3).build()
			)
			.addFieldOverMol(new TitleField.Builder().alignment(Vertical.LEFT_ADJUSTED).underline(true).build())
			
			.addFieldOverMol(new ColoredBoxField.Builder(Color.MAGENTA, "This is a very long long <b>looooong</b> text lorem ipsum ipsum dsagadgalkn sdad sdf")
			.boxShape(BoxShape.CIRCLE).boxSize(50).build())
			.addFieldOverMol(new HighlightExplanationField.Builder(Color.CYAN,"nonmutagen").boxShape(BoxShape.CIRCLE).boxSize(6).build())
			.addFieldUnderMol(new PValuesField.Builder(modelLabels)
				.alignment(Vertical.CENTERED)
				.layout(new CustomLayout.Builder()
					.padding(new Padding(10))
					.boarder(new Boarder.Builder()
						.shape(BoarderShape.ROUNDED_RECTANGLE)
						.color(Color.CYAN)
						.stroke(new BasicStroke(2f))
						.build())
					.build())
				.build()
			);

		bldr.addFieldUnderMol(new PredictionIntervalField.Builder(0.8)
			.property("Activity")
			.alignment(Vertical.RIGHT_ADJUSTED).build())
		.addFieldUnderMol(new PredictionIntervalField.Builder(0.8).layout(
			new CustomLayout.Builder().boarder(new Boarder.Builder().stroke(new BasicStroke(3.f)).build()).build()).build()
			)
		.addFieldUnderMol(new HighlightExplanationField.Builder(Color.BLUE).build())
		.addFieldUnderMol(new HighlightExplanationField.Builder(Color.CYAN,"mutagen").boxLocation(BoxLocation.RIGHT).alignment(Vertical.RIGHT_ADJUSTED).boxShape(BoxShape.TRIANGLE).build())
		.addFieldUnderMol(new ColorGradientField.Builder(grad).build());
		AtomContributionRenderer renderer = bldr.build();
		renderer.render(info).saveToFile(new File(imageOutputFolder, "gradientDepiction.png"));
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

		// Wish to paint a 12x10 gray with the orn outline
		int h=10,w=12;

		BufferedImage imgToEnclose = new BufferedImage(w+orn.getAdditionalWidth(), 
			h+orn.getAdditionalHeight(), IOSettings.BUFFERED_IMAGE_TYPE);
		Graphics2D g2d = imgToEnclose.createGraphics();
		Rectangle2D out = orn.addLayout(g2d, new Rectangle2D.Float(0,0,imgToEnclose.getWidth(), imgToEnclose.getHeight()));
		Assert.assertEquals(w, out.getWidth(),0.00001);
		Assert.assertEquals(h, out.getHeight(),0.00001);

		g2d.setColor(Color.GRAY);
		g2d.fill(out);
		g2d.dispose();
		ImageIO.write(imgToEnclose, "png", new File(imageOutputFolder,"withRoundedBoarder.png"));
	}
	
	@Test
	public void testRectangleBoarder() throws Exception {
		CustomLayout orn = new CustomLayout.Builder()
			.padding(new Padding(1))
			.boarder(new Boarder.Builder().build())
			.margin(new Margin(1)).build();

			int h=10,w=12;

		BufferedImage image = new BufferedImage(w+orn.getAdditionalWidth(), 
			h+orn.getAdditionalHeight(), IOSettings.BUFFERED_IMAGE_TYPE);
		Graphics2D g2d = image.createGraphics();
		Rectangle2D out = orn.addLayout(g2d, new Rectangle2D.Float(0,0,image.getWidth(), image.getHeight()));
		Assert.assertEquals(w, out.getWidth(),0.00001);
		Assert.assertEquals(h, out.getHeight(),0.00001);

		g2d.setColor(Color.WHITE);
		g2d.fill(out);
		g2d.dispose();

		ImageIO.write(image, "png", new File(imageOutputFolder,"withRectangleBoarder.png"));
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

	@Test
	public void testRealThing()throws Exception {

		ColorGradient grad = GradientFactory.getDefaultBloomGradient();
		AtomContributionRenderer renderer = new AtomContributionRenderer.Builder()
			.colorScheme(grad)
			.width(500)
			.height(500)
			.addFieldOverMol(new TitleField.Builder().alignment(Vertical.LEFT_ADJUSTED).underline(true).build())
			.addFieldOverMol(new HighlightExplanationField.Builder(Color.CYAN,"nonmutagen").boxShape(BoxShape.CIRCLE).boxSize(6).build()).build();
		SmilesParser sp = new SmilesParser(SilentChemObjectBuilder.getInstance());
		IAtomContainer mol = sp.parseSmiles("C");

		ChemCPClassifier predictor = (ChemCPClassifier) ModelSerializer.loadChemPredictor(PreTrainedModels.ACP_CLF_LIBLINEAR.toURI(), null);
		predictor.getDataset().setMinHAC(0);
		SignificantSignature ss = predictor.predictSignificantSignature(mol);
		Assert.assertTrue(ss.getAtomContributions().isEmpty());
		Assert.assertTrue(ss.getAtoms().isEmpty());
		RenderInfo info = new RenderInfo.Builder(mol, ss).build();

		renderer.render(info).saveToFile(new File(imageOutputFolder, "gradientDepiction_C.png"));

	}


}
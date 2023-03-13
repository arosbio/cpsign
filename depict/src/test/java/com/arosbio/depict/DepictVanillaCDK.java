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
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.AttributedString;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.imageio.ImageIO;

import org.freehep.graphicsio.pdf.PDFGraphics2D;
import org.freehep.graphicsio.ps.EPSGraphics2D;
import org.freehep.graphicsio.ps.PSGraphics2D;
import org.freehep.graphicsio.svg.SVGGraphics2D;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openscience.cdk.depict.Depiction;
import org.openscience.cdk.depict.DepictionGenerator;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.geometry.GeometryUtil;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.layout.StructureDiagramGenerator;
import org.openscience.cdk.renderer.AtomContainerRenderer;
import org.openscience.cdk.renderer.RendererModel;
import org.openscience.cdk.renderer.SymbolVisibility;
import org.openscience.cdk.renderer.color.CDK2DAtomColors;
import org.openscience.cdk.renderer.elements.Bounds;
import org.openscience.cdk.renderer.font.AWTFontManager;
import org.openscience.cdk.renderer.generators.BasicSceneGenerator;
import org.openscience.cdk.renderer.generators.BasicSceneGenerator.Margin;
import org.openscience.cdk.renderer.generators.IGenerator;
import org.openscience.cdk.renderer.generators.standard.SelectionVisibility;
import org.openscience.cdk.renderer.generators.standard.StandardGenerator;
import org.openscience.cdk.renderer.visitor.AWTDrawVisitor;
import org.openscience.cdk.renderer.visitor.IDrawVisitor;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

/**
 * Note that this is not tests for the classes included in this repo, only meant for playing with vectorized images and debugging before things started working
 * 
 */
public class DepictVanillaCDK extends BaseTestClass {
    private final static int BUFFERED_IMAGE_TYPE = BufferedImage.TYPE_4BYTE_ABGR;

    public static IAtomContainer mol = null;

    @BeforeClass
    public static void createMol() throws Exception {
        SmilesParser sp = new SmilesParser(SilentChemObjectBuilder.getInstance());
		mol = sp.parseSmiles("[C-]CC(C(=O)NC1=CC=C(C=C1)C(F)(F)F)N(C)CC(=O)N2CCN(CC2)CC3=CC=CC=C3");
    }

    int w = 500, h = 400;
    @Test
    public void testMolDep() throws Exception {
        Font font = new Font(Font.SANS_SERIF,Font.PLAIN,13);
        MoleculeDepictor dep = new MoleculeDepictor.Builder().height(h).width(w).generators(Arrays.asList(new BasicSceneGenerator(),new StandardGenerator(font))).build();
        BufferedImage img = dep.depict(mol, null);
        ImageIO.write(img, "png", new File(TEST_OUTPUT_DIR,"vanilla_mdp.png"));
    }

    @Test
    public void testVanillaBitmap() throws Exception {
        String fmt = "png";
        Font font = new Font(Font.SANS_SERIF,Font.PLAIN,13);
        
        // SmilesParser sp = new SmilesParser(SilentChemObjectBuilder.getInstance());
		// IAtomContainer mol1 = sp.parseSmiles("[C-]CC(C(=O)NC1=CC=C(C=C1)C(F)(F)F)N(C)CC(=O)N2CCN(CC2)CC3=CC=CC=C3");
        fixCoords(mol);
        // new StructureDiagramGenerator().generateCoordinates(mol1);
        File outputfile = new File(TEST_OUTPUT_DIR, "vanilla_cdk_non_vec."+fmt);
        int imageWidth = 400, imageHeight = 400;
        
        Rectangle2D drawArea = new Rectangle2D.Double(0,0,imageWidth,imageHeight);
        BufferedImage img = new BufferedImage(imageWidth, imageHeight, BUFFERED_IMAGE_TYPE);
		Graphics2D g2 = img.createGraphics(); //getPDF(outputfile,new java.awt.Dimension(imageWidth,imageHeight)); //img.createGraphics();
        g2.setColor(Color.WHITE);
		g2.fillRect(0, 0, imageWidth, imageHeight);


        List<IGenerator<IAtomContainer>> generators = Arrays.asList(new BasicSceneGenerator(),new StandardGenerator(font));
        
        AtomContainerRenderer renderer = new AtomContainerRenderer(generators, new AWTFontManager());
        renderer.setup(mol, drawArea.getBounds());

        RendererModel model = renderer.getRenderer2DModel();
        // rendererModel.set(Margin.class, 55d);
        // model.set(BasicSceneGenerator.FitToScreen.class, true);
        model.set(StandardGenerator.StrokeRatio.class, 
				1.3);
		model.set(Margin.class, 
				55d);
		// model.set(StandardGenerator.AnnotationColor.class, 
		// 		numberColor);
		// model.set(StandardGenerator.AnnotationFontScale.class,
		// 		atomNumberScaleFactor);
		model.set(StandardGenerator.Highlighting.class,
				StandardGenerator.HighlightStyle.OuterGlow);
		model.set(StandardGenerator.OuterGlowWidth.class,
				1d);
		model.set(StandardGenerator.Visibility.class, 
				SelectionVisibility.all(SymbolVisibility.iupacRecommendationsWithoutTerminalCarbon()));
        model.set(StandardGenerator.AtomColor.class,new CDK2DAtomColors());
        // renderer.setupModel()
        

        IDrawVisitor visitor = new AWTDrawVisitor(g2); //AWTDrawVisitor.forVectorGraphics(g2); //new AWTDrawVisitor(g2);
        visitor.setTransform(AffineTransform.getScaleInstance(1, -1));
        renderer.paint(mol, visitor, drawArea,true);

        g2.dispose();

        
        ImageIO.write(img, fmt, outputfile);
    }


    @Test
    public void testVanillaPDF() throws Exception {
        Font font = new Font(Font.SANS_SERIF,Font.BOLD,13);
        
        // SmilesParser sp = new SmilesParser(SilentChemObjectBuilder.getInstance());
		// IAtomContainer mol1 = sp.parseSmiles("[C-]CC(C(=O)NC1=CC=C(C=C1)C(F)(F)F)N(C)CC(=O)N2CCN(CC2)CC3=CC=CC=C3");
        fixCoords(mol);
        // new StructureDiagramGenerator().generateCoordinates(mol1);
        File outputfile = new File(TEST_OUTPUT_DIR,"vanilla_cdk.pdf");
        int imageWidth = 400, imageHeight = 400;
        
        Rectangle2D drawArea = new Rectangle2D.Double(0,0,imageWidth,imageHeight);
        // BufferedImage img = new BufferedImage(imageWidth, imageHeight, BUFFERED_IMAGE_TYPE);
		Graphics2D g2 = getPDF(outputfile,new java.awt.Dimension(imageWidth,imageHeight)); //img.createGraphics();


        List<IGenerator<IAtomContainer>> generators = Arrays.asList(new BasicSceneGenerator(),new StandardGenerator(font));
        AtomContainerRenderer renderer = new AtomContainerRenderer(generators, new AWTFontManager());
        RendererModel rendererModel = renderer.getRenderer2DModel();
        rendererModel.set(Margin.class, 55d);
        rendererModel.set(BasicSceneGenerator.FitToScreen.class, true);
        rendererModel.set(StandardGenerator.AtomColor.class,new CDK2DAtomColors());
        rendererModel.set(StandardGenerator.StrokeRatio.class, 
				2.3);
        // renderer.setupModel()
        renderer.setup(mol, drawArea.getBounds());
        IDrawVisitor visitor = AWTDrawVisitor.forVectorGraphics(g2);
        visitor.setTransform(AffineTransform.getScaleInstance(1, -1));
        // visitor.visit(new RectangleElement(0, -(int) Math.ceil(imageHeight), (int) Math.ceil(imageWidth), (int) Math.ceil(imageHeight),
        //                                    true, rendererModel.get(BasicSceneGenerator.BackgroundColor.class)));
        // renderer.paint(mol, visitor);
        renderer.paint(mol, visitor, drawArea,true);

        AttributedString str = new AttributedString("here is some text - vectorized?");
        str.addAttribute(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD, 0, 15);
        str.addAttribute(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE, 15, 25);
		// int indexInString=0;
		// for(TextSection sec: sections){
		// 	int sectionLength = sec.text.length();
		// 	if (sec.tags.contains(FontType.ITALIC))
		// 		str.addAttribute(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE, indexInString, indexInString+sectionLength);
		// 	if (sec.tags.contains(FontType.BOLD))
		// 		str.addAttribute(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD, indexInString, indexInString+sectionLength);
		// 	indexInString+=sectionLength;
        g2.drawString(str.getIterator(),5,10);
        g2.drawString("here is some text - vectorized?", 20, 100);


        // Here's a bit map image 
        BufferedImage img = new BufferedImage(50, 50, BUFFERED_IMAGE_TYPE);
        Graphics2D rasterG = img.createGraphics();
        rasterG.setBackground(Color.BLUE);
        rasterG.drawString("in raster..", 0, 0);
        rasterG.dispose();
        // try to add it to the pdf
        g2.drawImage(img, 5,50,null);

        ((PDFGraphics2D) g2).writeTrailer();
        ((PDFGraphics2D) g2).closeStream();
        g2.dispose();

        
        // ImageIO.write(img, "png", outputfile);
    }

    public static Graphics2D getPDF(File out, java.awt.Dimension dim) throws Exception{
        PDFGraphics2D pdf = new PDFGraphics2D(out, dim);
        pdf.setCreator("Staffan");
        Properties props = new Properties();
        props.setProperty(PDFGraphics2D.FIT_TO_PAGE, "false");
        props.setProperty(PDFGraphics2D.PAGE_SIZE, PDFGraphics2D.CUSTOM_PAGE_SIZE);
        props.setProperty(PDFGraphics2D.CUSTOM_PAGE_SIZE, dim.width + ", " + dim.height);
        props.setProperty(PDFGraphics2D.PAGE_MARGINS, "0, 0, 0, 0");
        pdf.setProperties(props);
        pdf.writeHeader();
        return pdf;
    }

    public static Graphics2D getEPS(File out, java.awt.Dimension dim) throws IOException{
        EPSGraphics2D eps = new EPSGraphics2D(out, dim);
        // For EPS (Encapsulated PostScript) page size has no
        // meaning since this image is supposed to be included
        // in another page.
        Properties eps_props = new Properties();
        eps_props.setProperty(PDFGraphics2D.FIT_TO_PAGE, "false");
        eps.setProperties(eps_props);
        eps.writeHeader();
        return eps;
    }
   

    public static Graphics2D getSVG(File out, java.awt.Dimension dim) throws Exception{
        SVGGraphics2D svg = new SVGGraphics2D(out,dim);
        return svg;
        // PDFGraphics2D pdf = new PDFGraphics2D(out, dim);
        // pdf.setCreator("Staffan");
        // Properties props = new Properties();
        // props.setProperty(PDFGraphics2D.FIT_TO_PAGE, "false");
        // props.setProperty(PDFGraphics2D.PAGE_SIZE, PDFGraphics2D.CUSTOM_PAGE_SIZE);
        // props.setProperty(PDFGraphics2D.CUSTOM_PAGE_SIZE, dim.width + ", " + dim.height);
        // props.setProperty(PDFGraphics2D.PAGE_MARGINS, "0, 0, 0, 0");
        // pdf.setProperties(props);
        // pdf.writeHeader();
        // return pdf;
    }

    @Test
    public void testVanillaSVG() throws Exception {
        Font font = new Font(Font.SANS_SERIF,Font.BOLD,13);
        
        // SmilesParser sp = new SmilesParser(SilentChemObjectBuilder.getInstance());
		// IAtomContainer mol1 = sp.parseSmiles("[C-]CC(C(=O)NC1=CC=C(C=C1)C(F)(F)F)N(C)CC(=O)N2CCN(CC2)CC3=CC=CC=C3");
        fixCoords(mol);
        // new StructureDiagramGenerator().generateCoordinates(mol1);
        File outputfile = new File(TEST_OUTPUT_DIR,"vanilla_cdk.svg");
        int imageWidth = 400, imageHeight = 400;
        
        Rectangle2D drawArea = new Rectangle2D.Double(0,0,imageWidth,imageHeight);
        // BufferedImage img = new BufferedImage(imageWidth, imageHeight, BUFFERED_IMAGE_TYPE);
		Graphics2D g2 = getSVG(outputfile,new java.awt.Dimension(imageWidth,imageHeight)); //img.createGraphics();


        List<IGenerator<IAtomContainer>> generators = Arrays.asList(new BasicSceneGenerator(),new StandardGenerator(font));
        AtomContainerRenderer renderer = new AtomContainerRenderer(generators, new AWTFontManager());
        RendererModel rendererModel = renderer.getRenderer2DModel();
        rendererModel.set(Margin.class, 55d);
        rendererModel.set(BasicSceneGenerator.FitToScreen.class, true);
        rendererModel.set(StandardGenerator.AtomColor.class,new CDK2DAtomColors());
        rendererModel.set(StandardGenerator.StrokeRatio.class, 
				2.3);
        // renderer.setupModel()
        renderer.setup(mol, drawArea.getBounds());
        IDrawVisitor visitor = AWTDrawVisitor.forVectorGraphics(g2);
        visitor.setTransform(AffineTransform.getScaleInstance(1, -1));
        // visitor.visit(new RectangleElement(0, -(int) Math.ceil(imageHeight), (int) Math.ceil(imageWidth), (int) Math.ceil(imageHeight),
        //                                    true, rendererModel.get(BasicSceneGenerator.BackgroundColor.class)));
        // renderer.paint(mol, visitor);
        renderer.paint(mol, visitor, drawArea,true);

        AttributedString str = new AttributedString("here is some text - vectorized?");
        str.addAttribute(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD, 0, 15);
        str.addAttribute(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE, 15, 25);
		// int indexInString=0;
		// for(TextSection sec: sections){
		// 	int sectionLength = sec.text.length();
		// 	if (sec.tags.contains(FontType.ITALIC))
		// 		str.addAttribute(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE, indexInString, indexInString+sectionLength);
		// 	if (sec.tags.contains(FontType.BOLD))
		// 		str.addAttribute(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD, indexInString, indexInString+sectionLength);
		// 	indexInString+=sectionLength;
        g2.drawString(str.getIterator(),5,10);
        // g2.drawString("here is some text - vectorized?", 20, 100);


        // Here's a bit map image 
        BufferedImage img = new BufferedImage(50, 50, BUFFERED_IMAGE_TYPE);
        Graphics2D rasterG = img.createGraphics();
        rasterG.setBackground(Color.BLUE);
        rasterG.drawString("in raster..", 0, 0);
        rasterG.dispose();
        // try to add it to the pdf
        g2.drawImage(img, 5,50,null);


        if (g2 instanceof PSGraphics2D){
            ((PSGraphics2D) g2).writeTrailer();
            ((PSGraphics2D) g2).closeStream();
        } else if (g2 instanceof EPSGraphics2D){
            ((EPSGraphics2D) g2).writeTrailer();
            ((EPSGraphics2D) g2).closeStream();
        }
        
        g2.dispose();

        
        // ImageIO.write(img, "png", outputfile);
    }

    @Test
    public void testUsingDepictionGen() throws Exception {
        DepictionGenerator gen = new DepictionGenerator();

        SmilesParser sp = new SmilesParser(SilentChemObjectBuilder.getInstance());
		IAtomContainer mol = sp.parseSmiles("[C-]CC(C(=O)NC1=CC=C(C=C1)C(F)(F)F)N(C)CC(=O)N2CCN(CC2)CC3=CC=CC=C3");
        Depiction dep = gen.withSize(400, 400).depict(mol);
        dep.writeTo(new File(TEST_OUTPUT_DIR,"vanilla_dep_gen.pdf").getAbsolutePath());

    }


    public static void fixCoords(IAtomContainer mol) throws CDKException{
        ensure2dLayout(mol);
        // if (mol.getBondCount() > 0) {
        //     final double factor = GeometryUtil.getScaleFactor(mol, 1.5);
        //     GeometryUtil.scaleMolecule(mol, factor);
        // }
    }

    protected final void draw(IDrawVisitor visitor, RendererModel model, double zoom, Bounds bounds, Rectangle2D viewBounds) {

        double modelScale = zoom * model.get(BasicSceneGenerator.Scale.class);
        double zoomToFit = Math.min(viewBounds.getWidth() / (bounds.width() * modelScale),
                                    viewBounds.getHeight() / (bounds.height() * modelScale));

        AffineTransform transform = new AffineTransform();
        transform.translate(viewBounds.getCenterX(), viewBounds.getCenterY());
        transform.scale(modelScale, -modelScale);

        // default is shrink only unless specified
        if (model.get(BasicSceneGenerator.FitToScreen.class) || zoomToFit < 1)
            transform.scale(zoomToFit, zoomToFit);

        transform.translate(-(bounds.minX + bounds.maxX) / 2,
                            -(bounds.minY + bounds.maxY) / 2);

        // not always needed
        AWTFontManager fontManager = new AWTFontManager();
        fontManager.setFontForZoom(zoomToFit);

        visitor.setRendererModel(model);
        visitor.setFontManager(fontManager);
        visitor.setTransform(transform);

        // setup up transform
        visitor.visit(bounds.root());
    }

    private static boolean ensure2dLayout(IAtomContainer container) throws CDKException {
        if (!GeometryUtil.has2DCoordinates(container)) {
            StructureDiagramGenerator sdg = new StructureDiagramGenerator();
            sdg.generateCoordinates(container);
            return true;
        }
        return false;
    }

    @Test
    public void testSetSeveralRowsRGB() throws Exception{
        File outputfile = new File(TEST_OUTPUT_DIR,"plain_two_rows.png");
        int imageHeight=300,imageWidth = 200;
        BufferedImage img = new BufferedImage(imageWidth, imageHeight, BUFFERED_IMAGE_TYPE);
        int[] twoRows = new int[imageWidth*2];
        Arrays.fill(twoRows, Color.CYAN.getRGB());
        img.setRGB(0, 150, imageWidth, 2, twoRows, 0, 0);

        int[] threeRows = new int[imageWidth*3];
        Arrays.fill(threeRows, Color.GREEN.getRGB());
        img.setRGB(0, 160, imageWidth, 3, threeRows, 0, 0);

        
        ImageIO.write(img, "png", outputfile);
    }
}

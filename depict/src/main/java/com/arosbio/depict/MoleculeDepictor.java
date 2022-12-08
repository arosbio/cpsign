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
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.arosbio.color.gradient.ColorGradient;
import com.arosbio.color.gradient.GradientFactory;
import com.arosbio.depict.cdk.bloom.BloomDrawVisitor;
import com.arosbio.depict.cdk.bloom.BloomGenerator;

import org.openscience.cdk.geometry.GeometryUtil;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.layout.StructureDiagramGenerator;
import org.openscience.cdk.renderer.AtomContainerRenderer;
import org.openscience.cdk.renderer.RendererModel;
import org.openscience.cdk.renderer.SymbolVisibility;
import org.openscience.cdk.renderer.font.AWTFontManager;
import org.openscience.cdk.renderer.generators.BasicSceneGenerator;
import org.openscience.cdk.renderer.generators.BasicSceneGenerator.Margin;
import org.openscience.cdk.renderer.generators.IGenerator;
import org.openscience.cdk.renderer.generators.standard.SelectionVisibility;
import org.openscience.cdk.renderer.generators.standard.StandardGenerator;
import org.openscience.cdk.renderer.visitor.IDrawVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>MoleculeDepictor</code> uses some of CDK rendering classes and mechanisms together with
 * our own BloomRenderer (currently only bloom depiction supported). Initialization and configuration is
 * handled with the {@link MoleculeDepictor.Builder} class. Visit the CPSign website for some example 
 * images http://arosbio.com/ <br>
 * <br>
 * Note that coloring of atoms are done by giving a <code>Map</code> linking <code>IAtom</code> to
 * their intended color. Coloring values should be normalized within the range [-1..1], values outside
 * these region will be capped of the respective extreme values. 
 *  
 * <br>
 * <b>Typical Usage:</b>
 * <pre>{@code 
 * MoleculeDepictor depictor = new MoleculeDepictor.Builder()
 * 		.colorScheme(gradient) // The gradient that should be used
 * 		.width(pageWidth) // pixel width
 * 		.height(pageHeight) // pixel height
 * 		.bg(Color.WHITE) // Add white background
 * 		.build();
 * BufferedImage img = depictor.depict(mol, coloring);
 * 
 * //Write the image
 * ImageIO.write(pageImage, "png", new File("output/mol-image.png");
 * 
 * }</pre>
 * 
 * @author Aros Bio AB
 * @author Staffan Arvidsson McShane
 */
public class MoleculeDepictor {
	
	private final static Font DEFAULT_FONT = new Font(Font.SANS_SERIF,Font.PLAIN,13);
	private final static int BUFFERED_IMAGE_TYPE = BufferedImage.TYPE_4BYTE_ABGR;
	private final static Logger LOGGER = LoggerFactory.getLogger(MoleculeDepictor.class);

	private final int imageWidth, imageHeight;
	private final ColorGradient colorGradient;
	private final int bloomRasterSize;
	private final boolean showNumbers;
	private final Color numberColor;
	private final Color bgColor;
	private final List<BufferedImageOp> imageOp;
	private final double atomNumberScaleFactor;
	private final Font font;
	private final boolean forceRecalcCoordinates;
	private final List<IGenerator<IAtomContainer>> generators;

	private MoleculeDepictor(Builder b){
		imageHeight = b.h;
		imageWidth = b.w;
		bloomRasterSize = b.bloomRaster;
		colorGradient = b.colorGradient;
		showNumbers = b.showNumbers;
		numberColor = b.numberColor;
		bgColor = b.background;
		imageOp = b.imageOp;
		atomNumberScaleFactor = b.atomNumberScaleFactor;
		font = b.font;
		forceRecalcCoordinates = b.forceCalcNew2DCoords;
		if (b.generators != null)
			generators = new ArrayList<>(b.generators);
		else {
			generators = Arrays.asList(
				new BasicSceneGenerator(),
				new BloomGenerator(),
				new StandardGenerator(font));
		}
	}

	/**
	 * Builder class for the {@link MoleculeDepictor} class
	 */
	public static class Builder {
		private int w = 400;
		private int h = 400;
		private int bloomRaster = 1;
		private ColorGradient colorGradient = GradientFactory.getDefaultBloomGradient();
		private boolean showNumbers;
		private Color numberColor = Color.BLUE;
		private Color background = null;
		private List<BufferedImageOp> imageOp = null;
		private double atomNumberScaleFactor = 1d;
		private Font font = DEFAULT_FONT;
		private List<IGenerator<IAtomContainer>> generators;
		private boolean forceCalcNew2DCoords = false;

		/**
		 * Alias for {@link #width(int)}
		 * @param w width in pixels
		 * @return the Builder instance
		 */
		public Builder w(int w){
			this.w = w;
			return this;
		}
		/**
		 * Set image width
		 * @param w width in pixels
		 * @return the Builder instance
		 */
		public Builder width(int w){
			this.w = w;
			return this;
		}
		/**
		 * Get the image width 
		 * @return the width in pixels
		 */
		public int width(){
			return w;
		}
		/**
		 * Alias for {@link #height(int)} 
		 * @param h height in pixels
		 * @return the Builder instance
		 */
		public Builder h(int h){
			this.h = h;
			return this;
		}
		/**
		 * Set image height
		 * @param h height in pixels
		 * @return the Builder instance
		 */
		public Builder height(int h){
			this.h = h;
			return this;
		}
		/**
		 * Get image height in pixels
		 * @return the height in pixels
		 */
		public int height(){
			return h;
		}

		/**
		 * Set how many pixels should be 'stepped' between calculation of the color
		 * in the bloom rendering. Default is stepping 1 in x and y direction, but 
		 * the runtime can be decreased by setting a larger raster step size (i.e. &gt;1).
		 * If specifying {@code size=2} the bloom is calculated in 2x2 pixel squares (i.e. 1 calculation instead of 4),
		 * {@code size=3} uses 3x3 pixel squares etc.
		 * @param size the 'step size' in the raster, must be &gt;0
		 * @return the Builder instance
		 */
		public Builder rasterSize(int size){
			if (size <= 0)
				throw new IllegalArgumentException("Invalid raster size: " + size);
			this.bloomRaster = size;
			return this;
		}

		public int rasterSize(){
			return this.bloomRaster;
		}
		
		/**
		 * The {@link ColorGradient} to use, i.e. deciding how coloring should be done
		 * @param scheme {@link ColorGradient} to use
		 * @return the same Builder instance
		 */
		public Builder colorScheme(ColorGradient scheme){
			if (scheme == null)
				throw new NullPointerException("Color scheme cannot be null");
			this.colorGradient = scheme;
			return this;
		}
		/**
		 * Alias for {@link #colorScheme(ColorGradient)}
		 * @param scheme {@link ColorGradient} to use
		 * @return the same Builder instance
		 */
		public Builder color(ColorGradient scheme){
			return colorScheme(scheme);
		}
		/**
		 * Get the {@link ColorGradient} that should be used
		 * @return the {@link ColorGradient}
		 */
		public ColorGradient colorScheme(){
			return colorGradient;
		}
		/**
		 * Toggle if atom numbers should be displayed 
		 * @param show {@code true} if numbers should be printed, {@code false} otherwise
		 * @return the Builder instance
		 */
		public Builder showAtomNumbers(boolean show){
			this.showNumbers = show;
			return this;
		}
		/**
		 * Getter for if atom numbers should be displayed
		 * @return if atom numbers should be rendered
		 */
		public boolean showAtomNumbers(){
			return showNumbers;
		}
		/**
		 * Set the color of the atom numbers. 
		 * Only has an effect in case {@link #showAtomNumbers(boolean)} is set to {@code true}
		 * @param color the AWT Color to use
		 * @return the Builder instance
		 */
		public Builder numberColor(Color color){
			this.numberColor = color;
			return this;
		}
		/**
		 * Getter for the color of atom numbers
		 * @return the ATW Color to use
		 */
		public Color numberColor(){
			return numberColor;
		}
		/**
		 * Alias for {@link #background(Color)}
		 * @param bg AWT Color to use as background, or {@code null}
		 * @return the Builder instance
		 */
		public Builder bg(Color bg){
			this.background = bg;
			return this;
		}
		/**
		 * Set a background color (default is {@code null}, i.e. no background)
		 * @param bg AWT Color to use as background, or {@code null}
		 * @return the Builder instance
		 */
		public Builder background(Color bg){
			this.background = bg;
			return this;
		}
		/**
		 * Get the background Color, default is no background added at all
		 * @return the AWT Color used, or {@code null}
		 */
		public Color background(){
			return background;
		}
		/**
		 * Optional {@link BufferedImageOp} operations to apply to the final image,
		 * after the image have been rendered by all IGenerators
		 * @param op custom {@link BufferedImageOp} to apply 
		 * @return the Builder instance
		 */
		public Builder imageOp(BufferedImageOp... op){
			this.imageOp = Arrays.asList(op);
			return this;
		}
		/**
		 * Scale atom numbers, the default is 1 (i.e. no scaling)
		 * @param factor optional scaling of the atom numbers
		 * @return the Builder instance
		 */
		public Builder scaleAtomNumbers(double factor){
			this.atomNumberScaleFactor = factor;
			return this;
		}
		/**
		 * Font to use for atoms and atom numbers
		 * @param font the AWT {@code Font} to use
		 * @return the Builder instance
		 */
		public Builder font(Font font){
			this.font = font;
			return this;
		}
		/**
		 * Get the Font used for atoms and atom numbers
		 * @return the Builder instance
		 */
		public Font font(){
			return font;
		}
		/**
		 * Custom {@code IGenerator<IAtomContainer>} list. The default is;
		 * <pre>
		 * {@code
		 *1. new BasicSceneGenerator();
		 *2. new BloomGenerator();
		 *3. new StandardGenerator(font);}
		 * </pre>
		 * where {@code font} is the one specified to {@link Builder#font(Font)}
		 * @param generators A concrete list of generators
		 * @return the Builder instance
		 */
		public Builder generators(List<IGenerator<IAtomContainer>> generators){
			this.generators = generators;
			return this;
		}
		/**
		 * If 2D coordinates should be forced to be re-calculated. If {@code false} existing
		 * 2D coordinates will be used when possible
		 * @param force {@code true} if coordinates always should be re-calculated
		 * @return the Builder instance
		 */
		public Builder recalc2D(boolean force){
			this.forceCalcNew2DCoords = force;
			return this;
		}

		/**
		 * Build the final {@link MoleculeDepictor} instance
		 * @return A {@link MoleculeDepictor} instance
		 */
		public MoleculeDepictor build(){
			return new MoleculeDepictor(this);
		}

	}

		
	/**
	 * Get the {@link BufferedImage} type used, the type is {@link BufferedImage#TYPE_4BYTE_ABGR}
	 * @return the {@link BufferedImage} imageType
	 */
	public static int getImageType(){
		return BUFFERED_IMAGE_TYPE;
	}
	
	/**
	 * Getter for the {@link ColorGradient}
	 * @return the {@link ColorGradient} that is used
	 */
	public ColorGradient getColorGradient() {
		return this.colorGradient;
	}

	public int getImageWidth() {
		return imageWidth;
	}

	public int getImageHeight() {
		return imageHeight; 
	}
	
	public Font getFont(){
		return this.font;
	}

	public boolean getShowAtomNumbers(){
		return this.showNumbers;
	}

	public Color getAtomNumberColor() {
		return numberColor;
	}
	
	public double getAtomNumberScaleFactor(){
		return this.atomNumberScaleFactor;
	}
	
	public boolean getForceRecalcCoordinates() {
		return forceRecalcCoordinates;
	}


	protected void setupModel(RendererModel model) {
		model.set(StandardGenerator.StrokeRatio.class, 
				1.3);
		model.set(Margin.class, 
				55d);
		model.set(StandardGenerator.AnnotationColor.class, 
				numberColor);
		model.set(StandardGenerator.AnnotationFontScale.class,
				atomNumberScaleFactor);
		model.set(StandardGenerator.Highlighting.class,
				StandardGenerator.HighlightStyle.OuterGlow);
		model.set(StandardGenerator.OuterGlowWidth.class,
				1d);
		model.set(StandardGenerator.Visibility.class, 
				SelectionVisibility.all(SymbolVisibility.iupacRecommendationsWithoutTerminalCarbon()));
	}

	/**
	 * Perform depiction of a molecule 
	 * @param mol the molecule to render
	 * @param atomColors a map with the values of atom coloring, values should be normalized to be in the range [-1..1]. Keys <b><i>must</i></b> either {@link Integer} or {@link IAtom}.
	 * @return An AWT {@link BufferedImage} with the rendered molecule
	 * @throws IllegalArgumentException In case the keys in {@code atomColors} are neither Integer nor IAtom, or if keys do not match the {@code mol} argument
	 */
	public BufferedImage depict(IAtomContainer mol, Map<?, Double> atomColors)
		throws IllegalArgumentException {
		
		try {

			if (showNumbers){
				for (IAtom atom : mol.atoms()){
					String label = Integer.toString(atom.getIndex());
					atom.setProperty(StandardGenerator.ANNOTATION_LABEL, label);
				}
			}

			Rectangle2D drawArea = new Rectangle2D.Double(0,0,imageWidth,imageHeight);

			BufferedImage img = new BufferedImage(imageWidth, imageHeight, BUFFERED_IMAGE_TYPE);
			Graphics2D g2 = img.createGraphics();
			if (bgColor != null){
				// Add background
				g2.setColor(bgColor);
				g2.fillRect(0, 0, imageWidth, imageHeight);
			}

			IDrawVisitor visitor = new BloomDrawVisitor(g2, colorGradient).setGridStepSize(bloomRasterSize);

			generate2DCoordinates(mol);
			
			Map<Integer,Number> colorMap = convertColorMap(mol,atomColors);
			if (colorMap != null && !colorMap.isEmpty())
				mol.setProperty(BloomGenerator.DS_DATA, colorMap); 

			AtomContainerRenderer renderer = new AtomContainerRenderer(generators, new AWTFontManager());
			renderer.setup(mol, drawArea.getBounds());
			RendererModel model = renderer.getRenderer2DModel();

			setupModel(model);

			//Add highlight for readability
			if (model.get(StandardGenerator.Highlighting.class).equals(StandardGenerator.HighlightStyle.OuterGlow)) {
				Color highlightColor = new Color(255,255,255,64);
				for (IAtom atom : mol.atoms()) {
					if (atom.getAtomicNumber() != 6 || 
							(atom.getAtomTypeName()!= null && (atom.getAtomTypeName().startsWith("C.plus") ||
							(atom.getAtomTypeName().startsWith("C.minus"))))){ // do not display carbon, unless with a charge (best effort-hack)
						atom.setProperty(StandardGenerator.HIGHLIGHT_COLOR, highlightColor);
					}
				}
			}

			renderer.paint(mol, visitor, drawArea, true);
			g2.dispose();
			

			if (imageOp != null && !imageOp.isEmpty()){
				// Apply custom image operations
				for (BufferedImageOp op : imageOp){
					BufferedImage tmp = op.createCompatibleDestImage(img, null);
					Graphics2D gc2 = tmp.createGraphics();
					gc2.drawImage(img, op, 0, 0);
					gc2.dispose();
					img = tmp; // Update the output image
				}
			}

			return img;
		} finally {
			// Clean up the molecule/atom properties
			if (showNumbers){
				// Remove the annotations in case someone else doesn't want them
				for (IAtom atom : mol.atoms()){
					atom.removeProperty(StandardGenerator.ANNOTATION_LABEL);
				}
			}
			// Remove color map (might not have been set, method call should not fail anyways)
			mol.removeProperty(BloomGenerator.DS_DATA);
		}
	}


	private Map<Integer, Number> convertColorMap(IAtomContainer mol, Map<?, Double> atomColors) {
		Map<Integer,Number> colorMap = new HashMap<>();
		if (atomColors == null || atomColors.isEmpty())
			return colorMap;
		int hac = mol.getAtomCount();
		for (Map.Entry<?, Double> atom : atomColors.entrySet()) {
			int indx = -1;
			if (atom.getKey() instanceof Integer) {
				indx = (Integer)atom.getKey();
			} else if (atom.getKey() instanceof IAtom){
				indx = mol.indexOf((IAtom)atom.getKey());
			} else {
				throw new IllegalArgumentException("Color map must be either using index (integer) or the IAtoms that should be colored");
			}

			// Check the index
			if (indx <0 || indx>= hac){
				throw new IllegalArgumentException("Invalid color map - index outside valid range");
			}
			
			colorMap.put(indx, atom.getValue());
		}
		return colorMap;
	}

	private IAtomContainer generate2DCoordinates(IAtomContainer mol) {
		if (GeometryUtil.has2DCoordinates(mol) && ! forceRecalcCoordinates)
			return mol;
		
		// Generate 2D
		try {
			new StructureDiagramGenerator().generateCoordinates(mol);
		} catch (Exception e) { 
			LOGGER.debug("Failed generating 2D coordinates for molecule",e);
		}
		return mol;
	}



}

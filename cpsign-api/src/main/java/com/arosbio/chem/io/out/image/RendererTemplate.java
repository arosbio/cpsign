package com.arosbio.chem.io.out.image;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.tuple.Pair;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.io.out.image.fields.FigureField;
import com.arosbio.chem.io.out.image.layout.DefaultLayout;
import com.arosbio.chem.io.out.image.layout.Layout;
import com.arosbio.chem.io.out.image.layout.Position.Vertical;
import com.arosbio.cheminf.SignificantSignature;
import com.arosbio.color.gradient.ColorGradient;
import com.arosbio.depict.MoleculeDepictor;
import com.google.common.collect.Range;

public abstract class RendererTemplate <T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RendererTemplate.class);

    /** The minimum number of pixels in both width and height for the molecule rendition itself */
    private final static int MIN_MOL_WIDTH_HEIGHT = 100;

    public static class Context {
        public final int imageFullWidth;
        public final int imageFullHeight;
        public final Font defaultFont;
        public final Color defaultTextColor;

        private Context(int w, int h, Font defaultFont, Color defaultColor){
            this.imageFullWidth = w;
            this.imageFullHeight = h;
            this.defaultFont = defaultFont;
            this.defaultTextColor = defaultColor;
        }
    }

    public static class RenderInfo {
        private final IAtomContainer mol;
        private final SignificantSignature gradient;
        private final Map<String,Double> probabilities;
        private final Map<String,Double> pValues;
        private final Range<Double> confidenceInterval;
        private final double confidence;

        public IAtomContainer getMol() {
            return mol;
        }
        public SignificantSignature getGradient(){
            return gradient;
        }
        public Map<String, Double> getProbabilities() {
            return probabilities;
        }
        public Map<String, Double> getPValues() {
            return pValues;
        }
        public Range<Double> getConfidenceInterval() {
            return confidenceInterval;
        }
        public double getConfidence() {
            return confidence;
        }

        public static class Builder {
            private IAtomContainer mol;
            private SignificantSignature gradient;
            private Map<String,Double> probabilities;
            private Map<String,Double> pValues;
            private Range<Double> confidenceInterval;
            private double confidence;
            
            public Builder(final IAtomContainer mol, final SignificantSignature gradient){
                Objects.nonNull(mol);
                Objects.nonNull(gradient);
                this.mol = mol;
                this.gradient = gradient;
            }
            public Builder probabilities(Map<String,Double> probs){
                this.probabilities = probs;
                return this;
            }

            public Builder pValues(Map<String,Double> pValues){
                this.pValues = pValues;
                return this;
            }
            public Builder predictionInterval(Range<Double> interval, double conf){
                this.confidenceInterval = interval;
                this.confidence = conf;
                return this;
            }
            public RenderInfo build(){
                return new RenderInfo(this);
            }
        }

        private RenderInfo(Builder b){
            this.mol = b.mol;
            this.gradient = b.gradient;
            this.probabilities = b.probabilities;
            this.pValues = b.pValues;
            this.confidenceInterval = b.confidenceInterval;
            this.confidence = b.confidence;
            
        }

    }

    public static class MolRendering {

        private final BufferedImage image;

        public MolRendering(final BufferedImage image){
            this.image = image;
        }

        public BufferedImage getImage(){
            return image;
        }
    
        public String getBase64(){
            return ImageUtils.convertToBase64(image);
        }
    
        public void saveToFile(File imgFile) throws IOException {
            ImageIO.write(image, "png", imgFile);
        }
    }




    static abstract class Builder <T,B> {

        /** The full width of the rendered image - including all fields */
        private int width = 400;
        /** The full height of the rendered image - including all fields */
        private int height = 400;
        private Color background = Color.WHITE;

        private List<FigureField> above = new ArrayList<>();
        private List<FigureField> below = new ArrayList<>();
        protected MoleculeDepictor.Builder figBuilder = new MoleculeDepictor.Builder();
        private List<Layout> molLayouts = new ArrayList<>();

        // Rendering defaults
        private Font defaultFont = null; // by default we calculate this based on the image size
        private Color defaultTextColor = Color.BLACK;

        
        protected abstract B getThis();
        abstract T build();

        protected Builder(ColorGradient grad){
            figBuilder.color(grad);
        }

        public B width(int w){
            this.width = w;
            return getThis();
        }
        public int width(){
            return width;
        }
        public B height(int h){
            this.height = h;
            return getThis();
        }
        public int height(){
            return height;
        }
        /** Set the background - a transparent background is achieved using either passing {@code null} here
         * or setting a color with an transparent alpha value
         * @return the Builder instance
         */
        public B background(Color bg){
            this.background = bg;
            return getThis();
        }
        public Color background(){
            return background;
        }
        public B addFieldOverMol(FigureField f){
            above.add(f);
            return getThis();
        }
        public B withFieldsOverMol(FigureField... fields){
            return withFieldsOverMol(Arrays.asList(fields));
        }
        public B withFieldsOverMol(List<FigureField> fields){
            if (fields != null) {
                this.above = fields;
            } else {
                this.above.clear();
            }
            return getThis();
        }

        public B addFieldUnderMol(FigureField f){
            below.add(f);
            return getThis();
        }

        public B withFieldsUnderMol(FigureField... fields){
            return withFieldsOverMol(Arrays.asList(fields));
        }

        public B withFieldsUnderMol(List<FigureField> fields){
            if (fields != null) {
                this.below = fields;
            } else {
                this.below.clear();
            }
            return getThis();
        }

        public B molLayout(Layout layout){
            if (!molLayouts.isEmpty()){
                this.molLayouts = new ArrayList<>();
            }
            this.molLayouts.add(layout);
            return getThis();
        }
        public B molLayouts(Layout... layouts){
            return molLayouts(Arrays.asList(layouts));
        }

        public B molLayouts(List<Layout> layouts){
            if (layouts == null){
                this.molLayouts = new ArrayList<>();
            } else {
                this.molLayouts = layouts;
            }
            return getThis();
        }

        /**
         * Access the underlying {@link MoleculeDepictor.Builder} instance, to set e.g. fonts etc. 
         * Note; users should not directly access the {@link MoleculeDepictor.Builder#colorScheme(ColorGradient)} method
         * @return the {@link MoleculeDepictor.Builder}
         */
        public MoleculeDepictor.Builder molBuilder(){
            return figBuilder;
        }

        /**
         * Set the default font to use - which can be overwritten individually for each field
         * @param font the default font
         * @return the Builder instance 
         */
        public B defaultFont(Font font){
            this.defaultFont = font;
            return getThis();
        }

        /**
         * Set the default text color - which can be overwritten individually for each field
         * @param c default Color to use
         * @return the Builder instance
         */
        public B defaultTextColor(Color c){
            this.defaultTextColor = c;
            return getThis();
        }
       
    }

    private final MoleculeDepictor depictor;

    /** The molecule area - excluding all layouts (if any) */
    private final Rectangle2D molArea;
    /** Any layouts surrounding the molecule area itself */
    private final List<Pair<Layout,Rectangle2D>> molLayouts;
    private final Context context;
    private final Color backgroundColor;
    private final List<Pair<FigureField, Rectangle2D>> extraFields;

    RendererTemplate(Builder<?,?> b){
        Font tmpFont = b.defaultFont;
        if (tmpFont == null){
            int shortestSide = Math.min(b.height, b.width);
			int defaultFontSize = Math.max(11, (int) (7 + shortestSide/40));
            tmpFont = FontFactory.plain(defaultFontSize);
            LOGGER.debug("No explicit font size set, calculated font to be of {}pt",defaultFontSize);
        }
        // Set some default settings
        context = new Context(b.width, b.height, tmpFont, b.defaultTextColor);
        if (b.width < MIN_MOL_WIDTH_HEIGHT || b.height < MIN_MOL_WIDTH_HEIGHT){
            throw new IllegalArgumentException("Molecule renditions should be at least 100px in width and height");
        }

        // Copy over the fields
        // this.molLayout = b.molLayouts;
        this.backgroundColor = b.background;

        // ================================================
        // Figure out the dimensions of all fields
        this.extraFields = new ArrayList<>(b.above.size()+b.below.size());

        // Fields above the molecule
        int topPixelY = 0;
        if (!b.above.isEmpty()){
            for (FigureField f : b.above){
                configureField(f, tmpFont.getSize());
                Dimension2D d = f.calculateDim(context);
                Rectangle2D pos = new Rectangle2D.Float(calcXPos(d.getWidth(), f.getAlignment()), topPixelY, (float)d.getWidth(), (float)d.getHeight());
                topPixelY += Math.ceil(d.getHeight());
                this.extraFields.add(Pair.of(f, pos));
            }
        }

        // Fields below the molecule
        int bottomPixelY = context.imageFullHeight;
        if (!b.below.isEmpty()){
            List<FigureField> tmp = new ArrayList<>(b.below);
            Collections.reverse(tmp);
            for (FigureField f : tmp){
                configureField(f, tmpFont.getSize());
                Dimension2D d = f.calculateDim(context);
                Rectangle2D pos = new Rectangle2D.Float(calcXPos(d.getWidth(), f.getAlignment()), (float)(bottomPixelY - d.getHeight()),(float) d.getWidth(), (float)d.getHeight());
                this.extraFields.add(Pair.of(f, pos));
                bottomPixelY -= Math.ceil(d.getHeight());
            }
        }
        // Calculate the remaining drawing area - used for the molecule image
        molLayouts = new ArrayList<>();
        if (b.molLayouts.isEmpty()){
            molArea = new Rectangle2D.Double(0, topPixelY, context.imageFullWidth, bottomPixelY - topPixelY);
        } else {
            // Exclude the width and height of any layouts added to the molecule "field"
            int xStart = 0, xStop = context.imageFullWidth;
            for (Layout l : b.molLayouts){
                // The outer boundaries of this layout
                molLayouts.add(Pair.of(l, new Rectangle2D.Double(xStart, topPixelY, xStop-xStart, bottomPixelY - topPixelY)));
                // left - right
                Pair<Integer,Integer> lr = l.getAddedLRWidth();
                xStart += lr.getLeft();
                xStop -= lr.getRight();
                Pair<Integer,Integer> tb = l.getAddedTBHeight();
                topPixelY += tb.getLeft();
                bottomPixelY -= tb.getRight();
            }
            molArea = new Rectangle2D.Double(xStart, topPixelY, xStop-xStart, bottomPixelY - topPixelY);
        }

        // Validate that the molecule drawing is large 'enough'
        if (molArea.getHeight() < MIN_MOL_WIDTH_HEIGHT || molArea.getWidth() < MIN_MOL_WIDTH_HEIGHT){
            throw new IllegalArgumentException("Molecule renditions should be at least 100px in width and height");
        }

        // Build the depictor instance
        if (b.figBuilder.font()==null){
            // if no font was set - use 10% larger than the default one
            b.figBuilder.font(tmpFont.deriveFont((float)(1.1*tmpFont.getSize())));
        }
        depictor =  b.figBuilder.w((int)molArea.getWidth()).h((int)molArea.getHeight()).build();
        
    }

    private void configureField(FigureField f, int defaultFontSize){
        if (f.getLayouts()!=null){
            for (Layout l : f.getLayouts()){
                if (l instanceof DefaultLayout)
                    ((DefaultLayout)l).configure(defaultFontSize);
            }
        }
    }

    private int calcXPos(double fieldW, Vertical pos){
        switch (pos) {
            case RIGHT_ADJUSTED:
                return (int) (context.imageFullWidth - fieldW);
            case CENTERED:
                return (int) ((context.imageFullWidth - fieldW)/2);
            case LEFT_ADJUSTED:
            default:
                return 0;
        }

    }


    abstract Map<?,Double> generateColorMapping(IAtomContainer mol, SignificantSignature prediction);

    public MolRendering render(RenderInfo blob){
        
        BufferedImage img = new BufferedImage(context.imageFullWidth, context.imageFullHeight, MoleculeDepictor.getImageType());
        Graphics2D g2 = null;
        try {
            g2 = img.createGraphics();

            // Add background 
            if (backgroundColor != null){
                g2.setColor(backgroundColor);
				g2.fillRect(0,0,context.imageFullWidth, context.imageFullHeight);
            }

            // Add extra fields
            if (!extraFields.isEmpty()){
                for (Pair<FigureField,Rectangle2D> f : extraFields){
                    f.getLeft().render(g2, f.getRight(), blob);
                }
            }

            // Add any layouts around the mol
            if (!molLayouts.isEmpty()){
                for (Pair<Layout,Rectangle2D> la : molLayouts){
                    la.getLeft().addLayout(g2, la.getRight());
                }
            }
            // Add the mol
            depictor.depict(blob.mol, generateColorMapping(blob.mol,blob.gradient), img, g2, molArea);
            // depictor.depict(blob.mol, )

        } finally {
            if (g2 != null)
                g2.dispose();
        }

        return new MolRendering(img);
    }
    
}

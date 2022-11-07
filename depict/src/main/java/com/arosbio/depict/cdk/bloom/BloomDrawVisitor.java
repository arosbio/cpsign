/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.depict.cdk.bloom;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.arosbio.color.gradient.ColorGradient;
import com.arosbio.depict.cdk.bloom.BloomElement.BloomingPoint;
import com.arosbio.depict.cdk.bloom.utils.Utils;

import org.openscience.cdk.renderer.elements.IRenderingElement;
import org.openscience.cdk.renderer.visitor.AWTDrawVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BloomDrawVisitor extends AWTDrawVisitor {

    private final static Logger LOGGER = LoggerFactory.getLogger(BloomDrawVisitor.class);
    private static final double MARGIN = 10.0D;
    
    
    private int rasterSize = 1;
    private ColorGradient gradient;

    public BloomDrawVisitor(Graphics2D graphics, ColorGradient gradient) {
        super(graphics);
        this.gradient = gradient;
    }

    /**
     * Set the "raster size" - i.e. how large {@code size}x{@code size} groups
     * of 'pixels' should be generated for each bloom-calculation. By default ({@code size=1})
     * the color for each individual pixel will be calculated but this i very computation-heavy 
     * (especially for large images). By increasing the raster size the background bloom will be more fuzzy,
     * but larger images can be generated so that clearer fonts and lines of the molecules can be rendered.
     * As this is a 2D size scale, setting e.g. {@code size=2} will take roughly 25% of the time to render 
     * compared to the default setting of 1.
     * @param size raster size, {@code >= 1}
     * @return The same instance but with new raster size
     */
    public BloomDrawVisitor setGridStepSize(int size){
        if (size < 1){
            throw new IllegalArgumentException("invalid raster size: "+size + ", must be >=1");
        }
        this.rasterSize=size;
        return this;
    }

    @Override
    public void visit(IRenderingElement element) {
        if (element instanceof BloomElement) {
            visit((BloomElement)element);
        } else {
            // Base AWTDrawVisitor handles all other element types
            super.visit(element);
        }
    }

    
    public void visit(BloomElement element){
        Graphics2D gc = getGraphics();
        Color savedColor = gc.getColor();

        final Rectangle2D bounds = calculateBounds(element);
        final Rectangle clipBounds = gc.getClipBounds();
        final Rectangle2D drawRect = (clipBounds != null) ? bounds.createIntersection(clipBounds) : bounds;
        this.setupValueCalulation(drawRect, transform, element);
        int w = (int)drawRect.getWidth();
        final BufferedImage image = new BufferedImage(
            w, 
            (int)drawRect.getHeight(), 
            BufferedImage.TYPE_4BYTE_ABGR);
        
        final int[] ccc = new int[w*rasterSize];

        double midPoint = .5 * rasterSize;

        // Loop through image row-by-row(outer)
        for (int y = 0; y < image.getHeight(); y += rasterSize) {
            // Loop col-by-col (inner)
            for (int x = 0; x < w; x += rasterSize) {

                final int color = this.calculateValue(x + midPoint, y + midPoint);
                
                // only in case of an actual color is given
                if (color != -1){
                    if (rasterSize == 1){
                        ccc[x] = color;
                    } else {
                        for (int row = 0; row<rasterSize; row++){
                            for (int col=0; col<rasterSize; col++){
                                if (x+col < w){
                                    int index = x + col + w*row;
                                    ccc[index] = color;
                                    
                                }
                            }
                        }
                    }
                }
                
            }
            // Write out the current rows
            for (int r = 0; r<rasterSize && y+r<image.getHeight(); r++){
                image.setRGB(0, y+r, w, 1, ccc, r*w, 0);
            }

            // Reset the array to all zeros
            Arrays.fill(ccc,0);

        }
        gc.drawImage(image, (int)Math.round(drawRect.getX()), (int)Math.round(drawRect.getY()), null);
        // Reset old color
        gc.setColor(savedColor);

    }

    
    protected Rectangle2D calculateBounds(BloomElement element) {
        Rectangle2D bb = transform.createTransformedShape(bounds(element)).getBounds2D();
        Rectangle2D bounds = new Rectangle2D.Double(bb.getX() - MARGIN, bb.getY() - MARGIN,
            bb.getWidth() + MARGIN * 2, bb.getHeight() + MARGIN * 2);
        return bounds;
    }

    public static Rectangle2D bounds(BloomElement element) {

        Rectangle2D bounds = new Rectangle.Double();
        for (BloomingPoint p : element.getBloomPoints()){
            double r = p.radius;
            Shape oval = new Ellipse2D.Double(p.xCoord - r,
                        p.yCoord - r, r * 2, r * 2);

            bounds.add(oval.getBounds2D());
        }
        return bounds;
    }
    
    private static final double ALPHA_RAD_SCALE = .28;
    private static final double ALPHA_AMPL_SCALE = 100;
    
    private static final double COLOR_RAD_SCALE = .48;
    private static final double COLOR_AMPL_SCALE = 1;
    
    protected int calculateValue(double x, double y) {
        try {
            double z1 = 0;
            double z2 = 0;
            double k1 = 0;
            int alpha = -1;
            boolean hasOnePoint=false;

            for (BloomingPoint p : this.points) {
                
                double sqrDist = (p.xCoord - x) * (p.xCoord - x) + (p.yCoord - y) * (p.yCoord - y);
                double dist = Math.sqrt(sqrDist); 
                if (dist > p.radius*1.5){
                    // If distance to this point is too long away - we skip it's contribution
                    continue;
                }
                hasOnePoint = true;

                // sums for alpha
                if (dist < p.radius*.5){
                    alpha = 255;
                } else {
                    k1 += guassian(sqrDist, p.radius*ALPHA_RAD_SCALE);
                }

                // sums for color
                double value = Math.max(0., 1. - dist / (.85*p.radius)) * p.value; 
                double guassianScaled = COLOR_AMPL_SCALE * guassian(sqrDist, p.radius*COLOR_RAD_SCALE); //b
                z1 += value * guassianScaled;
                z2 += guassianScaled;
            }

            if (!hasOnePoint)
                return -1;
            
            // Calculate alpha (if not already set)
            if (alpha < 0){
                double k01 = Utils.truncate(ALPHA_AMPL_SCALE*k1,0,1);
                alpha = (int) Math.floor(k01 * 255.0);
                alpha = Utils.truncate(alpha, 0, 255);
                if (alpha == 0) {
                    return -1;
                }
            }
            
            // Calculate value
            double val = Utils.truncate(z1 / z2, ColorGradient.MIN_VALUE, ColorGradient.MAX_VALUE);
            Color color = this.gradient.getColor(val);

            return color.getRGB() + ((alpha & 0xFF) << 24);

        } catch (Error | Exception e) {
            LOGGER.debug("Failed calculating bloom in point {{},{}}: {}",
                new Object[]{x,y,e.getMessage()}); 
            return Color.CYAN.getRGB();
        }
    }

    static double guassian(final double sqrDist, final double radius){
        return Math.exp(-sqrDist/(2*radius*radius));
    }

    /**
     * Local copy of the BloomElement points, with translated coordinates (CDK vs Java y-axis and scale)
     */
    private transient List<BloomingPoint> points;

    protected Rectangle2D setupValueCalulation(Rectangle2D bounds, AffineTransform af, BloomElement element) {
        
        final AffineTransform scaleTransform = new AffineTransform(af);
        double sX = scaleTransform.getScaleX();

        final AffineTransform translateTransform = new AffineTransform();
        translateTransform.translate(-bounds.getX(), -bounds.getY());
        
        // Loop through all points and convert them into scaled bloom-points
        this.points = new ArrayList<>(element.getBloomPoints().size());
        for (BloomingPoint po : element.getBloomPoints()) {
            double[] srcXY = { po.xCoord, po.yCoord };
            double[] tempXY = new double[2];
            double[] pXY = new double[2];
            scaleTransform.transform(srcXY, 0, tempXY, 0, 1);
            translateTransform.transform(tempXY, 0, pXY, 0, 1);
            this.points.add(new BloomingPoint(pXY[0], pXY[1], po.radius * sX, po.value));
        }
        Collections.sort(this.points, new Comparator<BloomingPoint>() {
            public int compare(BloomingPoint b1, BloomingPoint b2) {
                return Double.compare(b1.yCoord, b2.yCoord);
            }
        });
        return bounds;
    }
    
}

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
import java.util.ArrayList;
import java.util.List;

import org.openscience.cdk.renderer.elements.IRenderingElement;
import org.openscience.cdk.renderer.elements.IRenderingVisitor;
import org.openscience.cdk.renderer.elements.OvalElement;

/**
 * The {@code BloomElement} extends the {@link OvalElement} of CDK in order to not cause any warnings from CDK depiction code when calculating
 * the Bounds of the depiction. However, the default Bounds and padding added from the {@code BasicSceneGenerator} etc work well with our "bloom" 
 * depictions so we only create an oval element with 0 radius at the coordinates of the first atom encountered (i.e. we do not add any 'volume' 
 * to the bounds calculation).
 */
public class BloomElement extends OvalElement {

    private List<BloomingPoint> bloomPoints;
    
    public BloomElement(double x, double y) {
        super(x,y, 0, new Color(0,0,0,0));
        this.bloomPoints = new ArrayList<>();
    }

    public List<BloomingPoint> getBloomPoints(){
        return bloomPoints;
    }
    
    public void accept(final IRenderingVisitor visitor) {
        visitor.visit((IRenderingElement)this);
    }
    
    public void add(final BloomingPoint point) {
        this.bloomPoints.add(point);
    }

    public static class BloomingPoint {
        public final double xCoord;
        public final double yCoord;
        public final double radius;
        public final double value;
        
        public BloomingPoint(final double x, final double y, final double radius, final double value) {
            this.xCoord = x;
            this.yCoord = y;
            this.radius = radius;
            this.value = value;
        }

        public String toString() {
            return String.format("{%.2f:%.2f} | %.2f | %.3f", 
                xCoord,yCoord,radius,value);
        }
    }
}

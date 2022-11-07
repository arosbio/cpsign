/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.color.gradient.impl;

import java.awt.Color;
import java.awt.color.ColorSpace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.arosbio.color.CIELab;
import com.arosbio.color.gradient.ColorGradient;

public class MultipointGradient implements ColorGradient {
    
    private final ColorSpace colorSpace;
    private final List<GradientPoint> points;

    public MultipointGradient(GradientPoint p1, GradientPoint p2) {
        this.colorSpace = (ColorSpace) CIELab.getInstance();
        this.points = new ArrayList<>();
        this.points.add(p1);
        this.points.add(GradientPoint.of(Color.GRAY, 0.5F));
        this.points.add(p2);
    }

    public MultipointGradient(List<GradientPoint> points) {
        this(points, (ColorSpace) CIELab.getInstance());
    }

    public MultipointGradient(List<GradientPoint> points, ColorSpace colorSpace) {
        if (colorSpace == null) {
            this.colorSpace = (ColorSpace) CIELab.getInstance();
        } else {
            this.colorSpace = colorSpace;
        }
        this.points = new ArrayList<>(points);

        Collections.sort(this.points); 
    }

    public Color getColor(double val) {
        return produceColor((float) val);
    }

    private static float[] interpolate(final float[] a, final float[] b, final float t) {
        int length = Math.min(a.length, b.length);
        float[] res = new float[length];
        res[0] = a[0];
        for (int i = 0; i < length; i++) {
            res[i] = a[i] + (b[i] - a[i]) * t;
        }
        return res;
    }

    private Color produceColor(float value) {
        GradientPoint p1 = null;
        GradientPoint p2 = null;
        float[] vals = null;
        // float val = 0.0F;
        for (int i = 0; i < this.points.size() - 1; i++) {
            p1 = this.points.get(i);
            p2 = this.points.get(i + 1);

            if (value <= p1.getValue()) {
                // Before or on the first point - take the first color
                vals = p1.getColor().getColorComponents(this.colorSpace, null);
                break;
            } 
            if (value == p2.getValue()){
                // At the second point
                vals = p2.getColor().getColorComponents(this.colorSpace, null);
            }
            if (value < p2.getValue()) {
                // In-between these two points
                float[] a1s = p1.getColor().getColorComponents(this.colorSpace, null);
                float[] a2s = p2.getColor().getColorComponents(this.colorSpace, null);
                float val = (value - p1.getValue()) / Math.abs(p2.getValue() - p1.getValue());
                vals = interpolate(a1s, a2s, val);
                
                break;
            }
           
        }
        if (vals == null){
            // after the last point!
            vals = points.get(points.size()-1).getColor().getColorComponents(colorSpace, null);
        }

        float[] rgb = this.colorSpace.toRGB(vals);
        return new Color(ColorSpace.getInstance(ColorSpace.CS_sRGB), rgb, 1.0F);
    }
}

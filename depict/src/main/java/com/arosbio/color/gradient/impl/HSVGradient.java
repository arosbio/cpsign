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

import com.arosbio.color.gradient.ColorGradient;

public class HSVGradient implements ColorGradient {
    private Color c1;
    private Color c2;
    

    public HSVGradient(final Color c1, final Color c2) {
        this.c1 = c1;
        this.c2 = c2;
    }

    public Color getColor(final double value) {
        return new Color(calculateGradient((float)value));
    }

    private static float[] convert(final Color c) {
        final float[] hsb = new float[3];
        Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), hsb);
        return hsb;
    }
    
    private static float interpolate(final float a, final float b, final float v) {
        return a + (b - a) * v;
    }
    
    private int calculateGradient(float value) {
        final float[] h1 = convert(this.c1);
        final float[] h2 = convert(this.c2);
        final float h = interpolate(h1[0], h2[0], value);
        final float s = interpolate(h1[1], h2[1], value);
        final float b = interpolate(h1[2], h2[2], value);
        return Color.HSBtoRGB(h, s, b);
    }
}

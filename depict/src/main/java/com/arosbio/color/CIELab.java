/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.color;

import java.awt.color.ColorSpace;

public class CIELab extends ColorSpace {

    private static final long serialVersionUID = 5027741380892134289L;
    private static final ColorSpace CIEXYZ = ColorSpace.getInstance(1001);
    private static final double N = 0.13793103448275862D;
    private static CIELab INSTANCE = new CIELab();

    private CIELab() {
        super(1, 3);
    }

    public static CIELab getInstance() {
        return INSTANCE;
    }

    public float[] fromCIEXYZ(final float[] colorvalue) {
        final double l = f(colorvalue[1]);
        final double L = 116.0 * l - 16.0;
        final double a = 500.0 * (f(colorvalue[0]) - l);
        final double b = 200.0 * (l - f(colorvalue[2]));
        return new float[] { (float) L, (float) a, (float) b };
    }

    public float[] fromRGB(float[] rgbvalue) {
        final float[] xyz = CIEXYZ.fromRGB(rgbvalue);
        return fromCIEXYZ(xyz);
    }

    public float getMaxValue(int component) {
        return 128.0F;
    }

    public float getMinValue(int component) {
        return (component == 0) ? 0.0F : -128.0F;
    }

    public String getName(int idx) {
        return String.valueOf("Lab".charAt(idx));
    }

    public float[] toCIEXYZ(final float[] colorvalue) {
        final double i = (colorvalue[0] + 16.0) * 0.008620689655172414;
        final float X = (float) fInv(i + colorvalue[1] * 0.002);
        final float Y = (float) fInv(i);
        final float Z = (float) fInv(i - colorvalue[2] * 0.005);
        return new float[] { X, Y, Z };
    }

    public float[] toRGB(final float[] colorvalue) {
        final float[] xyz = toCIEXYZ(colorvalue);
        return CIEXYZ.toRGB(xyz);
    }

    private static double f(final double x) {
        if (x > 0.008856451679035631D) {
            return Math.cbrt(x);
        }
        return 7.787037037037037D * x + N;
    }

    private static double fInv(final double x) {
        if (x > 0.20689655172413793D) {
            return x * x * x;
        }
        return 0.12841854934601665D * (x - N);
    }

    private Object readResolve() {
        return getInstance();
    }

}

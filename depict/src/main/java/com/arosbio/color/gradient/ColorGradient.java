/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.color.gradient;

import java.awt.Color;

/**
 * An instance that represent a color gradient, that should be (normalized) to
 * being in the range [-1..1]. Min and max values can be queried using
 * {@link #MIN_VALUE} and
 * {@link #MAX_VALUE}.
 */
public interface ColorGradient {

  /**
   * The minimum value of all gradients
   */
  public static final double MIN_VALUE = -1;

  /**
   * The maximum value of all gradients
   */
  public static final double MAX_VALUE = 1;

  /**
   * Get the {@link Color} at a given {@code value} in the gradient
   * 
   * @param value should be in the range [-1..+1]
   * @return the {@link Color} in this location
   */
  public Color getColor(double value);
}

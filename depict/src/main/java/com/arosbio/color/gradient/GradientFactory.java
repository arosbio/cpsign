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
import java.util.Arrays;

import com.arosbio.color.gradient.impl.GradientPoint;
import com.arosbio.color.gradient.impl.MultipointGradient;

/**
 * A factory class for getting a {@link ColorGradient}. A {@link ColorGradient} is defined
 * for values in the range [-1..1]. 
 * <p>
 * Gradients generated in this factory can be any of these:
 * <ul>
 * 	<li>{@link #getRainbowGradient() rainbow}: going from red-&gt;orange-&gt;yellow-&gt;green-&gt;cyan-&gt;blue-&gt;magenta-&gt;red</li>
 * 	<li>{@link #getCustom2Points(Color, Color) 2 point}: Having c1 at -1 and c2 at 1, and a gradient between these end points</li>
 * 	<li>{@link #getCustom3Points(Color, Color, Color) 3 point}: Having c1 at -1, c2 at 0 and c3 at 1, and gradients between these colors</li>
 * 	<li>{@link #getBlueRedGradient() blue:red}: 2 point gradient with gray in between blue and red in each end</li>
 * 	<li>{@link #getRedBlueGradient() red:blue}: 2 point gradient with gray in between red and blue in each end</li>
 *  <li>{@link #getRedBlueRedGradient() red:blue:red}: 3 point gradient with gray in between -1: red, 0: blue, 1: red</li>
 * </ul>
 * 
 * @author Aros Bio AB
 *
 */
public class GradientFactory {
	
	private static final Color GRADIENT_NODE_COLOR = new Color(228, 228, 228);

	private static class RainbowGradient implements ColorGradient {
		@Override
		public Color getColor(double val) {
			return Color.getHSBColor((float)(0.5 + 0.5*val), 1, 1);
		}
	}
	
	
	/**
	 * A classic rainbow gradient going from red-&gt;orange-&gt;yellow-&gt;green-&gt;blue-&gt;magenta-&gt;red
	 * @return a {@code ColorGradient} defining the gradient
	 */
	public static ColorGradient getRainbowGradient(){
		return new RainbowGradient();
	}
	
	/**
	 * The default {@link ColorGradient}, same as {@link #getBlueRedGradient()}
	 * @return the default {@link ColorGradient}
	 */
	public static ColorGradient getDefaultBloomGradient() {
		return getBlueRedGradient();
	}
	
	/**
	 * Get a custom gradient from a JSON formatted string, must follow the correct
	 * format 
	 * @param json a valid json string
	 * @return a custom {@link ColorGradient}
	 */
	public static ColorGradient getCustomGradient(String json) {
		return new MultipointGradient(GradientPoint.fromJSON(json));
	}
	
	
	/**
	 * Creates a custom 3-points gradient, allows to set the intermediate color between the endpoint-values,
	 * 
	 * @param cNeg color at -1
	 * @param c0 color at 0
	 * @param cPos color at +1
	 * @param cBetween intermediate color between -1 and 0, and between 0 and +1
	 * @return a {@code ColorGradient} defining the gradient
	 */
	public static ColorGradient getCustom3Points(final Color cNeg, final Color c0, final Color cPos, final Color cBetween) {
		return new MultipointGradient(Arrays.asList(
				GradientPoint.of(cNeg, -.7f), // -1 end
				GradientPoint.of(cBetween, -.4f),
				GradientPoint.of(c0, 0f),
				GradientPoint.of(cBetween, .4f),
				GradientPoint.of(cPos, .7f) // +1 end
				));
	}
	
	/**
	 * Creates a custom 3-points gradient, uses a default for the color between the endpoint-values,
	 * 
	 * @param cNeg color at -1
	 * @param c0 color at 0
	 * @param cPos color at +1
	 * @return a {@code ColorGradient} defining the gradient
	 */
	public static ColorGradient getCustom3Points(final Color cNeg, final Color c0, final Color cPos) {
		return getCustom3Points(cNeg, c0, cPos, GRADIENT_NODE_COLOR);
	}
	
	/**
	 * Creates a custom 3-points gradient,  allows to set the intermediate color between the endpoint-values,
	 * 
	 * @param cNeg color at -1
	 * @param cPos color at +1
	 * @param cBetween intermediate color between -1 and +1
	 * @return a {@code ColorGradient} defining the gradient
	 */
	public static ColorGradient getCustom2Points(final Color cNeg, final Color cPos, final Color cBetween) {
		return new MultipointGradient(Arrays.asList(
				GradientPoint.of(cNeg, -.7f), // -1 end
				GradientPoint.of(cBetween, -.2f),
				GradientPoint.of(cBetween, .2f),
				GradientPoint.of(cPos, .7f) // +1 end
				));
	}
	
	/**
	 * Creates a custom 2-points gradient, uses a default for the color between the endpoint-values,
	 * 
	 * @param cNeg color at -1
	 * @param cPos color at +1
	 * @return a {@code ColorGradient} defining the gradient
	 */
	public static ColorGradient getCustom2Points(final Color cNeg, final Color cPos) {
		return getCustom2Points(cNeg, cPos, GRADIENT_NODE_COLOR);
	}
	
	
	public static ColorGradient getBlueRedGradient(){		
		return getCustom2Points(Color.BLUE, Color.RED);
	}
	
	public static ColorGradient getRedBlueRedGradient(){
		return getCustom3Points(Color.RED, Color.BLUE, Color.RED);
	}
	
	public static ColorGradient getRedBlueGradient(){
		return getCustom2Points(Color.RED, Color.BLUE);
	}
	
	public static ColorGradient getCyanMagenta() {
		return getCustom2Points(Color.CYAN, Color.MAGENTA, new Color(242, 242, 242));
	}
	
}

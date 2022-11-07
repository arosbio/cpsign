/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app.params.converters;

import org.beryx.awt.color.ColorFactory;

import com.arosbio.color.gradient.ColorGradient;
import com.arosbio.color.gradient.GradientFactory;
import com.arosbio.commons.StringUtils;
import com.arosbio.cpsign.app.params.CLIParameters.ColorScheme;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

public class ColorGradientConverter implements ITypeConverter<ColorGradient> {

	@Override
	public ColorGradient convert(String colorScheme) {
		String cleaned = StringUtils.stripQuotesAndEscape(colorScheme);
		// Here we're trying to get the standard Color Schemes
		try {
			return ColorScheme.getType(Integer.parseInt(cleaned)).getGradient();
		} catch (Exception e) {
			// No numeric thing
		}
		
		// Try rainbow
		cleaned = cleaned.toLowerCase();
		if (cleaned.equals("rainbow"))
			return GradientFactory.getRainbowGradient();
		
		// As a custom/string gradient
		try {
			String[] colors = cleaned.split(":");
			if (colors.length==2) {
				return GradientFactory.getCustom2Points(ColorFactory.web(colors[0]), ColorFactory.web(colors[1]));
			} else if (colors.length == 3) {
				return GradientFactory.getCustom3Points(ColorFactory.web(colors[0]), ColorFactory.web(colors[1]), ColorFactory.web(colors[2]));
			}
		} catch (Exception e) {

		}

		// Try as a Custom JSON color gradient 
		try {
			return GradientFactory.getCustomGradient(colorScheme);
		} catch(Exception e) {
		}

		throw new TypeConversionException("Argument \"" + colorScheme + "\" could not be parsed as a valid color scheme");

	}

}

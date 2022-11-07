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

import java.awt.Color;

import org.beryx.awt.color.ColorFactory;

import com.arosbio.commons.StringUtils;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

public class ColorConverter implements ITypeConverter<Color> { //, IParameterValidator {

	@Override
	public Color convert(String text) {
		try {
			return ColorFactory.web(StringUtils.stripQuotesAndEscape(text));
		} catch(IllegalArgumentException e){
			throw new TypeConversionException("\""+text+"\" cannot be parsed into a valid color, only a single color name or hex-encoded (0xAABBCC or \\#AABBCC) color accepted");
		}
	}


}

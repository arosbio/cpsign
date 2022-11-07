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

import java.io.IOException;
import java.net.URI;

import com.arosbio.io.UriUtils;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

public class URIConverter implements ITypeConverter<URI> {

	@Override
	public URI convert(String text) {
		try {
			return UriUtils.getURI(text);
		} catch(IllegalArgumentException | IOException e){
			throw new TypeConversionException("\""+text+"\" cannot be parsed into either a valid path or a valid URI");
		}
	}

}

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.ml.cp.nonconf.NCM;

import picocli.CommandLine.ITypeConverter;

public class NCMConverter implements ITypeConverter<NCM> {

	private static final Logger LOGGER = LoggerFactory.getLogger(NCMConverter.class);

	@Override
	public NCM convert(final String text) {
		LOGGER.debug("Converting {} to NCM", text);

		String[] splits = text.split(":");

		// Get the descriptor implementation
		NCM ncm = FuzzyServiceLoader.load(NCM.class, splits[0]);

		if (splits.length>1) {
			List<String> args = new ArrayList<>(Arrays.asList(splits));
			args.remove(0); // First one is the descriptor itself
			ConfigUtils.setConfigs(ncm, args, text);
		}

		return ncm;
	}

}

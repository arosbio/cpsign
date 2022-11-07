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
import com.arosbio.ml.algorithms.MLAlgorithm;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

public class MLAlgorithmConverter implements ITypeConverter<MLAlgorithm> {

	private static final Logger LOGGER = LoggerFactory.getLogger(MLAlgorithmConverter.class);
	
	@Override
	public MLAlgorithm convert(String text) {
		LOGGER.debug("Got the following argument for MLAlg: {}", text);
		String[] splits = text.split(":");
		
		MLAlgorithm alg = null;
		try {
			alg = FuzzyServiceLoader.load(MLAlgorithm.class, splits[0]);
			LOGGER.debug("Loaded user-specified ml-alg: {}", alg.getName());
		} catch (IllegalArgumentException e){
			throw new TypeConversionException(e.getMessage());
		}
		
		try {
			
			if (splits.length > 1) {
				LOGGER.debug("Parameters were given, will try to configure the alg");
				List<String> args = new ArrayList<>(Arrays.asList(splits));
				args.remove(0);
				ConfigUtils.setConfigs(alg, args, text);
			}
			return alg;
		} catch (IllegalArgumentException e){
			throw new TypeConversionException("Invalid parameter for " + alg.getName() + ": " + e.getMessage());
		}
	}

}

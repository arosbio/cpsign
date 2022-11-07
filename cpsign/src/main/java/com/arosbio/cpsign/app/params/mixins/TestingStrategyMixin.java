/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app.params.mixins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.commons.TypeUtils;
import com.arosbio.cpsign.app.params.converters.ConfigUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.ml.sampling.SingleSample;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.ml.testing.LOOCV;
import com.arosbio.ml.testing.TestingStrategy;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.TypeConversionException;

public class TestingStrategyMixin {

	private static final Logger LOGGER = LoggerFactory.getLogger(TestingStrategyMixin.class);

	@Option(names = {"-k","--cv-folds","--test-strategy"}, 
			description = "The testing strategy to use, three different options possible: " + KFoldCV.STRATEGY_NAME + ", " + SingleSample.NAME + " and " + LOOCV.NAME+
			". There are some extra parameters that can be set, run "+ParameterUtils.RUN_EXPLAIN_ANSI_ON+"explain test-strategy"+ParameterUtils.ANSI_OFF+" for all available parameters%n"+
			ParameterUtils.DEFAULT_VALUE_LINE,
			converter = TestingStrategyConverter.class,
			defaultValue="k-fold:k=10",
			paramLabel = ArgumentType.TEXT)
	public TestingStrategy testStrategy = new KFoldCV(10);

	public static class TestingStrategyConverter implements ITypeConverter<TestingStrategy> {

		@Override
		public TestingStrategy convert(String input)  {
			LOGGER.debug("Got the following argument for Testing-strategy: {}", input);

			TestingStrategy strat;
			try {

				if (input == null || input.isEmpty()) {
					throw new TypeConversionException("Testing strategy cannot be empty");
				}

				input = input.trim();

				// Short hand - the rest is done in standard way
				if (TypeUtils.isInt(input)) {
					// K-fold
					return new KFoldCV(Integer.parseInt(input));
				}

				// Textual ones
				String[] splits = input.split(":");

				strat = FuzzyServiceLoader.load(TestingStrategy.class, splits[0]);
			} catch (Exception e) {
				throw new TypeConversionException("Invalid testing strategy: '" + e.getMessage()+'\'');
			}

			try {
				// Textual ones
				String[] splits = input.split(":");
				
				if (splits.length > 1) {
					LOGGER.debug("Parameters were given, will try to configure the testing-strategy");
					List<String> args = new ArrayList<>(Arrays.asList(splits));
					args.remove(0); // First one is the implementation type 
					ConfigUtils.setConfigs(strat, args, input);
				}

				return strat;

			} catch (TypeConversionException e) {
				// Pass along
				throw e;
			} catch (Exception e) {
				throw new TypeConversionException("Invalid testing strategy: '" + e.getMessage()+'\'');
			}

		}

	}

}



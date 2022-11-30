/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.testing.utils;

import java.util.Arrays;
import java.util.List;

import com.arosbio.commons.config.BooleanConfig;
import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.commons.config.IntegerConfig;
import com.google.common.collect.Range;

public class TestStrategiesUtils {

	public final static List<String> shuffleParamNames = Arrays.asList("shuffle");
	public final static ConfigParameter shuffleParameter = new BooleanConfig.Builder(shuffleParamNames, true).build();
	
	public final static List<String> numRepParamNames = Arrays.asList("numRepeat", "nRep");
	public final static ConfigParameter numRepParameter = new IntegerConfig.Builder(numRepParamNames, 1)
		.range(Range.atLeast(1))
		.description("Num repeats that the strategy should be performed, using different seeds for shuffling").build();
	
	public final static List<String> stratifiedParamNames = Arrays.asList("stratified", "stratify");
	public final static ConfigParameter stratifiedParameter = new BooleanConfig.Builder(stratifiedParamNames, false)
			.description("If the splitting should be stratified (i.e. preserving the class-ratios in "+
			"the test and training splits). Note that stratification is only valid for classification!")
			.build();

}

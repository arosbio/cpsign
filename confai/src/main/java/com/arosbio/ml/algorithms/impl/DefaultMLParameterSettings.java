/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.algorithms.impl;

import java.util.Arrays;
import java.util.List;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.commons.config.Configurable.Sorter;
import com.arosbio.commons.config.Configurable.Sorter.Direction;
import com.arosbio.commons.config.Configurable.Sorter.Priority;
import com.arosbio.commons.config.NumericConfig;
import com.google.common.collect.Range;

public class DefaultMLParameterSettings {

	public static final double DEFAULT_STEPSIZE = 1.0;
	
	public static final double DEFAULT_C = 50.0;
	public static final double DEFAULT_SVR_EPSILON = 0.1;
	public final static double DEFAULT_EPSILON = 0.001;
	// Config names
	public static final List<String> COST_PARAM_NAMES = Arrays.asList("cost", "C");
	public static final List<String> EPSILON_PARAM_NAMES = Arrays.asList("epsilon", "eps");
	public static final List<String> SVR_EPSILON_PARAM_NAMES = Arrays.asList("svrEpsilon", "p");
	
	public static final ConfigParameter COST_CONFIG = new NumericConfig.Builder(COST_PARAM_NAMES, DEFAULT_C)
		.range(Range.atLeast(0d))
		.defaultGrid(CollectionUtils.listRange(-4, 12, 2,2))
		.description("Cost-parameter for errors in Cost-parameterized SVC and SVR")
		.sorting(new Sorter(Priority.HIGH, Direction.PREFER_LOWER))
		.build();
	public static final ConfigParameter SVR_EPSILON_CONFIG = new NumericConfig.Builder(SVR_EPSILON_PARAM_NAMES, DEFAULT_SVR_EPSILON)
		.range(Range.atLeast(0d))
		.description("Epsilon in loss function of epsilon-SVR")
		.build();
	public static final ConfigParameter EPSILON_CONFIG = new NumericConfig.Builder(EPSILON_PARAM_NAMES,DEFAULT_EPSILON)
		.range(Range.atLeast(0d))
		.description("Tolerance of termination criterion").build();
}

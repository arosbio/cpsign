/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.sampling;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.ml.io.impl.PropertyNameSettings;

public abstract class SamplingStrategyUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(SamplingStrategyUtils.class);


	public static SamplingStrategy fromProperties(Map<String,Object> props)
			throws IllegalArgumentException {
		SamplingStrategy strategy = FuzzyServiceLoader.load(SamplingStrategy.class, TypeUtils.asInt(props.get(PropertyNameSettings.SAMPLING_STRATEGY_KEY)));

		List<ConfigParameter> possibleParams = strategy.getConfigParameters();
		if (possibleParams.isEmpty())
			return strategy;

		try {
			strategy.setConfigParameters(CollectionUtils.dropNullValues(props));
			return strategy;
		} catch (Exception e) {
			LOGGER.debug("Failed setting the strategy parameters using config-api. Attempting to use the old way",e);
			throw new IllegalArgumentException("Could not load the sampling strategy properly");
		}

	}

	public static void validateTrainSplitIndex(SamplingStrategy strategy, int index){
		if (index <0 || index >= strategy.getNumSamples()){
			throw new IllegalArgumentException("Invalid training split index: " +index + ", only allowed indices are in the range [0,"+(strategy.getNumSamples()-1)+"]");
		}
	}


}

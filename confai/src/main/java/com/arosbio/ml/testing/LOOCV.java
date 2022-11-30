/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.testing;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.mixins.Aliased;
import com.arosbio.data.Dataset;
import com.arosbio.data.splitting.LOOSplitter;
import com.arosbio.ml.testing.utils.TestStrategiesUtils;
import com.arosbio.ml.testing.utils.TestTrainWrapper;

public class LOOCV implements TestingStrategy, Aliased {
	
	public static final String NAME = "LOO_CV";
	public static final String[] ALIASES = new String[] {"LeaveOneOutCV"};


	private static final Logger LOGGER = LoggerFactory.getLogger(LOOCV.class);
	
	
	private long rngSeed = GlobalConfig.getInstance().getRNGSeed();
	private boolean shuffle=true;
	
	public boolean hasDescription() {
		return true;
	}
	
	@Override
	public String getDescription() {
		return "Performs a leave-one-out cross-validation testing. I.e. for N examples - N test-train splits will be generated, "
				+ "each one with a single example in the test-set and N-1 examples in the training set. Extremely computationally demanding.";
	}
	
	@Override
	public String getName() {
		return NAME;
	}
	
	@Override
	public String[] getAliases() {
		return ALIASES;
	}
	
	@Override
	public void setSeed(long seed) {
		rngSeed = seed;
	}

	@Override
	public Long getSeed() {
		return rngSeed;
	}

	@Override
	public int getNumberOfSplitsAndValidate(Dataset data) throws IllegalArgumentException {
		if (data == null || data.getDataset().isEmpty())
			throw new IllegalArgumentException("Must supply data to split");
		return data.getDataset().size();
	}

	@Override
	public Iterator<TestTrainSplit> getSplits(Dataset data) {
		return new TestTrainWrapper(new LOOSplitter.Builder()
			.findLabelRange(false)
			.shuffle(shuffle)
			.seed(rngSeed)
			.build(data));
	}
	
	public String toString() {
		return "Leave-one-out cross-validation";
	}


	@Override
	public List<ConfigParameter> getConfigParameters() {
		return Arrays.asList(TestStrategiesUtils.shuffleParameter);
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalArgumentException {
		for (Map.Entry<String,Object> p : params.entrySet()) {
			if (CollectionUtils.containsIgnoreCase(TestStrategiesUtils.shuffleParamNames, p.getKey())) {
				try {
					shuffle = TypeUtils.asBoolean(p.getValue());
				} catch (Exception e) {
					LOGGER.debug("Got parameter shuffle with invalid value: {}", e.getMessage());
					throw Configurable.getInvalidArgsExcept(p.getKey(), p.getValue());
				}
			}
		}
	}

	public LOOCV clone(){
        LOOCV c = new LOOCV();
		c.shuffle = shuffle;
		c.rngSeed = rngSeed;
        return c;
    }

}

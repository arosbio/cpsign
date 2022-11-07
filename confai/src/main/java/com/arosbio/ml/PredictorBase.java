/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml;

import com.arosbio.commons.GlobalConfig;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.io.ModelInfo;

public abstract class PredictorBase implements Predictor {

	protected ModelInfo info;
	protected long seed = GlobalConfig.getInstance().getRNGSeed();
	
	@Override
	public ModelInfo getModelInfo() {
		return info;
	}

	@Override
	public void setModelInfo(ModelInfo info) {
		this.info = info;
	}

	@Override
	public Long getSeed() {
		return seed;
	}

	@Override
	public void setSeed(long seed) {
		this.seed = seed;
	}
	
	// Overrides the method in Predictor-interface as otherwise this class has the default Object.clone method which does not 
	// match the one in the Predictor-interface and cause compilation-problems 
	@Override
	public abstract Predictor clone();
	
}

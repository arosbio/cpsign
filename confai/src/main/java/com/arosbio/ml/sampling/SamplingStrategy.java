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

import java.util.Map;

import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.mixins.HasID;
import com.arosbio.commons.mixins.Named;
import com.arosbio.data.Dataset;

public interface SamplingStrategy extends Cloneable, Configurable, Named, HasID {
	
	public int getNumSamples();

	public TrainSplitIterator getIterator(Dataset dataset)
			throws IllegalArgumentException;
	
	public TrainSplitIterator getIterator(Dataset dataset, long seed)
			throws IllegalArgumentException;

	public Map<String, Object> getProperties();

	public boolean equals(Object obj);
	
	public SamplingStrategy clone();
	
	public boolean isFolded();
	
	public boolean isStratified();

}

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

import java.util.Iterator;

import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.mixins.Described;
import com.arosbio.commons.mixins.Named;
import com.arosbio.commons.mixins.RequireRNGSeed;
import com.arosbio.data.Dataset;

public interface TestingStrategy extends Configurable, Described, Named, RequireRNGSeed {
	
	/**
	 * Check the number of test-train splits that will be performed, which might differ depending on the {@link Dataset} (i.e. if using Leave-one-out CV) or might 
	 * be a fixed number. Also performs validation so that the testing-strategy is possible for this data.
	 * @param data The data to check
	 * @return The number of test-train splits that will be made
	 * @throws IllegalArgumentException If the <code>TestingStrategy</code> cannot be used with the given {@link com.arosbio.data.Dataset}
	 */
	public int getNumberOfSplitsAndValidate(Dataset data) 
			throws IllegalArgumentException;
	
	public Iterator<TestTrainSplit> getSplits(Dataset data);

	/**
	 * Return a clone with identical settings but no internal state of splits etc.
	 * This is due to implementations can be mutable, thus may be changed mid run and 
	 * give unexpected behaviour
	 * @return A clone with identical settings
	 */
	public TestingStrategy clone();

}

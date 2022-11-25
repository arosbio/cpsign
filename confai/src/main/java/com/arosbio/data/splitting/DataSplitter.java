/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.splitting;

import java.util.Iterator;
import java.util.NoSuchElementException;

public interface DataSplitter extends Iterator<DataSplit> {
    
    /**
	 * Get a specific {@link DataSplit} for a given index. Note; this should <b>not</b> interfere with the {@link #hasNext()} or {@link #next()} methods 
     * of the {@link Iterator}. The {@code index} should be in the range [{@link #getMinSplitIndex()},{@link #getMaxSplitIndex()}]
	 * @param index The index [min split index , max split index]
	 * @return The {@link DataSplit} for the given {@code index}
	 * @throws NoSuchElementException If index was out of accepted limits
	 */
	public DataSplit get(int index) throws NoSuchElementException;
	
    /**
     * The minimum split index. Is 0 for all ConfAI implementations
     * @return the min split index
     */
	public int getMaxSplitIndex();
	
    /**
     * The maximum split index. This depends on the settings, such as if repeated 
     * sampling is performed or not.
     * @return the maximum split index
     */
	public int getMinSplitIndex();
}

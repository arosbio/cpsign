/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp.tcp;

import com.arosbio.data.Dataset;
import com.arosbio.ml.cp.ConformalPredictor;
import com.arosbio.ml.cp.nonconf.NCM;

/**
 * Transductive Conformal Predictor
 * @author ola
 *
 */
public interface TCP extends ConformalPredictor {
	
	/**
	 * If the TCP Predictor has been 'trained', i.e. called the {@link #train(Dataset)}
	 * method, this method can be used to retrieve the data used for deriving the predictor.
	 * Returns {@code null} if not {@link #train(Dataset)} has not been called.
	 * @return the current {@link com.arosbio.data.Dataset Dataset} or {@code null}
	 */
	public Dataset getDataset();
	
	public NCM getNCM();
	
}

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.transform.scale;


import com.arosbio.data.transform.Transformer;

/**
 * FeatureScalers scale individual columns or all columns after a specific
 * scheme. They need to be saved from training and kept so the same transformation
 * can be applied at prediction time. Thus they should all implement the Serializable 
 * interface. 
 * 
 * @author staffan
 *
 */
public interface FeatureScaler extends Transformer {
	
	static final String CONVERTING_SPARSE_TO_DENSE_WARNING_MSG = "Note: using this transformer forces to store all features explicitly and removes sparseness in data, which could lead to memory issues.";

}

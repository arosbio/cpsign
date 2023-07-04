/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.transform.feature_selection;

import java.util.List;

import com.arosbio.data.transform.Transformer;

/**
 * FeatureSelectors are {@link Transformer} instances that removes columns, i.e. dimensions, in the datasets.
 * E.g. by variance or more advanced strategies. They need to be saved and should implement {@link java.io.Serializable} interface.
 * 
 * 
 * @author staffan
 *
 */
public interface FeatureSelector extends Transformer {
	
	/**
	 * Get the features that will be removed by this {@link FeatureSelector}, this will only
	 * be available once the {@link Transformer#fit(java.util.Collection) fit(Collection)} method has been called 
	 * and the appropriate calculations has been performed
	 * @return A copy of the removed feature indices
	 * @throws IllegalStateException In case the {@link Transformer#fit(java.util.Collection) fit(Collection)} method has not been called
	 */
	public List<Integer> getFeatureIndicesToRemove() throws IllegalStateException;
	
}

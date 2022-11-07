/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cheminf;

import java.util.Map;
import java.util.Set;

import com.arosbio.data.NamedLabels;

public interface ChemClassifier extends ChemPredictor {

	/**
	 * The mapping between numeric label used in the predictor to the name of the label
	 * @return A {@link java.util.Map Map} from numeric -&gt; String label
	 */
	public Map<Integer,String> getLabels();
	
	/**
	 * Get the internal representation of numeric -&gt; textual labels
	 * @return the {@link com.arosbio.data.NamedLabels NamedLabels}
	 */
	public NamedLabels getNamedLabels();
	
	/**
	 * Get all labels used, as a non-ordered {@link java.util.Set Set}. This method returns the 
	 * same thing as {@link #getLabels()}<code>.values();</code>
	 * @return A {@link java.util.Set Set} of Labels
	 */
	public Set<String> getLabelsSet();
	
	/**
	 * Update the labels, note that the keys of the mapping correspond to the numeric value
	 * used in the {@link com.arosbio.data.Dataset Dataset} class. The keys must be same as the ones returned
	 * by {@link #getLabels()} from the {@link com.arosbio.data.Dataset Dataset}.
	 * @param labels The new label-names
	 * @throws IllegalArgumentException If the keys does not correspond correctly to the ones used
	 */
	public void setLabels(Map<Integer, String> labels) throws IllegalArgumentException;
	
	/**
	 * Update the label names with mapping "old name"-&gt;"new name"
	 * @param newLabels Mapping "old name"-&gt;"new name"
	 * @throws IllegalArgumentException If "old name" does not exist
	 */
	public void updateLabels(Map<String,String> newLabels) throws IllegalArgumentException;
	
}

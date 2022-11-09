/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.transform.duplicates;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.transform.duplicates.DuplicateResolvingUtils.DuplicateEntry;

public class UseVoting implements DuplicatesResolverTransformer {
	
	public static final String DESCRIPTION = "For classification problems - use voting so that the label with the most votes is kept. Ties removes all duplicate records.";
	public static final String NAME = "Voting";
	private static final Logger LOGGER = LoggerFactory.getLogger(UseVoting.class);
	
	private transient TransformInfo info;
	private boolean inPlace = true;
	
	@Override
	public boolean isTransformInPlace() {
		return inPlace;
	}

	@Override
	public UseVoting transformInPlace(boolean inPlace) {
		this.inPlace = inPlace;
		return this;
	}
	
	@Override
	public String getDescription() {
		return DESCRIPTION; 
	}
	
	@Override
	public String getName() {
		return NAME;
	}
	
	public String toString() {
		return NAME;
	}
	
	public UseVoting clone() {
		return new UseVoting();
	}

	@Override
	public boolean isFitted() {
		return true;
	}
	
	@Override
	public UseVoting fit(Collection<DataRecord> data) {
		return this;
	}

	@Override
	public SubSet fitAndTransform(SubSet data) throws TransformationException {
		return transform(data);
	}

	@Override
	public SubSet transform(SubSet data) throws IllegalStateException {
		LOGGER.debug("Applying Duplicate-resolving transformer {}", this);
		
		SubSet transformed = inPlace ? data : data.clone(); 
		
		int initialSize = transformed.size();
		
		Set<DuplicateEntry> dups = DuplicateResolvingUtils.findDuplicates(transformed);
		
		for (DuplicateEntry entry : dups) {
			Map<Double,Integer> labelFreq = CollectionUtils.countFrequencies(entry.getLabels());
			if (labelFreq.size()==1) {
				// Single label - keep this one!
				entry.getRemainingRecord().setLabel(labelFreq.keySet().iterator().next());
			} else {
				// Loop through to find the largest one
				int maxFrequency = -1;
				Double labelForMax = null; 
				for (Map.Entry<Double, Integer> ent : labelFreq.entrySet()) {
					if (ent.getValue() > maxFrequency) {
						maxFrequency = ent.getValue();
						labelForMax = ent.getKey();
					} else if (ent.getValue() == maxFrequency) {
						// Reset label to null if there's a tie!
						labelForMax = null;
					}
				}
				if (labelForMax != null) {
					entry.getRemainingRecord().setLabel(labelForMax);
				} else {
					transformed.remove(entry.getRemainingRecord());
				}
				
			}
		}
		
		info = new TransformInfo(initialSize-transformed.size(), dups.size());
		
		LOGGER.debug("Finished transformer: " + info);
		
		return transformed;
	}

	@Override
	public FeatureVector transform(FeatureVector object) throws IllegalStateException {
		return object;
	}

	@Override
	public boolean appliesToNewObjects() {
		return false;
	}
	
	@Override
	public boolean applicableToClassificationData() {
		return true;
	}

	@Override
	public boolean applicableToRegressionData() {
		return false;
	}

	@Override
	public TransformInfo getLastInfo() {
		return info;
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		return new ArrayList<>();
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
		// do nothing
	}
}
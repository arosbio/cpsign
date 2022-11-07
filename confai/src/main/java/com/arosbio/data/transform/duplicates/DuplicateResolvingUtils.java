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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.arosbio.data.DataRecord;

public class DuplicateResolvingUtils {

	/**
	 * The <code>DuplicateEntry</code> class keeps track of a DataRecord that 
	 * had identical FeatureVectors in the list of DataRecords. 
	 * @author staffan
	 *
	 */
	public static class DuplicateEntry {
		private final DataRecord object;
		private final List<Double> foundLabels;
		public DuplicateEntry(DataRecord object, List<Double> labels) {
			this.object = object;
			this.foundLabels = labels;
		}

		public DataRecord getRemainingRecord() {
			return object;
		}
		public List<Double> getLabels(){
			return foundLabels;
		}
	}

	/**
	 * Finds duplicates - keeps the first occurrence but substitutes the label with Double.NaN,
	 * the remaining duplicates are removed from the list of records and only their labels
	 * are returned in the {@link DuplicateEntry} that is returned 
	 * @param records the records
	 * @return set of duplicates
	 */
	public static Set<DuplicateEntry> findDuplicates(List<DataRecord> records){
		Set<DuplicateEntry> result = new HashSet<>();


		// Outer loop
		for (int i=0; i<records.size()-1; i++) {
			// new list for every new potential feature
			List<Double> foundYs = new ArrayList<>();

			DataRecord currRec = records.get(i);

			foundYs.add(currRec.getLabel());

			// Inner loop of the remaining part of the dataset
			for (int j=i+1; j<records.size(); j++){
				if (records.get(j).getFeatures().equals(currRec.getFeatures())){
					// Found matching features
					foundYs.add(records.get(j).getLabel());
					records.remove(j);
					j--; // re-do current index as list has shifted 
				}
			}

			// Only have to care if we found a duplicate
			if (foundYs.size() > 1){
				// Set NaN to make sure this is updated in the Transformer after this
				currRec.setLabel(Double.NaN);
				result.add(new DuplicateEntry(currRec, foundYs));
			}

		} // end outer loop

		return result;
	}

	/**
	 * Finds duplicates - keeps the first occurrence but substitutes the label with Double.NaN,
	 * the remaining duplicates are removed from the list of records and only their labels
	 * are returned in the {@link DuplicateEntry} that is returned 
	 * @param records the records
	 * @return set of duplicates
	 */
	public static Set<DuplicateEntry> findDuplicatesKeepLast(List<DataRecord> records){
		Set<DuplicateEntry> result = new HashSet<>();


		// Outer loop - loop backwards
		for (int i=records.size()-1; i>0; i--) {
			// new list for every new potential label
			List<Double> foundYs = new ArrayList<>();

			DataRecord currRec = records.get(i); 

			foundYs.add(currRec.getLabel());
			List<Integer> recIndeciesToRm = new ArrayList<>();

			// Inner loop of the remaining part of the dataset (backwards as well)
			for (int j=i-1; j>=0; j--){
				if (records.get(j).getFeatures().equals(currRec.getFeatures())){
					// Found matching features
					foundYs.add(records.get(j).getLabel());
					recIndeciesToRm.add(j);
				}
			}

			// Remove the indices (they are reverse-order so indices are not scrambled)
			for (int rmIndex : recIndeciesToRm) {
				records.remove(rmIndex);
			}

			// Only have to care if we found a duplicate
			if (foundYs.size() > 1){
				// Set NaN to make sure this is updated in the Transformer after this
				currRec.setLabel(Double.NaN);
				result.add(new DuplicateEntry(currRec, foundYs));
				i -= recIndeciesToRm.size(); 
			}

		} // end outer loop

		return result;
	}

}

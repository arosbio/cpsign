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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

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
	 * Find duplicates in the given list (regarding the <b>features</b>)
	 * 
	 * This method removes duplicates in the given list but keeps
	 * the first occurrence, but sets the label of the record to {@code Double.NaN}.
	 * The calling code should use the returned {@link DuplicateEntry} in order to
	 * set an appropriate label for the remaining DataRecord.
	 * 
	 * <b>NOTE:</b> this method performs the check by computing hash codes for the feature vectors, if they are
	 * of different types (i.e. {@code records} contains both dense and sparse feature vectors) the result will not be 
	 * correct - as the hash code implementation differs between these feature vectors.
	 * @param records a list of records to check, <b>Note: it will be altered if duplicates are found</b>
	 * @return duplicate entries to handle 
	 * 
	 */
	public static Set<DuplicateEntry> findDuplicates(List<DataRecord> records){

		// Do one pass of hashing through the full dataset
		Map<Integer, List<Pair<Integer,DataRecord>>> hashedData = new HashMap<>(records.size());
		for (int i = 0; i<records.size(); i++){
			DataRecord r = records.get(i);
			int hash = r.getFeatures().hashCode();
			if (!hashedData.containsKey(hash)){
				hashedData.put(hash, new ArrayList<>());
			}
			List<Pair<Integer,DataRecord>> recs = hashedData.get(hash);
			recs.add(Pair.of(i, r));
		}

		
		Set<DuplicateEntry> duplicates = new HashSet<>();
		List<Integer> indicesToRemove = new ArrayList<>();
		// Go through each set of records with identical hash to find duplicates
		for (List<Pair<Integer,DataRecord>> recsInHash : hashedData.values()){
			if (recsInHash.size() <2){
				continue;
			}
			Pair<Set<DuplicateEntry>,List<Integer>> duplicatesAndIndices = findUsingHashHelper(recsInHash);

			duplicates.addAll(duplicatesAndIndices.getLeft());
			indicesToRemove.addAll(duplicatesAndIndices.getRight());
		}

		// Sort and remove all extra duplicates 
		Collections.sort(indicesToRemove, Comparator.reverseOrder());
		for (int toRM : indicesToRemove){
			records.remove(toRM);
		}

		return duplicates;
	}

	/**
	 * Helper method for the {@link #findDuplicates(List)}, here doing an Ordo(N*N) check within 
	 * each bucket after the hashing of the input.
	 * @param hashEqual buckets with equal hash codes
	 * @return the duplicate information and the list of indices in original dataset to remove
	 */
	private static Pair<Set<DuplicateEntry>,List<Integer>> findUsingHashHelper(List<Pair<Integer,DataRecord>> hashEqual){
		Set<DuplicateEntry> result = new HashSet<>();
		List<Integer> indicesToDrop = new ArrayList<>();


		// Outer loop
		for (int i=0; i<hashEqual.size()-1; i++) {
			// new list for every new potential feature
			List<Double> foundYs = new ArrayList<>();

			Pair<Integer,DataRecord> entry = hashEqual.get(i);
			DataRecord currentRecord = entry.getRight();

			foundYs.add(currentRecord.getLabel());

			// Inner loop of the remaining part of the set
			for (int j=i+1; j<hashEqual.size(); j++){
				if (hashEqual.get(j).getRight().getFeatures().equals(currentRecord.getFeatures())){
					// Found matching features
					Pair<Integer,DataRecord> toRm = hashEqual.get(j);
					foundYs.add(toRm.getRight().getLabel());
					indicesToDrop.add(toRm.getLeft());

					// Remove from the list of equal hash 
					hashEqual.remove(j);
					j--; // re-do current index as list has shifted 
				}
			}

			// Only have to care if we found a duplicate
			if (foundYs.size() > 1){
				// Set NaN to make sure this is updated in the Transformer after this
				currentRecord.setLabel(Double.NaN);
				result.add(new DuplicateEntry(currentRecord, foundYs));
			}

		} // end outer loop

		return Pair.of(result, indicesToDrop);
	}

}

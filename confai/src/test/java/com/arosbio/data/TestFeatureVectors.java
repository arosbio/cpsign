/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector.Feature;
import com.arosbio.data.transform.format.MakeDenseTransformer;
import com.arosbio.data.transform.scale.Standardizer;
import com.arosbio.testutils.TestDataLoader;
import com.google.common.collect.BoundType;
import com.google.common.collect.Range;


public class TestFeatureVectors {

	@Test
	public void testDenseVector2() {
		// zero size
		FeatureVector sp = new SparseVector(new ArrayList<SparseFeature>());
		FeatureVector v =  new DenseVector(sp,-1);
		Assert.assertEquals(-1, sp.getLargestFeatureIndex());
		Assert.assertEquals(-1, v.getLargestFeatureIndex());

		// 1 length
		sp.withFeature(1, 1d);
		Assert.assertEquals(1, sp.getLargestFeatureIndex());
		v = new DenseVector(sp, 1);
		Assert.assertEquals(1, v.getLargestFeatureIndex());
		Assert.assertEquals(0, v.getFeature(0),0.000001);
		Assert.assertEquals(1, v.getFeature(1),0.000001);

		// 2 length
		sp.withFeature(5, 2.5);
		Assert.assertEquals(5, sp.getLargestFeatureIndex());
		Assert.assertEquals(2, sp.getNumExplicitFeatures());
		v = new DenseVector(sp,-1);
		Assert.assertEquals(5, v.getLargestFeatureIndex());
		Assert.assertEquals(6, v.getNumExplicitFeatures());

		// padd to specified width
		DenseVector dense = new DenseVector(sp, 10);
		for (double d : dense.getInternalArray()){
			// When using sparse -> dense all values should be either the explit ones or zeros
			Assert.assertTrue(Double.isFinite(d));
		}
		Assert.assertEquals(10, dense.getLargestFeatureIndex());
		Assert.assertEquals(11, dense.getNumExplicitFeatures());


		assertOrdered(v);
		assertOrdered(sp);

		doDeleteFeaturesTest(false);

		// Create a new DenseVector with more features, copy from the previous with fewer features
		DenseVector dense_longer = new DenseVector(dense, 20);
		double[] internal1 = dense.getInternalArray();
		double[] internal_longer = dense_longer.getInternalArray();
		Assert.assertEquals(21, internal_longer.length); // index + 20 features
		// First instances should match
		for (int i=0; i<internal1.length; i++){
			Assert.assertEquals(internal1[i], internal_longer[i],0.000001);
		}
		// Then there should be NaNs
		for(int i=internal1.length; i<internal_longer.length;i++){
			Assert.assertTrue(Double.isNaN(internal_longer[i]));
		}

		// Create new Dense but with fewer features
		DenseVector shorter = new DenseVector(dense, 7);
		double[] internal_shorter = shorter.getInternalArray();
		for (int i=0; i<internal_shorter.length; i++){
			Assert.assertEquals(internal1[i], internal_shorter[i],0.000001);
		}

	}


	public void assertOrdered(FeatureVector v) {
		int currIndex = -1;
		for (Feature sf: v) {
			Assert.assertTrue(sf.getIndex() > currIndex);
			currIndex = sf.getIndex();
		}
	}

	@Test
	public void testVectorRemove() {
		FeatureVector v = new DenseVector(new double[] {0d,1d,2d,3d,4d,5d,6d,7d,8d,9d,10d});
		FeatureVector sp = new SparseVector(v);
		assert1to10(v);
		assert1to10(sp);
		Assert.assertEquals(11, v.getNumExplicitFeatures());
		Assert.assertEquals(10, v.getLargestFeatureIndex());
		Assert.assertEquals(10, sp.getNumExplicitFeatures()); // 1 less due to a 0 in the first position
		Assert.assertEquals(10, sp.getLargestFeatureIndex());

		FeatureVector clone = v.clone();
		FeatureVector spClone = sp.clone();

		v.removeFeatureIndex(1);
		Assert.assertEquals(10, v.getNumExplicitFeatures());
		Assert.assertEquals(9, v.getLargestFeatureIndex());

		sp.removeFeatureIndex(2);
		Assert.assertEquals(9, sp.getNumExplicitFeatures());
		Assert.assertEquals(9, sp.getLargestFeatureIndex());

		clone.removeFeatureIndices(Arrays.asList(0,2,4,6));
		Assert.assertEquals(11-4, clone.getNumExplicitFeatures());
		Assert.assertEquals(10-4, clone.getLargestFeatureIndex());

		spClone.removeFeatureIndices(Arrays.asList(0,2,4,6));
		Assert.assertEquals(11-4, spClone.getNumExplicitFeatures());
		Assert.assertEquals(10-4, spClone.getLargestFeatureIndex());
	}

	public void assert1to10(FeatureVector v) {
		Iterator<Feature> it = v.iterator();

		for (int i=0; i<=10; i++) {
			Feature f = it.next();
			if (v instanceof SparseVector && i==0) {
				i++; // the 0 index not explicitly saved! thus we start at 1 instead
			}
			Assert.assertEquals(i, f.getIndex());
			Assert.assertEquals(i, f.getValue(), 0.000001);
		}
		Assert.assertFalse(it.hasNext());
	}

	@Test
	public void testSparseVector() {
		FeatureVector v = new SparseVector()
			.withFeature(2, 4d)
			.withFeature(5, 3d)
			.withFeature(6, -4d)
			.withFeature(0, 40d);

		Assert.assertEquals(3d, v.getFeature(5), 0.00001);
		Assert.assertEquals(40d, v.getFeature(0), 0.00001);
		Assert.assertEquals(0d, v.getFeature(10), 0.00001);

		try {
			v.getFeature(-1);
			Assert.fail();
		} catch (IndexOutOfBoundsException e) {}

		doDeleteFeaturesTest(true);

	}

	private FeatureVector getNew(FeatureVector orig, boolean sparse) {
		if (sparse)
			return new SparseVector(orig);
		return new DenseVector(orig, orig.getLargestFeatureIndex());
	}

	@Test
	public void testRemoveFeaturesSparseVector() {
		int numFeatures=100;
		int jump=2;
		List<SparseFeature> feats = new ArrayList<>(numFeatures);
		
		for (int i=0;i<numFeatures;i++) {
			feats.add(new SparseFeatureImpl(i, i+1));
		}

		SparseVector vec = new SparseVector(feats);
		SparseVector vec2 = vec.clone();
		
		// Feats to remove
		List<Integer> indToRemove = new ArrayList<>();
		for (int i=0; i<=vec.getLargestFeatureIndex();i+=jump) {
			indToRemove.add(i);
		}

		vec.removeFeatureIndices(indToRemove);

		int ind=0;
		int val=2; // removing the 0 index, so will start at 2
		for (Feature f : vec) {
			Assert.assertEquals(ind, f.getIndex());
			Assert.assertEquals(val, f.getValue(), 1e-10);
			ind++;
			val+=jump;
		}
		
		// Remove some random indices
		Random rng =  new Random();
		Set<Integer> indSet = new HashSet<>();
		for (int i=0; i<10; i++) {
			indSet.add(rng.nextInt(numFeatures));
		}
		indToRemove = new ArrayList<>(indSet);
		Collections.sort(indToRemove);
		
		vec2.removeFeatureIndices(indToRemove);
		
		Assert.assertEquals(numFeatures-indSet.size(), vec2.getLargestFeatureIndex()+1);
		
		int index=0;
		int originalIndex=0;
		int value=1;
		for (Feature f : vec2) {
			while (indSet.contains(originalIndex)) {
				value++;
				originalIndex++;
			}
			Assert.assertEquals(index, f.getIndex());
			Assert.assertEquals(value, f.getValue(), 1e-10);
			index++;
			value++;
			originalIndex++;
		}
	}
	
	@Test
	public void testSparseRemove() {
		int numFeatures=1000;
		List<SparseFeature> feats = new ArrayList<>(numFeatures);
		
		for (int i=0;i<numFeatures;i++) {
			feats.add(new SparseFeatureImpl(i, i+1));
		}
		// First remove 70% features to make it a sparse array
		Random rng =  new Random();
		Set<Integer> sparseIndices = new HashSet<>();
		for (int i=0; i<numFeatures*.7; i++) {
			int index = rng.nextInt(feats.size());
			SparseFeature f = feats.remove(index);
			sparseIndices.add(f.getIndex());
		}
		
		
		SparseVector vec = new SparseVector(feats);
		
		// Only 30 features left !
		// Removing 10% more random features
		Set<Integer> removedIndSet = new HashSet<>();
		for (int i=0; i<numFeatures*.1; i++) {
			removedIndSet.add(rng.nextInt(numFeatures)); // Don't remove the last index
		}
		List<Integer> indToRemove = new ArrayList<>(removedIndSet);
		Collections.sort(indToRemove);
		
		vec.removeFeatureIndices(indToRemove);
		
		int index=0;
		int originalIndex=0;
		int value=1;
		for (Feature f : vec) {
			while (removedIndSet.contains(originalIndex) || sparseIndices.contains(originalIndex)) {
				
				if (!removedIndSet.contains(originalIndex)) {
					// Only a sparse index
					index++;
				}
				value++;
				originalIndex++;
			}
			Assert.assertEquals(index, f.getIndex());
			Assert.assertEquals(value, f.getValue(), 1e-10);
			index++;
			value++;
			originalIndex++;
		}
		
	}

	public void doDeleteFeaturesTest(boolean sparse) {

		FeatureVector original = new DenseVector(new double[] {0d,-1d,-2d,-3d,-4d,-5d,-6d,-7d,-8d,-9d,-10d});
		FeatureVector v = getNew(original, sparse);

		// Should start with a vector with 10 at the highest feature index
		Assert.assertEquals(10,v.getLargestFeatureIndex());
		if (sparse)
			Assert.assertEquals(1,v.getSmallestFeatureIndex()); // 0 values not explicitly saved
		else
			Assert.assertEquals(0,v.getSmallestFeatureIndex());

		// Test to remove only a single feature at a time
		v.removeFeatureIndex(0);
		v.removeFeatureIndex(1);

		Assert.assertEquals(8, v.getLargestFeatureIndex());
		Assert.assertEquals(-1d, v.getFeature(0), 0.00001);
		Assert.assertEquals(-3d, v.getFeature(1), 0.00001);
		Assert.assertEquals(-4d, v.getFeature(2), 0.00001);


		// Try the index in the end
		v = getNew(original, sparse);
		// Remove feature 9 2 times
		v.removeFeatureIndex(9);
		v.removeFeatureIndex(9);
		Assert.assertEquals(0d, v.getFeature(0), 0.00001);
		Assert.assertEquals(-8d, v.getFeature(8), 0.00001);
		Assert.assertEquals(8, v.getLargestFeatureIndex());


		// Test remove a set of features a the same time
		v = getNew(original, sparse);
		v.removeFeatureIndices(Arrays.asList(0,1,2));
		Assert.assertEquals(7, v.getLargestFeatureIndex());
		Assert.assertEquals(-10d, v.getFeature(7), 0.00001);
		Assert.assertEquals(-6d, v.getFeature(3), 0.00001);
		Assert.assertEquals(-3d, v.getFeature(0), 0.00001);

		// Some indices in the end and middle 
		v = getNew(original, sparse);
		v.removeFeatureIndices(Arrays.asList(5,9,10));
		Assert.assertEquals(7, v.getLargestFeatureIndex());
		Assert.assertEquals(-8d, v.getFeature(7), 0.00001);
		Assert.assertEquals(-3d, v.getFeature(3), 0.00001);
		Assert.assertEquals(0d, v.getFeature(0), 0.00001);

	}



	@Test
	public void testRemoveRange() {
		int testVecLength = 20;

		double [] vec = new double[testVecLength];
		for (int i=0;i<testVecLength; i++) {
			vec[i] = (double)i+1; // 1->21
		}
		DenseVector dense = new DenseVector(vec);
		SparseVector sparse = new SparseVector(dense);
		FeatureVector tmp = dense.clone();


		// DENSE
		Assert.assertEquals(tmp.getSmallestFeatureIndex(), 0);
		Assert.assertEquals(tmp.getLargestFeatureIndex(), testVecLength-1);
		doTest(tmp);

		// SPARSE
		tmp = sparse.clone();
		Assert.assertEquals(tmp.getSmallestFeatureIndex(), 0);
		Assert.assertEquals(tmp.getLargestFeatureIndex(), testVecLength-1);
		doTest(tmp);
		tmp = sparse.clone();


		// Create a sparse vector with implicit 0's
		for (int i=0;i<vec.length; i++) {
			if (i % 3 == 0) {
				vec[i] = 0d;
			}
		}
		sparse = new SparseVector(dense);
		// 0, 3, 6,...,18 --> implicit 0s
		tmp = sparse.clone();
		tmp.removeFeatureIndices(Range.closed(0, 2)); // remove 3 - the old "3" feature is implicit and not saved - i.e. new smallest index is 1
		Assert.assertEquals(tmp.getSmallestFeatureIndex(), 1);
		Assert.assertEquals(tmp.getLargestFeatureIndex(), testVecLength-1-3);
		Assert.assertEquals(tmp.getFeature(0), sparse.getFeature(3),.0001);

		// Same as above, but (inf, 3)
		tmp = sparse.clone();
		tmp.removeFeatureIndices(Range.upTo(3, BoundType.OPEN));
		Assert.assertEquals(tmp.getSmallestFeatureIndex(), 1);
		Assert.assertEquals(tmp.getLargestFeatureIndex(), testVecLength-1-3);
		Assert.assertEquals(tmp.getFeature(0), sparse.getFeature(3),.0001);

		// Remove in the middle, using an implicit to explicit index
		tmp = sparse.clone();
		tmp.removeFeatureIndices(Range.closed(3, 11)); // remove 9! 
		Assert.assertEquals(tmp.getSmallestFeatureIndex(), 1); // 0 still implicit
		Assert.assertEquals(tmp.getLargestFeatureIndex(), testVecLength-1-9);
		Assert.assertEquals(tmp.getFeature(2), sparse.getFeature(2),.0001);
		Assert.assertEquals(tmp.getFeature(3), sparse.getFeature(3+9),.0001);

	}



	/**
	 * 
	 * @param v Vector with all EXPLICIT features - not for sparsevector with some implicit 0's
	 */
	public static void doTest(FeatureVector v) {
		int vectorLength = v.getNumExplicitFeatures();
		FeatureVector tmp = v.clone();
		// Note we need all features to be explicit here!
		Assert.assertEquals(tmp.getLargestFeatureIndex()+1, vectorLength);

		// Removing all features should give -1 in largest and smallest feat-indices
		tmp.removeFeatureIndices(Range.all());
		Assert.assertEquals(tmp.getLargestFeatureIndex(), -1);
		Assert.assertEquals(tmp.getSmallestFeatureIndex(), -1);


		// Removing outside of feature-space should give an error!
		tmp = v.clone();
		try {
			tmp.removeFeatureIndices(Range.closed(-1, 4));
			Assert.fail();
		} catch (IndexOutOfBoundsException e) {}

		Assert.assertEquals(v, tmp); // No alteration should have been done if failing

		// Trying to remove at range above max-feat index (dense vector only!)
		if (v instanceof DenseVector) {
			try {
				tmp.removeFeatureIndices(Range.closed(3, tmp.getLargestFeatureIndex()+2));
				Assert.fail();
			} catch (IndexOutOfBoundsException e) {}
			Assert.assertEquals(v, tmp); // No alteration should have been done if failing
		}

		// Remove 0->3
		tmp = v.clone();
		tmp.removeFeatureIndices(Range.upTo(3, BoundType.CLOSED)); // E.g. including 3 (removing 0-3 --> 4 indices)
		Assert.assertEquals(tmp.toString() +"\n orig:" + v.toString(),
				vectorLength-1-4,
				tmp.getLargestFeatureIndex() 
				);
		Assert.assertEquals(tmp.toString(),tmp.getSmallestFeatureIndex(), 0);


		// Remove (3-12) ---> 4-11 (8 indices)
		tmp = v.clone();
		tmp.removeFeatureIndices(Range.open(3,12)); 
		Assert.assertEquals(tmp.toString(),tmp.getLargestFeatureIndex(), vectorLength-1-8);
		Assert.assertEquals(tmp.toString(),tmp.getSmallestFeatureIndex(), 0);

		// Remove [15-20] --> 15-20 (6 indices)
		tmp = v.clone();
		tmp.removeFeatureIndices(Range.closed(tmp.getLargestFeatureIndex()-5,tmp.getLargestFeatureIndex())); 
		Assert.assertEquals(tmp.toString(),tmp.getLargestFeatureIndex(), vectorLength-1-6);
		Assert.assertEquals(tmp.toString(),tmp.getSmallestFeatureIndex(), 0);
		////		Assert.assertEquals(clone2.getLargestFeatureIndex(), 9);


		// Remove [15,inf)
		tmp = v.clone();
		tmp.removeFeatureIndices(Range.atLeast(15)); 
		Assert.assertEquals(tmp.toString(),tmp.getLargestFeatureIndex(), 14);
		Assert.assertEquals(tmp.toString(),tmp.getSmallestFeatureIndex(), 0);

		
	}
	
	@Test
	public void testEquals() throws Exception { 
		SubSet d = TestDataLoader.getInstance().getDataset(true, true).getDataset();
		
//		System.out.println(d.toLibSVMFormat());
		SubSet dense = TestDataLoader.getInstance().getDataset(true, true).getDataset();
		dense = new MakeDenseTransformer().fitAndTransform(dense);
		// No transformations
		Assert.assertNotEquals("Dense and SparseVectors not equal",dense, d);
		
		d = new Standardizer().fitAndTransform(d);
		dense = new Standardizer().fitAndTransform(dense);
		
		// After transformations
		Assert.assertTrue(DataUtils.equals(dense, d));
		
	}

}

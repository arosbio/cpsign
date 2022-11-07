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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.data.Dataset.SubSet;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.UnitTestInitializer;

/**
 * 
 * @author ola
 *
 */
@Category(UnitTest.class)
public class TestShuffleData extends UnitTestInitializer{

	@Test
	public void testGenerateSignaturesSDF() throws Exception{

	Dataset p = new Dataset();
	List<DataRecord> ds = new ArrayList<>();
	List<SparseFeature> f = new ArrayList<>();
	f.add(new SparseFeatureImpl(1,1));
	f.add(new SparseFeatureImpl(2,2));
	f.add(new SparseFeatureImpl(3,3));
	ds.add(new DataRecord(1.0, f));
	List<SparseFeature> f2 = new ArrayList<>();
	f2.add(new SparseFeatureImpl(4,4));
	f2.add(new SparseFeatureImpl(5,5));
	f2.add(new SparseFeatureImpl(6,6));
	ds.add(new DataRecord(2.0, f2));
	List<SparseFeature> f3 = new ArrayList<>();
	f3.add(new SparseFeatureImpl(7,7));
	f3.add(new SparseFeatureImpl(8,8));
	f3.add(new SparseFeatureImpl(9,9));
	ds.add(new DataRecord(3.0, f3));
	

	p.setDataset(new SubSet(ds));
	
	System.out.println(p.toString());
	p.shuffle();
	System.out.println("SHUFFLE");
	System.out.println(p.toString());
	p.shuffle();
	System.out.println("SHUFFLE");
	System.out.println(p.toString());

	}
	
	@Test
	public void TestShuffleUsingStaticSeed() throws Exception {
		InputStream iStream = TestResources.SVMLIGHTFiles.REGRESSION_HOUSING.openStream();
		Dataset problem = Dataset.fromLIBSVMFormat(iStream);
		Dataset clonedProblem = problem.clone();
		Dataset nonShuffled = problem.clone();
		long seed = 42L;
		
		problem.shuffle(seed);
		clonedProblem.shuffle(seed);
		
		Assert.assertEquals(problem, clonedProblem);
		
		Assert.assertNotEquals(nonShuffled, problem);
		Set<DataRecord> shuffledRecs = new HashSet<>(problem.getDataset());
		Set<DataRecord> nonShuffedRecs = new HashSet<>(nonShuffled.getDataset());
		assertEquals(shuffledRecs, nonShuffedRecs);
	}
	
	
	private static <T> void assertEquals(Set<T> s1, Set<T> s2) {
		Assert.assertEquals(s1.size(), s2.size());
		
		for (T e: s1) {
			Assert.assertTrue(s1.contains(e));
		}
	}


}

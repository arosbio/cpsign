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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.io.LIBSVMFormat;
import com.arosbio.data.transform.duplicates.KeepMaxLabel;
import com.arosbio.data.transform.duplicates.KeepMeanLabel;
import com.arosbio.data.transform.duplicates.RemoveContradictoryRecords;
import com.arosbio.data.transform.duplicates.UseVoting;
import com.arosbio.data.transform.filter.LabelRangeFilter;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.GzipEncryption;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.TestEnv;
import com.google.common.collect.Range;


@Category(UnitTest.class)
public class TestSubSet extends TestEnv {

	@Test
	public void testNumAttributesWhenEmpty(){
		SubSet s = new SubSet();
		Assert.assertEquals(0, s.getNumFeatures());
	}
	
	@Test
	public void testDataRecord() {
		DataRecord original = new DataRecord(.1, Arrays.asList(new SparseFeatureImpl(1, 2d)));
		DataRecord clone = original.clone();
		Assert.assertTrue(clone.equals(original));
		Assert.assertTrue(original.equals(original));
		Assert.assertTrue(original.equals(clone));
		DataRecord original2 = new LIBSVMFormat().readLine("1.0 16:1.08 19:1.08 21:2.17 52:1.478 55:2.0 56:30.0 57:106.0");
		DataRecord clone2 = original2.clone();
		List<DataRecord> recs = Arrays.asList(original, original2);
		
		Assert.assertTrue(recs.contains(original));
		Assert.assertTrue(recs.contains(original2));
		
		Set<DataRecord> recs_set = new HashSet<>();
		recs_set.add(clone);
		recs_set.add(clone2);
		Assert.assertTrue(recs_set.contains(original));
		Assert.assertTrue(recs_set.contains(clone));
		Assert.assertTrue(recs_set.containsAll(recs));
	}
	
	@Test
	public void testEqualMissingVal() {
		Assert.assertEquals(new SparseFeatureImpl(1, Double.NaN), new MissingValueFeature(1));
	}
	
	@Test
	public void testLibSVMFormatWithNaNValues() throws Exception {
		DataRecord initial = new DataRecord(Double.NaN, Arrays.asList(new SparseFeatureImpl(1, 2), new SparseFeatureImpl(2,Double.NaN), new SparseFeatureImpl(3, 5)));
		String txt = LIBSVMFormat.serialize(initial);
		System.out.println(txt);
		DataRecord r = new LIBSVMFormat().readLine(txt);
		Assert.assertEquals(initial, r);
		System.out.println("parsed: " + r);
		
		initial = new DataRecord(2.325, Arrays.asList(new SparseFeatureImpl(1, 2), new MissingValueFeature(2), new SparseFeatureImpl(3, 5)));
		System.out.println("init: " + initial);
		txt = LIBSVMFormat.serialize(initial);
		System.out.println("second serial: " + txt);
		r = new LIBSVMFormat().readLine(txt);
		Assert.assertEquals(initial, r);
//		printLogs();
	}

	@Test
	public void TestSplitRandom() throws Exception {
		SubSet original = TestDataLoader.loadSubset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS);
		double splitFactor = 0.3;

		Assert.assertEquals(4601, original.size());
		//		Assert.assertEquals(4601, original.getY().size());

		SubSet[] splitted = original.splitRandom(splitFactor);
		Assert.assertEquals(2, splitted.length);

		Assert.assertEquals(original.size(), splitted[0].size() + splitted[1].size());

		Assert.assertEquals((int) Math.ceil(original.size()*splitFactor), splitted[0].size());

		// Make sure all records are there! (first partition)
		boolean foundMatch=false;
		for(int i=0; i<splitted[0].size(); i++){
			foundMatch=false;
			for(int j=0; j<original.size(); j++){
				if(original.get(j).equals(splitted[0].get(i)) ){
					original.remove(j);
					//					original.getY().remove(j);
					foundMatch =true;
					break;
				}
			}
			Assert.assertTrue(foundMatch);
		}

		// Second partition
		for(int i=0; i<splitted[1].size(); i++){
			foundMatch=false;
			for(int j=0; j<original.size(); j++){
				if(original.get(j).equals(splitted[1].get(i))){
					original.remove(j);
					//					original.getY().remove(j);
					foundMatch =true;
					break;
				}
			}
			Assert.assertTrue(foundMatch);
		}

	}


	@Test
	public void TestSplitStatic() throws Exception {
		SubSet original = TestDataLoader.loadSubset(TestResources.SVMLIGHTFiles.REGRESSION_HOUSING);
		double splitFactor = 0.1;
		int splitIndex = 51;

		Assert.assertEquals(506, original.size());
		//		Assert.assertEquals(506, original.getY().size());

		// SPLIT WITH FRACTION

		SubSet[] splitted = original.splitStatic(splitFactor);
		Assert.assertEquals(2, splitted.length);

		Assert.assertEquals(original.size(), splitted[0].size() + splitted[1].size());

		Assert.assertEquals((int) Math.ceil(original.size()*splitFactor), splitted[0].size());

		// Make sure all records are there! (first partition)
		for(int i=0; i<splitted[0].size(); i++){
			Assert.assertTrue(original .get(i).equals(splitted[0] .get(i)));
			//			Assert.assertEquals(original.getY().get(i), splitted[0] .get(i));
		}
		int offset = splitted[0] .size();
		// Second partition
		for(int i=0; i<splitted[1] .size(); i++){
			Assert.assertTrue(original .get(offset+i).equals(splitted[1] .get(i)));
			//			Assert.assertEquals(original.getY().get(offset+i), splitted[1].getY().get(i));
		}


		// SPLIT WITH INDEX

		SubSet[] splittedIndex = original.splitStatic(splitIndex);

		Assert.assertEquals(2, splitted.length);

		Assert.assertEquals(splitted[0], splittedIndex[0]);
		Assert.assertEquals(splitted[1], splittedIndex[1]);
	}

	public static SecretKey getSecretKey(){
		KeyGenerator keygen;
		try {
			keygen = KeyGenerator.getInstance("AES");
			keygen.init(128); // Set key-size!
			return keygen.generateKey();	
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Test
	public void testEncryptAndDecryptProblem() throws IOException, IllegalAccessException, InvalidKeyException{

		// Load in a real dataset
		SubSet orig = TestDataLoader.loadSubset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_100);
		Assert.assertTrue(orig .size() > 50);
		Assert.assertTrue(orig .size() == orig .size());

		EncryptionSpecification enc = new GzipEncryption();
		enc.init(enc.generateRandomKey(16));

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		orig.writeRecords(baos, enc);
		ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
		SubSet fromEncrypted = new SubSet();
		fromEncrypted.readRecords(bais, enc);
		Assert.assertEquals(orig .size(), fromEncrypted .size());
		for(int i=0; i<orig .size(); i++){
			Assert.assertTrue(orig .get(i).equals(fromEncrypted .get(i)));
		}
	}

	@Test
	public void testJoinTwoProblems() throws Exception {
		SubSet original = TestDataLoader.loadSubset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_100);
		try{
			original.join(original);
			Assert.fail();
		} catch(Exception e){
		}
		SubSet cpy = original.clone();

		original.join(cpy);

		cpy = new SubSet(); 
		try{
			original.join(cpy);
		} catch(Exception e){
			Assert.fail();
		}

	}
	
	@Test
	public void testResolveDuplicates() throws Exception {
		SubSet prob = new SubSet();
		List<SparseFeature> feature = Arrays.<SparseFeature>asList(new SparseFeatureImpl(0, 1.5), new SparseFeatureImpl(1, 2.5), new SparseFeatureImpl(3, 1.5));
		DataRecord rec = new DataRecord(0.0, feature);
		List<DataRecord> dataset = new ArrayList<>();
		dataset.add(rec.clone()); // identical
		dataset.add(rec.clone()); // identical
		DataRecord recDiffLabel = rec.clone();
		recDiffLabel.setLabel(1.0);
		dataset.add(recDiffLabel); // identical (different label)

		dataset.add(new DataRecord(0.0, Arrays.<SparseFeature>asList(new SparseFeatureImpl(0, 1.5), new SparseFeatureImpl(2, 2.57), new SparseFeatureImpl(5, 21.5)))); // different

		prob.setRecords(dataset);

		//		System.out.println(prob);

//		SubSet keep0 = prob.clone();
//		keep0.resolveDuplicates(DuplicatesStrategyFactory.keepOfClass(0));
//		System.out.println("Keep0:\n" +keep0);
//		Assert.assertEquals(2,keep0.size());
//
//
//		SubSet keep1 = prob.clone();
//		keep1.resolveDuplicates(DuplicatesStrategyFactory.keepOfClass(1));
//		Assert.assertEquals(2,keep1.size());
//		System.out.println("Keep1:\n" +keep1);

		SubSet removeTies = prob.clone();
		removeTies = new RemoveContradictoryRecords().fitAndTransform(removeTies);
//		removeTies.resolveDuplicates(DuplicatesStrategyFactory.removeContradictory());
		Assert.assertEquals(1,removeTies.size());
		System.out.println("Remove ties:\n" +removeTies);

		SubSet regRemoveTies1 = prob.clone();
		regRemoveTies1 = new RemoveContradictoryRecords(0.001).fitAndTransform(regRemoveTies1);
//		regRemoveTies1.resolveDuplicates(DuplicatesStrategyFactory.removeContradictory(0.001));
		Assert.assertEquals(1,regRemoveTies1.size());
		System.out.println("Remove ties (regression):\n" + regRemoveTies1);

		SubSet regRemoveTies = prob.clone();
		regRemoveTies = new RemoveContradictoryRecords(1).fitAndTransform(regRemoveTies);
//		regRemoveTies.resolveDuplicates(DuplicatesStrategyFactory.removeContradictory(1));
		Assert.assertEquals(2,regRemoveTies.size());
		System.out.println("Remove ties (regression):\n" + regRemoveTies);

		SubSet regAVG = prob.clone();
		regAVG = new KeepMeanLabel().fitAndTransform(regAVG);
//		regAVG.resolveDuplicates(DuplicatesStrategyFactory.mean());
		Assert.assertEquals(2,regAVG.size());
		System.out.println("avg (regression):\n" + regAVG);

	}
	
	@Test
	public void testResolveDuplicatesLarger() {
		SubSet prob = new SubSet();
		List<SparseFeature> feature = Arrays.<SparseFeature>asList(new SparseFeatureImpl(0, 1.5), new SparseFeatureImpl(1, 2.5), new SparseFeatureImpl(3, 1.5));
		List<SparseFeature> feature2 = Arrays.<SparseFeature>asList(new SparseFeatureImpl(0, 1.6), new SparseFeatureImpl(1, 2.5), new SparseFeatureImpl(3, 1.5));
		List<SparseFeature> feature3 = Arrays.<SparseFeature>asList(new SparseFeatureImpl(0, 1.7), new SparseFeatureImpl(1, 2.5), new SparseFeatureImpl(3, 1.5));
		List<SparseFeature> feature4 = Arrays.<SparseFeature>asList(new SparseFeatureImpl(0, 1.501), new SparseFeatureImpl(1, 2.5), new SparseFeatureImpl(3, 1.5));
		
		prob.add(new DataRecord(0.0, feature));
		prob.add(new DataRecord(0.0, feature2));
		prob.add(new DataRecord(0.0, feature3));
		prob.add(new DataRecord(0.0, feature4));
		
		prob = new KeepMaxLabel().fitAndTransform(prob);
//		prob.resolveDuplicates(DuplicatesStrategyFactory.keepMax());
		Assert.assertEquals(4, prob.size());
		
		prob.add(new DataRecord(0.0, feature));
		prob.add(new DataRecord(0.0, feature2));
		prob.add(new DataRecord(1.0, feature3));
		prob.add(new DataRecord(1.0, feature4));
		
		prob.add(new DataRecord(1.0, feature));
		prob.add(new DataRecord(1.0, feature2));
		prob.add(new DataRecord(1.0, feature3));
		prob.add(new DataRecord(1.0, feature4));
		
		Assert.assertEquals(4*3, prob.size());
		
		prob = new UseVoting().fitAndTransform(prob);
		Assert.assertEquals(4, prob.size());
	}

	
	@Test
	public void testFilter() throws Exception {
		SubSet prob = new SubSet();
		List<SparseFeature> feature = Arrays.<SparseFeature>asList(new SparseFeatureImpl(0, 1.5), new SparseFeatureImpl(1, 2.5), new SparseFeatureImpl(3, 1.5));
		DataRecord rec = new DataRecord(0.0, feature);
		List<DataRecord> dataset = new ArrayList<>();
		dataset.add(rec.clone()); // 0.0
		dataset.add(rec.clone()); 
		dataset.get(1).setLabel(0.05); // 0.05
		DataRecord recDiffLabel = rec.clone();
		recDiffLabel.setLabel(0.5); // 0.5
		dataset.add(recDiffLabel);

		dataset.add(new DataRecord(0.0, Arrays.<SparseFeature>asList(new SparseFeatureImpl(0, 1.5), new SparseFeatureImpl(2, 2.57), new SparseFeatureImpl(5, 21.5)))); // different

		prob.setRecords(dataset);

		SubSet minMax = prob.clone();
		new LabelRangeFilter(Range.closed(0.01, .49)).fitAndTransform(minMax);
//		minMax.filter(FilterStrategyFactory.allowInRange(0.01, 0.49));
		Assert.assertEquals(1, minMax.size());
		
		SubSet max = prob.clone();
		max = new LabelRangeFilter(Range.atMost(.49)).fitAndTransform(max);
//		max.filter(FilterStrategyFactory.filterMaximumValue(0.49));
		Assert.assertEquals(3, max.size());
		
		SubSet min = prob.clone();
		min = new LabelRangeFilter(Range.atLeast(.01)).fitAndTransform(min);
//		min.filter(FilterStrategyFactory.filterMinimumValue(0.01));
		Assert.assertEquals(2, min.size());
		
	}


//	@Test
//	public void testFilterProblem() throws Exception {
//		SubSet prob = new SubSet();
//		List<SparseFeature> feature = Arrays.<SparseFeature>asList(new SparseFeatureImpl(0, 1.5), new SparseFeatureImpl(1, 2.5), new SparseFeatureImpl(3, 1.5));
//		DataRecord rec = new DataRecord(0.0, feature);
//		List<DataRecord> dataset = new ArrayList<>();
//		dataset.add(rec.clone()); // identical
//		dataset.add(rec.clone()); // identical
//		DataRecord recDiffLabel = rec.clone();
//		recDiffLabel.setLabel(1.0);
//		dataset.add(recDiffLabel); // identical (different label)
//
//		dataset.add(new DataRecord(0.0, Arrays.<SparseFeature>asList(new SparseFeatureImpl(0, 1.5), new SparseFeatureImpl(2, 2.57), new SparseFeatureImpl(5, 21.5)))); // different
//
//		prob.setRecords(dataset);
//
//		//		System.out.println(prob);
//
//		SubSet keep0 = prob.clone();
//		keep0.filterOLD(FilterStrategyOLD.CLASSIFICATION_KEEP_0, 0);
//		System.out.println("Keep0:\n" +keep0);
//		Assert.assertEquals(2,keep0.size());
//
//
//		SubSet keep1 = prob.clone();
//		keep1.filterOLD(FilterStrategyOLD.CLASSIFICATION_KEEP_1, 0);
//		Assert.assertEquals(2,keep1.size());
//		System.out.println("Keep1:\n" +keep1);
//
//		SubSet removeTies = prob.clone();
//		removeTies.filterOLD(FilterStrategyOLD.CLASSIFICATION_REMOVE_TIES, 0);
//		Assert.assertEquals(1,removeTies.size());
//		System.out.println("Remove ties:\n" +removeTies);
//
//		SubSet regRemoveTies1 = prob.clone();
//		regRemoveTies1.filterOLD(FilterStrategyOLD.REGRESSION_REMOVE_TIES, 0.001);
//		Assert.assertEquals(1,regRemoveTies1.size());
//		System.out.println("Remove ties (regression):\n" + regRemoveTies1);
//
//		SubSet regRemoveTies = prob.clone();
//		regRemoveTies.filterOLD(FilterStrategyOLD.REGRESSION_REMOVE_TIES, 1);
//		Assert.assertEquals(2,regRemoveTies.size());
//		System.out.println("Remove ties (regression):\n" + regRemoveTies);
//
//		SubSet regAVG = prob.clone();
//		regAVG.filterOLD(FilterStrategyOLD.REGRESSION_USE_AVG, 0.001);
//		Assert.assertEquals(2,regAVG.size());
//		System.out.println("avg (regression):\n" + regAVG);
//
//	}

	@Test
	public void testStratisfiedForRegressionShouldFail() throws Exception {
		SubSet regressionDS = TestDataLoader.getInstance().getDataset(false, false).getDataset(); //SubSet.fromLIBSVMFormat(new FileInputStream(new File(NumericalSVMDatasets.REGRESSION_HOUSING_SCALE_FILE_PATH)));
//		System.out.println(regressionDS.getLabelFrequencies());
		try{
			regressionDS.splitStratified(0.3);
			Assert.fail();
		} catch(IllegalAccessException e){
		}
	}

	@Test
	public void testStratisfiedSplitClassification() throws Exception{
		SubSet classificationDS = TestDataLoader.loadSubset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS);
		doTestStratisfiedSplit(classificationDS, 0.3);
		doTestStratisfiedSplit(classificationDS, 0.8);
		doTestStratisfiedSplit(classificationDS, 0.96);
	}
	
	public void doTestStratisfiedSplit(SubSet ds, double splitFactor) throws Exception{
//		SubSet classificationDS = SubSet.fromSparseFile(get(CLASSIFICATION_2CLASS_PATH));
//		LoggerUtils.setDebugMode();
		int numRecsOrig = ds.size();
		Map<Double, Integer> freqsOrig = ds.getLabelFrequencies();
//		System.out.println(freqsOrig);
		
		SubSet[] dsSplitts = ds.splitStratified(splitFactor);
		SubSet d1 = dsSplitts[0];
		SubSet d2 = dsSplitts[1];
		Map<Double, Integer> freqs1 = d1.getLabelFrequencies();
		Map<Double, Integer> freqs2 = d2.getLabelFrequencies();
//		System.out.println(freqs1);
//		System.out.println(freqs2);
		
		Assert.assertTrue(Math.abs(d1.size()-numRecsOrig*splitFactor) <= 2);
		Assert.assertEquals(ds.size(), d1.size() + d2.size());
		Assert.assertEquals(freqsOrig.keySet().size(), freqs1.keySet().size());
		Assert.assertEquals(freqsOrig.keySet().size(), freqs2.keySet().size());
		
		Double c0 = Double.valueOf(0);
		Assert.assertEquals((int)freqsOrig.get(c0), freqs1.get(c0)+freqs2.get(c0));
		Double c1 = Double.valueOf(1);
		Assert.assertEquals((int)freqsOrig.get(c1), freqs1.get(c1)+freqs2.get(c1));
		Assert.assertTrue(Math.abs(freqsOrig.get(c0)*splitFactor-freqs1.get(c0))<= 1);
		Assert.assertTrue(Math.abs(freqsOrig.get(c1)*splitFactor-freqs1.get(c1))<= 1);

	}
	
	@Test
	public void testStratisfiedSplitClassificationSetSeed() throws Exception{
		SubSet classificationDS = TestDataLoader.loadSubset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS);
		long seed=124125L;
		double frac=0.4;
		SubSet[] dsSplit1 = classificationDS.splitStratified(seed, frac);
		SubSet[] dsSplit2 = classificationDS.splitStratified(seed, frac);
		Assert.assertEquals(dsSplit1[0], dsSplit2[0]);
		Assert.assertEquals(dsSplit1[1], dsSplit2[1]);
		
		SubSet[] dsSplit3 = classificationDS.splitStratified(seed+1, frac); 
		Assert.assertFalse(dsSplit1[0].equals(dsSplit3[0]));
		Assert.assertFalse(dsSplit1[1].equals(dsSplit3[1]));
		
	}
	
}

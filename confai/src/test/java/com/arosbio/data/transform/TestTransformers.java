/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.transform;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.stat.descriptive.moment.Variance;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.MathUtils;
import com.arosbio.commons.Stopwatch;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.DenseFloatVector;
import com.arosbio.data.DenseVector;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.FeatureVector.Feature;
import com.arosbio.data.MissingValueFeature;
import com.arosbio.data.SparseFeature;
import com.arosbio.data.SparseFeatureImpl;
import com.arosbio.data.SparseVector;
import com.arosbio.data.io.LIBSVMFormat;
import com.arosbio.data.transform.Transformer.TransformInfo;
import com.arosbio.data.transform.duplicates.DuplicatesResolverTransformer;
import com.arosbio.data.transform.duplicates.InterDatasetDuplicatesResolver;
import com.arosbio.data.transform.duplicates.KeepFirstRecord;
import com.arosbio.data.transform.duplicates.KeepLastRecord;
import com.arosbio.data.transform.duplicates.KeepMeanLabel;
import com.arosbio.data.transform.duplicates.RemoveContradictoryRecords;
import com.arosbio.data.transform.feature_selection.FeatureSelectUtils;
import com.arosbio.data.transform.feature_selection.FeatureSelectUtils.IndexedValue;
import com.arosbio.data.transform.feature_selection.FeatureSelector;
import com.arosbio.data.transform.feature_selection.L1_SVC_Selector;
import com.arosbio.data.transform.feature_selection.L2_SVC_Selector;
import com.arosbio.data.transform.feature_selection.L2_SVR_Selector;
import com.arosbio.data.transform.feature_selection.NumNonZeroSelector;
import com.arosbio.data.transform.feature_selection.SelectionCriterion;
import com.arosbio.data.transform.feature_selection.SelectionCriterion.Criterion;
import com.arosbio.data.transform.feature_selection.VarianceBasedSelector;
import com.arosbio.data.transform.filter.SubSampleMajorityClass;
import com.arosbio.data.transform.format.MakeDenseTransformer;
import com.arosbio.data.transform.format.MakeSparseTransformer;
import com.arosbio.data.transform.impute.SingleFeatureImputer;
import com.arosbio.data.transform.impute.SingleFeatureImputer.ImputationStrategy;
import com.arosbio.data.transform.scale.MinMaxScaler;
import com.arosbio.data.transform.scale.RobustScaler;
import com.arosbio.data.transform.scale.Standardizer;
import com.arosbio.data.transform.scale.VectorNormalizer;
import com.arosbio.data.transform.scale.VectorNormalizer.Norm;
import com.arosbio.data.transform.scale.ZeroMaxScaler;
import com.arosbio.io.JarDataSink;
import com.arosbio.io.JarDataSource;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.icp.ICPClassifier;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.testing.RandomSplit;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.TestEnv;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;

@RunWith(Enclosed.class)
@Category(UnitTest.class)
public class TestTransformers extends TestEnv {

	@Test
	public void testTransformerInfo() {
		TransformInfo info = new Transformer.TransformInfo(10, 1);
		info.toString();
		info = new Transformer.TransformInfo(10, 1, 15);
		System.out.println(info);
	}

	@Test
	public void testToStrings() {
		Iterator<Transformer> iter = FuzzyServiceLoader.iterator(Transformer.class);

		while (iter.hasNext()) {
			Transformer t = iter.next();
			System.err.println(t);
			Assert.assertFalse(t.toString().contains("arosbio"));

		}
		// printLogs();
	}

	@Test
	public void testIndexedValuesSort() {
		// 1, 4, 2, 0
		List<Integer> indicesSorted = Arrays.asList(0, 2, 4, 1);
		List<IndexedValue> vals = new ArrayList<>();
		vals.add(new IndexedValue(0, 5d));
		vals.add(new IndexedValue(1, -5d));
		vals.add(new IndexedValue(2, 3d));
		vals.add(new IndexedValue(4, 0.1d));

		Collections.sort(vals);
		for (int i = 0; i < indicesSorted.size(); i++) {
			Assert.assertEquals((int) indicesSorted.get(i), vals.get(i).index);
		}
		// System.err.println(vals);

		vals = new ArrayList<>();
		vals.add(new IndexedValue(0, 5d));
		vals.add(new IndexedValue(1, -5d));
		vals.add(new IndexedValue(2, 3d));
		vals.add(new IndexedValue(4, 0.1d));

		for (int i = 0; i < vals.size(); i++) {
			List<Integer> max3 = FeatureSelectUtils.getSmallestKeepingN(vals, i);
			// System.err.println(max3);
			// wish to keep 'n', --> original-size - removed-size
			Assert.assertEquals(i, vals.size() - max3.size());
			TestUtils.assertSorted(max3);
		}

		// Explicit version
		List<Integer> max3 = FeatureSelectUtils.getSmallestKeepingN(vals, 3);
		Assert.assertEquals(Arrays.asList(1), max3); // Only removing one value
		max3 = FeatureSelectUtils.getSmallestKeepingN(vals, 2);
		Assert.assertEquals(Arrays.asList(1, 4), max3);

		// printLogs();

		// Sort with multiple ones having the same value
		List<IndexedValue> original = new ArrayList<>();
		original.add(new IndexedValue(0, 1d));
		original.add(new IndexedValue(1, 1d));
		original.add(new IndexedValue(2, 1d));
		original.add(new IndexedValue(3, 1d));

		original.add(new IndexedValue(0, 2d));
		original.add(new IndexedValue(1, 2d));
		original.add(new IndexedValue(2, 2d));
		original.add(new IndexedValue(3, 2d));

		Collections.sort(original);
		// SYS_ERR.println(original);

	}

	@Test
	public void testDefaultSerialization() throws IOException, ClassNotFoundException {

		MinMaxScaler scaler = new MinMaxScaler(-1, 4);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (
				ObjectOutputStream os = new ObjectOutputStream(baos)) {
			os.writeObject(scaler);
			os.flush();
		}

		// System.err.println("the byte array: " + new String(baos.toByteArray()));

		// Read in again
		try (ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
				ObjectInputStream is = new ObjectInputStream(in);) {
			Transformer loaded = (Transformer) is.readObject();
			// System.err.println("Loaded: " + loaded);
			Assert.assertTrue(loaded instanceof MinMaxScaler);
			MinMaxScaler lMinMax = (MinMaxScaler) loaded;
			Assert.assertEquals(scaler.getMin(), lMinMax.getMin(), 0.00001);
			Assert.assertEquals(scaler.getMax(), lMinMax.getMax(), 0.00001);
		}
	}

	@Test
	public void testSaveTransformers() throws IllegalArgumentException, Exception {
		Iterator<Transformer> trans = FuzzyServiceLoader.iterator(Transformer.class);

		Dataset p = TestDataLoader.getInstance().getDataset(true, true);

		// System.err.println(p.getDataset().toLibSVMFormat());
		// int numColTrans=0;
		int numTrans = 0;
		while (trans.hasNext()) {
			Transformer t = trans.next();

			doTestSaveLoadTransformer(t, p.clone());
			// if (t instanceof ColumnTransformer) {
			// if (numColTrans==0)
			// ((ColumnTransformer) t).setColumns(new ColumnSpec(Range.closed(1, 5)));
			// else if (numColTrans == 1)
			// ((ColumnTransformer) t).setColumns(new ColumnSpec(8,7,3));
			// else if (numColTrans == 2)
			// ((ColumnTransformer) t).setColumns(new ColumnSpec(Arrays.asList(8,7,3)));
			// else
			// ((ColumnTransformer) t).setColumns(new ColumnSpec(Range.all()));
			// numColTrans++;
			// }
			//// System.err.println("Applying " + t.getNames());
			// p.apply(t);
			numTrans++;

			// if (t instanceof MinMaxScaler) {
			// System.err.println(p.getDataset().toLibSVMFormat());
			// }
		}
		Assert.assertTrue(numTrans > 5);

		// System.out.println(p.getDataset().toLibSVMFormat());
		// File theFile = TestEnvironmentSetup.createTempFile("data", "jar");
		//
		// try (JarDataSink sink = new JarDataSink(new JarOutputStream(new
		// FileOutputStream(theFile)))){
		// p.saveToDataSink(sink, null, null);
		// }
		//// System.err.println("Finished saving to jar");
		//
		//// LoggerUtils.setDebugMode(System.out);
		//
		// Dataset loaded = new Dataset();
		// try (JarDataSource src = new JarDataSource(new JarFile(theFile))){
		// loaded.loadFromDataSource(src, null);
		// }
		//// System.err.println("Finished loading " + loaded.getTransformers().size() +
		// " transformers");
		//
		// Assert.assertTrue(loaded.getTransformers().size() >= 3);
		// for (Transformer t : loaded.getTransformers()) {
		// System.err.println("Loaded transformer: " + t);
		// }

	}

	private static void doTestSaveLoadTransformer(Transformer t, Dataset p) throws Exception {
		p.apply(t);

		File theFile = TestUtils.createTempFile("data", "jar");

		try (JarDataSink sink = new JarDataSink(new JarOutputStream(new FileOutputStream(theFile)))) {
			p.saveToDataSink(sink, null, null);
		}

		Dataset loaded = new Dataset();
		try (JarDataSource src = new JarDataSource(new JarFile(theFile))) {
			loaded.loadFromDataSource(src, null);
		}

		if (t.appliesToNewObjects()) {
			// Then it must be saved and loaded correctly!
			Assert.assertEquals(1, loaded.getTransformers().size());
			Assert.assertTrue(loaded.getTransformers().get(0).getClass().equals(t.getClass()));
		} else {
			Assert.assertEquals(0, loaded.getTransformers().size());
		}
	}

	@Category(UnitTest.class)
	public static class TestDuplicatesResolverTransformer {

		@Test
		public void testKeep_last_first_mean_Record() throws Exception {
			SubSet dLast = TestDataLoader.getInstance().getDataset(true, false).getDataset().clone();
			SubSet dFirst = dLast.clone();
			SubSet dClone = dLast.clone();

			new KeepLastRecord().transform(dLast);
			Assert.assertTrue(dLast.size() < dClone.size());

			new KeepFirstRecord().transform(dFirst);

			Assert.assertEquals(dFirst.size(), dLast.size());

			new KeepMeanLabel().transform(dClone);

			// Should result in the same size
			Assert.assertEquals(dClone.size(), dLast.size());
		}

	}


	@Category(UnitTest.class)
	public static class TestFeatureSelectors {

		@Test
		public void testVarianceSelector() throws Exception {
			SubSet sparse = TestDataLoader.getInstance().getDataset(true, true).getDataset();
			SubSet dense = new MakeDenseTransformer().transformInPlace(false).fitAndTransform(sparse);

			// First try using all columns
			VarianceBasedSelector vbs = new VarianceBasedSelector();
			vbs.setSelectionCriterion(new SelectionCriterion(Criterion.KEEP_LARGER_THAN_MEDIAN));
			VarianceBasedSelector vbsDense = vbs.clone();
			sparse = vbs.fitAndTransform(sparse);
			dense = vbsDense.fitAndTransform(dense);

			Assert.assertEquals(vbs.getFeatureIndicesToRemove(), vbsDense.getFeatureIndicesToRemove());

			// LoggerUtils.setDebugMode(SYS_ERR);
			// Assert.assertTrue(DataUtils.equals(sparse, dense, 10e-6));

			// Subset of columns

			sparse = TestDataLoader.getInstance().getDataset(true, false).getDataset();
			dense = new MakeDenseTransformer().transformInPlace(false).fitAndTransform(sparse);
			int numFeats = sparse.getNumFeatures();
			ColumnSpec spec = new ColumnSpec(Range.closed(5, numFeats-1));

			int toKeep = numFeats/2;

			VarianceBasedSelector vbsSub = new VarianceBasedSelector(toKeep);
			vbsSub.setColumns(spec);
			VarianceBasedSelector vbsSubClone = vbsSub.clone();

			sparse = vbsSub.fitAndTransform(sparse);
			dense = vbsSubClone.fitAndTransform(dense);

			Assert.assertEquals(vbs.getFeatureIndicesToRemove(), vbsDense.getFeatureIndicesToRemove());

			// // The variance calculation differs slightly due to order of included values, so
			// // result might differ slightly
			// Set<Integer> differing = new HashSet<>(vbsSub.getIndicesToRemove());
			// differing.removeAll(vbsSubClone.getIndicesToRemove());
			// SYS_ERR.println("different: " + differing);

			// Assert.assertTrue("Should differ less than 1%: " + differing.size(), differing.size() < 300 * .01);

		}

		@Test
		public void testVarianceSelectorSmall() {
			FeatureSelector selector = new VarianceBasedSelector();
			List<DataRecord> records = new ArrayList<>();
			records.add(new DataRecord(1d, new DenseVector(new double[] { 0d, 1d, 3d })));

			selector.fit(records);
			Assert.assertEquals(Arrays.asList(0, 1, 2), selector.getFeatureIndicesToRemove());
			// Add some more records
			records.add(new DataRecord(1d, new DenseVector(new double[] { -1d, 2d, 3d })));
			records.add(new DataRecord(1d, new DenseVector(new double[] { 5d, 0d, 3d })));
			records.add(new DataRecord(1d, new DenseVector(new double[] { 0d, -1d, 3d })));
			selector.fit(records);
			// System.err.println();

			// Larger size

		}

		@Test
		public void testL1_SVC_select() throws Exception {
			FeatureSelector svc = (FeatureSelector) FuzzyServiceLoader.load(Transformer.class, "L1-SVCselector");

			Assert.assertTrue(svc instanceof L1_SVC_Selector);
			L1_SVC_Selector fs = (L1_SVC_Selector) svc;
			fs.setC(1d);
			fs.setSelectionCriterion(SelectionCriterion.keepN(10)); // KeepMaximumNumFeatures(10);

			SubSet d = TestDataLoader.getInstance().getDataset(true, true).getDataset();
			int numInitialFeats = d.getNumFeatures();
			// LoggerUtils.setDebugMode(SYS_OUT);
			new Standardizer().fitAndTransform(d);
			svc.fitAndTransform(d);

			int numFeatsAfter = d.getNumFeatures();

			// System.err.println("init: " + numInitialFeats + "; after: " + numFeatsAfter);
			Assert.assertEquals(svc.getFeatureIndicesToRemove().size(), numInitialFeats - numFeatsAfter);

		}

		@Test
		public void testL2_SVC_select() throws Exception {
			FeatureSelector svc = (FeatureSelector) FuzzyServiceLoader.load(Transformer.class, "L2-SVC_selector");

			Assert.assertTrue(svc instanceof L2_SVC_Selector);

			SubSet d = TestDataLoader.getInstance().getDataset(true, true).getDataset();
			int numInitialFeats = d.getNumFeatures();

			// LoggerUtils.setDebugMode(SYS_OUT);

			new Standardizer().fitAndTransform(d);
			svc.fitAndTransform(d);

			int numFeatsAfter = d.getNumFeatures();

			// System.err.println("init: " + numInitialFeats + "; after: " + numFeatsAfter);
			Assert.assertEquals(svc.getFeatureIndicesToRemove().size(), numInitialFeats - numFeatsAfter);

		}

		@Test
		public void testL2_SVR_select() throws Exception {
			FeatureSelector svc = (FeatureSelector) FuzzyServiceLoader.load(Transformer.class, "L2_SVR_Select");

			Assert.assertTrue(svc instanceof L2_SVR_Selector);

			SubSet d = TestDataLoader.getInstance().getDataset(false, true).getDataset();

			// LoggerUtils.setDebugMode(SYS_OUT);

			int numInitialFeats = d.getNumFeatures();
			new Standardizer().fitAndTransform(d);
			svc.fitAndTransform(d);

			int numFeatsAfter = d.getNumFeatures();

			// System.err.println("init: " + numInitialFeats + "; after: " + numFeatsAfter);
			Assert.assertEquals(svc.getFeatureIndicesToRemove().size(), numInitialFeats - numFeatsAfter);

		}

		@Test
		public void testNumNonZeroSelector() throws Exception {
			int threshold = 3;
			FeatureSelector selector = new NumNonZeroSelector(threshold);
			FeatureSelector newVersion = new NumNonZeroSelector(threshold);
			List<DataRecord> records = new ArrayList<>();
			records.add(new DataRecord(1d, new DenseVector(new double[] { 0d, 1d })));

			// System.err.println(records);
			newVersion.fit(records);
			selector.fit(records);
			// Assert.assertTrue(selector.getFeatureIndicesToRemove().size() == 2);
			Assert.assertEquals(Arrays.asList(0, 1), selector.getFeatureIndicesToRemove());
			Assert.assertEquals(newVersion.getFeatureIndicesToRemove(), newVersion.getFeatureIndicesToRemove());

			// Add some more records
			records.add(new DataRecord(1d, new DenseVector(new double[] { -1d, 2d })));
			records.add(new DataRecord(1d, new DenseVector(new double[] { 5d, 0d })));
			records.add(new DataRecord(1d, new DenseVector(new double[] { 0d, -1d })));

			// System.err.println(DataUtils.extractColumn(records, 0));
			// System.err.println(DataUtils.extractColumn(records, 1));

			selector.fit(records);
			newVersion.fit(records);
			Assert.assertEquals(Arrays.asList(0), selector.getFeatureIndicesToRemove());
			Assert.assertEquals(newVersion.getFeatureIndicesToRemove(), selector.getFeatureIndicesToRemove());
			// System.err.println(selector.getFeatureIndicesToRemove());

			for (DataRecord r : records) {
				selector.transform(r.getFeatures());
				Assert.assertEquals(0, r.getFeatures().getLargestFeatureIndex());
				// System.err.println(r);
			}
			SubSet sparse = null;
			try (InputStream in = TestResources.SVMLIGHTFiles.CLASSIFICATION_3CLASS.openStream()){
				sparse = SubSet.fromLIBSVMFormat(in);
			}
			
			// SubSet sparse = TestDataLoader.getInstance().getProblem(true, true).getDataset();
			// SubSet dense = new MakeDenseTransformer().fitAndTransform(sparse);


			doCheck(new NumNonZeroSelector(3), sparse, Range.all(), null, null);
			doCheck(new NumNonZeroSelector(3), sparse, Range.closed(100, 350), null, null);
			// printLogs();
		}

		@Test
		public void testFeatureSelecters() throws Exception {
			Stopwatch sw = new Stopwatch();
			sw.start();

			// CLASSIFICATION
			SubSet ds = null;
			try (InputStream stream = TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS.openStream()) {
				ds = SubSet.fromLIBSVMFormat(stream);
			}
			sw.stop();
			// System.out.println("Loaded " + sw + ", ds:size: " + ds.size() + ", " + ds.getNumFeatures());

			Iterator<Transformer> trans = FuzzyServiceLoader.iterator(Transformer.class);

			while (trans.hasNext()) {
				Transformer t = trans.next();
				if (t instanceof FeatureSelector) {
					if (((FeatureSelector) t).applicableToClassificationData())
						doTestRuntime(ds, (FeatureSelector) t);
				}
			}
			// REGRESSION
			try (InputStream stream = TestResources.SVMLIGHTFiles.REGRESSION_HOUSING.openStream();) {
				ds = SubSet.fromLIBSVMFormat(stream);
			}

			trans = FuzzyServiceLoader.iterator(Transformer.class);

			while (trans.hasNext()) {
				Transformer t = trans.next();
				if (t instanceof FeatureSelector) {
					if (((FeatureSelector) t).applicableToRegressionData())
						doTestRuntime(ds, (FeatureSelector) t);
				}
			}
			// printLogs();
		}

		private void doTestRuntime(SubSet input, FeatureSelector selector) {
			SubSet d = input.clone();
			int initSize = d.getNumFeatures();

			Stopwatch sw = new Stopwatch();

			sw.start();
			selector.fit(d);
			sw.stop();
			int toRm = selector.getFeatureIndicesToRemove().size();
			// System.out.println(selector.getName() + " (fit): " + sw + ", rm: " + toRm);

			sw.start();
			selector.transform(d);
			sw.stop();
			int diff = initSize - (d.getNumFeatures() + toRm);
			// System.out.println(selector.getName() + " (transform): " + sw + ", diff rm: " + diff);
			Assert.assertEquals(0, diff);
		}

	}

	@Category(UnitTest.class)
	public static class TestFilter {

		@Test
		public void testSubSampleMajorityCls() throws Exception {

			SubSet multiCls = null;
			try (
					InputStream stream = TestResources.SVMLIGHTFiles.CLASSIFICATION_7CLASS_LARGE.openStream();) {
				multiCls = SubSet.fromInput(stream, new LIBSVMFormat());
			}

			List<SubSet> testSets = Arrays.asList(
					TestDataLoader.getInstance().getDataset(true, false).getDataset(),
					multiCls);

			for (SubSet initial : testSets) {

				// SubSet initial = TestDataLoader.getInstance().getProblem(true,
				// false).getDataset();
				SubSet clone = initial.clone();
				SubSampleMajorityClass filter = new SubSampleMajorityClass();
				Assert.assertTrue(filter.applicableToClassificationData());
				Assert.assertFalse(filter.applicableToRegressionData());

				// First not in place, should not make any difference to the original data
				filter.transformInPlace(false);
				Map<Double, Integer> freq = DataUtils.countLabels(initial);
				// System.err.println("initial freq: " + freq);
				int minClsOcc = freq.values().stream().mapToInt(v -> v).min().getAsInt();

				SubSet result = filter.fitAndTransform(initial);
				Map<Double, Integer> freq_subsampled = DataUtils.countLabels(result);
				Assert.assertEquals("should not change labels",freq.keySet(), freq_subsampled.keySet());
				for (int occ : freq_subsampled.values()) {
					Assert.assertEquals("all class-occ should be the same as the min", minClsOcc, occ);
				}
				// System.err.println("sub freq: " + freq_subsampled);
				Assert.assertTrue("not inPlace, should not change the initial object", initial.equals(clone));

				// Then transform in place
				SubSet clone2 = initial.clone();
				filter.transformInPlace(true);
				SubSet result2 = filter.fitAndTransform(clone2);
				Assert.assertTrue("should return the ref to the same object", result2 == clone2);
				Map<Double, Integer> freq_subsampled2 = DataUtils.countLabels(result);
				Assert.assertEquals("should not change labels",freq.keySet(), freq_subsampled2.keySet());
				for (int occ : freq_subsampled2.values()) {
					Assert.assertEquals("all class-occ should be the same as the min", minClsOcc, occ);
				}
			}



			// Test set re-use and make sure it's correct
			SubSampleMajorityClass filter = new SubSampleMajorityClass();
			filter.setConfigParameters(ImmutableMap.of("reuse",true));
			filter.transformInPlace(true);
			
			int initSize = multiCls.getNumRecords();
			Dataset d = new Dataset();
			d.withDataset(multiCls);

			// Need to use this one to loop through normal and model-exclusive dataset
			// LoggerUtils.setDebugMode(SYS_ERR);
			d.apply(filter);
			
			// System.err.println(d);
			// All data should be there - but re-located to model-excl
			Assert.assertEquals(initSize, d.getNumRecords());
			Assert.assertFalse(d.getModelingExclusiveDataset().isEmpty());
		}

		@Test
		public void testTransformDataset() throws Exception {
			Dataset ds = TestDataLoader.getInstance().getDataset(true, false);
			// System.err.println(ds.getDataset().getDataType());
			// System.err.println(ds.getModelingExclusiveDataset().getDataType());
			// System.err.println(ds.getCalibrationExclusiveDataset().getDataType());
			int initSize = ds.getNumRecords();
			Map<Double,Integer> initFreq = DataUtils.countLabels(ds.getDataset());

			ds.apply(new SubSampleMajorityClass().placeExcludedInModelData(true));

			Assert.assertEquals(initSize, ds.getNumRecords());
			Map<Double,Integer> finalFreq = DataUtils.countLabels(ds);
			Assert.assertEquals(initFreq,finalFreq);
			Assert.assertEquals("All classes should have the same count",new HashSet<>(DataUtils.countLabels(ds.getDataset()).values()).size(),1);

			// Again but without 
			ds = TestDataLoader.getInstance().getDataset(true, false);
		}

	}

	@Category(UnitTest.class)
	public static class TestFormatTransformer {

		@Test
		public void testMakeDense() throws Exception {
			SubSet sparse = TestDataLoader.getInstance().getDataset(true, true).getDataset();
			SubSet dense = new MakeDenseTransformer().transformInPlace(false).fitAndTransform(sparse);
			Assert.assertTrue(dense.get(0).getFeatures() instanceof DenseVector);
			Assert.assertTrue(DataUtils.equals(sparse,dense));

			// Check in-place
			SubSet sparseClone = sparse.clone();
			SubSet denseInPlace = new MakeDenseTransformer().transformInPlace(true).fitAndTransform(sparse);
			Assert.assertTrue(denseInPlace.get(denseInPlace.size()-1).getFeatures() instanceof DenseVector);
			// These should be same instance
			Assert.assertTrue(sparse == denseInPlace);
			Assert.assertTrue(DataUtils.equals(sparseClone,denseInPlace));

			// Same for with mem-save option - this should make the feature vectors as floats instead
			GlobalConfig.getInstance().setMemSaveMode(true); 

			sparse = sparseClone.clone(); 
			MakeDenseTransformer makeDense = new MakeDenseTransformer().transformInPlace(false);
			Assert.assertFalse("Mem-save mode should be single-precision output",makeDense.useDoublePrecision());
			dense = new MakeDenseTransformer().transformInPlace(false).fitAndTransform(sparse);
			Assert.assertTrue(DataUtils.equals(sparseClone,dense,1e-4));

			// Check in-pace 
			denseInPlace = new MakeDenseTransformer().transformInPlace(true).fitAndTransform(sparse);
			Assert.assertTrue(dense.get(0).getFeatures() instanceof DenseFloatVector);
			Assert.assertTrue(denseInPlace.get(denseInPlace.size()-1).getFeatures() instanceof DenseFloatVector);
			// These should be same instance
			Assert.assertTrue(sparse == denseInPlace);
			Assert.assertTrue(DataUtils.equals(sparseClone,denseInPlace,1e-4));
		}

		@Test
		public void testMakeSparse() throws Exception {
			// Start with a dense version
			SubSet dense = new MakeDenseTransformer().fitAndTransform(TestDataLoader.getInstance().getDataset(true, true).getDataset());

			SubSet denseClone = dense.clone();
			Assert.assertFalse("should be different objects",dense == denseClone);

			SubSet sparse = new MakeSparseTransformer().transform(dense);
			SubSet sparse2 = new MakeSparseTransformer().transformInPlace(true).transform(denseClone);

			Assert.assertTrue(sparse.get(0).getFeatures() instanceof SparseVector);
			Assert.assertTrue(sparse2.get(sparse2.size()-1).getFeatures() instanceof SparseVector);
			Assert.assertTrue("inPlace-transformation should return the same instance",denseClone == sparse2);

		}
	}

	public static class TestImputer {

		@Test
		public void testSingleFeatureImputer() throws Exception {
			SingleFeatureImputer imputer =  new SingleFeatureImputer();
			
			Assert.assertTrue(imputer.appliesToNewObjects());
			Assert.assertTrue(imputer.applicableToClassificationData());
			Assert.assertTrue(imputer.applicableToRegressionData());

			imputer.transformInPlace(false);
			SubSet data = TestDataLoader.loadSubset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_100);
			imputer.fit(data);
			int numFeatsIn = data.getNumAttributes();
			Assert.assertEquals(numFeatsIn, imputer.getSubstitutions().size());
			// Create a completely empty input and all indices should have the mean (default impute strategy) value
			Pair<SparseVector, DenseVector> testVectors = getEmptyVectors(numFeatsIn, 0, -50);
			SparseVector sparseNanIn = testVectors.getLeft();
			DenseVector denseNanIn = testVectors.getRight();

			// Impute them
			FeatureVector sparseOut = imputer.transform(sparseNanIn);
			FeatureVector denseOut = imputer.transform(denseNanIn);
			for (int i = 0; i< numFeatsIn; i++){
				double expected = -50;
				if (i!=0){
					expected = imputer.getSubstitutions().get(i);
				}
				Assert.assertEquals(expected, sparseOut.getFeature(i), 0.0001);
				Assert.assertEquals(expected, denseOut.getFeature(i), 0.0001);
			}

			// The same but with dense input
			SingleFeatureImputer imputerDense =  new SingleFeatureImputer();
			
			Assert.assertTrue(imputerDense.appliesToNewObjects());
			Assert.assertTrue(imputerDense.applicableToClassificationData());
			Assert.assertTrue(imputerDense.applicableToRegressionData());

			imputerDense.transformInPlace(false);
			data = new MakeDenseTransformer().fitAndTransform(TestDataLoader.loadSubset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_100));
			imputerDense.fit(data);
			numFeatsIn = data.getNumAttributes();
			Assert.assertEquals(numFeatsIn, imputerDense.getSubstitutions().size());
			// Create a completely empty input and all indices should have the mean (default impute strategy) value
			testVectors = getEmptyVectors(numFeatsIn, 4, 10);
			sparseNanIn = testVectors.getLeft();
			denseNanIn = testVectors.getRight();

			// Impute them
			sparseOut = imputerDense.transform(sparseNanIn);
			denseOut = imputerDense.transform(denseNanIn);
			for (int i = 0; i< numFeatsIn; i++){
				double expected = 10;
				if (i!=4){
					expected = imputerDense.getSubstitutions().get(i);
				}
				Assert.assertEquals(expected, sparseOut.getFeature(i), 0.0001);
				Assert.assertEquals(expected, denseOut.getFeature(i), 0.0001);
			}
			
		}

		@Test
		public void testSingleFeatureImputerMedian() throws Exception {
			// The median imputation has a different implementation - and thus need a separate test
			SingleFeatureImputer imputer =  new SingleFeatureImputer().withStrategy(ImputationStrategy.MEDIAN);
			
			SubSet toyData = new SubSet();
			// -1 0 7
			toyData.add(new DataRecord(0., Arrays.asList((SparseFeature)new SparseFeatureImpl(0, -1),(SparseFeature)new SparseFeatureImpl(2, 7))));
			// -2 0 10
			toyData.add(new DataRecord(0., Arrays.asList((SparseFeature)new SparseFeatureImpl(0, -2),(SparseFeature)new SparseFeatureImpl(2, 10))));
			// 1 0 2
			toyData.add(new DataRecord(0., Arrays.asList((SparseFeature)new SparseFeatureImpl(0, 1),(SparseFeature)new SparseFeatureImpl(2, 2))));

			int numFeatsIn = toyData.getNumAttributes();
			Assert.assertEquals(3, numFeatsIn);
			imputer.fit(toyData);
			// we can manually calculate the median values
			Assert.assertEquals(Map.of(0,-1., 1, 0., 2, 7.), imputer.getSubstitutions());

			// Create completely empty inputs and all indices should have the median value from imputation
			Pair<SparseVector, DenseVector> testVectors = getEmptyVectors(numFeatsIn, 0, -50);
			SparseVector sparseNanIn = testVectors.getLeft();
			DenseVector denseNanIn = testVectors.getRight();

			// Impute them
			FeatureVector sparseOut = imputer.transform(sparseNanIn);
			FeatureVector denseOut = imputer.transform(denseNanIn);
			for (int i = 0; i< numFeatsIn; i++){
				double expected = -50;
				if (i!=0){
					expected = imputer.getSubstitutions().get(i);
				}
				Assert.assertEquals(expected, sparseOut.getFeature(i), 0.0001);
				Assert.assertEquals(expected, denseOut.getFeature(i), 0.0001);
			}


			// The same but for Dense input
			SubSet toyDense = new MakeDenseTransformer().fitAndTransform(toyData);
			SingleFeatureImputer imputer2 =  new SingleFeatureImputer().withStrategy(ImputationStrategy.MEDIAN);
			imputer2.fit(toyDense);

			// Get new Empty input vectors
			testVectors = getEmptyVectors(numFeatsIn, 1, 5);
			sparseNanIn = testVectors.getLeft();
			denseNanIn = testVectors.getRight();
			// Impute them
			sparseOut = imputer2.transform(sparseNanIn);
			denseOut = imputer2.transform(denseNanIn);
			for (int i = 0; i< numFeatsIn; i++){
				double expected = 5;
				if (i!=1){
					expected = imputer.getSubstitutions().get(i);
				}
				Assert.assertEquals(expected, sparseOut.getFeature(i), 0.0001);
				Assert.assertEquals(expected, denseOut.getFeature(i), 0.0001);
			}
			
		}

		public Pair<SparseVector,DenseVector> getEmptyVectors(int fullLen,int fixedIndex, double fixedValue){
			DenseVector denseNanIn = new DenseVector(fullLen).withFeature(fixedIndex, fixedValue); // Empty but with index=0 set something 

			List<SparseFeature> nanFeats = new ArrayList<>();
			for (int i = 0; i < fullLen; i++){
				if (i == fixedIndex)
					nanFeats.add(new SparseFeatureImpl(i, fixedValue));
				else
					nanFeats.add(new MissingValueFeature(i));
			}

			return Pair.of(new SparseVector(nanFeats), denseNanIn);

		}

	}

	@Category(UnitTest.class)
	public static class TestFeatureScaler {

		@Test
		public void testStandardizer() throws Exception {
			SubSet d = TestDataLoader.getInstance().getDataset(true, true).getDataset();

			Standardizer mms = (Standardizer) FuzzyServiceLoader.load(Transformer.class, "normalizer");

			CheckDataset stdChecker = new CheckDataset() {

				@Override
				public boolean check(SubSet ds, ColumnSpec cols) {
					List<Integer> columnInds = cols.getColumns(DataUtils.getMaxFeatureIndex(ds));

					for (int i : columnInds) { // =0; i<=DataUtils.getMaxFeatureIndex(ds.getRecords()); i++) {
						List<Double> featVals = DataUtils.extractColumn(ds, i);

						double mean = MathUtils.mean(featVals);
						Assert.assertEquals(0, mean, 0.001);

						double var = new Variance().evaluate(toArr(featVals));

						if (!(Math.abs(1 - var) <= 0.001 || Math.abs(var) < 0.0001))
							return false;
						// Assert.assertEquals(1, var, 0.001);
					}
					return true;
				}
			};

			doCheck(mms, d, Range.all(), null, stdChecker);
			doCheck(mms, d, Range.closed(4, 19), null, stdChecker);

			// SubSet d = p.getDataset().splitStatic(100)[0];
			//
			// SubSet sparse = d.clone();
			// SubSet dense = new MakeDenseTransformer().fitAndTransform(d);
			// // System.err.println(d.toLibSVMFormat());
			//
			//
			//
			// SubSet transformedDense = mms.fitAndTransform(dense);
			// SubSet transformedSparse = mms.fitAndTransform(sparse);
			//
			// // Verified that this somewhat matches the output of sklearn, with slight
			// differences due to std-calculations?
			// // transformed.writeRecords(new
			// FileOutputStream("/Users/staffan/Desktop/scaled_bias_corr.svm"), false);
			//
			// for (int i=0; i<=DataUtils.getMaxFeatureIndex(transformedDense.getRecords());
			// i++) {
			// List<Double> featVals =
			// DataUtils.extractColumn(transformedDense.getRecords(), i);
			//
			// double mean = MathUtils.mean(featVals);
			// Assert.assertEquals(0, mean,0.001);
			//
			// double var = new Variance().evaluate(toArr(featVals));
			//
			// Assert.assertTrue(Math.abs(1-var) <= 0.001 || Math.abs(var) < 0.0001);
			// // Assert.assertEquals(1, var, 0.001);
			// }
			// // printLogs();
			// Assert.assertTrue(DataUtils.equals(transformedDense, transformedSparse));
		}

		@Test
		public void testMinMaxScaler() throws Exception {
			SubSet d = TestDataLoader.getInstance().getDataset(true, true).getDataset();

			MinMaxScaler mms = (MinMaxScaler) FuzzyServiceLoader.load(Transformer.class, "min-max-scaler");

			mms.setMin(-1);
			mms.setMax(2);

			CheckSF checker = new CheckSF() {
				@Override
				public boolean check(Feature sf) {
					return sf.getValue() >= -1.00001 && sf.getValue() <= 2.00001;
				}
			};

			doCheck(mms, d, Range.closed(5, 15), checker, null);
			doCheck(mms, d, Range.all(), checker, null);
		}

		@Test
		public void testZeroMaxScaler() throws Exception {
			SubSet d = TestDataLoader.getInstance().getDataset(true, true).getDataset();

			ZeroMaxScaler mms = (ZeroMaxScaler) FuzzyServiceLoader.load(Transformer.class, "zero-max-scaler");

			mms.setMax(1.5);

			CheckSF checker = new CheckSF() {
				@Override
				public boolean check(Feature sf) {
					return sf.getValue() >= 0 && sf.getValue() <= 1.50001;
				}
			};

			doCheck(mms, d, Range.closed(5, 15), checker, null);
			doCheck(mms, d, Range.all(), checker, null);
		}

		@Test
		public void testRobustScaler() throws Exception {
	
			RobustScaler rs = new RobustScaler();
	
			CheckDataset dsChecker = new CheckDataset() {
	
				@Override
				public boolean check(SubSet ds, ColumnSpec spec) {
					int nRec = ds.size();
					List<double[]> cols = new ArrayList<>();
					int nFeats = DataUtils.getMaxFeatureIndex(ds);
	
					// List must be +1 as we use index 0 as well
					for (int i = 0; i <= nFeats; i++) {
						cols.add(new double[nRec]);
					}
	
					// Verify the transformed data is correct
					for (int r = 0; r < nRec; r++) {
						for (int i = 0; i < nFeats; i++)
							cols.get(i)[r] = ds.get(r).getFeatures().getFeature(i);
					}
	
					double lq = rs.getLowerQuantile();
					double uq = rs.getUpperQuantile();
					// Make sure the quantiles are OK - for the cols we transformed
					List<Integer> indices = spec.getColumns(nFeats);
	
					for (int i : indices) { // =0; i<nFeats;i++) {
						Percentile p = new Percentile();
						p.setData(cols.get(i));
	
						double low = p.evaluate(lq);
						double upp = p.evaluate(uq);
						double median = p.evaluate(50);
	
						Assert.assertEquals(0d, median, 0.0001);
						if (upp - low > 0.001) {
							// Means we made a scaling
							Assert.assertTrue(upp < 3);
							Assert.assertTrue(low > -3);
						}
					}
					return true;
				}
			};
	
			SubSet d = null;
			try (InputStream fis = TestResources.SVMLIGHTFiles.REGRESSION_ENRICHMENT.openStream();) {
				d = SubSet.fromLIBSVMFormat(fis);
			}
	
			SubSet[] splits = d.splitRandom(.2);
	
			SubSet test = splits[0];
			SubSet train = splits[1];
	
			doCheck(rs, train, Range.all(), null, dsChecker);
			doCheck(rs, train, Range.closed(1, 7), null, dsChecker);
	
			// reset the ranges to use all columns
			rs.setColumns(ColumnSpec.allColumns());
			rs.fit(train);
	
			test = rs.transform(test);
	
			double allowedDiff = 3;
	
			int totalNum = 0, numOutsideRange = 0;
			for (DataRecord r : test) {
				for (Feature sf : r.getFeatures()) {
					if (sf.getValue() > allowedDiff || sf.getValue() < -allowedDiff)
						numOutsideRange++;
					totalNum++;
				}
			}
	
			double ratio = (0d + numOutsideRange) / totalNum;
			Assert.assertTrue("Total number outside [-3,3] should be less than 1%, was: " + ratio, ratio < .01);
		}

	}

	@Category(UnitTest.class)
	public static class TestVectorNormalizer {

		@Test
		public void testRowNormalizer() throws Exception {
			SubSet d_l1 = TestDataLoader.getInstance().getDataset(true, true).getDataset();
			SubSet d_l2 = d_l1.clone();
			SubSet d_l_inf = d_l1.clone();

			// l1
			VectorNormalizer normalizer = (VectorNormalizer) FuzzyServiceLoader.load(Transformer.class, "vector-norm");
			normalizer.useNorm(Norm.L1);
			normalizer.fitAndTransform(d_l1);

			for (DataRecord r : d_l1) {
				Assert.assertEquals(1, DataUtils.l1_norm(r.getFeatures()), 0.001);
			}

			// l2
			VectorNormalizer l2 = normalizer.clone().useNorm(Norm.L2);
			l2.fitAndTransform(d_l2);

			for (DataRecord r : d_l2) {
				Assert.assertEquals(1, DataUtils.l2_norm(r.getFeatures()), 0.001);
			}

			// l_inf / l-max
			VectorNormalizer l_inf = normalizer.clone().useNorm(Norm.L_INF);
			l_inf.fitAndTransform(d_l_inf);

			for (DataRecord r : d_l_inf) {
				Assert.assertEquals(1, DataUtils.l_inf_norm(r.getFeatures()), 0.001);
			}

			// for (int i=0;i<10; i++) {
			// SYS_ERR.println(d_l1.get(i).getFeatures());
			// }
		}

	}

	private static void doCheck(Transformer t, SubSet sparseInput, Range<Integer> cols, CheckSF checker,
			CheckDataset dsChecker) {
		SubSet sparse = sparseInput.clone();
		SubSet dense = new MakeDenseTransformer().transformInPlace(false).fitAndTransform(sparseInput);
		Assert.assertTrue(DataUtils.equals(sparse, dense));

		if (t instanceof ColumnTransformer) {
			((ColumnTransformer) t).setColumns(new ColumnSpec(cols));
			// SYS_ERR.println(new ColumnSpec(cols));
		}

		SubSet sparseTrans = t.clone().fitAndTransform(sparse);
		SubSet denseTrans = t.fitAndTransform(dense);

		// SYS_ERR.println(((ZeroMaxScaler)t).getScaleFacs());

		if (checker != null) {
			int rInd = 0;
			for (DataRecord r : sparseTrans) {
				for (Feature f : r.getFeatures()) {
					if (cols.contains(f.getIndex()))
						Assert.assertTrue(
								"{" + rInd + "}: " + f + " failing: " + t + " "
										+ (t instanceof ColumnTransformer ? ((ColumnTransformer) t).getColumns() : ""),
								checker.check(f));
				}
				rInd++;
			}
			rInd = 0;
			for (DataRecord r : denseTrans) {
				for (Feature f : r.getFeatures()) {
					if (cols.contains(f.getIndex()))
						Assert.assertTrue(
								"{" + rInd + "}: " + f + " failing: " + t + " "
										+ (t instanceof ColumnTransformer ? ((ColumnTransformer) t).getColumns() : ""),
								checker.check(f));
				}
				rInd++;
			}
		}
		if (dsChecker != null) {
			Assert.assertTrue(dsChecker.check(denseTrans, new ColumnSpec(cols)));
			Assert.assertTrue(dsChecker.check(sparseTrans, new ColumnSpec(cols)));
		}
		// LoggerUtils.setDebugMode(SYS_OUT);
		Assert.assertTrue(DataUtils.equals(sparseTrans, denseTrans));
		// Assert.assertEquals(denseTrans, sparseTrans);
	}

	private static interface CheckSF {
		public boolean check(Feature sf);
	}

	private static interface CheckDataset {
		public boolean check(SubSet ds, ColumnSpec cols);
	}

	@Test
	public void testColumnSpec() throws Exception {
		// ALL
		ColumnSpec all = ColumnSpec.allColumns();
		Assert.assertEquals(all, all.clone());
		// ... constructor
		ColumnSpec finite = new ColumnSpec(1, 2, 5, 8, 9);
		Assert.assertEquals(finite, finite.clone());
		// Collection constructor
		finite = new ColumnSpec(Arrays.asList(5, 6, 8, 2, 1));
		Assert.assertEquals(finite, finite.clone());
		// Range constructor
		finite = new ColumnSpec(Range.closedOpen(5, 100));
		Assert.assertEquals(finite, finite.clone());

		// Something that is different
		Assert.assertNotEquals(finite, all);
		Assert.assertNotEquals(finite, new ColumnSpec(1, 2, 4));

		List<Integer> cols = ColumnSpec.allColumns().getColumns(10);
		// SYS_ERR.println(cols);
		Assert.assertEquals(10, Collections.max(cols), 0.000001);
		Assert.assertEquals(0, Collections.min(cols), 0.000001);

	}

	// @Test
	// public void testEqualArrs() {
	// String s1= "1501, 1502, 1504, 1505, 1506, 1507, 1508, 1511, 1519, 1520, 1523,
	// 1526, 1527, 1534, 1535, 1536, 1539, 1542, 1544, 1545, 1547, 1548, 1549, 1552,
	// 1556, 1557, 1558, 1560, 1562, 1563, 1564, 1565, 1566, 1567, 1568, 1571, 1572,
	// 1573, 1578, 1579, 1581, 1582, 1586, 1587, 1589, 1590, 1592, 1593, 1594, 1595,
	// 1596, 1597, 1599, 1601, 1602, 1603, 1604, 1605, 1606, 1607, 1608, 1609, 1611,
	// 1613, 1614, 1615, 1618, 1619, 1621, 1623, 1624, 1625, 1626, 1628, 1630, 1631,
	// 1632, 1635, 1642, 1643, 1644, 1650, 1651, 1652, 1655, 1657, 1666, 1669, 1670,
	// 1671, 1673, 1674, 1675, 1676, 1677, 1678, 1679, 1680, 1681, 1683, 1685, 1686,
	// 1687, 1693, 1697, 1698, 1700, 1702, 1703, 1704, 1705, 1706, 1707, 1708, 1709,
	// 1711, 1712, 1713, 1714, 1715, 1716, 1717, 1720, 1721, 1723, 1725, 1726, 1728,
	// 1730, 1732, 1735, 1736, 1738, 1739, 1740, 1743, 1744, 1745, 1746, 1747, 1748,
	// 1749, 1750, 1751, 1752, 1753, 1754, 1755, 1757, 1759, 1761, 1764, 1765, 1766,
	// 1770, 1774, 1775, 1776, 1777, 1779, 1781, 1783, 1785, 1787, 1790, 1792, 1793,
	// 1795, 1802, 1804, 1806, 1807, 1808, 1809, 1810, 1811, 1812, 1813, 1814, 1815,
	// 1816, 1817, 1819, 1820, 1824, 1825, 1828, 1830, 1833, 1834, 1835, 1836, 1837,
	// 1839, 1840, 1842, 1843, 1844, 1845, 1847, 1848, 1849, 1851, 1852, 1853, 1854,
	// 1855, 1856, 1858, 1861, 1862, 1863, 1864, 1866, 1867, 1868, 1869, 1871, 1872,
	// 1873, 1875, 1877, 1879, 1880, 1888, 1889, 1890, 1891, 1892, 1893, 1894, 1896,
	// 1897, 1898, 1899, 1900, 1902, 1903, 1904, 1906, 1907, 1908, 1909, 1912, 1913,
	// 1914, 1918, 1919, 1921, 1922, 1924, 1925, 1926, 1927, 1928, 1931, 1932, 1933,
	// 1934, 1935, 1936, 1937, 1939, 1940, 1941, 1942, 1943, 1944, 1945, 1946, 1947,
	// 1948, 1950, 1951, 1952, 1953, 1954, 1955, 1957, 1958, 1959, 1960, 1961, 1964,
	// 1965, 1966, 1967, 1968, 1970, 1974, 1975, 1977, 1978, 1979, 1983, 1987, 1988,
	// 1989, 1990, 1997, 2000";
	// String s2= "1501, 1502, 1504, 1505, 1506, 1507, 1508, 1511, 1519, 1520, 1523,
	// 1526, 1527, 1534, 1535, 1536, 1539, 1542, 1544, 1545, 1547, 1548, 1549, 1552,
	// 1556, 1557, 1558, 1560, 1562, 1563, 1564, 1565, 1566, 1567, 1568, 1570, 1571,
	// 1572, 1573, 1578, 1579, 1581, 1582, 1586, 1587, 1589, 1590, 1592, 1593, 1594,
	// 1595, 1596, 1597, 1599, 1601, 1602, 1603, 1604, 1605, 1606, 1607, 1608, 1609,
	// 1611, 1613, 1614, 1615, 1618, 1619, 1621, 1623, 1624, 1625, 1626, 1628, 1630,
	// 1631, 1632, 1642, 1643, 1644, 1650, 1651, 1652, 1655, 1657, 1666, 1669, 1670,
	// 1671, 1673, 1674, 1675, 1676, 1677, 1678, 1679, 1680, 1681, 1683, 1685, 1686,
	// 1687, 1693, 1697, 1698, 1700, 1702, 1703, 1704, 1705, 1706, 1707, 1708, 1709,
	// 1711, 1712, 1713, 1714, 1715, 1716, 1717, 1720, 1721, 1723, 1725, 1726, 1728,
	// 1730, 1732, 1735, 1736, 1738, 1739, 1740, 1743, 1744, 1745, 1746, 1747, 1748,
	// 1749, 1750, 1751, 1752, 1753, 1754, 1755, 1757, 1759, 1761, 1764, 1765, 1766,
	// 1770, 1774, 1775, 1776, 1777, 1779, 1781, 1783, 1785, 1787, 1790, 1792, 1793,
	// 1795, 1802, 1804, 1806, 1807, 1808, 1809, 1810, 1811, 1812, 1813, 1814, 1815,
	// 1816, 1817, 1819, 1820, 1824, 1825, 1828, 1830, 1833, 1834, 1835, 1836, 1837,
	// 1839, 1840, 1842, 1843, 1844, 1845, 1847, 1848, 1849, 1851, 1852, 1853, 1854,
	// 1855, 1856, 1858, 1861, 1862, 1863, 1864, 1866, 1867, 1868, 1869, 1871, 1872,
	// 1873, 1875, 1877, 1879, 1880, 1888, 1889, 1890, 1891, 1892, 1893, 1894, 1896,
	// 1897, 1898, 1899, 1900, 1902, 1903, 1904, 1906, 1907, 1908, 1909, 1912, 1913,
	// 1914, 1918, 1919, 1921, 1922, 1924, 1925, 1926, 1927, 1928, 1931, 1932, 1933,
	// 1934, 1935, 1936, 1937, 1939, 1940, 1941, 1942, 1943, 1944, 1945, 1946, 1947,
	// 1948, 1950, 1951, 1952, 1953, 1954, 1955, 1957, 1958, 1959, 1960, 1961, 1964,
	// 1965, 1966, 1967, 1968, 1970, 1974, 1975, 1977, 1978, 1979, 1983, 1987, 1988,
	// 1989, 1990, 1997, 2000";
	//
	// String[] splits1 = s1.split(",");
	// String[] splits2 = s2.split(",");
	// Assert.assertEquals(splits1.length, splits2.length);
	// for (int i=0;i<splits1.length;i++)
	// Assert.assertEquals("index " + i + " not matching",splits1[i], splits2[i]);
	//
	//// Assert.assertEquals(s1,s2);
	// }

	public static double[] toArr(List<Double> ds) {
		double[] arr = new double[ds.size()];
		for (int i = 0; i < ds.size(); i++) {
			arr[i] = ds.get(i);
		}
		return arr;
	}

	@Test
	public void testFactory() {
		Transformer t = FuzzyServiceLoader.load(Transformer.class, "minmaxscaler");
		Assert.assertTrue(t instanceof MinMaxScaler);
		// System.err.println(t.toString() + " t.names=" + t.getNames());
	}

	// @Test
	@Category(PerformanceTest.class)
	public void makeBenchmark() throws Exception {
		// LoggerUtils.setDebugMode(System.out);
		Dataset data = TestDataLoader.getInstance().getDataset(true, false);
		ACPClassifier acp =	new ACPClassifier(new ICPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC())),
						new RandomSampling(10, .2));
		System.err.println("Number of records: " + data.getNumRecords() + "num attributes: " + data.getNumAttributes());

		TestRunner runner = new TestRunner.Builder(new RandomSplit(.3)).build();

		// Original
		Stopwatch sw = new Stopwatch();
		sw.start();
		List<Metric> origMetric = runner.evaluate(data,acp);
		sw.stop();
		System.err.println("No scaling: " + sw.toString() + " metrics:\n" + origMetric);

		List<Transformer> trans = new ArrayList<>();
		trans.add(new NumNonZeroSelector(5));
		trans.add(new Standardizer());

		sw.start();
		data.apply(trans);
		sw.stop();
		System.err.println("Transform time " + sw.toString() + " num attributes: " + data.getNumAttributes());
		List<Metric> transformedMetric = runner.evaluate(data,acp);
		sw.stop();
		System.err.println("Transform scaling: " + sw.toString() + " metrics:\n" + transformedMetric);

	}



	@Test
	public void testIntraDupResolver() throws Exception {
		SubSet d = TestDataLoader.getInstance().getDataset(true, false).getDataset().clone();
		// int dSize = d.size();
		// dSmall is simply the first 100 records from the large one
		SubSet dSmall = TestDataLoader.getInstance().getDataset(true, true).getDataset().clone();
		// SYS_ERR.println("Original size="+dSmall.size());

		DuplicatesResolverTransformer dup = new RemoveContradictoryRecords();
		dup.transform(d);
		int dNewSize = d.size();

		InterDatasetDuplicatesResolver res = new InterDatasetDuplicatesResolver(new RemoveContradictoryRecords());

		res.transform(d, dSmall);
		// SYS_ERR.println("new size="+dSmall.size());
		Assert.assertEquals(dNewSize, d.size());
		Assert.assertTrue("dSmall.size=" + dSmall.size(), dSmall.isEmpty()); // all should have been removed!

		// New test

		d = TestDataLoader.getInstance().getDataset(true, false).getDataset().clone();
		SubSet[] ds = d.splitRandom(.6);
		SubSet d1 = ds[0];
		SubSet d2 = ds[1];

		new KeepMeanLabel().transform(d2);
		new KeepMeanLabel().transform(d1);
		int d1_size = d1.size();
		int d2_size = d2.size();

		SubSet d1_cl = d1.clone();
		SubSet d2_cl = d2.clone();
		new InterDatasetDuplicatesResolver(new KeepFirstRecord()).transform(d1, d2);
		Assert.assertTrue(d1.size() + d2.size() < d1_size + d2_size); // done an inter-dataset-de-duplication

		new InterDatasetDuplicatesResolver(new KeepLastRecord()).transform(d1_cl, d2_cl);
		Assert.assertEquals(d1.size() + d2.size(), d1_cl.size() + d2_cl.size());

	}

	@Test
	public void testColumnSpecTransform() throws Exception {
		// List<ChemDescriptor> descList = DescriptorFactory.getCDKDescriptorsNo3D();

		// List<ChemDescriptor> descToUse = new ArrayList<>(descList.subList(0, 4));
		// descToUse.add(new SignaturesDescriptor());

		// ChemDataset sp = new ChemDataset(descToUse);
		// sp.initializeDescriptors();

		// try (SDFReader reader = new SDFile(new File(AmesBinaryClass.MINI_FILE_PATH).toURI()).getIterator();) {
		// 	sp.add(new MolAndActivityConverter(reader, AmesBinaryClass.PROPERTY, AmesBinaryClass.AMES_LABELS_NL));
		// }
		Dataset data = TestDataLoader.loadDataset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_100);


		SubSet clone = data.getDataset().clone();

		// SYS_ERR.println(sp.getFeatureNames(true));
		Standardizer std = new Standardizer(new ColumnSpec(Range.closedOpen(0, 14)));
		data.apply(std);

		ZeroMaxScaler zero1 = new ZeroMaxScaler(new ColumnSpec(Range.atLeast(15)));
		data.apply(zero1);

		// SYS_ERR.println(sp.getTransformers());

		for (int i = 0; i < clone.size(); i++) {
			Assert.assertEquals(clone.get(i).getFeatures().getNumExplicitFeatures(),
					data.getDataset().get(i).getFeatures().getNumExplicitFeatures());
		}

	}

	/*
	 * Loaded 1.819 s
	 * Fitting transformer ([keep_max]): 0 ms
	 * transforming ([keep_max]): 1 min 29 s
	 * Fitting transformer ([keep_min]): 0 ms
	 * transforming ([keep_min]): 1 min 25 s
	 * Fitting transformer ([keep_mean]): 0 ms
	 * transforming ([keep_mean]): 1 min 8 s
	 * Fitting transformer ([keep_median]): 0 ms
	 * transforming ([keep_median]): 1 min 6 s
	 * Fitting transformer ([keep_first]): 0 ms
	 * transforming ([keep_first]): 1 min 3 s
	 * Fitting transformer ([keep_last]): 0 ms
	 * transforming ([keep_last]): 1 min 14 s
	 * Fitting transformer ([remove_contradictory]): 0 ms
	 * 
	 * Fitted transformer ([num-nonzero-selection]) in: 163 ms
	 * Applied transformation ([num-nonzero-selection]) in: 15.218 s
	 * Fitted transformer ([variance-based-selection]) in: 21.589 s
	 * Applied transformation ([variance-based-selection]) in: 1.494 s
	 * Fitted transformer ([l1-svc]) in: 3 min 20 s
	 * Applied transformation ([l1-svc]) in: 43.969 s
	 * Fitted transformer ([l2-svc]) in: 12.150 s
	 * Applied transformation ([l2-svc]) in: 21.481 s
	 * Fitted transformer ([l2-svr]) in: 802 ms
	 * Applied transformation ([l2-svr]) in: 24.971 s
	 * Fitted transformer ([drop-missing-data-feats]) in: 49 ms
	 * Applied transformation ([drop-missing-data-feats]) in: 0 ms
	 * 
	 * Fitted transformer ([min-max-scale, min-max-norm]) in: 138 ms
	 */
	@Category(PerformanceTest.class)
	// @Test
	public void doPerformanceTestForScalers() throws Exception {

		Stopwatch sw = new Stopwatch();
		sw.start();
		SubSet ds = null;
		try (InputStream stream = new FileInputStream(
				"/Users/staffan/git/assay-transition/AssayTransition/data/v2/hERG.reg.A1.csr")) {
			ds = SubSet.fromLIBSVMFormat(stream);
		}
		sw.stop();
		SYS_ERR.println("Loaded " + sw);

		// sw.start();
		// MakeDenseTransformer mdf = new MakeDenseTransformer();
		// mdf.fit(ds.getRecords());
		// sw.stop();
		// SYS_ERR.println("fitted make-dense: " + sw);
		//
		// SubSet dense = mdf.transformInPlace(ds); //new
		// MakeDenseTransformer().fitAndTransform(ds);
		// sw.stop();
		// SYS_ERR.println("Converted to dense-matrix: " + sw);

		// doTest(dense, new MinMaxScaler());
		List<Transformer> sparseTransformers = new ArrayList<>();
		// sparseTransformers.add(new ZeroMaxScaler());
		SingleFeatureImputer sfi = new SingleFeatureImputer();
		sfi.withStrategy(ImputationStrategy.MEDIAN);
		sparseTransformers.add(sfi);

		for (Transformer t : sparseTransformers) {
			doTest(ds.clone(), t);
		}

		printLogs();
		// Iterator<Transformer> iter =
		// TransformerFactory.getInstance().getTransformers();
		// //t instanceof FeatureSelecter ||
		// while (iter.hasNext()) {
		// Transformer t = iter.next();
		// if ( t instanceof FeatureScaler || t instanceof Imputer) {
		// doTest(ds.clone(), t);
		// }
		// }

	}

	// @Test
	public void testSummaryStats() throws Exception {
		SummaryStatistics ss = new SummaryStatistics();

		ss.addValue(1);
		ss.addValue(2);
		ss.addValue(5);
		if (Double.isFinite(Double.NaN))
			ss.addValue(Double.NaN);
		Assert.assertTrue(Double.isFinite(ss.getMin()));
		Assert.assertTrue(Double.isFinite(ss.getMax()));
		Assert.assertTrue(Double.isFinite(ss.getMean()));
	}

	private void doTest(SubSet d, Transformer t) {
		Stopwatch sw = new Stopwatch();
		sw.start();
		t.fit(d);
		sw.stop();

		System.err.println("Fitted transformer (" + t.getName() + ") in: " + sw);

		sw.start();
		t.transform(d);
		sw.stop();
		System.err.println("Applied transformation (" + t.getName() + ") in: " + sw);

	}

	/*
	 *
	 * data for A1 hERG
	 * Loaded 3.105 s, ds:size: 64239, 126358
	 * num-nonzero-selection (fit): 183 ms, rm: 58314
	 * num-nonzero-selection (transform): 2.826 s, diff rm: 0
	 * variance-based-selection (fit): 58.184 s, rm: 8028
	 * variance-based-selection (transform): 725 ms, diff rm: 0
	 * l1-svc (fit): 1 min 35 s, rm: 125783
	 * l1-svc (transform): 8.550 s, diff rm: 0
	 * l2-svc (fit): 6.862 s, rm: 81645
	 * l2-svc (transform): 5.110 s, diff rm: 0
	 * drop-missing-data-feats (fit): 73 ms, rm: 0
	 * drop-missing-data-feats (transform): 0 ms, diff rm: 0
	 * 
	 */

}

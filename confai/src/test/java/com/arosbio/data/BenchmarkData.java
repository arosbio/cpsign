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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;

import com.arosbio.commons.Stopwatch;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.transform.Transformer;
import com.arosbio.data.transform.format.MakeDenseTransformer;
import com.arosbio.data.transform.format.MakeSparseTransformer;
import com.arosbio.data.transform.scale.Standardizer;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.TestEnv;

public class BenchmarkData extends TestEnv {

	public static final int NUM_ITER = 10;


	@SuppressWarnings("deprecation")
	@Test
	public void checkSizeSparseFeatures() throws Exception {
		SparseFeature sf1 = new SparseFeatureShort(1, (short)1);
		SYS_OUT.println(ClassLayout.parseInstance(sf1).toPrintable());

		SYS_OUT.println(ClassLayout.parseInstance(new SparseFeatureImpl(1, 214.1241)).toPrintable());
	}

	@Test
	public void testDenseVsSparse() {
		int numAttr = 100;
		DenseVector dv = new DenseVector(new double[numAttr]);
		for (int i=0; i<numAttr; i++) {
			dv.setFeature(i, (double) (i % 10));
		}
		SparseVector sv = new SparseVector(dv);
		DenseVector dv2 = new DenseVector(dv, dv.getLargestFeatureIndex());
		DenseFloatVector dv3 = new DenseFloatVector(dv, dv.getLargestFeatureIndex());


		SYS_OUT.println(GraphLayout.parseInstance(dv).toFootprint());
		SYS_OUT.println(GraphLayout.parseInstance(sv).toFootprint());
		SYS_OUT.println(GraphLayout.parseInstance(dv2).toFootprint());
		SYS_OUT.println(GraphLayout.parseInstance(dv3).toFootprint());

		DenseFloatVector empty = new DenseFloatVector(new float[0]);
		SYS_OUT.println(GraphLayout.parseInstance(empty).toFootprint());
	}

	@Test
	public void checkVeryLarge() throws Exception {
		Stopwatch sw = new Stopwatch();
		sw.start();
		SubSet d = TestDataLoader.loadSubset(TestResources.SVMLIGHTFiles.CLASSIFICATION_7CLASS_LARGE);
		sw.stop();

		SYS_OUT.println("SparseVector " + sw);
		SYS_OUT.println(GraphLayout.parseInstance(d).toFootprint());

		//		new DropColumnSelecter()

		sw.start();
		SubSet dense = new MakeDenseTransformer().fitAndTransform(d);
		sw.stop();
		SYS_OUT.println("Time to make dense: " + sw);
		SYS_OUT.println("DenseVector");
		SYS_OUT.println(GraphLayout.parseInstance(dense).toFootprint());

	}

	//	@Test
	public void checkListVSSet() {
		Set<Integer> set = new LinkedHashSet<>();
		for (int i=0; i<100; i++) {
			set.add(i);
		}
		List<Integer> list = new ArrayList<>(set);
		SYS_OUT.println(GraphLayout.parseInstance(set).toFootprint());
		SYS_OUT.println(GraphLayout.parseInstance(list).toFootprint());
	}

	@Test
	public void checkDenseData() throws Exception {
//		LoggerUtils.setDebugMode(SYS_OUT);
		SubSet sparse = TestDataLoader.loadSubset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_MADELON);

		SYS_OUT.println(GraphLayout.parseInstance(sparse).toFootprint());

		SubSet denseDouble = new MakeDenseTransformer().useDoublePrecision(true).fitAndTransform(sparse);
		SYS_ERR.println(GraphLayout.parseInstance(denseDouble).toFootprint());

		SubSet denseFloat = new MakeDenseTransformer().useDoublePrecision(false).fitAndTransform(sparse);
		SYS_ERR.println(GraphLayout.parseInstance(denseFloat).toFootprint());
	}


	public SubSet getBenchmarkData() throws Exception {
		return TestDataLoader.loadSubset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_MADELON);
	}
	
	@Test
	@Category(PerformanceTest.class)
	public void benchmarkDenseDouble() throws Exception {
		doBenchmark(getBenchmarkData(), new MakeDenseTransformer().useDoublePrecision(true).transformInPlace(false));
		printLogs();
//		Stopwatch sw = new Stopwatch();
//		sw.start();
//		SubSet d = new MakeSparseTransformer().fitAndTransform(sparse);

	}

	@Test
	@Category(PerformanceTest.class)
	public void benchmarkDenseFloat() throws Exception {
//		SubSet d = new MakeDenseTransformer().useDoublePrecision(true).fitAndTransform(sparse);
		doBenchmark(getBenchmarkData(), new MakeDenseTransformer().useDoublePrecision(false).transformInPlace(false));
		printLogs();
	}

	@Test
	@Category(PerformanceTest.class)
	public void benchmarkSparse() throws Exception {
		doBenchmark(getBenchmarkData(), new MakeSparseTransformer().transformInPlace(false));
		printLogs();
//		SubSet d = new MakeDenseTransformer().useDoublePrecision(false).fitAndTransform(sparse);
	}
	
	
	private void doBenchmark(SubSet data, Transformer dataT) {
		System.err.println("running benchmark with data of size: [" + data.size() +","+data.getNumFeatures()+"]");
		// Transform to correct type
		SubSet d = null;
		Stopwatch sw = new Stopwatch();
		sw.start();
		for (int i=0; i<NUM_ITER; i++) {
			d = dataT.fitAndTransform(data);
			blackHole(d);
		}
		sw.stop();
		System.err.println("Converting to correct type: " + sw);
		
		// Standardize / Normalize
		sw.start();
		for (int i=0; i<NUM_ITER; i++) {
			d = new Standardizer().transformInPlace(false).fitAndTransform(d); //.fitAndTransform(data);
			blackHole(d);
		}
		sw.stop();
		System.err.println("Standardize: " + sw);
	}

	private static void blackHole(SubSet data) {
		data.size();
	}
}

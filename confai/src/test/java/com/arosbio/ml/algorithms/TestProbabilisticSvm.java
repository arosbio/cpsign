/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.algorithms;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.ml.algorithms.svm.PlattScaledC_SVC;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.TestDataLoader;
import com.arosbio.testutils.UnitTestInitializer;

@Category(UnitTest.class)
public class TestProbabilisticSvm extends UnitTestInitializer{
	
	@Test
	public void testTranAndPredict() throws Exception {
		LoggerUtils.setDebugMode();
		PlattScaledC_SVC libsvm = new PlattScaledC_SVC();
		SubSet trainingset = TestDataLoader.loadSubset(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_20);
		Assert.assertEquals(20, trainingset.size());
		
		DataRecord first0 = trainingset.remove(0);
		trainingset.addRecord(first0);// put it last instead
		
		System.out.println(first0);
		Assert.assertEquals(20, trainingset.size());
		
		libsvm.train(trainingset);
		System.out.print("labels (after train)=[");
		System.out.print(libsvm.getLabels());
		System.out.println("]");
		
		Map<Integer,Double> pred = libsvm.predictProbabilities(first0.getFeatures());
		System.out.println("pred.length=" +pred.size()+", pred=" + pred);
	}

//	@Test
//	public void testTrainAndPredict() throws Exception {
//		LoggerUtils.setDebugMode();
//		ProbabilisticLibSvm libsvm = new ProbabilisticLibSvm(LibSvmParameters.defaultClassification());
//		SubSet trainingset = SubSet.fromLIBSVMFormat(get("/resources/spambaseShuffled_small.svm"));
//		Assert.assertEquals(20, trainingset.size());
//		
//		DataRecord first0 = trainingset.getRecords().remove(0);
//		trainingset.addRecord(first0);// put it last instead
//		
//		System.out.println(first0);
//		Assert.assertEquals(20, trainingset.size());
//		
//		libsvm.train(trainingset.getRecords());
//		System.out.print("labels (after train)=[");
//		System.out.print(libsvm.getLabels());
//		System.out.println("]");
//		
//		Map<Integer,Double> pred = libsvm.predictProbabilities(first0.getFeatures());
//		
//		System.out.println("pred.length=" +pred.size()+", pred=" + pred);
//		
//		System.out.print("labels (after predict)=[");
//		System.out.print(libsvm.getLabels());
//		System.out.println("]");
////		original.println(systemOutRule.getLog());
//		svm_model m = libsvm.getModel();
//		System.out.println("nr-classes: " + m.nr_class);
//		System.out.println("nr examples: " + m.l);
//		int[] indices = m.sv_indices;
//		System.out.println("indicies: " + indices.length);
//		double[][] coeffs = m.sv_coef;
//		System.out.println("coeffs.length: " + coeffs.length);
//		System.out.println("coeffs[0].length: " + coeffs[0].length);
//		svm_node[][] nodes = m.SV;
//		System.out.println("nodes.length: " + nodes.length);
//		for(int i=0; i<nodes.length;i++)
//			System.out.println("nodes["+i+"].length: " + nodes[i].length);
////		SYS_OUT.println(systemOutRule.getLog());
//	}
//	
//	@Test
//	public void testLabels() throws Exception {
//		List<DataRecord> recs = new ArrayList<>();
//		recs.add(new DataRecord(-5.0, Arrays.asList((SparseFeature)new SparseFeatureImpl(1, 3.0),(SparseFeature)new SparseFeatureImpl(2, 5.0))));
//		recs.add(new DataRecord(-5.0, Arrays.asList((SparseFeature)new SparseFeatureImpl(1, 3.0),(SparseFeature)new SparseFeatureImpl(2, 5.0))));
//		recs.add(new DataRecord(3.0, Arrays.asList((SparseFeature)new SparseFeatureImpl(1, -3.0),(SparseFeature)new SparseFeatureImpl(2, 5.0))));
//		recs.add(new DataRecord(4.5, Arrays.asList((SparseFeature)new SparseFeatureImpl(1, -3.0),(SparseFeature)new SparseFeatureImpl(2, 5.0))));
//		recs.add(new DataRecord(5.0, Arrays.asList((SparseFeature)new SparseFeatureImpl(1, -3.0),(SparseFeature)new SparseFeatureImpl(2, 5.0))));
//		recs.add(new DataRecord(6.0, Arrays.asList((SparseFeature)new SparseFeatureImpl(1, -3.0),(SparseFeature)new SparseFeatureImpl(2, 5.0))));
//		recs.add(new DataRecord(7.0, Arrays.asList((SparseFeature)new SparseFeatureImpl(1, -3.0),(SparseFeature)new SparseFeatureImpl(2, 5.0))));
//		recs.add(new DataRecord(8.0, Arrays.asList((SparseFeature)new SparseFeatureImpl(1, -3.0),(SparseFeature)new SparseFeatureImpl(2, 5.0))));
//		
//		debugLinearProblem(LibSvm.createLibSvmTrainProblem(recs));
//		
//		ProbabilisticLibSvm ll = new ProbabilisticLibSvm(LibSvmParameters.defaultClassification());
//		ll.train(recs);
//		List<Integer> labels = ll.getLabels();
//		System.out.println(labels);
////		SYS_OUT.println(ll.predictProbabilities(Arrays.asList((SparseFeature)new SparseFeatureImpl(1, -3.0),(SparseFeature)new SparseFeatureImpl(2, 5.0))));
//	
//	}
//	
////	private void debugLabels(int [] labels){
////		for(int i=0; i<labels.length;i++)
////			SYS_OUT.print("label["+i+"]="+labels[i]+", ");
////		SYS_OUT.println();
////	}
//	
////	@SuppressWarnings("unused")
//	private void debugLinearProblem(svm_problem problem) {
//
//		StringBuilder builder = new StringBuilder();
//		for (int i=0; i<problem.l;i++){
//			svm_node[] f = problem.x[i];
//			builder.append(problem.y[i]);
//			for (int j=0; j< f.length;j++){
//				builder.append(" " + f[j].index + ":" +f[j].value); 
//			}
//			builder.append("\n");
//		}
//
////		SYS_OUT.println(builder.toString());
//	}

}

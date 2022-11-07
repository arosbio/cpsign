/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.testutils;

import org.junit.Assert;

import com.arosbio.ml.algorithms.MLAlgorithm;
import com.arosbio.tests.utils.TestUtils;

import de.bwaldvogel.liblinear.Model;
import libsvm.svm_model;

public class ModelComparisonUtils {

	public static boolean compareLibSVM_model(svm_model model1, svm_model model2){
		if(model1.l != model2.l){
			System.out.println("l differes");
			return false;
		}
		if(model1.nr_class != model2.nr_class){
			System.out.println("nr_class differes");
			return false;
		}
		if(model1.param != model2.param){
			//			System.out.println("param differes");
			//			System.out.println("model1.param: " + model1.param + "\nmodel2.param: " + model2.param);
			//			return false;
		}
		if((model1.label !=null && model2.label ==null) || (model1.label ==null && model2.label !=null)){
			System.out.println("One of the labels are null, and not the other");
			return false;
		}
		else if(! compareIntArrays(model1.label, model2.label)){
			System.out.println("label differes");
			return false;
		}
		if(! compareIntArrays(model1.nSV, model2.nSV)){
			System.out.println("nSV differes");
			return false;
		}
		if(! compareDoubleArrays(model1.probA, model2.probA)){
			System.out.println("probA differes");
			return false;
		}
		if(! compareDoubleArrays(model1.probB, model2.probB)){
			System.out.println("probB differes");
			return false;
		}
		if(! compareDoubleArrays(model1.rho, model2.rho)){
			System.out.println("rho differes");
			return false;
		}

		if(model1.sv_coef.length != model2.sv_coef.length){
			System.out.println("sv_coef differes");
			return false;
		}

		for(int i=0;i<model1.sv_coef.length; i++){
			if(!compareDoubleArrays(model1.sv_coef[i], model2.sv_coef[i]))
				return false;
		}

		if(model1.SV.length != model2.SV.length)
			return false;
		for(int i=0;i<model1.SV.length; i++){
			if(model1.SV[i].length != model2.SV[i].length)
				return false;
			for(int j=0; j<model1.SV[i].length;j++){
				if((model1.SV[i][j].index !=model2.SV[i][j].index) || (model1.SV[i][j].value !=model2.SV[i][j].value))
					return false;
			}
		}

		return true;
	}

	private static boolean compareIntArrays(int[] arr1, int[]arr2){
		if(arr1 == null && arr2 == null)
			return true;
		if(arr1.length != arr2.length)
			return false;
		for(int i=0;i<arr1.length;i++){
			if(arr1[i]!=arr2[i])
				return false;
		}
		return true;
	}

	private static boolean compareDoubleArrays(double[] arr1, double[]arr2){
		if(arr1 == null && arr2 == null)
			return true;
		if(arr1.length != arr2.length)
			return false;
		for(int i=0;i<arr1.length;i++){
			if(Math.abs(arr1[i]-arr2[i]) > 0.0000001)
				return false;
		}
		return true;
	}
	
	public static void assertEqualMLModels(MLAlgorithm m1, MLAlgorithm m2){
		if(! m1.getClass().equals(m2.getClass())){
			System.out.println("Models are of different implementations");
			Assert.fail("Models are of different implementation");
		}
		if (m1.isFitted() != m2.isFitted()) {
			Assert.fail("One model fitted and the other is not");
		}
		// If fitted - we cannot exactly check that the parameters equals!
		if (m1.isFitted()) {
			return;
		}
		TestUtils.assertEquals(m1.getProperties(), m2.getProperties());//m1.getProperties().equals(m2.getProperties());
//		if(m1 instanceof LibLinear)
//			return compareLibLinModel(((LibLinear) m1).getModel(), ((LibLinear) m2).getModel());
//		else
//			return compareLibSVM_model(((LibSvm) m1).getModel(), ((LibSvm) m2).getModel());
	}
	
	public static boolean compareLibLinModel(Model m1, Model m2){
		
		if(m1.isProbabilityModel() != m2.isProbabilityModel()){
			System.out.println("The probability model differs");
			return false;
		}
		if(m1.getBias() != m2.getBias()){
			System.out.println("The Bias model differs");
			return false;
		}
		if(! compareDoubleArrays(m1.getFeatureWeights(), m2.getFeatureWeights())){
			System.out.println("The featureWeights model differs");
			System.out.print("Arr1: ");printArr(m1.getFeatureWeights());
			System.out.print("Arr2: ");printArr(m2.getFeatureWeights());
			return false;
		}
		
		// labels only defined for classification!
		try{
			m1.getLabels();
			try{
				m2.getLabels();
				if(! compareIntArrays(m1.getLabels(), m2.getLabels())){
					System.out.println("The getLabels model differs");
					return false;
				}
			} catch(NullPointerException e3){
				// here labels (m1) != null but m2 labels are null!
				return false;
			}
		} catch(NullPointerException e){
			try{
				m2.getLabels();
				// Only one of the labels are there, return false!
				return false;
			} catch(NullPointerException e2){
				// Here both labels are null, so no problem!
			}
		}
		if(m1.getNrClass() != m2.getNrClass()){
			System.out.println("The NrClass model differs");
			return false;
		}
		if(m1.getNrFeature() != m2.getNrFeature()){
			System.out.println("The NrFeature model differs");
			return false;
		}

		return true;
	}
	
	
	@SuppressWarnings("unused")
	private static <T> void printArr(T[] arr1){
		if(arr1 == null){
			System.out.println("The array is null");
		}
		String out="";
		for(int i=0; i<arr1.length; i++){
			out += arr1[i] + " ";
		}
		System.out.println(out);
	}
	
	private static void printArr(double[] arr1){
		if(arr1 == null){
			System.out.println("The array is null");
		}
		String out="";
		for(int i=0; i<arr1.length; i++){
			out += arr1[i] + " ";
		}
		System.out.println(out);
	}
	
	@SuppressWarnings("unused")
	private static void printArr(int[] arr1){
		if(arr1 == null){
			System.out.println("The array is null");
		}
		String out="";
		for(int i=0; i<arr1.length; i++){
			out += arr1[i] + " ";
		}
		System.out.println(out);
	}
}

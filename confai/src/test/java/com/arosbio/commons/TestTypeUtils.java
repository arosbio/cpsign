/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.commons;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.ml.metrics.classification.ClassifierAccuracy;
import com.arosbio.ml.metrics.classification.PointClassifierMetric;
import com.arosbio.ml.metrics.regression.RMSE;
import com.arosbio.tests.suites.UnitTest;

@Category(UnitTest.class)
public class TestTypeUtils {

	@Test
	public void testOverrideEquals() {
		Assert.assertTrue(TypeUtils.overridesEquals(Double.valueOf(1)));
		Assert.assertFalse(TypeUtils.overridesEquals(new Object()));
	}
	
	public static interface MyInterface {}
	
	public static class A implements MyInterface {}
	
	public static class B extends A {}
	
	
	public static interface ExtendingInterface extends MyInterface {}
	
	public static class C implements ExtendingInterface{}
	
	public static class NotExtending {}
	
	@Test
	public void testIsOfType() {
		Assert.assertTrue(TypeUtils.isOfType(A.class, MyInterface.class));
		Assert.assertTrue(TypeUtils.isOfType(B.class, MyInterface.class));
		Assert.assertTrue(TypeUtils.isOfType(C.class, MyInterface.class));
		Assert.assertFalse(TypeUtils.isOfType(NotExtending.class, MyInterface.class));
	}

	@Test
	public void testMetrics(){
		Assert.assertTrue(TypeUtils.isOfType(ClassifierAccuracy.class, PointClassifierMetric.class));
		Assert.assertTrue(TypeUtils.objectIsOfType(new ClassifierAccuracy(), PointClassifierMetric.class));
		Assert.assertFalse(TypeUtils.isOfType(RMSE.class, PointClassifierMetric.class));
		Assert.assertFalse(TypeUtils.objectIsOfType(new RMSE(), PointClassifierMetric.class));
		Assert.assertTrue(TypeUtils.objectIsOfType(Double.valueOf(0), Number.class));
		Assert.assertTrue(TypeUtils.objectIsOfType(Double.valueOf(0), Object.class));
		Assert.assertTrue(TypeUtils.objectIsOfType(Double.valueOf(0), MyInterface.class, Number.class));
		Assert.assertTrue(TypeUtils.objectIsOfType(Double.valueOf(0), Double.class));
		Assert.assertFalse(TypeUtils.objectIsOfType(Double.valueOf(0), String.class));
	}

}

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.impl;

import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

import org.junit.Assert;
import org.junit.Test;

import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.commons.config.Configurable.Sorter.Priority;
import com.arosbio.commons.mixins.HasID;
import com.arosbio.commons.mixins.Named;
import com.arosbio.data.transform.Transformer;
import com.arosbio.ml.algorithms.MLAlgorithm;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.cp.nonconf.NCM;
import com.arosbio.ml.cp.nonconf.calc.PValueCalculator;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.sampling.SamplingStrategy;
import com.arosbio.ml.testing.TestingStrategy;

public class TestConfigParams {

	@Test
	public void testEnumSet() {
		LinearSVC lin = new LinearSVC();
		List<ConfigParameter> cp = lin.getConfigParameters();
		for (ConfigParameter c : cp) {
			System.err.println(c);
		}

		//		EnumConfigParameter<SolverType> set = new EnumConfigParameter<Enum<T>>(A, set, defaultValue)
	}
	
	@Test
	public void testNamingConventions() {
//printConfig(ChemDescriptor.class);
//		
//		printConfig(Transformer.class);
//		printConfig(MLAlgorithm.class);
//		printConfig(PValueCalculator.class);
//		printConfig(NCM.class);
//		
//		printConfig(SamplingStrategy.class);
//		printConfig(Metric.class);
//		printConfig(TestingStrategy.class);
		Class<?>[] clazzes = {Transformer.class,MLAlgorithm.class, PValueCalculator.class, NCM.class, SamplingStrategy.class, Metric.class,TestingStrategy.class};
		
		for (Class<?> c : clazzes) {
			Iterator<?> iter = ServiceLoader.load(c).iterator();
			while (iter.hasNext()) {
				Object impl = iter.next();
				
				if (impl instanceof Named) {
					assertValidImplName((Named)impl);
				}
				
				if (impl instanceof Configurable) {
					assertValidParamNames((Configurable)impl);
				}
			}
		}
 	}
	
	public static void assertValidImplName(Named impl) {
		for (String n : TypeUtils.getNames(impl)) {
			Assert.assertTrue("First letter should be upper-case: " + n, 
					Character.isUpperCase(n.charAt(0)));
			Assert.assertEquals("Should not contain white-space nor '-' char: "+ n,
					1, n.split("\\s-").length);
		}
	}
	
	public static void assertValidParamNames(Configurable impl) {
		for (ConfigParameter p : impl.getConfigParameters()) {
			for (String n : p.getNames()) {
				
				if (n.length() > 1)
					Assert.assertTrue("First letter should be lower-case: " + n, 
						Character.isLowerCase(n.charAt(0)));
				Assert.assertEquals("Should not contain white-space nor '-' char: "+ n,
						1, n.split("\\s-").length);
			}
		}
	}

//	@Test
	public void listAllConfigurableAndParams() {
		
		printConfig(Transformer.class);
		printConfig(MLAlgorithm.class);
		printConfig(PValueCalculator.class);
		printConfig(NCM.class);
		
		printConfig(SamplingStrategy.class);
		printConfig(Metric.class);
		printConfig(TestingStrategy.class);

	}

	private static void printConfig(Class<?> theClass) {
		Iterator<?> iter = FuzzyServiceLoader.iterator(theClass);

		while (iter.hasNext()) {
			Object impl = iter.next();
			// names
			if (impl instanceof Named) {
				System.err.println("Names: " + ((Named) impl).getName());
			} else {
				System.err.println("No names : " + impl);
			}

			if (impl instanceof HasID) {
				System.err.println("ID: " + ((HasID) impl).getID());
			}

			if (impl instanceof Configurable) {
				for (ConfigParameter p : ((Configurable) impl).getConfigParameters()) {
					System.err.println(p.getNames());
				}
			}

			// Line between each thingy
			System.err.println();
		}
	}


	@Test
	public void testSorting () {
		System.err.println(Priority.LOW.compareTo( Priority.HIGH));
		// System.err.println(Configurable.Sorter(Priority.LOW,Direction.PREFER_LOWER)> );
	}
}

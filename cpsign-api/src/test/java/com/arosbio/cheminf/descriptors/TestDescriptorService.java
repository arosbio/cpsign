/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cheminf.descriptors;

import java.util.Iterator;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.UnitTestBase;

@Category(UnitTest.class)
public class TestDescriptorService extends UnitTestBase {

	boolean print = false;
	
	@Test
	public void testListDescriptors() {
//		LoggerUtils.setDebugMode(SYS_ERR);
		DescriptorFactory service = DescriptorFactory.getInstance();
		Iterator<ChemDescriptor> descIterator = service.getDescriptors();
		
		int numDescriptors = 0;
		
		while (descIterator.hasNext()) {
			ChemDescriptor d = descIterator.next();
			numDescriptors++;
			if (print)
				System.out.println(d.getName());
			if (d.requires3DCoordinates()) {
				System.err.println("REQUIRE 3D: " + d.getName());
			}
		}
		Assert.assertTrue(numDescriptors > 3);
//		printLogs();
	}

	@Test
	public void testLoadUserSupplied(){
		DescriptorFactory loader = DescriptorFactory.getInstance();
		ChemDescriptor desc = loader.getDescriptorFuzzyMatch("UserSupplied");
		Assert.assertTrue(desc instanceof UserSuppliedDescriptor);
	}
	
}

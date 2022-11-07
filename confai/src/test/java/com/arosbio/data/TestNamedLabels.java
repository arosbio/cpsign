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

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.tests.suites.UnitTest;



@Category(UnitTest.class)
public class TestNamedLabels {

	@Test
	public void TestNumToLabel(){
		List<String> labels = Arrays.asList("nonmutagen", "mutagen");
		NamedLabels nl = new NamedLabels(labels);
		Assert.assertEquals(2, nl.getNumLabels());

		Assert.assertEquals(labels.get(0), nl.getLabel(0));
		Assert.assertEquals(labels.get(1), nl.getLabel(1));
		try{
			nl.getLabel(-3);
			Assert.fail();
		} catch(IllegalArgumentException e){}
	}

	@Test
	public void TestLabelToNum(){
		List<String> labels = Arrays.asList("nonmutagen", "mutagen");
		NamedLabels nl = new NamedLabels(labels);
		Assert.assertEquals(2, nl.getNumLabels());

		Assert.assertEquals((Integer)0, nl.getValue("nonmutagen"));
		Assert.assertEquals((Integer)1, nl.getValue("mutagen"));
		try{
			nl.getValue("not anything");
			Assert.fail();
		} catch(IllegalArgumentException e){}
	}
}

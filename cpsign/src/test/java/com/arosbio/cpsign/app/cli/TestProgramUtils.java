/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app.cli;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.mixins.Named;
import com.arosbio.cpsign.app.CLIBaseTest;
import com.arosbio.cpsign.app.Precompute;
import com.arosbio.cpsign.app.error_handling.InvalidParametersError;
import com.arosbio.cpsign.app.params.mixins.LogfileMixin;
import com.arosbio.cpsign.app.utils.CLIProgramUtils;
import com.arosbio.tests.suites.CLITest;

@Category(CLITest.class)
public class TestProgramUtils extends CLIBaseTest{
	
	@Test
	public void testSetupOutputModelFileIsDirectory() throws Exception {
		
//		exit.expectSystemExitWithStatus(ExitStatus.FAULTY_ARGS.code);
//		exit.checkAssertionAfterwards(new PrintSysOutput());
		
		// This is a directory
		File f = new File(new File("").getParent(), "src");
		
		Assert.assertTrue(f.isDirectory());
		try {
			CLIProgramUtils.setupOutputModelFile(f, new Named() {
				@Override
				public String getName() {
					return "program";
				}
			});
		} catch (InvalidParametersError e) {}
		
	}
	
	@Test
	public void getDefaultModelName() throws IOException {
		// No suffix
		File tmp = File.createTempFile("test", "");
		Assert.assertEquals(tmp.getName(), CLIProgramUtils.getModelNameFromFileName(tmp));
		
		// With suffix and many dots
		tmp = File.createTempFile("test.model.name.gah", ".jar");
		String name = tmp.getName();
		Assert.assertEquals(name.substring(0, name.length()-4), CLIProgramUtils.getModelNameFromFileName(tmp));
		
	}
	
	@Test
	public void testGetField() {
		Precompute p = new Precompute();
		Optional<LogfileMixin> lf = CLIProgramUtils.getFieldByType(p, LogfileMixin.class);
		
		Assert.assertTrue(lf.isPresent());
		// printLogs();
		
	}
	
	@Test
	public void testGetParamName() {
		Assert.assertEquals("--quiet", CLIProgramUtils.getParamName(Precompute.class, "silentMode", "QUIET_MODE"));
	}

}

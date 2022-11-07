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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.fusesource.jansi.AnsiRenderer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.StringUtils;
import com.arosbio.cpsign.app.Precompute;
import com.arosbio.cpsign.app.params.mixins.ClassificationLabelsMixin;
import com.arosbio.cpsign.app.utils.CLIProgramUtils;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.testutils.UnitTestInitializer;

import picocli.CommandLine;


@Category(CLITest.class)
public class TestCLIUtils extends UnitTestInitializer{
	
	final static boolean PRINT_RES = false;
//	
//	private static class TestClass extends CLIGeneralParamsBaseSection {
//		
//		@ParametersDelegate
//		private RNGSeedArg args = new RNGSeedArg();
//		
//		@ParametersDelegate
//		private LicenseArg lic = new LicenseArg();
//	}
//	
//	@Test
//	public void testGetArgument() throws IllegalArgumentException, IllegalAccessException {
//		TestClass base = new TestClass();
//		
//		Assert.assertNotNull(CLIProgramUtils.getArgument(base, RNGSeedArg.class));
//		
//		Assert.assertNull(CLIProgramUtils.getArgument(base, ConsoleVerbosityArgs.class));
//		
//		CLIParamsSection sec = base;
//		
//		CLIParams params = new CLIParams() {
//			
//			@Override
//			public boolean showHiddenParams() {
//				return false;
//			}
//			
//			@Override
//			public boolean isShortUsage() {
//				return false;
//			}
//			
//			@Override
//			public boolean hasFullUsageBeenTriggered() {
//				return false;
//			}
//			
//			@Override
//			public List<CLIParamsSection> getSections() {
//				return Arrays.asList(sec);
//			}
//		};
//		
//		Assert.assertNotNull(CLIProgramUtils.getArgument(params, RNGSeedArg.class));
//		
//		Assert.assertNull(CLIProgramUtils.getArgument(params, ConsoleVerbosityArgs.class));
//		
//		Assert.assertNotNull(CLIProgramUtils.getArgument(params, CLIGeneralParamsBaseSection.class));
//		
//	}
//	
	@Test
	public void testStripFunction() {
		doTestStrip("Something that should not change", "Something that should not change");
		doTestStrip("\"quoted sentence\"", "quoted sentence");
		doTestStrip("\\#AABBCC", "#AABBCC");
	}
	
	private static void doTestStrip(String input, String expectedResult) {
		Assert.assertEquals(StringUtils.stripQuotesAndEscape(input), expectedResult);
	}
	
	@Test
	public void testCLIErr() {
		String txt = AnsiRenderer.render(String.format("%nTry @|bold,italic %s --help|@ for more information.%n","some string")).toString();
//		String txt = org.fusesource.jansi.Ansi.ansi().format("%nTry @|bold,italic %s --help|@ for more information.%n","some string").toString();
		txt = txt.toLowerCase(); 
		Assert.assertFalse(txt.contains("bold"));
		Assert.assertFalse(txt.contains("italic"));
		Assert.assertFalse(txt.contains("@"));
	}
	
	@Test
	public void testSplitArgsToListNumbs(){

		List<String[]> texts = new ArrayList<>();
		texts.add(new String[]{"--labels", "-1 1"});
		texts.add(new String[]{"--labels", "-1, 1"});
		texts.add(new String[]{"--labels", "-1, 1"});
		texts.add(new String[]{"-l", "-1,1"});
		texts.add(new String[]{"-l", "-1, 1"});
//		texts.add(new String[]{"-l", "[-1, 1]"});
//		texts.add(new String[]{"-l", "-1", "-l", "1"});
		
		for (String[] tex: texts){
			ClassificationLabelsMixin labels = new ClassificationLabelsMixin();
			new CommandLine(labels).parseArgs(tex);
			
			Assert.assertEquals(Arrays.asList("-1","1"),labels.labels);
//			SYS_ERR.println("Fixed input: " + StringUtils.join(" ", tex));
		}
	}
	
	
	@Test
	public void testCheckFollowFieldsSilent() {
		Assert.assertEquals("--quiet",CLIProgramUtils.getParamName(Precompute.class, "silentMode", "QUIET_MODE"));
	}

}

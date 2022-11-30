/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.TestEnv;

@Category(UnitTest.class)
public class TestBufferedFileWriter extends TestEnv {

	static String txt = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Mauris iaculis velit nec \nest sagittis fermentum. "
			+ "Sed blandit mollis volutpat. Vivamus venenatis velit ac \nmauris porta aliquet. Nam libero tortor, "
			+ "pharetra sed nisi gravida, molestie \nlaoreet eros. Praesent condimentum, lorem at scelerisque sollicitudin, "
			+ "felis ipsum consequat arcu, nec commodo\neros ex et purus. Pellentesque habitant morbi tristique "
			+ "senectus et netus et malesuada fames ac turpis egestas.\n"
			+ "Donec fermentum, quam et ornare lobortis, ante massa cursus sapien, et faucibus metus libero in augue.";
	
	@Test
	public void testWriter() throws IOException {
		
		File tmpFile = TestUtils.createTempFile("someName", ".txt");
		
		try(
		BufferedFileWriter writer = BufferedFileWriter.getFileWriter(tmpFile);) {
			String[] splits = txt.split("\n");
			
			for (int i=0; i<splits.length-1;i++) {
				writer.write(splits[i]);
				writer.newLine();
			}
			writer.write(splits[splits.length-1]);
				
		}
		
		String readTxt = FileUtils.readFileToString(tmpFile, STANDARD_CHARSET);
		
		Assert.assertEquals(txt, readTxt);
		
	}
	
	@Test
	public void testWriterGZIP() throws IOException {
		
		File tmpFile = TestUtils.createTempFile("someName", ".txt");
		
		try(
		BufferedFileWriter writer = BufferedFileWriter.getCompressedFileWriter(tmpFile);) {
			String[] splits = txt.split("\n");
			
			for (int i=0; i<splits.length-1;i++) {
				writer.write(splits[i]);
				writer.newLine();
			}
			writer.write(splits[splits.length-1]);
				
		}
		
		String unzipped = IOUtils.toString(StreamUtils.unZIP(new FileInputStream(tmpFile)),IOSettings.CHARSET);
		
		Assert.assertEquals(txt, unzipped);
		
	}
	
}

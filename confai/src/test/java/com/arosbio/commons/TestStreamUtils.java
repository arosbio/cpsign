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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.data.Dataset.SubSet;
import com.arosbio.io.StreamUtils;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.UnitTestInitializer;


@Category(UnitTest.class)
public class TestStreamUtils extends UnitTestInitializer{
	
	
	@Test
	public void testUnZIP() throws Exception {
		String startingText = "This is some random text that is used for testing";
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		GZIPOutputStream gzipOut = new GZIPOutputStream(baos);
		OutputStreamWriter streamWriter = new OutputStreamWriter(gzipOut,StandardCharsets.UTF_8);
		streamWriter.write(startingText);
		streamWriter.close();
		ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
		BufferedInputStream baisBuff = new BufferedInputStream(bais);
		
//		String compressedText = IOUtils.toString(new ByteArrayInputStream(baos.toByteArray()));
//		System.out.println(compressedText.getBytes().length);
		
		Assert.assertTrue(StreamUtils.isGZIPCompressed(baisBuff)); // should be a zipped stream
		InputStream unzipped = StreamUtils.unZIP(baisBuff);
		
		String theText = IOUtils.toString(unzipped, StandardCharsets.UTF_8);
//		System.out.println(theText);
		Assert.assertEquals(startingText, theText);
	}
	
	@Test
	public void testUnZIPFromFile() throws Exception {
		// GZIPPED Stream
		try(BufferedInputStream buff = new BufferedInputStream(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_MADELON.openStream());
			InputStream processedStream = StreamUtils.unZIP(buff);){

			SubSet data = SubSet.fromLIBSVMFormat(processedStream);
			Assert.assertEquals(2000, data.getNumRecords());
		}
		
		// NOT - ZIPPED 
		try(BufferedInputStream buff = new BufferedInputStream(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_20.openStream());
			InputStream processedStream = StreamUtils.unZIP(buff);){
			SubSet data = SubSet.fromLIBSVMFormat(processedStream);
			Assert.assertEquals(20, data.getNumRecords());
		}

	}

}

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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.tests.suites.UnitTest;
import com.github.cliftonlabs.json_simple.JsonObject;

@Category(UnitTest.class)
public class TestSystemOutWriters {

	@Test
	public void testForcedWriter() throws IOException {
		PrintStream original = System.out;

		List<String> textShouldBeWritten = Arrays.asList("hej","second Line!", "Thiiiiird  woop woop?","Now printing should work again","doing it again"); 
		String txtNotIncluded = "writing other stuff - should not show";

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(baos);
		System.setOut(ps);

		ForcedSystemOutWriter writer = new ForcedSystemOutWriter(false);
		try (Writer w = new ForcedSystemOutWriter(true);){
			
			Assert.fail();
		} catch (IOException e) {}


		writer.write(textShouldBeWritten.get(0));
		writer.newLine();

		System.out.println(txtNotIncluded);

		writer.write(textShouldBeWritten.get(1));
		writer.newLine();
		writer.write(textShouldBeWritten.get(2));

		writer.close();

		System.out.println(textShouldBeWritten.get(3));

		writer = new ForcedSystemOutWriter(false);
		writer.write(textShouldBeWritten.get(4));
		writer.close();

		String sysOutLog = baos.toString("UTF-8");

		for (String t : textShouldBeWritten)
			Assert.assertTrue(sysOutLog.contains(t));

		Assert.assertFalse(sysOutLog.contains(txtNotIncluded));

		System.setOut(original);

		//		System.out.println(new String(baos.toByteArray()));
	}

	@Test
	public void testForcedWriterCompress() throws IOException {

		String textToWrite = "Some-text-that-should be zipped - long long text!";
		String txtShouldNotBeWritten = "something that shouldn't be included";
		PrintStream sysOutOrig = System.out;

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(baos);
		System.setOut(ps);

		try (ForcedSystemOutWriter writer = new ForcedSystemOutWriter(true)){
			writer.write(textToWrite);
			writer.newLine();
			System.out.println(txtShouldNotBeWritten);
			writer.write(textToWrite);
			//			writer.flush();
		}
		byte[] arr = baos.toByteArray();


		String unzipped = IOUtils.toString(StreamUtils.unZIP(new ByteArrayInputStream(arr)),IOSettings.CHARSET);

		System.setOut(sysOutOrig);

		//		System.out.println("byte length: " + arr.length);
		//		System.out.println("zipped: " + new String(arr));
		//		System.out.println("un-zipped: " + unzipped);

		Assert.assertEquals(textToWrite+"\n" + textToWrite, unzipped);
	}

	@Test
	public void testForcedWithJSON() throws Exception {
		PrintStream sysOutOrig = System.out;

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(baos);
		System.setOut(ps);

		String firstTxt = "Some text";
		String shouldNotShow = "writing to sys-out (1)";
		String shouldShow_AfterJSON = "writing to sys-out (2)";
		Map<String,Object> jsonStuff = new HashMap<>();

		System.out.println(firstTxt);
		
		// Take control of sys-out
		Writer writer = new ForcedSystemOutWriter(false);
		String writtenToForced = "written directly to ForcedWriter\n";
		writer.write(writtenToForced);
		try(IteratingJSONArrayWriter jsonWriter = new IteratingJSONArrayWriter(writer);){
		
			
			System.out.println(shouldNotShow);
			
			jsonStuff.put("someKey", "someValue");
			jsonWriter.write(new JsonObject(jsonStuff));
			jsonStuff.put("key2", "value2");
			jsonWriter.write(new JsonObject(jsonStuff));
			
		}

		String afterJSON = "write after, not released";
		try {
			writer.write(afterJSON); 
			Assert.fail("the forced writer should be closed");
		} catch (Exception e){
			Assert.assertTrue(e instanceof IOException);
			Assert.assertTrue(e.getMessage().contains("closed"));
		}		
		
		System.out.println(shouldShow_AfterJSON);

		// Set back original system out
		System.setOut(sysOutOrig);

		String fullOutput = new String(baos.toByteArray()) ;

		// Text that should be part of output
		Assert.assertTrue(fullOutput.contains(firstTxt)); // Before init of Forced sys out
		Assert.assertTrue(fullOutput.contains(writtenToForced)); // After init - before JSON writer
		// The json stuff - but ignoring white space characters (iterating writer indent things further)
		Assert.assertTrue(fullOutput.replaceAll("\\s", "")
			.contains(new JsonObject(jsonStuff).toJson().replaceAll("\\s", ""))); 
		Assert.assertTrue(fullOutput.contains(shouldShow_AfterJSON)); // After json-writer closed

		// Text that should _not_ have been written
		Assert.assertFalse(fullOutput.contains(shouldNotShow)); // After forced sys out init
		Assert.assertFalse(fullOutput.contains(afterJSON));
	}



}

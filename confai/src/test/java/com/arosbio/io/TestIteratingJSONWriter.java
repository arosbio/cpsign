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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.tests.suites.UnitTest;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

@Category(UnitTest.class)
public class TestIteratingJSONWriter {
    

    static JsonObject jsonItem = new JsonObject();
	static {
		jsonItem.put("key", "value");
		jsonItem.put("key2", 12421);
		
	}

	@Test
	public void testEmptyArray() throws IOException {
        // Without final flush
		String written = doTestWrite(null,false);
		Assert.assertEquals("[ ]", written);

        // With a flush
        String written2 = doTestWrite(null,true);
		Assert.assertEquals("[ ]", written2);

	}
	
	@Test
	public void testSingleItem() throws IOException {
		JsonArray arr = new JsonArray(Arrays.asList(jsonItem));
		String written = doTestWrite(arr, true); 
		
		String correct = Jsoner.prettyPrint(arr.toJson());
		Assert.assertEquals("should equal, with a trailing new-line",correct, written);
	}
	
	@Test
	public void testMultipleItems() throws IOException {
		JsonArray arr = new JsonArray(Arrays.asList(jsonItem, jsonItem, jsonItem, jsonItem));
		String written = doTestWrite(arr,false); 
		
		String correct = Jsoner.prettyPrint(arr.toJson());
		Assert.assertEquals("should equal, with a trailing new-line",correct, written);
	}
	
	private String doTestWrite(List<Object> items, boolean flush) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Writer writer = new OutputStreamWriter(baos);
		try (
				IteratingJSONArrayWriter jsonWriter = new IteratingJSONArrayWriter(writer);){
			if (items != null){
				for (Object item : items) {
					jsonWriter.write((JsonObject)item);
				}
            }
            if (flush){
                writer.flush();
            }
		}
        
		return baos.toString();
	}
	
}

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app.output;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.cpsign.out.JSONResultsWriter;
import com.arosbio.cpsign.out.ResultsHandler;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.UnitTestBase;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.Jsoner;

@Category(UnitTest.class)
public class TestJSONResultWriter extends UnitTestBase {
    
    @Test
    public void testEmpty() throws Exception {

        String empty1 = doTestWrite(null, true);
        Assert.assertEquals("[ ]", empty1);

        String empty2 = doTestWrite(null, true);
        Assert.assertEquals("[ ]", empty2);

        Object json = Jsoner.deserialize(empty1);
        Assert.assertTrue(json instanceof JsonArray);
        JsonArray arr = (JsonArray) json;
        Assert.assertEquals(0, arr.size());
    }

    @Test
    public void testSingle() throws Exception {
        String resFlush = doTestWrite(Arrays.asList(getTestMol()), true);
        String resNoFlush = doTestWrite(Arrays.asList(getTestMol()), false);
        Assert.assertEquals(resFlush, resNoFlush);

        Object json = Jsoner.deserialize(resFlush);
        Assert.assertTrue(json instanceof JsonArray);
        JsonArray arr = (JsonArray) json;
        Assert.assertEquals(1, arr.size());
    }

    private String doTestWrite(List<IAtomContainer> items, boolean flush) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
		try (
            Writer writer = new OutputStreamWriter(baos);
            JSONResultsWriter jsonWriter = new JSONResultsWriter(writer, true);
				){
			if (items != null){
				for (IAtomContainer mol : items) {
					jsonWriter.write(mol, new ResultsHandler());
				}
            }
            if (flush){
                writer.flush();
            }
		}
        
		return baos.toString();
	}
}

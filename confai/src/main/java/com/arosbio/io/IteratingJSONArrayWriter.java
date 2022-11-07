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

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.Writer;

import com.arosbio.commons.StringUtils;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

public class IteratingJSONArrayWriter implements Closeable, Flushable {
	
	// Settings
	private static String NEW_LINE = System.lineSeparator();
	
	// Resources 
	private Writer writer;
	
	// State
	private int numItemsWritten = 0;
	
	
	public IteratingJSONArrayWriter(Writer writer) {
		this.writer = writer;
	}
	
	
	public void write(JsonObject item) throws IOException {
		if (numItemsWritten < 1) {
			writer.write('['+NEW_LINE);
		} else {
			// Separator between items
			writer.write(','+NEW_LINE); 
		}
		
		writer.write(StringUtils.indent(Jsoner.prettyPrint(item.toJson())));
		numItemsWritten++;
	}
	
	public int getNumItemsWritten() {
		return numItemsWritten;
	}

	@Override
	public void close() throws IOException {
		if (numItemsWritten>0)
			writer.write(NEW_LINE+']');
		else
			writer.write("[ ]");
		writer.flush();
		writer.close();
	}


	@Override
	public void flush() throws IOException {
		writer.flush();
	}
	
	

}

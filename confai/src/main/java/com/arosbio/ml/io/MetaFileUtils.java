/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.arosbio.io.IOSettings;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

public class MetaFileUtils {

	
	public static void writePropertiesToStream(OutputStream ostream, Map<String,Object> properties) throws IOException {
		IOUtils.write(new JsonObject(properties).toJson(), ostream, IOSettings.CHARSET);
	}
	
	public static Map<String,Object> readPropertiesFromStream(InputStream istream) throws IOException {
		String rawData = IOUtils.toString(istream,IOSettings.CHARSET);
		try {
			return (JsonObject) Jsoner.deserialize(rawData);
		} catch (JsonException | ClassCastException e){
			throw new IOException("Could not parse input as a property-file, read: " + rawData);
		}
	}
	
}

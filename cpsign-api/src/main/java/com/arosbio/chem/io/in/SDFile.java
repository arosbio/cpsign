/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem.io.in;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.openscience.cdk.silent.SilentChemObjectBuilder;

import com.arosbio.commons.mixins.Described;
import com.arosbio.commons.mixins.Named;
import com.arosbio.io.StreamUtils;

public class SDFile implements ChemFile, Described, Named {

	public static final String FORMAT_NAME = "SDF";
	private URI uri;
	
	public SDFile(URI uri) {
		this.uri = uri;
	}

	@Override
	public URI getURI() {
		return uri;
	}

	@Override
	public String getFileFormat() {
		return FORMAT_NAME;
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String,Object> prop = new HashMap<>();
		prop.put("uri", uri.toString());
		prop.put("fileFormat", getFileFormat());
		return prop;
	}

	@Override
	public SDFReader getIterator() throws IOException {
		try {
			return new SDFReader(StreamUtils.unZIP(uri.toURL().openStream()), SilentChemObjectBuilder.getInstance());
		} catch (MalformedURLException e) {
			throw new IOException(e.getMessage());
		}
	}

	public SDFReader getIterator(ProgressTracker tracker) throws IOException {
		return getIterator(); // No tracker used
	}


	@Override
	public int countNumRecords() throws IOException {
		int records = 0;
		try (
				BufferedReader reader = new BufferedReader(new InputStreamReader(StreamUtils.unZIP(uri.toURL().openStream()))) ) {
			String line;
			while ((line = reader.readLine()) != null) {
				if ( line.startsWith("$$$$") )
					records++;
			}
		} catch (IOException e) {
			throw new IOException("Failed counting the number of lines in URI: " + uri);
		}
		return records;
	}
	
	public String toString() {
		return "SDFile: " + uri;
	}

	@Override
	public String getDescription() {
		return "SDF using v2000 and v3000 MDL formats are supported. Specified as " +
				"\"<flag> sdf <URI | Path>\" or \"<flag> format=sdf <URI | Path>\". "+ 
				"No further arguments supported.";
	}

	@Override
	public String getName() {
		return FORMAT_NAME;
	}

}

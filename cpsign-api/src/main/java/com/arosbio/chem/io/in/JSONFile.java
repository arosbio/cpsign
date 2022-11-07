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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.mixins.Described;
import com.arosbio.commons.mixins.Named;
import com.arosbio.io.StreamUtils;

public class JSONFile implements ChemFile, Described, Named {
	
	public static final String FORMAT_NAME = "JSON";
	public static final String FORMAT_DESCRIPTION = "A simplistic JSON format that is built up by a top-level JSON-array, where "+
			"each object is a record. Each record should be a flat JSON object, including " +
			"a field named \"SMILES\". The remaning key:value pairs will be loaded as " +
			"properties in the current record. No further arguments supported.";

	private URI uri;
	
	public JSONFile(URI uri) {
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
	public JSONChemFileReader getIterator() throws IOException {
		try {
			return new JSONChemFileReader(StreamUtils.unZIP(uri.toURL().openStream()));
		} catch (MalformedURLException e) {
			throw new IOException(e.getMessage());
		} catch (IOException e) {
			throw new IOException("Malformatted JSON file");
		}
		
	}

	@Override
	public int countNumRecords() throws IOException {
		return CollectionUtils.count(getIterator());
	}

	public String toString() {
		return "JSON file: " + uri;
	}

	@Override
	public String getName() {
		return FORMAT_NAME;
	}

	@Override
	public String getDescription() {
		return FORMAT_DESCRIPTION;
	}
}

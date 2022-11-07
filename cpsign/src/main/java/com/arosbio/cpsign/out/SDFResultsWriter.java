/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.out;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.io.SDFWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.CPSignMolProperties;

public class SDFResultsWriter implements PredictionResultsWriter {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SDFResultsWriter.class);
	
	private SDFWriter molWriter;
	private boolean addInChI;
	
	public SDFResultsWriter(Writer writer, boolean v3000, boolean addInChI) {
		this.molWriter = new SDFWriter(writer);
		this.molWriter.setAlwaysV3000(v3000);
		this.addInChI = addInChI;
	}

	@Override
	public void write(IAtomContainer mol, ResultsHandler results) throws IOException {
		CPSignMolProperties.stripEmptyCDKProperties(mol);
		CPSignMolProperties.stripBadProperties(mol);
		CPSignMolProperties.stripInternalProperties(mol);
		Map<Object,Object> props = new LinkedHashMap<>(mol.getProperties());
		
		props.putAll(results.getFlatMapping());
		
		for (Entry<Object,Object> prop: props.entrySet()) {
			if (prop.getKey() != null && prop.getValue() != null)
				props.put(prop.getKey(), prop.getValue().toString());
			else if (prop.getValue() == null)
				props.put(prop.getKey(), "null");
		}
		
		mol.setProperties(props);
		
		if (addInChI)
			CPSignMolProperties.setInChIProperties(mol);
		
		try {
			molWriter.write(mol);
		} catch (Exception e) {
			LOGGER.debug("Failed writing result for mol",e);
		}
	}

	@Override
	public void flush() throws IOException {
	}

	@Override
	public void close() throws IOException {
		try {
			molWriter.close();
		} catch (Exception e) {}
	}

}

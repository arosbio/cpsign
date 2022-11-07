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

import org.openscience.cdk.interfaces.IAtomContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.cpsign.out.OutputNamingSettings.JSON;
import com.arosbio.io.IOUtils;
import com.arosbio.io.IteratingJSONArrayWriter;
import com.github.cliftonlabs.json_simple.JsonObject;

public class JSONResultsWriter implements PredictionResultsWriter {

	private static final Logger LOGGER = LoggerFactory.getLogger(JSONResultsWriter.class);
	
	private IteratingJSONArrayWriter resWriter;
	private boolean addInChI;
	
	public JSONResultsWriter(Writer writer, boolean addInChI) {
		this.addInChI = addInChI;
		resWriter = new IteratingJSONArrayWriter(writer);
	}

	@Override
	public void write(IAtomContainer mol, ResultsHandler results) throws IOException {
		LOGGER.debug("compiling record");
		CPSignMolProperties.stripEmptyCDKProperties(mol);
		
		if (OutputNamingSettings.getCustomSmilesProperty(mol.getProperties().keySet()) == null) {
			LOGGER.debug("setting standard SMILES property");
			String smiles = CPSignMolProperties.getSMILES(mol);
			mol.setProperty(JSON.SMILES_KEY, smiles);
		}
		
		LOGGER.debug("stripping internal properties");
		CPSignMolProperties.stripInternalProperties(mol);
		LOGGER.debug("stripping bad properties");
		CPSignMolProperties.stripBadProperties(mol);
		
		if (addInChI){
			LOGGER.debug("adding InChI to output");
			CPSignMolProperties.setInChIProperties(mol);
		}
		
		JsonObject topLevel = JSONFormattingHelper.toJSON(results.getJSON());
		JsonObject molData = JSONFormattingHelper.toJSON(mol.getProperties());
		topLevel.put(JSON.MOLECULE_SECTION_KEY, molData);
		
		LOGGER.debug("writing record");
		resWriter.write(topLevel);
	}

	@Override
	public void close() throws IOException {
		resWriter.flush();
		IOUtils.closeQuietly(resWriter);
	}

	@Override
	public void flush() throws IOException {
		resWriter.flush();
	}

}

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

import java.io.BufferedWriter;
import java.io.IOException;

import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.cpsign.out.OutputNamingSettings.JSON;
import com.arosbio.io.IOUtils;
import com.github.cliftonlabs.json_simple.JsonObject;

public class SplittedJSONResultsWriter implements PredictionResultsWriter {
	
	private BufferedWriter writer;
	private boolean addInChI;
	
	public SplittedJSONResultsWriter(BufferedWriter writer, boolean addInChI) {
		this.writer = writer;
		this.addInChI = addInChI;
	}

	@Override
	public void write(IAtomContainer mol, ResultsHandler results) throws IOException {
		CPSignMolProperties.stripEmptyCDKProperties(mol);
		CPSignMolProperties.stripBadProperties(mol);
		
		if (OutputNamingSettings.getCustomSmilesProperty(mol.getProperties().keySet()) == null) {
			String smiles = CPSignMolProperties.getSMILES(mol);
			mol.setProperty(JSON.SMILES_KEY, smiles);
		}
		
		CPSignMolProperties.stripInternalProperties(mol);
		
		if (addInChI)
			CPSignMolProperties.setInChIProperties(mol);
		
		
		JsonObject topLevel = JSONFormattingHelper.toJSON(results.getJSON());
		JsonObject molData = JSONFormattingHelper.toJSON(mol.getProperties());
		topLevel.put(JSON.MOLECULE_SECTION_KEY, molData);
		
		writer.write(topLevel.toJson());
		writer.newLine();
	}

	@Override
	public void close() throws IOException {
		writer.flush();
		IOUtils.closeQuietly(writer);
	}

	@Override
	public void flush() throws IOException {
		writer.flush();
	}

}

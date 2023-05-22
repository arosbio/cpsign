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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.openscience.cdk.interfaces.IAtomContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.chem.io.in.FailedRecord.Cause;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

public class JSONChemFileReader implements ChemFileIterator {

	private static final Logger LOGGER = LoggerFactory.getLogger(JSONChemFileReader.class);

	private static final List<String> ALLOWED_SMILES_KEY_NAME = Arrays.asList("smiles","SMILES", "Smiles");
	
	private JsonArray json; // To be read once next/hasNext is called the first time

	private List<IAtomContainer> molecules; // we would like to create a iterating one in the future

	private ProgressTracker tracker = ProgressTracker.createDefault();


	public JSONChemFileReader(InputStream jsonStream) throws IOException {
		try (Reader reader = new InputStreamReader(jsonStream)){
			json = (JsonArray) Jsoner.deserialize(reader);
		} catch (JsonException e) {
			LOGGER.debug("Failed parsing file as json", e);
			throw new IOException("Could not read file as JSON");
		}
	}

	public void setProgressTracker(ProgressTracker tracker) {
		this.tracker = tracker;
	}

	public ProgressTracker getProgressTracker(){
		return tracker;
	}

	public JSONChemFileReader withProgressTracker(ProgressTracker tracker) {
		this.tracker = tracker;
		return this;
	}
	
	private void parseJSON() throws EarlyLoadingStopException {
		molecules = new ArrayList<>();

		Iterator<Object> jsonIterator = json.iterator();
		JsonObject jsonMolecule;
		String smiles = null;
		
		//Record index serves as the identifier to notify which records are failed in parsing/filtered out etc
		int recordIndex = -1; // starts at 0
		
		while (jsonIterator.hasNext()){
			recordIndex++;
			
			tracker.assertCanContinueParsing();
			smiles=null;
			jsonMolecule = (JsonObject) jsonIterator.next();
			
			for (String smileKey : ALLOWED_SMILES_KEY_NAME) {
				if (jsonMolecule.containsKey(smileKey)) {
					smiles = (String) jsonMolecule.get(smileKey);
				}
			}

			if (smiles == null){
				LOGGER.debug("Could not find any molecule(s) in json: {}", jsonMolecule.toJson());
				tracker.register(
					new FailedRecord.Builder(recordIndex,Cause.MISSING_STRUCTURE).withReason("Missing SMILES").build()
					);
				continue;
			}

			IAtomContainer mol = null;
			try {
				mol = ChemFileParserUtils.parseSMILES(smiles);
			} catch (Exception e){
				tracker.register(
					new FailedRecord.Builder(recordIndex,Cause.INVALID_STRUCTURE).withReason(String.format("Invalid SMILES \"%s\"",smiles)).build());
				continue;
			}

			// set all available properties
			for (Object key : jsonMolecule.keySet()){
				if (ALLOWED_SMILES_KEY_NAME.contains(key)){
					CPSignMolProperties.setSMILES(mol, jsonMolecule.get(key).toString());
					continue;
				}
				Object value = jsonMolecule.get(key);
				if(value != null && value instanceof Boolean)
					mol.setProperty(key, value.toString());
				else if(value != null)
					mol.setProperty(key, value);
			}

			CPSignMolProperties.setRecordIndex(mol, recordIndex);

			molecules.add(mol);

		}
	}

	@Override
	public boolean hasNext() throws EarlyLoadingStopException {
		if (molecules == null) {
			parseJSON();
		}
			
		return ! molecules.isEmpty();
	}

	@Override
	public IAtomContainer next() throws NoSuchElementException, EarlyLoadingStopException {
		if (molecules == null) {
			parseJSON();
		}
		if (molecules.isEmpty()) {
			throw new NoSuchElementException("No more elements available");
		}
		return molecules.remove(0);
	}

	@Override
	public void remove() {
		molecules.remove(0);
	}

	@Override
	public void close() {
		// Do nothing, we're reading the full stream so not having any resource allocations open
	}

}

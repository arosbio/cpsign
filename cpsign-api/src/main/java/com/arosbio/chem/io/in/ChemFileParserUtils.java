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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.CPSignMolProperties;


public class ChemFileParserUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(ChemFileParserUtils.class);
	private static SmilesParser smilesParser = new SmilesParser(SilentChemObjectBuilder.getInstance());

	/**
	 * Finds all properties in a SMILES/SDF file by looking at the first 20 molecules 
	 * @param datafile the file
	 * @return list of properties
	 * @throws IOException issues reading from the file
	 */
	public static List<String> findProperties(ChemFile datafile) throws IOException {
		return findProperties(datafile, 20);
	}

	public static List<String> findProperties(ChemFile datafile, int numMols) throws IOException {
		List<String> properties = new ArrayList<>();
		Iterator<IAtomContainer> molsIterator = datafile.getIterator(); 

		// If a CSV file, use the header if given
		if (datafile instanceof CSVChemFileReader) {
			return ((CSVChemFileReader) datafile).getHeaders();
			
		}

		// Else we need to go through the first "numMols" molecules 
		int numMolsChecked = 0;
		IAtomContainer mol;
		while (numMolsChecked < numMols && molsIterator.hasNext()){
			mol = molsIterator.next();
			Map<Object,Object> props = mol.getProperties();

			for (Entry<Object, Object> prop: props.entrySet()){ // Check all properties in the molecule
				if (! properties.contains((String)prop.getKey())){
					// New property
					if (prop.getValue() != null && !prop.getValue().toString().isEmpty()) // remove cdk:title, cdk:remark etc if not set in the file
						properties.add((String)prop.getKey());
				}
			}
			numMolsChecked++;
		}

		return properties;
	}
	

	public static boolean isJSONFile(InputStream stream) throws IOException {
		LOGGER.debug("Checking if stream is a json-chem file");
		try(BufferedReader buffReader = new BufferedReader(new InputStreamReader(stream))){
			char[] firstChars = new char[20];

			int numCharsRead = buffReader.read(firstChars);
			if (numCharsRead <0)
				return false;

			String first20chars = new String(firstChars);
			LOGGER.debug("Checking first 20 chars for start of json:"+first20chars);
			boolean isJSON = first20chars.matches("\\s*?\\[\\s*?\\{\\s*?.*?");
			if (isJSON)
				LOGGER.debug("found stream to be json-formatted");
			else
				LOGGER.debug("stream was not json");
			return isJSON;
		}
	}

	/**
	 * Parses a SMILES and apply aromaticity and suppresses hydrogens of the IAtomContainer 
	 * before returning it. Catches CDK exceptions and throws <code>InvalidSmilesException</code> instead
	 * with a good error message. 
	 * @param smiles the SMILES
	 * @return an {@link IAtomContainer} for the given SMILES
	 * @throws InvalidSmilesException invalid SMILES given
	 */
	public static IAtomContainer parseSMILES(String smiles) throws InvalidSmilesException {
		try{
			IAtomContainer mol = smilesParser.parseSmiles(smiles);
			
			// Add molecule names if present
			String[] splittedLine = smiles.split("\\s+");

			if(splittedLine.length > 1){
				String name = splittedLine[1];
				for(int i=2; i<splittedLine.length; i++)
					name +=" " + splittedLine[i];
				mol.setProperty(CDKConstants.TITLE, name);
			}

			CPSignMolProperties.setSMILES(mol, splittedLine[0]);

			return mol;
		} catch (InvalidSmilesException e){
			LOGGER.debug("failed parsing smiles={}",smiles, e);
			throw new InvalidSmilesException("Could not parse '"+smiles+"' as a valid SMILES");
		} 
	}

}

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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.chem.io.in.ChemFile;
import com.arosbio.chem.io.in.ChemFileParserUtils;
import com.arosbio.chem.io.out.CSVWriter;
import com.arosbio.io.IOUtils;

public class CSVResultsWriter implements PredictionResultsWriter {

	private static final Logger LOGGER = LoggerFactory.getLogger(CSVResultsWriter.class);

	private Writer writer;
	private CSVWriter csvWriter;

	private List<String> inputProperties;
	private boolean addInChI;
	private char delim;

	public CSVResultsWriter(ChemFile predictFile,
			Writer writer,
			boolean addInChI,
			char delim) {

		this.writer = writer;
		this.delim = delim;

		if (addInChI && CPSignMolProperties.isInChIAvailable()){
			this.addInChI = true;
		}

		if (predictFile != null) {
			try {
				inputProperties = CPSignMolProperties.stripInteralProperties(ChemFileParserUtils.findProperties(predictFile));
				LOGGER.debug("Detected properties {} to put first in CSV output", inputProperties);
			} catch (IOException e) {
				LOGGER.debug("Could not parse the predict-file for getting all properties, just using the defaults in output");
				inputProperties = new ArrayList<>();
			}
		} else {
			inputProperties = new ArrayList<>();
		}
	}

	@Override
	public void write(IAtomContainer mol, ResultsHandler res) throws IOException{
		if (csvWriter == null) {
			setupCSVWriter(mol, res);
		} else {
			addProperties(mol, res);
		}

		csvWriter.writeRecord(mol);
	}


	private void setupCSVWriter(IAtomContainer mol, ResultsHandler res) throws IOException {

		Set<Object> propNamesObj = new LinkedHashSet<>(); 
		// Add input properties fist
		if (inputProperties != null && !inputProperties.isEmpty()) {
			propNamesObj.addAll(inputProperties);
		} 
		// Add the properties in the input so these are located first in the output
		CPSignMolProperties.stripEmptyCDKProperties(mol);
		CPSignMolProperties.stripBadProperties(mol);
		propNamesObj.addAll(mol.getProperties().keySet());

		// Add the result properties
		addProperties(mol, res); 
		propNamesObj.addAll(mol.getProperties().keySet());

		// Clean the headers and convert to String
		List<String> propNames = CPSignMolProperties.stripInteralProperties(propNamesObj);

		// Check SMILES column is set, otherwise put it first
		if (OutputNamingSettings.getCustomSmilesProperty(propNames) == null) {
			propNames.add(0,OutputNamingSettings.SMILES_PROPERTY);
		}

		// Set up the CSV Writer
		csvWriter = new CSVWriter(writer, propNames, CSVFormat.DEFAULT.builder().setDelimiter(delim).setRecordSeparator(System.lineSeparator()).build());

	}
	
	private void addProperties(IAtomContainer mol, ResultsHandler res) {

		if (addInChI) {
			CPSignMolProperties.setInChIProperties(mol);
		}

		Map<Object,Object> resultProps = res.getFlatMapping();

		mol.addProperties(resultProps);
	}

	@Override
	public void close() throws IOException {
		try{
			csvWriter.flush();
		} catch (Exception e){}
		IOUtils.closeQuietly(csvWriter);
		IOUtils.closeQuietly(writer);
	}

	@Override
	public void flush() throws IOException {
		if (csvWriter!=null)
			csvWriter.flush(); 
		writer.flush();
	}

}

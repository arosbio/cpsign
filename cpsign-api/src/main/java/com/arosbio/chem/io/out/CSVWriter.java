/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem.io.out;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.commons.CollectionUtils;


public class CSVWriter implements Closeable {

	private static final Logger LOGGER = LoggerFactory.getLogger(CSVWriter.class);
	private static final CSVFormat DEFAULT_FORMAT = CSVFormat.DEFAULT.builder().setRecordSeparator(System.lineSeparator()).build();
	public static final String DEFAULT_SMILES_COL_HEADER = "Smiles";

	private CSVPrinter printer;
	private List<String> headers;
	private int numMolsWritten=0;
	private boolean headerHasBeenWritten = false;
	private String smilesHeaderName = null;

	// EITHER USE A PRE-DEFINED HEADER
	public CSVWriter(Writer writer, List<String> headers) throws IOException {
		this(writer);
		setAndVerifyHeaders(headers); 
	}

	public CSVWriter(OutputStream ostream, List<String> headers) throws IOException {
		this(ostream,headers,DEFAULT_FORMAT);
	}

	public CSVWriter(OutputStream ostream, List<String> headers, CSVFormat format) 
		throws IOException {
		this(ostream,format);
		setAndVerifyHeaders(headers);
	}

	public CSVWriter(Writer writer, List<String> headers, CSVFormat format) throws IOException {
		printer = format.print(writer);
		setAndVerifyHeaders(headers);
	}

	// OR TAKE IT FROM THE MOLECULE LATER ON
	public CSVWriter(OutputStream ostream) throws IOException {
		this(ostream,DEFAULT_FORMAT);
	}

	public CSVWriter(Writer writer) throws IOException {
		this(writer,DEFAULT_FORMAT);
	}

	public CSVWriter(OutputStream ostream, CSVFormat format) throws IOException {
		this(new BufferedWriter(new OutputStreamWriter(ostream)),format);
	}

	public CSVWriter(Writer writer, CSVFormat format) throws IOException {
		printer = format.print(writer);
	}

	public int getNumMolsWritten() {
		return numMolsWritten;
	}
	
	private void setAndVerifyHeaders(List<String> headers) {
		if (headers == null)
			return; // This will set header using the first molecule instead
		
		this.headers = new ArrayList<>(headers);
		smilesHeaderName = CPSignMolProperties.getCustomSmilesProperty(headers);
		
		if (smilesHeaderName == null) {
			// Need to include a header ourselves
			this.headers.add(DEFAULT_SMILES_COL_HEADER);
		}
		
	}

	private void writeHeader() throws IOException {
		for (String header : headers){
			printer.print(header);
		}
		printer.println();

		LOGGER.debug("Header in CSV file: {}",  headers);
		headerHasBeenWritten = true;
		printer.flush();

	}

	public void flush() throws IOException {
		if (printer != null)
			printer.flush();
	}
	
	public void close() throws IOException {
		printer.close(true);
	}

	public void writeRecord(IAtomContainer mol) throws IOException {
		// Check if header has been written
		if (! headerHasBeenWritten){
			if (headers == null || headers.isEmpty())
				setUpHeadersFromMol(mol);
			writeHeader();
		}


		// Get the input SMILES if already given - or generates it otherwise
		mol.setProperty(smilesHeaderName, CPSignMolProperties.getSMILES(mol));

		// Write the record row
		for (String header : headers){
			Object prop = mol.getProperty(header);

			if (prop instanceof double[]) {
				printer.print(CollectionUtils.arrToList((double[])prop));
			} else if (prop instanceof int[]) {
				printer.print(CollectionUtils.arrToList((int[])prop));
			} else {
				printer.print(prop);
			}
		}

		// Done writing the entire row: finish the line
		printer.println();
		printer.flush();
		numMolsWritten++;

	}

	private void setUpHeadersFromMol(IAtomContainer mol){
		LOGGER.debug("Setting up headers from Mol, original properties: {}",mol.getProperties().keySet());
		//Make sure that the columns are empty list
		headers = new ArrayList<>();
		
		// Strip away empty CDK properties 
		List<Object> tmp = CPSignMolProperties.stripEmptyCDKPropertiesKeys(mol.getProperties());
		// Convert to String
		List<String> propertyNames = tmp.stream().map(Object::toString).collect(Collectors.toList());
		// for (Object t : tmp) {
		// 	propertyNames.add(t.toString());
		// }
		LOGGER.debug("(all) prop names: {}", propertyNames);
		// List<Object> noCDK = CPSignMolProperties.stripEmptyCDKPropertiesKeys(propertyNames);
		List<String> stripped = CPSignMolProperties.stripInteralProperties(propertyNames);
		LOGGER.debug("Cleaned property names: {}", stripped);
		
		smilesHeaderName = CPSignMolProperties.getCustomSmilesProperty(stripped);
		
		if (smilesHeaderName == null) {
			// set the default one
			smilesHeaderName = DEFAULT_SMILES_COL_HEADER;
			headers.add(smilesHeaderName);
		}
		// Add the other properties
		headers.addAll(stripped);

	}


}

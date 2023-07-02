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
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.chem.io.in.FailedRecord.Cause;
import com.arosbio.commons.EarlyStoppingException;

public class CSVChemFileReader implements ChemFileIterator {

	private static final Logger LOGGER = LoggerFactory.getLogger(CSVChemFileReader.class);
	private static IChemObjectBuilder DEFAULT_BUILDER = SilentChemObjectBuilder.getInstance();
	private boolean hasLoggedFailedSMILES = false;


	private SmilesParser sp = new SmilesParser(DEFAULT_BUILDER);
	private CSVParser parser;
	private Iterator<CSVRecord> recordIterator;
	private String smilesHeaderField;
	private int numRecordsSuccessfullyRead = 0;

	// Next record
	private IAtomContainer nextMol;

	// Failed records 
	private ProgressTracker tracker = ProgressTracker.createDefault();

	// The next record index
	private int recordIndex = -1;


	public CSVChemFileReader(CSVFormat format, Reader reader) throws IOException {
		initialize(format,reader);
	}

	public CSVChemFileReader(CSVFormat format, Reader reader, String smilesHeaderName) throws IOException {
		this.smilesHeaderField = smilesHeaderName;

		initialize(format,reader);
	}

	public void setProgressTracker(ProgressTracker tracker){
		this.tracker = tracker;
	}
	
	public ProgressTracker getProgressTracker(){
		return tracker;
	}

	public CSVChemFileReader withProgressTracker(ProgressTracker tracker){
		this.tracker = tracker;
		return this;
	}

	private void initialize(CSVFormat format, Reader reader) throws IOException {
		LOGGER.debug("Initializing CSV parser with the following CSVFormat: {}", format.toString());
		if (format.getHeader() == null){
			if (!format.getSkipHeaderRecord())
				format = format.builder().setSkipHeaderRecord(true).build(); 

		}

		this.parser = format.parse(reader);
		this.recordIterator = parser.iterator();

		// Check the SMILES field
		if (smilesHeaderField == null) {
			LOGGER.debug("SMILES header field not set explicitly - trying to deduce from the parsed headers");
			smilesHeaderField = CPSignMolProperties.getCustomSmilesProperty(parser.getHeaderMap().keySet());
			if (smilesHeaderField == null) {
				LOGGER.debug("No header found in CSV that contains SMILES - user needs to set update the file header or set an explicit header for SMILES column");
				throw new IOException("No header found in CSV that contains SMILES - please check the file and give an explicit header");
			} 

		} 
		LOGGER.debug("Header field for smiles set to: {}, CSV reader headers: {}", smilesHeaderField,this.parser.getHeaderMap());
	}

	public List<String> getHeaders(){
		return parser.getHeaderNames(); 
	}

	public String getSmilesColumnHeader(){
		return smilesHeaderField;
	}

	@Override
	public boolean hasNext() throws EarlyStoppingException {
		if (nextMol != null)
			return true;
		else {
			return tryParseNext();
		}
	}

	@Override
	public IAtomContainer next() throws NoSuchElementException, EarlyLoadingStopException {
		if (nextMol != null) {
			numRecordsSuccessfullyRead ++;
			IAtomContainer tmp = nextMol;
			nextMol = null;
			return tmp;
		} else if (tryParseNext()){
			return next();
		} else {
			throw new NoSuchElementException("No more records in CSV");
		}
	}

	private boolean tryParseNext() throws EarlyLoadingStopException {

		if (!recordIterator.hasNext()) {
			LOGGER.debug("No more records in CSV File, found {} records", numRecordsSuccessfullyRead);
			return false;
		}

		// Always start with incrementing the record-index counter
		recordIndex++;

		try {
			CSVRecord next = recordIterator.next();

			String smiles = next.get(smilesHeaderField);
			try {
				nextMol = sp.parseSmiles(smiles);
			} catch (InvalidSmilesException e) {
				if (!hasLoggedFailedSMILES){
					LOGGER.debug("Failed record due to invalid smiles '{}', from record: {}", smiles, next);
					hasLoggedFailedSMILES = true;
				}
				FailedRecord.Builder recordBuilder = null; 
				if (smiles == null || smiles.isBlank()) {
					recordBuilder = new FailedRecord.Builder(recordIndex, Cause.MISSING_STRUCTURE).withReason("Missing SMILES");
				} else {
					recordBuilder = new FailedRecord.Builder(recordIndex, Cause.INVALID_STRUCTURE).withReason(String.format("Invalid SMILES \"%s\"",smiles));
				}
				tracker.register(recordBuilder.build());
				LOGGER.trace("Invalid smiles - skipping line");
				checkIfExit();
				return tryParseNext();
			}

			// Check that the columns were consistent
			if (next.size() > parser.getHeaderNames().size()){
				// numInconsistentRecords++;
				tracker.register(
					new FailedRecord.Builder(recordIndex, Cause.INVALID_RECORD)
						.withReason(
							String.format(Locale.ENGLISH,"Inconsistent number of columns in CSV record, found %d fields but header has %d fields", next.size(),parser.getHeaderNames().size()))
					.build());
				checkIfExit();
				return tryParseNext();
			}

			Map<Object,Object> properties = new LinkedHashMap<>(next.toMap());
			// remove all empty
			List<Object> keysToRemove = new ArrayList<>();
			for (Entry<Object,Object> prop : properties.entrySet()) {
				if (prop.getValue() == null || ((String) prop.getValue()).isEmpty())
					keysToRemove.add(prop.getKey());
			}
			for (Object key : keysToRemove)
				properties.remove(key);

			Map<Object,Object> newMap = new LinkedHashMap<>();
			newMap.putAll(nextMol.getProperties());
			newMap.putAll(properties);
			nextMol.setProperties(newMap);

			// Add the record index
			CPSignMolProperties.setRecordIndex(nextMol, recordIndex);
			CPSignMolProperties.setSMILES(nextMol, smiles);

			return true;
		} catch (EarlyStoppingException e){
			// Pass along
			throw e;	
		} catch (Exception e) {
			LOGGER.debug("Failed parsing line in CSV, continuing to next, err-message: {}", e.getMessage());
			return tryParseNext();
		}
	}

	private void checkIfExit() throws EarlyLoadingStopException {
		tracker.assertCanContinueParsing();
	}

	@Override
	public void close() throws IOException {
		parser.close();
	}

}

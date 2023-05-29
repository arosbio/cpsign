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
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.io.input.BOMInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.BooleanConfig;
import com.arosbio.commons.config.CharConfig;
import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.config.StringConfig;
import com.arosbio.commons.config.StringListConfig;
import com.arosbio.commons.mixins.Described;
import com.arosbio.commons.mixins.Named;
import com.arosbio.io.StreamUtils;

public class CSVFile implements ChemFile, Named, Described, Configurable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CSVFile.class);
	private static final List<String> CONF_SMILES_COL = Arrays.asList("smilesCol", "smilesHeader", "smilesColHeader"),
			CONF_SKIP_HEADER = Arrays.asList("skipFirstRow"),
			CONF_REC_DELIM = Arrays.asList("recDelim","recordDelim", "recordDelimiter"),
			CONF_HEADER = Arrays.asList("header", "customHeader"),
			CONF_COMMENT_MARKER = Arrays.asList("comment", "commentMarker"),
			CONF_DELIM = Arrays.asList("delim", "delimiter"),
			CONF_BOM = Arrays.asList("bom");
	
	public static final String FORMAT_NAME = "CSV";
	public static final String FORMAT_DESCRIPTION = 
			"Character Separated Values, allows to configure to fit the majority of CSV formats. The molecule must be formatted as a valid SMILES string in one of the columns of the CSV.";
	
	private URI uri;

	private char delimiter = '\t';
	private String recordSeparator = System.lineSeparator();
	private boolean ignoreEmptyLines = true;
	private Character commentMarker = null;
	private String[] userSpecifiedHeader = null;
	private boolean includesBOM = false;
	private Boolean skipFirstRow = null;
	private String explicitSmilesHeader = null;
	
	public CSVFile(URI uri) {
		this.uri = uri;
	}

	public URI getUri() {
		return uri;
	}

	public CSVFile setUri(URI uri) {
		this.uri = uri;
		return this;
	}

	public char getDelimiter() {
		return delimiter;
	}

	public CSVFile setDelimiter(char delimiter) {
		this.delimiter = delimiter;
		return this;
	}

	public String getRecordSeparator() {
		return recordSeparator;
	}

	public CSVFile setRecordSeparator(String recordSeparator) {
		this.recordSeparator = recordSeparator;
		return this;
	}

	public boolean isIgnoreEmptyLines() {
		return ignoreEmptyLines;
	}

	public CSVFile setIgnoreEmptyLines(boolean ignoreEmptyLines) {
		this.ignoreEmptyLines = ignoreEmptyLines;
		return this;
	}

	public Character getCommentMarker() {
		return commentMarker;
	}

	public CSVFile setCommentMarker(Character commentMarker) {
		this.commentMarker = commentMarker;
		return this;
	}
	
	public CSVFile setUserDefinedHeader(String... header) {
		this.userSpecifiedHeader = header;
		return this;
	}

	public CSVFile setHasBOM(boolean hasBOM) {
		this.includesBOM = hasBOM;
		return this;
	}
	public boolean getHasBOM(){
		return this.includesBOM;
	}

	/**
	 * Explicitly set if the first row should be skipped. By default,
	 * the first row is treated as a header - unless an explicit header is set. But if an explicit 
	 * header is set - this will be set to false as we assume that there is no header in 
	 * the file in that case. But <b>if there is a header</b> but one that is replaced by an 
	 * explicit header using {@link #setUserDefinedHeader(String[])} the first row should be skipped by calling this method with <code>true</code> 
	 * so that the original header is skipped! 
	 * @param skip <code>true</code> if there is an original header that should be skipped.
	 * @return the reference to the calling CSVFile instance
	 */
	public CSVFile setSkipFirstRow(boolean skip) {
		this.skipFirstRow = skip;
		return this;
	}
	
	public CSVFile setSmilesColumnHeader(String smilesColumn) {
		this.explicitSmilesHeader = smilesColumn;
		return this;
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
		prop.put("delimiter", delimiter);
		prop.put("recordSeparator", recordSeparator);
		prop.put("ignoreEmptyLines", ignoreEmptyLines);
		prop.put("commentMarker", commentMarker);
		prop.put("includesBOM", includesBOM);
		if (userSpecifiedHeader != null)
			prop.put("givenHeader", Arrays.asList(userSpecifiedHeader));
		if (skipFirstRow != null)
			prop.put("skipFirstRow", skipFirstRow);
		prop.put("explicitSmilesHeader", explicitSmilesHeader);
		return prop;
	}

	@Override
	public CSVChemFileReader getIterator() throws IOException {
		CSVFormat f = getFormat();

		try {
			InputStream is = uri.toURL().openStream();
			if (includesBOM)
				is = new BOMInputStream(is);
			return new CSVChemFileReader(f, new InputStreamReader(StreamUtils.unZIP(is)), explicitSmilesHeader);
		} catch (MalformedURLException e) {
			throw new IOException(e.getMessage());
		}
	}

	@Override
	public CSVChemFileReader getIterator(ProgressTracker tracker) throws IOException {
		return getIterator().withProgressTracker(tracker);
	}
	
	public CSVFormat getFormat() {
		CSVFormat.Builder f = CSVFormat.DEFAULT.builder()
				.setDelimiter(delimiter)
				.setIgnoreEmptyLines(ignoreEmptyLines)
				.setRecordSeparator(recordSeparator)
				.setSkipHeaderRecord(true)
				.setHeader();
		if (commentMarker != null)
			f = f.setCommentMarker(commentMarker);
		if (userSpecifiedHeader != null) {
			f = f.setHeader(userSpecifiedHeader);
			if (skipFirstRow != null) {
				f = f.setSkipHeaderRecord(skipFirstRow);
			} else {
				f = f.setSkipHeaderRecord(false); // Default is to skip it 
			}
		}
		return f.build();
	}

	@Override
	public int countNumRecords() throws IOException {
		// For CSV this is the number of lines - minus the header if there is one
		int lines = (getFormat().getSkipHeaderRecord() ? -1 : 0); 

		try (
				BufferedReader reader = new BufferedReader(new InputStreamReader(StreamUtils.unZIP(uri.toURL().openStream()))) ) {
			String line;
			if (commentMarker != null) {
				while ((line = reader.readLine()) != null) {
					if ( line.length() > 0 && ! commentMarker.equals(line.charAt(0)) )
						lines++;
				}
			} else {
				while ((line = reader.readLine()) != null) {
					if ( line.length() > 0 )
						lines++;
				}	
			}
		} catch (IOException e) {
			throw new IOException("Failed counting the number of lines in URI: " + uri);
		}
		return lines;
	}
	
	public String toString() {
		return "CSV file: " + uri;
	}

	@Override
	public String getDescription() {
		return FORMAT_DESCRIPTION;
	}

	@Override
	public String getName() {
		return FORMAT_NAME;
	}
	
	@Override
	public List<ConfigParameter> getConfigParameters() {
		List<ConfigParameter> list = new ArrayList<>();
		list.add(new BooleanConfig.Builder(CONF_BOM, false)
			.description("If the file contains a byte order mark (BOM)")
			.build());
		list.add(new CharConfig.Builder(CONF_COMMENT_MARKER, null)
			.description("Specify a character that marks a row as a comment (as allowed in some CSV formats)")
			.build());
		list.add(new CharConfig.Builder(CONF_DELIM, delimiter)
			.description("Specify what character is used for splitting columns in the CSV")
			.build());
		list.add(new StringListConfig.Builder(CONF_HEADER, null)
			.description("CSV files *must* have a header - either explicit in the file or given to this parameter")
			.build());
		list.add(new StringConfig.Builder(CONF_REC_DELIM, recordSeparator)
			.description("Specify what character(s) is/are used for splitting between records in the CSV, the default is the 'new line' of the machine the JVM is running on")
			.build());
		list.add(new BooleanConfig.Builder(CONF_SKIP_HEADER, false)
			.description("The default is to expect an explicit header in the file, but if a custom header is given by the 'header' parameter - an existing header can be skipped and ignored")
			.build());
		list.add(new StringConfig.Builder(CONF_SMILES_COL, null)
			.description("Explicitly set which column in the CSV that contain the SMILES. "+
				"By default the headers will be searched left-to-right and the first header "+
				"containing 'SMILES' (case-insensitive) will be used. E.g. usefull if the header "+
				"contain multiple columns with the text SMILES or if the column is called something else")
			.build());
		return list;
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
		
		for (Map.Entry<String, Object> kv : params.entrySet()) {
			try {
				String key = kv.getKey();
				if (CollectionUtils.containsIgnoreCase(CONF_SMILES_COL,key)){
					explicitSmilesHeader = kv.getValue().toString();
				} else if (CollectionUtils.containsIgnoreCase(CONF_SKIP_HEADER,key)){
					skipFirstRow = TypeUtils.asBoolean(kv.getValue());
				} else if (CollectionUtils.containsIgnoreCase(CONF_REC_DELIM,key)){
					recordSeparator = kv.getValue().toString();
				} else if (CollectionUtils.containsIgnoreCase(CONF_HEADER,key)){
					Object val = kv.getValue();
					if (val instanceof List<?>) {
						userSpecifiedHeader = ((List<?>) val).toArray(new String[] {});
					} else if (val instanceof String[]) {
						userSpecifiedHeader = (String[]) val;
					} else {
						LOGGER.debug("Parameter "+key + " had invalid format (must be list or array of string), but was: " + val.getClass());
						throw new IllegalArgumentException("Invalid format");
					}
				} else if (CollectionUtils.containsIgnoreCase(CONF_DELIM,key)){
					delimiter = TypeUtils.asChar(kv.getValue());
				} else if (CollectionUtils.containsIgnoreCase(CONF_BOM,key)){
					includesBOM = TypeUtils.asBoolean(kv.getValue());
				}
			} catch (Exception e) {
				LOGGER.debug("failed setting config with key{"+kv.getKey() +"} and value {"+kv.getValue()+"}",e);
				throw new IllegalArgumentException("Invalid parameter '"+kv.getKey()+ "' with value: "+kv.getValue());
			}
		}
		
	}

}

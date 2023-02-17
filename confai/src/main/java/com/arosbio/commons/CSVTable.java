/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.commons;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CSVTable {

	private final static Logger LOGGER = LoggerFactory.getLogger(CSVTable.class);
	private final Map<String, List<? extends Object>> columns;
	private final int numRows;

	public CSVTable(Map<String, List<? extends Object>> columns) {
		this.columns = columns;
		numRows = columns.values().iterator().next().size();
		for (List<? extends Object> ent: columns.values())
			if (ent.size() != numRows)
				throw new IllegalArgumentException("Number of rows in the CSV must be the same!");
	}

	public CSVTable(Map<String, List<? extends Object>> columns, Map<String,Object> singleValued) {
		this(columns);
		if (singleValued != null && !singleValued.isEmpty())
			appendSingleValuedColumns(singleValued);
	}

	public void appendSingleValuedColumns(Map<String,Object> singleValued) {
		for (Map.Entry<String, Object> col: singleValued.entrySet())
			appendSingleValuedColumn(col.getKey(), col.getValue());
	}
	public void appendSingleValuedColumn(String header, Object value) {
		columns.put(header, CollectionUtils.rep(value, numRows));
	}

	public String toCSV(char delim) {
		return toCSV(CSVFormat.DEFAULT.builder().setDelimiter(delim).build());
	}

	public String toCSV(CSVFormat format) {
		StringBuilder sb = new StringBuilder();
		try(CSVPrinter p = new CSVPrinter(sb, format)){
			toCSV(p);
		} catch (IOException e){
			LOGGER.debug("exception printing CSVTable to string, should never happen",e);
		}

		return sb.toString();
	}

	public void toCSV(CSVPrinter printer) throws IOException {
		tidyData();

		// Header
		for (String header: columns.keySet())
			printer.print(header);
		printer.println();


		// All other rows
		for (int i=0;i<numRows; i++) {
			for (List<? extends Object> col : columns.values()) {
				printer.print(col.get(i));
			}
			printer.println();
		}
		printer.flush();

	}
	
	public void tidyData() {
		for (Map.Entry<String, List<? extends Object>> col: columns.entrySet()) {
			
			if (isColumnOfFPvalues(col.getValue())){
				List<Number> tidyCol = new ArrayList<>();
				for (Object o: col.getValue()) {
					if (o instanceof Integer)
						tidyCol.add((int)o);
					else if (o instanceof Long)
						tidyCol.add((long)o);
					else
						tidyCol.add(MathUtils.roundTo3significantFigures((double)o));
				}
				columns.put(col.getKey(), tidyCol);
			}
		}
	}
	
	
	/**
	 * Checks if the full list is of floating point (or double) values
	 * @param col
	 * @return
	 */
	private static <T> boolean isColumnOfFPvalues(List<T> col) {
		for (Object val: col) {
			if (!(val instanceof Double || val instanceof Float) )
				return false;
		}
		return true;
	}
}

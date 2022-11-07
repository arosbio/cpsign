/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector.Feature;
import com.arosbio.data.MissingValueFeature;
import com.arosbio.data.SparseFeature;
import com.arosbio.data.SparseFeatureImpl;
import com.arosbio.io.IOSettings;

/**
 * Reads and writes records using the format:
 * 
 * <pre>
 * {@code 
 *<label> <index>:<value> <index>:<value> ..
 *<label> <index>:<value> <index>:<value> ..
 *..
 * }
 * </pre>
 * 
 * Note that the index <b>must</b> start at 1. No compression/decompression is performed here, that should be handled
 * outside of this class
 * 
 * @author staffan
 *
 */
public class LIBSVMFormat implements DataSerializationFormat {

	private static final Logger LOGGER = LoggerFactory.getLogger(LIBSVMFormat.class);
	public static final String FORMAT_NAME = "LibSVMFormat";

	private static double atof(String s)
	{
		return Double.valueOf(s).doubleValue();
	}

	private static int atoi(String s)
	{
		return Integer.parseInt(s);
	}

	@Override
	public SubSet read(InputStream stream) throws IOException {
		String line = null;
		List<DataRecord> recs = new ArrayList<>();
		try( 
				BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
				) {
			line = reader.readLine();

			while (line!=null) {
				// Parse the line
				recs.add(readLine(line));
				// Next one
				line = reader.readLine();
			} 
		} catch (NumberFormatException e) {
			LOGGER.debug("SubSet not in LIBSVM format",e);
			throw new IOException("Data not in LIBSVM format");
		} catch(IOException e) {
			LOGGER.debug("Failed reading from stream",e);
			throw new IOException("Could not load LIBSVM dataset: "+ e.getMessage());
		} catch (Exception e){
			LOGGER.debug("Failed reading LIBSVM dataset",e);
			if (recs.isEmpty()) {
				LOGGER.debug("Could not read any records from input, is this LIBSVM format? {}", line);
				throw new IOException("Data not in LIBSVM format");
			}
			
			throw new IOException(e.getMessage());
		}

		LOGGER.debug("Parsed {}  lines from LIBSVM format", recs.size());

		return new SubSet(recs);	
	}

	public DataRecord readLine(String line) {

		String[] chunks = line.split("\\s+");
		double label = atof(chunks[0]);

		List<SparseFeature> features = new ArrayList<>();

		String[] feature = null;
		for (int i = 1; i< chunks.length;i++){
			feature = chunks[i].split(":");
			int index = atoi(feature[0])-1; // -1 to start from 0 and not 1
			double value = atof(feature[1]);
			if (Double.isNaN(value)) {
				features.add(new MissingValueFeature(index));
			} else if (value==0) { 
				// Skip those that have identical to 0 value 
			} else {
				features.add(new SparseFeatureImpl(index, value));
			}

		}
		return new DataRecord(label, features);
	}
	
	public static String serialize(DataRecord rec) {
		StringBuilder sb = new StringBuilder();
		// Label
		sb.append(rec.getLabel());

		// Features
		for (Feature f : rec.getFeatures()) {
			sb.append(' ');
			sb.append(f.getIndex()+1); // Starts at index 1
			sb.append(':');
			sb.append(f.getValue());
		}
		return sb.toString();
	}

	@Override
	public void write(OutputStream ostream, SubSet data) throws IOException {

		try(
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(ostream, IOSettings.CHARSET));
				) {
			for (DataRecord rec: data){

				// Label
				writer.write(""+rec.getLabel());

				// Features
				for (Feature f : rec.getFeatures()) {
					writer.write(' ');
					writer.write(""+(f.getIndex()+1)+':'+f.getValue()); // +1 for starting at 1 in LIBSVM format
				}

				// new line
				writer.newLine();
			}
		}
		LOGGER.debug("Written {} records in LIBSVM format",data.size());
	}

	public String getName(){
		return FORMAT_NAME;
	}

	public String toString(){
		return this.getClass().getCanonicalName();
	}


}

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

import com.arosbio.commons.GlobalConfig;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.DenseFloatVector;
import com.arosbio.data.DenseVector;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.SparseFeature;
import com.arosbio.data.SparseVector;
import com.arosbio.io.IOSettings;

public class DenseFormat implements DataSerializationFormat {

	private static final Logger LOGGER = LoggerFactory.getLogger(DenseFormat.class);
	public static final String FORMAT_NAME = "DenseFormat";
	
	private boolean useDoublePrecision; 

	public DenseFormat(boolean useDoublePrecision){
		this.useDoublePrecision=useDoublePrecision;
	}
	public DenseFormat(){
		this(! GlobalConfig.getInstance().isMemSaveMode());
	}

	public DenseFormat withDoublePrecision(boolean dp){
		this.useDoublePrecision = dp;
		return this;
	}

	
	private static double atof(String s){
		return Double.valueOf(s);
	}

	@Override
	public SubSet read(InputStream stream) throws IOException {
		String line = null;
		List<DataRecord> recs = new ArrayList<>();
		try( 
				BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
				) {
			
			// Read the first line - determine the size of the vectors
			line = reader.readLine();
			int firstNumChunks = line.split("\\s+").length;
			
			while (line!=null) {
				String[] chunks = line.split("\\s+");
				
				if (chunks.length != firstNumChunks) {
					LOGGER.debug("The width of the dense-formatted records are not equal {} != {}", firstNumChunks, chunks.length);
					throw new IOException("File not in Dense format");
				}
				
				// First is label
				double label = atof(chunks[0]);
				
				// The rest should be singular values
				if (useDoublePrecision) {
					double[] vec = new double[firstNumChunks - 1]; 
					for (int i = 1; i < chunks.length; i++){
						vec[i-1] = atof(chunks[i]);
					}
					
					recs.add(new DataRecord(label, new DenseVector(vec)));
				} else {
					float[] vec = new float[firstNumChunks - 1]; 
					for (int i = 1; i < chunks.length; i++){
						vec[i-1] = (float) atof(chunks[i]);
					}
					
					recs.add(new DataRecord(label, new DenseFloatVector(vec)));
				}
				
				// Read next line
				line = reader.readLine();
			} 
		} catch (NumberFormatException e) {
			LOGGER.debug("SubSet not in Dense format",e);
			throw new IOException("Data set not in Dense format");
		} catch (Exception e){
			if (recs.isEmpty()) {
				LOGGER.debug("Could not read any records from input, is this Dense format? {}", line);
				throw new IOException("Data set not in Dense format");
			}
			throw new IOException(e.getMessage());
		}
		
		LOGGER.debug("Parsed {} records from Dense format",recs.size());

		return new SubSet(recs);
		
	}

	@Override
	public void write(OutputStream ostream, SubSet data) throws IOException {
		int maxFeatIndex = DataUtils.getMaxFeatureIndex(data);
		try(
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(ostream, IOSettings.CHARSET));
				) {
			for (DataRecord rec : data){
				// Add record to one line
				append(writer, rec, maxFeatIndex);
				// new line
				writer.newLine();
			}
		}
		LOGGER.debug("Written {} records in dense format",data.size());
	}

	public String serialize(DataRecord record){
		StringBuilder sb = new StringBuilder();
		try{
			append(sb, record, record.getMaxFeatureIndex());
		} catch(IOException e){
			// Should never happen to th StringBuilder
			LOGGER.debug("Failed serializing single record",e);
		}
		return sb.toString();
	}

	private void append(Appendable a, DataRecord rec, int maxFeatIndex) throws IOException {
		// Label
		a.append(Double.toString(rec.getLabel()));
						
		// Features - depend on concrete implementation 
		FeatureVector vec = rec.getFeatures();
		if (vec instanceof DenseVector){
			append(a, (DenseVector)vec);
		} else if (vec instanceof DenseFloatVector){
			append(a, (DenseFloatVector)vec);
		} else {
			append(a, (SparseVector)vec, maxFeatIndex);
		}
	}
	private void append(Appendable a, DenseVector v) throws IOException{
		for (double d : v.getInternalArray()){
			a.append(' ').append(Double.toString(d));
		}
	}
	private void append(Appendable a, DenseFloatVector v) throws IOException{
		for (float f : v.getInternalArray()){
			a.append(' ').append(Float.toString(f));
		}
	}
	
	private void append(Appendable a, SparseVector v, int maxFeat) throws IOException {
		int vInd = 0;
		List<SparseFeature> lst = v.getInternalList();
		SparseFeature current = lst.get(0);
		for (int i=0; i<=maxFeat; i++){
			a.append(' ');
			if (current.getIndex() == i){
				a.append(Double.toString(current.getValue()));
				vInd++;
				if (vInd < lst.size()){
					current = lst.get(vInd);
				}
			} else {
				a.append('0');
			}
		}
		
	}

	public String getName(){
		return FORMAT_NAME;
	}

	public String toString(){
		return this.getClass().getCanonicalName();
	}

}

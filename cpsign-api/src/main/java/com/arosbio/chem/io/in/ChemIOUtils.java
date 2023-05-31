package com.arosbio.chem.io.in;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import com.arosbio.commons.CollectionUtils;

public class ChemIOUtils {


    private static final int READ_LIMIT_SDF_JSON_BYTES = 500;
    private static final int READ_LIMIT_CSV_BYTES = 2000;

    public static enum ChemIOFormat {
        SDF ("SDF"), CSV("CSV"), JSON("JSON"), UNKNOWN("Unknown");

        public final String name;

        private ChemIOFormat(String name){
            this.name = name;
        }

        public String toString(){
            return name;
        }
    }

    public static ChemIOFormat deduceFormat(URI in) throws IOException {
        try(BufferedInputStream stream = new BufferedInputStream(in.toURL().openStream())){
            return deduceFormat(stream);
        }
    }

    public static ChemIOFormat deduceFormat(BufferedInputStream in) throws IOException {
        if (isSDF(in)){
            return ChemIOFormat.SDF;
        } else if (isCSV(in)){
            return ChemIOFormat.CSV;
        } else if (isJSON(in)){
            return ChemIOFormat.JSON;
        }
        return ChemIOFormat.UNKNOWN;
    }

    private static Pattern SDF_V2000 = Pattern.compile("\\s+[0-9\\s]{10,}V2000\\s+");
    private static Pattern SDF_V3000 = Pattern.compile("\\s+[0-9\\s]{10,}V3000\\s+"); //"\\v[0-9\s]{10,}V3000\\v"
    
    public static boolean isSDF(BufferedInputStream in) throws IOException {
        
        try {
            in.mark(READ_LIMIT_SDF_JSON_BYTES);
            byte[] buffer = new byte[READ_LIMIT_SDF_JSON_BYTES];
            int numRead = in.read(buffer);
            if (numRead != -1){
                String text = new String(buffer, StandardCharsets.UTF_8);
                // Check v2000
                Matcher v2000 = SDF_V2000.matcher(text);
                if (v2000.find())
                	return true;
                // Check v3000
                Matcher v3000 = SDF_V3000.matcher(text);
                if (v3000.find())
                	return true;
            }
            
        } finally {
            // reset stream
            try {
                in.reset();
            } catch (Exception e){

            }
        }
        return false;

    }
    
    public static boolean isCSV(BufferedInputStream in) throws IOException {
        try{
            deduceDelimiter(in);
            return true;
        } catch (IOException e){
            return false;
        }
    }

    public static char deduceDelimiter(URI in) throws IOException {
        try(BufferedInputStream stream = new BufferedInputStream(in.toURL().openStream())){
            return deduceDelimiter(stream);
        }
    }
    public static char deduceDelimiter(BufferedInputStream in) throws IOException {
        
        char[] commonDelimiters = new char[]{',',';','\t', ' ', '|'};

        try {
            in.mark(READ_LIMIT_CSV_BYTES);
            byte[] buffer = new byte[READ_LIMIT_CSV_BYTES];
            int numRead = in.read(buffer);
            if (numRead != -1){
            	// Convert into a string
                String text = new String(buffer, StandardCharsets.UTF_8);
                
                // Check the most common delimiters
                for (char d : commonDelimiters){
                	
                	CSVFormat fmt = CSVFormat.DEFAULT.builder().setDelimiter(d).build();
                	
                    try (
                        StringReader stringReader = new StringReader(text);
                        BufferedReader reader = new BufferedReader(stringReader);
                    	CSVParser p = fmt.parse(stringReader);
                    ){
                    	Iterator<CSVRecord> iter = p.iterator();
                    	List<Integer> numSplits = new ArrayList<>();
                        int i = 0;
                    	// We do not have a complete CSV file, so it may end with a failing record in the end
                    	try {
                    		
                    		while (iter.hasNext() & i<10) {
                    			CSVRecord r = iter.next();
                    			numSplits.add(r.size());
                                i++;
                    		}
                    		
                    	} catch (Exception e) {
                    		// This is expected, 
                    	}
                        
                        if (i<2) {
                        	// CSV cannot be 1 line only
                        	continue;
                        }
                        Map<Integer,Integer> counts = CollectionUtils.countFrequencies(numSplits);
                        for (Map.Entry<Integer,Integer> kv : counts.entrySet()){
                            if (kv.getKey()>1){
                                // If splitted this line more than once (i.e. at least 2 columns)
                                if (kv.getValue()>= (2./3)*i){
                                    // Splitted the lines in the same way in at least 2/3 of the checked rows
                                	
                                    return d;
                                }
                                    
                            }

                        }
                    } 
                }
                
            }
            
        } finally {
            // reset stream
            try {
                in.reset();
            } catch (Exception e){

            }
        }
        throw new IOException("No commonly used delimiter works for input data in CSV");
    }
    
    /**
     * Regular expression matching {@code [{"key":} part of a JSON array, where "key" is any non-vertical
     * blank character   
     */
    private final static Pattern JSON_ARRAY_REGEX = Pattern.compile("^\\[\\{\"\\V{1,}\":");


    public static boolean isJSON(BufferedInputStream in) throws IOException {

        try {
            in.mark(READ_LIMIT_SDF_JSON_BYTES);
            byte[] buffer = new byte[READ_LIMIT_SDF_JSON_BYTES];
            int numRead = in.read(buffer);
            if (numRead != -1){
                String text = new String(buffer, StandardCharsets.UTF_8).replaceAll("\\s+", "");
                return JSON_ARRAY_REGEX.matcher(text).find();
            }
            
        } finally {
            // reset stream
            try {
                in.reset();
            } catch (Exception e){

            }
        }
        return false;
    }
}

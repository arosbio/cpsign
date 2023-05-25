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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.tcp.TCPClassifier;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.vap.avap.AVAPClassifier;

/**
 * Singleton class to handle global settings
 * @author staffan
 *
 */
public class GlobalConfig {
	
	/**
	 * The default maximum number of classes, used for checking if a 
	 * problem is for classification or regression
	 */
	public static final int DEFAULT_MAX_NUM_CLASSES = 10;
	
	private static GlobalConfig instance;
	
	private long RNG_seed;
	private int maxNumClasses=10;
	private boolean memSave = false;
	
	private GlobalConfig() {
		RNG_seed = System.currentTimeMillis();
	}
	
	public static GlobalConfig getInstance() {
		if (instance == null)
			instance = new GlobalConfig();
		return instance;
	}
	
	public long getRNGSeed() {
		return RNG_seed;
	}

	public void setRNGSeed(long seed) {
		this.RNG_seed = seed;
	}
	
	public int getMaxNumClasses() {
		return maxNumClasses;
	}
	
	public void setMaxNumClasses(int max) {
		this.maxNumClasses = max;
	}
	
	public boolean isMemSaveMode() {
		return memSave;
	}
	
	public void setMemSaveMode(boolean memSave) {
		this.memSave = memSave;
	}

	public static class Defaults {
		
		public static enum PredictorType {
			ACP_CLASSIFICATION	(1, "ACP_Classification", "Aggegated conformal classifier (ACP)"),
			ACP_REGRESSION		(2, "ACP_Regression", "Aggregated conformal regressor (ACP)"),
			TCP_CLASSIFICATION	(3, "TCP_Classification", "Transductive conformal classifier (TCP)"),
			TCP_REGRESSION		(4, "TCP_Regression","TCP regression - doesn't exist"),
			VAP_CLASSIFICATION	(5, "VAP_Classification", "Venn-ABERS predictor (VAP)"),
			VAP_REGRESSION		(6, "VAP_Regression", "Venn-ABERS regression predictor (VAP)");
	
			private final int id;
			private final String name;
			private final String fancyName;
			// Constructor
			private PredictorType(int id, String name,String fancyName){
				this.id = id;
				this.name = name;
				this.fancyName=fancyName;
			}
	
			public int getId(){
				return id;
			}
	
			public String getName(){
				return name;
			}
			public String getFancyName() {
				return fancyName;
			}
	
			public boolean isClassification(){
				return this==ACP_CLASSIFICATION || this==TCP_CLASSIFICATION || this==VAP_CLASSIFICATION;
			}
	
			public boolean isTCP(){
				return this==TCP_CLASSIFICATION || this==TCP_REGRESSION;
			}
	
			public boolean isACP(){
				return this==ACP_REGRESSION || this==ACP_CLASSIFICATION;
			}
			public boolean isAVAP(){
				return this==VAP_CLASSIFICATION || this==VAP_REGRESSION;
			}
			public boolean isAggregatedPredictor() {
				return isAVAP() || isACP();
			}
	
			public static PredictorType getPredictorType(int id){
				for (PredictorType e : values()) {
					if (e.id==id) return e;
				}
				throw new IllegalArgumentException("PredictorType {" + id + "} not supported");
			}
	
			public static PredictorType getPredictorType(String text) throws IllegalArgumentException {
				try{
					return getPredictorType(Integer.parseInt(text));
				} catch (NumberFormatException e){
					// was not a id 
				}
	
				List<Pair<List<String>,PredictorType>> values = new ArrayList<>();
				for (PredictorType s : values()) {
					values.add(ImmutablePair.of(Arrays.asList(s.name), s));
				}
				return new FuzzyMatcher().matchPairs(values, text);
			}
			
			public static PredictorType getPredictorType(Predictor predictor) throws IllegalArgumentException {
				if (predictor instanceof ACPClassifier)
					return ACP_CLASSIFICATION;
				else if (predictor instanceof TCPClassifier)
					return TCP_CLASSIFICATION;
				else if (predictor instanceof ACPRegressor)
					return ACP_REGRESSION;
				else if (predictor instanceof AVAPClassifier)
					return VAP_CLASSIFICATION;
	
				throw new IllegalArgumentException("No predictor type for Predictor of class: " + predictor.toString());
							
			}
	
			@Override
			public String toString(){
				return ""+id;
			}
	
			public final String getAsOption(){
				return String.format("(%s) %s", id,name); 
			}
		}
	
	}
}

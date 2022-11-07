/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app.params;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.color.gradient.ColorGradient;
import com.arosbio.color.gradient.GradientFactory;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.FuzzyMatcher;
import com.arosbio.commons.Version;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.tcp.TCPClassifier;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.vap.avap.AVAPClassifier;

public class CLIParameters {

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
			return new FuzzyMatcher().match(values, text);
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

			throw new IllegalArgumentException("No predictor type for CompletePredictor of class: " + predictor.toString());
						
		}

		@Override
		public String toString(){
			return ""+id;
		}

		public final String getAsOption(){
			return String.format("(%s) %s", id,name); 
		}
	}


	public enum ClassOrRegType {
		CLASSIFICATION	(1, "classification"),
		REGRESSION		(2, "regression");

		private final int id;
		private final String name;
		private ClassOrRegType(int id, String name){
			this.id=id;
			this.name=name;
		}

		@Override
		public String toString(){
			return ""+id;
		}

		public static ClassOrRegType getType(int id){
			for (ClassOrRegType e : values()) {
				if(e.id==id) return e;
			}
			throw new IllegalArgumentException("ClassOrRegType {" + id + "} not supported");
		}

		public static ClassOrRegType getType(String text) throws IllegalArgumentException {
			try {
				return getType(Integer.parseInt(text));
			} catch(NumberFormatException e){
				// was not a id 
			}

			List<Pair<List<String>,ClassOrRegType>> values = new ArrayList<>();
			for (ClassOrRegType s : values()) {
				values.add(ImmutablePair.of(Arrays.asList(s.name), s));
			}
			return new FuzzyMatcher().match(values, text);
		}
	}

	public enum TextOutputType {
		JSON			(1, "json"),
		TEXT			(2, "text", "plain", "default"),
		CSV				(3, "csv"),
		TSV				(4, "tsv");

		private final int id;
		private final String[] names;
		private TextOutputType(int id, String... names){
			this.id=id;
			this.names=names;
		}

		@Override
		public String toString(){
			return ""+id;
		}

		public static TextOutputType getType(int id){
			for (TextOutputType e : values()) {
				if (e.id==id) return e;
			}
			throw new IllegalArgumentException("TextOutputType {" + id + "} not supported");
		}

		public static TextOutputType getType(String text) throws IllegalArgumentException {
			try{
				return getType(Integer.parseInt(text));
			} catch(NumberFormatException e){
				// was not a id 
			}

			List<Pair<List<String>,TextOutputType>> values = new ArrayList<>();
			for (TextOutputType s : values()) {
				values.add(ImmutablePair.of(Arrays.asList(s.names), s));
			}
			return new FuzzyMatcher().match(values, text);
		}
	}
	
	public enum ChemOutputType {
		JSON			(1, "json"),
		TSV				(2, "tsv"),
		SDF_V2000 		(3, "sdf", "sdf-v2000"),
		SDF_V3000 		(4, "sdf-v3000"),
		CSV				(5, "csv"),
		SPLITTED_JSON	(6, "splitted-json");

		private final int id;
		private final String[] names;
		private ChemOutputType(int id, String... names){
			this.id=id;
			this.names=names;
		}

		@Override
		public String toString(){
			return ""+id;
		}

		public static ChemOutputType getType(int id){
			for (ChemOutputType e : values()) {
				if (e.id==id) return e;
			}
			throw new IllegalArgumentException("ChemOutputType {" + id + "} not supported");
		}

		public static ChemOutputType getType(String text) throws IllegalArgumentException {
			try {
				return getType(Integer.parseInt(text));
			} catch (NumberFormatException e){
				// was not a id 
			}

			List<Pair<List<String>,ChemOutputType>> values = new ArrayList<>();
			for (ChemOutputType s : values()) {
				values.add(ImmutablePair.of(Arrays.asList(s.names), s));
			}
			return new FuzzyMatcher().match(values, text);

		}

		public boolean isDelimitedFormat() {
			if (this.id == TSV.id || this.id == CSV.id)
				return true;
			return false;
		}
	}
	
	public enum ColorScheme {
		BLUE_RED (1,"blue:red", "default"),
		RED_BLUE (2,"red:blue"),
		RED_BLUE_RED (3,"red:blue:red"),
		CYAN_MAGENTA (4,"cyan:magenta"),
		RAINBOW (5, "rainbow");

		private final int id;
		private final String[] names;

		private ColorScheme(int id, String text) {
			this.id = id;
			this.names = new String[] {text};
		}


		private ColorScheme(int id, String... text) {
			this.id = id;
			this.names = text;
		}

		public int getID() {
			return id;
		}

		public String getTextRep() {
			return names[0];
		}

		@Override
		public String toString(){
			return ""+id;
		}

		public static ColorScheme getType(int id){
			for (ColorScheme e : values()) {
				if (e.id==id) return e;
			}
			throw new IllegalArgumentException("ColorScheme {" + id + "} not supported");
		}

		public ColorGradient getGradient() {
			if (id == BLUE_RED.id)
				return GradientFactory.getDefaultBloomGradient();
			else if (id == RAINBOW.id)
				return GradientFactory.getRainbowGradient();
			else if (id == RED_BLUE_RED.id)
				return GradientFactory.getRedBlueRedGradient();
			else if (id == RED_BLUE.id)
				return GradientFactory.getRedBlueGradient();
			else if (id == CYAN_MAGENTA.id)
				return GradientFactory.getCyanMagenta();
			else 
				throw new RuntimeException("Gradient not found for ColorScheme " + id);
		}

	}
	public static final double DEFAULT_CONFIDENCE = 0.8;
	public static final List<Double> DEFAULT_EXPECTED_PROBS = CollectionUtils.listRange(0.05, 0.95,0.1);
	public static final int DEFAULT_SIGNATURE_START_HEIGHT = SignaturesDescriptor.DEFAULT_SIGN_START_HEIGHT;
	public static final int DEFAULT_SIGNATURE_END_HEIGHT = SignaturesDescriptor.DEFAULT_SIGN_END_HEIGHT;
	public static final int MAX_NUMBER_MOLS_FOR_PERCENTILES = 1000;
	public static final String DEFAULT_MODEL_ID = "MODEL_ID";
	public static final String DEFAULT_MODEL_NAME = "MODEL_NAME";
	public static final Version DEFAULT_MODEL_VERSION = Version.defaultVersion();

	// Aggregate model-types
	// public static final int PRECOMPUTED_CLASSIFICATION_MODELS = 1;
	// public static final int PRECOMPUTED_REGRESSION_MODELS = 2;
	// public static final int TRAINED_CLASSIFICATION_MODELS = 3;
	// public static final int TRAINED_REGRESSION_MODELS = 4;

	// Images
	public static final int DEFAULT_IMAGE_HEIGHT = 400; //px
	public static final int DEFAULT_IMAGE_WIDTH = 400; //px
	public static final String DEFAULT_COLORING_SCHEME="BLUE:RED";

	public static final String DEFAULT_MODEL_FILE_SUFFIX = ".jar";
	
}

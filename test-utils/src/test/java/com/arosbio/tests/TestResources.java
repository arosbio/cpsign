/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.tests;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.arosbio.tests.TestResources.CmpdData.Format;

public class TestResources {

    public static interface SVMLIGHTFiles {
		static final String NUMERICAL_DS_FOLDER = "/datasets/";
		static final String MULTICLASS_FOLDER = NUMERICAL_DS_FOLDER + "multiclass/";
		static final String BINARY_CLF_FOLDER = NUMERICAL_DS_FOLDER + "classification/";
		static final String REGRESSION_FOLDER = NUMERICAL_DS_FOLDER + "regression/";
		static final String GENERATED_DATA_FOLDER = NUMERICAL_DS_FOLDER + "generated/";

		// BINARY CLASSIFICATION 
		/** 4601 instances, 57 features, labels 1.0=1813, 0.0=2788 */
		public static final URL CLASSIFICATION_2CLASS = getURL(BINARY_CLF_FOLDER+"spambase.svmlight");
		/** 20 instances, 57 features, labels 1.0=9, 0.0=11 */
		public static final URL CLASSIFICATION_2CLASS_20 = getURL(BINARY_CLF_FOLDER+"spambase_20.svmlight");
		/** 100 instances, 57 features, labels 1.0=46, 0.0=54 */
		public static final URL CLASSIFICATION_2CLASS_100 = getURL(BINARY_CLF_FOLDER+"spambase_100.svmlight");
		/** 4601 instances, 57 features, labels 1.0=1813, -1.0=2788, same as {@link #CLASSIFICATION_2CLASS_PATH} but with other labels */
		public static final URL CLASSIFICATION_2CLASS_NEG_POS = getURL(BINARY_CLF_FOLDER+"spambase_neg_pos.svmlight");
		/** 100 instances, 57 features, labels 1.0=45, 0.0=55 */
		public static final URL CLASSIFICATION_2CLASS_TRAIN = getURL(BINARY_CLF_FOLDER+"train.svmlight");
		/** 2000 instances, 180 features, labels -1.0=1000, 1.0=1000 */
		public static final URL CLASSIFICATION_2CLASS_MADELON = getURL(BINARY_CLF_FOLDER+"madelon.train.svmlight.gz");

		// MULTICLASS CLASSIFICATION 
		/** 2000 instances, 500 features, labels 1.0=464, 2.0=485, 3.0=1051*/
		public static final URL CLASSIFICATION_3CLASS = getURL(MULTICLASS_FOLDER+"dna.3_class.scaled.svmlight.gz");
		/** 67557 instances, 126 features, labels 1.0=44473, 0.0=6449, -1.0=16635 */
		public static final URL CLASSIFICATION_3CLASS_CON4 = getURL(MULTICLASS_FOLDER+"connect-4.svmlight");
		/** 581012 instances with 54 features, labels 1.0=44473, 0.0=6449, -1.0=16635 */
		public static final URL CLASSIFICATION_7CLASS_LARGE = getURL(MULTICLASS_FOLDER+"covtype.scaled01.svmlight.gz");
		

		//  REGRESSION
		/** 506 instances with continous labels (regression) of 13 features */
		public static final URL REGRESSION_HOUSING = getURL(REGRESSION_FOLDER+"housing.scaled.svmlight");
		/** The 25 first instances from {@link #REGRESSION_HOUSING} */
		public static final URL REGRESSION_HOUSING_25 = getURL(REGRESSION_FOLDER+"housing_25.scaled.svmlight");
		/** (largest data set) 700 records with 237 features */
		public static final URL REGRESSION_ENRICHMENT = getURL(REGRESSION_FOLDER+"enrich.svmlight");
		/** 688 instances with 97 features */
		public static final URL REGRESSION_ANDROGEN = getURL(REGRESSION_FOLDER+"androgen.svmlight");
		/** 1040 instances, 305 features, based on chemical structure (with dense features) */
		public static final URL REGRESSION_GLUCC = getURL(REGRESSION_FOLDER+"glucocorticoid.svmlight");

		// GENERATED FILES
		public static final URL GENERATED_2FEAT_TEST_1K = getURL(GENERATED_DATA_FOLDER+"testFictTwoFeatures1000.txt");
		public static final URL GENERATED_2FEAT_TRAIN_400 = getURL(GENERATED_DATA_FOLDER+"trainFictTwoFeatures400.txt");
		public static final URL GENERATED_2FEAT_TRAIN_4K = getURL(GENERATED_DATA_FOLDER+"trainFictTwoFeatures4000.txt");
		public static final URL GENERATED_2FEAT_TRAIN_20K = getURL(GENERATED_DATA_FOLDER+"trainFictTwoFeatures20000.txt");
	}

	public static class CmpdData {
		private static final String CHEM_FOLDER = "/chem/";
		public static final String CLF_FOLDER = CHEM_FOLDER + "classification/";
		public static final String REG_FOLDER = CHEM_FOLDER + "regression/";
		public static final String MULTI_CLS_FOLDER = CHEM_FOLDER + "multiclass/";

		public static enum Format {
			CSV("csv"), SDF("sdf"), JSON("json");

			public String fmt;
			private Format(String fmt){
				this.fmt = fmt;
			}
		}

		final URL url;
		final Format fmt;
		final String property;
		final List<?> labels;
		final boolean isCompressed;
		final int numValidRecords;
		final int numInvalidRecords;
		final boolean isRegression;
		final boolean isClassification;

		public static class Builder<T extends Builder<T>> {

			URL url;
			Format fmt;
			String property;
			List<?> labels = null;
			boolean isCompressed = false;
			int numValidRecords;
			int numInvalidRecords;
			final boolean isRegression;
			final boolean isClassification;

			@SuppressWarnings("unchecked")
			private T getThis(){
				return (T) this;
			}

			private Builder(URL url, Format format, String property, int nValid, int nInvalid, List<?> labels, boolean isClassification, boolean isRegression){
				this.url = url;
				this.fmt = format;
				this.property = property;
				this.labels = labels;
				this.numValidRecords = nValid;
				this.numInvalidRecords = nInvalid;
				this.isClassification = isClassification;
				this.isRegression = isRegression;
			}

			public static Builder<?> classification(URL url, Format format, String property, int nValid, int nInvalid, List<?> labels){
				return new Builder<>(url, format, property, nValid, nInvalid, labels, true, false);
			}

			public static Builder<?> regression(URL url, Format format, String property, int nValid, int nInvalid){
				return new Builder<>(url, format, property, nValid, nInvalid, null, false, true);
			}

			public T zipped(boolean isCompressed){
				this.isCompressed = isCompressed;
				return getThis();
			}

			public CmpdData build(){
				return new CmpdData(this);
			}
		}

		private CmpdData(Builder<?> b){
            this.url = b.url;
			this.fmt = b.fmt;
			this.property = b.property;
			this.numValidRecords = b.numValidRecords;
			this.numInvalidRecords = b.numInvalidRecords;
			this.labels = b.labels;
			this.isRegression = b.isRegression;
			this.isClassification = b.isClassification;
			this.isCompressed = b.isCompressed;
		}

		public boolean isZipped(){
			return isCompressed;
		}

		public URL url(){
			return url;
		}

		public URI uri() throws IOException {
			try {
				return url.toURI();
			} catch (URISyntaxException e){
				throw new IOException(e);
			}
		}

		public String format(){
			return fmt.fmt;
		}

		public Format getFormat(){
			return fmt;
		}


		public String property(){
			return property;
		}

		/** Return the labels, might be {@code null} (regression data sets) 
		 * 
		 */
		public List<?> labels(){
			return labels;
		}

		public List<String> labelsStr(){
			if (labels == null)
				return null;
			return labels.stream().map(Object::toString).collect(Collectors.toList());
		}

		public boolean isClassification(){
			return isClassification;
		}
		public boolean isRegression(){
			return isRegression;
		}

		public int numValidRecords(){
			return numValidRecords;
		}

		public int numInvalidRecords(){
			return numInvalidRecords;
		}

		public String toString(){
			return String.format("%s: [%s], reg=%b, clf=%b", url.getFile(),fmt.fmt, isRegression,isClassification);
		}

	}

	public static class CSVCmpdData extends CmpdData {

		final char delim;

		public static class Builder extends CmpdData.Builder<Builder> {

			private char delim;

			private Builder getThis(){
				return this;
			}

			private Builder(URL url, String property, int nValid, int nInvalid, List<?> labels, boolean isClassification, boolean isRegression){
				super(url,Format.CSV, property, nValid, nInvalid, labels, isClassification, isRegression);
			}

			public static Builder classification(URL url, String property, int nValid, int nInvalid, List<?> labels){
				return new Builder(url, property, nValid, nInvalid, labels, true, false);
			}

			public static Builder regression(URL url, String property, int nValid, int nInvalid){
				return new Builder(url, property, nValid, nInvalid, null, false, true);
			}

			public Builder delim(char delim){
				this.delim = delim;
				return getThis();
			}

			public CSVCmpdData build(){
				return new CSVCmpdData(this);
			}

		}

		public char delim(){
			return delim;
		}

		private CSVCmpdData(Builder b){
			super(b);
			delim = b.delim;
		}
	}

	

	public static interface Cls {

		public static final String PROPERTY = "Ames test categorisation";
		public static final List<String> AMES_LABELS = Arrays.asList("mutagen", "nonmutagen");

		public static CmpdData getAMES_10(){
			return CmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"ames_10.sdf"), Format.SDF, PROPERTY, 10, 0, AMES_LABELS)
			.build();
		}
		public static CmpdData getAMES_10_json(){
			return CmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"ames_10.json"), Format.JSON, PROPERTY,10,0, AMES_LABELS)
			.build();
		}
		public static CmpdData getAMES_10_json_bool(){
			return CmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"ames_10_boolean.json"), Format.JSON, PROPERTY, 10, 0, Arrays.asList(true,false)).build();
		}
		public static CmpdData getAMES_10_gzip(){
			return CmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"ames_10.sdf.gz"), Format.SDF, PROPERTY, 10, 0, AMES_LABELS)
				.zipped(true).build();
		}
		public static CmpdData getAMES_126(){
			return CmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"ames_126.sdf"), Format.SDF, PROPERTY,123, 3, AMES_LABELS)
				.build();
		}
		public static CSVCmpdData getAMES_126_chem_desc_no_header(){
			return CSVCmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"ames_126_36_cdk_descriptors.no-header.csv"), PROPERTY,123, 3, AMES_LABELS)
				.delim(',').build();
		}
		/** Header line that corresponds to {@link #getAMES_126_chem_desc_no_header()} */
		public static final String AMES_126_chem_header_line = "Smiles,cdk_Title,\"Molecular Signature\",\"Ames test categorisation\",bpol,nRings3,nRings4,ALogp2,nAromBlocks,nA,nC,ALogP,nD,nE,nF,nG,nH,nI,nAromRings,nK,nL,nM,nN,nSmallRings,nRingBlocks,nP,nQ,nR,nS,nT,AMR,nV,nW,MW,nY,nRings9,nRings7,nRings8,nRings5,nRings6";

		public static CmpdData getAMES_126_gzip(){
			return CmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"ames_126.sdf.gz"), Format.SDF, PROPERTY, 123, 3, AMES_LABELS)
				.zipped(true).build();
		}
		public static CSVCmpdData getAMES_126_chem_desc(){
			return CSVCmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"ames_126_36_cdk_descriptors.csv"), PROPERTY,123, 3, AMES_LABELS)
				.delim(',').build();
		}
		public static CmpdData getAMES_1337(){
			return CmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"ames_1337.sdf"), Format.SDF, PROPERTY, 1314, 23, AMES_LABELS)
			.build();
		}
		public static CmpdData getAMES_2497(){
			return CmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"ames_2497.sdf"), Format.SDF, PROPERTY, 2947, 53, AMES_LABELS)
				.build();
		}
		public static CmpdData getAMES_4337(){
			return CmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"ames_4337.sdf"), Format.SDF, PROPERTY, 4261, 76, AMES_LABELS)
			.build();
		}

		public static CSVCmpdData getBBB(){
			return CSVCmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"BBB.csv"), "Blood-Brain-Barrier Penetration", 407, 8, Arrays.asList("non-penetrating","penetrating"))
				.delim(',').build();
		}

		public static CSVCmpdData getCAS_N6512(){
			return CSVCmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"cas_N6512.csv"), "class", 6399, 113, Arrays.asList(0,1))
				.delim('\t').build();
		}
		
		public static CSVCmpdData getCox2(){
			return CSVCmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"cox2.csv"), "class", 322, 0, Arrays.asList(-1,1))
				.delim('\t').build();
		}

		public static CSVCmpdData getCPD(){

			return CSVCmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"CPD.csv"), "class", 8, 2, Arrays.asList(0,1))
				.delim('\t').build();
		}

		public static CSVCmpdData getDHFR(){
			return CSVCmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"dhfr.csv"), "class", 397, 0, Arrays.asList(-1,1))
				.delim('\t').build();
		}

		public static CSVCmpdData getEPAFHM(){
			return CSVCmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"EPAFHM.csv"), "class", 553, 24, Arrays.asList(-1,1))
				.delim('\t').build();
		}

		public static CSVCmpdData getFDA(){
			return CSVCmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"FDA.csv"), "class", 1212, 4, Arrays.asList(-1,1))
				.delim('\t').build();
		}

		public static CSVCmpdData getScreen_U251(){
			return CSVCmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"screen_U251.csv"), "class", 3733, 10, Arrays.asList(-1,1))
				.delim('\t').build();
		}

		public static CmpdData getSingleSDF(){
			return CSVCmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"testmol.sdf"), Format.SDF, PROPERTY, 1, 0, AMES_LABELS)
				.build();
		}

		/**
		 * Contains the AMES 10 data set but with some failed MOL blocks,
		 * though the SDF reader cannot give those errors back - only set to skip or fail on
		 * these encounters. 
		 * @return a dataset with 7 valid records and 3 invalid ones
		 */
		public static CmpdData getAmes10WithInvalidRecords(){
			return CmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"ames_10.invalid.sdf"), Format.SDF, PROPERTY, 7, 3, AMES_LABELS)
				.build();
		}

		/**
		 * 2 invalid records in total; one row with non-valid smiles (and no property), and one smiles with missing property.
		 * @return
		 */
		public static CSVCmpdData getErroneous(){
			return CSVCmpdData.Builder.classification(getURL(CmpdData.CLF_FOLDER+"missing_activities.csv"), "solubility(fake!)", 17, 2, Arrays.asList("POS","NEG"))
				.delim('\t').build();
		}

		public static CmpdData getHERG(){
			return CmpdData.Builder.classification(getURL(CmpdData.CHEM_FOLDER+"hERG@PKKB-reg-class.sdf.gz"), Format.SDF, "class", 806, 0,Arrays.asList(0,1))
				.zipped(true).build();
		}

		public static CSVCmpdData getContradict_labels(){
			return CSVCmpdData.Builder.classification(getURL(CmpdData.CHEM_FOLDER+"fake_labels_reg-class.csv"), "solubility_class", 13, 0, Arrays.asList(-1,1))
				.delim('\t').build();
		}

	}

	public static interface MultiCls {

		/**
		 * LTKB three classes. dataset from the OpenRiskNet work - about 980 compounds in total
		 * @return
		 */
		public static CSVCmpdData getLTKB() {
			return CSVCmpdData.Builder.classification(getURL(CmpdData.MULTI_CLS_FOLDER+"ltkb.csv"),"DILIConcern", 982, 2,Arrays.asList("NO-DILI-CONCERN","LESS-DILI-CONCERN","MOST-DILI-CONCERN"))
				.delim(',').build();
		}

	}
	public static interface Reg {

		public static final String CHANG_PROPERTY = "BIO";

		/**
		 * 34 instances
		 * @return
		 */
		public static CmpdData getChang(){
			return CmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"chang.sdf"), Format.SDF, CHANG_PROPERTY, 34, 0)
				.build();
		}
		public static CmpdData getChang_gzip(){
			return CmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"chang.sdf.gz"), Format.SDF, CHANG_PROPERTY, 34, 0)
				.zipped(true).build();
		}
		public static CmpdData getChang_json(){
			return CmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"chang.json"), Format.JSON, CHANG_PROPERTY, 34, 0)
				.build();
		}
		/** Same as {@link #getChang_json()} but without any indentation and new lines, with the following failing records; 1 missing activity, 1 invalid activity and 1 invalid smiles */
		public static CmpdData getChang_json_no_indent(){
			return CmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"chang_single_line.json"), Format.JSON, CHANG_PROPERTY, 31, 3)
				.build();
		}
		public static CmpdData getGluc(){
			return CmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"glucocorticoid.sdf.gz"), Format.SDF, "target", 1124, 0)
				.zipped(true).build();
		}
		
		public static final String SOLUBILITY_PROPERTY = "solubility";
		/** 
		 * Two invalid smiles, one missing activity and one invalid activity (4 invalid records). 6 valid records.
		 * Saved using Excel and has a BOM in the beginning, which might screw up reading from CSV
		 */
		public static CSVCmpdData getErroneous(){
			return CSVCmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"invalid_smiles.csv"), SOLUBILITY_PROPERTY, 6, 4)
				.delim(';').build();
		}
		public static CSVCmpdData getSolubility_10_multicol(){
			return CSVCmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"solubility_10_multi_col.csv"), SOLUBILITY_PROPERTY, 10, 0)
				.delim('\t').build();
		}
		/**
		 * Contains a blank line and then 10 valid records
		 * @return
		 */
		public static CSVCmpdData getSolubility_10_no_header_multicol(){
			return CSVCmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"solubility_10_no_header_multi_col.csv"), SOLUBILITY_PROPERTY, 10, 0)
				.delim('\t').build();

		}
		public static CSVCmpdData getSolubility_10_no_header(){
			return CSVCmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"solubility_10_no_header.csv"), SOLUBILITY_PROPERTY, 10, 0)
				.delim('\t').build();
		}
		public static CSVCmpdData getSolubility_10(){
			return CSVCmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"solubility_10.csv"), SOLUBILITY_PROPERTY, 10, 0)
				.delim('\t').build();
		}
		public static CSVCmpdData getSolubility_10_gzip(){
			return CSVCmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"solubility_10.csv.gz"), SOLUBILITY_PROPERTY, 10, 0)
				.delim('\t').zipped(true).build();
		}
		/** Same as {@link #getSolubility_10()} but with semicolon, also other order of columns */
		public static CSVCmpdData getSolubility_10_excel(){
			return CSVCmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"solubility_10_semicolon.csv"), SOLUBILITY_PROPERTY, 10, 0)
				.delim(';').build();
		}
		public static CSVCmpdData getSolubility_100(){
			return CSVCmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"solubility_100.csv"), SOLUBILITY_PROPERTY, 100, 0)
				.delim('\t').build();
		}
		public static CSVCmpdData getSolubility_500(){
			return CSVCmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"solubility_500.csv"), SOLUBILITY_PROPERTY, 500, 0)
				.delim('\t').build();
		}
		public static CSVCmpdData getSolubility_1k(){
			return CSVCmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"solubility_1k.csv.gz"), SOLUBILITY_PROPERTY, 1000, 0)
				.delim('\t').zipped(true).build();
		}
		public static CSVCmpdData getSolubility_4k(){
			return CSVCmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"solubility_4k.csv.gz"), SOLUBILITY_PROPERTY, 4000, 0)
				.delim('\t').zipped(true).build();
		}
		public static CSVCmpdData getSolubility_5k(){
			return CSVCmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"solubility_5k.csv.gz"), SOLUBILITY_PROPERTY, 5000, 0)
				.delim('\t').zipped(true).build();
		}
		public static CSVCmpdData getSolubility_10k(){
			return CSVCmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"solubility_10k.csv.gz"), SOLUBILITY_PROPERTY, 9999, 1)
				.delim('\t').zipped(true).build();
		}
		/** Full solubility data set with 57857 valid compounds and 2 invalid records */
		public static CSVCmpdData getSolubility(){
			return CSVCmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"solubility_+57k.csv.gz"), SOLUBILITY_PROPERTY, 57857, 2)
				.delim('\t').zipped(true).build();
		}

		public static CmpdData getLogS_1210(){
			return CmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"solubility@PKKB_2009.sdf.gz"), Format.SDF, "LogS (exp)", 1210, 0)
				.zipped(true).build();
		}

		/**
		 * Contains 806 molecules in total, SDF format, with GZIP 
		 * @return SDF Data set
		 */
		public static CmpdData getHERG(){
			return CmpdData.Builder.regression(getURL(CmpdData.CHEM_FOLDER+"hERG@PKKB-reg-class.sdf.gz"), Format.SDF, "IC50", 474, 332)
				.zipped(true).build();
		}
		public static CSVCmpdData getToy_many_cols(){
			return CSVCmpdData.Builder.regression(getURL(CmpdData.REG_FOLDER+"toy_many_cols.csv"), "Response value", 3, 0)
				.delim(';').build();
		}

		public static CSVCmpdData getContradict_labels_and_outlier(){
			return CSVCmpdData.Builder.regression(getURL(CmpdData.CHEM_FOLDER+"fake_labels_reg-class.csv"), SOLUBILITY_PROPERTY, 13, 0)
				.delim('\t').build();
		}


	}

    private static URL getURL(String resourcePath){
        return TestResources.class.getResource(resourcePath);
    }
    
}

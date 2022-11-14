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

		URL url;
		Format fmt;
		String property;
		List<?> labels;
		boolean isCompressed=false;
		
		public CmpdData(URL url, Format format, String property){
			this.url = url;
			this.fmt = format;
			this.property = property;
		}

		public CmpdData(URL url, Format format, String property, List<?> labels){
			this(url, format, property);
			this.labels = labels;
		}

		public CmpdData withGzip(boolean isCompressed){
			this.isCompressed=isCompressed;
			return this;
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
			return labels.stream().map(Object::toString).collect(Collectors.toList());
		}

	}

	public static class CSVCmpdData extends CmpdData {

		char delim;

		public CSVCmpdData(URL url, String property, char delim){
			super(url, Format.CSV, property);
			this.delim = delim;
		}
		public CSVCmpdData(URL url, String property, char delim, List<?> labels){
			super(url, Format.CSV, property,labels);
			this.delim = delim;
		}
		public char delim(){
			return delim;
		}

		public CSVCmpdData withGzip(boolean isCompressed){
			this.isCompressed=isCompressed;
			return this;
		}
	}

	

	public static interface Cls {

		public static final String PROPERTY = "Ames test categorisation";
		public static final List<String> AMES_LABELS = Arrays.asList("mutagen", "nonmutagen");

		public static CmpdData getAMES_10(){
			return new CmpdData(getURL(CmpdData.CLF_FOLDER+"ames_10.sdf"), Format.SDF, PROPERTY,AMES_LABELS);
		}
		public static CmpdData getAMES_10_json(){
			return new CmpdData(getURL(CmpdData.CLF_FOLDER+"ames_10.json"), Format.JSON, PROPERTY, AMES_LABELS);
		}
		public static CmpdData getAMES_10_json_bool(){
			return new CmpdData(getURL(CmpdData.CLF_FOLDER+"ames_10_boolean.json"), Format.JSON, PROPERTY, Arrays.asList(true,false));
		}
		public static CmpdData getAMES_10_gzip(){
			return new CmpdData(getURL(CmpdData.CLF_FOLDER+"ames_10.sdf.gz"), Format.SDF, PROPERTY,AMES_LABELS).withGzip(true);
		}
		public static CmpdData getAMES_126(){
			return new CmpdData(getURL(CmpdData.CLF_FOLDER+"ames_126.sdf"), Format.SDF, PROPERTY,AMES_LABELS);
		}
		public static CmpdData getAMES_126_gzip(){
			return new CmpdData(getURL(CmpdData.CLF_FOLDER+"ames_126.sdf.gz"), Format.SDF, PROPERTY,AMES_LABELS).withGzip(true);
		}
		public static CSVCmpdData getAMES_126_chem_desc(){
			return new CSVCmpdData(getURL(CmpdData.CLF_FOLDER+"ames_126_36_cdk_descriptors.csv"), PROPERTY,',', AMES_LABELS);
		}
		public static CmpdData getAMES_1337(){
			return new CmpdData(getURL(CmpdData.CLF_FOLDER+"ames_1337.sdf"), Format.SDF, PROPERTY,AMES_LABELS);
		}
		public static CmpdData getAMES_2497(){
			return new CmpdData(getURL(CmpdData.CLF_FOLDER+"ames_2497.sdf"), Format.SDF, PROPERTY,AMES_LABELS);
		}
		public static CmpdData getAMES_4337(){
			return new CmpdData(getURL(CmpdData.CLF_FOLDER+"ames_4337.sdf"), Format.SDF, PROPERTY,AMES_LABELS);
		}

		public static CSVCmpdData getBBB(){
			return new CSVCmpdData(getURL(CmpdData.CLF_FOLDER+"BBB.csv"), "Blood-Brain-Barrier Penetration", ',', Arrays.asList("non-penetrating","penetrating"));
		}

		public static CSVCmpdData getCAS_N6512(){
			return new CSVCmpdData(getURL(CmpdData.CLF_FOLDER+"cas_N6512.csv"), "class", '\t', Arrays.asList(0,1));
		}
		
		public static CSVCmpdData getCox2(){
			return new CSVCmpdData(getURL(CmpdData.CLF_FOLDER+"cox2.csv"), "class", '\t', Arrays.asList(-1,1));
		}

		public static CSVCmpdData getCPD(){
			return new CSVCmpdData(getURL(CmpdData.CLF_FOLDER+"CPD.csv"), "class", '\t', Arrays.asList(0,1));
		}

		public static CSVCmpdData getDHFR(){
			return new CSVCmpdData(getURL(CmpdData.CLF_FOLDER+"dhfr.csv"), "class", '\t', Arrays.asList(-1,1));
		}

		public static CSVCmpdData getEPAFHM(){
			return new CSVCmpdData(getURL(CmpdData.CLF_FOLDER+"EPAFHM.csv"), "class", '\t', Arrays.asList(-1,1));
		}

		public static CSVCmpdData getFDA(){
			return new CSVCmpdData(getURL(CmpdData.CLF_FOLDER+"FDA.csv"), "class", '\t', Arrays.asList(-1,1));
		}

		public static CSVCmpdData getScreen_U251(){
			return new CSVCmpdData(getURL(CmpdData.CLF_FOLDER+"screen_U251.csv"), "class", '\t', Arrays.asList(-1,1));
		}

		public static CmpdData getSingleMOL(){
			return new CmpdData(getURL(CmpdData.CLF_FOLDER+"testmol.mol"), Format.SDF, PROPERTY, AMES_LABELS);
		}

		public static CSVCmpdData getErroneous(){
			return new CSVCmpdData(getURL(CmpdData.CLF_FOLDER+"missing_activities.csv"), "solubility(fake!)", '\t', Arrays.asList("POS","NEG"));
		}

		public static CmpdData getHERG(){
			return new CmpdData(getURL(CmpdData.CHEM_FOLDER+"hERG@PKKB-reg-class.sdf.gz"), Format.SDF, "class",Arrays.asList(0,1)).withGzip(true);
		}

		public static CSVCmpdData getContradict_labels(){
			return new CSVCmpdData(getURL(CmpdData.CHEM_FOLDER+"fake_labels_reg-class.csv"), "solubility_class",'\t',Arrays.asList(-1,1));
		}

	}

	public static interface MultiCls {

		/**
		 * LTKB three classes. dataset from the OpenRisknet work - about 980 compounds in total
		 * @return
		 */
		public static CSVCmpdData getLTKB() {
			return new CSVCmpdData(getURL(CmpdData.MULTI_CLS_FOLDER+"ltkb.csv"),"DILIConcern",',',Arrays.asList("NO-DILI-CONCERN","LESS-DILI-CONCERN","MOST-DILI-CONCERN"));
		}

	}
	public static interface Reg {

		public static final String CHANG_PROPERTY = "BIO";

		/**
		 * 34 instances
		 * @return
		 */
		public static CmpdData getChang(){
			return new CmpdData(getURL(CmpdData.REG_FOLDER+"chang.sdf"), Format.SDF, CHANG_PROPERTY);
		}
		public static CmpdData getChang_gzip(){
			return new CmpdData(getURL(CmpdData.REG_FOLDER+"chang.sdf.gz"), Format.SDF, CHANG_PROPERTY).withGzip(true);
		}
		public static CmpdData getChang_json(){
			return new CmpdData(getURL(CmpdData.REG_FOLDER+"chang.json"), Format.JSON, CHANG_PROPERTY);
		}
		/** Same as {@link #getChang_json()} but without any indentation and new lines, with the following failing records; 1 missing activity, 1 invalid activity and 1 invalid smiles */
		public static CmpdData getChang_json_no_indent(){
			return new CmpdData(getURL(CmpdData.REG_FOLDER+"chang_single_line.json"), Format.JSON, CHANG_PROPERTY);
		}
		public static CmpdData getGluc(){
			return new CmpdData(getURL(CmpdData.REG_FOLDER+"glucocorticoid.sdf.gz"), Format.SDF, "target").withGzip(true);
		}
		
		public static final String SOLUBILITY_PROPERTY = "solubility";
		/** 
		 * Two invalid smilies, one missing activity and one invalid activity (4 invalid records). 6 valid records.
		 * Saved using Excel and has a BOM in the beginning, which might screw up reading from CSV
		 */
		public static CSVCmpdData getErroneous(){
			return new CSVCmpdData(getURL(CmpdData.REG_FOLDER+"invalid_smiles.csv"), SOLUBILITY_PROPERTY,';');
		}
		public static CSVCmpdData getSolubility_10_multicol(){
			return new CSVCmpdData(getURL(CmpdData.REG_FOLDER+"solubility_10_multi_col.csv"), SOLUBILITY_PROPERTY,'\t');
		}
		public static CSVCmpdData getSolubility_10_no_header_multicol(){
			return new CSVCmpdData(getURL(CmpdData.REG_FOLDER+"solubility_10_no_header_multi_col.csv"), SOLUBILITY_PROPERTY,'\t');
		}
		public static CSVCmpdData getSolubility_10_no_header(){
			return new CSVCmpdData(getURL(CmpdData.REG_FOLDER+"solubility_10_no_header.csv"), SOLUBILITY_PROPERTY,'\t');
		}
		public static CSVCmpdData getSolubility_10(){
			return new CSVCmpdData(getURL(CmpdData.REG_FOLDER+"solubility_10.csv"), SOLUBILITY_PROPERTY,'\t');
		}
		public static CSVCmpdData getSolubility_10_gzip(){
			return new CSVCmpdData(getURL(CmpdData.REG_FOLDER+"solubility_10.csv.gz"), SOLUBILITY_PROPERTY,'\t').withGzip(true);
		}
		/** Same as {@link #getSolubility_10()} but with semicolon, also other order of columns */
		public static CSVCmpdData getSolubility_10_excel(){
			return new CSVCmpdData(getURL(CmpdData.REG_FOLDER+"solubility_10.csv"), SOLUBILITY_PROPERTY,';');
		}
		public static CSVCmpdData getSolubility_100(){
			return new CSVCmpdData(getURL(CmpdData.REG_FOLDER+"solubility_100.csv"), SOLUBILITY_PROPERTY,'\t');
		}
		public static CSVCmpdData getSolubility_500(){
			return new CSVCmpdData(getURL(CmpdData.REG_FOLDER+"solubility_500.csv"), SOLUBILITY_PROPERTY,'\t');
		}
		public static CSVCmpdData getSolubility_1k(){
			return new CSVCmpdData(getURL(CmpdData.REG_FOLDER+"solubility_1k.csv.gz"), SOLUBILITY_PROPERTY,'\t').withGzip(true);
		}
		public static CSVCmpdData getSolubility_4k(){
			return new CSVCmpdData(getURL(CmpdData.REG_FOLDER+"solubility_4k.csv.gz"), SOLUBILITY_PROPERTY,'\t').withGzip(true);
		}
		public static CSVCmpdData getSolubility_5k(){
			return new CSVCmpdData(getURL(CmpdData.REG_FOLDER+"solubility_5k.csv.gz"), SOLUBILITY_PROPERTY,'\t').withGzip(true);
		}
		public static CSVCmpdData getSolubility_10k(){
			return new CSVCmpdData(getURL(CmpdData.REG_FOLDER+"solubility_10k.csv.gz"), SOLUBILITY_PROPERTY,'\t').withGzip(true);
		}
		/** Full solubility data set with roughly 57.8k compounds */
		public static CSVCmpdData getSolubility(){
			return new CSVCmpdData(getURL(CmpdData.REG_FOLDER+"solubility_+57k.csv.gz"), SOLUBILITY_PROPERTY,'\t').withGzip(true);
		}

		public static CmpdData getLogS_1210(){
			return new CmpdData(getURL(CmpdData.REG_FOLDER+"solubility@PKKB_2009.sdf.gz"), Format.SDF, "LogS (exp)").withGzip(true);
		}

		public static CmpdData getHERG(){
			return new CmpdData(getURL(CmpdData.CHEM_FOLDER+"hERG@PKKB-reg-class.sdf.gz"), Format.SDF, "IC50").withGzip(true);
		}
		public static CSVCmpdData getToy_many_cols(){
			return new CSVCmpdData(getURL(CmpdData.REG_FOLDER+"toy_many_cols.csv"), "Response value",';');
		}

		public static CSVCmpdData getContradict_labels_and_outlier(){
			return new CSVCmpdData(getURL(CmpdData.CHEM_FOLDER+"fake_labels_reg-class.csv"), SOLUBILITY_PROPERTY,'\t');
		}


	}

    private static URL getURL(String resourcePath){
        return TestResources.class.getResource(resourcePath);
    }
    
}

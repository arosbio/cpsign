/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.io.impl;

/**
 * Holds the information of how the ConfAI and CPSign property files are structured. The property file is divided into three sub-sections:
 * <ul> 
 * <li>{@link TestSection}: contains info about the specifics of the model/data generation - 
 * 	e.g. build time, cpsign version, and a lot of legacy information written to a test.xml file which is no longer used (intended for Bioclipse)
 * <li>{@link ParameterSection}: contains info about parameters used by the model or precomputed dataset, 
 * 	such as predictor type (ACP/TCP/VAP), underlying scorer implementation etc. Not used when loading the model, 
 * 	but servers as backup and additional meta-data for the saved model.
 * <li>{@link ResourceSection}: Aids in locating data in the serialized model. In case any updates are made to the internal structure of the jar.
 * </ul>
 * <p>
 * Each section has a String {@code NESTING_KEY} which is the top-level nesting for each section, which can be used to fetching the appropriate 
 * section at loading time.
 */
public class PropertyFileStructure {

	public static class InfoSection {
		public final static String NESTING_KEY = "info";

		public final static String MODEL_NAME_KEY = "modelName";
		public final static String MODEL_VERSION_KEY = "modelVersion";
		public final static String MODEL_CATEGORY_KEY = "modelCategory";
		/** Type corresponding to {@link com.arosbio.ml.io.ModelIO.ModelType} */
		public static final String MODEL_TYPE_KEY = "modelType";

		public final static String BUILD_TS_KEY = "buildTimestamp";
		/** Version of the software used for building the model */
		public final static String BUILD_SW_VERSION_KEY = "cpsignVersion";

		public String toString(){
			return NESTING_KEY;
		}

	}

	public static class TestSection {
		public final static String NESTING_KEY = "test";

		public String toString(){
			return NESTING_KEY;
		}

		// -------------- TEST_SECTION ----------------
		public final static String TEST_ID_KEY = "id";
		public final static String TEST_NAME_KEY = "name";
		public final static String TEST_ENDPOINT_KEY = "endpoint";
		public final static String TEST_CLASS_RUNNER_KEY = "modelRunner";
		public final static String TEST_CPSIGN_BUILD_VERSION_KEY = "cpsignBuildVersion";
		public final static String TEST_BUILD_TIME_KEY = "cpsignBuildTimestamp";
		public final static String TEST_PROPERTYCALCULATOR_KEY="propertycalculator";
		public final static String TEST_HELP_PAGE_KEY = "helppage";
		/** Not used any more */
		@Deprecated public final static String TEST_VALUE_TCP_CLASSIFICATION_RUNNER = "com.arosbio.modeling.ds.TCPClassification";
		/** Not used any more */
		@Deprecated public final static String TEST_VALUE_ACP_CLASSIFICATION_RUNNER = "com.arosbio.modeling.ds.ACPClassification";
		/** Not used any more */
		@Deprecated public final static String TEST_VALUE_ACP_REGRESSION_RUNNER = "com.arosbio.modeling.ds.ACPRegression";
		/** Not used any more */
		@Deprecated public final static String TEST_VALUE_CVAP_CLASSIFICATION_RUNNER = "com.arosbio.modeling.ds.CVAPClassification";
		/** Not used any more */
		@Deprecated public final static String TEST_VALUE_CVAP_REGRESSION_RUNNER = "com.arosbio.modeling.ds.CVAPRegression";
		/** Not used any more */
		@Deprecated public final static String TEST_VALUE_SPARSE_RUNNER = "";
	}

	public static class ParameterSection {
		public static final String NESTING_KEY = "parameters";

		public String toString(){
			return NESTING_KEY;
		}
	}

	public static class ResourceSection {
		public static final String NESTING_KEY = "resources";

		public String toString(){
			return NESTING_KEY;
		}
		
		/**
		 * Data directory, where e.g. transformations and descriptors should be saved
		 */
		public final static String DATA_DIR = "dataDir";
		/**
		 * The legacy property-key for where descriptors were saved
		 */
		public final static String LEGACY_DATA_LOC = "signaturesBaseDir";
		/**
		 * Base directory for where predictors + models are saved
		 */
		public final static String MODELS_DIR = "modelsBaseDir";

		public final static String CUSTOM_MOUNTED_LIST_KEY = "mountedLocations";
	}

}
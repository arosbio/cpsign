/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.testutils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.commons.lang3.tuple.Pair;

import com.arosbio.chem.io.in.CSVFile;
import com.arosbio.chem.io.in.ChemFileIterator;
import com.arosbio.chem.io.in.JSONFile;
import com.arosbio.chem.io.in.SDFile;
import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.data.ChemDataset.DescriptorCalcInfo;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.NamedLabels;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.TestResources.CmpdData.Format;

public class TestChemDataLoader {
	
	private static TestChemDataLoader instance = new TestChemDataLoader();
	
	private ChemDataset LARGE_REGRESSION, LARGE_CLASSIFICATION, SMALL_REGRESSION, SMALL_CLASSIFICATION;
	
	private TestChemDataLoader() {}
	
	public static TestChemDataLoader getInstance() {
		return instance;
	}

	public ChemDataset getChemicalProblem(boolean classification, boolean small) throws Exception {
		// CLASSIFICATION
		if (classification) {
			if (small) {
				if (SMALL_CLASSIFICATION == null) {
					SMALL_CLASSIFICATION = new ChemDataset();
					SMALL_CLASSIFICATION.initializeDescriptors();
					CmpdData ames126 = TestResources.Cls.getAMES_126();
					SMALL_CLASSIFICATION.add(new SDFile(ames126.uri()).getIterator(), ames126.property(), new NamedLabels(TestResources.Cls.AMES_LABELS));
				}
				return SMALL_CLASSIFICATION.clone();
			} else {
				if (LARGE_CLASSIFICATION == null) {
					LARGE_CLASSIFICATION = new ChemDataset();
					LARGE_CLASSIFICATION.initializeDescriptors();
					CmpdData ames2497 = TestResources.Cls.getAMES_2497();
					LARGE_CLASSIFICATION.add(new SDFile(ames2497.uri()).getIterator(), ames2497.property(), new NamedLabels(TestResources.Cls.AMES_LABELS));
				}
				return LARGE_CLASSIFICATION.clone();
			}
		} 
		// REGRESSION
		if (small) {
			if (SMALL_REGRESSION == null) {
				SMALL_REGRESSION = new ChemDataset();
				SMALL_REGRESSION.initializeDescriptors();
				CSVCmpdData sol100 = TestResources.Reg.getSolubility_100();
				SMALL_REGRESSION.add(new CSVFile(sol100.uri()).getIterator(), sol100.property());
			}
			return SMALL_REGRESSION.clone();
		} else {
			if (LARGE_REGRESSION == null) {
				LARGE_REGRESSION = new ChemDataset();
				LARGE_REGRESSION.initializeDescriptors();
				CSVCmpdData sol5k = TestResources.Reg.getSolubility_5k();
				LARGE_REGRESSION.add(new CSVFile(sol5k.uri()).getIterator(), sol5k.property());
			}
			return LARGE_REGRESSION.clone();
		}
	}
	
	/**
	 * Loads a dataset and manages the open/closing of the URL connection
	 * @param resource A URL with a data set in SVMLight format
	 * @return The loaded {@link Dataset.SubSet} 
	 * @throws IOException Any issues with loading the data
	 */
	public static SubSet loadSubset(URL resource) throws IOException {
		try (InputStream iStream = resource.openStream()){
			return SubSet.fromLIBSVMFormat(iStream);
		}
	}

	/**
	 * Loads a dataset and manages the open/closing of the URL connection
	 * @param resource A URL with a data set in SVMLight format
	 * @return The loaded {@link Dataset} 
	 * @throws IOException Any issues with loading the data
	 */
	public static Dataset loadDataset(URL resource) throws IOException {
		try (InputStream iStream = resource.openStream()){
			return Dataset.fromLIBSVMFormat(iStream);
		}
	}

	public static ChemDataset loadDataset(CmpdData data) throws IOException {
		return loadDatasetWithInfo(data).getLeft();
	}
	
	public static Pair<ChemDataset,DescriptorCalcInfo> loadDatasetWithInfo(CmpdData data)throws IOException {
		
		ChemDataset d = new ChemDataset();
		d.initializeDescriptors();
		
		// Get an iterator of the correct type
		ChemFileIterator molIterator = null;
		if (data instanceof CSVCmpdData){
			molIterator = new CSVFile(data.uri()).setDelimiter(((CSVCmpdData)data).delim()).getIterator();
		} else if (data.getFormat() == Format.SDF) {
			molIterator = new SDFile(data.uri()).getIterator();
		} else {
			// JSON left
			molIterator = new JSONFile(data.uri()).getIterator();
		}

		// Load it
		DescriptorCalcInfo info = null;
		if (data.isClassification()){
			info = d.add(molIterator, data.property(), new NamedLabels(data.labelsStr()));
		} else {
			info = d.add(molIterator, data.property());
		}

		return Pair.of(d,info);
	}
	
	public static interface PreTrainedModels {

		public static final String DIR_NAME = "saved_models";
		public static final String ACP_CLF_LIBLINEAR_FILENAME = "acp_clf_linear-2.0.0.jar";
		public static final String ACP_REG_LIBSVM_FILENAME = "acp_reg_rbf-2.0.0.jar";
		public static final String CVAP_LIBLINEAR_FILENAME = "cvap_linear-2.0.0.jar";
		public static final String TCP_CLF_LINEAR_FILENAME = "tcp_linear-2.0.0.jar";
		public static final String TCP_CLF_SMALL_LINEAR_FILENAME = "tcp_small-2.0.0.jar";
		public static final String PRECOMP_CLF_FILENAME = "precomp_clf.herg.pyschem-2.0.0.jar";
		public static final String PRECOMP_REG_FILENAME = "precomp_reg-2.0.0.jar";
		public static final String NON_CHEM_FILENAME = "acp_reg_no_chem-2.0.0.jar";


		/** Chem ACP classifier */
		public static final URL ACP_CLF_LIBLINEAR = getURL(ACP_CLF_LIBLINEAR_FILENAME);
		/** Chem ACP regressor */
		public static final URL ACP_REG_LIBSVM = getURL(ACP_REG_LIBSVM_FILENAME);
		/** Chem CVAP classifier */
		public static final URL CVAP_LIBLINEAR = getURL(CVAP_LIBLINEAR_FILENAME);
		/** Chem TCP classifier */
		public static final URL TCP_CLF_LIBLINEAR = getURL(TCP_CLF_LINEAR_FILENAME);
		/** Smaller TCP classifier (20 examples per class - 2 classes - i.e. 40 examples in total) */
		public static final URL TCP_CLF_SMALL_LIBLINEAR = getURL(TCP_CLF_SMALL_LINEAR_FILENAME);
		/** Precomputed data with phys-chem descriptors */
		public static final URL PRECOMP_CLF = getURL(PRECOMP_CLF_FILENAME);
		/** Precomputed data with signatures descriptor */
		public static final URL PRECOMP_REG = getURL(PRECOMP_REG_FILENAME);

		/** Predictor model (without descriptors) for ACP regression */
		public static final URL NON_CHEM_REG = getURL(NON_CHEM_FILENAME);
	}

	private static URL getURL(String fileName){
        return TestResources.class.getResource(String.format("/%s/%s",PreTrainedModels.DIR_NAME, fileName));
    }
	
}

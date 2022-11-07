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

import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.tests.TestResources;

public class TestDataLoader {
	
	private static TestDataLoader instance = new TestDataLoader();
	
	
	private Dataset LARGE_REG, LARGE_CLASS, SMALL_REG, SMALL_CLASS;
	
	
	
	private TestDataLoader() {}
	
	public static TestDataLoader getInstance() {
		return instance;
	}
	
	
	public Dataset getDataset(boolean classification, boolean small) throws IOException {
		// CLASSIFICATION
		if (classification) {
			if (small) {
				if (SMALL_CLASS == null) {
					SMALL_CLASS = new Dataset();
					Dataset p = getDataset(true, false);
					SubSet d = p.getDataset().splitStatic(100)[0].clone();
					SMALL_CLASS.setDataset(d);
				}
				return SMALL_CLASS.clone();
			} else {
				if (LARGE_CLASS == null) {
					LARGE_CLASS = Dataset.fromLIBSVMFormat(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS.openStream());
				}
				return LARGE_CLASS.clone();
			}
		} 
		// REGRESSION
		if (small) {
			if (SMALL_REG == null) {
				SMALL_REG = Dataset.fromLIBSVMFormat(TestResources.SVMLIGHTFiles.REGRESSION_HOUSING_25.openStream());
			}
			return SMALL_REG.clone();
		} else {
			if (LARGE_REG == null) {
				LARGE_REG = Dataset.fromLIBSVMFormat(TestResources.SVMLIGHTFiles.REGRESSION_HOUSING.openStream());
			}
			return LARGE_REG.clone();
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
	
	
}

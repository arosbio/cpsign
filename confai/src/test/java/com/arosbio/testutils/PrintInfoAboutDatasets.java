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

import java.io.InputStream;
import java.net.URL;

import com.arosbio.data.Dataset.SubSet;
import com.arosbio.tests.TestResources;

public class PrintInfoAboutDatasets {

    public static void main(String[] args) {
        // binary clf
        loadAndPrint(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS, true);
        loadAndPrint(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_20, true);
        loadAndPrint(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_100, true);
        loadAndPrint(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_NEG_POS, true);
        loadAndPrint(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_TRAIN, true);
        loadAndPrint(TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_MADELON, true);
        // multiclass clf
        loadAndPrint(TestResources.SVMLIGHTFiles.CLASSIFICATION_3CLASS, true);
        loadAndPrint(TestResources.SVMLIGHTFiles.CLASSIFICATION_3CLASS_CON4, true);        
        loadAndPrint(TestResources.SVMLIGHTFiles.CLASSIFICATION_7CLASS_LARGE, true);
        // Regression
        loadAndPrint(TestResources.SVMLIGHTFiles.REGRESSION_HOUSING, false);
        loadAndPrint(TestResources.SVMLIGHTFiles.REGRESSION_HOUSING_25, false);
        loadAndPrint(TestResources.SVMLIGHTFiles.REGRESSION_ENRICHMENT, false);
        loadAndPrint(TestResources.SVMLIGHTFiles.REGRESSION_ANDROGEN, false);
    }

    public static void loadAndPrint(URL url, boolean isClf){
        
        try (InputStream stream = url.openStream()){
            SubSet data = SubSet.fromLIBSVMFormat(stream);
            if (isClf)
                System.err.printf("%s: numRecs=%s, numFeats=%s,labels=%s%n",url,data.getNumRecords(), data.getNumFeatures(),data.getLabelFrequencies());
            else
                System.err.printf("%s: numRecs=%s, numFeats=%s%n",url,data.getNumRecords(), data.getNumFeatures());

        } catch (Exception e){
            e.printStackTrace();
        }
        
    }
    
}

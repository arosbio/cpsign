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

import java.net.URL;
import java.util.Arrays;

import com.arosbio.tests.TestResources.CSVCmpdData;

public class BenchmarkResources {

    private final static String CLF_FOLDER = "/chem/classification/";
    private final static String CLF_PROPERTY = "FLAG";


    public static CSVCmpdData getADAM5(){
        return new CSVCmpdData(getURL(CLF_FOLDER + "ADAMTS5.tsv.gz"), CLF_PROPERTY, '\t', Arrays.asList("active", "inactive")).withGzip(true);
    }
    public static CSVCmpdData getCyclin(){
        return new CSVCmpdData(getURL(CLF_FOLDER + "Cyclin-dependent_kinase_1.tsv.gz"), CLF_PROPERTY, '\t', Arrays.asList("active", "inactive")).withGzip(true);
    }
    public static CSVCmpdData getD_Amino(){
        return new CSVCmpdData(getURL(CLF_FOLDER + "D-amino-acid_oxidase.tsv.gz"), CLF_PROPERTY, '\t', Arrays.asList("active", "inactive")).withGzip(true);
    }
    public static CSVCmpdData getSerine(){
        return new CSVCmpdData(getURL(CLF_FOLDER + "Serine-threonine-protein_kinase_AKT2.tsv.gz"), CLF_PROPERTY, '\t', Arrays.asList("active", "inactive")).withGzip(true);
    }
    
    private static URL getURL(String resourcePath){
        return BenchmarkResources.class.getResource(resourcePath);
    }

}

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;

public class CLITestReqOutputDir extends CLIBaseTest {

    public static final String TEST_DIR_REL_PATH = "testoutput/";
    public static final File TEST_DIR_FILE = new File(new File("").getAbsoluteFile(),TEST_DIR_REL_PATH);
    
    static {
        try{
            FileUtils.forceMkdir(TEST_DIR_FILE);
        } catch (IOException e){
            Assert.fail("Failed setting up test put dir");
        }
    }

    public static void clearTestOutDir(){
        deleteDirectory(TEST_DIR_FILE, false);
    }

    private static boolean deleteDirectory(File directoryToBeDeleted, boolean deleteCurrlLevel) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file, true);
            }
        }
        if (deleteCurrlLevel)
            return directoryToBeDeleted.delete();
        else
            return true; // We're done!
    }
    
}

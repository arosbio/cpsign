/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.depict;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Assert;

public class BaseTestClass {

    public final static File TEST_OUTPUT_DIR = new File(new File("").getAbsoluteFile(),"testoutput");

    static {
        // create test output directory if not exists
        try{
            Files.createDirectories(TEST_OUTPUT_DIR.toPath());
        } catch (IOException e){
            Assert.fail("failed setting up test directory for images");
        }
    }

    

    public static class Timer {
        private long startT;
        private long stopT=-1;

        public Timer(){
            start();
        }

        public void start(){
            startT = System.currentTimeMillis();
        }

        public void stop(){
            stopT = System.currentTimeMillis();
        }
        public long getTime(){
            if (stopT<0)
                return System.currentTimeMillis() - startT;
            return stopT - startT;
        }
        public String toString(){
            long t = getTime();
            StringBuffer res = new StringBuffer();
            // Get mins
            if (t>1e6){
                int mins = (int) (t /1e6);
                t -= mins*1e6;
                res.append(mins).append(" mins ");
            }
            // Secs
            res.append(t/1e3).append(" s");
            return res.toString();
        }
    }

    
    
}

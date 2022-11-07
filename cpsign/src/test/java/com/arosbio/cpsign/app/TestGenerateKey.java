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
import java.util.Base64;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.utils.GzipEncryption;
import com.arosbio.tests.utils.TestUtils;

@Category(CLITest.class)
public class TestGenerateKey extends CLIBaseTest {

    @Test
    public void testNoArgs(){
        mockMain(GenerateEncryptionKey.CMD_NAME);
        // printLogs();
    }

    @Test
    public void testWrongKeyLen(){
        exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
        exit.checkAssertionAfterwards(new PrintSysOutput(false));
        mockMain(GenerateEncryptionKey.CMD_NAME,"--length","20");
    }

    @Test
    public void testInvalidEncryptionType(){
        exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
        exit.checkAssertionAfterwards(new PrintSysOutput(false));
        mockMain(GenerateEncryptionKey.CMD_NAME,"--type","aros-bio","-l","20");
    }

    @Test
    public void testGenerateToSysOut(){
        mockMain(GenerateEncryptionKey.CMD_NAME,"-l","16");

        String log = systemOutRule.getLog();
        String[] lines = log.split("\n");
        for (String l : lines){
            if (l.endsWith("==")){
                // This is the line with the base64 string
                byte[] key = Base64.getDecoder().decode(l);
                Assert.assertEquals(GzipEncryption.ALLOWED_KEY_LEN, key.length);
                break;
            }
        }
        // printLogs();
    }

    @Test
    public void testGenerateToFile()throws Exception{
        File f = TestUtils.createTempFile("encrypt-key", ".txt");
        mockMain(GenerateEncryptionKey.CMD_NAME,"-l","16","-f",f.getAbsolutePath());
        // When writing to file, the contents is raw bytes, no Base64 encoding added!
        byte[] key = FileUtils.readFileToByteArray(f);
        Assert.assertEquals(GzipEncryption.ALLOWED_KEY_LEN, key.length);
        // printLogs();
    }
    
}

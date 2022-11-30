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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.encryption.utils.EncryptionSpecFactory;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.GzipEncryption;

@Category(UnitTest.class)
public class TestGzipEncryption extends TestEnv {

    @Test
    public void testEncrAndDecrypt() throws Exception {

        LoggerUtils.addStreamAppenderToLogger((ch.qos.logback.classic.Logger)LoggerFactory.getLogger(GzipEncryption.class), System.err, "%m%n");
        LoggerUtils.setTraceMode();

		String text1 = "This is a metpred enabled model";
		String text2 = "This is encrypted data";

        // Spec 1 inited using bytes
        EncryptionSpecification spec1 = EncryptionSpecFactory.getInstance("gzip");
        spec1.init(spec1.generateRandomKey(16));

        // Spec 2 inited using string
        EncryptionSpecification spec2 = EncryptionSpecFactory.getInstance("gzip");
        spec2.init(spec1.generateRandomKey(16));

        ByteArrayOutputStream s1 = new ByteArrayOutputStream();
        ByteArrayOutputStream s2 = new ByteArrayOutputStream();

        try(OutputStream stream1 = spec1.encryptStream(s1);
            OutputStream stream2 = spec2.encryptStream(s2);){
            stream1.write(text1.getBytes());
            stream1.flush();
            stream2.write(text2.getBytes());
            stream2.flush();
        }

        try(BufferedInputStream buff1 = new BufferedInputStream(new ByteArrayInputStream(s1.toByteArray()));
            BufferedInputStream buff2 = new BufferedInputStream(new ByteArrayInputStream(s2.toByteArray()));
            ){
                // Resource 1
                Assert.assertTrue(spec1.canDecrypt(buff1));
                Assert.assertFalse(spec2.canDecrypt(buff1));
                Assert.assertTrue(spec2.encryptedByType(buff1));


                // Resource 2
                Assert.assertFalse(spec1.canDecrypt(buff2));
                Assert.assertTrue(spec2.canDecrypt(buff2));
                Assert.assertTrue(spec1.encryptedByType(buff2));
                Assert.assertTrue(spec2.encryptedByType(buff2));
                

            }
    }
    
}

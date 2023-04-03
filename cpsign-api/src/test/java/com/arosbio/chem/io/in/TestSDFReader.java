/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem.io.in;

import java.io.InputStream;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.commons.TypeUtils;
import com.arosbio.io.StreamUtils;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.UnitTestBase;

@Category(UnitTest.class)
public class TestSDFReader extends UnitTestBase {

    @Test
    public void testhERGWithoutPropertyChecks() throws Exception {
        CmpdData herg = TestResources.Reg.getHERG();
        
        int numOK = 0, numMissingProp = 0, numInvalidProp = 0;
        try(InputStream in = herg.url().openStream();
            InputStream unzipped = StreamUtils.unZIP(in);
            SDFReader reader = new SDFReader(unzipped);){

                while (reader.hasNext()){
                    IAtomContainer mol = reader.next();

                    if (!mol.getProperties().containsKey(herg.property())){
                        // No property defined at all
                        numMissingProp ++;
                        continue;
                    }
                    String prop = mol.getProperty(herg.property()).toString();
                    if (! TypeUtils.isDouble(prop)){
                        numInvalidProp ++;
                    } else {
                        numOK ++;
                    }

                }
               
            }
            Assert.assertEquals(474, numOK);
            Assert.assertEquals(0, numMissingProp); // all should have the property
            Assert.assertEquals(806-numOK, numInvalidProp); // The rest should be invalid, e.g. N/A and greater/equals etc
       
    }
    
}

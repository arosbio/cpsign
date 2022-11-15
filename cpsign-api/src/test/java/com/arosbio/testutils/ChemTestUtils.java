package com.arosbio.testutils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.smiles.SmiFlavor;
import org.openscience.cdk.smiles.SmilesGenerator;

import com.arosbio.chem.io.in.SDFReader;


public class ChemTestUtils {
    
    public static String CDK_TITLE_REGEX = "\\s+CDK\\s+\\d{8,}";
    
    public static void assertSameSDFOutput(String sdf1, String sdf2){
        assertSameSDFOutput(Arrays.asList(sdf1.split("\n")), Arrays.asList(sdf2.split("\n")));
    }
    /**
     * Checks if two files in SDF formats are equal, but ignores the CDK-title line
     * as is time-dependent and otherwise may differ only due to this
     * @param sdf1 content from file 1
     * @param sdf2 content from file 2
     */
    public static void assertSameSDFOutput(List<String> sdf1, List<String> sdf2){
        Assert.assertEquals("The SDF files should have the same number of lines", 
            sdf1.size(), sdf2.size());

        for (int i = 0; i<sdf1.size(); i++){
            if (Pattern.matches(CDK_TITLE_REGEX, sdf1.get(i))){
                // If one file matches the CDK title line of a MOL block - the other one should as well
                Assert.assertTrue("Title line in one file but not the other",Pattern.matches(CDK_TITLE_REGEX, sdf2.get(i)));
            } else {
                Assert.assertEquals(sdf1.get(i), sdf2.get(i));
            }

        }
    }

    /**
     * Checks that two files, but written with different SDF versions (v2000 vs v3000) still
     * has the same content - while not being able to check string vs string (have to read in as molecules).
     * NB: there might be an issue with CDK:Title field if it's generated by CDK SDFWriter class
     * @param sdf1 content file 1
     * @param sdf2 content file 2
     * @throws CDKException
     */
    public static void assertSDFHasSameContentDifferentVersions(String sdf1, String sdf2) throws IOException, CDKException {
        final SmilesGenerator generator = new SmilesGenerator( SmiFlavor.Canonical | SmiFlavor.UseAromaticSymbols );


        try(SDFReader reader1 = new SDFReader(IOUtils.toInputStream(sdf1, StandardCharsets.UTF_8));
            SDFReader reader2 = new SDFReader(IOUtils.toInputStream(sdf2,StandardCharsets.UTF_8));){
            
            while (reader1.hasNext()){
                IAtomContainer mol1 = reader1.next();
                Assert.assertTrue(reader2.hasNext());
                IAtomContainer mol2 = reader2.next();

                Assert.assertEquals(generator.create(mol1), generator.create(mol2));
                Assert.assertEquals(mol1.getProperties(), mol2.getProperties());
            }

            Assert.assertFalse("No more molecules in file 1 - should be no more in file 2",reader2.hasNext());
        }


    }
}

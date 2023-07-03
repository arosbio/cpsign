/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */

package com.arosbio.cheminf.filter;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import com.arosbio.chem.CDKConfigureAtomContainer;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.UnitTestBase;
import com.google.common.collect.ImmutableMap;

@Category(UnitTest.class)
public class TestChemFilters extends UnitTestBase {
    
    static IAtomContainer SMALL_MOL, MEDIUM_MOL, LARGE_MOL;

    @BeforeClass
    public static void setupMols() throws InvalidSmilesException{
        SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
        SMALL_MOL = parser.parseSmiles("CCCC"); // HAC: 4, mass: 58.1223511254099
        MEDIUM_MOL = parser.parseSmiles(TEST_SMILES); // HAC: 10, mass: 133.14758058568995
        // HAC: 108, mass: 1840.540609365747
        LARGE_MOL = CDKConfigureAtomContainer.getLargestContainer(parser.parseSmiles("[Na+].[Na+].[Na+].[Na+].[Na+].[Na+].[Na+].[Na+].[Na+].[Na+].[Na+].[Na+].[Na+].CCCCc1ccc(CO[C@H]2O[C@H](COS(=O)(=O)[O-])[C@@H](OS(=O)(=O)[O-])[C@H](OS(=O)(=O)[O-])[C@@H]2O[C@H]3O[C@H](COS(=O)(=O)[O-])[C@@H](OS(=O)(=O)[O-])[C@H](O[C@H]4O[C@H](COS(=O)(=O)[O-])[C@@H](OS(=O)(=O)[O-])[C@H](O[C@H]5O[C@H](COS(=O)(=O)[O-])[C@@H](OS(=O)(=O)[O-])[C@H](OS(=O)(=O)[O-])[C@@H]5OS(=O)(=O)[O-])[C@@H]4OS(=O)(=O)[O-])[C@@H]3OS(=O)(=O)[O-])cc1"));

        // SYS_ERR.println("HAC: " + SMALL_MOL.getAtomCount() + ", mass: " + AtomContainerManipulator.getMass(SMALL_MOL, AtomContainerManipulator.MolWeightIgnoreSpecified));
        // SYS_ERR.println("HAC: " + MEDIUM_MOL.getAtomCount() + ", mass: " + AtomContainerManipulator.getMass(MEDIUM_MOL, AtomContainerManipulator.MolWeightIgnoreSpecified));
        // SYS_ERR.println("HAC: " + LARGE_MOL.getAtomCount() + ", mass: " + AtomContainerManipulator.getMass(LARGE_MOL, AtomContainerManipulator.MolWeightIgnoreSpecified));
        
        
    }

    @Test
    public void testHACFilter() throws Exception {
        HACFilter defaultFilter = new HACFilter();
        
        Assert.assertFalse("should discard mols with less than 5 HAC",defaultFilter.keep(SMALL_MOL));
        Assert.assertTrue(defaultFilter.keep(MEDIUM_MOL));
        Assert.assertTrue(defaultFilter.keep(LARGE_MOL));

        Assert.assertTrue("should get a discard message for too small molecule",defaultFilter.getDiscardReason(SMALL_MOL).length()>5);
        try{
            defaultFilter.getDiscardReason(LARGE_MOL);
            Assert.fail("Large molecule (not discarded) should result in exception"); 
        } catch (IllegalArgumentException e){}
        try{
            defaultFilter.getDiscardReason(MEDIUM_MOL);
            Assert.fail("medium molecule (not discarded) should result in exception"); 
        } catch (IllegalArgumentException e){}


        // Conf it to allow smaller mols, and filter out large mols
        defaultFilter.setConfigParameters(ImmutableMap.of("min", 3, "max", 50));

        Assert.assertTrue("should now pass this molecule",defaultFilter.keep(SMALL_MOL));
        Assert.assertTrue(defaultFilter.keep(MEDIUM_MOL));
        Assert.assertFalse(defaultFilter.keep(LARGE_MOL));
    }

    @Test
    public void testMolMassFilter() throws Exception {
        MolMassFilter filter = new MolMassFilter().withMaxMass(150d).withMinMass(5d);

        Assert.assertTrue(filter.keep(SMALL_MOL));
        Assert.assertTrue(filter.keep(MEDIUM_MOL));
        Assert.assertFalse(filter.keep(LARGE_MOL));

        try{
            filter.getDiscardReason(SMALL_MOL);
            Assert.fail("small molecule (not discarded) should result in exception"); 
        } catch (IllegalArgumentException e){}
        try{
            filter.getDiscardReason(MEDIUM_MOL);
            Assert.fail("medium molecule (not discarded) should result in exception"); 
        } catch (IllegalArgumentException e){}

        Assert.assertTrue(filter.getDiscardReason(LARGE_MOL).length()>5);

        filter.setConfigParameters(ImmutableMap.of("min", 0, "max", Integer.MAX_VALUE));
        Assert.assertTrue(filter.keep(SMALL_MOL));
        Assert.assertTrue(filter.keep(MEDIUM_MOL));
        Assert.assertTrue(filter.keep(LARGE_MOL));
    }
}

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.inchi.InChIGenerator;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;

import com.arosbio.chem.CPSignMolProperties.InChIHandler;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.UnitTestBase;

@Category(UnitTest.class)
public class TestInChIGeneration extends UnitTestBase{

	@Test
	public void testGenerateInChI() throws Exception {

		InChIHandler inst = CPSignMolProperties.InChIHandler.getInstance();

		if (inst.isAvailable()){
			System.err.println("Is available!");
			SmilesParser sp = new SmilesParser(SilentChemObjectBuilder.getInstance());
			IAtomContainer mol = sp.parseSmiles("CC(C1=CC=CC=C1)NCCC(C2=CC=CC=C2)C3=CC=CC=C3.Cl");
			AtomContainerManipulator.suppressHydrogens(mol);
			InChIGenerator igf = inst.getGenerator(mol);
			System.out.println(igf.getInchi());
			System.out.println(igf.getInchiKey());
		} else {
			Assert.fail("Inchi generation not available");
		}

	}


}

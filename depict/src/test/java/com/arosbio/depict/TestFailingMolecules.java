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
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.imageio.ImageIO;

import com.arosbio.color.gradient.GradientFactory;

import org.junit.Before;
import org.junit.Test;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

public class TestFailingMolecules extends BaseTestClass {

	List<IAtomContainer> mols;
	@Before
	public void setupMolecules() throws InvalidSmilesException{
		SmilesParser sp = new SmilesParser(SilentChemObjectBuilder.getInstance());
		mols = Arrays.asList(
				sp.parseSmiles("CC(C1=CC=CC=C1)NCCC(C2=CC=CC=C2)C3=CC=CC=C3.Cl"),
				sp.parseSmiles("CCN(CC)CCOC(=O)C(C1CCCCC1)C2=CC=CC=C2.Cl"),
				sp.parseSmiles("CN(C)CCOC(C1=CC=CC=C1)C2=CC=CC=C2.Cl"),
				sp.parseSmiles("C1=CC=C(C=C1)N=NC2=C(N=C(C=C2)N)N.Cl"));
	}

	@Test
	public void testGenerateImages() throws Exception {
		MoleculeDepictor mp = new MoleculeDepictor.Builder().color(GradientFactory.getRainbowGradient()).build();
		List<BufferedImage> images = new ArrayList<>();
		for(IAtomContainer mol : mols){
			images.add(mp.depict(mol, new HashMap<IAtom, Double>()));
		}
		int i = 0;
		for(BufferedImage image:images) {
	        ImageIO.write(image, "png", new File(TEST_OUTPUT_DIR,"molecule_"+i+".png"));	
	        i++;
		}
	}

}

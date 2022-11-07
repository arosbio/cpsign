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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import com.arosbio.color.gradient.GradientFactory;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

public class TestBase64Generation extends BaseTestClass {
	
	static IAtomContainer mol;
	static Map<IAtom, Double> colorMap;
	
	@Before
	public void setup() throws Exception {
		SmilesParser sp = new SmilesParser(SilentChemObjectBuilder.getInstance());
		mol = sp.parseSmiles("c1ccccc1C(CC)CO");
		colorMap = new HashMap<>();
		for(int i=0; i< 5; i++){
			IAtom atom = mol.getAtom(i);
			colorMap.put(atom, (((double)i)/5)*2-1);
		}
	}
	
	@Test
	public void testBase64() throws IOException{
		
		MoleculeDepictor md = new MoleculeDepictor.Builder().color(GradientFactory.getRainbowGradient()).build();
		BufferedImage bi = md.depict(mol, colorMap);
		
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		ImageIO.write(bi, "png", os);
		String result = Base64.getEncoder().encodeToString(os.toByteArray());
		Assert.assertTrue(result.length()> 100);
		// System.out.println(result);
	}

}

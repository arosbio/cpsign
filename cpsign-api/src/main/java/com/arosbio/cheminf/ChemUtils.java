/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cheminf;

import javax.vecmath.Point2d;

import com.arosbio.chem.CDKConfigureAtomContainer;

import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.silent.SilentChemObjectBuilder;

public class ChemUtils {

    public static IAtomContainer makeCCC() throws IllegalArgumentException, CDKException {
		IChemObjectBuilder builder = SilentChemObjectBuilder.getInstance();
		IAtomContainer container = builder.newInstance(IAtomContainer.class);
		container.addAtom(builder.newInstance(IAtom.class, "C", new Point2d(-1, -1)));
		container.addAtom(builder.newInstance(IAtom.class, "C", new Point2d(0, 0)));
		container.addAtom(builder.newInstance(IAtom.class, "C", new Point2d(1, -1)));
		container.addBond(0, 1, IBond.Order.SINGLE);
		container.addBond(1, 2, IBond.Order.SINGLE);
		CDKConfigureAtomContainer.configMolecule(container);
		return container;
	}
    
}

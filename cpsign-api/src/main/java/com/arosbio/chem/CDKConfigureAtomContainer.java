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

import java.util.LinkedHashMap;

import org.openscience.cdk.aromaticity.Aromaticity;
import org.openscience.cdk.aromaticity.ElectronDonation;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.geometry.GeometryUtil;
import org.openscience.cdk.graph.ConnectivityChecker;
import org.openscience.cdk.graph.Cycles;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IAtomContainerSet;
import org.openscience.cdk.modeling.builder3d.ModelBuilder3D;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.tools.CDKHydrogenAdder;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;

public class CDKConfigureAtomContainer {

	private static Aromaticity arom = new Aromaticity(ElectronDonation.daylight(),
			Cycles.or(Cycles.all(), Cycles.all(6)));
	private static CDKHydrogenAdder adder = CDKHydrogenAdder.getInstance(SilentChemObjectBuilder.getInstance());

	public static void configMolecule(IAtomContainer mol) 
			throws IllegalArgumentException, CDKException{
		if (mol==null)
			throw new IllegalArgumentException("Molecule cannot be null");
		AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(mol);
		adder.addImplicitHydrogens(mol);
		arom.apply(mol);
		AtomContainerManipulator.suppressHydrogens(mol);
	}

	public static IAtomContainer calculate3DCoordinates(IAtomContainer mol, boolean forceRecalculate) 
			throws CDKException {
		if (GeometryUtil.has3DCoordinates(mol) && !forceRecalculate) {
			return mol;
		}
		
		IAtomContainer largestConnectedFragment = mol;
		if (!ConnectivityChecker.isConnected(mol)) {
			IAtomContainerSet set = ConnectivityChecker.partitionIntoMolecules(mol);
			largestConnectedFragment = set.getAtomContainer(0);
			for (IAtomContainer container : set.atomContainers()) {
				if (largestConnectedFragment.getAtomCount() < container.getAtomCount())
					largestConnectedFragment = container;
			}
		}
		
		largestConnectedFragment.setProperties(new LinkedHashMap<>(mol.getProperties()));
		
		try {
			ModelBuilder3D mb3d = ModelBuilder3D.getInstance(SilentChemObjectBuilder.getInstance());
			mb3d.generate3DCoordinates(largestConnectedFragment, false);
			return largestConnectedFragment;
		} catch (Exception e) {
			throw new CDKException(e.getMessage());
		}
	}

}

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cheminf.descriptors;

import java.util.HashSet;
import java.util.Set;

import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IDoubleBondStereochemistry;
import org.openscience.cdk.interfaces.IStereoElement;
import org.openscience.cdk.interfaces.ITetrahedralChirality;
import org.openscience.cdk.silent.AtomContainer;
import org.openscience.cdk.smiles.SmiFlavor;
import org.openscience.cdk.smiles.SmilesGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;

public class StereoAtomSignature {
	private static final Logger LOGGER = LoggerFactory.getLogger(StereoAtomSignature.class);

	private IAtom atom;
	private IAtomContainer molecule;
	private IAtomContainer signature;
	private int height;
	private static SmilesGenerator canonicalGenerator = new SmilesGenerator(SmiFlavor.Absolute|SmiFlavor.Stereo);

	public StereoAtomSignature(IAtom atom, int height, IAtomContainer mol){
		if (height < 0)
			throw new IllegalArgumentException("parameter height must be >=0");
		this.atom = atom;
		this.molecule = mol;
		this.height = height;
	}

	public String toCanonicalString() 
			throws IllegalArgumentException{
		if (signature==null)
			doComputeSignatureWithHydrogencount();
		if (height == 0) // this is to not get [CH] and stuff like that, we only care about the atoms themselves on height=0!!
			return atom.getSymbol();
		try {
			return canonicalGenerator.create(signature);
		} catch (CDKException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@SuppressWarnings("unused")
	private void doComputeSignatureNoHydrogencount() throws CDKException {
		signature = new AtomContainer();

		// Quick-stop if only height 0
		if (height==0) {
			signature.addAtom(atom); // Add the center atom
			return;	
		}

		// Set all atoms first
		Set<IAtom> allAtoms = new HashSet<>();
		allAtoms.add(atom);
		Set<IAtom> atomsToCheck = new HashSet<>();
		atomsToCheck.add(atom);
		for (int i=1; i<=height; i++) {
			Set<IAtom> currentRound = new HashSet<>(atomsToCheck);
			for (IAtom currentCenter: currentRound){
				Set<IAtom> foundAtoms = getConnectedAtoms(currentCenter);
				atomsToCheck.addAll(CollectionUtils.getUniqueFromFirstSet(foundAtoms, allAtoms));
				atomsToCheck.remove(currentCenter);
				allAtoms.addAll(foundAtoms);
			}
		}
		for (IAtom at: allAtoms){
			signature.addAtom(at);
		}

		// Set all bonds
		int numBonds=0;
		for (IBond bond : molecule.bonds()) {
			if(allAtoms.contains(bond.getEnd()) && allAtoms.contains(bond.getBegin())) {
				signature.addBond(bond);
				numBonds++;
			} 
		}
		LOGGER.trace("StereoAtomSignautre: Found {} atom(s) and {} bond(s) at height {}",allAtoms.size(),numBonds,height);

		// Set all stereo elements
		for (IStereoElement<?, ?> se : molecule.stereoElements()) {
			if (se instanceof ITetrahedralChirality) {
				ITetrahedralChirality tc = (ITetrahedralChirality) se;
				if (allAtoms.contains(tc.getChiralAtom()) &&
						allAtoms.contains(tc.getLigands()[0]) &&
						allAtoms.contains(tc.getLigands()[1]) &&
						allAtoms.contains(tc.getLigands()[2]) &&
						allAtoms.contains(tc.getLigands()[3]))
					signature.addStereoElement(tc);
			}
			else if (se instanceof IDoubleBondStereochemistry){
				IDoubleBondStereochemistry dbs = (IDoubleBondStereochemistry)se;
				IBond stereoBond = dbs.getStereoBond();

				if (allAtoms.contains(stereoBond.getBegin()) && allAtoms.contains(stereoBond.getEnd())){
					// we also require next level of atoms to be part of the fingerprint as well (otherwise there is no stereo-element)
					IBond[] nextLevelBonds = dbs.getBonds();
					boolean allPresent = true;
					for (IBond bond: nextLevelBonds){
						if(!allAtoms.contains(bond.getBegin()) || ! allAtoms.contains(bond.getEnd())){
							allPresent = false;
							break;
						}
					}
					if (allPresent) {
						signature.addStereoElement(dbs);
					}
				}
			}
		}
		// Somehow this fixed the bug of not getting canonical smiles!
		try{
			signature = signature.clone();
		} catch(CloneNotSupportedException e){
			LOGGER.debug("Could not clone the signature container", e);
			throw new CDKException("Could not clone the signature: "+ e.getMessage());
		}

	}

	private void doComputeSignatureWithHydrogencount() 
			throws IllegalArgumentException {
		signature = new AtomContainer();

		// Quick-stop if only height 0
		if (height==0) {
			signature.addAtom(atom); // Add the center atom
			return;	
		}

		int[] hcounts = new int[molecule.getAtomCount()];

		// Set all atoms first
		Set<IAtom> allAtoms = new HashSet<>();
		allAtoms.add(atom);
		Set<IAtom> atomsToCheck = new HashSet<>();
		atomsToCheck.add(atom);
		for (int i=1; i<=height; i++) {
			Set<IAtom> currentRound = new HashSet<>(atomsToCheck);
			for (IAtom currentCenter: currentRound){
				Set<IAtom> foundAtoms = getConnectedAtoms(currentCenter);
				atomsToCheck.addAll(CollectionUtils.getUniqueFromFirstSet(foundAtoms, allAtoms));
				atomsToCheck.remove(currentCenter);
				allAtoms.addAll(foundAtoms);
			}
		}
		for (IAtom at: allAtoms){
			signature.addAtom(at);
			hcounts[molecule.indexOf(at)] = at.getImplicitHydrogenCount();
		}

		// Set all bonds
		int numBonds=0;
		for (IBond bond : molecule.bonds()) {
			if(allAtoms.contains(bond.getEnd()) && allAtoms.contains(bond.getBegin())) {
				signature.addBond(bond);
				numBonds++;
			} else if (allAtoms.contains(bond.getBegin())) {
				int hadj = bond.getOrder().numeric();
				bond.getBegin().setImplicitHydrogenCount(hadj + bond.getBegin().getImplicitHydrogenCount());
			} else if (allAtoms.contains(bond.getEnd())) {
				int hadj = bond.getOrder().numeric();
				bond.getEnd().setImplicitHydrogenCount(hadj + bond.getEnd().getImplicitHydrogenCount());
			}
		}
		LOGGER.trace("StereoAtomSignature: Found {} atom(s) and {} bond(s) at height {}",allAtoms.size(), numBonds, height);

		// Set all stereo elements
		for (IStereoElement<?, ?> se : molecule.stereoElements()) {
			if (se instanceof ITetrahedralChirality) {
				ITetrahedralChirality tc = (ITetrahedralChirality) se;
				if (allAtoms.contains(tc.getChiralAtom()) &&
						allAtoms.contains(tc.getLigands()[0]) &&
						allAtoms.contains(tc.getLigands()[1]) &&
						allAtoms.contains(tc.getLigands()[2]) &&
						allAtoms.contains(tc.getLigands()[3]))
					signature.addStereoElement(tc);
			}
			else if (se instanceof IDoubleBondStereochemistry){
				IDoubleBondStereochemistry dbs = (IDoubleBondStereochemistry)se;
				IBond stereoBond = dbs.getStereoBond();

				if (allAtoms.contains(stereoBond.getBegin()) && allAtoms.contains(stereoBond.getEnd())){
					// we also require next level of atoms to be part of the fingerprint as well (otherwise there is no stereo-element)
					IBond[] nextLevelBonds = dbs.getBonds();
					boolean allPresent = true;
					for (IBond bond: nextLevelBonds){
						if(!allAtoms.contains(bond.getBegin()) || ! allAtoms.contains(bond.getEnd())){
							allPresent = false;
							break;
						}
					}
					if (allPresent) {
						signature.addStereoElement(dbs);
					}
				}
			}
		}
		// Somehow this fixed the bug of not getting canonical smiles!
		try {
			signature = signature.clone();
		} catch(CloneNotSupportedException e){
			LOGGER.debug("Could not clone the signature container", e);
			throw new IllegalArgumentException("Could not clone the signature: "+ e.getMessage());
		}

		// Set back all hydrogen 
		for (IAtom at: allAtoms){
			at.setImplicitHydrogenCount(hcounts[molecule.indexOf(at)]);
			hcounts[molecule.indexOf(atom)] = atom.getImplicitHydrogenCount();
		}
	}

	private Set<IAtom> getConnectedAtoms(IAtom centerAtom) {
		Set<IAtom> connectedAtoms = new HashSet<>();
		for (IBond bond: molecule.bonds()){
			if (bond.contains(centerAtom)){
				for (IAtom otherAtom: molecule.atoms()){
					if (centerAtom!=otherAtom && bond.contains(otherAtom))
						connectedAtoms.add(otherAtom);
				}
			}
		}
		return connectedAtoms;
	}

}

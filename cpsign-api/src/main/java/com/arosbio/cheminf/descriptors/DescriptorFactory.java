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

import java.nio.file.ProviderNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.openscience.cdk.qsar.IMolecularDescriptor;
import org.openscience.cdk.qsar.descriptors.molecular.LongestAliphaticChainDescriptor;
import org.openscience.cdk.qsar.descriptors.protein.TaeAminoAcidDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cheminf.descriptors.fp.FPDescriptor;
import com.arosbio.commons.FuzzyMatcher;
import com.arosbio.commons.TypeUtils;

public final class DescriptorFactory {

	private static final Logger LOGGER = LoggerFactory.getLogger(DescriptorFactory.class); 
	private static DescriptorFactory theRegistry;

	private List<ChemDescriptor> allDescriptors = new ArrayList<>();

	///////////////////////////////////////////////////
	///////// INSTANTIATION AND LOADING OF PROVIDERS
	///////////////////////////////////////////////////

	//	LongestAliphaticChainDescriptor || 
	//	impl instanceof TaeAminoAcidDescriptor)

	private DescriptorFactory() throws IllegalStateException {
		// Load all CPSign-descriptor ones
		ServiceLoader<ChemDescriptor> loader = ServiceLoader.load(ChemDescriptor.class);
		LOGGER.debug("Initiating the DescriptorFactory - loading all ChemDescriptor providers");
		Iterator<ChemDescriptor> classes = loader.iterator();

		int numCPSignProviders=0;
		List<String> names = new ArrayList<>();
		while (classes.hasNext()) {
			ChemDescriptor impl = classes.next();
			allDescriptors.add(impl);
			names.addAll(TypeUtils.getNames(impl));
			numCPSignProviders++;
		}
		LOGGER.debug("Loaded ChemDescriptor-providers: {}", allDescriptors);
		int numInitialProviders = allDescriptors.size();

		// Load the CDK IMolecularDescriptors
		ServiceLoader<IMolecularDescriptor> ckdDescriptorLoader = ServiceLoader.load(IMolecularDescriptor.class);
		int numCDKProviders = 0;
		Iterator<IMolecularDescriptor> cdkClasses = ckdDescriptorLoader.iterator();
		while (cdkClasses.hasNext()) {
			IMolecularDescriptor impl = cdkClasses.next();
			if (impl instanceof TaeAminoAcidDescriptor || 
					impl instanceof LongestAliphaticChainDescriptor)
				continue; // seems to be a bug with this one!
			ChemDescriptor wrapped = new CDKPhysChemWrapper(impl);
			allDescriptors.add(wrapped);
			names.addAll(TypeUtils.getNames(wrapped));
			numCDKProviders++;
		}
		LOGGER.debug("Loaded {} CDK phys-chem descriptors: {}",numCDKProviders,
				allDescriptors.subList(numInitialProviders, allDescriptors.size()));

		if (numCPSignProviders < 1 || numCDKProviders < 50) {
			throw new IllegalStateException("No ChemDescriptor Providers could be loaded. Something is wrong with the Java classpath");
		}

		if (new HashSet<>(names).size() != names.size()) {
			throw new IllegalStateException("The names for the ChemDescriptors are not unique! Note that custom descriptors must have their own unique names!");
		}
	}


	public static synchronized DescriptorFactory getInstance() {
		if (theRegistry == null) {
			theRegistry = new DescriptorFactory();
		}
		return theRegistry;
	}


	///////////////////////////////////////////////////
	///////// GETTERS AND SETTERS
	///////////////////////////////////////////////////

	public ChemDescriptor getDescriptorFuzzyMatch(String name) throws ProviderNotFoundException {

		List<Pair<List<String>,ChemDescriptor>> availableDescriptors = new ArrayList<>();
		for (ChemDescriptor d: allDescriptors) {
			availableDescriptors.add(ImmutablePair.of(TypeUtils.getNames(d), d));
		}
		try {
			return new FuzzyMatcher().match(availableDescriptors, name).clone();
		} catch (IllegalArgumentException e) {
			throw new ProviderNotFoundException("ChemDescriptor not found: " + e.getMessage());
		}
	}

	public ChemDescriptor getDescriptor(String name) throws ProviderNotFoundException {
		for (ChemDescriptor d: allDescriptors) {
			for (String descName: TypeUtils.getNames(d))
				if (descName.toLowerCase().equals(name.toLowerCase()))
					return d.clone();
		}
		throw new ProviderNotFoundException("Provider with name " + name + " not found");
	}

	public Iterator<ChemDescriptor> getDescriptors(){
		return getDescriptorsList().iterator();
	}

	public List<ChemDescriptor> getDescriptorsList(){
		// Make a deep cloned list
		List<ChemDescriptor> clone = new ArrayList<>();
		for (ChemDescriptor d : allDescriptors)
			clone.add(d.clone());
		return clone;
	}

	public static List<ChemDescriptor> getCDKDescriptorsNo3D(){
		List<ChemDescriptor> cdkDesc = new ArrayList<>();

		for (ChemDescriptor d : DescriptorFactory.getInstance().getDescriptorsList()) {
			if (d instanceof CDKPhysChemWrapper && !d.requires3DCoordinates()) {
				cdkDesc.add(d);
			}
		}
		return cdkDesc;
	}
	
	public static List<ChemDescriptor> getCDKDescriptorsRequire3D(){
		List<ChemDescriptor> cdkDesc = new ArrayList<>();

		for (ChemDescriptor d : DescriptorFactory.getInstance().getDescriptorsList()) {
			if (d instanceof CDKPhysChemWrapper && d.requires3DCoordinates()) {
				cdkDesc.add(d);
			}
		}
		return cdkDesc;
	}

	public static List<ChemDescriptor> getFingerprintDescriptors(){
		List<ChemDescriptor> fpDesc = new ArrayList<>();

		for (ChemDescriptor d : DescriptorFactory.getInstance().getDescriptorsList()) {
			if (d instanceof FPDescriptor) {
				fpDesc.add(d);
			}
		}
		return fpDesc;
	}
}

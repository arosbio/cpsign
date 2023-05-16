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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.inchi.InChIGenerator;
import org.openscience.cdk.inchi.InChIGeneratorFactory;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.smiles.SmiFlavor;
import org.openscience.cdk.smiles.SmilesGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cheminf.ChemUtils;

public class CPSignMolProperties {

	private static final Logger LOGGER = LoggerFactory.getLogger(CPSignMolProperties.class);

	private static final char MISSING_INDEX = '-';

	/* 
	 * ========================================================================
	 * Constants used for setting and collecting properties of IAtomContainers
	 * ========================================================================
	 */

	public static class Constants {
		/**
		 * Prefix always added to keys in {@code IAtomContainer} properties
		 */
		public static final String PROPERTY_PREFIX = "cpsign:";

		/** Property for keeping track of the index of IAtomContainer when read from some input source */
		public static final String RECORD_INDEX = PROPERTY_PREFIX + "record-index";
		/** Property for either given or computed smiles for the given IAtomContainer */
		public static final String SMILES_PROPERTY = PROPERTY_PREFIX + "SMILES";
		/** InChI property - this is not started with internal prefix - should end up in result */
		public static final String INCHI = "InChI";
		/** InChIKey property - this is not started with internal prefix - should end up in result */
		public static final String INCHIKEY = "InChIKey";

		public static final String CDK_PREFIX = "cdk:";
	}

	/**
	 * Get a custom name for smiles, or <code>null</code> if no such one 
	 * is found (then use the default one in the output instead)
	 * @param properties properties found in input
	 * @return a custom name or <code>null</code>
	 */
	public static final String getCustomSmilesProperty(Collection<? extends Object> properties) {
		for (Object prop : CPSignMolProperties.stripInteralProperties(properties)) {
			String lowCase = prop.toString().toLowerCase();
			if (lowCase.contains("smiles")) {
				return prop.toString();
			}
		}
		return null;
	}

	public static boolean hasMolTitle(IAtomContainer mol) {
		return getMolTitle(mol) != null ? true : false;
	}

	public static String getMolTitle(IAtomContainer mol) {
		return mol.getProperty(CDKConstants.TITLE);
	}

	public static void setRecordIndex(IAtomContainer mol, Object index) {
		mol.setProperty(Constants.RECORD_INDEX, (index!=null? index : MISSING_INDEX));
	}

	public static boolean hasRecordIndex(IAtomContainer mol) {
		if (mol==null)
			return false;
		return mol.getProperty(Constants.RECORD_INDEX) != null;
	}
	public static Object getRecordIndex(IAtomContainer mol) {
		Object index = mol.getProperty(Constants.RECORD_INDEX) ;
		return (index != null) ? index : MISSING_INDEX;
	}

	public static void setSMILES(IAtomContainer mol, String smiles) {
		mol.setProperty(Constants.SMILES_PROPERTY, smiles);
	}

	private static SmilesGenerator smilesGen = new SmilesGenerator(SmiFlavor.Absolute | SmiFlavor.UseAromaticSymbols);
	private static final String FAILED_SMILES_GEN = "failed generating SMILES";

	public static String getSMILES(IAtomContainer mol) {
		if (mol.getProperty(Constants.SMILES_PROPERTY) != null)
			return mol.getProperty(Constants.SMILES_PROPERTY).toString();

		try {
			LOGGER.debug("no existing SMILES - generating one");
			return smilesGen.create(mol);
		} catch (CDKException cdk){
			return FAILED_SMILES_GEN;
		}
	}

	public static void stripInternalProperties(IAtomContainer mol){
		Map<Object,Object> cleanedProps = new LinkedHashMap<>();
		for (Map.Entry<Object,Object> kv : mol.getProperties().entrySet()) {
			if (!kv.getKey().toString().startsWith(Constants.PROPERTY_PREFIX)) {
				cleanedProps.put(kv.getKey(), kv.getValue());
			}
		}
		mol.setProperties(cleanedProps);
	}

	public static List<String> stripInteralProperties(Collection<?> props){
		Set<String> cleaned = new LinkedHashSet<>();
		for (Object p : props) {
			String pStr = p.toString();
			if (pStr.startsWith(Constants.PROPERTY_PREFIX)) {
				// Skip it!
			} else {
				cleaned.add(pStr);
			}
		}
		return new ArrayList<>(cleaned);
	}


	public static <K,V> Map<K,V> stripInteralPropertiesMap(Map<K, V> props){
		Map<K, V> cleaned = new LinkedHashMap<>();
		//		Set<String> cleaned = new LinkedHashSet<>();
		for (Map.Entry<K, V> kv : props.entrySet()) {
			if (kv.getKey().toString().startsWith(Constants.PROPERTY_PREFIX)) {
				// Skip it!
			} else {
				cleaned.put(kv.getKey(), kv.getValue());
			}
		}
		return cleaned;
	}

	public static <K,V> List<K> stripEmptyCDKPropertiesKeys(Map<K,V> props){
		List<K> cleanedKeys = new ArrayList<>();
		for (Map.Entry<K,V> kv : props.entrySet()){
			if (kv.getKey() instanceof String && kv.getKey().toString().startsWith(Constants.CDK_PREFIX) && kv.getValue() == null){
				// do not add this key
				continue;
			} else {
				cleanedKeys.add(kv.getKey());
			}
			
		}
		return cleanedKeys;
	}

	public static <K,V> Map<K,V> stripEmptyCDKProperties(Map<K,V> props){
		Map<K,V> cleaned = new LinkedHashMap<>(props);
		if (cleaned.get(CDKConstants.REMARK)==null)
			cleaned.remove(CDKConstants.REMARK);
		if (cleaned.get(CDKConstants.TITLE)==null)
			cleaned.remove(CDKConstants.TITLE);

		return cleaned;
	}

	/**
	 * Some CDK readers intialize molcules with empty properties for e.g. CDKConstants.REMARK and CDKConstants.TITLE,
	 * this method removes these properties if empty
	 * @param mol the object 
	 */
	public static void stripEmptyCDKProperties(IAtomContainer mol) {
		if (mol.getProperty(CDKConstants.REMARK)==null)
			mol.removeProperty(CDKConstants.REMARK);
		if (mol.getProperty(CDKConstants.TITLE)==null)
			mol.removeProperty(CDKConstants.TITLE);
	}

	public static void stripBadProperties(IAtomContainer mol) {
		Set<Object> toRm = new HashSet<>();
		// go through each key and check its a valid one
		for (Object k : mol.getProperties().keySet()) {
			if (! (k instanceof String)) {
				toRm.add(k);
			} else {
				String key = (String) k;
				if (key.startsWith("org.openscience")) {
					toRm.add(k);
				}
			}
		}
		// Remove if any offenders
		for (Object k : toRm) {
			mol.removeProperty(k);
		}
	}

	public static class InChIHandler {
		private final static String FAILED_INCHI_GEN = "failed"; 
		private static InChIHandler instance;
		private InChIGeneratorFactory igf;
		private InChIHandler(){}

		public static InChIHandler getInstance(){
			if (instance != null)
				return instance;
			LOGGER.debug("Setting up the InChIHandler");
			
			instance = new InChIHandler();
			// Here peforms the actual setup of the inchi generator factory
			try {
				instance.igf = InChIGeneratorFactory.getInstance();
			} catch (Exception | Error e){
				LOGGER.error("Could not instantiate the InChIGeneratorFactory for this system", e);
			}
			// Also check with a simple structure, as there might be a failure when running due to missing native code
			try {
				instance.igf.getInChIGenerator(ChemUtils.makeCCC());
			} catch (Exception | Error e){
				LOGGER.error("Failed generating inchi for test-structure",e);
				instance.igf = null; // Set back to null, which is the way we check if available or not
			}
			return instance;
		}

		public boolean isAvailable(){
			return igf != null;
		}

		public InChIGenerator getGenerator(IAtomContainer mol)throws IllegalStateException, CDKException {
			if (igf == null)
				throw new IllegalStateException("InChI generation not possible on this system");
			return igf.getInChIGenerator(mol);
		}
	}
	private static InChIHandler handler;

	public synchronized static void setupInChIGenerator() throws IllegalStateException {
		LOGGER.debug("Called setupInChIGenerator");
		if (handler == null)
			handler = InChIHandler.getInstance();
		if (!handler.isAvailable())
			throw new IllegalStateException("InChI generation not possible for this setup - it requires native libraries and running as root");
	}

	public synchronized static void setInChIProperties(IAtomContainer mol) 
		throws IllegalStateException {
		LOGGER.debug("Called setInChIProperties");
		if (handler == null)
			setupInChIGenerator();
		
		try {
			InChIGenerator gen = handler.getGenerator(mol);
			mol.setProperty(Constants.INCHI, gen.getInchi());
			mol.setProperty(Constants.INCHIKEY, gen.getInchiKey());
		} catch (CDKException e) {
			LOGGER.debug("Failed generating InChI for molecule",e);
			mol.setProperty(Constants.INCHI, InChIHandler.FAILED_INCHI_GEN);
			mol.setProperty(Constants.INCHIKEY, InChIHandler.FAILED_INCHI_GEN);
		}
	}

	/**
	 * Attempts to initialize the CDK InChIGeneratorFactory in a safe manner
	 * @return <code>true</code> if InChI can be generated, <code>false</code> otherwise
	 */
	public static boolean isInChIAvailable(){
		LOGGER.debug("Called isInChIAvailable");
		if (handler == null)
			handler = InChIHandler.getInstance();
		return handler.isAvailable();
	}


}

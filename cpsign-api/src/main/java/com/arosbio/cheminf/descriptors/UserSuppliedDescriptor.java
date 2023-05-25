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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.Experimental;
import com.arosbio.commons.FuzzyMatcher;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.BooleanConfig;
import com.arosbio.commons.config.EnumConfig;
import com.arosbio.commons.config.StringListConfig;
import com.arosbio.commons.mixins.Aliased;
import com.arosbio.commons.mixins.Described;
import com.arosbio.data.MissingValueFeature;
import com.arosbio.data.SparseFeature;
import com.arosbio.data.SparseFeatureImpl;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.DataSink;
import com.arosbio.io.DataSource;
import com.arosbio.ml.io.MetaFileUtils;

/**
 * This descriptor will take properties from the {@link org.openscience.cdk.interfaces.IAtomContainer IAtomContainer} objects and parse as features
 * that can be used in the modeling.  
 * @author staffan
 *
 */
@Experimental
public class UserSuppliedDescriptor implements ChemDescriptor, Described, Aliased {
	
	public static enum SortingOrder {
		UNMODIFIED ("unmodified"), ALPHABETICAL("alphabetical"), REVERSE_ALPHABETICAL ("reverseAlphabetical");
		
		private final String name;
		
		private SortingOrder(String name) {
			this.name = name;
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(UserSuppliedDescriptor.class);
	private static final String DESCRIPTOR_NAME = "UserSuppliedDescriptor";
	private static final String[] DESCRIPTOR_ALIASES = new String[] {"Supplied"};
	private static final String META_FILE_NAME = "meta.json";
	private static final String DESCRIPTOR_NAME_KEY = "descriptorName";
	private static final String DESCRIPTOR_PROPERTIES_LIST_KEY = "suppliedDescriptorPropertyNames";
	private static final String DESCRIPTION_OF_DESCRIPTOR = 
			"Descriptors that the user can supply from outside of CPSign. These descriptors should "
					+ "be specified (with names) in the input file(s) or as properties set on the IAtomContainer "
					+ "object if used by the API. Property names are case-insensitive as all names are converted "
					+ "to uppercase before usage. E.g.: 'supplied:solubility,my_descriptor'"
					+ "The property \"all\" is reserved and denotes "
					+ "\"all available properties\", this should be used in conjunction with discarding of certain "
					+ "properties using a \"-\" character before the property name (such as the property holding the endpoint you wish to model): e.g.: '"
					+ "all,-target_value,-comment,-compound_ID'. The final list of properties will be decided once "
					+ "the first compound has been processed. Thus the usage of a \"-\" in the beginning of properties-names are not allowed. "
					+ "N.B. all values must be numerical values, e.g. encode "
					+ "boolean properties as 0 or 1, categorical properties without internal ordering can be encoded "
					+ "using one-hot notation etc.";

	private List<String> propertiesUsedAsDescriptors = new ArrayList<>();
	private boolean descriptorInitialized = false;
	private boolean allowMissingValues = true;
	private transient SortingOrder sorting = SortingOrder.UNMODIFIED;

	// TMP stuff before first molecule has been processed
	/**
	 * The useAll variable also denotes weather a processing of the
	 * molecule needs to be performed, after the first molecule this should be
	 * set back to false and the properties should be fixed from that point!
	 */
	private boolean useAll = false;
	private List<String> propertiesToExclude = new ArrayList<>();

	public UserSuppliedDescriptor() {}

	public UserSuppliedDescriptor(String property) {
		parseAndSetDescriptors(Arrays.asList(property));
	}

	public UserSuppliedDescriptor(String... properties) {
		this(Arrays.asList(properties));
	}
	
	public UserSuppliedDescriptor(List<String> properties) {
		parseAndSetDescriptors(properties);
	}
	
	/**
	 * Get a descriptor using <b>all</b> available properties found in the first molecule,
	 * except possibly for a list that should be excluded. Note that this list should
	 * be non-empty and <b>at least include the endpoint activity</b> (otherwise that will be
	 * included as a feature in the dataset!)
	 * @param toExclude molecule properties that should be excluded (<b>not!</b> with the '-' sign to signify removal of properties)
	 * @return an instance of {@link UserSuppliedDescriptor}
	 */
	public static UserSuppliedDescriptor allPropertiesExcluding(List<String> toExclude) {
		UserSuppliedDescriptor desc = new UserSuppliedDescriptor();
		desc.useAll = true;
		if (toExclude != null) {
			for (String ex : toExclude) {
				desc.propertiesToExclude.add(ex.toUpperCase());
			}
		}
		return desc;
	}

	/**
	 * Set the properties to use
	 * @param properties properties to use
	 * @throws IllegalStateException If the descriptor is already initialized and doesn't allow any changes
	 */
	public void setPropertyNames(List<String> properties) throws IllegalStateException {
		if (descriptorInitialized) {
			LOGGER.debug("Tried setting new property names but descriptor has been initialized already");
			throw new IllegalStateException("ChemDescriptor has been initalized - no more changes allowed");
		}
		propertiesUsedAsDescriptors.clear();
		propertiesUsedAsDescriptors.addAll(properties);
	}

	public boolean requires3DCoordinates() {
		return false;
	}

	/**
	 * If missing values should either result in failure at descriptor-calculation or if a
	 * missing-value-feature should be added so that it can later be handled with imputation
	 * or filtering. 
	 * @param allowMissingValues <code>true</code> if missing properties are allowed, <code>false</code> otherwise
	 */
	public void setAllowMissingValues(boolean allowMissingValues) {
		this.allowMissingValues = allowMissingValues;
	}
	
	public boolean getAllowMissingValues() {
		return allowMissingValues;
	}
	/**
	 * 
	 * @return A copy of the list of properties
	 */
	public List<String> getPropertyNames(){
		return new ArrayList<>(propertiesUsedAsDescriptors);
	}

	@Override
	public void initialize() {
		descriptorInitialized = true;
		
		resolveSorting();
	}
	
	private void resolveSorting() {
		if (!propertiesUsedAsDescriptors.isEmpty()) {
			if (sorting == SortingOrder.ALPHABETICAL) {
				Collections.sort(propertiesUsedAsDescriptors);
			} else if (sorting == SortingOrder.REVERSE_ALPHABETICAL) {
				Collections.sort(propertiesUsedAsDescriptors, Collections.reverseOrder());
			}
		}
	}

	@Override
	public boolean isReady() {
		return descriptorInitialized;
	}

	private void assertChangesAllowedOrFail() throws IllegalStateException {
		if (descriptorInitialized) {
			LOGGER.debug("Tried making changes to ChemDescriptor after initialized had been called - failing");
			throw new IllegalStateException("ChemDescriptor has been initialized - no changes allowed");
		}
	}

	private void assertInitialized() throws IllegalStateException {
		if (!descriptorInitialized) {
			LOGGER.debug("ChemDescriptor not initialized yet, but called method requiring initialization");
			throw new IllegalStateException("ChemDescriptor not initialized");
		}
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String,Object> props = new HashMap<>();
		props.put(DESCRIPTOR_NAME_KEY, DESCRIPTOR_NAME);
		props.put(DESCRIPTOR_PROPERTIES_LIST_KEY, propertiesUsedAsDescriptors);
		return props;
	}

	public static final List<String> CONFIG_PROPERTIES_NAMES = Arrays.asList("properties", "props");
	public static final List<String> CONFIG_ALLOW_MISSING_NAMES = Arrays.asList("allowMissingValues", "allowMissing");
	public static final List<String> CONFIG_SORT_ORDER_NAMES = Arrays.asList("sortingOrder");
	@Override
	public List<ConfigParameter> getConfigParameters() {
		List<ConfigParameter> params =  new ArrayList<>();
		params.add(new StringListConfig.Builder(CONFIG_PROPERTIES_NAMES, null).build());
		params.add(new BooleanConfig.Builder(CONFIG_ALLOW_MISSING_NAMES, true).build());
		params.add(new EnumConfig.Builder<>(CONFIG_SORT_ORDER_NAMES,EnumSet.allOf(SortingOrder.class),SortingOrder.UNMODIFIED)
			.description("Sort the descriptors in alphabetical or reversed alphabetical order").build());
		return params;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setConfigParameters(Map<String, Object> params) 
			throws IllegalStateException, IllegalArgumentException {
		assertChangesAllowedOrFail();

		List<String> argsToConfig = new ArrayList<>();
		for (Map.Entry<String, Object> kv: params.entrySet()) {
			if (CollectionUtils.containsIgnoreCase(CONFIG_PROPERTIES_NAMES, kv.getKey())) {

				if (kv.getValue() instanceof List) {
					// We got a fixed list already - nice! 
					for (Object val: (List<Object>)kv.getValue()) {
						argsToConfig.add(val.toString().toUpperCase().trim());
					}
				}

				else {
					String upperCaseValue = kv.getValue().toString().toUpperCase().trim();
					if (upperCaseValue.contains(",")) {
						// if a list
						for (String s : upperCaseValue.split(",")) {
							argsToConfig.add(s);
						}
					} else {
						// if a single value
						argsToConfig.add(upperCaseValue);
					}
				}

			} else if (CollectionUtils.containsIgnoreCase(CONFIG_ALLOW_MISSING_NAMES, kv.getKey())){
				try {
					allowMissingValues = TypeUtils.asBoolean(kv.getValue());
				} catch (IllegalArgumentException e) {
					throw new IllegalArgumentException("ChemDescriptor "+ DESCRIPTOR_NAME + " could not convert input (param="+kv.getKey() + ") to boolean : " + kv.getValue());
				}
			} else if (CollectionUtils.containsIgnoreCase(CONFIG_SORT_ORDER_NAMES, kv.getKey())) {
				try {
					Collection<Pair<List<String>,SortingOrder>> possibleValues = new ArrayList<>();
					for (SortingOrder so : SortingOrder.values()) {
						possibleValues.add(ImmutablePair.of(Arrays.asList(so.name), so));
					}
					
					sorting = new FuzzyMatcher().matchPairs(possibleValues, ""+kv.getValue());
				} catch (Exception e) {
					throw new IllegalArgumentException("ChemDescriptor "+ DESCRIPTOR_NAME + " could not set a valid sorting order for input: " + kv.getValue());
				}
			} else {
				throw new IllegalArgumentException("ChemDescriptor " + DESCRIPTOR_NAME + " got non-accepted parameter: " + kv.getKey());
			}
		}

		parseAndSetDescriptors(argsToConfig);
	}

	private void parseAndSetDescriptors(List<String> args) {
		for (String p : args) {
			String pUpp = p.toUpperCase();
			if (pUpp.equals("ALL")) {
				useAll = true;
			} else if (pUpp.startsWith("-")) {
				propertiesToExclude.add(pUpp.substring(1));
			} else {
				propertiesUsedAsDescriptors.add(pUpp);
			}
		}
		LOGGER.debug("Using all={}, excluding={}, including={}",useAll,propertiesToExclude,propertiesUsedAsDescriptors);
	}

	@Override
	public String getName() {
		return DESCRIPTOR_NAME;
	}
	
	@Override
	public String[] getAliases() {
		return DESCRIPTOR_ALIASES;
	}
	
	@Override
	public List<String> getFeatureNames(){
		return new ArrayList<>(propertiesUsedAsDescriptors);
	}
	
	public SortingOrder getSortingOrder() {
		return sorting;
	}
	
	/**
	 * Note: calling this method after {@link #initialize()} will have no effect
	 * @param order The sorting that should be applied to the descriptors
	 */
	public void setSortingOrder(SortingOrder order) {
		this.sorting = order;
	}

	@Override
	public String getDescription() {
		return DESCRIPTION_OF_DESCRIPTOR;
	}

	@Override
	public UserSuppliedDescriptor clone() {
		UserSuppliedDescriptor c = new UserSuppliedDescriptor(new ArrayList<>(propertiesUsedAsDescriptors));
		c.sorting = sorting;
		return c;
	}

	public String toString() {
		return "User-supplied descriptors with properties="+propertiesUsedAsDescriptors;
	}

	@Override
	public boolean hasFixedLength() {
		return true;
	}

	@Override
	public int getLength() {
		assertInitialized();
		return propertiesUsedAsDescriptors.size();
	}

	@Override
	public List<SparseFeature> calculateDescriptors(IAtomContainer molecule)
			throws DescriptorCalcException, IllegalStateException {
		assertInitialized();
		Map<String,Object> molProperties = standardize(molecule.getProperties());
		List<SparseFeature> features = new ArrayList<>();

		if (useAll) {
			processFirstMolecule(molProperties);
		}


		for (int i=0; i<propertiesUsedAsDescriptors.size(); i++) {
			String prop = propertiesUsedAsDescriptors.get(i);
			if (!molProperties.containsKey(prop)) {
				LOGGER.debug("Property '" +prop + "' not found in the molecule, setting as MissingValueFeature");
				if (allowMissingValues)
					features.add(new MissingValueFeature(i));
				else
					throw new DescriptorCalcException("Failed computing descriptors using " + getName() + ": for record id {"
				+ CPSignMolProperties.getRecordIndex(molecule) +"} due to missing property '" +prop + "'");
			} else {
				Object value = molProperties.get(prop);
				try {
					features.add(new SparseFeatureImpl(i, TypeUtils.asDouble(value)));
				} catch (NumberFormatException e) {
					LOGGER.debug("Property " + prop + " could not be converted to a numerical value, was: " + value);
					if (allowMissingValues)
						features.add(new MissingValueFeature(i));
					else
						throw new DescriptorCalcException("Failed computing descriptors using " + getName() + ": for record id {"+ 
								CPSignMolProperties.getRecordIndex(molecule) + "} due to property '" +prop + "' could not be converted to a numerical value, was: " + value);
				}
			}
		}
		
		return features;
	}

	private static Map<String,Object> standardize(Map<Object,Object> properties){
		Map<String, Object> standard = new LinkedHashMap<>();

		for (Map.Entry<Object, Object> kv : properties.entrySet()) {
			String k = kv.getKey().toString();
			standard.put(k.toUpperCase().trim(), kv.getValue());
		}

		return standard;
	}

	private void processFirstMolecule(Map<String,Object> molProperties) {
		LOGGER.debug("ALL properties has been specified, with exclusion of the following: {}", propertiesToExclude);

		// Keys are already standardized
		for (Map.Entry<String, Object> kv : molProperties.entrySet()) {
			String key = kv.getKey();
			if (key.startsWith(CPSignMolProperties.Constants.CDK_PREFIX.toUpperCase())) {
				continue; // Skip this
			} else if (key.startsWith(CPSignMolProperties.Constants.PROPERTY_PREFIX.toUpperCase())) {
				continue;
			} else if (key.startsWith(CPSignMolProperties.Constants.RECORD_INDEX.toUpperCase())){
				continue;
			} else if (propertiesToExclude.contains(key)) {
				continue;
			} else {
				propertiesUsedAsDescriptors.add(key);
			}
		}
		
		LOGGER.debug("List or properties to include: {}", propertiesUsedAsDescriptors);
		
		resolveSorting();

		LOGGER.debug("Final list of properties that will be used: {}", propertiesUsedAsDescriptors);
		// We've finished processing the molecule and can now set the useAll to false
		useAll = false;
	}

	@Override
	public List<SparseFeature> calculateDescriptorsAndUpdate(IAtomContainer molecule)
			throws DescriptorCalcException, IllegalStateException {
		return calculateDescriptors(molecule);
	}

	@Override
	public void saveDescriptorToSink(DataSink sink, String basePath, EncryptionSpecification spec)
			throws IOException, InvalidKeyException, IllegalStateException {
		try (OutputStream ostream = sink.getOutputStream(basePath+'/'+META_FILE_NAME)){
			Map<String,Object> props = getProperties();
			LOGGER.debug("Writing UserSuppliedDescriptor properties to meta.json: {}", props);
			MetaFileUtils.writePropertiesToStream(ostream, props);
		} catch (IOException e) {
			LOGGER.debug("Failed writing properties for UserSupplied-descriptor: {}", getName(),e);
			throw new IOException("Failed saving descriptor " + getName() + " to output");
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void loadDescriptorFromSource(DataSource source, String path, EncryptionSpecification spec)
			throws IOException, InvalidKeyException {
		try (InputStream istream = source.getInputStream(path+'/' + META_FILE_NAME)){
			Map<String,Object> props = MetaFileUtils.readPropertiesFromStream(istream);
			LOGGER.debug("Loaded UserSuppliedDescriptor properties: " + props);
			if (! props.containsKey(DESCRIPTOR_PROPERTIES_LIST_KEY)) {
				LOGGER.debug("UserSuppliedDescriptor descriptor not properly saved, cannot load the descriptor (doesn't contain the mapping {{}}",DESCRIPTOR_PROPERTIES_LIST_KEY);
				throw new IOException("Failed loading ChemDescriptor");
			}
			Object listAsObj = props.get(DESCRIPTOR_PROPERTIES_LIST_KEY);
			if (listAsObj instanceof List)
				propertiesUsedAsDescriptors = (List<String>) props.get(DESCRIPTOR_PROPERTIES_LIST_KEY);
			else {
				LOGGER.debug("ChemDescriptor list not saved as a list, but a {}", listAsObj.getClass());
				throw new IOException("Failed loading ChemDescriptor");
			}
		} 

		initialize();
	}

}

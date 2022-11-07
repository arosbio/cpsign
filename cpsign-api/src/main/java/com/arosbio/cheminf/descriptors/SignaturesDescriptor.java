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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.signature.AtomSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.CDKConfigureAtomContainer;
import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.EnumConfig;
import com.arosbio.commons.config.IntegerConfig;
import com.arosbio.commons.mixins.Aliased;
import com.arosbio.commons.mixins.Described;
import com.arosbio.commons.mixins.HasID;
import com.arosbio.commons.mixins.Named;
import com.arosbio.data.SparseFeature;
import com.arosbio.data.SparseFeatureImpl;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.encryption.utils.EncryptUtils;
import com.arosbio.encryption.utils.EncryptUtils.EncryptionStatus;
import com.arosbio.io.DataSink;
import com.arosbio.io.DataSource;
import com.arosbio.io.StreamUtils;
import com.arosbio.ml.io.MetaFileUtils;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.google.common.collect.Range;

public class SignaturesDescriptor implements ChemDescriptor, Described, Aliased {

	public static int DEFAULT_SIGN_START_HEIGHT = 1;
	public static int DEFAULT_SIGN_END_HEIGHT = 3;

	private static final Logger LOGGER = LoggerFactory.getLogger(SignaturesDescriptor.class);
	private static final String SIGNATURES_META_FILE_NAME = "meta.json";
	private static final String SIGNATURES_FILE_NAME = "signatures.txt";
	private static final int SIGNATURES_MAX_HEIGHT = 10;

	private static final String PROPERTY_GENERATOR_TYPE_ID_KEY = "signaturesGeneratorID";
	private static final String PROPERTY_GENERATOR_TYPE_NAME_KEY = "signaturesGeneratorName";
	private static final String PROPERTY_GENERATOR_VECTOR_ID_KEY = "signaturesVectorTypeID";
	private static final String PROPERTY_GENERATOR_VECTOR_NAME_KEY = "signaturesVectorTypeName";

//	public static final long DESCRIPTOR_ID = 1;
	public static final String DESCRIPTOR_NAME = "Signatures";
	public static final String[] DESCRIPTOR_ALIASES = new String[] {"SignaturesDescriptor", "FaulonSignatures"};
	public static final String DESCRIPTOR_INFO = "The signature molecular descriptor described in Faulon J-L, Collins MJ, Carr RD. The signature molecular descriptor. 4. Canonizing molecules using extended valence sequences. J Chem Inf Comput Sci. 2004;44: 427–436. and implemented in the CDK.";

	/**
	 * Saves the index for each signature, note that indices starts at 0!
	 */
	private LinkedHashMap<String, Integer> signaturesHelper = new LinkedHashMap<>();
	private int startHeight=1, endHeight=3;
	private SignatureType signaturesType = SignatureType.STANDARD;
	private VectorType vectorType = VectorType.COUNT;
	private boolean descriptorInitialized = false;

	public static enum SignatureType implements HasID, Named {
		STANDARD(1, "standard"), STEREO (2, "stereo");

		final int id;
		final String name;

		private SignatureType(int id, String name) {
			this.id = id;
			this.name = name;
		}

		public static SignatureType getType(long id) {
			for (SignatureType e : values()) {
				if (e.id==id) return e;
			}
			throw new IllegalArgumentException("SignatureType {" + id + "} not supported");
		}

		public static SignatureType getType(String text) throws IllegalArgumentException {
			try {
				return getType(Integer.parseInt(text));
			} catch (NumberFormatException e){
				// was not a id 
			}

			String lowerCase = text.toLowerCase();
			for (SignatureType e : values()) {
				if (e.name.equals(lowerCase)) return e;
			}
			throw new IllegalArgumentException("SignatureType {" + text + "} not supported");
		}

		@Override
		public int getID() {
			return id;
		}

		@Override
		public String getName() {
			return name;
		}
		
		public String toString() {
			return String.format("(%d) %s", id, name); 
		}
	}

	public static enum VectorType implements HasID, Named {
		COUNT(1,"counts", "count"), BINARY(2, "binary", "bit");

		final int id;
		final String[] names;

		private VectorType(int id, String... names) {
			this.id = id;
			this.names = names;
		}

		public static VectorType getType(long id) {
			for (VectorType e : values()) {
				if (e.id==id) return e;
			}
			throw new IllegalArgumentException("VectorType {" + id + "} not supported");
		}

		public static VectorType getType(String text) throws IllegalArgumentException {
			try {
				return getType(Integer.parseInt(text));
			} catch (NumberFormatException e){
				// was not a id 
			}

//			String lowerCase = text.toLowerCase();
			for (VectorType e : values()) {
				for (String n : e.names)
					if (n.equalsIgnoreCase(text)) return e;
			}
			throw new IllegalArgumentException("VectorType {" + text + "} not supported");
		}
		
		@Override
		public int getID() {
			return id;
		}

		@Override
		public String getName() {
			return names[0];
		}
		
		public String toString() {
			return String.format("(%d) %s/%s", id, names[0],names[1]); 
		}
	}

	/**
	 * Using default heights of 1 to 3
	 */
	public SignaturesDescriptor() {
	}

	public SignaturesDescriptor(int start, int end) {
		this.startHeight = start;
		this.endHeight = end;
		assertStartEndAreCorrect();
	}

	public SignaturesDescriptor(int start, 
			int end, SignatureType type, VectorType vector) {
		this.startHeight = start;
		this.endHeight = end;
		this.signaturesType = type;
		this.vectorType = vector;
		assertStartEndAreCorrect();
	}

	/**
	 * Call this method when changes have been done, will throw 
	 * an {@link IllegalStateException} if start and end-heights 
	 * are not correct (i.e. end >= start and 0 <= heights <= SIGNATURES_MAX_HEIGHT)
	 */
	private void assertStartEndAreCorrect() {
		if (endHeight < startHeight)
			throw new IllegalStateException("Signatures end height must be >= signatures start height");
		if (startHeight > SIGNATURES_MAX_HEIGHT || startHeight < 0)
			throw new IllegalStateException("Signatures start height must be in the range [0.." + SIGNATURES_MAX_HEIGHT+']');
		if (endHeight > SIGNATURES_MAX_HEIGHT || endHeight < 0)
			throw new IllegalStateException("Signatures end height must be in the range [0.." + SIGNATURES_MAX_HEIGHT+']');
	}

	// ---------------------------------------------------------------------
	// GETTERS AND SETTERS
	// ---------------------------------------------------------------------

	@Override
	public void initialize() {
		descriptorInitialized = true;
	}

	@Override
	public boolean isReady() {
		return descriptorInitialized;
	}

	private void assertChangesAllowedOrFail() throws IllegalStateException {
		if (descriptorInitialized) {
			LOGGER.debug("Tried making changes to SignaturesDescriptor after initalized had been called - failing");
			throw new IllegalStateException("ChemDescriptor has been initialized - no changes allowed");
		}
	}

	private void assertInitialized() throws IllegalStateException {
		if (!descriptorInitialized) {
			LOGGER.debug("ChemDescriptor not inialized yet, but called method requiring initialization");
			throw new IllegalStateException("ChemDescriptor not initialized");
		}
	}

	public boolean requires3DCoordinates() {
		return false;
	}

	public SignatureType getSignatureType() {
		return signaturesType;
	}

	public void setSignaturesType(SignatureType type) throws IllegalStateException {
		assertChangesAllowedOrFail();
		if (type == SignatureType.STEREO && ! CPSignMolProperties.isInChIAvailable()){
			LOGGER.debug("Tried to set to calculate stereo signatures - but cannot compute InChI on current platform - failing");
			throw new IllegalArgumentException("Cannot calculate stereo signatures on the current machine - need to run on a platform with native support");
		}
		this.signaturesType = type;
	}

	public VectorType getVectorType() {
		return vectorType;
	}

	public void setVectorType(VectorType type) throws IllegalStateException {
		assertChangesAllowedOrFail();
		this.vectorType = type;
	}

	public int getStartHeight() {
		return startHeight;
	}

	public void setStartHeight(int startHeight) throws IllegalStateException {
		assertChangesAllowedOrFail();
		this.startHeight = startHeight;
	}

	public int getEndHeight() {
		return endHeight;
	}

	public void setEndHeight(int endHeight) throws IllegalStateException {
		assertChangesAllowedOrFail();
		this.endHeight = endHeight;
	}

	public Iterable<String> getSignatures() {
		return signaturesHelper.keySet();
	}

	public void setSignatures(List<String> signatures) throws IllegalStateException {
		assertChangesAllowedOrFail();
		signaturesHelper.clear();
		for (int i = 0; i<signatures.size(); i++) {
			signaturesHelper.put(signatures.get(i), i);
		}
	}

	@Override
	public String getName() {
		return DESCRIPTOR_NAME;
	}
	
	@Override
	public String[] getAliases() {
		return DESCRIPTOR_ALIASES;
	}

	/**
	 * Note - this will potentially be a huge list of signatures!
	 */
	@Override
	public List<String> getFeatureNames(){
		return new ArrayList<>(signaturesHelper.keySet());
	}

	public String getDescription() {
		return DESCRIPTOR_INFO;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder("SignaturesDescriptor properties=").append(getProperties());
		if (!signaturesHelper.isEmpty())	{
			sb.append(", with ").append(signaturesHelper.size()).append(" signatures");
		}
		return sb.toString();
	}

	@Override
	public boolean hasFixedLength() {
		return false;
	}

	@Override
	public int getLength() {
		return signaturesHelper.size();
	}

	@Override
	public SignaturesDescriptor clone() {
		SignaturesDescriptor clone = new SignaturesDescriptor(startHeight, endHeight);
		clone.setSignaturesType(signaturesType);
		clone.setVectorType(vectorType);
		// Strings are immutable so shallow copy should not be an issue
		clone.signaturesHelper = new LinkedHashMap<>(signaturesHelper);
		LOGGER.debug("Cloned SignaturesDescriptor, with {} signatures",clone.signaturesHelper.size());
		return clone;
	}

	public int getNumSignatures() {
		return signaturesHelper.size();
	}

	public Map<String, Object> getProperties(){
		Map<String, Object> params = new HashMap<>();
		params.put(PropertyNameSettings.SIGNATURES_START_HEIGHT_KEY, startHeight);
		params.put(PropertyNameSettings.SIGNATURES_END_HEIGHT_KEY, endHeight);

		params.put(PROPERTY_GENERATOR_TYPE_ID_KEY, signaturesType.id);
		params.put(PROPERTY_GENERATOR_TYPE_NAME_KEY, signaturesType.name);
		params.put(PROPERTY_GENERATOR_VECTOR_ID_KEY, vectorType.id);
		params.put(PROPERTY_GENERATOR_VECTOR_NAME_KEY, vectorType.names[0]);

		return params;
	}

	// ---------------------------------------------------------------------
	// CONFIG PARAMETERS
	// ---------------------------------------------------------------------

	public static final List<String> CONFIG_SIGNATURES_TYPE = Arrays.asList("signatureType");
	public static final List<String> CONFIG_VECTOR_TYPE = Arrays.asList("vectorType");
	public static final List<String> CONFIG_SIGNATURES_START_HEIGHT = Arrays.asList("startHeight","minHeight");
	public static final List<String> CONFIG_SIGNATURES_END_HEIGHT = Arrays.asList("endHeight","maxHeight","stopHeight");

	@Override
	public List<ConfigParameter> getConfigParameters() {
		List<ConfigParameter> params = new ArrayList<>();
		params.add(new IntegerConfig.Builder(CONFIG_SIGNATURES_START_HEIGHT,DEFAULT_SIGN_START_HEIGHT)
			.range(Range.closed(0, SIGNATURES_MAX_HEIGHT))
			.description("Smallest 'height', i.e. number of bonds that should be considered for atom neighbours").build());
		params.add(new IntegerConfig.Builder(CONFIG_SIGNATURES_END_HEIGHT,DEFAULT_SIGN_END_HEIGHT)
			.range(Range.closed(0, SIGNATURES_MAX_HEIGHT))
			.description("Largest 'height', i.e. number of bonds that should be considered for atom neighbours").build());
		params.add(new EnumConfig.Builder<>(CONFIG_SIGNATURES_TYPE, EnumSet.allOf(SignatureType.class),SignatureType.STANDARD)
			.description("Whether stereo information should be captured in the signatures")
			.build());
		params.add(new EnumConfig.Builder<>(CONFIG_VECTOR_TYPE, EnumSet.allOf(VectorType.class), VectorType.COUNT)
			.description("Choose a binary (i.e. presence or absence) or count type of vector")
			.build());
		return params;
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) 
			throws IllegalStateException, IllegalArgumentException {
		assertChangesAllowedOrFail();

		for (Map.Entry<String, Object> kv : params.entrySet()) {

			// Start height
			if (CollectionUtils.containsIgnoreCase(CONFIG_SIGNATURES_START_HEIGHT, kv.getKey())) {
				int pStart = -1;
				try {
					pStart = TypeUtils.asInt(kv.getValue());
				} catch (NumberFormatException ne) {
					throw new IllegalArgumentException("Parameter "+kv.getKey()+" could not be parsed as a numerical value"); 
				}
				if (pStart < 0 || pStart > SIGNATURES_MAX_HEIGHT) {
					throw new IllegalArgumentException("Parameter " + kv.getKey() + " must be within range [0.."+SIGNATURES_MAX_HEIGHT+']');
				}
				startHeight = pStart;
				LOGGER.debug("Using new signatures start height = {}", startHeight);
			}

			// End height
			else if (CollectionUtils.containsIgnoreCase(CONFIG_SIGNATURES_END_HEIGHT, kv.getKey())) {
				int pEnd = -1;
				try {
					pEnd = TypeUtils.asInt(kv.getValue());
				} catch(NumberFormatException ne) {
					throw new IllegalArgumentException("Parameter " + kv.getKey() + " could not be parsed as a numerical value"); 
				}
				if (pEnd < 0 || pEnd > SIGNATURES_MAX_HEIGHT) {
					throw new IllegalArgumentException("Parameter " + kv.getKey() + " must be within range [0.."+SIGNATURES_MAX_HEIGHT+']');
				}
				endHeight = pEnd;
				LOGGER.debug("Using new signatures end height = {}", endHeight);
			}

			else {
				try {
					if (CollectionUtils.containsIgnoreCase(CONFIG_SIGNATURES_TYPE,kv.getKey())) {
						if (kv.getValue() instanceof SignatureType) {
							setSignaturesType((SignatureType) kv.getValue());
						} else {
							setSignaturesType(SignatureType.getType(kv.getValue().toString()));
						} 
						LOGGER.debug("Using new signatures type = {}", signaturesType);
					}
					else if (CollectionUtils.containsIgnoreCase(CONFIG_VECTOR_TYPE,kv.getKey())) {
						if (kv.getValue() instanceof VectorType) {
							vectorType = (VectorType) kv.getValue();
						} else {
							vectorType = VectorType.getType(kv.getValue().toString());
						} 
						LOGGER.debug("Using new vector type = {}", vectorType);
					}

				} catch (Exception e) {
					throw new IllegalArgumentException("Invalid input for parameter " + kv.getKey() + ": " + kv.getValue());
				}
			}
		}

	}

	// ---------------------------------------------------------------------
	// DESCRIPTOR CALCULATIONS
	// ---------------------------------------------------------------------

	public static class SignatureInfo {
		private final String signature;
		private final int signatureHeight;
		private final int signatureIndex;
		private final Set<Integer> centerAtoms = new HashSet<>();

		private SignatureInfo(String signature, int height, int signatureIndex){
			this.signature = signature;
			this.signatureHeight = height;
			this.signatureIndex = signatureIndex;
		}

		public int getNumOccurrences(){
			return centerAtoms.size();
		}

		private void addOriginatesFrom(int atom){
			centerAtoms.add(atom);
		}

		public Set<Integer> getCenterAtoms(){
			return centerAtoms;
		}

		public int getHeight(){
			return signatureHeight;
		}

		public String getSignature(){
			return signature;
		}

		public int getSignatureIndex() {
			return signatureIndex;
		}

		public String toString() {
			return String.format(Locale.ENGLISH, "%s index=%s height=%s",
				signature, signatureIndex,signatureHeight);
		}

	}

	@Override
	public List<SparseFeature> calculateDescriptors(IAtomContainer molecule) 
			throws DescriptorCalcException, IllegalStateException {
		if (signaturesHelper.isEmpty())
			throw new IllegalStateException("No signatures in the current descriptor - no data loaded yet");
		Map<String, Integer> signatures = generateSignatures(molecule);

		List<SparseFeature> nodes = new ArrayList<>();

		for (Map.Entry<String, Integer> sign : signatures.entrySet()){
			Integer index = signaturesHelper.get(sign.getKey());
			if (index != null){ // This signature is known
				if (vectorType == VectorType.COUNT)
					nodes.add(new SparseFeatureImpl(index, sign.getValue()));
				else
					nodes.add(new SparseFeatureImpl(index, 1));
			} else {
				LOGGER.trace("Signature {} generated but not found in the global list", sign);
			}
		}
		Collections.sort(nodes);

		if (nodes.isEmpty())
			LOGGER.debug("No signatures descriptors found for molecule");

		return nodes;
	}

	@Override
	public List<SparseFeature> calculateDescriptorsAndUpdate(IAtomContainer molecule) 
			throws DescriptorCalcException, IllegalStateException {
		Map<String,Integer> signatures = generateSignatures(molecule);

		List<SparseFeature> nodes = new ArrayList<>();

		for (Map.Entry<String, Integer> sign : signatures.entrySet()){
			Integer index = signaturesHelper.get(sign.getKey());
			// If not known before
			if (index == null) {
				index = signaturesHelper.size();
				signaturesHelper.put(sign.getKey(), index);
			}
			if (vectorType == VectorType.COUNT)
				nodes.add(new SparseFeatureImpl(index, sign.getValue()));
			else
				nodes.add(new SparseFeatureImpl(index, 1));
			
		}
		Collections.sort(nodes);

		if (nodes.isEmpty())
			LOGGER.debug("No signatures descriptors found for molecule");

		return nodes;
	}

	public Map<String,SignatureInfo> generateSignaturesExtended(IAtomContainer mol)
			throws IllegalStateException, IllegalArgumentException, CDKException {
		assertInitialized();
		if (signaturesHelper.isEmpty())
			throw new IllegalStateException("No signatures in the current descriptor - no data loaded yet");

		CDKConfigureAtomContainer.configMolecule(mol);

		Map<String, SignatureInfo> allSignaturesInfo = new HashMap<>();

		// Generate signatures for the molecule
		int atomNr;
		String currentSignature;
		Map<IAtom, String> signsOfGivenHeight;

		for (int height = startHeight; height <= endHeight; height++){

			signsOfGivenHeight = generateSignatures(mol, height);

			for (Entry<IAtom, String> atom2sig: signsOfGivenHeight.entrySet()){

				currentSignature = atom2sig.getValue();
				atomNr = mol.indexOf(atom2sig.getKey());

				// Only care about the new signature if it exists in our models 
				if (! signaturesHelper.containsKey(currentSignature)){
					continue;
				}

				// Add info about the newly found signature
				SignatureInfo sigInfo = null;
				if (! allSignaturesInfo.containsKey(currentSignature)){
					sigInfo = new SignatureInfo(currentSignature, height, signaturesHelper.get(currentSignature));
					allSignaturesInfo.put(currentSignature, sigInfo);
				} else {
					sigInfo = allSignaturesInfo.get(currentSignature);

				}
				sigInfo.addOriginatesFrom(atomNr);

			}
		}

		return allSignaturesInfo;
	}

	public Map<String, Integer> generateSignatures(IAtomContainer molecule)
			throws IllegalArgumentException, IllegalStateException {
		assertInitialized();
		Map<String, Integer> molSignatures = new HashMap<>();

		for (int h=startHeight; h<= endHeight; h++){

			Map<String,Integer> signsCurrentHeight = new HashMap<>();
			for (IAtom atom : molecule.atoms()){

				String canonString = generateSignature(molecule, atom, h);

				if (canonString.isEmpty())
					continue;

				signsCurrentHeight.put(canonString, 
						signsCurrentHeight.getOrDefault(canonString,1) + 1);
			}
			boolean newSignaturesEncountered = false;
			for (String signature : signsCurrentHeight.keySet()) {
				if (!molSignatures.containsKey(signature)) {
					newSignaturesEncountered = true;
					molSignatures.put(signature, signsCurrentHeight.get(signature));
				}
			}
			if (!newSignaturesEncountered)
				return molSignatures;
		}
		return molSignatures;
	}



	private Map<IAtom, String> generateSignatures(IAtomContainer mol, int height) throws CDKException{
		Map<IAtom, String> sigs = new HashMap<>();

		for (IAtom atom : mol.atoms()){
			String canonString = generateSignature(mol, atom, height);

			if (canonString.isEmpty())
				continue;

			sigs.put(atom, canonString);
		}

		return sigs;
	}

	private String generateSignature(IAtomContainer mol, IAtom atom, int height) 
			throws IllegalArgumentException {
		if (signaturesType == SignatureType.STEREO)
			return new StereoAtomSignature(atom, height, mol).toCanonicalString();
		else
			return new AtomSignature(atom, height, mol).toCanonicalString();
	}


	// ---------------------------------------------------------------------
	// SAVING DATA
	// ---------------------------------------------------------------------

	private String getSignaturesJarBasePath(String basePath){
		if (basePath==null || basePath.isEmpty())
			return "/";
		return (basePath.endsWith("/") ? basePath : basePath + '/');
	}

	public void saveDescriptorToSink(DataSink sink, String basePath, EncryptionSpecification spec) 
			throws IOException, InvalidKeyException, IllegalStateException {

		if (signaturesHelper==null || signaturesHelper.isEmpty())
			throw new IllegalStateException("Signatures description generator is empty - no signatures to write");

		// create the directory
		String signaturesDir = getSignaturesJarBasePath(basePath);
		sink.createDirectory(signaturesDir);
		LOGGER.debug("Saving signatures to path={}",signaturesDir);

		// write meta.params
		Map<String, Object> params = getProperties();
		try (OutputStream metaStream = sink.getOutputStream(signaturesDir+SIGNATURES_META_FILE_NAME)){
			MetaFileUtils.writePropertiesToStream(metaStream, params);
		}
		sink.closeEntry();
		LOGGER.debug("written signature properties to jar: {}", params);

		// write signatures.txt
		try (OutputStream jos = sink.getOutputStream(signaturesDir+SIGNATURES_FILE_NAME);){
			if (spec!=null)
				writeSignaturesEncrypted(jos, spec);
			else
				writeSignatures(jos, false);
		}
		sink.closeEntry();
		LOGGER.debug("written signatures to sink");
	}

	/**
	 * Write the signatures to a stream
	 * @param stream Stream to write signatures to
	 * @param compress if gzip compression should be applied when written
	 * @throws IOException If an IO exception occurs while writing
	 */
	public void writeSignatures(OutputStream stream, boolean compress) throws IOException {
		if (compress)
			stream = new GZIPOutputStream(stream);
		writeSignatures(stream);
	}

	/**
	 * Write encrypted signatures to stream, following the specifications in the EncryptionSpecification
	 * @param stream the stream to write to
	 * @param encryption EncryptionSpecification used for encryption
	 * @throws InvalidKeyException If the {@link EncryptionSpecification} is invalid
	 * @throws IOException If an IO exception occurs while writing
	 */
	public void writeSignaturesEncrypted(OutputStream stream, EncryptionSpecification encryption) 
			throws InvalidKeyException, IOException {
		if (encryption==null)
			throw new InvalidKeyException("No encryption specification given - cannot encrypt signatures");
		try(
				OutputStream encryptedStream = encryption.encryptStream(stream);
				){
			writeSignatures(encryptedStream);
		}
	}

	private void writeSignatures(OutputStream stream) throws IOException {
		try(
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
				){
			for (String sign : signaturesHelper.keySet()) {
				writer.write(sign);
				writer.newLine();
			}
		}
	}

	// ---------------------------------------------------------------------
	// LOADING DATA
	// ---------------------------------------------------------------------

	public void loadDescriptorFromSource(DataSource source, String path, EncryptionSpecification spec) 
			throws IOException, InvalidKeyException {
		// Find the base directory for the Signatures
		String signaturesDir = getSignaturesJarBasePath(path); 
		LOGGER.debug("loading signatures from path={}",signaturesDir);

		// load the meta-data
		try(
				InputStream metaDataStream = source.getInputStream(signaturesDir+SIGNATURES_META_FILE_NAME);
				){
			Map<String,Object> properties = MetaFileUtils.readPropertiesFromStream(metaDataStream);

			LOGGER.debug("Props for signatures problem={}",properties);

			startHeight = TypeUtils.asInt(properties.get(PropertyNameSettings.SIGNATURES_START_HEIGHT_KEY));
			endHeight = TypeUtils.asInt(properties.get(PropertyNameSettings.SIGNATURES_END_HEIGHT_KEY));

			// Old model version!
			if (properties.containsKey(PropertyNameSettings.SIGNATURES_GENERATOR_KEY)) {
				String generator = (String) properties.get(PropertyNameSettings.SIGNATURES_GENERATOR_KEY);
				if (generator.toLowerCase().contains("stereo"))
					signaturesType = SignatureType.STEREO;
				else
					signaturesType = SignatureType.STANDARD;
				vectorType = VectorType.COUNT;
			}
			// New model version
			if (properties.containsKey(PROPERTY_GENERATOR_TYPE_ID_KEY)) {
				int loadedID = TypeUtils.asInt(properties.get(PROPERTY_GENERATOR_TYPE_ID_KEY));
				if (loadedID == SignatureType.STANDARD.id)
					signaturesType = SignatureType.STANDARD;
				else if (loadedID == SignatureType.STEREO.id)
					signaturesType = SignatureType.STEREO;
				else {
					LOGGER.debug("Property value not recognized for key: {}, value: {}", PROPERTY_GENERATOR_TYPE_ID_KEY, loadedID);
				}

			}
			if (properties.containsKey(PROPERTY_GENERATOR_VECTOR_ID_KEY)) {
				int loadedID = TypeUtils.asInt(properties.get(PROPERTY_GENERATOR_VECTOR_ID_KEY));
				if (loadedID == VectorType.COUNT.id)
					vectorType = VectorType.COUNT; 
				else if (loadedID == VectorType.BINARY.id)
					vectorType = VectorType.BINARY;
				else {
					LOGGER.debug("Property value not recognized for key: {}, value: {}", PROPERTY_GENERATOR_VECTOR_ID_KEY, loadedID);
				}
			}

		} catch (IOException e){
			LOGGER.debug("Could not read the signatures meta-file",e);
		}
		LOGGER.debug("Loaded signatures meta-file");

		// Load the actual signatures
		try(
				InputStream signs = source.getInputStream(signaturesDir+SIGNATURES_FILE_NAME);
				){
			readSignatures(signs, spec);
		}
		LOGGER.debug("Loaded signatures from source");

		initialize();
	}


	/**
	 * Over-layered method that don't require user to send "null" as EncryptionSpec if not encrypted file 
	 * @param stream Stream to read from
	 * @throws IOException If an IO exception occurs while writing
	 * @throws IllegalAccessException If the signatures are encrypted, but now {@link EncryptionSpecification} was sent
	 */
	public void readSignatures(InputStream stream) throws IOException, IllegalAccessException {
		try {
			readSignatures(stream, null);
		} catch (InvalidKeyException e){
			throw new IllegalAccessException(e.getMessage());
		}
	}

	/**
	 * Read in signatures from a file in encrypted, compressed or plain-text
	 * @param stream Stream to read from
	 * @param spec An {@link com.arosbio.encryption.EncryptionSpecification EncryptionSpecification} to decrypt the stream with
	 * @throws IOException If an IO exception occurs while writing
	 * @throws InvalidKeyException Invalid {@link com.arosbio.encryption.EncryptionSpecification EncryptionSpecification} 
	 */
	public void readSignatures(InputStream stream, EncryptionSpecification spec) 
			throws IOException, InvalidKeyException {
		signaturesHelper.clear();

		try(
				InputStream unzippedStream = StreamUtils.unZIP(stream);
				BufferedInputStream buffStream = new BufferedInputStream(unzippedStream);){

			EncryptionStatus status = EncryptUtils.getStatus(buffStream, spec);
			switch (status) {
			case ENCRYPTED_CORRECT_SPEC:
				LOGGER.debug("Trying to read encrypted signatures");
				readSignaturesFromStream(spec.decryptStream(buffStream));
				break;
			case ENCRYPTED_WRONG_SPEC:
				LOGGER.debug("Signatures are encrypted with a different key than the given one");
				throw new InvalidKeyException("Signatures are encrypted with a different key than the given one");
			case UNKNOWN:
				LOGGER.debug("Trying to read plain-text or compressed signatures");
				readSignaturesFromStream(buffStream);
				break;
			default:
				LOGGER.debug("EncryptionStatus returned for signatures was: {}", status);
				throw new IOException("Could not read signatures due to coding error, please send the log-file to Aros Bio");
			}
		} catch(InvalidKeyException | IOException e){
			LOGGER.debug("Failed read signatures",e);
			throw e;
		}

	}

	private void readSignaturesFromStream(InputStream stream) throws IOException {

		signaturesHelper.clear();

		try(
				BufferedReader signaturesReader= new BufferedReader(new InputStreamReader(stream));
				){

			// Read the following lines containing signatures
			String signature;
			int index = 0;
			while ( (signature = signaturesReader.readLine()) != null ) {
				signaturesHelper.put(signature, index);
				index ++;
			}
		}

		if (signaturesHelper.size()<10){
			LOGGER.info("WARNING: Could only detect {} signatures", signaturesHelper.size());
		}

		//Do some sanity checking
		boolean containsBracket=false;
		int numChecked = 0;
		for (String sign: signaturesHelper.keySet()){
			if (sign.contains("[")){
				containsBracket=true;
				break;
			}
			if (numChecked >= 20) {
				break;
			}
			numChecked++;
		}
		if (!containsBracket){
			LOGGER.info("WARNING: First 20 signatures do not contain '['");
		}

		LOGGER.debug("Loaded {} signatures from stream", signaturesHelper.size());
	}


	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (! (o instanceof SignaturesDescriptor))
			return false;
		return equals((SignaturesDescriptor) o);
	}


	public boolean equals(SignaturesDescriptor other) {
		LOGGER.debug("Comparing two SignaturesDescriptors");

		if (this == other)
			return true;

		// Check startHeight
		if (startHeight != other.startHeight){
			LOGGER.debug("startHeight does not match: {} vs. {}", startHeight, other.startHeight);
			return false;
		}

		// Check endHeight
		if(endHeight != other.endHeight){
			LOGGER.debug("endHeight does not match: {} vs. {}", endHeight, other.endHeight);
			return false;
		}

		// Check signatures
		if (signaturesHelper.size() != other.signaturesHelper.size()){
			LOGGER.debug("The signatures-lists does not match (different sizes)");
			return false;
		}

		// Check signatures_helper
		if(! signaturesHelper.equals(other.signaturesHelper)){
			LOGGER.debug("The signatures_helper is not the same in the two objects");
			return false;
		}

		LOGGER.debug("SignaturesDescriptors were equal!");
		return true;
	}

}

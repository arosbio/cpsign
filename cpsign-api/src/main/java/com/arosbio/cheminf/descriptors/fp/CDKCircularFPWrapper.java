/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cheminf.descriptors.fp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.fingerprint.CircularFingerprinter;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cheminf.descriptors.DescriptorCalcException;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.BooleanConfig;
import com.arosbio.commons.config.IntegerConfig;
import com.arosbio.data.SparseFeature;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.DataSink;
import com.arosbio.io.DataSource;
import com.arosbio.ml.io.MetaFileUtils;
import com.google.common.collect.Range;

/**
 * Uses the same folding of the generated hash as in CDK:
 * https://github.com/cdk/cdk/blob/master/descriptor/fingerprint/src/main/java/org/openscience/cdk/fingerprint/CircularFingerprinter.java
 * @author staffan
 *
 */
public abstract class CDKCircularFPWrapper implements FPDescriptor {

	private static final Logger LOGGER = LoggerFactory.getLogger(CDKCircularFPWrapper.class);
	private static final int DEFAULT_FP_LENGTH = 1024;
	private static final int MIN_FP_LENGTH = 50;
	private static final String META_FILE_NAME =  "meta.json";

	private static final String FP_LENGTH_KEY = "FPLength";
	private static final String FP_CLASS = "FPClass";
	private static final String FP_USE_COUNT = "useCounts";
	
	static final String DESCRIPTION_FMT = "Extended-connectivity fingerprints of dimension %s";

	private int length = DEFAULT_FP_LENGTH;
	private boolean useCount = false;
	/** Will be != null only when initialized */
	private CircularFingerprinter generator;
	/** The finger print "class" - used for save/load of the object */
	private int fpCls = -1;

	@Override
	public Map<String, Object> getProperties() {
		Map<String,Object> p = new HashMap<>();
		p.put(FP_LENGTH_KEY, length);
		p.put(FP_CLASS, fpCls);
		p.put(FP_USE_COUNT, useCount);
		return p;
	}
	
	private void assertCanUpdateParams() {
		if (generator != null)
			throw new IllegalStateException("ChemDescriptor " + getName() + " already initialized, cannot change parameters");
	}

	private static List<String> CONFIG_LEN = Arrays.asList("len","length");
	private static List<String> CONFIG_COUNT = Arrays.asList("count","useCount");
	
	@Override
	public List<ConfigParameter> getConfigParameters() {
		List<ConfigParameter> confs = new ArrayList<>();
		confs.add(new IntegerConfig.Builder(CONFIG_LEN, DEFAULT_FP_LENGTH)
			.range(Range.atLeast(MIN_FP_LENGTH))
			.description("The length/width of the fingerprint, i.e. number of attributes/features").build());
		confs.add(new BooleanConfig.Builder(CONFIG_COUNT, false)
			.description("Use the count for each encountered hash, default is to only use the 'bit' version of the fingerprint").build());
		return confs;
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) 
			throws IllegalStateException, IllegalArgumentException {
		assertCanUpdateParams();
		if (params == null)
			return;
		for (Map.Entry<String, Object> kv : params.entrySet()) {
			// Length
			if (CollectionUtils.containsIgnoreCase(CONFIG_LEN, kv.getKey())) {
				try {
					int newLen = TypeUtils.asInt(kv.getValue());
					if (newLen < MIN_FP_LENGTH) {
						throw new IllegalArgumentException("Invalid 'length' argument: " + newLen);
					}
					length = newLen;
					LOGGER.debug("Setting length of {} to {}",getName(),newLen);
				} catch (NumberFormatException nfe) {
					LOGGER.debug("Failed setting new length of " +getName(), nfe);
					throw new IllegalArgumentException(String.format("Invalid argument for '%s' parameter for descriptor %s: %s",kv.getKey(), getName(), kv.getValue()));
				}
			}
			// Count
			else if (CollectionUtils.containsIgnoreCase(CONFIG_COUNT, kv.getKey())) {
				try {
					useCount = TypeUtils.asBoolean(kv.getValue());
					LOGGER.debug("Setting {} to count={}",getName(),useCount);
				} catch (IllegalArgumentException e) {
					LOGGER.debug("Failed setting FP count/bit version for descriptor {}",getClass().getCanonicalName());
					throw new IllegalArgumentException(String.format("Invalid argument for '%s: %s",kv.getKey(), kv.getValue()));
				}
				
			}
		}

	}
	
	@Override
	public boolean hasFixedLength() {
		return true;
	}

	@Override
	public int getLength() {
		return length;
	}

	public void setLength(int len) {
		assertCanUpdateParams();
		this.length = len;
	}
	
	public boolean usesCountVersion() {
		return useCount; 
	}
	
	public void useCountVersion(boolean useCount) {
		assertCanUpdateParams();
		this.useCount = useCount; 
	}
	
	/**
	 * Internal usage only 
	 */
	void initialize(int FP_CLASS) {
		fpCls = FP_CLASS;
		generator = new CircularFingerprinter(FP_CLASS, length);
	}

	@Override
	public boolean isReady() {
		return generator!=null;
	}

	public List<String> getFeatureNames() {
		if (generator == null)
			return new ArrayList<>();
		String base = getName();
		List<String> names = new ArrayList<>(length);
		for (int i=0;i<length; i++) {
			names.add(base + '_'+i);
		}
		return names;
	}

	@Override
	public boolean requires3DCoordinates() {
		return false;
	}

	@Override
	public List<SparseFeature> calculateDescriptors(IAtomContainer molecule)
			throws DescriptorCalcException, IllegalStateException {
		if (generator == null)
			throw new IllegalStateException("ChemDescriptor "+getName() + " not initialized yet");

		try {
			if (useCount) {
				return FPUtils.convert(generator.getCountFingerprint(molecule),length);
			} else {
				return FPUtils.convert(generator.getBitFingerprint(molecule));
			}
		} catch (CDKException e) {
			LOGGER.debug("Failed calculating descriptors",e);
			throw new DescriptorCalcException("ChemDescriptor " +getName()+" failed computing descriptors: "+e.getMessage());
		}
	}

	@Override
	public List<SparseFeature> calculateDescriptorsAndUpdate(IAtomContainer molecule)
			throws DescriptorCalcException, IllegalStateException {
		return calculateDescriptors(molecule);
	}

	@Override
	public void saveDescriptorToSink(DataSink sink, String basePath, EncryptionSpecification spec)
			throws IOException, InvalidKeyException, IllegalStateException {
		if (! isReady()) {
			LOGGER.debug("Attempted to save descriptor {}, but it is not initialized yet",getClass().getName());
			throw new IllegalStateException("ChemDescriptor not initialized - cannot be saved");
		}
		try (OutputStream ostream = sink.getOutputStream(basePath+'/'+META_FILE_NAME)){
			Map<String,Object> props = getProperties();
			LOGGER.debug("Writing CDKCircularFPWrapper properties to meta.json: {}", props);
			MetaFileUtils.writePropertiesToStream(ostream, props);
		} catch (IOException e) {
			LOGGER.debug("Failed writing properties for CDKCircularFPWrapper-descriptor: {}", getName(),e);
			throw new IOException("Failed saving descriptor " + getName() + " to output");
		}

	}

	@Override
	public void loadDescriptorFromSource(DataSource source, String path, EncryptionSpecification spec)
			throws IOException, InvalidKeyException {

		try (InputStream istream = source.getInputStream(path+'/' + META_FILE_NAME)){
			Map<String,Object> props = MetaFileUtils.readPropertiesFromStream(istream);
			LOGGER.debug("Loaded CDK-CircularFPWrapper properties: {}", props);

			if (props.containsKey(FP_LENGTH_KEY)) {
				length = TypeUtils.asInt(props.get(FP_LENGTH_KEY));
			} else { 
				failLoad(); 
			}
			
			if (props.containsKey(FP_CLASS)) {
				fpCls = TypeUtils.asInt(props.get(FP_CLASS));
			} else {
				failLoad();
			}
			
			if (props.containsKey(FP_USE_COUNT)) {
				useCount = TypeUtils.asBoolean(props.get(FP_USE_COUNT));
			} else {
				failLoad();
			}
			
			initialize(fpCls);
			LOGGER.debug("Initialized descriptor {}",getName());
		}

	}
	
	private void failLoad() {
		throw new IllegalArgumentException("Failed loading descriptor " + getName());
	}

	public abstract CDKCircularFPWrapper clone();

	public String toString() {
		return getName();
	}
}

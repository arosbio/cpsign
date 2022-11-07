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
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.fingerprint.IBitFingerprint;
import org.openscience.cdk.fingerprint.MACCSFingerprinter;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cheminf.descriptors.DescriptorCalcException;
import com.arosbio.commons.mixins.Described;
import com.arosbio.data.SparseFeature;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.DataSink;
import com.arosbio.io.DataSource;

public class MACCS implements FPDescriptor, Described {

	private static final Logger LOGGER = LoggerFactory.getLogger(MACCS.class);
	
	public static final String NAME = "MACCS";
	public static final String DESCRIPTION = "MACCS fingerprint is a key-based 166 bit fingerprint";

	private MACCSFingerprinter generator = null; 

	
	@Override
	public void initialize() {
		generator = new MACCSFingerprinter(SilentChemObjectBuilder.getInstance());
	}

	@Override
	public boolean isReady() {
		return generator != null;
	}

	@Override
	public String getName() {
		return NAME;
	}
	
	@Override
	public String getDescription() {
		return DESCRIPTION;
	}
	
	public String toString() {
		return NAME;
	}
	

	@Override
	public List<String> getFeatureNames() {
		List<String> names = new ArrayList<>();
		if (generator == null)
			return names;

		for (int i=0; i<generator.getSize(); i++) {
			names.add(NAME+'_'+i);
		}

		return names;
	}

	@Override
	public boolean requires3DCoordinates() {
		return false;
	}

	@Override
	public MACCS clone() {
		return new MACCS();
	}

	@Override
	public boolean hasFixedLength() {
		return true;
	}

	@Override
	public int getLength() {
		return 166;
	}

	@Override
	public List<SparseFeature> calculateDescriptors(IAtomContainer molecule)
			throws DescriptorCalcException, IllegalStateException {
		if (! isReady()) {
			throw new IllegalStateException(NAME + " descriptor not initialized yet");
		}
		
		try {
			IBitFingerprint fp = generator.getBitFingerprint(molecule);
			// CDK adds this property - we will not use it later so we remove it 
			molecule.removeProperty("org.openscience.cdk.isomorphism.VentoFoggia$AdjListCache");
			return FPUtils.convert(fp);
		} catch (CDKException e) {
			LOGGER.debug("Failed computing MACCS FP",e);
			throw new DescriptorCalcException("Failed computing MACCS fingerprint, due to: " + e.getMessage());

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
		// nothing to save
	}

	@Override
	public void loadDescriptorFromSource(DataSource source, String path, EncryptionSpecification spec)
			throws IOException, InvalidKeyException {
		// nothing to load - only init the descriptor
		initialize();
		LOGGER.debug("Successfully loaded descriptor %s",NAME);
	}

	@Override
	public Map<String, Object> getProperties() {
		return new HashMap<>();
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		return new ArrayList<>();
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) {
		// nothing to do
	}


}

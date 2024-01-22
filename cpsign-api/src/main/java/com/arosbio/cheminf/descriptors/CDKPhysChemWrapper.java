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
import java.lang.reflect.InvocationTargetException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openscience.cdk.IImplementationSpecification;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.qsar.DescriptorValue;
import org.openscience.cdk.qsar.IMolecularDescriptor;
import org.openscience.cdk.qsar.result.BooleanResult;
import org.openscience.cdk.qsar.result.DoubleArrayResult;
import org.openscience.cdk.qsar.result.DoubleResult;
import org.openscience.cdk.qsar.result.IDescriptorResult;
import org.openscience.cdk.qsar.result.IntegerArrayResult;
import org.openscience.cdk.qsar.result.IntegerResult;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cheminf.ChemUtils;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.BooleanConfig;
import com.arosbio.commons.config.IntegerConfig;
import com.arosbio.commons.config.NumericConfig;
import com.arosbio.commons.config.StringConfig;
import com.arosbio.commons.mixins.Described;
import com.arosbio.data.SparseFeature;
import com.arosbio.data.SparseFeatureImpl;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.DataSink;
import com.arosbio.io.DataSource;
import com.arosbio.ml.io.MetaFileUtils;

public class CDKPhysChemWrapper implements ChemDescriptor, Described {

	private static final Logger LOGGER = LoggerFactory.getLogger(CDKPhysChemWrapper.class);
	private static final String META_FILE_NAME = "meta.json";
	private static final String DESCRIPTOR_PARAM_LIST_KEY = "descriptorParameters";
	private static final String DESCRIPTOR_NAME_KEY = "descriptorName";

	private IMolecularDescriptor descriptorImplementation;
	private boolean descriptorInitialized = false;
	private Boolean require3Dcoords = null;

	public CDKPhysChemWrapper(IMolecularDescriptor descriptor) {
		this.descriptorImplementation = descriptor;
		this.descriptorImplementation.initialise(SilentChemObjectBuilder.getInstance());
	}


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

	public boolean requires3DCoordinates() {
		if (require3Dcoords != null) {
			return require3Dcoords;
		} else {
			// Check if needed or not
			LOGGER.debug("Checking if descriptor {} requires 3D coordinates",getName());
			try {
				DescriptorValue dv = descriptorImplementation.calculate(ChemUtils.makeCCC());
				Exception e = dv.getException();
				if (e == null) {
					LOGGER.debug("{} does NOT require 3D coordinates",getName());
					return false;
				} else {
					String msg = e.getMessage().toUpperCase();
					if (msg.contains("3D") && msg.contains("COORDINATE")) {
						// Requires 3D!
						LOGGER.debug("{} DOES require 3D coordinates",getName());
						require3Dcoords = true;
						return true;
					} else {
						LOGGER.debug("Cannot tell if the descriptor needs 3D or not, returning false, the exception was: ", e);
					}
					return false;
				}
			} catch (Exception e) {
				LOGGER.debug("Failed checking if the descriptor {} require 3D coordinates or not",getName(),e);
				return false;
			}
		}
	}

	@SuppressWarnings("null")
	@Override
	public Map<String, Object> getProperties() {
		Map<String, Object> props = new HashMap<>();
		props.put(DESCRIPTOR_NAME_KEY, getName());

		Object[] descProps = descriptorImplementation.getParameters();

		if (descProps != null && descProps.length>0) {
			props.put(DESCRIPTOR_PARAM_LIST_KEY, Arrays.asList(descProps));
		}

		// This will not be used, but could be good for debugging
		String[] descPropNames = descriptorImplementation.getParameterNames();
		if (descPropNames != null && descPropNames.length > 0) {
			for (int i=0; i<descPropNames.length; i++) {
				props.put(descPropNames[i], descProps[i]);
			}
		}
		return props;
	}

	@Override
	public List<ConfigParameter> getConfigParameters() {
		if (descriptorImplementation.getParameterNames() != null && 
				descriptorImplementation.getParameterNames().length > 0) {

			String[] names = descriptorImplementation.getParameterNames();

			List<ConfigParameter> configs = new ArrayList<>();
			Object [] defaults = descriptorImplementation.getParameters();
			for (int i=0; i<defaults.length; i++) {

				Object pType = descriptorImplementation.getParameterType(names[i]);
				ConfigParameter conf = null;

				if (TypeUtils.isInt(pType)) {
					conf = new IntegerConfig.Builder(Arrays.asList(names[i]), TypeUtils.asInt(defaults[i])).build();
				} else if (TypeUtils.isDouble(pType)) {
					conf = new NumericConfig.Builder(Arrays.asList(names[i]), TypeUtils.asDouble(defaults[i])).build();
				} else if (TypeUtils.isBoolean(pType)) {
					conf = new BooleanConfig.Builder(Arrays.asList(names[i]), TypeUtils.asBoolean(defaults[i])).build();
				} else if (pType instanceof String) {
					conf = new StringConfig.Builder(Arrays.asList(names[i]), (String)defaults[i]).build();
				} else {
					LOGGER.debug("Argument for CDK IMolecularDescriptor was of type={}, for param={}, in desciptor={}", 
							pType.getClass(), names[i], getName());
				}

				if (conf != null) {
					configs.add(conf);
				} else {
					// Try to deduce from the default parameter
					if (TypeUtils.isInt(defaults[i])) {
						conf = new IntegerConfig.Builder(Arrays.asList(names[i]), TypeUtils.asInt(defaults[i])).build();
					} else if (TypeUtils.isDouble(defaults[i])) {
						conf = new NumericConfig.Builder(Arrays.asList(names[i]), TypeUtils.asDouble(defaults[i])).build();
					} else if (TypeUtils.isBoolean(defaults[i])) {
						conf = new BooleanConfig.Builder(Arrays.asList(names[i]), TypeUtils.asBoolean(defaults[i])).build();
					} else if (defaults[i] instanceof String) {
						conf = new StringConfig.Builder(Arrays.asList(names[i]), (String)defaults[i]).build();
					} else {
						LOGGER.debug("Could not infer param type from default value either.. default={}, for param={}, in descriptor: {}",
								defaults[i].getClass(), names[i], getName());
						throw new RuntimeException();
					}
					configs.add(conf);
				}

			}
			return configs;
		} else 
			return new ArrayList<>();
	}

	@Override
	public void setConfigParameters(Map<String, Object> params) 
			throws IllegalStateException, IllegalArgumentException {
		assertChangesAllowedOrFail();
		if (params == null || params.isEmpty())
			return;
		String[] pNames = descriptorImplementation.getParameterNames();
		Object[] defaultParams = descriptorImplementation.getParameters();
		Object[] toSet = new Object[pNames.length];
		for (int i=0; i<pNames.length; i++) {
			Object expectedParamType = descriptorImplementation.getParameterType(pNames[i]);
			String pLC = pNames[i].toLowerCase();
			String originalParamsKey = null;
			for (String paramK : params.keySet()) {
				if (pLC.equals(paramK.toLowerCase())) {
					originalParamsKey = paramK;
					toSet[i] = convertToType(expectedParamType, params.get(paramK));
					break;
				}
			}
			if (originalParamsKey == null) {
				// This parameter was not given - so we use the current setting!
				toSet[i] = defaultParams[i];
			} else {
				// remove it from the map of given parameters
				params.remove(originalParamsKey);
			}
		}


		if (!params.isEmpty()) {
			// More parameters was sent than could be recognized
			LOGGER.debug("Parameters not recognized: {}", params);
			throw new IllegalArgumentException("Parameter(s) not recognized by "+getName() + ": " + params.keySet());
		}

		// Everything found, now set the parameters!
		try {
			descriptorImplementation.setParameters(toSet);
		} catch (CDKException e) {
			LOGGER.debug("Failed setting CDK IMolecularDescriptor parameters",e);
			throw new IllegalArgumentException(e.getMessage());
		}
	}

	private static Object convertToType(Object toType, Object toConvert) {
		if (toType instanceof Integer) {
			return (Object) TypeUtils.asInt(toConvert);
		} else if (toType instanceof Double) {
			return (Object) TypeUtils.asDouble(toConvert);
		} else if (toType instanceof Boolean) {
			return (Object) TypeUtils.asBoolean(toConvert);
		} else if (toType instanceof String) {
			return toConvert.toString();
		} else {
			LOGGER.debug("Parameter type not recognized: {}", toType.getClass());
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Returns the implementation class name, to find the names of actual descriptors use the
	 * {@link #getFeatureNames()} method instead
	 */
	public String getName() {
		return descriptorImplementation.getClass().getSimpleName();
	}


	@Override
	public List<String> getFeatureNames(){
		return Arrays.asList(this.descriptorImplementation.getDescriptorNames());
	}

	@Override
	public String getDescription() {
		IImplementationSpecification spec = descriptorImplementation.getSpecification();
		StringBuilder sb = new StringBuilder();
		if (spec.getSpecificationReference() != null)
			sb.append("Specification ref: ").append(spec.getSpecificationReference()).append(' ');
		if (spec.getImplementationTitle() != null)
			sb.append("Implementation title: ").append(spec.getSpecificationReference()).append(' ');
		if (spec.getImplementationIdentifier()!=null)
			sb.append("Implementation id: ").append(spec.getImplementationIdentifier()).append(' ');
		if (spec.getImplementationVendor() != null)
			sb.append("Implementation vendor: ").append(spec.getImplementationVendor()).append(' ');
		return sb.toString();
	}

	public IMolecularDescriptor getCDKDescriptor() {
		return descriptorImplementation;
	}

	@Override
	public CDKPhysChemWrapper clone() {
		try {
			CDKPhysChemWrapper clone = new CDKPhysChemWrapper(descriptorImplementation.getClass().getDeclaredConstructor().newInstance());
			try {
				if (this.descriptorImplementation.getParameterNames() != null && 
						this.descriptorImplementation.getParameterNames().length >0) {
					clone.descriptorImplementation.setParameters(this.descriptorImplementation.getParameters());
				}
			} catch (CDKException e) {
				throw new RuntimeException("Failed copying parameters to clone descriptor");
			}
			
			return clone;
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			LOGGER.debug("Failed to clone the IDescriptor!",e);
		}
		throw new RuntimeException();
	}

	@Override
	public void saveDescriptorToSink(DataSink sink, String basePath, EncryptionSpecification spec)
			throws IOException, InvalidKeyException, IllegalStateException {
		try (OutputStream ostream = sink.getOutputStream(basePath+'/'+META_FILE_NAME)){
			Map<String,Object> props = getProperties();
			LOGGER.debug("Writing CDKDescriptor properties to meta.json: {}", props);
			MetaFileUtils.writePropertiesToStream(ostream, props);
		} catch (IOException e) {
			LOGGER.debug("Failed writing properties for CDK-descriptor: {}", getName(),e);
			throw new IOException("Failed saving descriptor " + getName() + " to output");
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void loadDescriptorFromSource(DataSource source, String path, EncryptionSpecification spec)
			throws IOException, InvalidKeyException {
		try (InputStream istream = source.getInputStream(path+'/'+ META_FILE_NAME)){
			Map<String,Object> props = MetaFileUtils.readPropertiesFromStream(istream);
			LOGGER.debug("Loaded CDK-descriptor properties: {}", props);

			if (props.containsKey(DESCRIPTOR_PARAM_LIST_KEY)) {
				List<Object> descriptorParams = (List<Object>) props.get(DESCRIPTOR_PARAM_LIST_KEY);
				if (descriptorParams != null && !descriptorParams.isEmpty()) {
					try {
						setParameters(descriptorParams);
					} catch (CDKException e) {
						LOGGER.debug("Failed setting parameters at the IMolecularDescriptor object",e);
						throw new IOException("Failed loading CDKDescriptor");
					}
				}
			}
		}
		initialize();
	}

	private void setParameters(List<Object> params) throws CDKException {
		String[] pNames = descriptorImplementation.getParameterNames();
		Object[] finalParams = new Object[params.size()];
		for (int i=0; i<pNames.length; i++) {
			String name = pNames[i];
			Object pType = descriptorImplementation.getParameterType(name);
			if (pType instanceof Integer) {
				finalParams[i] = TypeUtils.asInt(params.get(i));
			} else if (pType instanceof Double) {
				finalParams[i] = TypeUtils.asDouble(params.get(i));
			} else if (pType instanceof Boolean) {
				finalParams[i] = TypeUtils.asBoolean(params.get(i));
			} else if (pType instanceof String) {
				finalParams[i] = params.get(i).toString();
			} else {
				LOGGER.debug("Parameter type of class: {} not recognized in CDKPhysChemWrapper",pType.getClass());
			}
		}
	}

	@Override
	public boolean hasFixedLength() {
		return true;
	}

	@Override
	public int getLength() {
		assertInitialized();
		return descriptorImplementation.getDescriptorNames().length;
	}

	public String toString() {
		if (!isReady()){
			return "CDKDescriptor: "+getName();
		}
		return "CDKDescriptor: "+getProperties();
	}

	/**
	 * Use typical conventions from C, true -> 1, false -> 0
	 * @param value
	 * @return
	 */
	private static double toNumeric(boolean value) {
		return (value ? 1 : 0);
	}

	private double getValue(double val) {
		if (Double.isNaN(val))
			return 0;
		return val;
	}


	@Override
	public List<SparseFeature> calculateDescriptors(IAtomContainer molecule)
			throws DescriptorCalcException, IllegalStateException {
		assertInitialized();

		DescriptorValue response = descriptorImplementation.calculate(molecule);
		IDescriptorResult value = response.getValue();
		if (response.getException() != null) {
			LOGGER.debug("Failed computing descriptors for descriptor {}, message {}",
					getName(),response.getException().getMessage(),response.getException());
			throw new DescriptorCalcException(response.getException());
		}

		if (value instanceof DoubleResult) {
			return Arrays.asList(new SparseFeatureImpl(0, getValue(((DoubleResult)value).doubleValue())));
		} else if (value instanceof IntegerResult) {
			return Arrays.asList(new SparseFeatureImpl(0, getValue(((IntegerResult)value).intValue())));
		}  else if (value instanceof BooleanResult) {
			return Arrays.asList(new SparseFeatureImpl(0, toNumeric(((BooleanResult)value).booleanValue())));
		} else if (value instanceof DoubleArrayResult) {
			DoubleArrayResult res = (DoubleArrayResult) value;
			List<SparseFeature> features = new ArrayList<>();
			for (int i=0; i<res.length(); i++) {
				features.add(new SparseFeatureImpl(i, getValue(res.get(i))));
			}
			return features;
		} else if (value instanceof IntegerArrayResult) {
			IntegerArrayResult res = (IntegerArrayResult) value;
			List<SparseFeature> features = new ArrayList<>();
			for (int i=0; i<res.length(); i++) {
				features.add(new SparseFeatureImpl(i, getValue(res.get(i))));
			}
			return features;
		} else {
			LOGGER.debug("CDK ChemDescriptor result was none of the 5 classes, instead: {}", value.getClass());
			throw new DescriptorCalcException("Failed computing descriptors using " + getName());
		}

	}

	@Override
	public List<SparseFeature> calculateDescriptorsAndUpdate(IAtomContainer molecule)
			throws DescriptorCalcException, IllegalStateException {
		return calculateDescriptors(molecule);
	}


}

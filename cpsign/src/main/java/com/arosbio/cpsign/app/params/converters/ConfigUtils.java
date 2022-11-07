/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app.params.converters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.arosbio.commons.FuzzyMatcher;
import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.BooleanConfig;
import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.commons.config.DescribedConfig;
import com.arosbio.commons.config.EnumConfig;
import com.arosbio.commons.config.ImplementationConfig;
import com.arosbio.commons.config.IntegerConfig;
import com.arosbio.commons.config.NumericConfig;
import com.arosbio.commons.config.StringConfig;
import com.arosbio.commons.config.StringListConfig;
import com.arosbio.commons.mixins.HasID;
import com.arosbio.commons.mixins.Named;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.TypeConversionException;

public class ConfigUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConfigUtils.class);
	private static final String KEY_VALUE_DELIM = "=";

	public static void setConfigs(Configurable object, final List<String> configList, final String originalText) 
			throws TypeConversionException {
		
		if (configList == null || configList.isEmpty()) {
			return;
		}
		// Config parameters
		List<ConfigParameter> possibleParams = object.getConfigParameters();

		Map<String,Object> config = new HashMap<>();

		if (!configList.isEmpty()) {
			if (possibleParams== null || possibleParams.isEmpty()) {
				throw new TypeConversionException(getName(object) + " does not accept any sub-parameters");
			}
			boolean positionalArgAllowed = true; // positional arg - without "=". once a "=" has been encountered, we do not allow positional args

			for (int i=0; i <configList.size(); i++) {
				String arg = configList.get(i);
				String[] paramSplit = arg.split(KEY_VALUE_DELIM);


				if (paramSplit.length == 1) {
					// Positional argument
					if (! positionalArgAllowed) {
						throw new TypeConversionException("Failed setting sub-parameters for " + getName(object) + " - positional argument not allowed after explicitly named arguments {"+ configList.get(i) + "}");
					}

					if (possibleParams.size() > i) {
						ConfigParameter p = possibleParams.get(i);
						config.put(p.getNames().get(0), convertValue(p, arg));
					}
				} else if (paramSplit.length == 2) {
					// Named argument
					positionalArgAllowed = false;
					ConfigParameter p = null;
					try {
						p = getConfig(possibleParams, paramSplit[0]);
					} catch (Exception e) {
						throw new TypeConversionException("Failed configuring " + getName(object) + ": " + e.getMessage());
					}
					config.put(p.getNames().get(0), convertValue(p, paramSplit[1]));
				} else {
					LOGGER.debug("Text for configurable argument did not have the correct syntax: {}", originalText);
					throw new TypeConversionException("Argument " + originalText + " did not have the correct syntax");
				}
			}
			LOGGER.debug("Configuring {} with the following config args: {}",object, config);
			try {
				object.setConfigParameters(config);
			} catch (Exception e) {
				LOGGER.debug("Failed configuration",e);
				throw new TypeConversionException("Invalid sub-parameter for " + getName(object) + ": " + e.getMessage());
			}
		}

	}
	
	private static String getName(Configurable config) {
		if (config instanceof Named) {
			return ((Named) config).getName();
		} else {
			return config.getClass().getSimpleName();
		}
	}

	private static Object convertValue(ConfigParameter p, String input) {
		if (p instanceof DescribedConfig) {
			return convertValue(((DescribedConfig) p).getOriginalConfig(), input);
		}
		LOGGER.debug("Trying to convert input '{}' to config '{}' of type {}",
				input,p.getNames().get(0), p.getType());
		if (p instanceof BooleanConfig) {
			try {
				return TypeUtils.asBoolean(input);
			} catch (Exception e) {
				LOGGER.debug("tried to convert input '{}' to a boolean value for argument {} but failed: {}",
						input,p.getNames().get(0),e.getMessage());
			}
		} else if (p instanceof EnumConfig<?>) {

			Object[] values = ((EnumConfig<?>) p).getEnumValues();
			for (Object o : values) {
				Integer id = null;
				try {
					id = TypeUtils.asInt(input);
				} catch (Exception e) {}
				
				if (id!=null && o instanceof HasID) {
					try {
						if (id==((HasID)o).getID()) {
							return o;
						}
					} catch (Exception e) {}
				} 
				if (o instanceof Named) {
					if (((Named) o).getName().equalsIgnoreCase(input)) {
						return o;
					}
				}
				// try using the name
				if (input.equalsIgnoreCase(((Enum<?>)o).name())) {
					return o;
				}
			}
			LOGGER.debug("Found no match for enum of class {} for input: {}", p.getClass().getSimpleName(), input);

		} else if (p instanceof ImplementationConfig<?>) {
			ImplementationConfig<?> impl = (ImplementationConfig<?>)p;
			Class<?> cls = impl.getClassOrInterface();
			try {
				return FuzzyServiceLoader.load(cls, input);
			} catch (Exception e) {
				LOGGER.debug("tried to convert input '{}' to a {} value for argument {} but failed: {}",
						input,cls.getSimpleName(),p.getNames().get(0),e.getMessage());
			}
		} else if (p instanceof IntegerConfig) {
			try {
				return TypeUtils.asInt(input);
			} catch (Exception e) {
				LOGGER.debug("tried to convert input '{}' to a integer value for argument {} but failed: {}",
						input,p.getNames().get(0),e.getMessage());
			}
		} else if (p instanceof NumericConfig) {
			try {
				return TypeUtils.asDouble(input);
			} catch (Exception e) {
				LOGGER.debug("tried to convert input '{}' to a double value for argument {} but failed: {}",
						input,p.getNames().get(0),e.getMessage());
			}
		} else if (p instanceof StringConfig) {
			return input;
		} else if (p instanceof StringListConfig) {
			LOGGER.debug("Configuring {} with argument: {}",p,input);
			String[] array = input.split("[,:\t]");
			LOGGER.debug("Splitted list: {}", Arrays.toString(array));
			List<String> list = new ArrayList<>();
			for (String s : array) {
				String trimmed = s.trim();
				if (!trimmed.isEmpty()) {
					list.add(trimmed);
				}
			}
			LOGGER.debug("Final LIST: {}", list);
			return list;
		} 
		
		LOGGER.debug("Fallback no Config type found for parameter {}", p);
		// Fall-back - hope the Configurable object can convert it correctly
		return input;

	}

	@SuppressWarnings("unused")
	private static String getConfigName(List<ConfigParameter> params, String inputArg) {
		List<Pair<List<String>, String>> possibleMatches = new ArrayList<>();
		for (ConfigParameter p : params) {
			possibleMatches.add(ImmutablePair.of(p.getNames(),p.getNames().get(0)));
		}

		return new FuzzyMatcher().match(possibleMatches, inputArg);
	}

	private static ConfigParameter getConfig(List<ConfigParameter> params, String inputKey) {
		List<Pair<List<String>, ConfigParameter>> possibleMatches = new ArrayList<>();
		for (ConfigParameter p : params) {
			possibleMatches.add(ImmutablePair.of(p.getNames(),p));
		}
		try {
			return new FuzzyMatcher().match(possibleMatches, inputKey);
		} catch (IllegalArgumentException e) {
			throw new TypeConversionException("Invalid sub-parameter '"+inputKey+'\'');
		}
	}
}

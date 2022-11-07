/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp.nonconf.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.Version;
import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.commons.mixins.Aliased;
import com.arosbio.io.DataSource;
import com.arosbio.ml.cp.nonconf.NCM;
import com.arosbio.ml.io.MetaFileUtils;
import com.arosbio.ml.io.impl.PropertyNameSettings;

public class NCMUtils {

	public static final String NCM_BASE_PATH = ".ncm";
	public static final String SCORING_MODEL_FILE = ".scoring";
	public static final String ERROR_MODEL_FILE = ".errorModel";
	public static final String NCM_PARAMS_META_FILE = ".meta.json";
	public static final String MODEL_PARAMS_FILE_ENDING = ".params";

	public static final String ERROR_MODEL_PREFIX = "ERROR_MODEL_"; 
	
	private static List<String> getNames(NCM ncm){
		if (ncm instanceof Aliased) {
			List<String> ns = new ArrayList<>();
			ns.add(ncm.getName());
			for (String n : ((Aliased) ncm).getAliases()){
				ns.add(n);
			}
			return ns;
			
		} else {
			return Arrays.asList(ncm.getName());
		}
	}

	public static void verifyNCMmatches(NCM ncm, DataSource src, String paramPath) throws IOException {
		try (InputStream metaStream = src.getInputStream(paramPath)){
			Map<String,Object> props = MetaFileUtils.readPropertiesFromStream(metaStream);

			// Name
			if (props.containsKey(PropertyNameSettings.NCM_NAME)) {
				String ncmName = props.get(PropertyNameSettings.NCM_NAME).toString();
				if (!CollectionUtils.containsIgnoreCase(getNames(ncm), ncmName)) {
					throw new IllegalArgumentException("NCM of different types");
				}
			}
			// ID
			if (props.containsKey(PropertyNameSettings.NCM_ID)) {
				long ncmID = TypeUtils.asLong(props.get(PropertyNameSettings.NCM_ID));
				if (ncmID != ncm.getID()) {
					throw new IllegalArgumentException("NCM of different types");
				}
			}

			// Version
			if (props.containsKey(PropertyNameSettings.NCM_VERSION)) {
				Version v = Version.parseVersion(props.get(PropertyNameSettings.NCM_VERSION).toString());
				if (!ncm.getVersion().equals(v)) {
					throw new IllegalArgumentException("NCM of different versions");
				}
			}
		}

	}

	public static List<ConfigParameter> addErrorModelPrefix(List<ConfigParameter> params){
		List<ConfigParameter> fixedList = new ArrayList<>();
		for (ConfigParameter p : params) {
			List<String> newNames = new ArrayList<>();
			for (String name : p.getNames())
				newNames.add(ERROR_MODEL_PREFIX+name); // Set prefix for error model
			fixedList.add(p.withNames(newNames));
		}
		return fixedList;
	}
	
	public static Map<String,Object> addErrorModelPrefix(Map<String,Object> properties){
		Map<String,Object> newMap = new HashMap<>();
		for (Map.Entry<String, Object> p : properties.entrySet()) {
			newMap.put(ERROR_MODEL_PREFIX+p.getKey(), p.getValue());
		}
		return newMap;
	}

	public static Map<String,Object> stripPrefixAndGetErrorModelParams(Map<String,Object> params){
		// Error params
		Map<String,Object> errorParams = new HashMap<>(); 
		// Here we could have issues with lower/upper case keys. first add all in upper-case
		for (Map.Entry<String,Object> p: params.entrySet()) {
			errorParams.put(p.getKey().toUpperCase(), p.getValue());
		}

		// Refine by looking for the error-model prefix (loop still over old-params names - not to cause concurrent-modification errors
		for (Map.Entry<String,Object> p: params.entrySet()) {
			String UPPER_CASE_KEY = p.getKey().toUpperCase();
			if (UPPER_CASE_KEY.startsWith(ERROR_MODEL_PREFIX)) {
				// Write over potential duplicates from the scoring params
				errorParams.put(UPPER_CASE_KEY.substring(ERROR_MODEL_PREFIX.length()), p.getValue());
			}
		}
		return errorParams;
	}

}

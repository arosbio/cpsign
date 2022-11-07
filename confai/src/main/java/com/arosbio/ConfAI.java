/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.Version;

public final class ConfAI {

	/**
	 * A backup version to print, the correct one should be in confai.properties file and the MANIFEST.mf file
	 */
	public final static String FALLBACK_VERSION = "2.0.0-x";

	private final static Logger LOGGER = LoggerFactory.getLogger(ConfAI.class);
	private final static String PROPERTIES_FILE = "confai.properties";
	private final static String BUILD_VERSION_KEY = "build.version";
	private final static String TIME_STAMP_KEY = "build.ts";
	private final static String FALLBACK_TS = "Undefined";

	public static Version getVersion() throws IllegalStateException {
		return Version.parseVersion(getVersionAsString());
	}

	public static String getVersionAsString() {
		return getIfSet(getProperties(), BUILD_VERSION_KEY, FALLBACK_VERSION);
	}

	/**
	 * The build time-stamp
	 * @return Time stamp as a string
	 */
	public static String getBuildTS() {
		return getIfSet(getProperties(), TIME_STAMP_KEY, FALLBACK_TS);
	}

	private static String getIfSet(Properties p, String key, String fallback) {
		String value = p.getProperty(key);
		if (value==null || value.startsWith("$"))
			return fallback;
		return value;
	}

	/**
	 * get properties
	 * @return A properties object - can have properties or be empty
	 */
	private static Properties getProperties() {
		Properties prop = new Properties();
		try {
			Enumeration<URL> resources = ConfAI.class.getClassLoader()
					.getResources(PROPERTIES_FILE);
			while (resources.hasMoreElements()) {
				try (InputStream is = resources.nextElement().openStream();){
					prop.load(is);
				} catch(IOException ioe) {
					LOGGER.debug("Failed reading properties from file");
				} 
			}
		} catch (Exception e){
			LOGGER.debug("Could not find cpsign version in confai.properties file");
		}
		return prop;
	}


}

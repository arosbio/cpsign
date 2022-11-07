/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.encryption.utils;

import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ServiceLoader;

import javax.management.ServiceNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.encryption.EncryptionSpecification;

/**
 * Factory/utility class for {@link com.arosbio.encryption.EncryptionSpecification EncryptionSpecification} 
 * instances. Uses 
 * 
 *  
 * @author staffan
 *
 */
public class EncryptionSpecFactory {
	private static final Logger LOGGER = LoggerFactory.getLogger(EncryptionSpecFactory.class);

	public static Iterator<EncryptionSpecification> getAvailable(){
		ServiceLoader<EncryptionSpecification> loader = ServiceLoader.load(EncryptionSpecification.class);
		return loader.iterator();
	}

	public static EncryptionSpecification getInstance(String name) 
		throws ServiceNotFoundException, InvalidKeyException {
		Iterator<EncryptionSpecification> iterator = getAvailable();
		if (name == null || name.isEmpty()){
			if (! iterator.hasNext())
				throw new ServiceNotFoundException("No encryption implementations available at class/module path");
			return iterator.next();
		}
		final String cleanedName = name.trim();
		while (iterator.hasNext()){
			EncryptionSpecification iSpec = iterator.next();
			if (cleanedName.equalsIgnoreCase(iSpec.getName().trim())){
				LOGGER.debug("matched encryption spec argument {} to implementation: {}",
					name, iSpec.getClass().getName());
				return iSpec;
			}
		}
		throw new ServiceNotFoundException("No service available for name '" + name+'\'');
	}

	
	public static EncryptionSpecification configure(EncryptionSpecification spec, byte[] key)
		throws InvalidKeyException{
		return configure(spec, key, true);
	}
	/**
	 * Utility method for configuring an EncryptionSpecification and clearing the sent key
	 * @param spec the encryption spec
	 * @param key the key to use
	 * @param clearKey if the input {@code key} should be zeroed out for security
	 * @return The same reference as the given {@code spec} param
	 * @throws InvalidKeyException In case the {@code key} is invalid in some way
	 */
	public static EncryptionSpecification configure(EncryptionSpecification spec, byte[] key, boolean clearKey)
		throws InvalidKeyException {
		// Init the spec
		spec.init(key);

		// Optionally clear the key
		if (clearKey){
			LOGGER.trace("Clearing the sent key after encryption-spec init");
			Arrays.fill(key, (byte) 0);
		}

		return spec;
	}
	
}

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.io;

import java.io.IOException;
import java.security.InvalidKeyException;

import com.arosbio.encryption.EncryptionSpecification;

public interface Saveable {
	
	/**
	 * Save the object to a given {@link com.arosbio.io.DataSink DataSink} in a given path within the {@link com.arosbio.io.DataSink DataSink}. 
	 * @param sink the {@link com.arosbio.io.DataSink DataSink} to save to
	 * @param path the path (or <code>null</code>) within the sink to save to
	 * @param encryptSpec an {@link com.arosbio.encryption.EncryptionSpecification EncryptionSpecification} to encrypt the resource after (or <code>null</code> if no encryption)
	 * @throws IOException Issues writing
	 * @throws InvalidKeyException Invalid {@link com.arosbio.encryption.EncryptionSpecification EncryptionSpecification} 
	 * @throws IllegalStateException The resource is not in a saveable state
	 * 
	 * @see com.arosbio.io.DataSink
	 */
	void saveToDataSink(DataSink sink, String path, EncryptionSpecification encryptSpec) 
			throws IOException, InvalidKeyException, IllegalStateException;
	/**
	 * Loads a Saveable object from a given path in a given {@link com.arosbio.io.DataSource DataSource} object. 
	 * Fails with an {@link java.lang.IllegalAccessException IllegalAccessException}
	 * if it cannot locate anything that the class can load.
	 * @param source the {@link com.arosbio.io.DataSource DataSource} object
	 * @param path the path within the {@link com.arosbio.io.DataSource DataSource} that the data should be loaded from
	 * @param encryptSpec the {@link com.arosbio.encryption.EncryptionSpecification EncryptionSpecification} required to decrypt the data, or <code>null</code> if not encrypted
	 * @throws InvalidKeyException If the {@link com.arosbio.encryption.EncryptionSpecification EncryptionSpecification} cannot decrypt the resource
	 * @throws IOException Issues loading
	 * 
	 * @see com.arosbio.io.DataSink
	 */
	void loadFromDataSource(DataSource source, String path, EncryptionSpecification encryptSpec) 
			throws IOException, InvalidKeyException;

}

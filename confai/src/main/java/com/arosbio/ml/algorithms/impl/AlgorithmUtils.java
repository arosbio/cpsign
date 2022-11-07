/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.algorithms.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.DataSink;
import com.arosbio.io.DataSource;
import com.arosbio.ml.algorithms.MLAlgorithm;

public class AlgorithmUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(AlgorithmUtils.class);
	
	
	public static void loadAlgorithm(DataSource src, String location, MLAlgorithm alg, EncryptionSpecification spec) 
			throws IOException, InvalidKeyException {
		if (spec != null) {
			try (InputStream rawStream = src.getInputStream(location);
					InputStream istream = spec.decryptStream(rawStream);){
				alg.loadFromStream(istream);
			}
		} else {
			try (InputStream istream = src.getInputStream(location);){
				alg.loadFromStream(istream);
			}
		}
	}
	
	public static void saveModelToStream(DataSink sink, String location, MLAlgorithm model, EncryptionSpecification spec) 
			throws IOException, InvalidKeyException {

		if (spec != null){
			try(
					OutputStream modelStream = sink.getOutputStream(location);
					OutputStream encryptedStream = spec.encryptStream(modelStream);){
				model.saveToStream(encryptedStream);
			}
		} else {
			try(OutputStream modelStream = sink.getOutputStream(location);){
				model.saveToStream(modelStream);
			}
		}
		LOGGER.debug("Saved model, location={}",location);

	}


}

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app.params.mixins;

import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.MathUtils;
import com.arosbio.cpsign.app.params.converters.URIConverter;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.encryption.utils.EncryptionSpecFactory;

// import ch.qos.logback.classic.Logger;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;

public class EncryptionMixin {

	private static final Logger LOGGER = LoggerFactory.getLogger(EncryptionMixin.class);

	@ArgGroup(exclusive = true, multiplicity = "0..1")
	public Exclusive exclusive = new Exclusive();

	public static class Exclusive {

		public EncryptionSpecification encryptSpec;

		private void validateOneEncryptAvailable(){
			Iterator<EncryptionSpecification> iterator = EncryptionSpecFactory.getAvailable();
			if (!iterator.hasNext()){
				throw new IllegalArgumentException("%nEncryption is @|bold not available|@, please refer to arosbio.com and the documentation for how to enable encryption.");
			}
			int count = CollectionUtils.count(iterator);
			if (count>1){
				throw new IllegalArgumentException("More than one encryption implementation is available, please make sure that only one (1) is available on the class/module path");
			}
		}

		@Option(names = {"--key"},
			description = "Encryption key (as text) to use for decrypting/encrypting input/output",
			paramLabel = ArgumentType.TEXT
		)
		public void setEncryptKey(String key){
			validateOneEncryptAvailable();
			Iterator<EncryptionSpecification> iterator = EncryptionSpecFactory.getAvailable();
			EncryptionSpecification spec = iterator.next();
			try {
				spec.init(Base64.getDecoder().decode(key));
			} catch (Exception e){
				LOGGER.debug("Failed configuring encryption specification using key as string");
				throw new IllegalArgumentException(e);
			}
			encryptSpec = spec;
		}


		@Option(names = {"--key-file"},
				description = "Encryption key (from file) to use for decrypting/encrypting input/output",
				paramLabel = ArgumentType.URI_OR_PATH,
				converter = URIConverter.class
		)
		public void setEncryptKey(URI encryptFile){
			validateOneEncryptAvailable();
			LOGGER.debug("Attempting to set encryption key from file: {}",encryptFile);

			Iterator<EncryptionSpecification> iterator = EncryptionSpecFactory.getAvailable();
			EncryptionSpecification spec = iterator.next();
			spec.getAllowedKeyLengths();
			int maxKeyLen = MathUtils.max(spec.getAllowedKeyLengths());

			// Read the key
			byte[] key=null, tmpKey=null;
			try {
				
				try (InputStream stream = encryptFile.toURL().openStream()){
					tmpKey = new byte[maxKeyLen];
					int numRead = stream.read(tmpKey);
					if (numRead <= 0){
						LOGGER.debug("No bytes read from input URI");
						throw new IllegalArgumentException("No encryption key could be read from " +encryptFile);
					}
					if (numRead == tmpKey.length){
						key = tmpKey;
					} else {
						// Use only the given bytes 
						key = new byte[numRead];
						System.arraycopy(tmpKey, 0, key, 0, numRead);
					}

				} catch (Exception e){
					LOGGER.debug("Failed reading from encryption key file", e);
					throw new IllegalArgumentException(e.getMessage());
				}

				// Init the encryption spec with the key
				try {
					spec.init(key);
				} catch (Exception e){
					LOGGER.debug("Failed configuring the encryption spec with the key",e);
					throw new IllegalArgumentException(e.getMessage());
				}

				// All OK
				encryptSpec = spec;
			} finally {
				// Make sure to wipe the keys from memory
				if (key != null)
					Arrays.fill(key, (byte)0);
				if (tmpKey != null)
					Arrays.fill(tmpKey, (byte)0);
			}
		}

	}

	

}

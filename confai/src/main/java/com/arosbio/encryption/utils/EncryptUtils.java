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

import java.io.BufferedInputStream;
import java.io.IOException;

import com.arosbio.encryption.EncryptionSpecification;

public class EncryptUtils {
	
	/**
	 * Utility method for checking if a stream is encrypted by a given specification. Returns
	 * a {@link EncryptionStatus} enum with the possible states
	 * @param resource A {@link BufferedInputStream} (so buffer can be reset)
	 * @param spec an {@link EncryptionSpecification} to be checked, or {@code null}
	 * @return an {@link EncryptionStatus} with information about status of the resource
	 * @throws IOException Exceptions from the {@code resource} stream
	 */
	public static EncryptionStatus getStatus(
			BufferedInputStream resource, EncryptionSpecification spec) 
			throws IOException {
		
		if (spec == null){
			return EncryptionStatus.UNKNOWN;
		}

		if (spec.encryptedByType(resource)){
			return spec.canDecrypt(resource) ? EncryptionStatus.ENCRYPTED_CORRECT_SPEC : EncryptionStatus.ENCRYPTED_WRONG_SPEC;
		}
		return EncryptionStatus.UNKNOWN;
		
	}
	
	public enum EncryptionStatus {
		/**
		 * Resource is encrypted, and the supplied specification is the one that can decrypt it
		 */
		ENCRYPTED_CORRECT_SPEC,
		/**
		 * Resource is encrypted, but wrong {@link EncryptionSpecification} was given
		 */
		ENCRYPTED_WRONG_SPEC,
		/**
		 * Either no spec given, or of wrong class
		 */
		UNKNOWN
	}

	

}

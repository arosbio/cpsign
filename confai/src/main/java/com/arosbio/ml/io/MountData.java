/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.security.InvalidKeyException;

import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.IOSettings;
import com.arosbio.io.UriUtils;

import org.apache.commons.io.IOUtils;

/**
 * The {@link MountData} is a specification of either a String of text or a file/URI that should be included in a 
 * serialized form of either data or trained models from {@link ConfAISerializer} or the {@code ModelSerializer} of CPSign.
 * This provides means for saving additional meta data with your acquired data/model. I.e. if you wish to save some help text, 
 * pre-processing information performed prior to CPSign or anything else.    
 * 
 * <p>
 * Note that custom data is not allowed to be saved in certain locations, which can be queried using {@link ModelIO#verifyLocationForCustomData(String)} 
 * (e.g. not allowed in the {@code models/} directory or its sub-directories, or the file {@code cpsign.json}).
 * An {@link IllegalArgumentException} to be thrown if any of these requirements are violated when instantiating an {@link MountData} instance.
 * 
 * 
 * @author staffan
 *
 */
public class MountData {

	private String location;
	private String data;
	private URI resourceToMount;
	private EncryptionSpecification encryptSpec;

	public MountData(String location, String data) throws IllegalArgumentException {
		ModelIO.verifyLocationForCustomData(location);
		this.location = location;
		this.data = data;
		if (data == null)
			this.data = ""; 
	}

	public MountData(String location, URI resourceToMount) throws IllegalArgumentException {
		ModelIO.verifyLocationForCustomData(location);
		this.resourceToMount = resourceToMount;
		this.location = location;
		if (!UriUtils.canReadFromURI(resourceToMount))
			throw new IllegalArgumentException("Cannot read data from URI=" + resourceToMount);
	}

	public void encryptData(EncryptionSpecification spec) {
		this.encryptSpec = spec;
	}

	public String getLocation() {
		return location;
	}

	public void writeData(OutputStream ostream) throws IOException, InvalidKeyException {
		if (encryptSpec != null) {
			ostream = encryptSpec.encryptStream(ostream);
		}
		if (data != null)
			writeString(ostream);
		else if (resourceToMount != null)
			writeURI(ostream);
		
		ostream.close(); // make sure to close stream in the end (IOUtils does not do this for you)
	}

	private void writeURI(OutputStream ostream) throws IOException {
		byte[] buffer = new byte[2048];
		try (InputStream is = resourceToMount.toURL().openStream()){

			int bytesRead = 0;
			while ((bytesRead = is.read(buffer)) != -1) {
				ostream.write(buffer, 0, bytesRead);
			}
			ostream.flush();
		}
	}

	private void writeString(OutputStream ostream) throws IOException {
		IOUtils.write(data, ostream, IOSettings.CHARSET);
		ostream.flush();
	}

}

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.encryption;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;

/**
 * The {@link EncryptionSpecification} interface is the interface that all types of encryption are required to 
 * implement. 
 * <p>
 * All specifications are <b>required to include knowledge if it can decrypt a given {@link InputStream}</b>.
 * This can for instance be achieved by adding a bit of plain text in be beginning of each stream. The String
 * {@code Encrypted(AES)} is reserved for the implementation supplied by Aros Bio. 
 * <p>
 * The two methods {@link #encryptedByType(BufferedInputStream)} and {@link #canDecrypt(BufferedInputStream)} differs in
 * that the former checks if an instance of the class was used for encrypting the stream, and the latter if the concrete 
 * instance can decrypt the stream (i.e. the latter has the correct key to decrypt it).
 * <p>
 * For security reasons the encryption keys are encoded as {@code byte[]}, which can be overwritten once it is no longer needed.
 * E.g. by calling {@code Arrays.fill(key, (byte)0);}.
 *  
 *  
 * @author staffan
 *
 */
public interface EncryptionSpecification extends Cloneable, javax.security.auth.Destroyable {

	/**
	 * Name of the implementation. Useful in case several specs are available, i.e. this name
	 * should be unique for this class but not for individual instances of the class.
	 * @return the name of the implementation
	 */
	public String getName();

	/**
	 * Give some details about the implementation. This may include details about settings that
	 * can be tweaked, such as setting different length of keys and the preferred key-length etc. 
	 * <b>Note:</b> this is mainly intended for CLI users as API users can use the {@link java.util.ServiceLoader ServiceLoader}
	 * and read java doc for further details and possibly have access to more parameters than possible from the CLI
	 * @return a text with details.
	 */
	public String getInfo();

	/**
	 * Get the length of the allowed key lengths in number of bytes (i.e. number of {@code bytes} that should be sent to {@link #init(byte[])}.
	 * E.g. multiple lengths can be allowed, depending on the specific implementation.
	 * @return an array of allowed lengths
	 */
	public int[] getAllowedKeyLengths();
	
	/**
	 * Generate a random key that can be used by the user. Uses a custom key length, that 
	 * should correspond to one given from the {@link EncryptionSpecification#getAllowedKeyLengths()} 
	 * method. This method should thrown an {@link IllegalArgumentException} in case the {@code length}
	 * parameter is not one of the allowed lengths. 
	 * @param length the length of the key, in bytes, should be &gt;0
	 * @return an encryption key 
	 * @throws IllegalArgumentException In case the {@code length} parameter is &le; 0
	 */
	public byte[] generateRandomKey(int length) throws IllegalArgumentException;

	/**
	 * Initializes the instance with an encryption key. The {@code key} should be
	 * of a length that the class allows, e.g. retrieved using the method {@link #getAllowedKeyLengths()}.
	 * @param key a {@code byte[]} with the actual key
	 * @throws InvalidKeyException If the given encryption key is faulty in some way, or could not instantiate the instance for some reason
	 */
	public void init(byte[] key) throws InvalidKeyException;

	/**
	 * This method adds a layer of encryption to the {@link OutputStream}.
	 * <b>NOTE</b> It is up to the caller to make sure to call the {@link OutputStream#close()} 
	 * (explicitly or implicitly) once everything is written to the stream.  
	 * @param outStream The {@link OutputStream} that should be encrypted
	 * @return The encrypted {@link OutputStream}
	 * @throws InvalidKeyException If the given encryption key is faulty
	 * @throws IOException Problem with the Stream
	 */
	public OutputStream encryptStream(OutputStream outStream) throws InvalidKeyException, IOException;
	
	/**
	 * This method decrypts in the same fashion as {@link #encryptStream(OutputStream)} 
	 * encrypts an {@link InputStream}. 
	 * @param inStream An encrypted {@link InputStream} that should be decrypted 
	 * @return The {@link InputStream}, but decrypted
	 * @throws IOException Problem with the Stream
	 * @throws InvalidKeyException If this {@link EncryptionSpecification} cannot decrypt the stream
	 */
	public InputStream decryptStream(InputStream inStream) throws InvalidKeyException, IOException;
	
	/**
	 * Check if a stream is encrypted by this implementation of {@link EncryptionSpecification}. 
	 * This method <b>MUST</b> reset the stream so other methods 
	 * will see the same data. Use {@link BufferedInputStream#mark(int)} to mark the stream and 
	 * {@link BufferedInputStream#reset()} to reset it after. 
	 * This method should tests if it's encrypted for this specific implementation of 
	 * {@link EncryptionSpecification}, <b>not</b> that it actually can decrypt it. 
	 * <b>Does NOT check if the key itself is correct</b>, for this, use the {@link #canDecrypt(BufferedInputStream) canDecrypt} 
	 * method instead.
	 * @param inStream The {@link BufferedInputStream} that <i>might</i> be encrypted
	 * @return {@code true} if the stream was encrypted by this specific {@link EncryptionSpecification}, {@code false} otherwise
	 * @throws IOException Problem with the Stream
	 */
	public boolean encryptedByType(BufferedInputStream inStream) throws IOException;
	
	/**
	 * Check if the current {@link EncryptionSpecification} can decrypt the given stream. The 
	 * stream should be buffered and <b>MUST</b> reset it internally, so that it can be read
	 * normally after calling this method.
	 * @param inStream {@link InputStream} to decrypt
	 * @return {@code true} if the current specification can decrypt the stream, {@code false} otherwise
	 */
	public boolean canDecrypt(BufferedInputStream inStream);
	
	public EncryptionSpecification clone();
}

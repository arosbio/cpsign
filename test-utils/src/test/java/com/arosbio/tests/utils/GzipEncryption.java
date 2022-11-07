/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.tests.utils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.encryption.EncryptionSpecification;

public class GzipEncryption implements EncryptionSpecification {

    private static final Logger LOGGER = LoggerFactory.getLogger(GzipEncryption.class);

    private int encNumber = -1;
    private final static String PRE_TEXT = "gzip=";
    public final static int ALLOWED_KEY_LEN = 16;

    public GzipEncryption(){}

    public GzipEncryption(String key) throws IllegalArgumentException {
        try{
            this.init(Arrays.copyOf(key.getBytes(StandardCharsets.UTF_8), ALLOWED_KEY_LEN));
        } catch (Exception e){
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public boolean canDecrypt(BufferedInputStream stream) {
        stream.mark(200);

        byte[] toRead = new byte[PRE_TEXT.getBytes(StandardCharsets.UTF_8).length+4];



        try {
            int nRead = stream.read(toRead);
            if (nRead<toRead.length)
                return false;
            return getUniqueStartText().equals(new String(toRead,StandardCharsets.UTF_8));
        } catch (Exception e){
            return false;
        } finally{
            try {
                stream.reset();
            } catch (IOException e){
                LOGGER.trace("failed reset in canDecrypt, id={}",encNumber);
            }
        } 
    }

    

    @Override
    public InputStream decryptStream(InputStream stream) throws InvalidKeyException, IOException {
        if (encNumber == -1){
            throw new InvalidKeyException("Not inited");
        }
        stream.mark(200);

        byte[] beginning = new byte[PRE_TEXT.getBytes(StandardCharsets.UTF_8).length + 4]; // Unique number padded into 4 bytes
        int nRead = stream.read(beginning);
        if (nRead < beginning.length)
            throw new InvalidKeyException("Not encrypted by spec");
        String txt = new String(beginning, StandardCharsets.UTF_8);

        LOGGER.trace("Read the start: {} from potentially encrypted stream",txt);
        String uniquePreTxt = getUniqueStartText();
        if (!txt.equals(uniquePreTxt)){
            // If not correct, we reset the stream and throw an exception 
            try{
                stream.reset();
                LOGGER.trace("reset complete in decryptStream - exception thrown (id={})",encNumber);
            } catch (IOException e){
                LOGGER.trace("failed resetting in decryptStream (id={})",encNumber);
            }
            throw new InvalidKeyException("Invalid key");
        }
        
        // return the stream, but with these bytes read - should be possible to read now
        return new GZIPInputStream(stream);
    }

    @Override
    public OutputStream encryptStream(OutputStream stream) throws InvalidKeyException, IOException {
        LOGGER.trace("Printing start-text to encryptStream: {}",getUniqueStartText());
        stream.write(getUniqueStartText().getBytes(StandardCharsets.UTF_8));
        stream.flush();
        return new GZIPOutputStream(stream);
    }

    private String getUniqueStartText(){
        return String.format("%s%4d",PRE_TEXT,encNumber);
    }

    @Override
    public byte[] generateRandomKey(int length) throws IllegalArgumentException {
        if (length != ALLOWED_KEY_LEN)
            throw new IllegalArgumentException("Invalid key length: " + length);
        return SecureRandom.getSeed(length);
    }

    @Override
    public int[] getAllowedKeyLengths() {
        return new int[]{ALLOWED_KEY_LEN};
    }

    @Override
    public String getInfo() {
        return "test 'encryption' - not to be used in proper use cases! just writing a longer info-string so that it will spann multiple rows";
    }

    @Override
    public String getName() {
        return "gzip";
    }

    @Override
    public void init(byte[] arg0) throws InvalidKeyException {
        if (arg0 == null || arg0.length <=0)
            throw new InvalidKeyException();
        encNumber = 0;
        for (int i=0;i<arg0.length;i++){
            encNumber += (int)arg0[i];
        }
        encNumber = encNumber % 1000;
    }

    @Override
    public boolean encryptedByType(BufferedInputStream stream) throws IOException {
        stream.mark(100);
        byte[] beginning = new byte[PRE_TEXT.getBytes().length];
        stream.read(beginning);
        stream.reset();
        String txt = new String(beginning);
        LOGGER.trace("Read text: {} in usedForEncrypting",txt );
        if (txt.equals(PRE_TEXT)){
            return true;
        }
        return false;
    }

    public String toString(){
        return "gzip test encryption";
    }
   
    public GzipEncryption clone(){
        GzipEncryption clone =  new GzipEncryption();
        clone.encNumber = encNumber;
        return clone;
    }
    
}

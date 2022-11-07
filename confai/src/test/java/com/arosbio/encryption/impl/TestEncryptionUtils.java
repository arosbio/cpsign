/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.encryption.impl;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.encryption.utils.EncryptUtils;
import com.arosbio.encryption.utils.EncryptUtils.EncryptionStatus;
import com.arosbio.tests.utils.GzipEncryption;

public class TestEncryptionUtils {

	static List<String> lines;
	static byte[] encryptedBytes;
	static EncryptionSpecification defaultSpec;

	@BeforeClass
	public static void initEncryptedText() throws Exception{
		lines = new ArrayList<>();
		lines.add("Here we're writing some new text");
		lines.add("Second line of text");
		lines.add("Third line... ");

		defaultSpec = new GzipEncryption("password and salt");
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try(
				OutputStream encryptedStream = defaultSpec.encryptStream(baos);
				PrintWriter writer = new PrintWriter(encryptedStream);
				){
			for(String line: lines){
				writer.write(line);
				writer.println();
			}
		}
		baos.close();
		encryptedBytes = baos.toByteArray();
	}

	@Test
	public void testEncryptedFile() throws Exception {

		try(ByteArrayInputStream bais = new ByteArrayInputStream(encryptedBytes);
				BufferedInputStream encryptedStream = new BufferedInputStream(bais);){
			EncryptionStatus status = EncryptUtils.getStatus(encryptedStream, defaultSpec);
			// System.out.println("status="+status);
			Assert.assertTrue(EncryptionStatus.ENCRYPTED_CORRECT_SPEC.equals(status));

			// Make sure we can actually read the stream after checking encryption-status!
			try(BufferedReader reader = new BufferedReader(new InputStreamReader(defaultSpec.decryptStream(encryptedStream)));){
				List<String> decryptedLines = new ArrayList<>();
				String line;
				while((line = reader.readLine())!=null){
					decryptedLines.add(line);
				}
				Assert.assertEquals(lines, decryptedLines);
			}
		}


	}

	@Test
	public void testPlainText() throws Exception {
		String unEncryptedText = "Hej pa dig";
		byte[] plainBytes = unEncryptedText.getBytes("UTF-8");
		try(
				ByteArrayInputStream bais = new ByteArrayInputStream(plainBytes);
				BufferedInputStream bufferedStream = new BufferedInputStream(bais);){
			EncryptionStatus status = EncryptUtils.getStatus(bufferedStream, defaultSpec);
//			System.out.println("status="+status);
			Assert.assertTrue(EncryptionStatus.UNKNOWN.equals(status));
			String shouldBeUnchanged = IOUtils.toString(bufferedStream, "UTF-8");
			Assert.assertEquals(unEncryptedText, shouldBeUnchanged);
		}
	}

	@Test
	public void testWrongKey() throws Exception {

	}


}

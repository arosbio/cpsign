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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Assert;

import com.arosbio.encryption.EncryptionSpecification;
import com.google.common.collect.Ordering;

public class TestUtils {
	
	public static InputStream getStream(String file){
		return TestUtils.class.getResourceAsStream(file);
	}

	public static File getFile(String file){
		return new File(TestUtils.class.getResource(file).getFile());
	}

	public static InputStream getStreamFromAbsPath(String absPath) throws IOException{
		return new FileInputStream(new File(absPath));
	}

	public static String getPath(String file){
		return new File(TestUtils.class.getResource(file).getFile()).getAbsolutePath();
	}

	public static URI getURI(String file) throws Exception {
		return TestUtils.class.getResource(file).toURI();
	}

	public static URI getURIFromFullPath(String path) throws IOException {
		return new File(path).toURI();
	}
	
	public static File createTempFile(String fileName, String fileEnding) throws IOException {
		File tmpFile = File.createTempFile(fileName, fileEnding); 
		tmpFile.deleteOnExit();
		return tmpFile;
	}

		/**
	 * Get an initialized EncryptionSpecification object, with a randomly generated key 
	 */
	public static EncryptionSpecification getSpec(){
		try{
			EncryptionSpecification spec = new GzipEncryption();
			spec.init(spec.generateRandomKey(GzipEncryption.ALLOWED_KEY_LEN));
			return spec;
		} catch (Exception e){
			Assert.fail("Failed generating a random encryption spec: "+e.getMessage());
			return null;
		}
	}

	/**
	 * Generates and init's an EncryptionSpecification based on the key. The 
	 * key is not required to be "good" - but the resulting spec should be deterministic
	 * @param key
	 * @return
	 */
	public static EncryptionSpecification getSpec(String key){
		try {
			byte[] bytes = key.getBytes(StandardCharsets.UTF_16);
			if (bytes.length < GzipEncryption.ALLOWED_KEY_LEN){
				byte[] tmp = new byte[GzipEncryption.ALLOWED_KEY_LEN];
				// Copy the first bytes from the input
				System.arraycopy(bytes, 0, tmp, 0, bytes.length);
				// Fill the rest with 42
				Arrays.fill(tmp, bytes.length, tmp.length, (byte)42);
				bytes = tmp;
			}
			GzipEncryption spec = new GzipEncryption();
			spec.init(bytes);
			return spec;
		} catch (Exception e){
			Assert.fail("Could not init the GzipEncryption spec: " + e.getMessage());
			return null;
		}
	}

	public static String toString(double[] array) {
		String str = "";
		for (int i=0; i<array.length-1; i++) {
			str += array[i];
			str += ',';
		}
		if (array.length> 0)
			str+=array[array.length-1];
		return str;
	}

	public static void assertEquals(List<Double> l1, List<Double> l2){
		if (l1.size() != l2.size())
			Assert.fail("Lists of different length");
		for (int i=0; i<l1.size(); i++){
			Assert.assertEquals(l1.get(i), l2.get(i),0.000001);
		}
	}

	/**
	 * Assert that two maps match completely, both keys and values.
	 * @param <K>
	 * @param <V>
	 * @param m
	 * @param m2
	 */
	public static <K,V>void assertEquals(Map<K,V> m, Map<K,V> m2){
		assertEquals(m, m2,0.00001);
	}
	
	/**
	 * Assert that two maps match completely, floating point values allowed 
	 * difference thresholded to {@code diff}
	 * @param <K>
	 * @param <V>
	 * @param m
	 * @param m2
	 * @param diff
	 */
	public static <K,V>void assertEquals(Map<K,V> m, Map<K,V> m2, double diff){
		if(m.size() != m2.size())
			Assert.fail("different sizes");
		for (K key : m.keySet()){
			Assert.assertTrue("key="+key, m2.containsKey(key));
			try {
				// Special treat floating point values
				if (m.get(key) instanceof Double){
					double d1 = (double) m.get(key);
					double d2 = (double) m2.get(key);
					Assert.assertEquals("key="+key,d1, d2,diff);
				} else if (m.get(key) instanceof Float){
					float d1 = (float) m.get(key);
					float d2 = (float) m2.get(key);
					Assert.assertEquals("key="+key,d1, d2,diff);
				} else
					Assert.assertEquals("key="+key,m.get(key), m2.get(key));
			} catch (Exception e){
				Assert.fail(e.getMessage());
			}
		}
		
	}
	
	public static <K> void assertEqualsSum(Map<K,Double> m, Map<K,Double> m2, double allowedDiff) {
		double diff = 0d;
		if(m.size() != m2.size())
			Assert.fail("different sizes");
		Assert.assertEquals(m.keySet(), m2.keySet());
		for (K key : m.keySet()) {
			diff += Math.abs(m.get(key) - m2.get(key));
		}
		Assert.assertTrue("Total diff " + diff + ", larger than allowed " + allowedDiff, allowedDiff > diff);
	}
	
	public static void assertEquals(double[] a, double[] b) {
		if (a == b)
			return;
		if (a == null || b == null)
			Assert.fail("One of the arrays are null");
		
		if (a.length != b.length)
			Assert.fail("Arrays of different length");
		
		for (int i=0; i<a.length; i++) {
			Assert.assertEquals(a[i], b[i], 0.00001);
		}
	}
	
	public static <T extends Comparable<T>> void assertSorted(List<T> vals) {
		Assert.assertTrue(Ordering.natural().isOrdered(vals));
	}
	
	public static void assertSimilar(double val1, double val2, double percentage) {
		if (val1==val2)
			return;
			
		Assert.assertTrue(""+val1+" and " + val2 + " differing with more than " + percentage + " percent",Math.abs(val1-val2)/val2 <= percentage);
		Assert.assertTrue(Math.abs(val2-val1)/val1 <= percentage);
	}
	
	
	public static void assertTextContainsIgnoreCase(final String template, String... ts) {
		List<String> notThere = new ArrayList<>();
		String templateUP = template.toUpperCase();
		for (String t : ts) {
			if (! templateUP.contains(t.toUpperCase()))
				notThere.add(t);
		}
		if (! notThere.isEmpty())
			Assert.fail("Strings not contained by text: "+ notThere);
		
	}
	
	public static String toString(List<?> list, char sep) {
		StringBuilder sb = new StringBuilder();
		for (int i=0;i<list.size(); i++) {
			sb.append(list.get(i));
			if (i<list.size()-1)
				sb.append(sep);
		}
		return sb.toString();
	}
	
	public static class StringOutputStream extends ByteArrayOutputStream {
		
		public String toString() {
			try {
				flush();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			try {
				return super.toString(StandardCharsets.UTF_8.name());
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
				return "failed";
			}
		}
		
	}

	/**
	 * Helper method to count number of lines in a file.
	 * 
	 * @param filename
	 * @return
	 * @throws IOException
	 */
	public static int countLines(String filename) throws IOException {
		InputStream is = new BufferedInputStream(new FileInputStream(filename));
		try {
			byte[] c = new byte[1024];
			int count = 0;
			int readChars = 0;
			boolean empty = true;
			while ((readChars = is.read(c)) != -1) {
				empty = false;
				for (int i = 0; i < readChars; ++i) {
					if (c[i] == '\n') {
						++count;
					}
				}
			}
			return (count == 0 && !empty) ? 1 : count;
		} finally {
			is.close();
		}
	}
	
}

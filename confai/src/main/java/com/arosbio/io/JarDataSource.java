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
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarDataSource implements DataSource, AutoCloseable {

	private JarFile jar;
	public JarDataSource(JarFile jar) {
		this.jar = jar;
	}
	@Override
	public InputStream getInputStream(String name) throws IOException {
		return jar.getInputStream(jar.getEntry(name));
	}

	@Override
	public boolean hasEntry(String entry) {
		return jar.getEntry(entry) != null;
	}
	@Override
	public Iterator<DataEntry> entries() {
		return new DSEntryEnumerator(jar.entries());
	}
	
	@Override
	public void close() throws IOException {
		jar.close();
	}
	
	private static class DSEntryEnumerator implements Iterator<DataEntry>{
		private Enumeration<JarEntry> jarEnumerator;
		public DSEntryEnumerator(Enumeration<JarEntry> jarEnumerator) {
			this.jarEnumerator = jarEnumerator;
		}
		@Override
		public boolean hasNext() {
			return jarEnumerator.hasMoreElements();
		}
		@Override
		public DataEntry next() {
			return new JarDataSourceEntry(jarEnumerator.nextElement());
		}
		@Override
		public void remove() {
			// not used 
		}
		
	}
	
	private static class JarDataSourceEntry implements DataEntry{
		private JarEntry je; 
		public JarDataSourceEntry(JarEntry jarEntry) {
			this.je = jarEntry;
		}
		@Override
		public String getName() {
			return je.getName();
		}
		@Override
		public boolean isDirectory() {
			return je.isDirectory();
		}
		
		public String toString() {
			return getName();
		}
		
	}

}

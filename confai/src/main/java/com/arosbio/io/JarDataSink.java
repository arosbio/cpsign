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
import java.io.OutputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipException;

public class JarDataSink implements DataSink {

	private JarOutputStream jar;

	public JarDataSink(JarOutputStream jar) {
		this.jar = jar;
	}

	@Override
	public void createDirectory(String directoryName) throws IOException {
		if (directoryName==null || directoryName.isEmpty())
			throw new IOException("Cannot create an empty directory in Jar");
		if (!directoryName.endsWith("/"))
			directoryName+='/';
		try {
			jar.putNextEntry(new JarEntry(directoryName));
			jar.closeEntry();
		} catch (ZipException e) {
			// thrown if duplicate creation of a directory
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	@Override
	public OutputStream getOutputStream(String name) throws IOException {
		jar.putNextEntry(new JarEntry(name));
		return new JarWrapperOutputStream(jar);
	}

	@Override
	public void closeEntry() throws IOException {
		jar.closeEntry();
	}

	@Override
	public void close() throws IOException {
		jar.close();
	}

}

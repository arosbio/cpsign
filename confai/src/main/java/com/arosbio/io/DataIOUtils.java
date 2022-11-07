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
import java.util.Iterator;

import com.arosbio.io.DataSource.DataEntry;

public class DataIOUtils {

	/**
	 * Utility method for locating the base-path in the {@link DataSource}
	 * @param src the DataSource to look in
	 * @param givenPath (optional) given path or <code>null</code>
	 * @param requiredStart A required start (cannot be null!) and which will be included in the returned String. Is matched at the <b>end</b> of the path
	 * @return The base path, including the <code>requiredStart</code> at the end of the string
	 * @throws IOException In case no such path can be located
	 */
	public static String locateBasePath(final DataSource src, final String givenPath, final String requiredStart) throws IOException {
		String lead = (givenPath != null ? givenPath : "");
		if (requiredStart == null || requiredStart.isEmpty())
			throw new IllegalArgumentException("The requiredStart parameter cannot be null or empty string!");
		Iterator<DataEntry>entries = src.entries();
		DataEntry currentEntry=null;
		String entryName=null;
		while (entries.hasNext()){
			currentEntry = entries.next();
			entryName = currentEntry.getName();
			if (entryName.startsWith(lead) && entryName.contains(requiredStart)) {
				return entryName.substring(0, 
						entryName.lastIndexOf(requiredStart) + requiredStart.length());
			}
		}
		if (lead.length()>0)
			throw new IOException("No entry starting with " + givenPath + " and ending with " + requiredStart);
		else
			throw new IOException("No entry matching " + requiredStart);

	}
	
	public static String appendTrailingDash(String input) {
		if (input==null || input.isEmpty())
			return "";
		return input.endsWith("/") ? input : input+'/';
	}

	public static String createBaseDirectory(final DataSink sink, final String basePath, final String directoryName) 
			throws IOException {
		String baseDir = basePath;
		// Make sure the base path is either empty
		if (basePath==null || basePath.isEmpty())
			baseDir="";
		// Or a directory
		else {
			baseDir += (baseDir.endsWith("/")? "" : '/');
			try {
				sink.createDirectory(baseDir);
			} catch (IOException e) {}
		}

		// Create the new directory - 
		String newDir = baseDir + directoryName;
		if (! newDir.endsWith("/")) {
			newDir += '/';
		}
		try {
			sink.createDirectory(newDir);
		} catch (IOException e) {}
		return newDir;
	}

}

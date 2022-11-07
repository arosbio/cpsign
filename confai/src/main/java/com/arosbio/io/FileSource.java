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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public class FileSource implements DataSource {

	private File baseDir;
	
	public FileSource(File baseDir) {
		super();
		this.baseDir = baseDir;
		
	}
	
	@Override
	public InputStream getInputStream(String name) throws IOException {
		return new FileInputStream(new File(baseDir,name));
	}

	@Override
	public boolean hasEntry(String entry) {
		return new File(baseDir,entry).exists();
	}

	@Override
	public Iterator<DataEntry> entries() {
		
		return new Iterator<DataEntry>() {
			private File[] files=baseDir.listFiles();
			private int i = 0;
			@Override
			public boolean hasNext() {
				
				return i<files.length;
			}

			@Override
			public DataEntry next() {
				final File nextFile = files[i];
				i++;
				return new DataEntry() {
					
					@Override
					public boolean isDirectory() {

						return nextFile.isDirectory();
					}
					
					@Override
					public String getName() {
						return nextFile.getName();
					}
				};
			}

			@Override
			public void remove() {
				// Do nothing
			}
		};
	}

}

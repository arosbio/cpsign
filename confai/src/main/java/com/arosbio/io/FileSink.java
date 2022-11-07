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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.io.FileUtils;

public class FileSink implements DataSink {
	
	private File baseDir;
	private OutputStream currentOutput;
	
	public FileSink(File baseDir) throws IOException {
		this.baseDir = baseDir;
		FileUtils.forceMkdir(baseDir);
	}

	@Override
	public void createDirectory(String directoryName) throws IOException {
		FileUtils.forceMkdir(new File(baseDir,directoryName));
	}

	@Override
	public OutputStream getOutputStream(String name) throws IOException {
		return new FileOutputStream(new File(baseDir,name));
	}

	@Override
	public void closeEntry() throws IOException {
		if(currentOutput!=null){
			try{
				currentOutput.close();
			} catch(IOException e){}
		}
	}

	@Override
	public void close() throws IOException {
		// try to close the currentOutput (if any)
		closeEntry();
	}

}

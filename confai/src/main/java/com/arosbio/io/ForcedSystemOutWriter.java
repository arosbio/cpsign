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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.output.NullOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class that ensures that printing to system out is restricted to holders of an instance
 * of this class. Note that there can only be a single instance of this class at one point and the 
 * {@link #close()} method must be called before a new instance can be created. All write calls to 
 * System.out will be disregarded and pointed to a {@link org.apache.commons.io.output.NullOutputStream}.
 * This makes sure that output can be printed in GZIP format to the system-out (and likely piped to 
 * a file or other process). Otherwise the output would be broken.
 * 
 * @author staffan
 *
 */
public class ForcedSystemOutWriter extends BufferedWriter {

	private static final Logger LOGGER = LoggerFactory.getLogger(ForcedSystemOutWriter.class);
	private final static IOException WRITER_CLOSED_EXCEPT = new IOException("Writer closed");

	private static PrintStream originalSysOut;
	private static GZIPOutputStream gzip;

	private static ForcedSystemOutWriter instance;

	private boolean isOpen = false;

	public ForcedSystemOutWriter(boolean compress) throws IOException {
		super(takeControl(compress));
		instance = this;
		isOpen = true;
		LOGGER.debug("Finished init of ForcedSystemOutWriter");
	}

	private synchronized static Writer takeControl(boolean compress) throws IOException {
		LOGGER.debug("Attempting to take control over system-out printing");
		if (instance != null) {
			LOGGER.debug("Failed to take control - there's already an active instance");
			throw new IOException("Already an active instance of the System-out-writer");
		}
		// The original print stream
		originalSysOut = System.out;
		OutputStream out = System.out;

		// remove all other prints to System.out!
		System.setOut(new NullPrintStream());
		
		if (compress) {
			gzip = new GZIPOutputStream(out);
			out = gzip; 
		}
		return new OutputStreamWriter(out, IOSettings.CHARSET);
	}

	private static class NullPrintStream extends PrintStream {

		public NullPrintStream() {
			super(NullOutputStream.NULL_OUTPUT_STREAM);
		}

		public void close(){
			// do nothing
		}

	}

	public void write(int c) throws IOException {
		if (isOpen){
			super.write(c);
		} else {
			throw WRITER_CLOSED_EXCEPT;
		}
    }
    
    public void write(char cbuf[], int off, int len) throws IOException {
		if (isOpen){
			super.write(cbuf, off, len);
		} else {
			throw WRITER_CLOSED_EXCEPT;
		}
       
    }

    public void write(String s, int off, int len) throws IOException {
		if (isOpen){
			super.write(s,off,len);
		} else {
			throw WRITER_CLOSED_EXCEPT;
		}
    }

    /**
     * Flushes the stream.
     *
     * @exception  IOException  If an I/O error occurs
     */
    public void flush() throws IOException {
		if (isOpen){
			super.flush();
		} else {
			throw WRITER_CLOSED_EXCEPT;
		}
    }

	

	@Override
	public synchronized void close() throws IOException {
		if (instance != null) {
			flush(); // Flush to GZIP
			if (gzip!=null) {
				gzip.finish();
				flush();
			}
			// reset the output to original
			System.setOut(originalSysOut);
			instance = null; // release the instance so a new one can be created
			gzip = null;
			LOGGER.debug("Closed ForcedSystemOutWriter");
		} else {
			LOGGER.debug("Called close on ForcedSystemOutWriter without an active instance");
		}
		isOpen = false;
	}

}

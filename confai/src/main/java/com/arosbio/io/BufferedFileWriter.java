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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.zip.GZIPOutputStream;

public class BufferedFileWriter extends BufferedWriter {

	private GZIPOutputStream gzipStream;
	
	private BufferedFileWriter(Writer writer) {
		super(writer);
	}
	
	public static BufferedFileWriter getFileWriter(File outputFile) throws IOException {
		setupFile(outputFile);
		
		OutputStream ostream = new FileOutputStream(outputFile);
		return new BufferedFileWriter(new OutputStreamWriter(ostream, IOSettings.CHARSET));
	}
	
	public static BufferedFileWriter getCompressedFileWriter(File outputFile) throws IOException {
		setupFile(outputFile);
		
		OutputStream ostream = new FileOutputStream(outputFile);
		GZIPOutputStream gzip = new GZIPOutputStream(ostream, true);
		BufferedFileWriter bfw = new BufferedFileWriter(new OutputStreamWriter(gzip, IOSettings.CHARSET));
		
		bfw.gzipStream = gzip;
		return bfw;
	}
	
	private static void setupFile(File outputFile) throws IOException {
		if (outputFile==null)
			throw new IOException("No output file given");
		UriUtils.createParentOfFile(outputFile);
	}

//	public BufferedFileWriter(File outputFile, boolean compress) throws IOException {
//
//		// Check if outputFile is already created, otherwise try to create it
//		if (outputFile==null)
//			throw new IOException("No output file given");
//		if (!outputFile.exists()) {
//			File outputParent = outputFile.getParentFile();
//			if (outputParent!=null){
//				if (! outputParent.exists()){
//					try{
//						FileUtils.forceMkdir(outputParent);
//					} catch (IOException e){
//						throw new IOException("Could not create parent folders to outputfile: " + e.getMessage());
//					}
//				}
//			}
//		}
//
//		// File should be there
//		OutputStream ostream = new FileOutputStream(outputFile);
//		if (compress) {
//			gzipStream = new GZIPOutputStream(ostream, true);
//			ostream = gzipStream;
//		}
//
//		// Buffer the stream
//		writer = new BufferedWriter(new OutputStreamWriter(ostream, IOSettings.CHARSET));
//	}

//	@Override
//	public void flush() throws IOException{
//		writer.flush();
//		if(gzipStream!=null)
//			gzipStream.flush();
//	}

	@Override
	public void close() throws IOException{
		flush();
		if (gzipStream!=null)
			gzipStream.finish();
		super.close();
	}

}

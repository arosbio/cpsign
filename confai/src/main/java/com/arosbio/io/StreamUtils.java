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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

public class StreamUtils {

	/**
	 * Check if a stream is GZipped or not. This requires that the user sends a BufferedInputStream because
	 * we need to peak on the first few bytes, and these will be lost in case we create a new BufferedInputStream
	 * within this method and not return it to the user!
	 * @param stream a buffered stream
	 * @return {@code true} if the stream is compressed using gzip, {@code false} otherwise
	 * @throws IOException Any issues reading/resetting the {@code stream}
	 */
	public static boolean isGZIPCompressed(BufferedInputStream stream) throws IOException{

		stream.mark(2);
		int magic = 0;
		try {
			magic = stream.read() & 0xff | ((stream.read() << 8) & 0xff00);
			stream.reset();
		} catch (IOException e) {
			return false;
		}
		return magic == GZIPInputStream.GZIP_MAGIC;
	}

	/**
	 * Takes a InputStream, checks if it's GZIP'ed, if it is: unZip it, otherwise just 
	 * return the InputStream as it is (untouched) 
	 * @param stream a stream that may be compressed
	 * @return A normal InputStream without compression
	 * @throws IOException Any issues reading/resetting the {@code stream}
	 */
	public static InputStream unZIP(InputStream stream) throws IOException {
		if (stream instanceof BufferedInputStream) {
			if (isGZIPCompressed((BufferedInputStream)stream))
				return new GZIPInputStream(stream);
			else
				return stream;
		} else {
			BufferedInputStream buffStream = new BufferedInputStream(stream);
			if (isGZIPCompressed(buffStream))
				return new GZIPInputStream(buffStream);
			else
				return buffStream;
		}
	}
	
	public static BufferedOutputStream getBufferedOutputStream(File file) throws FileNotFoundException{
		return new BufferedOutputStream(new FileOutputStream(file));
	}

}

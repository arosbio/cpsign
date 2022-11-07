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
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CollectionsWriter extends Writer {
	
	private final Collection<Writer> writers;
	
	public CollectionsWriter(final Collection<Writer> writers) {
		if (writers == null || writers.isEmpty())
			throw new IllegalArgumentException("CollectionsWriter cannot use no writers");
		this.writers = writers;
	}

	@Override
	public void write(char[] cbuf, int off, int len) throws IOException {
		List<IOException> ex = new ArrayList<>();
		for (Writer w: writers) {
			try {
				w.write(cbuf, off, len);
			} catch (IOException e) {
				ex.add(e);
			}
		}
		if (!ex.isEmpty()) {
			String msgs = "";
			for (IOException e: ex) {
				msgs += e.getMessage();
			}
			throw new IOException(msgs,ex.get(0).getCause());
		}
		
	}

	@Override
	public void flush() throws IOException {
		List<IOException> ex = new ArrayList<>();
		for (Writer w: writers) {
			try {
				w.flush();
			} catch (IOException e) {
				ex.add(e);
			}
		}
		if (!ex.isEmpty()) {
			String msgs = "";
			for (IOException e: ex) {
				msgs += e.getMessage();
			}
			throw new IOException(msgs,ex.get(0).getCause());
		}
	}

	@Override
	public void close() throws IOException {
		List<IOException> ex = new ArrayList<>();
		for (Writer w: writers) {
			try {
				w.close();
			} catch (IOException e) {
				ex.add(e);
			}
		}
		if (!ex.isEmpty()) {
			String msgs = "";
			for (IOException e: ex) {
				msgs += e.getMessage();
			}
			throw new IOException(msgs,ex.get(0).getCause());
		}
	}

}

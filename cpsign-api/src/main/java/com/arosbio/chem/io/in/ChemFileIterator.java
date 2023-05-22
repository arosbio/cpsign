/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem.io.in;

import java.io.Closeable;
import java.util.Iterator;

import org.openscience.cdk.interfaces.IAtomContainer;

public interface ChemFileIterator extends Iterator<IAtomContainer>, Closeable {

	/**
	 * Set a {@link ProgressTracker} to monitor early stopping
	 * @param tracker the tracker to use
	 */
	public void setProgressTracker(ProgressTracker tracker);

	/**
	 * Get the currently used {@link ProgressTracker} instance
	 * @return the {@link ProgressTracker} that is used
	 */
	public ProgressTracker getProgressTracker();

	/**
	 * Set a {@link ProgressTracker} to monitor early stopping
	 * @param tracker the tracker to use
	 * @return The same instance
	 */
	public ChemFileIterator withProgressTracker(ProgressTracker tracker);

}

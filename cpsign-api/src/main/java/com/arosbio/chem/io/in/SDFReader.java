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

import java.io.InputStream;
import java.io.Reader;

import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.io.iterator.IteratingSDFReader;
import org.openscience.cdk.silent.SilentChemObjectBuilder;

import com.arosbio.chem.CPSignMolProperties;

public class SDFReader extends IteratingSDFReader implements ChemFileIterator {

	private int recordIndex = 0;
	
	public SDFReader(InputStream in, IChemObjectBuilder builder) {
		super(in, builder,true);
	}
	
	public SDFReader(InputStream in) {
		super(in, SilentChemObjectBuilder.getInstance(),true);
	}
	
	public SDFReader(Reader in, IChemObjectBuilder builder) {
		super(in, builder,true);
	}
	
	public SDFReader(Reader in) {
		super(in, SilentChemObjectBuilder.getInstance(),true);
	}
	
	@Override
	public IAtomContainer next() {
		IAtomContainer tmp = super.next();
		String title = tmp.getTitle();
		if (title != null) {
			tmp.setID(title);
		}
		CPSignMolProperties.setRecordIndex(tmp, recordIndex);
		
		recordIndex++;
		return tmp;
	}
	
	/**
	 * Has no effect - this class do not register any failures due to
	 * underlying CDK implementation doesn't expose those errors
	 * @param tracker ignored
	 */
	public void setProgressTracker(ProgressTracker tracker){
		// Do nothing 
	}

	/**
	 * Returns the default tracker, which has no failed records
	 */
	public ProgressTracker getProgressTracker(){
		return ProgressTracker.createDefault();
	}

	/**
	 * Has no effect - this class do not register any failures due to
	 * underlying CDK implementation doesn't expose those errors
	 * @param tracker ignored
	 */
	public SDFReader withProgressTracker(ProgressTracker tracker) {
		return this;
	}

}

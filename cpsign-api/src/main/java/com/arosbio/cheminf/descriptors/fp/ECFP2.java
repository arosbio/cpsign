/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cheminf.descriptors.fp;

import org.openscience.cdk.fingerprint.CircularFingerprinter;

import com.arosbio.commons.mixins.Described;

public class ECFP2 extends CDKCircularFPWrapper implements Described {
	
	public static String NAME = "ECFP2";

	@Override
	public void initialize() {
		super.initialize(CircularFingerprinter.CLASS_ECFP2);
	}

	@Override
	public String getName() {
		return NAME;
	}
	
	@Override
	public String getDescription() {
		return String.format(DESCRIPTION_FMT, 2);
	}

	@Override
	public ECFP2 clone() {
		ECFP2 c = new ECFP2();
		c.setLength(getLength());
		c.useCountVersion(usesCountVersion());
		return c;
	}

}

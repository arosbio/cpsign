/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app.error_handling;

/**
 * An {@link java.lang.Error} that signifies an invalid user input of some kind,
 * either incorrectly given, in the wrong setting or in an invalid combination with
 * some other parameter.
 * 
 * @author staffan
 *
 */
public class InvalidParametersError extends CPSignCLIError {

	/**
	 * 
	 */
	private static final long serialVersionUID = 378718874619770173L;

	public InvalidParametersError(String msg) {
		super(msg);
	}
}

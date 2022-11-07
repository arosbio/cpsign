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
 * A base {@link java.lang.Error} for handling termination of a CLI call and
 * give feedback to the user. This so that the {@link com.arosbio.cpsign.app.CPSignApp} can
 * deduce the correct error code and format the error message to the user
 *  
 * @author staffan
 *
 */
public class CPSignCLIError extends Error {

	public CPSignCLIError(String msg) {
		super(msg);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -7201684654706620749L;

}

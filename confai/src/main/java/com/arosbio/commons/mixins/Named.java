/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.commons.mixins;

public interface Named {

	/**
	 * Get the name used for the particular implementation type. Should be CamelCase, starting with a 
	 * capital letter. Acronyms following other acronyms should be separated by an underscore '_' character. E.g.
	 * LOO CV should be LOO_CV
	 * @return the name of the instance type
	 * @see Aliased
	 */
	public String getName();
}

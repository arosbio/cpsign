/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem.logging;

import org.openscience.cdk.tools.ILoggingTool;

/**
 * Do nothing..
 * @author staffan
 *
 */
public class DevNullLoggingTool implements ILoggingTool {
	
	private static DevNullLoggingTool instance = new DevNullLoggingTool();

	public static ILoggingTool create(Class<?> cls) {
		return instance;
	}
	
	@Override
	public void debug(Object arg0) {}

	@Override
	public void debug(Object arg0, Object... arg1) {}

	@Override
	public void dumpClasspath() {}

	@Override
	public void dumpSystemProperties() {}

	@Override
	public void error(Object arg0) {}

	@Override
	public void error(Object arg0, Object... arg1) {}

	@Override
	public void fatal(Object arg0) {}

	@Override
	public int getLevel() {
		return 0;
	}

	@Override
	public void info(Object arg0) {}

	@Override
	public void info(Object arg0, Object... arg1) {}

	@Override
	public boolean isDebugEnabled() {
		return false;
	}

	@Override
	public void setLevel(int arg0) {}

	@Override
	public void setStackLength(int arg0) {}

	@Override
	public void warn(Object arg0) {}

	@Override
	public void warn(Object arg0, Object... arg1) {}

}

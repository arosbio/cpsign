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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This crap is needed for CDK that otherwise falls back to printing to sysout for warnings etc.
 * @author staffan
 *
 */
public class Slf4jLoggingTool implements ILoggingTool {
	
	private Logger LOGGER; 

	public static ILoggingTool create(Class<?> cls) {
		Slf4jLoggingTool tool = new Slf4jLoggingTool();
		tool.LOGGER = LoggerFactory.getLogger(cls);
		return tool;
	}
	
	@Override
	public void debug(Object arg0) {
		LOGGER.debug(arg0.toString());
	}

	@Override
	public void debug(Object arg0, Object... arg1) {
		LOGGER.debug(convert(arg0, arg1));
	}

	@Override
	public void dumpClasspath() {
		debug("java.class.path: " + System.getProperty("java.class.path"));
	}

	@Override
	public void dumpSystemProperties() {
		debug("os.name        : " + System.getProperty("os.name"));
        debug("os.version     : " + System.getProperty("os.version"));
        debug("os.arch        : " + System.getProperty("os.arch"));
        debug("java.version   : " + System.getProperty("java.version"));
        debug("java.vendor    : " + System.getProperty("java.vendor"));
	}

	@Override
	public void error(Object arg0) {
		LOGGER.error(arg0.toString(), arg0);
	}

	@Override
	public void error(Object arg0, Object... arg1) {
		LOGGER.error(arg0.toString(), convert(arg0, arg1));
	}

	@Override
	public void fatal(Object arg0) {
		error(arg0);
	}

	@Override
	public int getLevel() {
		if (LOGGER.isDebugEnabled())
			return ILoggingTool.DEBUG;
		else if (LOGGER.isErrorEnabled())
			return ILoggingTool.ERROR;
		else if (LOGGER.isInfoEnabled())
			return ILoggingTool.INFO;
		else if (LOGGER.isTraceEnabled())
			return ILoggingTool.TRACE;
		else if (LOGGER.isWarnEnabled())
			return ILoggingTool.WARN;
		// default is debug
		return ILoggingTool.DEBUG;
	}

	@Override
	public void info(Object arg0) {
		LOGGER.info(arg0.toString());
	}

	@Override
	public void info(Object arg0, Object... arg1) {
		LOGGER.info(convert(arg0, arg1));
	}

	@Override
	public boolean isDebugEnabled() {
		return LOGGER.isDebugEnabled();
	}

	@Override
	public void setLevel(int arg0) {
		// Cannot do this with Slf4j ... 
	}

	@Override
	public void setStackLength(int arg0) {
		// blargh..
	}

	@Override
	public void warn(Object arg0) {
		LOGGER.warn(arg0.toString());

	}

	@Override
	public void warn(Object arg0, Object... arg1) {
		LOGGER.warn(convert(arg0, arg1));
	}
	
	private static String convert(Object arg0, Object... arg1) {
		StringBuilder sb = new StringBuilder(arg0.toString());
		for (int i=0; i < arg1.length; i++) {
			sb.append(", ");
			sb.append(arg1[i]);
		}
		return sb.toString();
		
	}

}

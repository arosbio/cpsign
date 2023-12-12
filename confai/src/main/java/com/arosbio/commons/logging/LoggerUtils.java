/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.commons.logging;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

import org.apache.commons.io.FileUtils;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.util.FileSize;
import ch.qos.logback.core.util.StatusPrinter;

public class LoggerUtils {
	private static final Logger LOGGER = (Logger)LoggerFactory.getLogger(LoggerUtils.class);
	
	private static final String CPSIGN_ROOT_LOGGER_NAME = "com.arosbio";
	final private static String DEFAULT_PATTERN = "%d{yyyy-MM-dd HH:mm:ss} %-5p %c [%t] - %m%n";
	final private static String SYSTEM_OUT_APPENDER = "STDOUT";
	final private static String SYSTEM_ERR_APPENDER = "STDERR";

	final private static String DEFAULT_LOG_FILE_NAME = "cpsign.log";
	final private static String LOGFILE_APPENDER = "CPSIGN_LOG";
	final private static String MAX_LOGFILE_SIZE = "50MB";
	final private static int MAX_NUMBER_BACKUP_LOGFILES = 2;

	public static void setDebugMode(){
		Logger rootLogger = 
				(Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		Appender<ILoggingEvent> sysout = rootLogger.getAppender(SYSTEM_OUT_APPENDER);
		if(sysout != null){
			sysout.clearAllFilters();
			ThresholdFilter thresholdFilter = new ThresholdFilter();
			thresholdFilter.setLevel("DEBUG");
			sysout.addFilter(thresholdFilter);
			LOGGER.debug("Set Level.DEBUG for system.out appender");
		}
		
	}

	public static void setTraceMode(){
		Logger rootLogger = 
				(Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		rootLogger.setLevel(Level.TRACE);

		// Update all appenders (except System-err)
		Iterator<Appender<ILoggingEvent>> appenders = rootLogger.iteratorForAppenders();
		LOGGER.debug("Activating Level.TRACE for all appenders except System.err (in case that exists)");
		while( appenders.hasNext() ){
			Appender<ILoggingEvent> currentAppender = appenders.next();
			if(currentAppender.getName().equals(SYSTEM_ERR_APPENDER))
				continue; // skip the sys-err appender
			currentAppender.clearAllFilters();
			ThresholdFilter thresholdFilter = new ThresholdFilter();
			thresholdFilter.setLevel("TRACE");
			currentAppender.addFilter(thresholdFilter);
		}
	}

	public static void setDebugMode(OutputStream stream){
		
		addStreamAppenderToRootLogger(stream);
		setDebugMode();
		
	}

	public static String getDefaultLogfileName(){
		return DEFAULT_LOG_FILE_NAME;
	}

	protected static void addFileAppenderToLogger(Logger theLogger, String filePath, String pattern, boolean appendToOldFile){
		if (theLogger == null)
			theLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		LoggerContext context = theLogger.getLoggerContext();

		if(pattern == null || pattern.isEmpty())
			pattern = DEFAULT_PATTERN;

		PatternLayoutEncoder ple = new PatternLayoutEncoder();
		ple.setPattern(pattern);
		ple.setContext(context);
		ple.start();


		FileAppender<ILoggingEvent> fileAppender = new FileAppender<ILoggingEvent>();
		fileAppender.setContext(context);
		fileAppender.setFile(filePath);
		fileAppender.setEncoder(ple);
		fileAppender.setAppend(appendToOldFile);
		fileAppender.start();

		theLogger.addAppender(fileAppender);
	}

	public static Appender<ILoggingEvent> addStreamAppenderToRootLogger(OutputStream stream){
		return addStreamAppenderToLogger((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME), stream, null);
	}
	
	public static Appender<ILoggingEvent> addStreamAppenderToLogger(Logger theLogger, OutputStream stream, String pattern){
		LoggerContext context = theLogger.getLoggerContext();

		if(pattern == null || pattern.isEmpty())
			pattern = DEFAULT_PATTERN;
		PatternLayoutEncoder ple = new PatternLayoutEncoder();
		ple.setPattern(pattern);
		ple.setContext(context);
		ple.start();

		OutputStreamAppender<ILoggingEvent> streamAppender = new OutputStreamAppender<ILoggingEvent>();
		streamAppender.setContext(context);
		streamAppender.setEncoder(ple);
		streamAppender.setOutputStream(stream);
		streamAppender.start();
		String name = "stream-appender-"+(int)Math.round(Math.random()*100);
		streamAppender.setName(name);
		
		theLogger.addAppender(streamAppender);
		
		return streamAppender;
	}

	public static void removeAppender(Logger theLogger, String appenderName){
		if (theLogger.detachAppender(appenderName))
			LOGGER.debug("successfully removed appender {} from logger {}",appenderName,theLogger.getName());
		else
			LOGGER.debug("Could not detach appender with name \"{}\" from logger {}",appenderName, theLogger.getName());
	}
	
	public static void removeAppender(Logger theLogger, Appender<ILoggingEvent> appender){
		theLogger.detachAppender(appender);
	}

	public static void removeSysOutAndSysErr(){
		// First the root
		Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		removeAppender(root, SYSTEM_ERR_APPENDER);
		removeAppender(root, SYSTEM_OUT_APPENDER);
		
		// Then the root logger for cpsign
		Logger cpsignRoot = (Logger) LoggerFactory.getLogger(CPSIGN_ROOT_LOGGER_NAME);
		removeAppender(cpsignRoot, SYSTEM_ERR_APPENDER);
		removeAppender(cpsignRoot, SYSTEM_OUT_APPENDER);
	}

	public static void removeStandardLogfile(){
		Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		removeAppender(root, LOGFILE_APPENDER);
	}

	public static void addLogfileToRootLogger(String filePath) throws IOException{
		String cleanedFilePath = cleanLoggerNameAndCreateFile(filePath);

		addFileAppenderToLogger((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME), cleanedFilePath, null, false);
	}

	public static void addStandardCPSignRollingFileAppender() throws IOException {
		addRollingFileAppender(DEFAULT_LOG_FILE_NAME);
	}

	public static void addRollingFileAppender(String loggerFilePath) throws IOException {
		Logger rootLogger = 
				(Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

		LoggerContext loggerContext = rootLogger.getLoggerContext();

		// Set up the logger file
		String finalLoggerPath = cleanLoggerNameAndCreateFile(loggerFilePath);
		String fileBaseName = getFileBaseName(finalLoggerPath);
		String filePrefix = getFilePrefix(finalLoggerPath);
		String pattern = fileBaseName + "-%i";
		if (filePrefix !=null && !filePrefix.isEmpty())
			pattern += "."+filePrefix;

		RollingFileAppender<ILoggingEvent> rfAppender = new RollingFileAppender<ILoggingEvent>();
		rfAppender.setContext(loggerContext);
		rfAppender.setFile(finalLoggerPath);
		rfAppender.setName(LOGFILE_APPENDER);

		FixedWindowRollingPolicy fwRollingPolicy = new 
				FixedWindowRollingPolicy();
		
		fwRollingPolicy.setContext(loggerContext);
		fwRollingPolicy.setFileNamePattern(pattern);
		fwRollingPolicy.setParent(rfAppender);
		fwRollingPolicy.setMaxIndex(MAX_NUMBER_BACKUP_LOGFILES);
		fwRollingPolicy.start();

		SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy = new 
				SizeBasedTriggeringPolicy<ILoggingEvent>();
		triggeringPolicy.setMaxFileSize(FileSize.valueOf(MAX_LOGFILE_SIZE));
		triggeringPolicy.start();

		PatternLayoutEncoder encoder = new PatternLayoutEncoder();
		encoder.setContext(loggerContext);
		encoder.setPattern(DEFAULT_PATTERN);
		encoder.start();

		rfAppender.setEncoder(encoder);
		rfAppender.setRollingPolicy(fwRollingPolicy);
		rfAppender.setTriggeringPolicy(triggeringPolicy);
		rfAppender.start();

		rootLogger.addAppender(rfAppender);
	}

	private static String cleanLoggerNameAndCreateFile(String loggerPath) throws IOException {
		if(loggerPath == null || loggerPath.isEmpty())
			return DEFAULT_LOG_FILE_NAME;

		File logFile = new File(loggerPath);

		// IF FILE IS DIRECTORY
		if (loggerPath.endsWith("/") || (logFile.exists() && logFile.isDirectory())){

			File parentFile = logFile;
			logFile = new File(parentFile, DEFAULT_LOG_FILE_NAME);

			// create parent if does not exist
			if (! parentFile.exists()){
				FileUtils.forceMkdir(parentFile);
			}
			// create actual logfile if not exist
			if (! logFile.exists()){
				try{
					logFile.createNewFile();
				} catch (IOException | SecurityException e){
					throw new IOException(e.getMessage());
				}
			}

			return new File(logFile, DEFAULT_LOG_FILE_NAME).getPath();
		}

		// IF FILE IS FULLY SPECIFIED 
		else {
			if (! logFile.exists()){
				// create parents if needed
				if (logFile.getParentFile() != null)
					FileUtils.forceMkdir(logFile.getParentFile());
				logFile.createNewFile();
			}

			return logFile.getAbsolutePath();
		}
	}

	private static String getFileBaseName(String fullPath){
		String fileName = new File(fullPath).getName();
		if (fileName.contains("."))
			return fileName.substring(0, fileName.lastIndexOf('.'));
		return fileName;
	}
	
	private static String getFilePrefix(String fullPath) {
		if (fullPath.contains(".")) {
			String prefix = fullPath.substring(fullPath.lastIndexOf('.')+1).trim();
			if (prefix.length()>0)
				return prefix;
		}
		return "log";
	}

	public static void reloadLoggingConfig() throws JoranException{
		Logger rootLogger = 
				(Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		rootLogger.detachAndStopAllAppenders();
		
		LoggerContext loggerContext = rootLogger.getLoggerContext();

		ContextInitializer ci = new ContextInitializer(loggerContext);
		ci.autoConfig();
		
	}
	
	public static void reloadLogger() {
	    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

	    ContextInitializer ci = new ContextInitializer(loggerContext);

	    try {
	        JoranConfigurator configurator = new JoranConfigurator();
	        configurator.setContext(loggerContext);
	        loggerContext.reset();
			ci.autoConfig();
	    } catch (JoranException je) {
	        // StatusPrinter will handle this
	    }
	    StatusPrinter.printInCaseOfErrorsOrWarnings(loggerContext);
	}
	
	public static String getShortExceptionMsg(Throwable t) {
		return t.getClass().getName() + ": " + t.getMessage();
	}
}

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.commons;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.io.IOSettings;
import com.arosbio.tests.suites.UnitTest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.joran.spi.JoranException;

@Category(UnitTest.class)
public class TestLoggerUtils {
	
	@Before
	@After
	public void resetLogging(){
		try {
			LoggerUtils.reloadLoggingConfig();
		} catch(JoranException e) {
			System.err.println("Could not reload the default logging-config");
			Assert.fail();
		}
	}
	
	@Test
	public void testRemoveLogger(){
		Logger utilsLogger = (Logger) LoggerFactory.getLogger(LoggerUtils.class);
		utilsLogger.setLevel(Level.ALL);
//		List<Appender<ILoggingEvent>> before1 = getAppenders(utilsLogger);
		Assert.assertEquals("no default appenders!", 0, getAppenders(utilsLogger).size());
		
		// Add one appender!
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		LoggerUtils.addStreamAppenderToLogger(utilsLogger, bos, "%m%n");
		
		Assert.assertEquals("now there should be 1!", 1, getAppenders(utilsLogger).size()); 
		
		Logger testLogger = (Logger) LoggerFactory.getLogger("com.arosbio"); //Logger.ROOT_LOGGER_NAME);
		
		// add appender to logger
		Appender<ILoggingEvent> apper = LoggerUtils.addStreamAppenderToLogger(testLogger, new ByteArrayOutputStream(), "%m%n");
		
		Assert.assertEquals("testLogger should have 1 appender",1, getAppenders(testLogger).size());
		Assert.assertEquals("utilsLogger should have 1 appender",1, getAppenders(utilsLogger).size());
		
		// Now remove the one from testLogger! - so should be 0
		LoggerUtils.removeAppender(testLogger, apper.getName());
		Assert.assertEquals("testLogger should have 0 appenders",0, getAppenders(testLogger).size());
		
		Assert.assertTrue(bos.toString().contains("successfully removed appender"));
		
	}
	
	static List<Appender<ILoggingEvent>> getAppenders(Logger theLogger){
		Iterator<Appender<ILoggingEvent>> appenders = theLogger.iteratorForAppenders();
		List<Appender<ILoggingEvent>> list = new ArrayList<>();
		while (appenders.hasNext()) {
			list.add(appenders.next());
		}
		return list;
	}
	
	// This appender doesn't exist any more!
//	@Test
	public void testRemoveStdout_err(){

		Logger rootLogger = (Logger) LoggerFactory.getLogger("com.arosbio"); //Logger.ROOT_LOGGER_NAME);
		List<Appender<ILoggingEvent>> before = getAppenders(rootLogger);
		Assert.assertTrue( ! before.isEmpty());
		LoggerUtils.removeSysOutAndSysErr();
		
		List<Appender<ILoggingEvent>> after = getAppenders(rootLogger);

		Assert.assertTrue(after.isEmpty());
	}
	
	@Test
	public void testSetDebugMode() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		LoggerUtils.setDebugMode(baos);
//		LoggerUtils.addStreamAppenderToRootLogger(System.out);
//		LoggerUtils.setDebugMode();
		Logger log = (Logger) LoggerFactory.getLogger(TestLoggerUtils.class);
		log.debug("HEJ");
		log.debug("då");
		
		Assert.assertTrue(baos.toString().length() > 4);
	}
	
	@Test
	public void testAddFileAppender() throws IOException {
		LoggerUtils.removeSysOutAndSysErr();
		Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		List<Appender<ILoggingEvent>> before = getAppenders(rootLogger);
//		printAppenders(before);
		Assert.assertTrue( before.isEmpty() ); // removed the system out appenders
		
		File tmpFile = File.createTempFile("logfile", ".suffix"); 
		tmpFile.deleteOnExit();

		LoggerUtils.addLogfileToRootLogger(tmpFile.getAbsolutePath());
		List<Appender<ILoggingEvent>> after = getAppenders(rootLogger);
//		printAppenders(after);
		Assert.assertEquals(1+before.size(), after.size());
		
		long fileSizeBefore = FileUtils.sizeOf(tmpFile);
		Assert.assertEquals(0, fileSizeBefore);
		
		Logger logger = (Logger) LoggerFactory.getLogger("My Logger");
		
		List<String> msgs = Arrays.asList("here is a debug-text", "Here is some INFO", "Here's a warning","error error!!");
		logger.debug(msgs.get(0));
		logger.info(msgs.get(1));
		logger.warn(msgs.get(2));
		logger.error(msgs.get(3));
		
		long fileSizeAfter = FileUtils.sizeOf(tmpFile);
		
//		System.err.println("before: " + fileSizeBefore + " after: " + fileSizeAfter);
		Assert.assertTrue(fileSizeAfter > fileSizeBefore+50);
		String contents = FileUtils.readFileToString(tmpFile, IOSettings.CHARSET);
		
		for (String msg: msgs) {
			contents.contains(msg);
		}
	}
	
	@Test
	public void testAddStreamAppender() {
		Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		List<Appender<ILoggingEvent>> before = getAppenders(rootLogger);
		
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		LoggerUtils.addStreamAppenderToLogger(rootLogger, os, "%m%n");
		
		List<Appender<ILoggingEvent>> after = getAppenders(rootLogger);
		Assert.assertEquals(1+before.size(), after.size());
		
		Logger logger = (Logger) LoggerFactory.getLogger("My Logger");
		
		logger.debug("debug message");
		Assert.assertTrue(os.toString().contains("debug message"));
		
	}
	
	public void printAppenders(List<Appender<ILoggingEvent>> appenders) {
		for (Appender<ILoggingEvent> l : appenders) {
			System.out.println(l + ": " + l.getCopyOfAttachedFiltersList());
		}
	}
	
	
}

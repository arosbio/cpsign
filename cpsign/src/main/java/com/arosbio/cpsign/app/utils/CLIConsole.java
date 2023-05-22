/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app.utils;

import org.apache.commons.text.WordUtils;
import org.fusesource.jansi.AnsiRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.io.in.ChemFile;
import com.arosbio.commons.StringUtils;
import com.arosbio.cpsign.app.CPSignApp;
import com.arosbio.cpsign.app.error_handling.InvalidParametersError;

import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;

public class CLIConsole {

	public static final String INTERNAL_PROGRAM_ERROR_TXT = "Internal program error";

	private static final Logger LOGGER = LoggerFactory.getLogger(CLIConsole.class);

	public static final int DEFAULT_CLI_CHARS_WIDTH = CommandLine.Model.UsageMessageSpec.DEFAULT_USAGE_WIDTH;
	public static final char DEFAULT_DELIMITER_CSV = '\t';
	//	public static final char NON_BREAKING_SPACE = '\u00A0';

	public static enum VerbosityLvl {
		SILENT (0), 
		NORMAL (1), 
		VERBOSE (2);

		private final int code;

		private VerbosityLvl(int code) {
			this.code = code;
		}

	}

	public static class PrintMode {

		public static final PrintMode NORMAL = new PrintMode(VerbosityLvl.NORMAL, false);
		public static final PrintMode NORMAL_ON_MATCH = new PrintMode(VerbosityLvl.NORMAL, true);
		public static final PrintMode SILENT = new PrintMode(VerbosityLvl.SILENT, false);
		public static final PrintMode SILENT_ON_MATCH = new PrintMode(VerbosityLvl.SILENT, true);
		public static final PrintMode VERBOSE = new PrintMode(VerbosityLvl.VERBOSE, false);
		public static final PrintMode VERBOSE_ON_MATCH = new PrintMode(VerbosityLvl.VERBOSE, true);

		private VerbosityLvl level;
		private boolean matchLevel = false;

		private PrintMode(VerbosityLvl lvl, boolean match) {
			this.level=lvl;
			this.matchLevel = match;
		}

		private boolean shouldWrite(VerbosityLvl consoleLevel) {
			if (matchLevel) {
				return level == consoleLevel;
			} 
			// here it's a threshold-thing instead
			return consoleLevel.code >= level.code ;

		}
	}

	private static CLIConsole console;
	private String programName = CPSignApp.PROGRAM_NAME;
	private VerbosityLvl mode = VerbosityLvl.NORMAL;
	private int consoleWidth;
	private Ansi ansi;

	
	public static CLIConsole getInstance() {
		if (console !=null)
			return console;
		console = new CLIConsole();
		return console;
	}
	
	public static CLIConsole getInstance(String runningCMD) {
		if (console != null) {
			return console.setRunningCMD(runningCMD);
		}
		console = new CLIConsole();
		return console.setRunningCMD(runningCMD);
	}
	
	private CLIConsole() {
		consoleWidth = new CommandLine.Model.UsageMessageSpec().autoWidth(true).width();
		ansi = (Ansi.AUTO.enabled() ? Ansi.ON : Ansi.OFF);
	}
	
	public void close() {
		console = null;
	}
	
	public CLIConsole setRunningCMD(String cmd) {
		this.programName = cmd;
		return this;
	}

	public String getRunningCmd() {
		return programName;
	}

	public int getTextWidth() {
		return consoleWidth;
	}

	public CLIConsole setVerbosity(VerbosityLvl mode) {
		this.mode = mode;
		return this;
	}

	public VerbosityLvl getVerbosity() {
		return mode;
	}

	public boolean ansiON() {
		return ansi == Ansi.ON;
	}
	
	public CLIConsole turnOfAnsi() {
		ansi = Ansi.OFF;
		return this;
	}

	public void printlnWrapped(String text, PrintMode m, Object... args) {
		if (m.shouldWrite(mode))
			write(StringUtils.wrap(text, consoleWidth), true, args);
		logInfo(text, args);
	}

	public void print(String text, PrintMode m, Object... args) {
		if (m.shouldWrite(mode))
			write(text, false,args);
		logInfo(text, args);
	}

	public void println(String text, PrintMode m, Object... args) {
		if (m.shouldWrite(mode))
			write(text, true,args);
		logInfo(text, args);
	}

	private void write(String text, boolean newLine, Object... args) {
		if (text != null) {
			System.out.printf(AnsiRenderer.render(text),args); 
			if (newLine)
				System.out.println();
		} else {
			System.out.println("null");
		}
	}

	private void logInfo(String text, Object... args){
		if (args.length>0)
			LOGGER.info(String.format(text,args));
		else
			LOGGER.info(String.format(text));
	}

	public void printlnWrappedStdErr(String text, PrintMode m, Object... args) {
		if (m.shouldWrite(mode))
			writeStdError(WordUtils.wrap(text, consoleWidth), true, args);
		logErr(text, args);
	}

	public void printlnStdErr(String text, PrintMode m, Object... args) {
		if (m.shouldWrite(mode))
			writeStdError(text, true,args);
		logErr(text, args);
	}

	public void printStdErr(String text, PrintMode m, Object... args) {
		if (m.shouldWrite(mode))
			writeStdError(text, false,args);
		logErr(text, args);
	}

	private void writeStdError(String text, boolean newLine, Object... args) {
		if (text != null) {
			System.err.printf(text,args);
			if (newLine)
				System.err.println();
		} else {
			System.err.printf("null");
		}
	}

	private void logErr(String text,Object...args){
		if (args.length>0)
			LOGGER.error(String.format(text, args));
		else
			LOGGER.error(text);
	}

	public void failWithNoMoleculesCouldBeLoaded(ChemFile file) {
		failWithArgError(String.format("No molecules parsed from: %s using format %s, was the correct parameters given?",file.getURI() ,file.getFileFormat()));
	}

	public void failWithArgError(String format,Object... args) {
		throw new InvalidParametersError(String.format(format, args));
	}
	
	public void failWithArgError(String txt) {
		throw new InvalidParametersError(txt);
	}
	
	public void failWithInternalError() {
		failWithInternalError(INTERNAL_PROGRAM_ERROR_TXT);
	}
	
	public void failWithInternalError(String msg) {
		throw new InternalError(msg);
	}
	
	public void failWithInternalError(String format, Object... args) {
		throw new InternalError(String.format(format, args));
	}

	public enum ParamComb{
		AND (" and "), OR (" or "), AND_OR (" and/or ");

		final String comb;
		private ParamComb(String comb) {
			this.comb = comb;
		}
		public String comb() {
			return comb;
		}
	}

	private final void addParamName(StringBuilder sb, MissingParam param) {
		sb.append(CLIProgramUtils.getParamName(param.origin, param.fieldName, param.fallbackName));
	}

	@SafeVarargs
	public final void failDueToMissingParameters(MissingParam... params) {
		failDueToMissingParameters(ParamComb.AND, null, params);
	}

	@SafeVarargs
	public final void failDueToMissingParameters(final ParamComb combination, String ending, MissingParam... missingParams) {
		StringBuilder sb = new StringBuilder();

		if (missingParams == null || missingParams.length == 0){
			sb.append("Missing parameters");
		} else if(missingParams.length == 1){
			sb.append("Parameter ");
			addParamName(sb, missingParams[0]);
			sb.append(" must be given");
		} else if (missingParams.length == 2){ 
			sb.append("Parameters ");
			addParamName(sb, missingParams[0]);
			sb.append(combination.comb);
			addParamName(sb, missingParams[1]);
			sb.append(" must be given");
		} else {
			// Here we have multiple parameters
			sb.append("Parameters ");
			for (int i=0; i<missingParams.length-2; i++){
				addParamName(sb, missingParams[i]);
				sb.append(", ");
			}
			addParamName(sb, missingParams[missingParams.length-2]);
			sb.append(combination.comb);
			addParamName(sb, missingParams[missingParams.length-1]);
			sb.append(" must be given");
		}

		if (ending!=null && !ending.isEmpty()) {
			sb.append(' ');
			sb.append(ending.trim());
		}

		sb.append("%n");

		LOGGER.debug("exiting due to missing required parameters");
		failWithArgError(sb.toString());
	}

	@SafeVarargs
	public final void failDueToNonCombineableParameters(MissingParam... params) { 
		failDueToNonCombineableParameters(" together ", params);
	}

	@SafeVarargs
	public final void failDueToNonCombineableParameters(String ending,  MissingParam... params) { 
		StringBuilder sb = new StringBuilder();

		if (params.length==1){
			// Here we have multiple parameters
			sb.append("Parameter ");
			addParamName(sb, params[0]);
			sb.append(" cannot be given");
		} else if (params.length >= 2){ 
			// Here we have multiple parameters
			sb.append("Parameters ");
			for (int i=0; i<params.length-2; i++){
				addParamName(sb, params[i]);
				sb.append(", ");
			}
			addParamName(sb, params[params.length-2]);
			sb.append(" and ");
			addParamName(sb, params[params.length-1]);
			sb.append(" cannot be given");
		}
		if (ending!=null && !ending.isEmpty()) {
			sb.append(' ');
			sb.append(ending.trim());
		}

		sb.append("%n");

		LOGGER.debug("exiting due to non-combinable parameters given");
		failWithArgError(sb.toString());
	}

	public void writeHowToGetHelpText(){
		if (CPSignApp.PROGRAM_NAME.equalsIgnoreCase(programName)) {
			writeStdError("Try cpsign --help for more information.%n", false);
		} else {
			writeStdError("Try cpsign "+programName + " --help for more information.%n%n", false);
		}
	}



}

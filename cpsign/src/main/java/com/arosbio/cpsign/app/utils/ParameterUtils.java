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

import com.arosbio.commons.config.Configurable;

public class ParameterUtils {
	
	public class ArgumentType {
		
		public final static String INTEGER="<integer>";
		public final static String NUMBER="<number>";
		public final static String NUMBER_OR_TEXT="<number | text>";
		public final static String TEXT="<text>";
		public final static String TEXT_MATCH_ONE_OF="<text>";
		public final static String ID_OR_TEXT="<id | text>";
		public final static String MULTIPLE_TEXT = "["+TEXT + ' ' + TEXT +" ..]";
		public final static String FILE_PATH = "<path>";
		public final static String URI_OR_PATH="<URI | path>";
		public final static String SMILES="<SMILES>";
		public final static String LABELS="<labels>";
		public final static String COLOR="<color name | hex color>";
		public final static String START_STOP_STEP="<start:stop:step | numbers>";
		public final static String MOUNT_TYPE = "<location:URI>";
		public final static String CHEM_FILE_ARGS = "<file format> [optional args] " + URI_OR_PATH;
		public final static String IMG_HEIGHT = "<height>";
		public final static String IMG_WIDTH = "<width>";
		public final static String VERSION = "<version>";
		public final static String TUNE_KEY_VALUE = "<key=values>";
		
	}
	
	public static final String DEFAULT_PRE_TEXT = "Default: ";
	public static final String DEFAULT_VALUE_LINE = "Default: ${DEFAULT-VALUE}";
	
	public static final String SPLIT_WS_COMMA_REGEXP = "[\\s,]"; // Changed
	public static final String SPLIT_WS_REGEXP = "\\s";
	public static final String LIST_TYPE_ARITY = "1..*";
	
	public static final String RUN_EXPLAIN_ANSI_ON = "@|bold,red ";
	public static final String PARAM_FLAG_ANSI_ON = "@|yellow ";
	public static final String ANSI_OFF = "|@";
	
	public final static String MULTIPLE_OPTIONS_INDENTATION = "  ";
	public final static String MULTIPLE_OPTIONS_INDENTATION_C_BEFORE = "* ";
	public final static String MULTIPLE_ARGUMENT_NAME_SPLITTER = " | ";
	
	/**
	 * Used for splitting text-input of {@link Configurable} type which comes with
	 * optional config mappings
	 */
	public final static String SUB_PARAM_SPLITTER = ":";
	/**
	 * Used for splitting key to value arguments of {@link Configurable} types
	 */
	public static final String SUB_PARAM_KEY_VALUE_DELIM = "=";

}

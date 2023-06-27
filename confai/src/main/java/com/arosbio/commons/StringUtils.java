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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.text.CaseUtils;
import org.apache.commons.text.WordUtils;

public class StringUtils {

	private static final String NEW_LINE_SEP = System.lineSeparator();

	private static final String NL_REGEX = "\\R|%n";
	
	public static String wrap(String txt, int width) {
		return wrap(txt,width,null);
	}
	
	public static String wrap(String txt, int totalWidth, String newLineStart) {
		return wrap(txt, totalWidth, newLineStart, true);
	}
	
	public static String wrap(final String txt, int totalWidth, final String newLineStart, boolean applyToFirst) {
		return wrap(txt, totalWidth, newLineStart, applyToFirst ? newLineStart : "");
	}
	
	public static String wrap(final String txt, int totalWidth, final String newLineStart, final String beginFirstLine) {
		// Handle the input text
		if (txt == null || txt.isEmpty())
			return "";
		if (totalWidth < 10)
			return txt;
		
		// Handle the new-line-text
		int indentW = 0;
		String indent = null, indentToWordUtils = ' ' +NEW_LINE_SEP;
		boolean containsNL = false;
		if (newLineStart == null) {
			indent = "";
		} else {
			indent = newLineStart; //.replaceAll("\t", TAB);
			String[] splits = indent.split(NL_REGEX);
			if (splits.length>=2) {
				// Only the width of the last thing actually matters
				indentW = splits[splits.length-1].length();
				containsNL = true;
				indentToWordUtils = ' ' +newLineStart;
			} else {
				indentW = indent.length();
				indentToWordUtils = ' '+NEW_LINE_SEP + indent;
			}
		}

		String[] lines = txt.split(NL_REGEX);
		StringBuilder sb = new StringBuilder();
		
		// Special-treat first index in the array
		if (beginFirstLine != null && !beginFirstLine.isEmpty()) {
			sb.append(beginFirstLine);
			sb.append(WordUtils.wrap(lines[0], totalWidth-Math.max(indentW, beginFirstLine.length()), indentToWordUtils,false));
		} else {
			sb.append(WordUtils.wrap(lines[0], totalWidth-indentW, indentToWordUtils,false));
		}
		for (int i=1; i<lines.length; i++) {
			if (containsNL)
				sb.append(indent);
			else
				sb.append(NEW_LINE_SEP).append(indent);
			sb.append(WordUtils.wrap(lines[i], totalWidth-indentW, indentToWordUtils, false));
		}

		return sb.toString();
	}

	public static void replicate(StringBuilder sb, char c, int times){
		for (int i=0; i<times;i++)
			sb.append(c);
	}

	public static String replicate(char c, int times) {
		if (times <= 0)
			return "";
		StringBuilder sb = new StringBuilder(times);
		for (int i=0; i<times; i++)
			sb.append(c);
		return sb.toString();
	}

	public static void paddBeforeCentering(Appendable sb, String textToWriteLater, int totalWidth) {
		int blanksToAdd = (totalWidth-textToWriteLater.length())/2;
		try {
		for (int i=0; i<blanksToAdd;i++)
			sb.append(' ');
		} catch (Exception e) {}
	}
	
	public static String center(String text, int totalWidth) {
		StringBuilder sb = new StringBuilder(text.length()+totalWidth/2);
		center(sb, text,totalWidth);
		return sb.toString();
	}

	public static void center(StringBuilder sb, String text, int totalWidth){

		int blanksToAdd = (totalWidth-text.length())/2;
		for (int i=0; i<blanksToAdd;i++)
			sb.append(' ');
		sb.append(text);
	}

	private final static String CAMEL_CASE_PATTERN = "([a-z]+[A-Z]+\\w+)+";
	private final static String CAPITAL_LETTERS_ONLY_PATTERN = "[A-Z0-9\\p{Punct}]+";

	public static String toCamelCase(String txt) {
		if (txt.matches(CAMEL_CASE_PATTERN))
			return txt;
		else if (txt.matches(CAPITAL_LETTERS_ONLY_PATTERN))
			return txt;
		if (!txt.contains(" ")) {
			return txt;
		}
		if (txt.contains("(")) {
			String[] splits = txt.split("\\(",2);
			return CaseUtils.toCamelCase(splits[0], false)+'('+splits[1];
		} else {
			return CaseUtils.toCamelCase(txt, false);
		}

	}

	public static String indent(String txt) {
		return '\t'+txt.replaceAll(NL_REGEX, NEW_LINE_SEP+'\t');
	}

	public static String indent(String txt, int numSpace) {
		if (numSpace < 1)
			return txt;
		String ind = replicate(' ', numSpace);
		return ind+txt.replaceAll(NL_REGEX, NEW_LINE_SEP+replicate(' ', numSpace));
	}

	public static String standardizeTextSplits(final String text) {
		// Replace splitting characters with a space character (e.g. -,_, long dash, any white space char etc)
		String withBlanks = text.replaceAll("[\\s_\\u2012\\u2013\\u2014\\u2015\\u2212\\u002D\\uFE63\\uFF0D]", " ");
		// Split on camel case (e.g. LogisticRegression -> Logistic Regression)
		String[] camelCaseQueries = withBlanks.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");

		// Concat the whole thing
		String result = camelCaseQueries[0];
		for (int i=1; i<camelCaseQueries.length; i++)
			result += ' '+ camelCaseQueries[i];

		// Make sure only single white spaces 
		return result.trim().replaceAll("\\s+", " ");

	}

	public static String handlePlural(String txt,int num) {
		if (num>1)
			return txt+"s";
		return txt;
	}

	public static String joinCollection(String delimiter, Collection<? extends Object> args) {
		List<Object> lst = new ArrayList<>(args.size());
		for (Object o : args){
			lst.add(o);
		}
		return join(delimiter,lst);
	}

	public static String join(String delimiter, List<? extends Object> args) {
		StringBuilder builder = new StringBuilder();

		for (int i = 0; i < args.size(); i++) {
			builder.append(args.get(i));

			if (i + 1 < args.size())
				builder.append(delimiter);
		}
		return builder.toString();
	}

	public static String join(String delimiter, Object[] args) {
		StringBuilder builder = new StringBuilder();

		for (int i = 0; i < args.length; i++) {
			builder.append(args[i]);

			if (i + 1 < args.length)
				builder.append(delimiter);
		}
		return builder.toString();
	}

	public static String join(String delimiter, int[] args) {
		StringBuilder builder = new StringBuilder();

		for (int i = 0; i < args.length; i++) {
			builder.append(args[i]);

			if (i + 1 < args.length)
				builder.append(delimiter);
		}
		return builder.toString();
	}

	public static String join(String delimiter, double[] args) {
		StringBuilder builder = new StringBuilder();

		for (int i = 0; i < args.length; i++) {
			builder.append(args[i]);

			if (i + 1 < args.length)
				builder.append(delimiter);
		}
		return builder.toString();
	}

	public static String stripQuotesAndEscape(String input) {
		String trimmed = input.trim();
		if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
			trimmed = trimmed.substring(1, trimmed.length()-1);
		}
		else if (trimmed.startsWith("\\")) {
			trimmed = trimmed.substring(1);
		}
		return trimmed;
	}
	
	public static String quoteEscapes(String input) {
		if (input == null || input.isEmpty())
			return input;
		return input.replaceAll("\t", "\\\\t").replaceAll("\n", "\\\\n").replaceAll("\r", "\\\\r");
	}

	public static String toStringNoBrackets(List<?> list) {
		if (list==null || list.isEmpty())
			return "";
		StringBuilder sb = new StringBuilder();
		for (int i=0;i<list.size()-1;i++) {
			sb.append(list.get(i)).append(", ");
		}
		sb.append(list.get(list.size()-1));
		return sb.toString();
	}

}

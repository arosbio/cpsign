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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiArgumentSplitter {

	private static final Logger LOGGER = LoggerFactory.getLogger(MultiArgumentSplitter.class);
	
	public static final String LIST_SPLIT_REGEX = "[\\s,]";
	

	public static List<String> split(final String input) {
		LOGGER.debug("Attempting to convert {} into a list", input);
		String value = input.trim();

		boolean containsQuote = value.contains("\"") || value.contains("'");

		if (! containsQuote) {
			// This is the easy one
			LOGGER.debug("Input contained no quotes");
			return getNonQuotedSplits(value);
		}

		// We have quotation marks
		if (value.contains("\"") && value.contains("'")) {
			throw new IllegalArgumentException("Input cannot contain both single (') and double (\") type quotation marks");
		}

		char quoteType = (value.contains("'") ? '\'' : '"');

		int nQuotes = StringUtils.countMatches(value, quoteType);

		if (nQuotes % 2 != 0) {
			LOGGER.debug("Invalid input, qoutation marks doesn't add up, counted {} in total", nQuotes);
			throw new IllegalArgumentException("Invalid input: " + input);
		} 

		if (nQuotes == 2) {
			// Check if they are the out-most in the string
			if (value.startsWith(""+quoteType) && value.endsWith(""+quoteType)) {
				LOGGER.debug("Input had outer-most quotation marks");
				return getNonQuotedSplits(value.substring(1, value.length()-1));
			}
		} 

		// Here we go through the quotes step by step
		LOGGER.debug("Attempting to split argument with quoteType {} : {}",quoteType, value);

		List<String> finalList = new ArrayList<>();

		int startIndex = 0;
		int nextIndex = Math.max(0,value.indexOf(quoteType));
		// First do everything before the first quote
		if (nextIndex > 0) {
			finalList.addAll(getNonQuotedSplits(value.substring(0, nextIndex)));
		}
		while (nextIndex < value.length() && value.indexOf(quoteType, nextIndex) > -1) {
			// Inside the loop we should have found the first quote-character
			startIndex = nextIndex+1; // step past the quote-character
			nextIndex = value.indexOf(quoteType, startIndex);

			if (nextIndex < 0) {
				throw new IllegalArgumentException(input + " was not correctly formatted, the quotation marks did not match up!");
			}

			// add the quoted section
			finalList.add(value.substring(startIndex, nextIndex));

			// Do the section until next quote
			startIndex = nextIndex+1;
			nextIndex = value.indexOf(quoteType, startIndex);
			if (nextIndex < 0)
				nextIndex = value.length();
			if (nextIndex > 0) {
				finalList.addAll(getNonQuotedSplits(value.substring(startIndex, nextIndex)));
			}
			else {
				// would've been handled anyhow by the while-statement, but less code to break..
				break; 
			}
		}


		LOGGER.debug("Final list after splitting with quotes: {}", finalList);
		return finalList;

	}

	private static List<String> getNonQuotedSplits(String input){
		List<String> finalList = new ArrayList<>();
		List<String> tmpRes = Arrays.asList(input.split(LIST_SPLIT_REGEX));
		for (String s : tmpRes) {
			String trimmed = s.trim();
			if (!trimmed.isEmpty()) {
				finalList.add(trimmed);
			}
		}
		return finalList;
	}

}

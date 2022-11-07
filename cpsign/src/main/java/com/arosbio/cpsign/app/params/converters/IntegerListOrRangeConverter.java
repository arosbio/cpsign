/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app.params.converters;

import java.util.ArrayList;
import java.util.List;

import com.arosbio.commons.CollectionUtils;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

public class IntegerListOrRangeConverter implements ITypeConverter<List<Integer>> {

	private static final Logger LOGGER = LoggerFactory.getLogger(IntegerListOrRangeConverter.class);
	//	private static final String CORRECT_BASE_SYNTAX = "<start>:<end>[:<step>]";

	public List<Integer> convert(final String input){
		if (input == null || input.trim().isEmpty())
			return new ArrayList<>();

		// First split the thing into a list of separate strings
		//		List<String> inputs = MultiArgumentSplitter.split(input);

		List<Integer> finalList = new ArrayList<>();

		String inp = input.trim();
		//		for (String inp: inputs) {
		if (inp.contains(":")) {
			// This is a start-stop-step-'base'-range!
			String[] splitted = inp.split(":");

			// should contain 3 parts - otherwise not in proper format
			if (splitted.length > 3) 
				throw new TypeConversionException("\""+input+"\" cannot be parsed into a valid range parameter");

			List<Integer> startEndStep = new ArrayList<>();
			try {

				for (String s : splitted) {
					try {
						startEndStep.add(Integer.parseInt(s));
					} catch (NumberFormatException e) {
						throw new TypeConversionException("Invalid syntax of range-parameter '"+splitted+"'");
					}
				}

				Triple<Integer,Integer,Integer> ses = getStartEndStep(startEndStep, input);
				finalList.addAll(CollectionUtils.listRange(ses.getLeft(), ses.getMiddle(), ses.getRight()));
			} catch (Exception e){
				if (e instanceof TypeConversionException)
					throw e;
				throw new TypeConversionException("\""+input+"\" cannot be parsed into a valid range parameter");
			}
		} else {
			try {
				finalList.add(Integer.parseInt(inp));
			} catch (NumberFormatException e) {
				LOGGER.debug("Could not convert input to a list", e);
				throw new TypeConversionException("Argument " + input + " could not be converted into a list");
			}
		}
		//		}

		return CollectionUtils.getUnique(finalList);
	}

	private static Triple<Integer,Integer,Integer> getStartEndStep(List<Integer> l, String input){

		if (l.size()==1)
			return ImmutableTriple.of(0, l.get(0), 1);
		else if (l.size()==2)
			return ImmutableTriple.of(l.get(0), l.get(1), 1);
		else if (l.size()==3)
			return ImmutableTriple.of(l.get(0), l.get(1), l.get(2));
		throw new TypeConversionException("Invalid syntax for input \""+input+'"');
	}

}

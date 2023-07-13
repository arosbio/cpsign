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
import java.util.Locale;

import com.arosbio.commons.CollectionUtils;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

public class ListOrRangeConverter implements ITypeConverter<List<Double>> {

	private static final Logger LOGGER = LoggerFactory.getLogger(ListOrRangeConverter.class);
	private static final String CORRECT_BASE_SYNTAX = "b<base>:<start>:<end>[:<step>] or <start>:<end>[:<step>]:b=<base>";

	public List<Double> convert(final String input){
		if (input == null || input.trim().isEmpty())
			return new ArrayList<>();

		List<Double> finalList = new ArrayList<>();

		String inp = input.trim();
		LOGGER.debug("Working with input: {}", inp);
		if (inp.contains(":")) {
			// This is a start-stop-step-'base'-range!
			String[] splitted = inp.split(":");

			// should contain maximum 4 parts - otherwise not in proper format
			if (splitted.length > 4) 
				throw new TypeConversionException("\""+input+"\" cannot be parsed into a valid range parameter");

			Double base = null;
			List<Double> startEndStep = new ArrayList<>();


			try {

				for (String s : splitted) {
					if (s.startsWith("b") || s.startsWith("B")) {
						// Base!
						if (base != null)
							throw new TypeConversionException("Invalid syntax of range-parameter '"+input+"', correct syntax is "+CORRECT_BASE_SYNTAX);
						try {
							base = getBaseFromString(s);
						} catch (IllegalArgumentException e) {
							throw new TypeConversionException("Invalid 'base' section of number-range parameter: '"+input +'\'');
						}
					} else {
						// Not a base (i.e., start/end/step)
						try {
							startEndStep.add(Double.parseDouble(s));
						} catch(NumberFormatException e) {
							throw new TypeConversionException("Invalid syntax of range-parameter '"+input+"'");
						}
					}
				}
				if (base != null) {
					Triple<Double,Double,Double> ses = getStartEndStep(startEndStep, input);
					finalList.addAll(CollectionUtils.listRange(ses.getLeft(), ses.getMiddle(), ses.getRight(), base));
				} else {
					Triple<Double,Double,Double> ses = getStartEndStep(startEndStep, input);
					finalList.addAll(CollectionUtils.listRange(ses.getLeft(), ses.getMiddle(), ses.getRight()));
				}

			} catch (Exception e){
				if (e instanceof TypeConversionException)
					throw e;
				throw new TypeConversionException("Argument '"+input+"' cannot be parsed into a valid range parameter");
			}
		} else {
			try {
				finalList.add(Double.parseDouble(inp));
			} catch (NumberFormatException e) {
				LOGGER.debug("Could not convert input to a list", e);
				throw new TypeConversionException("Argument '" + input + "' could not be converted into a list");
			}
		}

		return CollectionUtils.getUnique(finalList);
	}

	private static Triple<Double,Double,Double> getStartEndStep(List<Double> l, String input){

		if (l.size()==1)
			return ImmutableTriple.of(0., l.get(0), 1.);
		else if (l.size()==2)
			return ImmutableTriple.of(l.get(0), l.get(1), 1.);
		else if (l.size()==3)
			return ImmutableTriple.of(l.get(0), l.get(1), l.get(2));
		throw new TypeConversionException("Invalid syntax for input \""+input+'"');
	}

	private static double getBaseFromString(String input) throws IllegalArgumentException, NumberFormatException{
		String lc = input.toLowerCase(Locale.ENGLISH);
		if (lc.startsWith("base")) {
			lc = lc.substring(4);
		} else if (lc.startsWith("b")) {
			lc = lc.substring(1);
		} else {
			throw new IllegalArgumentException("Input not a valid base: " + input);
		}
		// Strip equal-sign if present
		if (lc.startsWith("=")) {
			lc = lc.substring(1);
		}

		return Double.parseDouble(lc);
	}

}

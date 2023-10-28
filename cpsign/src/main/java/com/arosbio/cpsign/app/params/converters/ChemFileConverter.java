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

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.io.in.CSVFile;
import com.arosbio.chem.io.in.ChemFile;
import com.arosbio.chem.io.in.JSONFile;
import com.arosbio.chem.io.in.SDFile;
import com.arosbio.commons.StringUtils;
import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.cpsign.app.param_exceptions.InputConversionException;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.io.UriUtils;

import picocli.CommandLine.IParameterConsumer;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.TypeConversionException;

public class ChemFileConverter implements IParameterConsumer {

	private static final Logger LOGGER = LoggerFactory.getLogger(ChemFileConverter.class);

	private static final String CORRECT_SYNTAX = "<parameter flag> " + ArgumentType.CHEM_FILE_ARGS;

	String getCorrectSyntax(){
		return CORRECT_SYNTAX;
	}

	public static class TrainDataConverter extends ChemFileConverter {
		private static final String FLAG_ARG = "-td | --train-data ";
		@Override
		String getCorrectSyntax() {
			return FLAG_ARG + ArgumentType.CHEM_FILE_ARGS;
		}
	}

	public static class ModelExclusiveDataConverter extends ChemFileConverter {
		private static final String FLAG_ARG = "-md | --model-data ";
		@Override
		String getCorrectSyntax() {
			return FLAG_ARG + ArgumentType.CHEM_FILE_ARGS;
		}
	}

	public static class CalibrationExclusiveDataConverter extends ChemFileConverter {
		private static final String FLAG_ARG = "-cd | --calibration-data ";
		@Override
		String getCorrectSyntax() {
			return FLAG_ARG + ArgumentType.CHEM_FILE_ARGS;
		}
	}

	public static class PredictFileConverter extends ChemFileConverter {
		private static final String FLAG_ARG = "-p | --predict-file ";
		@Override
		String getCorrectSyntax() {
			return FLAG_ARG + ArgumentType.CHEM_FILE_ARGS;
		}
	}

	public static class PercentilesDataConverter extends ChemFileConverter {
		private static final String FLAG_ARG = "--percentiles-data";
		@Override
		String getCorrectSyntax() {
			return FLAG_ARG + ArgumentType.CHEM_FILE_ARGS;
		}
	}

	@Override
	public void consumeParameters(Stack<String> args, 
			ArgSpec argSpec, 
			CommandSpec commandSpec) {

		LOGGER.debug("Attempting to consume parameters for chem-file input");

		int initStackSize = args.size();

		if (args.size() < 2)
			throw new TypeConversionException("Chemical file must at least specify the file format and a URI/path to the file");

		// First should be the format _or_ format including :-syntax formatted arguments
		String format;
		List<String> extraOpts;
		URI uri;

		// Process differently depending on using new :-syntax or older one with each separated by whitespace character(s)
		boolean newSyntax = args.peek().contains(ParameterUtils.SUB_PARAM_SPLITTER);
		try {
			if (newSyntax) {
				Triple<String,List<String>,URI> result = processUsingNewSyntax(args);
				format = result.getLeft();
				extraOpts = result.getMiddle();
				uri = result.getRight();
			} else {
				Triple<String,List<String>,URI> result = processUsingOldSyntax(args);
				format = result.getLeft();
				extraOpts = result.getMiddle();
				uri = result.getRight();
			}
		} catch (Exception e) {
			LOGGER.debug("failed converting chemical file correctly",e);
			throw new InputConversionException(initStackSize, e.getMessage());
		}

		// Convert to the ChemFile implementation
		ChemFile file = getFormat(uri, format, initStackSize);

		if (!extraOpts.isEmpty()) {

			if (! (file instanceof Configurable)) {
				throw new InputConversionException(initStackSize,file.getFileFormat() + " does not support additional arguments"); 
			}

			LOGGER.debug("Setting config arguments for {}; {}",file.getFileFormat(), StringUtils.toStringNoBrackets(extraOpts));

			Configurable csv = (Configurable) file;

			try {
				ConfigUtils.setConfigs(csv, extraOpts, format+ ' ' + StringUtils.toStringNoBrackets(extraOpts) + ' ' + uri);
			} catch (Exception e) {
				LOGGER.debug("Failed configuring "+file.getFileFormat()+" input format",e);

				// here try to deduce if the user mixed the new and old syntax, which then causes issues
				String extraErrText = "";
				if (e.getMessage().toLowerCase(Locale.ENGLISH).contains("correct syntax") && ! newSyntax){
					for (String opt : extraOpts){
						if (opt.contains(ParameterUtils.SUB_PARAM_SPLITTER)){
							LOGGER.debug("Extra option contains sub-param splitter: may be mixing of input syntax");
							extraErrText = "%nThe input seems to mix the two input syntaxes, please see explain chemical-files for more info";
						}
					}
				}

				throw new InputConversionException(initStackSize,"Invalid sub parameter(s) to "+file.getFileFormat()+" file: " + e.getMessage() + extraErrText);
			}

		}

		argSpec.setValue(file);

	}

	private Triple<String,List<String>,URI> processUsingNewSyntax(Stack<String> args){
		// new syntax, should be format:key1=value1 <URI>
		String formatAndConf = args.pop();
		String[] splits = formatAndConf.split(ParameterUtils.SUB_PARAM_SPLITTER);
		String format = cleanFormatArg(splits[0]);

		// Extra args
		List<String> extraArgs = new ArrayList<>();
		if (splits.length>1) {
			for (int i=1;i<splits.length;i++)
				extraArgs.add(splits[i]);
		}

		// URI / Path
		if (args.isEmpty())
			throw new IllegalArgumentException("Missing required URI/Path, the correct syntax is: " + getCorrectSyntax());
		URI uri = null;
		String tmpVal = null;
		try {
			tmpVal = args.pop();
			uri = UriUtils.getURI(tmpVal);
		} catch (IOException |IllegalArgumentException e) {
			LOGGER.debug("URI/Path not valid (or if's not a URI - wrong syntax!): {}",tmpVal);
			if (tmpVal.contains(ParameterUtils.SUB_PARAM_SPLITTER)){
				LOGGER.debug("The URI/path contains a sub-param splitter character (:), it might be wrong syntax as well..");

			}
			throw new IllegalArgumentException("Invalid URI/path '"+ tmpVal+'\'');
		}

		return ImmutableTriple.of(format, extraArgs, uri);
	}

	private Triple<String,List<String>,URI> processUsingOldSyntax(Stack<String>args){

		// First should be format
		String format = cleanFormatArg(args.pop());

		// List of extra arguments
		List<String> extraOpts = new ArrayList<>();
		// URI
		URI uri = null;

		while (args.size() > 0) {
			String val = args.pop();

			// Check if URI - then we're finished
			try {
				// Here we stop popping of the argument stack
				uri = UriUtils.getURI(val);
				break;
			} catch (IOException |IllegalArgumentException e) {
				LOGGER.debug("Checked arg {}: not valid URI so keep checking..",val);
			}

			if (val.startsWith("-")) {
				// next argument was a new flag - put the argument back on stack and exit
				args.push(val);
				break; 
			}

			// Add to extra arguments
			extraOpts.add(val);

		}

		if (uri == null) {
			// If we end up here - there was no valid URI/path found
			LOGGER.debug("Found no valid URI or path - invalid input of user");
			if (extraOpts.isEmpty()) {
				// No argument given at all
				throw new IllegalArgumentException("Missing required URI or path, the correct syntax is: " + getCorrectSyntax());
			}
			String lastArg = extraOpts.get(extraOpts.size()-1);
			if (isCSVConfigParam(lastArg))
				throw new IllegalArgumentException("Missing required URI or path, the correct syntax is: " + getCorrectSyntax());
			
			throw new IllegalArgumentException("'"+ lastArg + "' is not a valid URI or path");
		}

		return ImmutableTriple.of(format, extraOpts, uri);
	}
	
	private static boolean isCSVConfigParam(String text) {
		String lc = text.toLowerCase();
		for (ConfigParameter cp : new CSVFile(null).getConfigParameters()) {
			for (String name : cp.getNames()) {
				if (lc.startsWith(name.toLowerCase()))
					return true;
			}
		}
		return false;
	}

	private static String cleanFormatArg(String arg) {
		String lc = arg.toLowerCase();
		if (lc.startsWith("format"+ParameterUtils.SUB_PARAM_KEY_VALUE_DELIM)) {
			return arg.substring(("format"+ParameterUtils.SUB_PARAM_KEY_VALUE_DELIM).length());
		}
		return arg;
	}
	

	private static ChemFile getFormat(URI uri, String format, int index) {
		format = format.toLowerCase();
		switch (format) {
		case "csv":
			return new CSVFile(uri);
		case "tsv":
			return new CSVFile(uri).setDelimiter('\t');
		case "sdf":
			return new SDFile(uri);
		case "json":
			return new JSONFile(uri);
		default:
			throw new InputConversionException(index,"File format not recognized: " + format);
		}
	}

}

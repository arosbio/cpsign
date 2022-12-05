/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app.params.mixins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.cpsign.app.param_exceptions.InputConversionException;
import com.arosbio.cpsign.app.param_exceptions.NotOfCorrectTypeException;
import com.arosbio.cpsign.app.params.converters.ConfigUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.data.transform.Transformer;

import picocli.CommandLine.IParameterConsumer;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.TypeConversionException;

public class TransformerMixin {

	@Option(names = {"--transform", "--transformations" }, 
			description = "Transformations that filter, solves duplicate entries, feature selection and more. Run "+ParameterUtils.RUN_EXPLAIN_ANSI_ON+"explain transformations"+ParameterUtils.ANSI_OFF+
			" to get a list of all available transformations",
			parameterConsumer = TransformerParamConsumer.class,
			split = ParameterUtils.SPLIT_WS_REGEXP,
			arity = "0..*",
			paramLabel = ArgumentType.TEXT
			)
	public List<Transformer> transformers = new ArrayList<>();
	
	public static class TransformerParamConsumer implements IParameterConsumer {
		private static final Logger LOGGER = LoggerFactory.getLogger(TransformerParamConsumer.class);
		
		@Override
		public void consumeParameters(Stack<String> args, 
				ArgSpec argSpec, CommandSpec commandSpec) {
			
			LOGGER.debug("Attempting to consume descriptor parameters from args");
			int initStackSize = args.size();
			List<Transformer> transformerList = argSpec.getValue();
			
			if (transformerList == null) {
				argSpec.setValue(new ArrayList<>());
				transformerList = argSpec.getValue();
			}
			
			while (args.size()>0) {
				String nextArg = args.peek();
				LOGGER.debug("Processing argument '{}' as transformer",nextArg);
				
				if (nextArg.startsWith("-")) {
					LOGGER.debug("Next argument in call stack was '{}' - starts with an '-' and this stop parsing as transformers",nextArg);
					break;
				}
				
				try {
					transformerList.add(convertSingleArg(nextArg));
					args.pop();
				} catch (NotOfCorrectTypeException notCorrect) {
					LOGGER.debug("Next argument was not recognized as a transformer - stoping argument consuming");
					break;
				} catch (TypeConversionException e) {
					// Wrapping it in our custom exception
					throw new InputConversionException(initStackSize, e.getMessage());
				}
			} // end of while loop
			
		}
		
		
		public static Transformer convertSingleArg(String text) { 
			String[] splits = text.split(ParameterUtils.SUB_PARAM_SPLITTER);
			Transformer transformer = null;
			try {
				// Get the transformer implementation
				transformer = FuzzyServiceLoader.load(Transformer.class, splits[0]);
			} catch (Exception e) {
				LOGGER.debug("Failed getting transformer for input: {} - this is probably something else", splits[0]);
				throw new NotOfCorrectTypeException();
			} 
			
			List<String> args = new ArrayList<>(Arrays.asList(splits));
			args.remove(0);  // First one is the descriptor itself

			try {
				
				ConfigUtils.setConfigs(transformer, args, text);
				return transformer;

			} catch (TypeConversionException e) {
				// should have been handled already
				throw e; 
			} catch (Exception e) {
				LOGGER.debug("Failed when trying to configure transformer",e);
				throw new TypeConversionException("Failed configuring transformer "+transformer.getName() + ": "+ e.getMessage());
			}
		}
		
	}

}

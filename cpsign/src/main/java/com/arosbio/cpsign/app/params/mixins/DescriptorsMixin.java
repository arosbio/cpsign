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

import com.arosbio.cheminf.descriptors.ChemDescriptor;
import com.arosbio.cheminf.descriptors.DescriptorFactory;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.cpsign.app.param_exceptions.InputConversionException;
import com.arosbio.cpsign.app.params.converters.ConfigUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.google.common.collect.ImmutableList;

import picocli.CommandLine.IParameterConsumer;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.TypeConversionException;

public class DescriptorsMixin {

	private static final Logger LOGGER = LoggerFactory.getLogger(DescriptorsMixin.class);

	@Option(
			names= {"-d", "--descriptors"},
			description = "Descriptors to use. Run "+ParameterUtils.RUN_EXPLAIN_ANSI_ON+"explain descriptors"+ParameterUtils.ANSI_OFF+" to view all available " +
					"decriptors%n" + 
					ParameterUtils.DEFAULT_PRE_TEXT + "signatures:1:3",
					parameterConsumer = DescriptorParamConsumer.class,
					split = ParameterUtils.SPLIT_WS_REGEXP,
//					defaultValue = "signatures:1:3", // TODO - change with bug fix in picocli?
					arity = ParameterUtils.LIST_TYPE_ARITY,
					paramLabel = ArgumentType.TEXT)
	public List<ChemDescriptor> descriptors = ImmutableList.of(new SignaturesDescriptor(1, 3));


	public static class DescriptorParamConsumer implements IParameterConsumer {
		
		// TODO - this is needed due to bug in picocli? defaultValue not picked up correctly 
		private boolean firstCall = true;
		
		@Override
		public void consumeParameters(Stack<String> args, 
				ArgSpec argSpec, 
				CommandSpec commandSpec) {

			LOGGER.debug("Attempting to consume descriptor parameters from args");
			int initialSize = args.size();

			List<ChemDescriptor> descriptorList = argSpec.getValue();
			
			if (firstCall || descriptorList == null) {
				argSpec.setValue(new ArrayList<>());
				descriptorList = argSpec.getValue();
			}
			firstCall = false;

			while (args.size()>0) {
				
				String nextArg = args.peek();
				LOGGER.debug("Peeked next argument '{}' from the parameter stack",nextArg);
				
				if (nextArg == null || nextArg.isEmpty() || nextArg.startsWith("-")) {
					LOGGER.debug("Stopped processing parameter stack at '{}' - probably not more descriptors",
							nextArg);
					return;
				}

				try {
					descriptorList.addAll(convertSingleArg(nextArg));
					args.pop();
					
				} catch (TypeConversionException e) {
					LOGGER.debug("failed converting '{}' - this is likely a bad config of the argument - passing it but converted to custom exception class",
							nextArg, e);
					throw new InputConversionException(initialSize, e.getMessage());
				} catch (Exception e) {
					LOGGER.debug("Failed converting '{}' as a descriptor - with a generic exception - likely not a descriptor argument - returning parsing to picocli and hope all is well",
							nextArg, e);
					return;
				}
			}

		}

		static List<ChemDescriptor> convertSingleArg(String text){
			
			String lc = text.trim().toLowerCase();
			if (lc.equals("all-cdk")) {
				return DescriptorFactory.getCDKDescriptorsNo3D();
			} else if (lc.equals("all-cdk-3d")) {
				return DescriptorFactory.getCDKDescriptorsRequire3D();
			}
			LOGGER.debug("Input was not a list 'all-cdk' or 'all-cdk-3d' - trying as a single descriptor");

			String[] splits = text.split(ParameterUtils.SUB_PARAM_SPLITTER);

			// Get the descriptor implementation
			ChemDescriptor desc = DescriptorFactory.getInstance().getDescriptorFuzzyMatch(splits[0]);

			if (splits.length>1) {
				LOGGER.debug("Matched input to descriptor {} - now checking for further configs",desc.getName());
				List<String> args = new ArrayList<>(Arrays.asList(splits));
				args.remove(0); // First one is the descriptor itself
				ConfigUtils.setConfigs(desc, args, text);
			}
			
			return ImmutableList.of(desc);
		}

	}
}

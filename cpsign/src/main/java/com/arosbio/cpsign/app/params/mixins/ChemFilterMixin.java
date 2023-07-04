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
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cheminf.ChemFilter;
import com.arosbio.cheminf.filter.HACFilter;
import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.cpsign.app.params.converters.ConfigUtils;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;

import picocli.CommandLine.Option;

public class ChemFilterMixin {

	private static final Logger LOGGER = LoggerFactory.getLogger(ChemFilterMixin.class);
	private final static String DEFAULT_VALUE = "HAC:min=5";

	private List<ChemFilter> filters = new ArrayList<>();
	private boolean explicitlyNone = false;

	@Option(names = {"--chem-filters" }, 
			description = "Chemical structure filters that should be applied on the loaded records. Run "+ParameterUtils.RUN_EXPLAIN_ANSI_ON+"explain chem-filters"+ParameterUtils.ANSI_OFF+
			" to get a list of all available filters. Note that there is a default value, in order to not have any filter at all, pass --chem-fiter none%n" + ParameterUtils.DEFAULT_PRE_TEXT + DEFAULT_VALUE,
			split = ParameterUtils.SPLIT_WS_REGEXP,
			arity = "0..*",
			paramLabel = ArgumentType.TEXT
			)
	public void setFilters(List<String> args){
		for (String a : args){
			// Check if explicitly not use any filters
			String lc = a.toLowerCase(Locale.ENGLISH).trim();
			if ("none".equals(lc) || "null".equals(lc)|| "na".equals(lc) || "no".equals(lc)){
				LOGGER.debug("Specified to not use any chem filter, skipping the rest: {}", args);
				filters.clear();
				explicitlyNone = true;
				break;
			}
			LOGGER.debug("Parsing the following: '{}' as a chemical-filter", a);
			filters.add(convert(a));
		}
	}

	@Option(names = "--min-hac",
			description= "DEPRECATED: use the --chem-filters flag instead for increased flexibility.",
			paramLabel = ArgumentType.INTEGER)
	@Deprecated
	private Integer minHAC = null;


	public List<ChemFilter> getFilters(CLIConsole console){
		if (minHAC!=null){
			LOGGER.debug("User gave the --min-hac flag, which is now deprecated - will give a notification");
			StringBuilder errMessage = new StringBuilder("Deprecation warning: the ")
				.append(ParameterUtils.PARAM_FLAG_ANSI_ON).append("--min-hac").append(ParameterUtils.ANSI_OFF)
				.append(" parameter is now deprecated and will be removed in a future release, please use the ")
				.append(ParameterUtils.PARAM_FLAG_ANSI_ON).append("--chem-filters").append(ParameterUtils.ANSI_OFF)
				.append(" instead. Run ").append(ParameterUtils.RUN_EXPLAIN_ANSI_ON).append("explain chem-filters").append(ParameterUtils.ANSI_OFF)
				.append(" for further information.%n");
			
			console.printlnWrapped(errMessage.toString(), PrintMode.NORMAL);
		}

		if (explicitlyNone)
			return new ArrayList<>();
		else if (filters.isEmpty()){
			// if no filters given
			// Use the deprecated value if given
			if (minHAC != null){
				filters.add(new HACFilter().withMinHAC(minHAC));
			} else {
				// Or use the default HAC filter
				filters.add(new HACFilter());
			}
		}
		return filters;
	}

	private static ChemFilter convert(final String text) {
		LOGGER.debug("Converting {} to ChemFilter", text);

		String[] splits = text.split(":");

		// Get the descriptor implementation
		ChemFilter filter = FuzzyServiceLoader.load(ChemFilter.class, splits[0]);

		if (splits.length>1) {
			List<String> args = new ArrayList<>(Arrays.asList(splits));
			args.remove(0); // First one is the descriptor itself
			ConfigUtils.setConfigs(filter, args, text);
		}

		return filter;
	}
	
	/*
	TODO - hopefully can replace all above with the code below, if bug in picocli is fixed - less complex
	@Option(names = {"--chem-filters" }, 
			description = "Chemical structure filters that should be applied on the loaded records. Run "+ParameterUtils.RUN_EXPLAIN_ANSI_ON+"explain chem-filters"+ParameterUtils.ANSI_OFF+
			" to get a list of all available filters. Note that there is a default value, in order to not have any filter at all, pass --chem-fiter none%n" + ParameterUtils.DEFAULT_PRE_TEXT + DEFAULT_VALUE,
			parameterConsumer = ChemFilterConsumer.class,
			split = ParameterUtils.SPLIT_WS_REGEXP,
			arity = "0..*",
			paramLabel = ArgumentType.TEXT,
			defaultValue = "HAC:min=5"
			)
	public List<ChemFilter> filters = new ArrayList<>();
	

	static class ChemFilterConsumer implements IParameterConsumer {

		private static final Logger LOGGER = LoggerFactory.getLogger(ChemFilterConsumer.class);

		@Override
		public void consumeParameters(Stack<String> args, ArgSpec argSpec, CommandSpec commandSpec) {
			
			List<ChemFilter> list = argSpec.getValue();
			System.err.println("RUNNING CONSUME PARAMETERS, current list: " + list);
			while (!args.isEmpty()){
				String top = args.peek();
				if (top.startsWith("-")){
					LOGGER.debug("top most argument starts with '-', stop parsing as a chemfilter");
					break;
				}
				
				String lc = top.toLowerCase(Locale.ENGLISH).trim();
				if ("none".equals(lc) || "null".equals(lc)|| "na".equals(lc) || "no".equals(lc)){
					LOGGER.debug("Specified to not use any chem filter, returning... (popping the value before and clearling the list)");
					args.pop();
					list.clear();
					break;
				}
				try {
					ChemFilter f = convertSingleArgument(top);
					LOGGER.debug("Converted filter: {}", f);
					list.add(f);
				} catch (Exception e){
					LOGGER.debug("Failed converting {} into a chem-filter, returning un-popped arg-stack", top);
					break;
				}
				// once we know it was an OK value, pop the top of the stack
				args.pop();

			}
			LOGGER.debug("Finished consuming --chem-filters arguments, current list of filters: {}", list);
			
		}

		public ChemFilter convertSingleArgument(final String text) {
			LOGGER.debug("Converting {} to ChemFilter", text);

			String[] splits = text.split(":");

			// Get the descriptor implementation
			ChemFilter filter = FuzzyServiceLoader.load(ChemFilter.class, splits[0]);

			if (splits.length>1) {
				List<String> args = new ArrayList<>(Arrays.asList(splits));
				args.remove(0); // First one is the descriptor itself
				ConfigUtils.setConfigs(filter, args, text);
			}

			return filter;
		}

	}
*/

}

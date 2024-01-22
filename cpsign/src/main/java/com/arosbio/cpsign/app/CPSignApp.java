/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.fusesource.jansi.AnsiConsole;
import org.fusesource.jansi.AnsiRenderer;
import org.openscience.cdk.tools.LoggingToolFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.ConfAI;
import com.arosbio.chem.logging.Slf4jLoggingTool;
import com.arosbio.commons.EarlyStoppingException;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.StringUtils;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.cpsign.app.error_handling.InvalidParametersError;
import com.arosbio.cpsign.app.param_exceptions.InputConversionException;
import com.arosbio.cpsign.app.params.converters.FileConverter;
import com.arosbio.cpsign.app.params.converters.URIConverter;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.io.UriUtils;

import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.IExitCodeExceptionMapper;
import picocli.CommandLine.IParameterExceptionHandler;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.MissingParameterException;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.PicocliException;
import picocli.CommandLine.PropertiesDefaultProvider;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.TypeConversionException;
import picocli.CommandLine.UnmatchedArgumentException;

/**
 * The CLI entry point (i.e. the {@link #main(String[])} method)
 */
@Command(name = CPSignApp.PROGRAM_NAME, 
		subcommands = {
			Precompute.class,
			Transform.class,
			Train.class,
			Predict.class,
			PredictOnline.class,
			Tune.class,
			TuneScorer.class,
			CrossValidate.class,
			Validate.class,
			Aggregate.class,
			AggregateFast.class,
			GenerateSignatures.class,
			ModelInfoCMD.class,
			ListFeatures.class,
			ExplainArgument.class,
			FilterData.class,
			GenerateEncryptionKey.class
		}, 
		versionProvider = CPSignApp.class, 
		mixinStandardHelpOptions = true, 
		scope = ScopeType.INHERIT, 
		usageHelpAutoWidth = true, 
		requiredOptionMarker = '*',

		synopsisHeading = CPSignApp.USAGE_HEADER, 
		synopsisSubcommandLabel = "COMMAND", 
		abbreviateSynopsis = true,

		// customSynopsis
		optionListHeading = CPSignApp.PARAMETERS_HEADER,

		commandListHeading = CPSignApp.COMMANDS_HEADER,

		exitCodeListHeading = CPSignApp.EXIT_CODE_HEADER, exitCodeList = {
				"0:Successful program execution",
				"1:Usage error, e.g. faulty argument(s)",
				"2:Out of memory, the program ran out of memory either due to large problem size or internal errors. Possibly fixable by giving more memory to the JVM",
				"4:Internal error. Most likely due to a bug in the code, please contact Aros Bio with the log-file for the run and hints of what caused the issue" },

		footer = "%n", 
		sortOptions = false

)
public class CPSignApp implements IVersionProvider {

	private static final Logger LOGGER = LoggerFactory.getLogger(CPSignApp.class);

	public static final String PROGRAM_NAME = "cpsign";
	public static final String DESCRIPTION_HEADER = "%n@|bold DESCRIPTION|@%n";
	public static final String COMMANDS_HEADER = "%n@|bold COMMANDS|@%n";
	public static final String EXIT_CODE_HEADER = "%n@|bold EXIT CODES|@%n";
	public static final String USAGE_HEADER = "%n@|bold USAGE|@%n";
	public static final String PARAMETERS_HEADER = "%n@|bold PARAMETERS|@%n";

	// Hold a reference to this, to keep from GC and accidentally change global seed
	private static GlobalConfig settings = GlobalConfig.getInstance();

	@Option(hidden = true, names = { "--no-ansi", "--ansi-off" })
	private boolean turnOffAnsi = false;

	private static class SeedAndLogConfig {
		@Option(names = { "--seed" }, hidden = true)
		public void setRNGSeed(long seed) {
			settings.setRNGSeed(seed);
			LOGGER.debug("Setting user-defined RNG-seed: {}", seed);
		}

		@ArgGroup(exclusive = true, multiplicity = "0..1")
		public ExclusiveOptions exclusive = new ExclusiveOptions();

		public static class ExclusiveOptions {
			@Option(names = "--logfile", 
				description = "Path to a user-set logfile, will be specific for this run", 
				paramLabel = ArgumentType.FILE_PATH, 
				negatable = false, 
				hidden=true)
			public File logfile;

			@Option(names = "--no-logfile", 
				description = "Write no logfile", 
				negatable = false,
				hidden=true)
			public boolean turnOfLogging = false;
		}

		public void configLogging() {

			if (exclusive.turnOfLogging)
				return;

			if (exclusive.logfile != null) {
				// custom logfile
				try {
					LoggerUtils.addRollingFileAppender(exclusive.logfile.toString());
					LOGGER.debug("Added custom logfile: {}",exclusive.logfile.toString());
				} catch (Exception e) {
					throw new IllegalArgumentException("Failed creating custom log file:%n" + e.getMessage());
				}
			} else {
				// If no specific logfile given - use the standard one
				addDefaultLogfile();
				LOGGER.debug("Added default logfile (cpsign.log)");
			}
		}

		private static void addDefaultLogfile() {
			// If no specific logfile given - use the standard one
			try {
				LoggerUtils.addStandardCPSignRollingFileAppender();
			} catch (Exception e) {
				throw new IllegalArgumentException(
						"Could not create the default CPSign logfile, please either specify custom logfile in a location where you have write permissions to the --logfile option or use the --no-logfile option");
			}
		}
	}

	/**
	 * Entry point of the CLI 
	 * @param args arguments passed to the CLI
	 */
	public static void main(String[] args) {
		LoggingToolFactory.setLoggingToolClass(Slf4jLoggingTool.class);

		Properties p = new Properties();
		boolean loaded = false;
		try (InputStream s = CPSignApp.class.getClassLoader().getResourceAsStream("confai.properties")) {
			if (s != null){
				p.load(s);
				loaded = true;
			}
		} catch (Exception e) {
			LOGGER.debug("Could not load resources from property-file", e);
		}

		try {
			AnsiConsole.systemInstall();
			GlobalConfig.getInstance().setAnsiAvailable(CLIConsole.getInstance().ansiON());
			CPSignApp app = new CPSignApp();
			CommandLine cmd = new CommandLine(app);

			int status = 0;
			try {

				// Special handle when not giving any arguments (e.g., cpsign train) should
				// result in usage being printed for train
				if (args.length == 0) {
					// If length is 0 - only 'cpsign' program given
					if (app.turnOffAnsi)
						cmd.usage(System.out, Ansi.OFF);
					else
						cmd.usage(System.out);
					return;
				} else if (args.length == 1) {
					// If length is 1 - might be the sub-command (must contain at least 3
					// characters)
					for (Map.Entry<String, CommandLine> prog : cmd.getSubcommands().entrySet()) {
						if (prog.getKey().equals(args[0])
								|| (args[0].length() > 2 && prog.getKey().startsWith(args[0]))) {
							if (app.turnOffAnsi)
								prog.getValue().usage(System.out, Ansi.OFF);
							else
								prog.getValue().usage(System.out);
							return;
						}
					}
				}

				// Update the @-file paths
				updateAtFilePaths(args);

				// Config RNG seed and logfile before anything else
				configLogAndSeed(args);

				// Check echo of arguments
				echoArgs(args);

				if (loaded) {
					cmd.setDefaultValueProvider(new PropertiesDefaultProvider(p));
				}
				cmd.setAbbreviatedSubcommandsAllowed(true)
					.setExitCodeExceptionMapper(new ExitCodeMapper())
					.setParameterExceptionHandler(new ShortErrorMessageHandler())
					.setExecutionExceptionHandler(new ExecutionHandler())
					.setTrimQuotes(true)
					.setPosixClusteredShortOptionsAllowed(false);

				// Converters
				cmd.registerConverter(URI.class, new URIConverter());

				LOGGER.debug("Running CPSign of version {}", ConfAI.getVersionAsString());
				// Execute command
				status = cmd.execute(args);

			} catch (Error | Exception e) {

				// Make sure to debug the issue so it's at the end of the logfile
				LOGGER.error("failed CPSign execution with error/exception", e);
				status = new ExecutionHandler().handleThrowable(e.getMessage(), e, cmd.getCommandSpec().qualifiedName(),
						cmd.getErr());
			} finally {
				CLIConsole.getInstance().close();
			}

			if (status != 0)
				System.exit(status);
		} finally {
			AnsiConsole.systemUninstall();
		}
	}

	private static void echoArgs(String[] args){
		boolean echo = false;
		StringBuilder sb = new StringBuilder("%nRunning command:%n");
		for (String arg : args) {
			if (arg.equals("--echo"))
				echo = true;
			if (arg.split(" ").length > 1 && !arg.contains("\"")) {
				sb.append('"');
				sb.append(arg);
				sb.append('"');
			} else {
				sb.append(arg);
			}

			// print a space between each parameter
			sb.append(' ');
		}
		sb.append("%n");

		if (echo){
			CLIConsole.getInstance().println(sb.toString(), PrintMode.NORMAL);
		}
		else {
			LOGGER.debug(sb.toString());
		}
		
	}

	private static void configLogAndSeed(String[] args){
		LOGGER.debug("Attempting to config RNG seed and logging");
		SeedAndLogConfig obj = new SeedAndLogConfig();
		CommandLine cmdTmp = new CommandLine(obj);
		cmdTmp.registerConverter(File.class, new FileConverter());
		cmdTmp.setUnmatchedArgumentsAllowed(true);

		cmdTmp.parseArgs(args);
		obj.configLogging();
		LOGGER.debug("config done");
	}

	/**
	 * Getter for a pretty version-string used by the Picocli tooling
	 * @return lines of text to output to terminal
	 */
	@Override
	public String[] getVersion() throws Exception {
		String v = "Undefined";
		String ts = "Undefined";
		try {
			v = ConfAI.getVersionAsString();
			ts = ConfAI.getBuildTS();
		} catch (Exception e) {
		}

		return new String[] {
				"",
				"@|bold CPSign|@ - Conformal Prediction with the signatures molecular descriptor",
				"\u00a9 2022, Aros Bio AB, www.arosbio.com",
				"Version: " + v,
				"Build time: " + ts,
				""
		};
	}

	private static void updateAtFilePaths(String[] args) {
		// resolve @-syntax (with different paths than what JCommander can handle)
		for (int i = 0; i < args.length; i++) {
			if (args[i].startsWith("@")) {
				try {
					String resolvedParamFile = UriUtils.resolvePath(args[i].substring(1));
					if (!UriUtils.verifyLocalFileExists(resolvedParamFile)) {
						throw new IOException("Path to @file is not valid: " + args[i]);
					}

					args[i] = "@" + resolvedParamFile;
				} catch (IOException e) {
					// could not resolve path
					CLIConsole.getInstance().failWithArgError(e.getMessage());
				}
			}
		}
	}

	static class ExitCodeMapper implements IExitCodeExceptionMapper {

		@Override
		public int getExitCode(Throwable t) {
			if (t instanceof PicocliException)
				return ExitStatus.USER_ERROR.code;
			if (t instanceof UnmatchedArgumentException)
				return ExitStatus.USER_ERROR.code;
			if (t instanceof TypeConversionException || t instanceof IllegalArgumentException
					|| t instanceof MissingParameterException || t instanceof EarlyStoppingException)
				return ExitStatus.USER_ERROR.code;
			if (t instanceof InternalError)
				return ExitStatus.PROGRAM_ERROR.code;
			if (t instanceof InvalidParametersError)
				return ExitStatus.USER_ERROR.code;
			if (t instanceof OutOfMemoryError) {
				LOGGER.error(
						"The application run out of memory, please give the Java VM more memory by for instance giving the parameter -Xmx6G (give 6GB RAM)%n%nExample: java -jar -Xmx6G cpsign [command] [parameters]");
				return ExitStatus.OUT_OF_MEMORY.code;
			}
			return ExitStatus.PROGRAM_ERROR.code;
		}
	}

	/**
	 * Class that is required to tweak ParameterException's generated from Picocli,
	 * will write out suggestions and (possibly) clean up the generated
	 * error-message and
	 * delegate the error-printing to {@link ExecutionHandler} instead to streamline
	 * the message to the user
	 */
	static class ShortErrorMessageHandler implements IParameterExceptionHandler {

		public int handleParseException(ParameterException ex, String[] args) {
			LOGGER.debug("Invalid arguments - compiling error message to user", ex);

			CommandLine cmd = ex.getCommandLine();
			String usageErrMessage = null;

			// Check if can improve the message
			if (canImproveMsg(ex.getCause())) {

				try {
					if (ex.getCause() instanceof InputConversionException) {
						usageErrMessage = convertInputConvMsg((InputConversionException) ex.getCause(),
								cmd.getParseResult().expandedArgs());
					} else if (ex.getCause() instanceof TypeConversionException) {
						usageErrMessage = convertTypeConvMsg(ex, (TypeConversionException) ex.getCause(),
								ex.getArgSpec(),
								cmd.getParseResult().expandedArgs());
					}
				} catch (Exception e) {
					LOGGER.error("Failed compiling a good error message - setting the original error message", e);
					usageErrMessage = ex.getMessage();
				}
			} else {
				usageErrMessage = ex.getMessage();
			}

			// After this, check if message is null/empty - then return internal error
			if (usageErrMessage == null || usageErrMessage.isEmpty()) {
				usageErrMessage = "%n" + CLIConsole.INTERNAL_PROGRAM_ERROR_TXT;
			}

			return new ExecutionHandler()
					.handleThrowable(usageErrMessage, ex,
							cmd.getCommandSpec().qualifiedName(), cmd.getErr());

		}

		private static boolean canImproveMsg(final Throwable expt) {
			// We can improve the these two exceptions
			return expt != null && (expt instanceof TypeConversionException ||
					expt instanceof InputConversionException);
		}

		private static String convertTypeConvMsg(ParameterException paramEx, TypeConversionException ex, ArgSpec spec,
				List<String> args) {
			LOGGER.debug("Compiling error message from TypeConversionException");

			// Check if top level message contains ".. before arg[index] .."
			// which allows us to fetch the flag which was offending
			String topLevelErrMsg = paramEx.getMessage();
			if (topLevelErrMsg.matches(".*arg\\[\\d+\\].*")) {
				int beginIndex = topLevelErrMsg.indexOf("arg[") + 4;
				int endIndex = topLevelErrMsg.indexOf("]", beginIndex);

				if (topLevelErrMsg.substring(0, beginIndex).contains("before")) {
					String subStr = topLevelErrMsg.substring(beginIndex, endIndex);
					try {
						int lastPotentialIndex = Integer.parseInt(subStr) - 1;
						String flag = args.get(lastPotentialIndex);

						if (matchFlagFormat(flag)) {
							return compileMsg(flag, stripServiceLoaderMessage(ex.getMessage()));
						}
						// the last index did not have a 'flag' format - continue to look backwards
						int index = lastPotentialIndex - 1;
						while (index >= 0 && !matchFlagFormat(args.get(index))) {
							index--;
						}

						// If we found a good flag - use it
						if (matchFlagFormat(args.get(index))) {
							return compileMsg(args.get(index), stripServiceLoaderMessage(ex.getMessage()));
						}
						// Otherwise fall back to use the message only
						return stripServiceLoaderMessage(ex.getMessage());
					} catch (Exception e) {
						LOGGER.error("Failed generating error message for TypeConversionException, message: {}",
								ex.getMessage());
					}
				}

				// Here we failed getting the flag - use the causing message as the top-level
				// contains .. arg[..] etc
				return stripServiceLoaderMessage(ex.getMessage());

			} else {
				// If the top level message doesn't contain that "arg[..]" stuff we should use
				// the top level message!
				return stripServiceLoaderMessage(paramEx.getMessage());
			}

		}

		private static final boolean matchFlagFormat(String txt) {
			// Regex for one/two "-" characters, followed by one or more lower-case letters
			// and then whatever
			return txt.matches("-{1,2}[a-z]+.*");
		}

		private static String convertInputConvMsg(InputConversionException ex, List<String> args) {
			LOGGER.debug("Compiling error message from InputConversionException");
			// Convert the index (from end) into the index in 'args' list
			String flag = args.get(Math.max(0, args.size() - ex.getOffendingFlagIndex() - 1));

			return compileMsg(flag, stripServiceLoaderMessage(ex.getMessage()));
		}

		private static final String SERVICE_LOADER_PRE_TEXT = "Service not found: ";

		private static String stripServiceLoaderMessage(String msg) {
			if (msg.startsWith(SERVICE_LOADER_PRE_TEXT)) {
				return msg.substring(SERVICE_LOADER_PRE_TEXT.length(), msg.length());
			} else if (msg.contains(SERVICE_LOADER_PRE_TEXT)) {
				String[] prePost = msg.split(SERVICE_LOADER_PRE_TEXT, 2);
				return prePost[0] + prePost[1];
			}
			return msg;
		}

		private static String compileMsg(String flagName, String errMsg) {
			return String.format("Invalid value for option '%s': %s", flagName, errMsg);
		}
	}

	static class ExecutionHandler implements IExecutionExceptionHandler {

		@Override
		public int handleExecutionException(Exception ex,
				CommandLine cmd,
				ParseResult parseResult) throws Exception {

			LOGGER.debug("Failed execution of {}", cmd.getCommandName(), ex);

			return handleThrowable(ex.getMessage(), ex, cmd.getCommandSpec().qualifiedName(), cmd.getErr());
		}

		public int handleThrowable(String errMsg, Throwable t, String cmdName, PrintWriter err) {

			// Print error in bold red
			try {
				String wrapped = StringUtils.wrap(errMsg, CLIConsole.getInstance().getTextWidth());
				err.printf(AnsiRenderer.render(String.format("%n@|bold,red %s|@%n%n", wrapped)));
			} catch (IllegalArgumentException e) {
				// Possibly due to AnsiRenderer exception
				err.printf(AnsiRenderer.render(String.format("%n%sn%n", errMsg)));
			} catch (Exception | Error e) {
				err.printf(AnsiRenderer.render(String.format("%n@|bold,red %s|@%n%n", errMsg)));
			}

			// Suggestions in case there are any
			if (t instanceof ParameterException) {
				// Write suggestions (if any)
				UnmatchedArgumentException.printSuggestions((ParameterException) t, err);
				err.println();
			} 

			// Print how to get usage text (white)
			err.printf(AnsiRenderer
					.render(String.format("Try @|bold,red %s --help|@ for more information.%n%n", cmdName)));

			return new ExitCodeMapper().getExitCode(t);
		}
	}

}

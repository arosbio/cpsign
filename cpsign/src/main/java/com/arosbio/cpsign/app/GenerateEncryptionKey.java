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
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.StringUtils;
import com.arosbio.cpsign.app.params.converters.EmptyFileConverter;
import com.arosbio.cpsign.app.params.mixins.ConsoleVerbosityMixin;
import com.arosbio.cpsign.app.params.mixins.EchoMixin;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.CLIProgramUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.encryption.utils.EncryptionSpecFactory;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/*
 * PB.VALIDATE_PARAMS_PROGRESS
 * PB.LOADING_FILE_OR_MODEL_PROGRESS
 * PB.CROSSVALIDATING_PROGRESS
 * PB.RESULTS_WRITING_PROGRESS
 */
@Command(
		name=GenerateEncryptionKey.CMD_NAME,
		aliases=GenerateEncryptionKey.CMD_ALIAS,
		description = GenerateEncryptionKey.CMD_DESCRIPTION, 
		descriptionHeading = CPSignApp.DESCRIPTION_HEADER, 
		header = GenerateEncryptionKey.CMD_HEADER,
		subcommands = {GenerateEncryptionKey.ListAvailable.class}
		)
public class GenerateEncryptionKey implements RunnableCmd {

	private static final Logger LOGGER = LoggerFactory.getLogger(GenerateEncryptionKey.class);

	public static final String CMD_NAME = "generate-key";
	public final static String CMD_ALIAS = "key";
	public final static String CMD_HEADER = "Generate an encryption key";
	public final static String CMD_DESCRIPTION = "Generates an encryption key, either as plain text or "+
		"written directly to a file (by giving the -f parameter). These can be used for encrypting precomputed data sets or trained "+
		"models. @|bold Note:|@ this command and encryption of models is @|italic only available in case "+
		"an additional encryption-module|@ is given on the class or module-path. Visit the documentation "+
		"website for further details. Security note: keys written to files are preferable as they never exists as plain strings in the memory, "+
		"and are saved as raw bytes. Further note that the content of the file and the string key differs in that the string is a Base64 encoding of the raw bytes. "+
		"Thus it is not possible to directly copy the contents of the file and send to the --key parameter, instead use the --key-file parameter with the path of the file. "+
		"@|italic Further note that when using encryption, only one encryption specification is allowed to be on the class/module path at a time!|@";
	

	/*****************************************
	 * INTERNAL STATE
	 *****************************************/
	
	@Spec private CommandSpec spec;

	private CLIConsole console = CLIConsole.getInstance();

	/*****************************************
	 * OPTIONS
	 *****************************************/
	
	 //
	
	@Command(name = "ls", aliases = {"list"}, description = "list available encryption types", helpCommand = true)
	public static class ListAvailable implements RunnableCmd {

		@Override
		public Integer call() {
			Iterator<EncryptionSpecification> iterator = EncryptionSpecFactory.getAvailable();
			CLIConsole console = CLIConsole.getInstance(CMD_NAME);
			
			// If no available encryption specs
			if (!iterator.hasNext()){
				console.printlnWrapped("%nEncryption is @|bold not available|@, please refer to arosbio.com and the documentation for how to enable encryption.",
				PrintMode.SILENT);
				return 0;
			}
			// Else list them
			else {
				StringBuilder sb = new StringBuilder("%nAvailable encryption types:%n");

				int numAvail = 0;
				while (iterator.hasNext()){
					EncryptionSpecification spec = iterator.next();
					// name @|bold 
					sb.append(" - @|bold Name         ").append(spec.getName()).append("|@%n");
					// Num bytes
					sb.append("   Key lengths  ").append(StringUtils.join(", ",spec.getAllowedKeyLengths())).append("%n");
					// Info - if any
					if (spec.getInfo() != null && ! spec.getInfo().isBlank()){
						String blankIndent = StringUtils.replicate(' ', 18);
						sb.append("   Info         ")
							.append(StringUtils.wrap(spec.getInfo(), console.getTextWidth(), blankIndent,false)).append("%n");
					}
					sb.append("%n");
					numAvail ++;

				}

				sb.append("%n");
				console.println(sb.toString(), PrintMode.NORMAL);

				if (numAvail>1){
					LOGGER.debug("More than one encryption spec implementation is available, need to warn user of not working at run-time");
					console.printlnStdErr("Note that only one Encryption type is allowed on the class/module path at a time when using the CLI, please make sure to only add one in order to use encryption", 
						PrintMode.SILENT);
				}
			}


			return 0;
		}

		@Override
		public String getName() {
			return CMD_NAME + " ls";
		}

		
	}

	/*****************************************
	 * OPTIONS
	 *****************************************/

	@Option(names = {"--type"},
		description = "The implementation type to use (can be omitted if only one is available to CPSign)",
		paramLabel = ParameterUtils.ArgumentType.TEXT_MATCH_ONE_OF)
	private String encryptType;

	// Input
	@Option(names = {"-l","--length"}, 
		description = "The encryption key length in number of bytes, use @|bold "
		+CMD_NAME+" ls|@ to list the available types and their available key lengths",
		paramLabel = ArgumentType.INTEGER,
		required = true)
	private int keyLength = -1;
	
	@Option(names={"-f","--file"},
		description = "File to write the encryption key to (if none is given, the key is written to the terminal)",
		converter = EmptyFileConverter.class,
		paramLabel = ArgumentType.FILE_PATH
		)
	private File keyFile;
	
	@Mixin
	private ConsoleVerbosityMixin consoleArgs;

	@Mixin
	private EchoMixin echo;
	
	/*****************************************
	 * END OF OPTIONS
	 *****************************************/

	@Override
	public String getName() {
		return CMD_NAME;
	}
	
	@Override
	public Integer call() {

		CLIProgramUtils.doFullProgramConfig(this);

		Iterator<EncryptionSpecification> iterator = EncryptionSpecFactory.getAvailable();
			
		// If no available encryption specs
		if (!iterator.hasNext()){
			console.printlnWrappedStdErr("%nEncryption is @|bold not available|@, please refer to @|underline arosbio.com|@ and the documentation for how to enable encryption.",
			PrintMode.SILENT);
			return ExitStatus.USER_ERROR.code;
		}

		List<EncryptionSpecification> specList = new ArrayList<>();
		while (iterator.hasNext()){
			specList.add(iterator.next());
		}
		// If no explicit type given
		if (encryptType == null || encryptType.isEmpty()){
			LOGGER.debug("No explicit encryption type given by the user - will either use the only available or fail if multiple exists");
			if (specList.size()>1){
				console.failWithArgError("Multiple encryption types are available, you need to specify an explicit implementation type. Available ones; " + listToString(specList));
			}
			LOGGER.debug("Generating encryption key using the only available implementation type: {}",specList.get(0).getName());
			generateKey(specList.get(0));
		}
		// Explicit type given
		else {
			// Loop through and check for exact match
			boolean foundMatch = false;
			for (EncryptionSpecification spec : specList){
				if (spec.getName().equalsIgnoreCase(encryptType)){
					LOGGER.debug("Matched encryption type argument to concrete type: " + spec.getName());
					generateKey(spec);
					foundMatch=true;
					break;
				}
			}
			if (! foundMatch){
				LOGGER.debug("Found no encryption type matching input {}",encryptType);
				console.failWithArgError("Invalid input for argument " + CLIProgramUtils.getParamName(this, "encryptType", "ENCRYPTION_TYPE") + " : " + encryptType);
			}

		}
		
		return ExitStatus.SUCCESS.code;

	}

	/**
	 * Generates the key once the concrete type has been found 
	 * @param type The concrete implementation type 
	 */
	private void generateKey(EncryptionSpecification type){
		if (keyFile!=null){
			LOGGER.debug("Aiming to writing the key to file: {}", keyFile);
			byte[] key = type.generateRandomKey(keyLength);
			
			try (FileOutputStream fos = new FileOutputStream(keyFile);){
				IOUtils.write(key, fos);
			} catch (Exception e){
				LOGGER.error("Failed writing key to file", e);
				console.failWithArgError("Failed writing encryption key to file:%n%s",keyFile);
			}
			console.println("Encryption key written to file:%n%s", PrintMode.NORMAL, keyFile);
		} else {
			LOGGER.debug("Generating a string-key and writing to console");
			byte[] key = type.generateRandomKey(keyLength);
			String keyStr = Base64.getEncoder().encodeToString(key);

			console.print("%s", PrintMode.SILENT_ON_MATCH, keyStr);
			console.print("Here is your generated encryption-key, copy the full line below:%n%s%n", 
				PrintMode.NORMAL, keyStr);
		}
	}

	private static String listToString(List<EncryptionSpecification> specList){
		StringBuilder sb = new StringBuilder();
		for (int i=0; i< specList.size()-1;i++){
			sb.append(specList.get(i).getName()).append(", ");
		}
		sb.append(specList.get(specList.size()-1));
		return sb.toString();
	}
	
}

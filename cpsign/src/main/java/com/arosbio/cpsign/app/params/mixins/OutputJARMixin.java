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

import java.io.File;
import java.net.URI;

import com.arosbio.commons.Version;
import com.arosbio.cpsign.app.params.CLIParameters;
import com.arosbio.cpsign.app.params.converters.EmptyFileConverter;
import com.arosbio.cpsign.app.params.converters.VersionConverter;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.io.UriUtils;
import com.arosbio.ml.io.MountData;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.TypeConversionException;

public class OutputJARMixin {

	@Option(names = {"-mo", "--model-out"}, 
			description = "Model file to generate. Must be a non-existing or empty file.",
			converter = EmptyFileConverter.class,
			required=true,
			paramLabel = ArgumentType.FILE_PATH)
	public File modelFile;

	@Option(names = {"-mn", "--model-name"}, 
			description = "The name of the model",
			paramLabel = ArgumentType.TEXT)
	public String modelName;

	@Option(names={"-mc", "--model-category"}, 
			description = "The category of the model, will end up as model-endpoint in the model JAR",
			paramLabel = ArgumentType.TEXT,
			hidden = true)
	public String modelCategory;

	@Option(names = {"-mv","--model-version"}, 
			description = "Optional model version in SemVer versioning format%n"+
					ParameterUtils.DEFAULT_PRE_TEXT +"1.0.0+{date time string}",
					converter = VersionConverter.class,
					paramLabel = ArgumentType.VERSION)
	public Version modelVersion = CLIParameters.DEFAULT_MODEL_VERSION;

	@Option(names = "--mount", 
			description = "List of files to mount in the output model JAR", 
			hidden = true,
			converter = MountDataConverter.class,
			arity = "1..*",
			split = ParameterUtils.SPLIT_WS_COMMA_REGEXP,
			paramLabel = ArgumentType.MOUNT_TYPE
			)
	public MountData[] dataToMount;

	@Option(names = "--keep-mounts", 
			description = "Keep old mounted data in any input model file", 
			hidden=true)
	public boolean keepMounts;

	public static class MountDataConverter implements ITypeConverter<MountData>{

		@Override
		public MountData convert(String parameter) throws TypeConversionException {
			if (parameter == null || parameter.trim().isEmpty())
				throw new TypeConversionException("Cannot mount empty stuff");

			String[] parts = parameter.split(":");
			if (parts.length != 2)
				throw new TypeConversionException("Mount data not formatted correctly: " + parameter);
			if (parts[0].trim().isEmpty())
				throw new TypeConversionException("Mount location is empty for input: " + parameter);

			URI toMount=null;
			try {
				toMount = UriUtils.getURI(parts[1].trim());
			} catch (Exception e) {
				throw new TypeConversionException("URI to mount not valid: " + parts[1]);
			}
			try {
				return new MountData(parts[0].trim(), toMount);
			} catch (IllegalArgumentException e) {
				throw new TypeConversionException(e.getMessage());
			}
		}

	}
}

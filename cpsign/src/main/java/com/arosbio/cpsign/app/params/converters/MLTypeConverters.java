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

import com.arosbio.commons.GlobalConfig.Defaults.PredictorType;
import com.arosbio.cpsign.app.params.CLIParameters.ClassOrRegType;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

public class MLTypeConverters {
	
	public static class MLTypeConverter implements ITypeConverter<PredictorType> {
		@Override
		public PredictorType convert(String value) {
			try{
				return PredictorType.getPredictorType(value);
			} catch (IllegalArgumentException e){
				throw new TypeConversionException("type {"+value+"} not supported");
			}
		}	
	}
	
	public static class ClassRegConverter implements ITypeConverter<ClassOrRegType> {
		@Override
		public ClassOrRegType convert(String value) {
			try{
				return ClassOrRegType.getType(value);
			} catch (IllegalArgumentException e){
				throw new TypeConversionException("type {"+value+"} not supported, only classification or regression supported");
			}
		}
	}

}

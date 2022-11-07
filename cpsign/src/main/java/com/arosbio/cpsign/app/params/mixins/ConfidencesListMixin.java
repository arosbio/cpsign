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
import java.util.List;

import com.arosbio.cpsign.app.params.converters.ListOrRangeConverter;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;

import picocli.CommandLine.Option;
import picocli.CommandLine.TypeConversionException;

public class ConfidencesListMixin {
	
	public List<Double> confidences;
	
	@Option(
			names = { "-co", "--confidences" }, 
			description = "Confidences for predictions, either an explicit list of numbers (e.g. '0.5,0.7,0.9' or '0.5 0.7 0.9'), "
							+ "or using a start:stop:step syntax (e.g. 0.5:1:0.05 generates all numbers between 0.5 to 1.0 with 0.05 increments, see "
					+ParameterUtils.RUN_EXPLAIN_ANSI_ON +"explain list-syntax" + ParameterUtils.ANSI_OFF +"). All numbers must be in the range [0..1]",
			split = ParameterUtils.SPLIT_WS_COMMA_REGEXP,
			paramLabel = ArgumentType.NUMBER,
			arity = ParameterUtils.LIST_TYPE_ARITY
			)
	public void setConfidences(List<String> input){
		if (confidences == null)
			confidences = new ArrayList<>();
		List<Double> confs = new ArrayList<>();
		for (String t : input)
			confs.addAll(new ListOrRangeConverter().convert(t));
		
		if (confs != null && ! confs.isEmpty()){
			for (Double conf: confs)
				if (conf< 0 || conf>1)
					throw new TypeConversionException("Confidence values must be in the range [0..1], got offending value: " + conf);
		}
		confidences.addAll( confs );
	}

}

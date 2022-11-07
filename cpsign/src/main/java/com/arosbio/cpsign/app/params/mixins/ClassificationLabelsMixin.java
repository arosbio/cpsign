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

import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;

import picocli.CommandLine.Option;

public class ClassificationLabelsMixin {
	
	public List<String> labels;
	
	@Option(names = { "-l", "--labels" }, 
			description = "Labels used in the input file in classification mode. More info can be found running "+ParameterUtils.RUN_EXPLAIN_ANSI_ON+"explain labels" + ParameterUtils.ANSI_OFF,
			split = ParameterUtils.SPLIT_WS_COMMA_REGEXP,
			arity = ParameterUtils.LIST_TYPE_ARITY,
			paramLabel = ArgumentType.TEXT
			)
	public void setLabels(List<String> input) {
		if (labels==null)
			labels = new ArrayList<>();
		for (String in : input) {
			if (in != null && !in.trim().isEmpty()) {
				labels.add(in.trim());
			}
		}
	}
	
}

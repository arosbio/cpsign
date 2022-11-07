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

import com.arosbio.cpsign.app.params.CLIParameters;
import com.arosbio.cpsign.app.params.converters.ListOrRangeConverter;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;

import picocli.CommandLine.Option;
import picocli.CommandLine.TypeConversionException;

public class ValidationPointsMixin {
	
	public List<Double> calibrationPoints;

	@Option(names = { "-cp", "--calibration-points" }, 
			description = {"Calibration points used in model evaluation, i.e. confidence levels in Conformal Prediction and "+
				"expected probabilities for Venn Prediction (each value must be in the range [0..1]).",
				"(Venn-ABERS only) The calibration of the model is preferably made for several calibration points (mapping predicted probability of the 'true class' to observed frequency of the 'true class', made by bins - see " + 
				 ParameterUtils.PARAM_FLAG_ANSI_ON + CALIB_POINTS_WIDTH_NAME + ParameterUtils.ANSI_OFF +
				 "). The default value for Venn-ABERS is thus several values instead of a single value as for CP models.",
					ParameterUtils.DEFAULT_PRE_TEXT + CLIParameters.DEFAULT_CONFIDENCE + " (CP) or 0.05:0.95:0.1 (Venn-ABERS)"},
			arity = ParameterUtils.LIST_TYPE_ARITY,
			split = ParameterUtils.SPLIT_WS_COMMA_REGEXP,
			paramLabel = ArgumentType.NUMBER)
	public void setCalibrationPoints(List<String> input) {
		if (calibrationPoints == null)
			calibrationPoints = new ArrayList<>();
		
		for (String i : input) {
			List<Double> d = new ListOrRangeConverter().convert(i);
			calibrationPoints.addAll(d);
		}
		
		
		for (double conf: calibrationPoints) {
			if (conf<0 || conf>1)
				throw new TypeConversionException("Points must be in the range [0..1]");
		}
	}

	private static final String CALIB_POINTS_WIDTH_NAME = "--calibration-points-width";
	@Option(names = {CALIB_POINTS_WIDTH_NAME},
			description="(VAP only) the width around each calibration point that should be considered for each calibration point, default is to use 1/[number of calibration points]. "
			+ "Note that the parameter is taken as the total width, the intervals will be [midpoint-0.5*width, midpoint+0.5*width]",
			paramLabel = ArgumentType.NUMBER)
	public Double calibrationPointWidth;


	@Option(names = { "-pw", "--prediction-widths" }, 
			description = "(Regression only) The prediction interval widths used for metrics scoring prediction width -> confidence for regression.",
			arity = ParameterUtils.LIST_TYPE_ARITY,
			split = ParameterUtils.SPLIT_WS_COMMA_REGEXP,
			paramLabel = ArgumentType.NUMBER
			)
	public List<Double> predictionWidths;


}
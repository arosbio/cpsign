/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp.acp;

import java.util.List;

import com.arosbio.commons.MathUtils;
import com.arosbio.ml.cp.ConformalPredictor;
import com.arosbio.ml.interfaces.AggregatedPredictor;

/**
 * Aggregated Conformal Predictor (ACP), also called Split Conformal Predictors (SCP)
 * 
 * @author staffan
 *
 */
public interface ACP extends ConformalPredictor, AggregatedPredictor {

    public static enum AggregationType {
		MEDIAN, MEAN;
	}

    public void setAggregation(AggregationType type);

    public AggregationType getAggregation();
	

	public static double aggregate(AggregationType type, List<Double> values){
		switch(type){
			case MEDIAN:
				return MathUtils.median(values);
			case MEAN:
				return MathUtils.mean(values);
			default:
				throw new IllegalStateException("Invalid aggregation type");
		}
	}

	public static double aggregate(AggregationType type, double... values){
		switch(type){
			case MEDIAN:
				return MathUtils.median(values);
			case MEAN:
				return MathUtils.mean(values);
			default:
				throw new IllegalStateException("Invalid aggregation type");
		}
	}
    
}

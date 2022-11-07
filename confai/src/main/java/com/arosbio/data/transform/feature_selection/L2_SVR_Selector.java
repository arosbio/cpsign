/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.transform.feature_selection;

import de.bwaldvogel.liblinear.Parameter;
import de.bwaldvogel.liblinear.SolverType;

public class L2_SVR_Selector extends LinearModelBasedSelection {
	
	public static final String DESCRIPTION = "Use the feature weights produced by EpsilonSVR with L2 regularization. Default is to remove all features "+ 
			"with weights smaller than the mean weight (considering magnitudes). The selection criterion can be changed to e.g. keeping the 'N' most important features. "
			+ "Note that the scaling of features prior to this step is critical to get reliable output.";
	public static final String NAME = "L2SVRSelector";
	private static final long serialVersionUID = -5825202923163243113L;


	public L2_SVR_Selector() {
		super(SolverType.L2R_L2LOSS_SVR);
	}

	public L2_SVR_Selector(double cost, double epsilon) {
		super(new Parameter(SolverType.L2R_L2LOSS_SVR, cost, epsilon));
	}
	
	@Override
	public String getName() {
		return NAME;
	}
	
	public String toString() {
		return NAME;
	}
	
	@Override
	public boolean applicableToClassificationData() {
		return false;
	}
	@Override
	public boolean applicableToRegressionData() {
		return true;
	}
	
	@Override
	public String getDescription() {
		return DESCRIPTION;
	}


	@Override
	public L2_SVR_Selector clone() {
		L2_SVR_Selector clone;
		if (getParameters() != null)
			clone = new L2_SVR_Selector(getParameters().getC(), getParameters().getEps());
		else
			clone = new L2_SVR_Selector();
		super.copyStateToClone(clone);
		return clone;
	}

}

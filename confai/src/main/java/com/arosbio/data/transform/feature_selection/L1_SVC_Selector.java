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

public class L1_SVC_Selector extends LinearModelBasedSelection {

	public static final String NAME = "L1SVCSelector";
	private static final long serialVersionUID = -5825202923163243113L;

	public L1_SVC_Selector() {
		super(SolverType.L1R_L2LOSS_SVC);
	}

	public L1_SVC_Selector(double cost, double epsilon) {
		super(new Parameter(SolverType.L1R_L2LOSS_SVC, cost, epsilon));
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
		return true;
	}

	@Override
	public boolean applicableToRegressionData() {
		return false;
	}

	@Override
	public String getDescription() {
		return "Use the feature weights produced by a linear SVC with L1 regularization. Default is to remove all features "
				+
				"with weights equal to 0. The selection criterion can be changed to e.g. keeping the 'N' most important features. "
				+ "Note that the scaling of features prior to this step is critical to get reliable output.";
	}

	@Override
	public L1_SVC_Selector clone() {
		L1_SVC_Selector clone;
		if (getParameters() != null)
			clone = new L1_SVC_Selector(getParameters().getC(), getParameters().getEps());
		else
			clone = new L1_SVC_Selector();
		super.copyStateToClone(clone);
		return clone;
	}

}

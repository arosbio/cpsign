/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.vap;

import java.util.LinkedList;
import java.util.List;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.github.sanity.pav.PairAdjacentViolators;
import com.github.sanity.pav.Point;

import kotlin.jvm.functions.Function1;
import com.arosbio.tests.suites.NonSuiteTest;

@Category(NonSuiteTest.class)
public class TestIsotonicRegressionLib {
	
	@Test
	public void testIsoReg() throws Exception {
		List<Point> points = new LinkedList<>();
		points.add(new Point(2.0, 3.0));
		points.add(new Point(0.0, 0.0));
        points.add(new Point(3.0, 5.0));
        points.add(new Point(1.0, 1.0));
        PairAdjacentViolators pav = new PairAdjacentViolators(points);
        final Function1<Double, Double> interpolator = pav.interpolator();
        for (double x=0; x<=3; x+=0.1) {
            System.out.println(x+"\t"+interpolator.invoke(x));
        }
	}

}

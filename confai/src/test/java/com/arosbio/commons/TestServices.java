/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.commons;

import java.util.Iterator;

import org.junit.Assert;
import org.junit.Test;

import com.arosbio.data.transform.Transformer;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.encryption.utils.EncryptionSpecFactory;
import com.arosbio.ml.algorithms.MLAlgorithm;
import com.arosbio.ml.cp.nonconf.NCM;
import com.arosbio.ml.cp.nonconf.calc.PValueCalculator;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.sampling.SamplingStrategy;
import com.arosbio.ml.testing.TestingStrategy;

public class TestServices {

	@Test
    public void testIfAvailableOnPath(){
        Iterator<EncryptionSpecification> specs = EncryptionSpecFactory.getAvailable();
        int count = 0;
        while(specs.hasNext()){
            // System.err.println(specs.next());
			specs.next();
            count++;
        }
        Assert.assertEquals(1, count);
    }
	
	@Test
	public void testToStringMethods() {
		
		
		doList(FuzzyServiceLoader.iterator(Transformer.class));
		
		doList(FuzzyServiceLoader.iterator(MLAlgorithm.class));
		doList(FuzzyServiceLoader.iterator(PValueCalculator.class));
		doList(FuzzyServiceLoader.iterator(NCM.class));
		doList(FuzzyServiceLoader.iterator(SamplingStrategy.class));
		doList(FuzzyServiceLoader.iterator(Metric.class));
		doList(FuzzyServiceLoader.iterator(TestingStrategy.class));
		
	}
	
	private void doList(Iterator<? extends Object> iter) {
		
		while (iter.hasNext()) {
			Object o = iter.next();
			System.err.println(o.toString());
		}
	}

}

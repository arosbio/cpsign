/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.testutils;

import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.cheminf.ChemPredictor;
import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.descriptors.ChemDescriptor;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.ml.TrainingsetValidator;
import com.google.common.collect.Lists;

import ch.qos.logback.core.joran.spi.JoranException;


/**
 * The base class for unit tests, so that common code can be executed using {@code @BeforeClass} annotation. E.g. running {@code TrainingsetValidator.setTestingEnv();}
 * @author staffan
 *
 */
public class UnitTestBase extends TestEnv {


	public UnitTestBase() {}


	@BeforeClass
	public static void initSecurity() throws JoranException{
		TrainingsetValidator.setTestingEnv();
	}

	public static final boolean INCHI_AVAILABLE_ON_SYSTEM = CPSignMolProperties.isInChIAvailable();

	public static final String TEST_SMILES = "COc(c1)cccc1C#N";
	public static final String TEST_SMILES_2 = "OCc1ccc(cc1)Cl";
	
	public static final SmilesParser sp = new SmilesParser(SilentChemObjectBuilder.getInstance());

	public static IAtomContainer getTestMol(){
		try{
			return sp.parseSmiles(TEST_SMILES);
		} catch(Exception e){
			return null;
		}
	}

	public static List<String> getSignatures(ChemPredictor predictor){
		return getSignatures(predictor.getDataset());
	}

	public static List<String> getSignatures(ChemDataset problem){
		for (ChemDescriptor d : problem.getDescriptors()) {
			if (d instanceof SignaturesDescriptor)
				return Lists.newArrayList(((SignaturesDescriptor)d).getSignatures());
		}
		Assert.fail("No signatures!");
		return null;
	}


}

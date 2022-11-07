/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cheminf;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.chem.io.in.SDFile;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.data.NamedLabels;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.JarDataSource;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.cp.tcp.TCPClassifier;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.GzipEncryption;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.UnitTestBase;

@Category(UnitTest.class)
public class TestTCPClassification extends UnitTestBase {

	@Test
	public void testTCPSign() throws Exception {
		CmpdData ames = TestResources.Cls.getAMES_126();
		EncryptionSpecification spec = new GzipEncryption("some password and salt");
		
		ChemCPClassifier signTCP = new ChemCPClassifier(
				new TCPClassifier(
						new NegativeDistanceToHyperplaneNCM(
								new LinearSVC())));
		signTCP.addRecords(new SDFile(ames.uri()).getIterator(), ames.property(), new NamedLabels(ames.labelsStr()));
		signTCP.train();
		Map<String, Double> pVals = signTCP.predict(getTestMol());
		Assert.assertEquals(2,pVals.size());
		Set<String> labels = pVals.keySet();
		Assert.assertTrue(labels.contains(ames.labelsStr().get(0)));
		Assert.assertTrue(labels.contains(ames.labelsStr().get(1)));
		SignificantSignature signSign = signTCP.predictSignificantSignature(getTestMol(), ames.labelsStr().get(0));
		SignaturesDescriptor d = (SignaturesDescriptor)signTCP.getDataset().getDescriptors().get(0);
		int startHeight = d.getStartHeight();
		int endHeight = d.getEndHeight();
		assertSignSignNotEmpty(signSign, startHeight, endHeight);

		// Save 
		File tmpNormal = TestUtils.createTempFile("tcp", ".plain");
		File tmpEnc = TestUtils.createTempFile("tcp", ".enc");
		ModelSerializer.saveModel(signTCP, tmpNormal, null);
		ModelSerializer.saveModel(signTCP, tmpEnc, spec);


		// Load - plain
		ChemCPClassifier tcpLoadedPlain = (ChemCPClassifier) ModelSerializer.loadChemPredictor(new JarDataSource(new JarFile(tmpNormal)), null);
		Assert.assertEquals(pVals, tcpLoadedPlain.predict(getTestMol()));
		assertSignSignNotEmpty(tcpLoadedPlain.predictSignificantSignature(getTestMol(), ames.labelsStr().get(0)), startHeight, endHeight);

		// Load - enc
		try{
			ModelSerializer.loadChemPredictor(new JarDataSource(new JarFile(tmpEnc)), null);
			Assert.fail();
		} catch(InvalidKeyException | IOException e){

		}
		ChemCPClassifier tcpLoadedEnc = (ChemCPClassifier) ModelSerializer.loadChemPredictor(new JarDataSource(new JarFile(tmpEnc)), spec);
		Assert.assertEquals(pVals, tcpLoadedEnc.predict(getTestMol()));
		assertSignSignNotEmpty(tcpLoadedEnc.predictSignificantSignature(getTestMol(), ames.labelsStr().get(0)), startHeight, endHeight);
	}

	private void assertSignSignNotEmpty(SignificantSignature signSign, int startHeight, int endHeight){
		Assert.assertTrue(signSign.getAtoms().size()>1);
		Assert.assertTrue(signSign.getHeight()>=startHeight && signSign.getHeight() <= endHeight);
		Assert.assertTrue(signSign.getAtomContributions().size()>= 1);
	}
	
}

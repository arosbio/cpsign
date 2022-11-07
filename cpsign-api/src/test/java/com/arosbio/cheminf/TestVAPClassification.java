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
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.chem.io.in.SDFile;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.commons.Version;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.data.NamedLabels;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.io.ModelInfo;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.vap.avap.AVAPClassifier;
import com.arosbio.ml.vap.ivap.IVAPClassifier;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.UnitTestBase;

@Category(UnitTest.class)
public class TestVAPClassification extends UnitTestBase{

	private int nrModels = 5;
	private double calibrationPart = 0.2;

	@Test
	public void testCompleteThingy() throws Exception {
		EncryptionSpecification spec = null; 
		CmpdData ames_mini = TestResources.Cls.getAMES_126();		
		AVAPClassifier cvap = new AVAPClassifier(new LinearSVC(), 
				new RandomSampling(nrModels, calibrationPart));
		ChemVAPClassifier sigProb = new ChemVAPClassifier(cvap);
		sigProb.addRecords(new SDFile(ames_mini.uri()).getIterator(),ames_mini.property(), new NamedLabels(ames_mini.labelsStr()));
//		sigProb.fromChemFile(new File(MINI_FILE_PATH).toURI(), PROPERTY, AMES_LABELS);

		Assert.assertTrue(! sigProb.getDataset().isEmpty());
		Assert.assertFalse(sigProb.getPredictor().isTrained());
		sigProb.train();
		System.out.println("nr models="+cvap.getNumTrainedPredictors());
		Map<Integer, IVAPClassifier> models = cvap.getModels();
		for(Map.Entry<Integer, IVAPClassifier> model: models.entrySet())
			System.out.println("Model {"+model.getKey()+"}="+model.getValue().isTrained());
		Assert.assertTrue(sigProb.getPredictor().isTrained());
		
		Map<String,Double> probPre = sigProb.predictProbabilities(getTestMol());

		File modelOut = TestUtils.createTempFile("cvap.sign", ".jar"); 
		ModelInfo info = new ModelInfo("sign cvap", new Version(1, 0, 0), null);
		sigProb.withModelInfo(info);
		ModelSerializer.saveModel(sigProb, modelOut, spec);
		
		LoggerUtils.setDebugMode();
		ChemVAPClassifier loadedCVAP = (ChemVAPClassifier) ModelSerializer.loadChemPredictor(modelOut.toURI(), spec);
		
		Assert.assertEquals(sigProb.getDataset().getDescriptors(), loadedCVAP.getDataset().getDescriptors());
		System.out.println(((AVAPClassifier) loadedCVAP.getPredictor()).getStrategy().getProperties());
		Assert.assertTrue(loadedCVAP.getPredictor().isTrained());
		AVAPClassifier model = (AVAPClassifier) loadedCVAP.getPredictor();
		Assert.assertEquals(nrModels, model.getNumTrainedPredictors());
		
		Map<String,Double> probPost = loadedCVAP.predictProbabilities(getTestMol());
		for (String label: probPost.keySet())
			Assert.assertEquals(probPre.get(label), probPost.get(label),0.000001);
	}

}

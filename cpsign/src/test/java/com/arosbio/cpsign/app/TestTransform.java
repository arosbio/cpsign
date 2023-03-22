package com.arosbio.cpsign.app;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.data.DataUtils;
import com.arosbio.tests.TestResources.Cls;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.utils.TestUtils;

@Category(CLITest.class)
public class TestTransform extends CLIBaseTest {
    
    @Test
    public void testRobustScaler() throws Exception {
        
        CmpdData ames = Cls.getAMES_10();

        // Do it in 2 steps
        // Precompute - without transform
        File precompNoTransform = TestUtils.createTempFile("precomp.v1", ".jar");
        mockMain("precomp", 
            "--train-data", ames.format(), ames.url().toString(),
            "--property", ames.property(),
            "--labels", getLabelsArg(ames.labelsStr()), 
            "--model-out", precompNoTransform.getAbsolutePath(), 
            "--descriptors", "signatures", "ALOGPDescriptor");

        File transformOut = TestUtils.createTempFile("transformed.v1", ".jar");
        mockMain(Transform.CMD_NAME, 
            "--data-set", precompNoTransform.getAbsolutePath(),
            "--model-out", transformOut.getAbsolutePath(), 
            "--transform", "robust-scaler:colMaxIndex=2", "ZeroMaxScaler:colMinIndex=3:colMaxIndex=10");
        
        // All in one go
        File precompWithTransform = TestUtils.createTempFile("precomp.with", ".jar");
        mockMain("precomp", 
            "--train-data", ames.format(), ames.url().toString(),
            "--property", ames.property(),
            "--labels", getLabelsArg(ames.labelsStr()), 
            "--model-out", precompWithTransform.getAbsolutePath(), 
            "--descriptors", "signatures", "ALOGPDescriptor",
            "--transform", "robust-scaler:colMaxIndex=2","ZeroMaxScaler:colMinIndex=3:colMaxIndex=10");
        
        ChemDataset separate = ModelSerializer.loadDataset(transformOut.toURI(), null);
        ChemDataset allInOne = ModelSerializer.loadDataset(precompWithTransform.toURI(), null);

        Assert.assertEquals(separate.size(), allInOne.size());
        Assert.assertEquals(2, allInOne.getTransformers().size());
        Assert.assertEquals(2, separate.getTransformers().size());
        for (int i = 0; i < 2; i++){
            Assert.assertEquals(separate.getTransformers().get(i).getClass(), allInOne.getTransformers().get(i).getClass());
        }

        Assert.assertTrue(DataUtils.equals(separate, allInOne));

    }

    
}

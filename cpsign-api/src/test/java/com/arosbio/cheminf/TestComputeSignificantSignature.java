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

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.chem.io.in.CSVFile;
import com.arosbio.chem.io.in.SDFReader;
import com.arosbio.chem.io.in.SDFile;
import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.descriptors.ChemDescriptor;
import com.arosbio.cheminf.descriptors.DescriptorFactory;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.MathUtils;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.NamedLabels;
import com.arosbio.data.SparseFeature;
import com.arosbio.data.SparseVector;
import com.arosbio.data.transform.ColumnSpec;
import com.arosbio.data.transform.feature_selection.DropColumnSelector;
import com.arosbio.data.transform.feature_selection.L2_SVC_Selector;
import com.arosbio.data.transform.feature_selection.VarianceBasedSelector;
import com.arosbio.data.transform.scale.Standardizer;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.Cls;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.TestResources.Reg;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.MockFailingDescriptor;
import com.arosbio.testutils.MockNoninformativeDescriptor;
import com.arosbio.testutils.UnitTestBase;
import com.google.common.collect.Range;

@Category(UnitTest.class)
public class TestComputeSignificantSignature {
    
    static final CmpdData ames = Cls.getAMES_126();

    @BeforeClass
    public static void setSeed(){
        GlobalConfig.getInstance().setRNGSeed(56789);
    }

    // [C](p[C](p[C])p[C](p[C])) , height 2
    // [C](p[C](p[C])p[C](p[C])) , height 2
    // after change:
    // sign-sign: Significant signature "[C](p[C](p[C])p[C](p[C])[O]([C]))" of height 2
    // sign-sign extra grad: {}
    @Test
    public void testShouldGiveSameOutputWithTransformers() throws IOException, IllegalStateException, NullPointerException, CDKException{
        // System.err.println("testShouldGiveSameOutputWithTransformers");
        // Given two exactly the same configurations
        ChemCPClassifier acp1 = new ChemCPClassifier(new ACPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()), new RandomSampling()));
        ChemCPClassifier acp2 = new ChemCPClassifier(new ACPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()), new RandomSampling()));
        // But change the descriptor of the second one
        acp2.getDataset().setDescriptors(new MockFailingDescriptor(), new MockFailingDescriptor(), acp1.getDataset().getDescriptors().get(0).clone());

        // Load same training set
        try(SDFReader r1 = new SDFile(ames.uri()).getIterator();
            SDFReader r2 = new SDFile(ames.uri()).getIterator();
            ){
            acp1.addRecords(r1, ames.property(), new NamedLabels(ames.labelsStr()));
            acp2.addRecords(r2, ames.property(), new NamedLabels(ames.labelsStr()));
        }

        // equals apart from the sometimes failing descriptor
        Assert.assertEquals(acp1.getDataset().getNumAttributes() + 2, acp2.getDataset().getNumAttributes());

        // We apply two transformations to the one with MockFailingDescriptor - in order to remote the two first feats
        acp2.getDataset().apply(new DropColumnSelector(0), new DropColumnSelector(0));

        // Now they should have the same number of features!
        Assert.assertEquals(acp1.getDataset().getNumAttributes(), acp2.getDataset().getNumAttributes());

        // Train them both
        acp1.train();
        acp2.train();

        // Predict the same compound
        SignificantSignature ss1 = acp1.predictSignificantSignature(UnitTestBase.getTestMol());
        SignificantSignature ss2 = acp2.predictSignificantSignature(UnitTestBase.getTestMol());
        // System.err.println("sign-sign: " + ss1);
        // System.err.println("sign-sign extra grad: " + ss1.getAdditionalFeaturesGradient());

        Assert.assertEquals(ss1.getSignature(), ss2.getSignature());
        Assert.assertEquals(ss1.getFullGradient(), ss2.getFullGradient());
        Assert.assertEquals(ss1.getAtoms(), ss2.getAtoms());

    }

    //sign-sign: Significant signature "[C](p[C](p[C]([C]p[C,0]))p[C](p[C](p[C,0][O])))" of height 3
// sign-sign extra grad: {non-informative{1:0,00}=0.0, non-informative{0:1,00}=0.0, non-informative{2:0,00}=0.0, non-informative{0:0,00}=0.0}

// sign-sign: Significant signature "[C](p[C](p[C])p[C](p[C]))" of height 2
// sign-sign extra grad: {non-informative{1:0,00}=0.0, non-informative{0:1,00}=0.0, non-informative{2:0,00}=0.0, non-informative{0:0,00}=0.0}
    @Test
    public void testShouldGiveSameOutputWithNonInformativeFeats() throws IOException, IllegalStateException, NullPointerException, CDKException{
        // System.err.println("testShouldGiveSameOutputWithNonInformativeFeats");
        // Given two exactly the same configurations
        ChemCPClassifier acp1 = new ChemCPClassifier(new ACPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()), new RandomSampling()));
        ChemCPClassifier acp2 = new ChemCPClassifier(new ACPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()), new RandomSampling()));
        // But change the descriptor of the second one
        acp2.getDataset().setDescriptors(new MockNoninformativeDescriptor(), new MockNoninformativeDescriptor(0,3), acp1.getDataset().getDescriptors().get(0).clone());

        // Load same training set
        try(SDFReader r1 = new SDFile(ames.uri()).getIterator();
            SDFReader r2 = new SDFile(ames.uri()).getIterator();
            ){
            acp1.addRecords(r1, ames.property(), new NamedLabels(ames.labelsStr()));
            acp2.addRecords(r2, ames.property(), new NamedLabels(ames.labelsStr()));
        }

        // equals apart from the sometimes failing descriptor
        Assert.assertEquals(acp1.getDataset().getNumAttributes() + 4, acp2.getDataset().getNumAttributes());

        // Train them both
        acp1.train();
        acp2.train();

        // Predict the same compound
        SignificantSignature ss1 = acp1.predictSignificantSignature(UnitTestBase.getTestMol());
        SignificantSignature ss2 = acp2.predictSignificantSignature(UnitTestBase.getTestMol());

        Assert.assertEquals(ss1.getSignature(), ss2.getSignature());
        Assert.assertEquals(ss1.getAtoms(), ss2.getAtoms());

        // Check the full gradients - need to counter for the first 4 features that should have 0 in value (non-informative)
        List<SparseFeature> ss1_full = ss1.getFullGradient(), ss2_full = ss2.getFullGradient();
        // First 4 features should be == 0
        Assert.assertEquals(0, ss2_full.get(0).getValue(), 0.00001);
        Assert.assertEquals(0, ss2_full.get(1).getValue(), 0.00001);
        Assert.assertEquals(0, ss2_full.get(2).getValue(), 0.00001);
        Assert.assertEquals(0, ss2_full.get(3).getValue(), 0.00001);

        // The remaining ones should be identical!
        for (int i=0; i<ss1_full.size(); i++){
            Assert.assertEquals(ss1_full.get(i).getValue(), ss2_full.get(i+4).getValue(), 0.000001);
        }

        // Check the additional features gradient (they should all have ==0 feats)
        Assert.assertEquals(4, ss2.getAdditionalFeaturesGradient().size());
        for (double v : ss2.getAdditionalFeaturesGradient().values()){
            Assert.assertEquals(0, v, .00001);
        }
        // System.err.println("sign-sign: " + ss2);
        // System.err.println("sign-sign extra grad: " + ss2.getAdditionalFeaturesGradient());
        // System.err.println("full grad: " + ss2.getFullGradient());
        // System.err.println("mol-grad: " + ss2.getAtomContributions());
        // System.out.println(ss2.getAdditionalFeaturesGradient());
    }

    // sign-sign: Significant signature "[C](p[C]([C]p[C])p[C](p[C][O]))" of height 2
    // sign-sign extra grad: {Zagreb=0.0, tpsaEfficiency=0.0, XLogP=0.0, MW=0.0, VAdjMat=0.0, WTPT-2=0.0}
    @Test
    public void testCombineCDKAndSignatures() throws IOException, IllegalStateException, NullPointerException, CDKException{
        // System.err.println("testCombineCDKAndSignatures");
        List<ChemDescriptor> descsToUse = DescriptorFactory.getCDKDescriptorsNo3D().subList(2, 10);
        int count = 0;
        for (ChemDescriptor d : descsToUse){
            d.initialize();
            count += d.getLength();
        }
        // System.err.println("num descriptor features without signatures: " + count);
        descsToUse.add(new SignaturesDescriptor());

        // Init the predictor 
        ChemCPClassifier acp = new ChemCPClassifier(new ACPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()), new RandomSampling()));
        ChemDataset dataset = acp.getDataset();
        dataset.setDescriptors(descsToUse);

        // Load some data
        try(SDFReader r1 = new SDFile(ames.uri()).getIterator();
            ){
            acp.addRecords(r1, ames.property(), new NamedLabels(ames.labelsStr()));
        }
        // System.err.println("Num original features: " + dataset.getNumAttributes());

        // Now we apply a transformation on the features and see if that works as well
		// Scale the CDK features
        Standardizer std = new Standardizer(new ColumnSpec(Range.atMost(count)));
        dataset.apply(std);

        // First CDK features only
        int nToKeep = (int)(.5*count);
        VarianceBasedSelector cdkSelector = new VarianceBasedSelector(nToKeep);
        cdkSelector.setColumns(new ColumnSpec(Range.atMost(count)));
        dataset.apply(cdkSelector);
        Assert.assertTrue(cdkSelector.getFeatureIndicesToRemove().size()>(count - nToKeep));
        
        // Then applied on the rest
        L2_SVC_Selector l2Selector = new L2_SVC_Selector();
        dataset.apply(l2Selector);
        Assert.assertTrue(l2Selector.getFeatureIndicesToRemove().size()>100);

        Assert.assertTrue("Make sure we have some features left",dataset.getNumAttributes()>100); 

        // Check num CDK features after transformations 
        List<String> remainingCDKFeats = dataset.getFeatureNames(false);

        // System.err.println("Num features after applied selectors: " + dataset.getNumAttributes() + ", of which are non-signatures: " + dataset.getFeatureNames(false));
        
        Assert.assertTrue(remainingCDKFeats.size()>2);
        
        acp.train();

        SignificantSignature ss =  acp.predictSignificantSignature(UnitTestBase.getTestMol());

        FeatureVector molVector = dataset.convertToFeatureVector(UnitTestBase.getTestMol());
        Assert.assertEquals(molVector.getNumExplicitFeatures(), ss.getFullGradient().size());

        List<SparseFeature> grad = ss.getFullGradient();
        List<SparseFeature> molFeatures = null;
        if (molVector instanceof SparseVector){
            molFeatures  = ((SparseVector) molVector).getInternalList();
        } else {
            molFeatures = new SparseVector(molVector).getInternalList();
        }
         
        for (int i = 0 ; i < molVector.getNumExplicitFeatures(); i++ ){
            SparseFeature gradFeat = grad.get(i);
            SparseFeature molFeat = molFeatures.get(i);
            Assert.assertEquals(molFeat.getIndex(), gradFeat.getIndex());
        }

        Assert.assertEquals(new HashSet<>(remainingCDKFeats), ss.getAdditionalFeaturesGradient().keySet());


        // Make sure the results are identical with the ones given from the underlying predictor
        List<SparseFeature> rawGrad = acp.getPredictor().calculateGradient(molVector);
        Assert.assertEquals(rawGrad, grad);

        // System.err.println("sign-sign: " + ss);
        // System.err.println("// sign-sign extra grad: " + ss.getAdditionalFeaturesGradient());

        // predict several ones, need to check that the maximum feature index is not larger than the max of the reduced feature space
        int numAttr = dataset.getNumAttributes();
        Set<String> additionalFeats = new HashSet<>(dataset.getFeatureNames(false));
        Set<String> remainingSignatures = new HashSet<>(dataset.getFeatureNames(true).subList(additionalFeats.size(), numAttr));
        int numCDKAttr = additionalFeats.size();
        CSVCmpdData solu100 = Reg.getSolubility_100();
        Iterator<IAtomContainer> molIter = new CSVFile(solu100.uri()).setDelimiter(solu100.delim()).getIterator();
        while(molIter.hasNext()){
            IAtomContainer mol = molIter.next();
            SignificantSignature res = acp.predictSignificantSignature(mol);
            List<SparseFeature> fullGrad = res.getFullGradient();
            Assert.assertTrue(fullGrad.get(fullGrad.size()-1).getIndex() <= numAttr);
            Assert.assertEquals(numCDKAttr, res.getAdditionalFeaturesGradient().size());
            Assert.assertEquals(additionalFeats, res.getAdditionalFeaturesGradient().keySet());
            Assert.assertTrue(remainingSignatures.contains(res.getSignature()));
            Assert.assertEquals(mol.getAtomCount(), (int) MathUtils.max( res.getAtomContributions().keySet()) +1 ); // +1 for atom numbers using 0 start index
        }

    }

}

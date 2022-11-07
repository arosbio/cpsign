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

import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.cheminf.descriptors.ChemDescriptor;
import com.arosbio.cheminf.descriptors.DescriptorCalcException;
import com.arosbio.data.SparseFeature;
import com.arosbio.data.SparseFeatureImpl;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.DataSink;
import com.arosbio.io.DataSource;
import com.google.common.collect.ImmutableList;

public class MockFailingDescriptor implements ChemDescriptor {

    public double probabilityOfFailure = 0.2;

    public MockFailingDescriptor withProbOfFailure(double prob){
        this.probabilityOfFailure = prob;
        return this;
    }

    @Override
    public Map<String, Object> getProperties() {
        return new HashMap<>();
    }

    @Override
    public List<ConfigParameter> getConfigParameters() {
        return ImmutableList.of();
    }

    @Override
    public void setConfigParameters(Map<String, Object> params) 
        throws IllegalStateException, IllegalArgumentException {
        // Do nothing
    }

    @Override
    public void initialize() {}

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public String getName() {
        return "sometimes failing descriptor";
    }

    @Override
    public List<String> getFeatureNames() {
        return ImmutableList.of("Feat1");
    }

    @Override
    public boolean requires3DCoordinates() {
        return false;
    }

    @Override
    public ChemDescriptor clone() {
        return new MockFailingDescriptor().withProbOfFailure(probabilityOfFailure);
    }

    @Override
    public boolean hasFixedLength() {
        return true;
    }

    @Override
    public int getLength() {
        return 1;
    }

    @Override
    public List<SparseFeature> calculateDescriptors(IAtomContainer molecule)
            throws DescriptorCalcException, IllegalStateException {
        if (Math.random() < probabilityOfFailure)
                throw new DescriptorCalcException("I decided to fail");
        return ImmutableList.of(new SparseFeatureImpl(0, 1d));
    }

    @Override
    public List<SparseFeature> calculateDescriptorsAndUpdate(IAtomContainer molecule)
            throws DescriptorCalcException, IllegalStateException {
        return calculateDescriptors(molecule);
    }

    @Override
    public void saveDescriptorToSink(DataSink sink, String basePath, EncryptionSpecification spec)
            throws IOException, InvalidKeyException, IllegalStateException {
        // do nothing
        
    }

    @Override
    public void loadDescriptorFromSource(DataSource source, String path, EncryptionSpecification spec)
            throws IOException, InvalidKeyException {
        // do nothing
    }
    
}

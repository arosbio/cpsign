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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.cheminf.descriptors.ChemDescriptor;
import com.arosbio.cheminf.descriptors.DescriptorCalcException;
import com.arosbio.data.SparseFeature;
import com.arosbio.data.SparseFeatureImpl;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.DataSink;
import com.arosbio.io.DataSource;

public class MockNoninformativeDescriptor implements ChemDescriptor {

    private double valueToReturn = 1;
    private int numFeats=1;
    private boolean ready = false;

    public MockNoninformativeDescriptor(){}
    public MockNoninformativeDescriptor(double returnVal){
        this.valueToReturn = returnVal;
    }
    public MockNoninformativeDescriptor(double returnVal, int numFeats){
        this.valueToReturn = returnVal;
        this.numFeats = numFeats;
    }

    @Override
    public Map<String, Object> getProperties() {
        return new HashMap<>();
    }

    @Override
    public List<ConfigParameter> getConfigParameters() {
        return new ArrayList<>();
    }

    @Override
    public void setConfigParameters(Map<String, Object> arg0) throws IllegalStateException, IllegalArgumentException {
        // do nothing
    }

    @Override
    public void initialize() {
        ready = true;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public String getName() {
        return "MockNoninformativeDescriptor";
    }

    @Override
    public List<String> getFeatureNames() {
        List<String> feats = new ArrayList<>();
        for (int i = 0; i<numFeats; i++){
            feats.add(String.format("non-informative{%s:%.2f}", i,valueToReturn));
        }
        return feats;
    }

    @Override
    public boolean requires3DCoordinates() {
        return false;
    }

    @Override
    public boolean hasFixedLength() {
        return true;
    }

    @Override
    public int getLength() {
        return numFeats;
    }

    @Override
    public List<SparseFeature> calculateDescriptors(IAtomContainer molecule)
            throws DescriptorCalcException, IllegalStateException {
        if (! ready){
            throw new IllegalStateException("Not init");
        }
        List<SparseFeature> feats = new ArrayList<>();
        for (int i=0;i<numFeats; i++){
            feats.add(new SparseFeatureImpl(i,valueToReturn));
        }
        return feats;
    }

    @Override
    public List<SparseFeature> calculateDescriptorsAndUpdate(IAtomContainer molecule)
            throws DescriptorCalcException, IllegalStateException {
        return calculateDescriptors(molecule);
    }

    @Override
    public void saveDescriptorToSink(DataSink sink, String basePath, EncryptionSpecification spec)
            throws IOException, InvalidKeyException, IllegalStateException {
        String base = (basePath!=null && !basePath.isBlank()) ? basePath : "/";
        base += base.endsWith("/") ? "" : "/"; // add trailing last dash
        
        try(OutputStream out = sink.getOutputStream(base+"params.txt");
            Writer w = new OutputStreamWriter(out);){
            w.write(""+valueToReturn);
            w.write(';');
            w.write(""+numFeats);
        }
        
    }

    @Override
    public void loadDescriptorFromSource(DataSource src, String basePath, EncryptionSpecification spec)
            throws IOException, InvalidKeyException {
        String base = (basePath!=null && !basePath.isBlank()) ? basePath : "/";
        base += base.endsWith("/") ? "" : "/"; // add trailing last dash
        
        try(InputStream out = src.getInputStream(base+"params.txt");
            ){
            String contents = IOUtils.toString(out, StandardCharsets.UTF_8);
            String[] params = contents.split(";");
            valueToReturn = Double.parseDouble(params[0]);
            numFeats = Integer.parseInt(params[1]);
        }
    }

    public MockNoninformativeDescriptor clone(){
        return new MockNoninformativeDescriptor(valueToReturn);
    }
    
}

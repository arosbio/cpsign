/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.splitting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.GlobalConfig;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.google.common.collect.Range;

public class LOOSplitter implements DataSplitter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LOOSplitter.class);

    private final SubSet calibrationData, modelExclusiveData;
    private final long seed;
    private final List<DataRecord> shuffledRecords;
    private final Range<Double> foundRange;
    
    /** Iteration index */ 
    private int currentIndex=0;
    
    private LOOSplitter(final Builder b, final Dataset data) {
        Objects.requireNonNull(data, "data must not be null");
        calibrationData = data.getCalibrationExclusiveDataset();
        modelExclusiveData = data.getModelingExclusiveDataset();
        this.seed = b.seed;
        
        shuffledRecords = new ArrayList<>(data.getDataset());
        if (b.shuffle) {
            Collections.shuffle(shuffledRecords, new Random(seed));
        }

        if (b.findLabelRange){
            Range<Double> r = null;
            try {
                r = findObservedLabelSpace(data);
            } catch (Exception e){
                LOGGER.debug("attempted to find label-space but failed: {}",e.getMessage());
            }
            foundRange = r;
        } else {
            foundRange = null;
        }
    }

    public static class Builder {
        private boolean shuffle = false;
        private long seed = GlobalConfig.getInstance().getRNGSeed();
        private boolean findLabelRange = false;
        
        public boolean shuffle(){
            return shuffle;
        }
        public Builder shuffle(boolean shuffle){
            this.shuffle = shuffle; 
            return this;
        }
        public long seed(){
            return seed;
        }
        public Builder seed(long seed){
            this.seed = seed;
            return this;
        }
        public boolean findLabelRange(){
            return findLabelRange;
        }
        public Builder findLabelRange(boolean findRange){
            this.findLabelRange = findRange;
            return this;
        }

        public LOOSplitter build(Dataset data){
            return new LOOSplitter(this, data);
        }

    }


    private Range<Double> findObservedLabelSpace(Dataset data){
        // Find the regression label space once in case we should
        try {
            return DataUtils.findLabelRange(data);
        } catch (Exception e){
            LOGGER.debug("failed to find the observed label-range", e);
            throw new IllegalArgumentException("could not find the min and max observed values: " + e.getMessage());
        }
    }

    @Override
    public boolean hasNext() {
        return currentIndex < shuffledRecords.size();
    }

    @Override
    public DataSplit next() {
        if (! hasNext()){
            throw new NoSuchElementException("No more test-train splits");
        }

        try {
            return get(currentIndex);
        } finally{
            currentIndex++;
        }
    }


    @Override
    public DataSplit get(int index) throws NoSuchElementException {
        LOGGER.debug("Generating split {}/{}", (index+1), shuffledRecords.size());
        
        // Generate the training and test-set
        List<DataRecord> trainingSet = new ArrayList<>(shuffledRecords);
        trainingSet.remove(index);
        List<DataRecord> testSet = new ArrayList<>();
        testSet.add(shuffledRecords.get(index));

        Dataset trainingData = new Dataset()
            .withDataset(new SubSet(trainingSet))
            .withCalibrationExclusiveDataset(calibrationData)
            .withModelingExclusiveDataset(modelExclusiveData);

        LOGGER.debug("Using {} examples for training and {} example(s) for testing (not counting model-exclusive or calibration-exclusive data)",
            trainingSet.size(), testSet.size());
        
        return new DataSplit(trainingData, testSet, seed, foundRange);
    }

    @Override
    public int getMinSplitIndex() {
        return 0;
    }

    @Override
    public int getMaxSplitIndex() {
        return shuffledRecords.size() -1;
    }

}

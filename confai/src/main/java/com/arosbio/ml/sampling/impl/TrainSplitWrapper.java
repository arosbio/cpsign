/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.sampling.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

import com.arosbio.data.DataRecord;
import com.arosbio.data.splitting.DataSplit;
import com.arosbio.data.splitting.DataSplitter;
import com.arosbio.ml.sampling.TrainSplit;
import com.arosbio.ml.sampling.TrainSplitGenerator;

public class TrainSplitWrapper implements TrainSplitGenerator {

    private final DataSplitter base;

    public TrainSplitWrapper(DataSplitter toWrap){
        this.base = toWrap;
    }

    @Override
    public boolean hasNext() {
        return base.hasNext();
    }

    @Override
    public TrainSplit next() throws NoSuchElementException {
        return convert(base.next());
    }

    @Override
    public TrainSplit get(int index) throws NoSuchElementException {
        return convert(base.get(index));
    }

    @Override
    public int getMaxSplitIndex() {
        return base.getMaxSplitIndex();
    }

    @Override
    public int getMinSplitIndex() {
        return base.getMinSplitIndex();
    }

    private static TrainSplit convert(DataSplit input){
        // Put together the proper training set
        List<DataRecord> proper = null; 
        if (input.getFirst().getModelingExclusiveDataset().isEmpty()){
            proper = input.getFirst().getDataset();
        } else {
            // Here we need to a copy of the data
            proper = new ArrayList<>(input.getFirst().getDataset().size() + input.getFirst().getModelingExclusiveDataset().size());
            proper.addAll(input.getFirst().getDataset());
            proper.addAll(input.getFirst().getModelingExclusiveDataset());
            // Now need to shuffle again
            Collections.shuffle(proper, new Random(input.getSeed()));
        }
        
        // Put together the calibration set
        List<DataRecord> calibration = null; 
        if (input.getFirst().getCalibrationExclusiveDataset().isEmpty()){
            calibration = input.getSecond();
        } else {
            // Here we need to copy data
            calibration = new ArrayList<>(input.getSecond().size() + input.getFirst().getCalibrationExclusiveDataset().size());
            calibration.addAll(input.getSecond());
            calibration.addAll(input.getFirst().getCalibrationExclusiveDataset());
            // Now need to shuffle again
            Collections.shuffle(calibration, new Random(input.getSeed()));
        }
        
        return new TrainSplit(proper, calibration,input.getObservedLabelSpace());
    }
    
}

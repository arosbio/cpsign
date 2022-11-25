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
        List<DataRecord> proper = new ArrayList<>();
        proper.addAll(input.getFirst().getDataset());
        if (!input.getFirst().getModelingExclusiveDataset().isEmpty()){
            proper.addAll(input.getFirst().getModelingExclusiveDataset());
            // Now need to shuffle again
            Collections.shuffle(proper, new Random(input.getSeed()));
        }
        // Put together the calibration set
        List<DataRecord> calibration = new ArrayList<>(input.getSecond());
        if (! input.getFirst().getCalibrationExclusiveDataset().isEmpty()){
            calibration.addAll(input.getFirst().getCalibrationExclusiveDataset());
            // Now need to shuffle again
            Collections.shuffle(calibration, new Random(input.getSeed()));
        }

        return new TrainSplit(proper, calibration,input.getObservedLabelSpace());
    }
    
}

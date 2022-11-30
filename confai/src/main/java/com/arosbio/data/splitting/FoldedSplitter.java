package com.arosbio.data.splitting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.google.common.collect.Range;

public class FoldedSplitter implements DataSplitter {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoldedSplitter.class);

    // State
    private final Dataset dataClone;
    private final boolean stratify, shuffle;
    private final int numRepeats, numFolds;
    private final long seed;
    private Range<Double> foundRange;

    // Iteration state
    /** The folds generated when using the iterating  */
    private List<List<DataRecord>> iteratorFolds;
    private int currentFold = 0;
    private int currentRepetition = 0;
    /** Only populated in case stratify == true */
    private final List<List<DataRecord>> stratifiedRecs;

    public static class Builder {
        private int numFolds = 10;
        private boolean shuffle = true;
        private long seed = GlobalConfig.getInstance().getRNGSeed(); 
        private boolean stratify = false;
        private int numRepeats = 1;
        private boolean findObservedLabelSpace = false;
        private String name = "k-fold CV";

        public int numFolds(){
            return numFolds;
        }
        public Builder numFolds(int folds){
            if (folds < 2){
                throw new IllegalArgumentException("Invalid number of folds: " + folds + ", must be >=2");
            }
            this.numFolds = folds;
            return this;
        }
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
        public boolean stratify(){
            return stratify;
        }
        public Builder stratify(boolean stratify){
            this.stratify = stratify;
            return this;
        }
        public int numRepeat(){
            return numRepeats;
        }
        public Builder numRepeat(int num){
            if (num < 1){
                throw new IllegalArgumentException("Num repeats must be at least 1 (i.e. performed once)");
            }
            this.numRepeats = num;
            return this;
        }
        public boolean findLabelRange(){
            return findObservedLabelSpace;
        }
        public Builder findLabelRange(boolean findRange){
            this.findObservedLabelSpace = findRange;
            return this;
        }
        public String name(){
            return name;
        }
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        public FoldedSplitter build(Dataset data){
            return new FoldedSplitter(this, data);
        }
    }

    private FoldedSplitter(
        final Builder b,
        final Dataset data
        ) {

        Objects.requireNonNull(data, "data must not be null");

        if (data.getDataset().size() < b.numFolds) {
            throw new IllegalArgumentException("Cannot run "+b.name + " with more folds than records!");
        }
        if (b.numFolds < 2)
            throw new IllegalArgumentException("Number of folds must be >= 2");
        if (!b.shuffle && b.numRepeats>1)
            throw new IllegalArgumentException("Shuffling cannot be false if number of repeated k-fold splits is larger than 1");
        this.numFolds = b.numFolds;
        this.stratify = b.stratify;
        this.shuffle = b.shuffle;
        this.numRepeats = b.numRepeats;
        this.seed = b.seed;
        this.dataClone = data.cloneDataOnly();

        if (stratify){
            // do the stratification of data once, also verifies input is not of regression type
            stratifiedRecs = Collections.unmodifiableList(DataUtils.stratify(dataClone.getDataset()));
        } else {
            stratifiedRecs = null;
        }

        if (b.findObservedLabelSpace){
            try {
                findObservedLabelSpace();
            } catch (Exception e){
                LOGGER.debug("attempted to find label-space but failed: {}",e.getMessage());
            }
        }
    }

    private void findObservedLabelSpace(){
        // Find the regression label space once in case we should
        try {
            foundRange = DataUtils.findLabelRange(dataClone);
            LOGGER.debug("found label-range: {}", foundRange);
        } catch (Exception e){
            LOGGER.debug("failed to find the observed label-range", e);
            throw new IllegalArgumentException("could not find the min and max observed values: " + e.getMessage());
        }
    }

    public Range<Double> getObservedLabelSpace() {
        return foundRange;
    }

    

    private List<List<DataRecord>> getFoldsForRep(int forRep) {
        long seedForRep = getSeedForRep(forRep);
        LOGGER.debug("generating folds for repetition {} using stratify={}, shuffle={}, seed={}",
            forRep, stratify, shuffle,seedForRep);
        
        if (stratify) {

            // Init the folds
            int initSize = dataClone.getDataset().size() / numFolds + 1;
            List<List<DataRecord>> folds = new ArrayList<>();
            for (int i=0; i<numFolds; i++){
                folds.add(new ArrayList<>(initSize));
            }

            // split the stratified datasets into the folds
            for (List<DataRecord> recs : stratifiedRecs) {
                
                List<DataRecord> tmp = recs;
                // Shuffle if set to do so
                if (shuffle){
                    tmp = new ArrayList<>(tmp); // Need copy before we shuffle 
                    Collections.shuffle(tmp, new Random(seedForRep));
                }

                // Folds for each strata - note the first ones will be the largest ones (if not evenly divisible)
                List<List<DataRecord>> foldStrata = CollectionUtils.getDisjunctSets(tmp, numFolds, true);
               
                if (tmp.size() % numFolds == 0){
                    // The folds will all have the same size, no need to worry about order
                    for (int i=0; i<numFolds; i++) {
                        folds.get(i).addAll(foldStrata.get(i));
                    }
                    
                } else {
                    // The records are not evenly divisible - need to find where to put the larger folds
                   
                    // Take the smallest folds first, and add the largest strata-folds to them
                    List<Integer> sizeSort = CollectionUtils.getSortedIndicesBySize(folds, true);
                    for (int i=0; i<numFolds; i++) {
                        folds.get(sizeSort.get(i)).addAll(foldStrata.get(i));
                    }
                }
            }

            // Shuffle the folds (so not arranged in order of their labels)
            for (List<DataRecord> f : folds) {
                Collections.shuffle(f, new Random(seedForRep));
            }
            return folds;

        } else {
            List<DataRecord> recs = new ArrayList<>(dataClone.getDataset());

            if (shuffle)
                Collections.shuffle(recs, new Random(seedForRep));
            return CollectionUtils.getDisjunctSets(recs, numFolds, false);
        }

    }

    @Override
    public boolean hasNext() {
        // If called for the first time only - init the folds
        if (iteratorFolds == null){
            iteratorFolds = getFoldsForRep(currentRepetition);
        }
        // If more folds for the current repetition
        if (currentFold < iteratorFolds.size())
            return true;
        // finished the current rep - start new
        currentRepetition ++;
        // Check if there are more reps
        if (currentRepetition < numRepeats) {
            iteratorFolds = getFoldsForRep(currentRepetition);
            currentFold = 0; // reset the fold index for new rep
            return true;
        }
        // No more reps 
        return false;
    }

    @Override
    public DataSplit next() throws NoSuchElementException {
        if (! hasNext())
            throw new NoSuchElementException("No more folds!");

        LOGGER.debug("Generating fold {}/{} for repeat: {}",(currentFold+1),numFolds,currentRepetition);

        try {
            return get(currentFold, currentRepetition);
        } finally{
            currentFold++;
        }

        // Dataset trainingData = new Dataset()
        //     .withCalibrationExclusiveDataset(dataClone.getCalibrationExclusiveDataset().clone()) // TODO - do we need the clone call?
        //     .withModelingExclusiveDataset(dataClone.getModelingExclusiveDataset().clone());

        // List<DataRecord> split0 = new ArrayList<>(numberOfDataRecords);
        // List<DataRecord> split1 = iteratorFolds.get(currentFold);
        // for (int i=0; i<iteratorFolds.size(); i++) {
        //     if (i != currentFold) {
        //         split0.addAll(iteratorFolds.get(i));
        //     }
        // }

        // trainingData.withDataset(new SubSet(split0));

        // LOGGER.debug("Using {} examples in first {} examples second (not counting model-exclusive or calibration-exclusive data)",
        //     split0.size(), split1.size());
        // // Update the fold for next call to next()
        // currentFold++;

        // return new DataSplit(trainingData,split1,seed,foundRange);
    }
    
    @Override
    public DataSplit get(int index) throws NoSuchElementException {
        if (index < 0 || index > getMaxSplitIndex()){
            throw new NoSuchElementException("Invalid index: " + index + ", it must be in the range [0,"+getMaxSplitIndex()+']');
        }

        int getRep = index / numFolds;
        int getIndex = index % numFolds;

        LOGGER.debug("Getting fold {}/{} for repeat: {}",(getIndex+1),numFolds,getRep);

        return get(getIndex, getRep);

        // LOGGER.debug("Generating fold {}/{} for repeat: {}",currentIndex,numFolds,currentRep);

        // Dataset trainingData = new Dataset();
        // trainingData.withCalibrationExclusiveDataset(dataClone.getCalibrationExclusiveDataset().clone()); // TODO - do we need the clone call?
        // trainingData.withModelingExclusiveDataset(dataClone.getModelingExclusiveDataset().clone());

        // List<DataRecord> trainingSet = new ArrayList<>(numberOfDataRecords);
        // List<DataRecord> testSet = iteratorFolds.get(currentFold);
        // for (int i=0; i<iteratorFolds.size(); i++) {
        //     if (i != currentFold) {
        //         trainingSet.addAll(iteratorFolds.get(i));
        //     }
        // }

        // trainingData.withDataset(new SubSet(trainingSet));

        // LOGGER.debug("Using {} examples for training and {} examples for testing (not counting model-exclusive or calibration-exclusive data)",
        //     trainingSet.size(),testSet.size());
        // return new DataSplit(trainingData,testSet,seed,foundRange);
    }

    private DataSplit get(int fold, int rep) throws NoSuchElementException {

        LOGGER.debug("Generating fold {}/{} for repeat: {}",(fold+1),numFolds, rep);

        List<List<DataRecord>> theFolds = null;
        if (rep == currentRepetition && iteratorFolds != null){
            theFolds = iteratorFolds;
        } else {
            theFolds = getFoldsForRep(rep);
        }

        // Copy over the calibration and modeling exclusive data sets
        Dataset first = new Dataset()
            .withCalibrationExclusiveDataset(dataClone.getCalibrationExclusiveDataset().clone()) // TODO - do we need the clone call?
            .withModelingExclusiveDataset(dataClone.getModelingExclusiveDataset().clone());

        List<DataRecord> firstDataOnly = new ArrayList<>(  dataClone.getDataset().size());
        List<DataRecord> second = theFolds.get(fold);
        for (int i=0; i<numFolds; i++) {
            if (i != fold) {
                firstDataOnly.addAll(theFolds.get(i));
            }
        }

        first.withDataset(new SubSet(firstDataOnly));

        LOGGER.debug("Using {} examples in first {} examples second (not counting model-exclusive or calibration-exclusive data)",
            firstDataOnly.size(), second.size());

        return new DataSplit(first, second, getSeedForRep(rep), foundRange);
    }

    private long getSeedForRep(int rep){
        return rep + seed;
    }
    /**
     * The max split index = {@code numFolds * numRepeats - 1}
     * @return {@code numFolds * numRepeats - 1}
     */
    @Override
    public int getMaxSplitIndex() {
        return numFolds*numRepeats - 1;
    }

    /**
     * min split index is always 0
     * @return 0
     */
    @Override
    public int getMinSplitIndex() {
        return 0;
    }

}
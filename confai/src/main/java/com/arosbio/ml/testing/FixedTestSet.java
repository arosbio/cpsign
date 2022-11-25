/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.testing;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.arosbio.data.DataRecord;
import com.arosbio.data.Dataset;
import com.arosbio.ml.interfaces.Predictor;
import com.google.common.collect.ImmutableList;

/**
 * The {@code FixedTestSet} makes it possible to have custom test-train split and use it
 * in with the {@link com.arosbio.ml.testing.TestRunner TestRunner}. This implementation
 * will create a single test-split with the given input records and pass all data from
 * the {@link TestRunner#evaluate(Dataset, Predictor)} (and equivalent methods)
 * as training data. This strategy is not available using ServiceLoader and thus not from the CLI, instead CLI users
 * use the {@code validate} command which is equivalent.
 */
public class FixedTestSet implements TestingStrategy {

    private static final class IteratorImplementation implements Iterator<TestTrainSplit> {

        private boolean beenCalled = false;
        private final Dataset train;
        private final List<DataRecord> test;
        private final long seed;

        private IteratorImplementation(final Dataset train, final List<DataRecord> test, final long seed){
            this.train = train;
            this.test = test;
            this.seed = seed;
        }

        @Override
        public boolean hasNext() {
            return ! beenCalled;
        }

        @Override
        public TestTrainSplit next() throws NoSuchElementException {
            if (beenCalled){
                throw new NoSuchElementException("No more test-train splits");
            }
            beenCalled = true;
            Dataset d = train.cloneDataOnly();
            d.shuffle(seed);
            return new TestTrainSplit(d, test);
        }
    }

    private final List<DataRecord> testSet;
    private long seed;

    public FixedTestSet(List<DataRecord> testSet){
        if (testSet == null || testSet.isEmpty())
            throw new IllegalArgumentException("Test set cannot be empty");
        this.testSet = testSet;
    }

    @Override
    public List<ConfigParameter> getConfigParameters() {
        return ImmutableList.of();
    }

    @Override
    public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
        // no configs to set
    }

    @Override
    public String getDescription() {
        return "Fixed test-set, given by the user";
    }

    @Override
    public String getName() {
        return "Fixed test-set";
    }

    @Override
    public void setSeed(long seed) {
        this.seed = seed;
    }

    @Override
    public Long getSeed() {
        return this.seed;
    }

    @Override
    public int getNumberOfSplitsAndValidate(Dataset data) throws IllegalArgumentException {
        if (data == null || data.isEmpty())
            throw new IllegalArgumentException("Training data cannot be empty");
        return 1;
    }

    @Override
    public Iterator<TestTrainSplit> getSplits(Dataset data) {
        return new IteratorImplementation(data,testSet,seed);
    }
    
    public FixedTestSet clone(){
        FixedTestSet c = new FixedTestSet(new ArrayList<>(testSet));
        c.seed = this.seed;
        return c;
    }
}

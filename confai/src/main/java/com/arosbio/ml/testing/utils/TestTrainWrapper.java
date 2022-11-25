package com.arosbio.ml.testing.utils;

import java.util.Iterator;

import com.arosbio.data.splitting.DataSplit;
import com.arosbio.ml.testing.TestTrainSplit;

public class TestTrainWrapper implements Iterator<TestTrainSplit> {

    private final Iterator<DataSplit> baseSplitter;
    
    public TestTrainWrapper(Iterator<DataSplit> splitter){
        this.baseSplitter = splitter;
    }

    @Override
    public boolean hasNext() {
        return baseSplitter.hasNext();
    }

    @Override
    public TestTrainSplit next() {
        return convert(baseSplitter.next());
    }
    
    private static TestTrainSplit convert(DataSplit s){
        return new TestTrainSplit(s.getFirst(), s.getSecond());
    }
    
}

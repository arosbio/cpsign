/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem.io.in;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Tracking class to keep track of all failed records during file IO, computing descriptors etc. 
 * Intended to be created and shared between the ChemDataset and
 * any reader/processing classes to stop processing records once the maximum allowed failures have been 
 * encountered and an additional failure occurs. I.e. if setting processing to fail after 10 failed records, 
 * the execution will be stopped once 11 failures are encountered.
 */
public final class ProgressTracker {

    private static final int DEFAULT_NUM_ALLOWED_FAILURES = 10;

    private final List<FailedRecord> failures = new ArrayList<>();
    private final boolean usesEarlyStopping;
    private final int maxAllowedFails;

    private ProgressTracker(boolean earlyStop, int maxAllowedFails){
        this.usesEarlyStopping = earlyStop;
        this.maxAllowedFails = maxAllowedFails;
    }

    public static ProgressTracker createNoEarlyStopping(){
        return new ProgressTracker(false, -1);
    }
    /**
     * Create a progress tracker that allows at most {@code maxAllowedFails} failures,
     * if {@code maxAllowedFails} is negative there will be no early stopping 
     * @param maxAllowedFails allowed failures, or negative if no early stopping
     * @return A progress tracker that fails after {@code maxAllowedFails} have been encountered
     */
    public static ProgressTracker createStopAfter(int maxAllowedFails) {
        if (maxAllowedFails<0){
            return new ProgressTracker(false, -1);
        }
        return new ProgressTracker(true, maxAllowedFails);
    }
    /**
     * Create an instance that allows maximum 10 failed records and then stops execution
     * @return the default {@link ProgressTracker} with max 10 allowed failed records
     */
    public static ProgressTracker createDefault(){
        return new ProgressTracker(true, DEFAULT_NUM_ALLOWED_FAILURES);
    }

    public int getNumFailures(){
        return failures.size();
    }

    /**
     * Get the maximum number of failed records during processing,
     * or -1 in case no early stopping should be done
     * @return num allowed processing failures, or -1
     */
    public int getMaxAllowedFailures(){
        return maxAllowedFails;
    }

    /**
     * Returns an unmodifiable list of the {@link FailedRecord} instances
     * @return list of the {@link FailedRecord} instances
     */
    public List<FailedRecord> getFailures(){
        return Collections.unmodifiableList(failures);
    }

    public int register(FailedRecord rec){
        failures.add(rec);
        return failures.size();
    }
    
    public void clear(){
        failures.clear();
    }

    /**
     * Check for early stopping, if this returns {@code true} then the
     * next call to {@link #assertCanContinueParsing()} will cause an 
     * {@link EarlyLoadingStopException} to be thrown
     * @return {@code true} if processing should stop
     */
    public boolean shouldStop(){
        // if no early stopping
        if (!usesEarlyStopping)
            return false;
        return failures.size() > maxAllowedFails;
    }

    public void assertCanContinueParsing() throws EarlyLoadingStopException {
        if (shouldStop())
            throw new EarlyLoadingStopException("Encountered " + failures.size() + " failed records during reading/processing of input", failures);
    }

    public ProgressTracker clone(){
        return new ProgressTracker(usesEarlyStopping, maxAllowedFails);
    }
}

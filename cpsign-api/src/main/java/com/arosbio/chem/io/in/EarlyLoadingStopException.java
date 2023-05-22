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

import java.util.List;

import com.arosbio.commons.EarlyStoppingException;

public class EarlyLoadingStopException extends EarlyStoppingException {

    /**
     * 
     */
    private static final long serialVersionUID = 3760158731846495710L;

    private List<FailedRecord> recs = null;
    
    public EarlyLoadingStopException(String msg, List<FailedRecord> recs) {
        super(msg);
        this.recs = recs;
    }
    
    public List<FailedRecord> getFailedRecords(){
        return this.recs;
    }
}

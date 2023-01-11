/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.commons.mixins;


public interface ResourceAllocator {

    /**
     * Causes the object to release all allocations explicitly. Note that 
     * this method should never result in an Exception being thrown.
     * @return {@code true} if resources were de-allocated successfully
     */
    public boolean releaseResources();

    /**
     * Specifies if the object has resources that needs to be de-allocated
     * at object destruction, and thus if the {@link #releaseResources()} method must be called 
     * at that point
     * @return {@code true} if resources are allocated and needs to be manually released
     */
    public boolean holdsResources();

}
/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.algorithms.impl;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A serializing mechanism that works by having an internal lock. Each method that
 * uses LibLinear is required to call the method "requireLock" before calling any 
 * LibLinear-methods and after it is done, it should release the lock by calling
 * "releaseLock"
 * @author staffan
 *
 */
public class LibLinearSerializer {
	
	private final static Lock lock = new ReentrantLock();
	
	public static void requireLock(){
		lock.lock();
	}
	
	public static void releaseLock() {
		lock.unlock();
	}

}

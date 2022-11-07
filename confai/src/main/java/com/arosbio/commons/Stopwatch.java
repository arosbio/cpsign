/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.commons;

/**
 * 
 * @author ola
 * @author Staffan
 *
 */
public class Stopwatch {
	private long start = System.currentTimeMillis();;
	private long stop;

	public Stopwatch start() {
		start = System.currentTimeMillis(); 
		return this;
	}

	public Stopwatch stop() {
		stop = System.currentTimeMillis();
		return this;
	}

	public long elapsedTimeMillis() {
		return stop - start;
	}
	
	public static String toNiceString(long elapsed) {
		final int millis = (int) (elapsed % 1000);
		final int seconds = (int) ((elapsed / 1000) % 60);
		int minutes = (int) ((elapsed / 1000) / 60);
		final int hours = (int) (minutes / 60);
		minutes -= hours*60;

		if (hours > 0){
			int rounding = (seconds>30 ? 1 : 0);
			return String.format("%d h %d min", hours, minutes+rounding);
		} else if (minutes>0){
			int rounding = (millis>500 ? 1 : 0);
			return String.format("%d min %d s", minutes, seconds+rounding);
		} else if (seconds>0){
			return String.format("%d.%d s",seconds,millis);
		} else {
			return String.format("%d ms",millis);
		}
			
	}

	//return nice string
	public String toString() {
		return toNiceString(elapsedTimeMillis());
	}

}
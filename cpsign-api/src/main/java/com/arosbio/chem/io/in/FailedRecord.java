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

public class FailedRecord implements Comparable<FailedRecord>{
	
	private int index;
	private String id;
	private String reason;

	public FailedRecord(int index) {
		this.index = index;
	}
	
	public FailedRecord(int index, String id) {
		this(index);
		this.id = id;
	}
	
	public int getIndex() {
		return index;
	}
	
	public FailedRecord setID(String id) {
		this.id = id;
		return this;
	}
	
	public boolean hasID() {
		return id != null;
	}
	
	public String getID() {
		return id;
	}
	
	public FailedRecord setReason(String reason) {
		this.reason = reason;
		return this;
	}
	
	public boolean hasReason() {
		return reason != null;
	}
	
	public String getReason() {
		return reason;
	}

	@Override
	public int compareTo(FailedRecord o) {
		return index - o.index;
	}
	
	public String toString() {
		String txt = "Record-index " + index;
		if (id!=null)
			txt += ", ID: "+id;
		if (reason != null)
			txt += ", reason: " + getReason();
		return txt;
	}

}

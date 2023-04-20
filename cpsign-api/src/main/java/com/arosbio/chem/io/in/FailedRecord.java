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
	
	private final int index;
	private final String id;
	private final String reason;
	private final Cause cause;

	public static enum Cause {
		// File/record issues
		/** If the structure is missing, e.g. in a CSV file with the structure column being empty */
		MISSING_STRUCTURE("Missing structure"),
		/** In case a record is valid, but the structure cannot be either parsed into a valid IAtomContainer, or that e.g. perception of aromaticity fails */
		INVALID_STRUCTURE("Invalid structure"),
		/** If a record itself is invalid, i.e. wrong number of columns in CSV */
		INVALID_RECORD("Invalid data record"),
		/** If the record do not contain the property value of interest */
		MISSING_PROPERTY("Missing property value"),
		/** If a record contains the property value, but cannot be converted to a numeric label */
		INVALID_PROPERTY("Invalid property value"),

		// Structure issues
		/** Structure filtered out based on heavy atom count threshold */
		LOW_HAC("Too low Heavy Atom Count (HAC)"),
		/** Record that failed during descriptor calculation */
		DESCRIPTOR_CALC_ERROR("Failed computing descriptors"),
		
		/** If the record fails by a cause not covered by the other enums */
		UNKNOWN("Unknown");

		public final String message;

		private Cause(String msg){
			this.message = msg;
		}

		public String getMessage(){
			return message;
		}
	}

	public static class Builder {

		private int index;
		private String id;
		private String reason;
		private Cause cause = Cause.UNKNOWN;

		public Builder(int index, Cause cause){
			this.index = index;
			this.cause = cause;
		}

		public Builder withID(String id){
			this.id = id;
			return this;
		}

		public Builder withReason(String reason){
			this.reason = reason;
			return this;
		}

		public Builder withCause(Cause cause){
			this.cause = cause;
			if (reason == null || reason.isBlank()){
				this.reason = cause.message;
			}
			return this;
		}

		public FailedRecord build(){
			return new FailedRecord(index,id,reason, cause);
		}
	}
	
	public FailedRecord(int index, String id, String reason, Cause cause) {
		this.index = index;
		this.id = id;
		this.reason = reason;
		this.cause = cause;
	}
	
	public int getIndex() {
		return index;
	}
	
	public boolean hasID() {
		return id != null;
	}
	
	public String getID() {
		return id;
	}
	
	public boolean hasReason() {
		return reason != null;
	}
	
	public String getReason() {
		return reason;
	}

	public Cause getCause(){
		return cause;
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
			txt += ", reason: " + reason;
		else if (cause != null){
			txt += ", cause: " + cause.message;
		}
		return txt;
	}

	public boolean equals(Object o){
		return o instanceof FailedRecord ? 
			equals((FailedRecord)o) : false;
	}
	public boolean equals(FailedRecord r){
		if (this == r){
			// Same ref
			return true;
		}
		return this.index == r.index &&
			this.id == r.id &&
			this.reason.equals(r.reason) &&
			this.cause == r.cause;
	}

}

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

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * A class that allows simplified versioning following SemVer versioning (not strictly though). I.e. a version should be
 * looking like <code>X.Y.Z</code>, where <code>X,Y,Z</code> should be non-negative numbers. When parsing Versions the parser
 * also allows for a {@code X.Y} version (no suffix allowed) and then a patch version of 0 is assigned. Optionally a suffix can be added
 * after <code>X.Y.Z</code> which should be separated by a {@code .}, {@code -}, {@code _} or {@code +} character.
 *   
 * @author staffan
 * @author Aros Bio AB
 */
public class Version {

	private final int major, minor, patch;
	/**
	 * Should never be {@code null}
	 */
	private final String suffix;

	public static class InvalidVersionException extends IllegalArgumentException {

		public InvalidVersionException(String msg){
			super(msg);
		}
	}


	/**
	 * Creates a {@code major.minior.0} version
	 * @param major the major version, {@code >=0}
	 * @param minor the minor version, {@code >=0}
	 */
	public Version(int major, int minor) 
		throws InvalidVersionException {

		this(major, minor, 0, null);
	}

	/**
	 * Creates a {@code major.minior.patch} version
	 * @param major the major version, {@code >=0}
	 * @param minor the minor version, {@code >=0}
	 * @param patch the patch version, {@code >=0}
	 */
	public Version(int major, int minor, int patch)
		throws InvalidVersionException {

		this(major, minor, patch, null);
	}

	/**
	 * Creates a {@code major.minior.patch} version
	 * @param major the major version, {@code >=0}
	 * @param minor the minor version, {@code >=0}
	 * @param patch the patch version, {@code >=0}
	 * @param suffix optional suffix text
	 */
	public Version(int major, int minor, int patch, String suffix)
		throws InvalidVersionException {
		// Validate major.minor.patch all >=0
		if (major<0)
			throw new InvalidVersionException("Invalid major version: "+major);
		if (minor<0)
			throw new InvalidVersionException("Invalid minor version: "+minor);
		if (patch<0)
			throw new InvalidVersionException("Invalid patch version: "+patch);
		this.major = major;
		this.minor = minor;
		this.patch = patch;
		this.suffix = cleanSuffix(suffix);
		if (this.suffix!=null && !this.suffix.isEmpty()){
			// Verify it starts with any of the valid characters (.,-,_ or +)
			if (!Pattern.compile("^[-_+.]").matcher(this.suffix).find()){
				throw new IllegalArgumentException("Invalid version suffix: "+ this.suffix +", the suffix must start with any of these characters; [.-_+]");
			}
		}
	}
	
	/**
	 * Generates a version of 1.0.0 with a suffix of the current UTC time-stamp,
	 * i.e. {@code 1.0.0+time-stamp}
	 * @return a {@link Version} with major.minor.patch of 1.0.0
	 */
	public static Version defaultVersion() {
		return new Version(1,0,0, cleanSuffix('+'+Instant.now().toString()));
	}

	public static Version parseVersion(String version) throws InvalidVersionException {

		String[] splitted = version.split("\\.",3);
		if (splitted.length < 2)
			throw new IllegalArgumentException("Version number \"" + version + "\" does not fulfill the minimum required syntax of major.minor");

		int major, minor, patch;

		// Major
		try {
			major = Integer.parseInt(splitted[0]);
		} catch (NumberFormatException e){
			throw new IllegalArgumentException("Could not parse the major version as an Integer, input: " + version);
		}
		// Minor
		try {
			minor = Integer.parseInt(splitted[1]);
		} catch (NumberFormatException e){
			throw new IllegalArgumentException("Could not parse the minor version as an Integer, input: " + version);
		}

		// If only major.minor
		if (splitted.length == 2){
			return new Version(major, minor);
		}

		// Split using -/_/+/. (being relaxed)
		String[] patchStuff = splitted[2].split("[-_+.]",2);

 		// Get patch
		try {
			patch = Integer.parseInt(patchStuff[0]);
		} catch (NumberFormatException e){
			throw new IllegalArgumentException("Could not parse the patch version as an Integer, input: " + version);
		}

		// The optional suffix part
		String suffix = patchStuff.length>1 ? splitted[2].substring(patchStuff[0].length()) : null;
		
		return new Version(major, minor,patch,suffix);

	
	}

	public int getMajor() {
		return major;
	}

	public int getMinor() {
		return minor;
	}

	public int getPatch() {
		return patch;
	}

	public String getSuffix() {
		return suffix;
	}

	public String toString(){
		String xyz = toStringNoSuffix();
		// StringBuilder sb = new StringBuilder();
		// sb.append(major);
		// sb.append('.');
		// sb.append(minor);
		// sb.append('.');
		// sb.append(patch);
		if (suffix != null && ! suffix.isEmpty()){
			return xyz + suffix;
		}
		
		return xyz;
	}

	public String toStringNoSuffix(){
		return String.format("%d.%d.%d",major,minor,patch);
	}

	public boolean equals(Object o){
		if (o == this)
			return true;
		if (! (o instanceof Version))
			return false;
		Version v = (Version)o;
		if (major != v.major)
			return false;
		if (minor!=v.minor)
			return false;
		if (patch!=v.patch)
			return false;
		if (! suffix.equals(v.suffix))
			return false;
		return true;
	}

	/**
	 * Version instances are immutable - no need to clone
	 */
	public Version clone(){
		return this; 
	}
	
	public Version copyExceptSuffix() {
		return new Version(major, minor, patch);
	}

	public boolean matchMajorMinor(Version other){
		return major == other.major && minor == other.minor;
	}
	
	private static String cleanSuffix(String suffix){
		if (suffix==null || suffix.isBlank())
			return "";
		
		return suffix.trim().replaceAll("\\s", ".");
	}

}

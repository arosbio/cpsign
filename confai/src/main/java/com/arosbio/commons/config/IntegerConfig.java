/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.commons.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.commons.config.Configurable.Sorter;
import com.google.common.collect.Range;

public class IntegerConfig implements ConfigParameter {
	
	public final static String TYPE = "integer";
	
	private final List<String> names;
	private final Range<Integer> allowedRange;
	private final Integer defaultValue;
	private final List<Integer> defaultGridValues;
	private final Sorter sorting;

	private IntegerConfig(Builder b){
		this.names = new ArrayList<>(b.names);
		this.allowedRange = b.allowedRange;
		this.defaultValue = b.defaultValue;
		if (b.defaultGrid != null && ! b.defaultGrid.isEmpty())
			this.defaultGridValues = new ArrayList<>(b.defaultGrid);
		else
			this.defaultGridValues = null;
		this.sorting = b.sorting;
	}

	public static class Builder {
		private List<String> names;
		private Integer defaultValue;
		private Range<Integer> allowedRange = null; 
		private List<Integer> defaultGrid = new ArrayList<>();
		private String description;
		private Sorter sorting = Sorter.none();

		public Builder(String name){
			this(Arrays.asList(name));
		}

		public Builder(List<String> names){
			if (names==null || names.isEmpty())
				throw new IllegalArgumentException("Names must not be empty");
			this.names = names;
		}

		public Builder(String name, Integer defaultValue){
			this(Arrays.asList(name), defaultValue);
		}
		
		public Builder(List<String> names, Integer defaultValue){
			this(names);
			this.defaultValue = defaultValue;
		}

		public Builder names(List<String> names){
			if (names==null || names.isEmpty())
				throw new IllegalArgumentException("Names must not be empty");
			this.names = names;
			return this;
		}

		public Builder defaultValue(int value){
			this.defaultValue = value;
			return this;
		}

		public Builder range(Range<Integer> allowed){
			this.allowedRange = allowed;
			return this;
		}

		public Builder defaultGrid(List<Integer> grid){
			this.defaultGrid = grid;
			return this;
		}

		public Builder description(String description){
			this.description = description;
			return this;
		}

		public Builder sorting(Sorter sorting){
			this.sorting = sorting;
			return this;
		}

		public ConfigParameter build(){
			IntegerConfig conf = new IntegerConfig(this);
			if (description!=null && !description.isEmpty())
				return new DescribedConfig(conf, description);
			return conf;
		}
		
	}

	@Override
	public List<String> getNames() {
		return names;
	}
	
	public Sorter getSorting(){
		return sorting;
	}
	
	/**
	 * Get the allowed range of values (if any)
	 * @return A Range or <code>null</code> if none specified
	 */
	public Range<Integer> getAllowedRange(){
		return allowedRange;
	}
	
	public String toString() {
		String ret =  "Parameter {" + names + "} type {integer}"; 
		if (allowedRange != null)
			ret += ", allowed range " + allowedRange;
		return ret;
	}
	
	public Integer getDefault() {
		return defaultValue;
	}

	@Override
	public String getType() {
		return TYPE;
	}
	
	public List<Object> getDefaultGrid(){
		if (defaultGridValues == null)
			return new ArrayList<>();
		return new ArrayList<>(defaultGridValues);
	}
	
	public ConfigParameter withNames(List<String> names) {
		return new Builder(names,defaultValue)
			.range(allowedRange)
			.defaultGrid(defaultGridValues)
			.sorting(sorting).build();
	}

}

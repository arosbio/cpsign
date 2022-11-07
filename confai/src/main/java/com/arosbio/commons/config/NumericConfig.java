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
import java.util.List;

import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.commons.config.Configurable.Sorter;
import com.google.common.collect.Range;

public class NumericConfig implements ConfigParameter {
	
	public final static String TYPE = "number";
	
	private final List<String> names;
	private final Range<Double> allowedRange;
	private final double defaultValue;
	private final List<Double> defaultGrid;
	private final Sorter sorting;

	private NumericConfig(Builder b){
		this.names = new ArrayList<>(b.names);
		this.allowedRange = b.allowedRange;
		this.defaultValue = b.defaultValue;
		if (b.defaultGrid != null && ! b.defaultGrid.isEmpty())
			this.defaultGrid = new ArrayList<>(b.defaultGrid);
		else
			this.defaultGrid = null;
		this.sorting = b.sorting;
	}

	public static class Builder {
		private List<String> names;
		private double defaultValue;
		private Range<Double> allowedRange = null; 
		private List<Double> defaultGrid = new ArrayList<>();
		private String description;
		private Sorter sorting = Sorter.none();

		public Builder(List<String> names, double defaultValue){
			if (names==null || names.isEmpty())
				throw new IllegalArgumentException("Names must not be empty");
			this.names = names;
			this.defaultValue = defaultValue;
		}

		public Builder names(List<String> names){
			if (names==null || names.isEmpty())
				throw new IllegalArgumentException("Names must not be empty");
			this.names = names;
			return this;
		}

		public Builder defaultValue(double value){
			this.defaultValue = value;
			return this;
		}

		public Builder range(Range<Double> allowed){
			this.allowedRange = allowed;
			return this;
		}

		public Builder defaultGrid(List<Double> grid){
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
			NumericConfig ncp = new NumericConfig(this);
			if (description!=null && !description.isEmpty())
				return new DescribedConfig(ncp, description);
			return ncp;
		}
		
	}

	@Override
	public List<String> getNames() {
		return names;
	}

	public Sorter getSorting(){
		return sorting;
	}

	public Range<Double> getAllowedRange(){
		return allowedRange;
	}
	
	@Override
	public String getType() {
		return TYPE;
	}
	
	public String toString() {
		String ret =  "Parameter {" + names + "} type {"+TYPE+'}'; 
		if (allowedRange != null)
			ret += ", allowed range " + allowedRange;
		return ret;
	}
	
	public Double getDefault() {
		return defaultValue;
	}

	@Override
	public List<Object> getDefaultGrid() {
		if (defaultGrid == null)
			return new ArrayList<>();
		return new ArrayList<>(defaultGrid);
	}

	public DescribedConfig addDescription(String description) {
		return new DescribedConfig(this, description);
	}
	
	public ConfigParameter withNames(List<String> names) {
		return new Builder(names, defaultValue)
			.range(allowedRange)	
			.defaultGrid(defaultGrid)
			.sorting(sorting)
			.build();
	}
}

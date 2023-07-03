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

public class BooleanConfig implements ConfigParameter {
	
	public final static String TYPE = "boolean";

	private final List<String> names;
	private final boolean defaultValue;
	private final List<Boolean> defaultGrid;
	private final Sorter sorting;

	private BooleanConfig(Builder b){
		this.names = new ArrayList<>(b.names);
		this.defaultValue = b.defaultValue;
		if (b.defaultGrid!=null && !b.defaultGrid.isEmpty())
			this.defaultGrid = new ArrayList<>(b.defaultGrid);
		else
			this.defaultGrid = null;
		this.sorting = b.sorting;
	}

	public static class Builder {
		private List<String> names;
		private boolean defaultValue;
		private List<Boolean> defaultGrid;
		private String description;
		private Sorter sorting = Sorter.none();

		public Builder(String name, boolean defaultValue){
			this(Arrays.asList(name), defaultValue);
		}

		public Builder(List<String> names, boolean defaultValue){
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

		public Builder defaultValue(boolean value){
			this.defaultValue = value;
			return this;
		}

		public Builder defaultGrid(List<Boolean> grid){
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
			BooleanConfig conf = new BooleanConfig(this);
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

	public Boolean getDefault() {
		return defaultValue;
	}
	
	public String toString() {
		return  "Parameter {" + names + "} type {"+TYPE+"} default {"+defaultValue+'}'; 
	}

	@Override
	public String getType() {
		return TYPE;
	}
	
	public List<Object> getDefaultGrid(){
		if (defaultGrid == null || defaultGrid.isEmpty())
			return new ArrayList<>();
		return new ArrayList<>(defaultGrid);
	}

	@Override
	public ConfigParameter withNames(List<String> names) {
		return new BooleanConfig.Builder(names,defaultValue)
			.defaultGrid(defaultGrid)
			.sorting(sorting)
			.build();
	}

}

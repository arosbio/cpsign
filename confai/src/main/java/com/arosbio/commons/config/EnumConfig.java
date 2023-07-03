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
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.commons.config.Configurable.Sorter;

public class EnumConfig <T extends Enum<T>> implements ConfigParameter {
	
	public final static String TYPE = "enum/one-of";
	
	private final List<String> names;
	private final EnumSet<T> enumClazz;
	private final T defaultValue;
	private final List<T> defaultGrid;
	private final Sorter sorting;

	private EnumConfig(Builder<T> b){
		this.names = new ArrayList<>(b.names);
		this.defaultValue = b.defaultValue;
		this.enumClazz = b.enumClazz;
		if (b.defaultGrid!=null && !b.defaultGrid.isEmpty())
			this.defaultGrid = new ArrayList<>(b.defaultGrid);
		else
			this.defaultGrid = new ArrayList<>();
		this.sorting = b.sorting;
	}

	public static class Builder<T extends Enum<T>> {
		private List<String> names;
		private T defaultValue;
		private EnumSet<T> enumClazz;
		private List<T> defaultGrid;
		private String description;
		private Sorter sorting = Sorter.none();

		public Builder(String name, EnumSet<T> allowedValues, T defaultValue){
			this(Arrays.asList(name), allowedValues, defaultValue);
		}

		public Builder(List<String> names,EnumSet<T> allowedValues, T defaultValue){
			if (names==null || names.isEmpty())
				throw new IllegalArgumentException("Names must not be empty");
			this.names = names;
			this.enumClazz = allowedValues;
			this.defaultValue = defaultValue;
		}

		public Builder<T> names(List<String> names){
			if (names==null || names.isEmpty())
				throw new IllegalArgumentException("Names must not be empty");
			this.names = names;
			return this;
		}

		public Builder<T> defaultValue(T value){
			this.defaultValue = value;
			return this;
		}

		public Builder<T> defaultGrid(List<T> grid){
			this.defaultGrid = grid;
			return this;
		}

		public Builder<T> description(String description){
			this.description = description;
			return this;
		}

		public Builder<T> sorting(Sorter sorting){
			this.sorting = sorting;
			return this;
		}

		public ConfigParameter build(){
			EnumConfig<T> conf = new EnumConfig<>(this);
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
	
	public Object[] getEnumValues() {
		List<T> allowedVals = new ArrayList<>();
		Iterator<T> iter = enumClazz.iterator();
		while (iter.hasNext()) {
			allowedVals.add(iter.next());
		}
		
		return allowedVals.toArray();
	}
	
	public Iterator<T> getEnumIterator(){
		return enumClazz.iterator();
	}
	
	@Override
	public String getType() {
		return TYPE;
	}
	
	public String toString() {
		return String.format("Parameter {%s}, type {%s}, allowed values {%s}, default value {%s}",
			names, TYPE, enumClazz,defaultValue);
	}
	
	public Object getDefault(){
		return defaultValue;
	}
	
	public List<Object> getDefaultGrid(){
		if (defaultGrid == null)
			return new ArrayList<>();
		return new ArrayList<>(defaultGrid);
	}
	
	public ConfigParameter withNames(List<String> names) {
		return new EnumConfig.Builder<>(names, enumClazz,defaultValue)
			.defaultGrid(defaultGrid)
			.sorting(sorting)
			.build();
	}

}

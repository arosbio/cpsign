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

public class ImplementationConfig<T> implements ConfigParameter {
	
	public final static String TYPE = "Implementation";
	
	private final List<String> names;
	private final Class<T> clazz;
	private final T defaultValue;
	private final List<Class<T>> defaultGrid;
	private final Sorter sorting;

	private ImplementationConfig(Builder<T> b){
		this.names = new ArrayList<>(b.names);
		this.defaultValue = b.defaultValue;
		this.clazz = b.clazz;
		if (b.defaultGrid != null && ! b.defaultGrid.isEmpty())
			this.defaultGrid = new ArrayList<>(b.defaultGrid);
		else
			this.defaultGrid = null;
		this.sorting = b.sorting;
	}

	public static class Builder<T> {
		private List<String> names;
		private T defaultValue;
		private Class<T> clazz;
		private List<Class<T>> defaultGrid = new ArrayList<>();
		private String description;
		private Sorter sorting = Sorter.none();

		public Builder(String name,  Class<T> clazz){
			this(Arrays.asList(name), clazz);
		}

		public Builder(List<String> names, Class<T> clazz){
			if (names==null || names.isEmpty())
				throw new IllegalArgumentException("Names must not be empty");
			this.names = names;
			this.clazz = clazz;
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

		public Builder<T> defaultGrid(List<Class<T>> grid){
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
			ImplementationConfig<T> conf = new ImplementationConfig<>(this);
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

	public Class<T> getClassOrInterface() {
		return clazz;
	}
	
	@Override
	public String getType() {
		return TYPE;
	}
	
	public T getDefault(){
		return defaultValue;
	}
	
	public String toString() {
		return String.format("Parameter {%s}, type {%s}, Class/Interface: {%s}",
			names,TYPE,clazz);
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

	@Override
	public ConfigParameter withNames(List<String> names) {
		return new Builder<>(names, clazz)
			.defaultValue(defaultValue)
			.defaultGrid(defaultGrid)
			.sorting(sorting).build();
	}

}

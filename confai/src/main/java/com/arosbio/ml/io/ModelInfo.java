/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.io;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.arosbio.commons.Version;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * ModelInfo is an immutable object containing information about a predictor or precomputed data 
 * required when serializing it using {@link ConfAISerializer}. Instantiation can be done either by
 * the two constructors or using the {@link Builder} class for more flexibility in what information
 * you wish to store.
 */
public class ModelInfo implements Cloneable {
	
	private final String modelName;
	private final Version modelVersion;
	private final String modelCategory;
	
	public ModelInfo(String name, Version version, String category){
		Objects.requireNonNull(name, "Model name cannot be empty");
		Objects.requireNonNull(version, "Model version cannot be null");
		this.modelName = name;
		this.modelVersion = version;
		this.modelCategory = (category != null? category : "");
	}
	
	public ModelInfo(String name){
		this(name, Version.defaultVersion(), "");
	}

	public static class Builder {
		private String name, cat = "";
		private Version v = Version.defaultVersion();

		public Builder(String name){
			this.name = name;
		}

		public Builder name(String name){
			this.name = name;
			return this;
		}
		public Builder category(String cat){
			this.cat = cat;
			return this;
		}
		public Builder version(Version v){
			this.v = v;
			return this;
		}
		public ModelInfo build(){
			return new ModelInfo(name,v,cat);
		}

	}
	
	public String getName(){
		return modelName;
	}
	public Version getVersion(){
		return modelVersion;
	}
	public String getCategory(){
		return modelCategory;
	}
	public boolean isValid(){
		return modelVersion!=null && modelName!=null && ! modelName.isEmpty();
	}
	
	public String toString(){
		return new JsonObject(getProperties()).toJson(); 
	}
	
	public Map<String,Object> getProperties(){
		Map<String, Object> infoProps = new HashMap<>();
		infoProps.put(PropertyNameSettings.MODEL_NAME_KEY, modelName);
		infoProps.put(PropertyNameSettings.MODEL_VERSION_KEY, modelVersion.toString());
		infoProps.put(PropertyNameSettings.MODEL_CATEGORY_KEY, modelCategory);
		return infoProps;
	}
	
	public static ModelInfo fromProperties(Map<String,Object> props) throws IllegalArgumentException {
		try {
			String n = (String)props.get(PropertyNameSettings.MODEL_NAME_KEY);
			String cat = (String)props.get(PropertyNameSettings.MODEL_CATEGORY_KEY);
			Version v = Version.parseVersion((String)props.get(PropertyNameSettings.MODEL_VERSION_KEY));
			return new ModelInfo(n,v,cat);
		} catch (Exception e){
			e.printStackTrace();
			throw new IllegalArgumentException("Could not get ModelInfo from properties");
		}
	}
	
	public ModelInfo clone(){
		return new ModelInfo(modelName, modelVersion.clone(), modelCategory);
	}

	public boolean equals(Object o){
		if (o == this)
			return true;
		if (!(o instanceof ModelInfo))
			return false;
		ModelInfo i = (ModelInfo) o;
		return this.modelVersion.equals(i.modelVersion) 
			&& this.modelCategory.equals(i.modelCategory)
			&& this.modelName.equals(i.modelName);
	}
}

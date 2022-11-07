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

import java.util.List;

import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.commons.config.Configurable.Sorter;
import com.arosbio.commons.mixins.Described;

public class DescribedConfig implements ConfigParameter, Described {
	
	private final ConfigParameter config;
	private final String description;
	
	public DescribedConfig(ConfigParameter conf, String description) {
		this.config = conf;
		this.description = description;
	}
	
	@Override
	public String getDescription() {
		return description;
	}
	
	public ConfigParameter getOriginalConfig() {
		return config;
	}

	@Override
	public List<String> getNames() {
		return config.getNames();
	}

	@Override
	public Sorter getSorting(){
		return config.getSorting();
	}

	@Override
	public String getType() {
		return config.getType();
	}

	@Override
	public Object getDefault() {
		return config.getDefault();
	}

	@Override
	public List<Object> getDefaultGrid() {
		return config.getDefaultGrid();
	}
	
	@Override
	public String toString() {
		return "Described config: " + config.toString() + ", description: " + description;
	}

	@Override
	public ConfigParameter withNames(List<String> names) {
		return new DescribedConfig(config.withNames(names), description);
	}

}

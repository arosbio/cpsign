/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.transform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.Experimental;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.BooleanConfig;
import com.arosbio.commons.config.IntegerConfig;
import com.google.common.collect.Range;

/**
 * The <code>ColumnTransformer</code> abstract class groups functionality needed for Transformers 
 * that can be applied to only a subset of the columns (i.e. features) in a dataset. 
 *  
 * @author staffan
 *
 */
@Experimental
public abstract class ColumnTransformer implements Transformer {
	
	private static final List<String> COL_MAX_IND_CONF = Arrays.asList("colMaxIndex");
	private static final List<String> COL_MIN_IND_CONF = Arrays.asList("colMinIndex");
	private static final List<String> EXPLICIT_LIST_CONF = Arrays.asList("colList");
	private static List<String> USE_ALL_COLS_CONF = Arrays.asList("useAllCols");
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 2125667918913161829L;
	private ColumnSpec columns = ColumnSpec.allColumns();
	
	public ColumnTransformer() {}
	
	public ColumnTransformer(ColumnSpec cols) {
		if (cols != null)
			columns = cols;
	}
	
	public void setColumns(ColumnSpec cols) {
		if (cols == null) {
			this.columns = ColumnSpec.allColumns();
		} else {
			this.columns = cols;
		}
	}
	
	public ColumnSpec getColumns() {
		return columns;
	}
	
	public abstract Transformer clone();
	
	@Override
	public List<ConfigParameter> getConfigParameters() {
		List<ConfigParameter> params = new ArrayList<>();
		params.add(new BooleanConfig.Builder(USE_ALL_COLS_CONF, true).build());
		params.add(new IntegerConfig.Builder(EXPLICIT_LIST_CONF, null).description("A list or range of integer values").build());
		params.add(new IntegerConfig.Builder(COL_MIN_IND_CONF, null).range(Range.atLeast(0)).build());
		params.add(new IntegerConfig.Builder(COL_MAX_IND_CONF, null).range(Range.atLeast(0)).build());
		return params;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
		if (params.isEmpty())
			return;
		Integer min = null;
		Integer max = null;
		Set<Integer> colsCollection = null;
		Boolean useAllCols = null;
		
		for (Map.Entry<String, Object> kv: params.entrySet()) {
			if (CollectionUtils.containsIgnoreCase(COL_MIN_IND_CONF, kv.getKey())) {
				min = TypeUtils.asInt(kv.getValue());
			} else if (CollectionUtils.containsIgnoreCase(COL_MAX_IND_CONF,kv.getKey())) {
				max = TypeUtils.asInt(kv.getValue());
			} else if (CollectionUtils.containsIgnoreCase(EXPLICIT_LIST_CONF, kv.getKey())) {
				colsCollection = new HashSet<>();
				if (kv.getValue() instanceof List) {
					for (Object o : (List<Object>) kv.getValue()) {
						colsCollection.add(TypeUtils.asInt(o));
					}
				} else if (kv.getValue() instanceof Integer){
					colsCollection.add((Integer)kv.getValue());
				}
				
			} else if (CollectionUtils.containsIgnoreCase(USE_ALL_COLS_CONF,kv.getKey())) {
				useAllCols = TypeUtils.asBoolean(kv.getValue());
			} 
			
		}
		
		int numValidArgs = 0;
		numValidArgs += (min!=null || max!=null) ? 1 : 0;
		numValidArgs += colsCollection !=null? 1 : 0;
		numValidArgs += useAllCols != null && useAllCols ? 1 : 0; // gotten the argument and it's true

		if (numValidArgs > 1) {
			throw new IllegalArgumentException("Transformer " + getName() + " got multiple non-combinable parameters: " + params.keySet());
		}
		
		if (useAllCols != null && useAllCols) {
			columns = ColumnSpec.allColumns();
		} else if (min!=null || max!=null) {
			if (min != null && max!= null) {
				columns = new ColumnSpec(Range.closed(min, max));
			} else if (min!=null) {
				columns = new ColumnSpec(Range.atLeast(min));
			} else {
				columns = new ColumnSpec(Range.atMost(max));
			}
		} else if (colsCollection != null) {
			columns = new ColumnSpec(colsCollection);
		} else {
			// No column spec specified!
		}

	}
	
}

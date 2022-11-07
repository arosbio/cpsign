/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.out;

import java.util.List;
import java.util.Map;

import com.arosbio.commons.MathUtils;
import com.arosbio.commons.StringUtils;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;

public class JSONFormattingHelper {

	// --------------
//	public static JsonObject getJSONMoleculeData(IAtomContainer ac){
//		JsonObject molVals = new JsonObject(CollectionUtils.toStringKeys(ac.getProperties()));
//
//		// Title only if explicitly set
//		if(molVals.get(CDKConstants.TITLE)==null)
//			molVals.remove(CDKConstants.TITLE);
//		// cdk:Remark only if explicitly set
//		if(molVals.get(CDKConstants.REMARK)==null)
//			molVals.remove(CDKConstants.REMARK);
//
//		return molVals;
//	}
	
	public static JsonObject toJSON(Map<? extends Object,? extends Object> map) {
		JsonObject cleaned = new JsonObject();
		for (Map.Entry<?, ?> kv : map.entrySet()) {
			if (kv.getKey() instanceof String) {
				if (kv.getValue() == null) {
					// Skip if value is null
					continue;
				} 
				
				Object val = kv.getValue();
				if (val instanceof Double || val instanceof Float) {
					val = MathUtils.roundTo3significantFigures((Double)val);
				} else if (val instanceof Map<?, ?>)
					val = toJSON((Map<?,?>)val);
				else if (val instanceof List<?>) {
					val = toJSON((List<?>)val);
				} 
				cleaned.put((String)kv.getKey(), val);
			}
			// If not string - skip it!
		}
		return cleaned;
	}
	
	public static <T> JsonArray toJSON(List<T> list) {
		JsonArray resList = new JsonArray();
		for (T val: list) {
			if (val instanceof Double | val instanceof Float)
				resList.add(MathUtils.roundTo3significantFigures((Double)val));
			else if (val instanceof Map<?, ?>)
				resList.add(toJSON((Map<?,?>)val));
			else if (val instanceof List<?>)
				resList.add(toJSON((List<?>)val));
			else
				resList.add(val);
		}

		return resList;
	}
	
	public static JsonObject toCamelCaseKeys(JsonObject json) {
		JsonObject res = new JsonObject();
		for (Map.Entry<String, Object> kv : json.entrySet()) {
			String key = StringUtils.toCamelCase(kv.getKey());
			Object v = kv.getValue();
			if (v instanceof Map<?,?>)
				res.put(key, toCamelCaseKeys((JsonObject)v));
			else if (v instanceof List<?>)
				res.put(key, toCamelCaseKeys((JsonArray) v));
			else
				res.put(key, v);
		}
		
		return res;
	}
	
	public static JsonArray toCamelCaseKeys(JsonArray array) {
		JsonArray res = new JsonArray();
		for (Object v : array) {
			if (v instanceof Map<?,?>)
				res.add(toCamelCaseKeys((JsonObject)v));
			else if (v instanceof List<?>)
				res.add(toCamelCaseKeys((JsonArray) v));
			else
				res.add(v);
		}
		return res;
	}

}

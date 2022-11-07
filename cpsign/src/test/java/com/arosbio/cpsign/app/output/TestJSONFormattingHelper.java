/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app.output;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.cpsign.out.JSONFormattingHelper;
import com.arosbio.tests.suites.NonSuiteTest;
import com.arosbio.tests.suites.UnitTest;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

@Category({UnitTest.class, NonSuiteTest.class})
public class TestJSONFormattingHelper {

	private final static String JSON_TEXT = "{\"calibrationPlot\":[{\"ClassifierAccuracy\":0.6593913955928646,\"UnobservedConfidence\":0.6,\"#Examples\":4765.0},{\"ClassifierAccuracy\":0.7601259181532004,\"UnobservedConfidence\":0.7,\"#Examples\":4765.0},{\"ClassifierAccuracy\":0.869884575026233,\"UnobservedConfidence\":0.8,\"#Examples\":4765.0},{\"ClassifierAccuracy\":0.955299055613851,\"UnobservedConfidence\":0.9,\"#Examples\":4765.0},{\"ClassifierAccuracy\":0.963903462749213,\"UnobservedConfidence\":0.95,\"#Examples\":4765.0}],\"Class confidence\":0.8370261132487038,\"Observed Fuzziness (0)\":0.26826720002291066,\"observedFuzziness\":0.2751503569842145,\"Observed Fuzziness (1)\":0.28355063102039874,\"Class credibility\":0.6532907683553111,\"efficiencyPlot\":[{\"Empty label predictions\":0.08037775445960126,\"UnobservedConfidence\":0.6,\"Efficiency (fraction multi label predictions)\":4.197271773347324E-4,\"Single label predictions\":0.919202518363064},{\"Empty label predictions\":0.0,\"UnobservedConfidence\":0.7,\"Efficiency (fraction multi label predictions)\":0.1286463798530955,\"Single label predictions\":0.8713536201469045},{\"Empty label predictions\":0.0,\"UnobservedConfidence\":0.8,\"Efficiency (fraction multi label predictions)\":0.4098635886673662,\"Single label predictions\":0.5901364113326338},{\"Empty label predictions\":0.0,\"UnobservedConfidence\":0.9,\"Efficiency (fraction multi label predictions)\":0.6986358866736622,\"Single label predictions\":0.3013641133263379},{\"Empty label predictions\":0.0,\"UnobservedConfidence\":0.95,\"Efficiency (fraction multi label predictions)\":0.7500524658971668,\"Single label predictions\":0.24994753410283316}]}";
	
	@Test
	public void testCleanJSONFormatter() throws JsonException {
		JsonObject obj = (JsonObject) Jsoner.deserialize(JSON_TEXT);

		// JsonObject rounded = JSONFormattingHelper.toJSON(obj);

//		JSONObject obj = (JSONObject) new JSONParser().parse(JSON_TEXT);
		System.err.println(obj.toJson());
		System.err.println(Jsoner.prettyPrint(obj.toJson())); //JSONFormattingHelper.toPrettyJSON(obj));
	}

	// @Test
	public void checkRoundingNotChangeOrderOfKeys(){
		// JsonObject implements HashMap - which doesn't preserve order of keys. so no this doesn't work..
		Map<String,Object> jsonMap = new LinkedHashMap<>();
		jsonMap.put("firstKey", Arrays.asList(1,2,3,4));
		jsonMap.put("secondKey", "value");
		jsonMap.put("someKey", "value2");
		jsonMap.put("aaaa", 5.12512512);
		jsonMap.put("bbb122", 4567890);

		System.err.println(jsonMap);

		JsonObject rounded = JSONFormattingHelper.toJSON(jsonMap);
		System.err.println(rounded.toJson());

		System.err.println("\nPRETTY PRINT:\n");
		System.err.println(Jsoner.prettyPrint(rounded.toJson()));
	}
}

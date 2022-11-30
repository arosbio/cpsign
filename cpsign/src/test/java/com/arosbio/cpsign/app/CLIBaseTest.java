/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.fusesource.jansi.AnsiConsole;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.contrib.java.lang.system.Assertion;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.commons.GlobalConfig.Defaults.PredictorType;
import com.arosbio.commons.StringUtils;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.cpsign.app.params.CLIParameters.ClassOrRegType;
import com.arosbio.cpsign.out.OutputNamingSettings;
import com.arosbio.cpsign.out.OutputNamingSettings.JSON;
import com.arosbio.ml.TrainingsetValidator;
import com.arosbio.ml.sampling.FoldedSampling;
import com.arosbio.ml.sampling.FoldedStratifiedSampling;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.sampling.RandomStratifiedSampling;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.TestEnv;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

import ch.qos.logback.core.joran.spi.JoranException;
import picocli.CommandLine.Help;

public abstract class CLIBaseTest extends TestEnv {

	static {
		AnsiConsole.systemInstall();
	}

	public final static String ACP_CLASSIFICATION_TYPE = ""+PredictorType.ACP_CLASSIFICATION.getId();
	public final static String ACP_REGRESSION_TYPE = ""+PredictorType.ACP_REGRESSION.getId();
	public final static String TCP_CLASSIFICATION_TYPE = ""+PredictorType.TCP_CLASSIFICATION.getId();
	public final static String TCP_REGRESSION_TYPE = ""+PredictorType.TCP_REGRESSION.getId();
	public final static String CVAP_CLASSIFICATION_TYPE = ""+PredictorType.VAP_CLASSIFICATION.getId();
	public final static String CVAP_REGRESSION_TYPE = ""+PredictorType.VAP_REGRESSION.getId();

	public final static String PRECOMPUTE_CLASSIFICATION = "" + ClassOrRegType.CLASSIFICATION.toString();
	public final static String PRECOMPUTE_REGRESSION = "" + ClassOrRegType.REGRESSION.toString();

	public final static String RANDOM_SAMPLING = RandomSampling.NAME; 
	public final static String RANDOM_STRATIFIED_SAMPLING = ""+RandomStratifiedSampling.ID;
	public final static String FOLDED_SAMPLING = FoldedSampling.NAME; 
	public final static String FOLDED_STRATIFIED_SAMPLING = FoldedStratifiedSampling.NAME;

	public static final boolean INCHI_AVAILABLE_ON_SYSTEM = CPSignMolProperties.isInChIAvailable();
	
	public static final String TEST_SMILES = "COc(c1)cccc1C#N";
	public static final String TEST_SMILES_2 = "OCc1ccc(cc1)Cl";

	static final SmilesParser sp = new SmilesParser(SilentChemObjectBuilder.getInstance());

	public static IAtomContainer getTestMol(){
		try{
			return sp.parseSmiles(TEST_SMILES);
		} catch(Exception e){
			return null;
		}
	}

	public static String strategy(String strat, int nrModels) {
		return strat+':'+nrModels;
	}

	public static String strategy(String strat, int nrModels, double calib) {
		return strategy(strat, nrModels)+':'+calib;
	}

	public static String getLabelsArg(List<?> labels){
		return getLabelsArg(labels, ',');
	}
	public static String getLabelsArg(List<?> labels, char delim){
		StringBuilder b = new StringBuilder();
		for (Object o : labels){
			b.append(o.toString()).append(delim);
		}
		return b.toString();
	}

	@Rule
	public final ExpectedSystemExit exit = ExpectedSystemExit.none();

	public void printOutput() {
		exit.checkAssertionAfterwards(new PrintSysOutput());
	}

	@AfterClass
	public static void teardown()throws JoranException{
		LoggerUtils.reloadLoggingConfig();
		// System.setOut(SYS_OUT);
		// System.setErr(SYS_ERR);
		TrainingsetValidator.setProductionEnv();
	}

	@Before
	public void betweenMethods() {	
		super.betweenMethods();
		try{
			LoggerUtils.reloadLoggingConfig();
		} catch (JoranException e){
			Assert.fail(e.getMessage());
		}
	}
	
	public static void mockMain(List<String> args) {
		AnsiConsole.systemInstall(); // will lead to calling "install" multiple times, but will the 'installed' counter will make sure the heavy checking is not performed multiple times

		List<String> argsNoEmpty = new ArrayList<>();
		for (String arg: args) {
			if (arg!=null && !arg.isEmpty())
				argsNoEmpty.add(arg);
		}
		CPSignApp.main(argsNoEmpty.toArray(new String[]{}));
	}
	
	public static void mockMain(String... args) {
		AnsiConsole.systemInstall(); // will lead to calling "install" multiple times, but will the 'installed' counter will make sure the heavy checking is not performed multiple times

		List<String> argsNoEmpty = new ArrayList<>();
		for (String arg: args) {
			if (arg!=null && !arg.isEmpty())
				argsNoEmpty.add(arg);
		}
		CPSignApp.main(argsNoEmpty.toArray(new String[]{}));
	}

	public static File generateEncryptionKeyFile(){
		try {
			File encryptKeyFile = TestUtils.createTempFile("encryption", ".key");
			mockMain(GenerateEncryptionKey.CMD_NAME,"-l","16","-f",encryptKeyFile.getAbsolutePath());
			return encryptKeyFile;
		} catch (Exception e){
			Assert.fail("Could not create encryption file: "+ e.getMessage());
			return null;
		}
	}

	public String generateEncryptionKey(){
		try {
			mockMain(GenerateEncryptionKey.CMD_NAME,"-l","16","-q");
			return systemOutRule.getLog();
		} catch (Exception e){
			Assert.fail("Could not create encryption key: "+ e.getMessage());
			return null;
		}
	}

	public class AssertSysErrContainsString implements Assertion {
		private String[] toLookFor;
		public AssertSysErrContainsString(String lookFor) {
			this.toLookFor = new String[] {lookFor};
		}

		public AssertSysErrContainsString(String... toLookFor) {
			this.toLookFor = toLookFor.clone();
		}

		@Override
		public void checkAssertion() throws Exception {
			String log = Help.Ansi.OFF.string(systemErrRule.getLog().toLowerCase());
			for(String text: toLookFor)
				Assert.assertTrue(log.contains(text.toLowerCase()));
		}

	}
	
	public class SetTestEnv implements Assertion {

		@Override
		public void checkAssertion() throws Exception {
			TrainingsetValidator.setTestingEnv();
		}
		
	}

	public JsonObject getJSONFromLog(String log) throws JsonException {
		String json = log.substring(log.indexOf("\n{")+1, log.lastIndexOf("}\n")+2);
//		SYS_ERR.println("should be valid JSON:\n" + json + "\n-----");
		return (JsonObject) Jsoner.deserialize(json);
	}
	
	public JsonArray getJSONArrayFromLog(String log) throws JsonException {
		String json = log.substring(log.indexOf("\n[\n")+1, log.lastIndexOf("\n]\n")+2);
		return (JsonArray) Jsoner.deserialize(json);
	}

	public class PrintSysOutput implements Assertion {
		final private boolean print;

		public PrintSysOutput(){print=true;}

		public PrintSysOutput(boolean print) {
			this.print = print;
		}
		@Override
		public void checkAssertion() throws Exception {
			if (print){
				printLogs();
			}
		}
	}

//	@SuppressWarnings("unchecked")
	public static void assertJSONPred(String jsonString, boolean isClassification, boolean gradient, boolean inchi, List<Double> confs, List<Double> distances){
		JsonObject json = null;
		try{
			json = (JsonObject) Jsoner.deserialize(jsonString); //parser.parse(jsonString);
		} catch (JsonException e){
			e.printStackTrace(SYS_ERR);
			Assert.fail("JSON-output not parsable");
		}
		assertJSONPred(json, isClassification, gradient, inchi, confs, distances);
	}


	@SuppressWarnings("unchecked")
	public static void assertJSONPred(JsonObject json, boolean isClassification, boolean gradient, boolean inchi, List<Double> confs, List<Double> distances) {
		//		JSONParser parser = new JSONParser();


		// MOL SECTION
		Assert.assertTrue(json.containsKey(JSON.MOLECULE_SECTION_KEY));
		JsonObject molSection = (JsonObject) json.get(JSON.MOLECULE_SECTION_KEY);
		Assert.assertNotNull(OutputNamingSettings.getCustomSmilesProperty(molSection.keySet()));
		if(inchi){
			Assert.assertTrue(molSection.containsKey(StringUtils.toCamelCase(JSON.INCHI_KEY)));
			Assert.assertTrue(molSection.containsKey(StringUtils.toCamelCase(JSON.INCHIKEY_KEY)));
		}

		// GRADIENT SECTION
		if(gradient) {
			Assert.assertTrue(json.containsKey(JSON.GRADIENT_RESULTS_SECTION_KEY));
			JsonObject grad = (JsonObject) json.get(JSON.GRADIENT_RESULTS_SECTION_KEY);
			grad.containsKey(JSON.ATOM_VALS_KEY);
			grad.containsKey(JSON.SIGNIFICANT_SIGNATURE_KEY);
			grad.containsKey(JSON.SIGNIFICANT_SIGNATURE_HEIGHT_KEY);
		} else {
			Assert.assertTrue(! json.containsKey(JSON.GRADIENT_RESULTS_SECTION_KEY));
		}

		// PREDICTION SECTION
		Assert.assertTrue(json.containsKey(JSON.PREDICTING_SECTION_KEY));

		if (isClassification) {
			// Result should be a map of Label -> value
			Map<Object, Object> predictionsSection = (Map<Object, Object>) json.get(JSON.PREDICTING_SECTION_KEY);
			Map<Object, Object> pValsMapping = (Map<Object, Object>) predictionsSection.get(JSON.CLASS_PVALS_KEY);
			Assert.assertEquals("There should be 2 labels", 2 ,pValsMapping.size());
			for(Entry<Object, Object> mapping: pValsMapping.entrySet()){
				Assert.assertTrue(mapping.getKey() instanceof String);
				Assert.assertTrue("value should of class Double, was of class: " + mapping.getValue().getClass(),mapping.getValue() instanceof Number);
				Assert.assertTrue("each p-value shoud be [0,1]",0<=((Number)mapping.getValue()).doubleValue() && ((Number)mapping.getValue()).doubleValue() <= 1);
			}

			if(confs!=null && !confs.isEmpty()){

			}
		} else {
			JsonArray intervalsArray = (JsonArray)((JsonObject)json.get(JSON.PREDICTING_SECTION_KEY)).get(JSON.REG_INTERVALS_SECTION_KEY);

			// midpoint is confidence-independent and should be outside of the array
			Assert.assertTrue(((JsonObject)json.get(JSON.PREDICTING_SECTION_KEY)).containsKey(JSON.REG_MIDPOINT_KEY));

			for(int i=0; i<intervalsArray.size(); i++){
				JsonObject item = (JsonObject) intervalsArray.get(i);
				Assert.assertTrue(item.containsKey(JSON.REG_WIDTH_KEY));
				Assert.assertTrue(item.containsKey(JSON.REG_RANGE_UPPER_KEY));
				Assert.assertTrue(item.containsKey(JSON.REG_RANGE_LOWER_CAPPED_KEY));
				Assert.assertTrue(item.containsKey(JSON.REG_RANGE_LOWER_KEY));
				Assert.assertTrue(item.containsKey(JSON.REG_RANGE_LOWER_CAPPED_KEY));
			}

			if(confs!=null && !confs.isEmpty()){

			}
			if(distances!=null && !distances.isEmpty()){

			}
		}		
	}

	public void expectExit() {
		expectExit(ExitStatus.SUCCESS);
	}

	public void expectExit(ExitStatus status) {
		exit.expectSystemExitWithStatus(status.code);
	}
	
	public static String[] append(String[] first, String... args) {
		String[] res = new String[first.length+args.length];
		// Copy first part
		System.arraycopy(first, 0, res, 0, first.length);
		// Copy the second part
		System.arraycopy(args, 0, res, first.length, args.length);
		
		return res;
	}

}

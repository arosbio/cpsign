/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app.cli;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.commons.config.ImplementationConfig;
import com.arosbio.commons.config.IntegerConfig;
import com.arosbio.commons.config.StringConfig;
import com.arosbio.commons.mixins.HasID;
import com.arosbio.commons.mixins.Named;
import com.arosbio.cpsign.app.CLIBaseTest;
import com.arosbio.cpsign.app.CrossValidate;
import com.arosbio.cpsign.app.ExplainArgument.TuneParamsInfo;
import com.arosbio.cpsign.app.Precompute;
import com.arosbio.cpsign.app.PrecomputedDatasets.Classification;
import com.arosbio.cpsign.app.Train;
import com.arosbio.cpsign.app.params.converters.ListOrRangeConverter;
import com.arosbio.cpsign.app.params.converters.MLAlgorithmConverter;
import com.arosbio.cpsign.app.params.mixins.ClassificationLabelsMixin;
import com.arosbio.cpsign.app.params.mixins.PredictorMixinClasses;
import com.arosbio.cpsign.app.params.mixins.TestingStrategyMixin;
import com.arosbio.cpsign.app.params.mixins.TransformerMixin.TransformerParamConsumer;
import com.arosbio.cpsign.app.params.mixins.TuneGridMixin;
import com.arosbio.cpsign.app.utils.MultiArgumentSplitter;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.cpsign.app.utils.TuneUtils;
import com.arosbio.data.transform.ColumnTransformer;
import com.arosbio.data.transform.Transformer;
import com.arosbio.data.transform.impute.SingleFeatureImputer;
import com.arosbio.data.transform.scale.Standardizer;
import com.arosbio.ml.algorithms.MLAlgorithm;
import com.arosbio.ml.algorithms.impl.DefaultMLParameterSettings;
import com.arosbio.ml.algorithms.impl.LibSvm.KernelType;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.sampling.SamplingStrategy;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.ml.testing.LOOCV;
import com.arosbio.ml.testing.RandomSplit;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.utils.TestUtils;
import com.google.common.collect.Range;

import picocli.CommandLine;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.TypeConversionException;

@Category(CLITest.class)
public class TestConverters extends CLIBaseTest {

	static CmpdData amesMini = TestResources.Cls.getAMES_10();

	static void parse(Object o, String... args) {
		CommandLine cmd = new CommandLine(o);
		cmd.setTrimQuotes(true);
		cmd.parseArgs(args);
	}

	@Test
	public void testConvertListWithBase() {
		ListOrRangeConverter conv = new ListOrRangeConverter();
		List<Double> list = conv.convert("b=2:1:2");
		Assert.assertEquals(Math.pow(2, 1), list.get(0),0.00001);
		Assert.assertEquals(Math.pow(2, 2), list.get(1),0.00001);

		list = conv.convert("BASE=2:1:2:0.5");
		Assert.assertEquals(Math.pow(2, 1), list.get(0),0.00001);
		Assert.assertEquals(Math.pow(2, 1.5), list.get(1),0.00001);
		Assert.assertEquals(Math.pow(2, 2), list.get(2),0.00001);


		list = conv.convert("1:2:0.5:b4");
		Assert.assertEquals(Math.pow(4, 1), list.get(0),0.00001);
		Assert.assertEquals(Math.pow(4, 1.5), list.get(1),0.00001);
		Assert.assertEquals(Math.pow(4, 2), list.get(2),0.00001);

		//		SYS_ERR.println(conv.convert("0.01:0.99:0.01").size());
	}

	//Parameter of type GAMMA could not converted properly: Argument "0.5, 1.0, 2.0" could not be converted into a list
	//Unused parameters given to parameter --grid: GAMMA


	@Test
	public void testListConv() {
		TestClass t = new TestClass();
		parse(t, "-co", "1,2,5,1:5:b=2");
		//		SYS_ERR.println(t.confidences);
		Assert.assertTrue(t.confidences.containsAll(Arrays.asList(1.,2.,5.)));
		Assert.assertTrue("3 explicit values + a range-type thing. ",t.confidences.size() >4);
		t = new TestClass();
		parse(t, "-co", "0.5, 1.0, 2.0");
		Assert.assertEquals(Arrays.asList(0.5,1.0,2.0), t.confidences);
	}

	@Test
	public void testParseList() {
		List<Double> list = new ListOrRangeConverter().convert(".05:0.95:0.05");
		Assert.assertEquals(list.get(0), 0.05, 0.000001);
		Assert.assertEquals(list.get(list.size()-1), 0.95, 0.000001);
		Assert.assertEquals(19,list.size());
	}

	@Test
	public void testTransformer() {
		Transformer t = TransformerParamConsumer.convertSingleArg("single_feat_imp:median:colminindex=10:colmaxindex=200");
		Assert.assertTrue(t instanceof SingleFeatureImputer);

		try {
			Transformer t2 = TransformerParamConsumer.convertSingleArg("RobustScaler:maxCol=15");
			SYS_OUT.println(t2);
			Assert.fail("Miss-spelled sub-parameter");
		} catch (TypeConversionException e) {

		}
	}

	@Test
	public void testTestSplits() throws Exception {
		TestingStrategyMixin.TestingStrategyConverter conv = new TestingStrategyMixin.TestingStrategyConverter();
		KFoldCV tester = (KFoldCV) conv.convert("k-fold:k=7:stratify=true");
		Assert.assertTrue(tester.isStratified());
		Assert.assertEquals(7, tester.getNumFolds());

		RandomSplit rs = (RandomSplit) conv.convert("random-split:shuffle=F");
		Assert.assertFalse(rs.usesShuffle());
		Assert.assertFalse(rs.isStratified());

		Assert.assertTrue(conv.convert("loo-cv") instanceof LOOCV);
	}

	@Test
	public void testGetCorrectTransformer() {
		Transformer t = TransformerParamConsumer.convertSingleArg("standardizer:col_max_index=20");

		Assert.assertTrue(t instanceof Standardizer);
		Standardizer st = (Standardizer) t;
		List<Integer> cols = st.getColumns().getColumns(10000);
		Assert.assertEquals(20, (int) Collections.max(cols));

		// printLogs();
		//		SYS_ERR.println(st.getColumns());
		//		SYS_ERR.println(st.getColumns().getColumns(100));
	}

	@Test
	public void testSamplingStrat() {
		RandomSampling rs = (RandomSampling) new PredictorMixinClasses.SamplingStrategyConverter().convert("random:10:.2");
		Assert.assertEquals(10, rs.getNumSamples());
		Assert.assertEquals(0.2, rs.getCalibrationRatio(),0.0001);
	}

	@Test
	public void testMLArg() throws Exception {
		MLAlgorithm alg = new MLAlgorithmConverter().convert("Epsilon:kernel=SIGMOID");
		Assert.assertTrue(alg instanceof EpsilonSVR);
		EpsilonSVR svr = (EpsilonSVR) alg;
		Assert.assertEquals(svr.getKernel(), KernelType.SIGMOID);


		MLAlgorithm alg2 = new MLAlgorithmConverter().convert("Epsilon:kernel=1");
		Assert.assertTrue(alg2 instanceof EpsilonSVR);
		svr = (EpsilonSVR) alg2;
		Assert.assertEquals(svr.getKernel(), KernelType.forID(1));
	}

	@Test
	public void tesetColumnSelection() throws Exception {
		ColumnTransformer t = (ColumnTransformer) TransformerParamConsumer.convertSingleArg("MinMaxScale:colMinIndex=50");
		//		SYS_OUT.println(t.getColumns());
		List<Integer> expected = Arrays.asList(50,51);
		Assert.assertEquals(expected,t.getColumns().getColumns(51));
		//		SYS_ERR.println("Fixed first");
		t = (ColumnTransformer) TransformerParamConsumer.convertSingleArg("ZeroMaxScale:colMaxIndex=5");
		//		SYS_OUT.println(t.getColumns());
		//		SYS_ERR.println("Converted second");
		expected = Arrays.asList(0,1,2,3,4,5);
		Assert.assertEquals(expected,t.getColumns().getColumns(51));

		//		SYS_OUT.println(t.getColumns());
	}

	//	@Test
	public void testEnumInit() {
		EnumSet<?> set = EnumSet.allOf(SignaturesDescriptor.VectorType.class);
		Iterator<?> iter = set.iterator();
		while (iter.hasNext()) {
			Object o = iter.next();
			if (o instanceof HasID) {
				SYS_ERR.println("Has ID: " + ((HasID)o).getID() + " o.string: " + o.toString());
			} else {
				SYS_ERR.println("NO ID " + o.toString());
			}
		}
	}

	@Test
	public void testTuneConvertNumbers() {
		List<Object> vals = TuneUtils.convertToParamValues("10 100 1000", DefaultMLParameterSettings.COST_CONFIG);
		Assert.assertEquals(Arrays.asList(10., 100., 1000.), vals);
		List<Object> eps = TuneUtils.convertToParamValues("B2:-10:-2:1.5", DefaultMLParameterSettings.SVR_EPSILON_CONFIG);
		Assert.assertEquals(CollectionUtils.listRange(-10, -2, 1.5, 2), eps);
	}

	@Test
	public void testTuneConvertIntegers() {
		List<Object> vals = TuneUtils.convertToParamValues("10 100 1000", 
				new IntegerConfig.Builder(Arrays.asList("conf"), 10).range( Range.atLeast(1)).build());
		Assert.assertEquals(Arrays.asList(10, 100, 1000), vals);
		List<Object> intsRange = TuneUtils.convertToParamValues("-10:-2:2", 
				new IntegerConfig.Builder(Arrays.asList("conf"), 10).range(Range.all()).build());
		Assert.assertEquals(Arrays.asList(-10,-8,-6,-4,-2), intsRange);

	}

	@Test
	public void testTuneConvertImpls() {
		List<Object> vals = TuneUtils.convertToParamValues("Random Random:numSamples=2:calibRatio=.3", 
				new ImplementationConfig.Builder<>(Arrays.asList("conf"), SamplingStrategy.class).build());
		Assert.assertEquals(Arrays.asList(new RandomSampling(), new RandomSampling(2, .3)),vals);
	}

	@Test
	public void testTuneConvertEnum() {
		ConfigParameter kernelP = null;
		for (ConfigParameter p : new C_SVC().getConfigParameters()) {
			if (p.getNames().get(0).equals("kernel"))
				kernelP = p;
		}
		// All of a type
		List<Object> valsOrdinal = TuneUtils.convertToParamValues(" 0 2 1", kernelP);
		Assert.assertEquals(Arrays.asList(KernelType.LINEAR, KernelType.RBF, KernelType.POLY),valsOrdinal);
		//		SYS_ERR.println(valsOrdinal);

		//		LoggerUtils.addStreamAppenderToRootLogger(SYS_OUT);
		List<Object> valsString = TuneUtils.convertToParamValues("SIGMOID,POLY,RBF", kernelP);
		Assert.assertEquals(Arrays.asList(KernelType.SIGMOID, KernelType.POLY, KernelType.RBF),valsString);
	}

	@Test
	public void testTuneConfigString() {
		List<Object> vals = TuneUtils.convertToParamValues("'1,2,3,4','5,6,7'", new StringConfig.Builder(Arrays.asList("bla"),null).build());
		Assert.assertEquals(Arrays.asList("1,2,3,4","5,6,7"), vals);
		//		SYS_ERR.println(vals);
		List<Object> vals2 = TuneUtils.convertToParamValues("\"1,2,3,4\",\"5,6,7\"", new StringConfig.Builder(Arrays.asList("bla"), null).build());
		Assert.assertEquals(Arrays.asList("1,2,3,4","5,6,7"), vals2);
	}
	// COPIED FROM PICOCLI - TO CHECK HOW IT WORKS
	public static String smartUnquote(String value) {
		String unquoted = unquote(value);
		if (unquoted == value) { return value; }
		StringBuilder result = new StringBuilder();
		int slashCount = 0;
		for (int ch, i = 0; i < unquoted.length(); i += Character.charCount(ch)) {
			ch = unquoted.codePointAt(i);
			switch (ch) {
			case '\\':
				slashCount++;
				break;
			case '\"':
				// if the unquoted value contains an unescaped quote, we should not convert escaped quotes into quotes
				if (slashCount == 0) { return value; }
				slashCount = 0;
				break;
			default: slashCount = 0; break;
			}
			if ((slashCount & 1) == 0) { result.appendCodePoint(ch); }
		}
		return result.toString();
	}
	private static String unquote(String value) {
		if (value == null) { return null; }
		return (value.length() > 1 && value.startsWith("\"") && value.endsWith("\""))
				? value.substring(1, value.length() - 1)
						: value;
	}
	//	@Test // Just checking the functionality of Picocli un-quoting
	public void testSmartUnquote() {
		SYS_ERR.println(smartUnquote("some test"));
		SYS_ERR.println(smartUnquote("'some test','other text'"));
		SYS_ERR.println(smartUnquote("1,2,3"));
		SYS_ERR.println(smartUnquote("\"1,2,3\""));
	}

	@Test
	public void testInputSplitter() {
		List<String> texts = MultiArgumentSplitter.split("'some text' 'divided by some spaces','and commas as well as ; semi-colons (;)'");
		//		SYS_OUT.println(texts);
		Assert.assertEquals("Semicolons no longer allowed for splitting between arguments",3, texts.size());

		List<String> splits = MultiArgumentSplitter.split("'0.5, 1.0, 2.0'");
		Assert.assertEquals(3, splits.size());
		Assert.assertEquals(Arrays.asList("0.5", "1.0", "2.0"), splits);
	}


	private static class TestClass implements Callable<Integer>{

		public List<Double> confidences;

		@Option(
				names = { "-co", "--confidences" }, 
				description = "Confidences for predictions, either an explicit list of numbers (e.g. '0.5,0.7,0.9' or '0.5 0.7 0.9'), "
						+ "or using a start:stop:step syntax (e.g. 0.5:1:0.05 generates all numbers between 0.5 to 1.0 with 0.05 increments). "
						+ "All numbers must be in the range [0..1]",
						split = ParameterUtils.SPLIT_WS_COMMA_REGEXP,
						paramLabel = ArgumentType.NUMBER,
						arity = ParameterUtils.LIST_TYPE_ARITY
				)
		public void setConfidences(List<String> input){
			if (confidences == null)
				confidences = new ArrayList<>();

			List<Double> confs = new ArrayList<>();
			//			SYS_ERR.println("INPUT: " + input);
			for (String t : input) {
				//				if (t == null || t.trim().isEmpty())
				//					continue;
				confs.addAll(new ListOrRangeConverter().convert(t));
			}

			//			if (confs != null && ! confs.isEmpty()){
			//				for (Double conf: confs)
			//					if (conf< 0 || conf>1)
			//						throw new TypeConversionException("Confidence values must be in the range [0..1], got offending value: " + conf);
			//			}
			confidences.addAll(confs);
		}

		@Mixin
		public ClassificationLabelsMixin labels;

		@Option(names = {"-g", "--grid"},
				description = "Specify which parameters that should be part of the parameter grid, specified using syntax -g<KEY>=<VALUE> or --grid=<KEY>=<VALUE>, e.g., -g=COST=1,10,100 or --grid=Gamma=b2:-8:-1:2. Run "+
						ParameterUtils.RUN_EXPLAIN_ANSI_ON+"explain " + TuneParamsInfo.SUB_NAME + ParameterUtils.ANSI_OFF+" for further details.",
						paramLabel = ArgumentType.TUNE_KEY_VALUE,
						mapFallbackValue = "default",
						arity = "1..*",
						required = false)
		private LinkedHashMap<String,String> paramGrid;

		@Option(
				names = { "-vp", "--validation-property" },
				description = "Endpoint that should be validated on. Can be omitted if identical to the one in used in training.%n"+
						"(SDFile) Name of field with correct label, should match a property in the predict file%n"+
						"(CSV) Name of the column to use for validation, should match header of that column%n"+
						"(JSON) JSON-key for the property with the true response value",
						paramLabel = ArgumentType.TEXT)
		private String validationEndpoint;


		@Override
		public Integer call() throws Exception {
			return null;
		}

	}


	//	@Test
	public void testPicocliNativeSplitting() {
		TestClass test = new TestClass();

		CommandLine cml = new CommandLine(test);
		cml.setTrimQuotes(true);
		cml.parseArgs("-co", "0.1, 0.2, 0.5",
				"--labels", "\"Type A\", \"Type B\"",
				"-gkey=value key2=2",
				"-g","c=1,2,5,1:5:b=2",
				"-g","updater=Sgd;0.1,Sgd;0.01",
				"-vp=PROPERTY 1");
		for (double c : test.confidences)
			SYS_ERR.println(c);

		SYS_ERR.println("labels: " +test.labels.labels);

		SYS_ERR.println("grid: " +test.paramGrid);

		SYS_ERR.println("vp: " + test.validationEndpoint);
	}

	@Test
	public void testBadConfigSubParams() throws Exception {
		File tmpOut =TestUtils.createTempFile("some-file",".jar");
		SecurityManager defaultManger = System.getSecurityManager();
		NoExitSecurityManager myManger = new NoExitSecurityManager();
		System.setSecurityManager(myManger);

		// Scorers

		// Bad "Lin" doesn't match anything
		doBadConfigTest(new Train(),
				Arrays.asList("Lin","--scorer", "invalid", "value"),
				Arrays.asList(
						"-ds", Classification.getAmes123().getAbsolutePath(),
						"-mo", tmpOut.getAbsolutePath(),
						"--scorer", "Lin"));
		// Bad "Linear" matches 2 ones
		doBadConfigTest(new Train(),
				Arrays.asList("Linear","--scorer", "invalid", "value", "LinearSVR", "LinearSVC"),
				Arrays.asList(
						"-ds", Classification.getAmes123().getAbsolutePath(),
						"-mo", tmpOut.getAbsolutePath(),
						"--scorer", "Linear"));
		// With bad sub-argument to LinearSVC
		doBadConfigTest(new Train(),
				Arrays.asList("badArg","--scorer", "invalid", "value", "LinearSVC"),
				Arrays.asList(
						"-ds", Classification.getAmes123().getAbsolutePath(),
						"-mo", tmpOut.getAbsolutePath(),
						"--scorer", "LinearSVC:badArg=10"));
		doBadConfigTest(new Train(),
				Arrays.asList("Lin","--scorer", "invalid",  "parameter", "value", "C","<=", "must", "not"),
				Arrays.asList(
						"-ds", Classification.getAmes123().getAbsolutePath(),
						"-mo", tmpOut.getAbsolutePath(),
						"--scorer", "LinearSVC:C=-10"));
		// NCM
		doBadConfigTest(new Train(),
				Arrays.asList("Normalized","--nonconf-measure","beta", "invalid", "parameter", "value", "-1", "must"),
				Arrays.asList(
						"-ds", Classification.getAmes123().getAbsolutePath(),
						"-mo", tmpOut.getAbsolutePath(),
						"--ncm", "Normalized:beta=-1"));
		// Sampling Strategy
		doBadConfigTest(new Train(),
				Arrays.asList("Random","--sampling-strategy","invalid", "parameter", "value"),
				Arrays.asList(
						"-ds", Classification.getAmes123().getAbsolutePath(),
						"-mo", tmpOut.getAbsolutePath(),
						"--sampling-strategy", "Random:calibRatio=-1"));

		// Test strategy - K-fold with bad input
		doBadConfigTest(new CrossValidate(),
				Arrays.asList("KFoldCV","num", "folds", "invalid", "parameter", "value", "-1"),
				Arrays.asList(
						"-ds", Classification.getAmes123().getAbsolutePath(),
						"--test-strategy", "KFoldCV:numSplits=-1"));
		// Test strategy - K-fold with invalid parameter name
		doBadConfigTest(new CrossValidate(),
				Arrays.asList("KFoldCV","param1", "invalid", "parameter", "value"),
				Arrays.asList(
						"-ds", Classification.getAmes123().getAbsolutePath(),
						"--test-strategy", "KFoldCV:param1=5"));

//		printLogs();

		System.setSecurityManager(defaultManger);

	}

	@Test
	public void testDescriptorConfig() throws Exception {
		File tmpOut =TestUtils.createTempFile("some-file",".jar");
		SecurityManager defaultManger = System.getSecurityManager();
		NoExitSecurityManager myManger = new NoExitSecurityManager();
		System.setSecurityManager(myManger);
//		LoggerUtils.setDebugMode(SYS_OUT);
		doBadConfigTest(new Precompute(),
				Arrays.asList("Signatures","startHeight", "invalid", "parameter"),
				Arrays.asList(
						"-td", amesMini.format(), amesMini.uri().toString(), // AmesBinaryClass.FILE_FORMAT, AmesBinaryClass.MINI_FILE_PATH,
						"-mo", tmpOut.getAbsolutePath(),
						"-d", "AminoAcidCountDescriptor", "Signatures:startHeight=-1"));
		// Same thing but re-arranging the input order
		doBadConfigTest(new Precompute(),
				Arrays.asList("Signatures","startHeight", "invalid", "parameter"),
				Arrays.asList(
						"-td", amesMini.format(), amesMini.uri().toString(), //AmesBinaryClass.FILE_FORMAT, AmesBinaryClass.MINI_FILE_PATH,
						"-d", "AminoAcidCountDescriptor",
						"-mo", tmpOut.getAbsolutePath(),
						"-d", "Signatures:startHeight=-1"));
//		printLogs();

		System.setSecurityManager(defaultManger);
	}
	
	@Test
	public void testTransformersConfig() throws Exception {
		File tmpOut = TestUtils.createTempFile("some-file",".jar");
		SecurityManager defaultManger = System.getSecurityManager();
		NoExitSecurityManager myManger = new NoExitSecurityManager();
		System.setSecurityManager(myManger);
//		LoggerUtils.setDebugMode(SYS_OUT);
		doBadConfigTest(new Precompute(),
				Arrays.asList("RobustScaler","upperQuantile", "invalid", "parameter"),
				Arrays.asList(
						"-td", amesMini.format(), amesMini.uri().toString(), //AmesBinaryClass.FILE_FORMAT, AmesBinaryClass.MINI_FILE_PATH,
						"-mo", tmpOut.getAbsolutePath(),
						"--transformations", "MinMaxScaler", "RobustScaler:upperQuantile=110"));
//		printLogs();
		System.setSecurityManager(defaultManger);
	}
	
	@Test
	public void testChemFileConfigSDF() throws Exception {
		File tmpOut = TestUtils.createTempFile("some-file",".jar");
		SecurityManager defaultManger = System.getSecurityManager();
		NoExitSecurityManager myManger = new NoExitSecurityManager();
		System.setSecurityManager(myManger);
		
		// Giving extra args when not possible - as a full list
		doBadConfigTest(new Precompute(),
				Arrays.asList("SDF", "not", "support"), //"invalid", "parameter"),
				Arrays.asList(
						"-td", amesMini.format(), "some_key:some_value", amesMini.uri().toString(), //AmesBinaryClass.MINI_FILE_PATH,
						"-mo", tmpOut.getAbsolutePath()));
		
		// Giving extra args when not possible - as a file!
		//  ---- compile as a file
		File paramsFile =TestUtils.createTempFile("params",".txt");
		FileUtils.write(paramsFile, convToParamTxt(Arrays.asList(
				"-td", amesMini.format(), "some_key:some_value", amesMini.uri().toString(), // AmesBinaryClass.MINI_FILE_PATH,
				"-mo", tmpOut.getAbsolutePath())), StandardCharsets.UTF_8);
		
//		LoggerUtils.setDebugMode(SYS_OUT);
		
		// Giving extra args when not possible
		doBadConfigTest(new Precompute(),
				Arrays.asList("SDF", "not", "support"), //"invalid", "parameter"),
				Arrays.asList("@"+paramsFile.getAbsolutePath()));
		
		System.setSecurityManager(defaultManger);
	}
	@Test
	public void testChemFileConfigCSV() throws Exception {
		File tmpOut =TestUtils.createTempFile("some-file",".jar");
		SecurityManager defaultManger = System.getSecurityManager();
		NoExitSecurityManager myManger = new NoExitSecurityManager();
		System.setSecurityManager(myManger);
		
		// invalid URI/Path (but looks correct)
		doBadConfigTest(new Precompute(),
				Arrays.asList("--train-data","non.existing.file.csv"),
				Arrays.asList(
						"--train-data", "csv", "delim=,", "non.existing.file.csv",
						"-mo", tmpOut.getAbsolutePath()));
		
		// Missing URI/Path - continue with a new flag
		doBadConfigTest(new Precompute(),
				Arrays.asList("missing", "-td","--train-data","path", "uri"), //, "parameter"),
				Arrays.asList(
						"-td", "csv", "delim=,", // "file.csv", - no URI/path!
						"-mo", tmpOut.getAbsolutePath()));
		
		// Missing URI/Path - end of args
		doBadConfigTest(new Precompute(),
				Arrays.asList("missing","-td", "path", "uri"), //, "parameter"),
				Arrays.asList(
						"-mo", tmpOut.getAbsolutePath(),
						"-td", "csv", "delim=," // "file.csv", - no URI/path!
						));
		
		// Invalid parameter input, for a valid sub-parameter
		doBadConfigTest(new Precompute(),
				Arrays.asList("invalid", "parameter", "-td", "delim"), //, "parameter"),
				Arrays.asList(
						"-mo", tmpOut.getAbsolutePath(),
						"-td", "csv", "delim=5621", amesMini.uri().toString() //AmesBinaryClass.MINI_FILE_PATH 
						));
		
		// Invalid sub-parameter
		doBadConfigTest(new Precompute(),
				Arrays.asList("invalid", "parameter", "-td", "some_param", "sub-parameter"), //, "parameter"),
				Arrays.asList(
						"-mo", tmpOut.getAbsolutePath(),
						"-td", "csv:some_param=some_value", amesMini.uri().toString() //AmesBinaryClass.MINI_FILE_PATH 
						));
		
//		printLogs();
		System.setSecurityManager(defaultManger);
	}
	
	private String convToParamTxt(List<String> args) {
		StringBuilder sb = new StringBuilder();
		for (String a : args) {
			sb.append(a).append(' ');
		}
		return sb.toString();
	}
	
	private static class NoExitSecurityManager extends SecurityManager {
		@Override
		public void checkPermission(Permission perm) {
			// allow anything.
		}
		@Override
		public void checkPermission(Permission perm, Object context) {
			// allow anything.
		}
		@Override
		public void checkExit(int status) {
			super.checkExit(status);
			switch(status) {
			case 1:
				throw new IllegalArgumentException();
			case 8:
				throw new IllegalStateException("Invalid program code.. :(");
			case 0:
			default:
				throw new SecurityException("No shutdown");
			}

		}
	}

	private void doBadConfigTest(Object program, List<String> errShouldContain, List<String> args) {
		systemErrRule.clearLog();
		systemOutRule.clearLog();
		CommandLine cml = new CommandLine(program);
		//		LoggerUtils.setDebugMode(SYS_OUT);
		try {
			cml.parseArgs(args.toArray(new String[] {}));
			Assert.fail("This should fail");
		} catch (Exception e) {
//			e.printStackTrace();
			// System.err.println("Message: " + e.getMessage());
			TestUtils.assertTextContainsIgnoreCase(e.getMessage(),
					errShouldContain.toArray(new String[] {}));
		}

		// Use the train command 
		clearLogs();
		List<String> argsList = new ArrayList<>(args);
		argsList.add(0, ((Named) program).getName());
		try {
			mockMain(argsList);
			Assert.fail("This should fail!");
		} catch (IllegalArgumentException e) {
			// This is what we expect
			TestUtils.assertTextContainsIgnoreCase(systemErrRule.getLog(),
					errShouldContain.toArray(new String[] {}));
		}
	}

	public static class TestGridSyntax implements Callable<Integer> {

		@Mixin
		public TuneGridMixin gridMixin;

		@Override
		public Integer call() throws Exception {
			return null;
		}

	}

	@Test
	public void testDifferentGridSyntax() throws Exception {
		TestGridSyntax test = new TestGridSyntax();

		CommandLine cml = new CommandLine(test);
		cml.setTrimQuotes(true);
		cml.parseArgs(
				"-g","updater=Sgd;0.1,Sgd;0.01",
				"--grid", "EPSILON=0.01,0.001,0.0001",
				"COST=base10:-4:7:0.5",
				"--grid=GAMMA=base=10:-24:-3:2",
				"some other argument"
				);
		System.err.println(test.gridMixin.paramGrid);
		Map<String,List<?>> grid = TuneUtils.setupParamGrid(new C_SVC(), test.gridMixin.paramGrid);
		Set<String> lcParamNames = grid.keySet().stream().map(String::toLowerCase).collect(Collectors.toSet());
		Assert.assertTrue(lcParamNames.contains("epsilon"));
		Assert.assertTrue(lcParamNames.contains("cost"));
		Assert.assertTrue(lcParamNames.contains("gamma"));
	}

}

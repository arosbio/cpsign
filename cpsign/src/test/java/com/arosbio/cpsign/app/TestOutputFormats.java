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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.io.iterator.IteratingSDFReader;
import org.openscience.cdk.silent.SilentChemObjectBuilder;

import com.arosbio.chem.io.in.ChemFileIterator;
import com.arosbio.chem.io.in.SDFReader;
import com.arosbio.commons.StringUtils;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.cpsign.app.PrecomputedDatasets.Classification;
import com.arosbio.cpsign.app.params.CLIParameters;
import com.arosbio.cpsign.app.params.CLIParameters.ChemOutputType;
import com.arosbio.cpsign.out.OutputNamingSettings;
import com.arosbio.cpsign.out.OutputNamingSettings.JSON;
import com.arosbio.cpsign.out.OutputNamingSettings.PredictionOutput;
import com.arosbio.io.StreamUtils;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.ChemTestUtils;
import com.arosbio.testutils.TestChemDataLoader.PreTrainedModels;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

@Category(CLITest.class)
public class TestOutputFormats extends CLIBaseTest {

	static final CSVCmpdData predictfile_smiles = TestResources.Reg.getSolubility_10();
	static final CmpdData ames10 = TestResources.Cls.getAMES_10();
	static final int nrModels=3;


	static final boolean GENERATE_INCHI = INCHI_AVAILABLE_ON_SYSTEM;

	static final String modelFileACP_reg = PreTrainedModels.ACP_REG_LIBSVM.toString(); 
	static final String modelFileACP_class = PreTrainedModels.ACP_CLF_LIBLINEAR.toString();


	private List<String> getPredictionOutputFromFullLog(String log){
		List<String> plainPredictionLines = new ArrayList<>();
		boolean inPredictionPart=false;
		String[] lines = log.split("\n");
		for(String line: lines){
			if(inPredictionPart){
				if (line.contains("Successfully")){
					// we've reached the end of prediction-lines
					// need to double-check the last line in case it was simply a blank and that case remove it
					if (plainPredictionLines.get(plainPredictionLines.size()-1).isBlank())
						plainPredictionLines.remove(plainPredictionLines.size()-1);
					break; 
				}
				plainPredictionLines.add(line);
			} else if (line.contains("predictions")){
				// Next line will be the header
				inPredictionPart=true;
			}
		}
		return plainPredictionLines;
	}

	private String getPredictionOutputFromFullLogStr(String log){
		String rmStart = log.split("predictions")[1].split("\n",2)[1];
		return rmStart.split("\nSuccessfully")[0];
	}

	@Test
	public void TestSignificantSignatureOutputREG() throws Exception {
		List<Double> confs = Arrays.asList(0.1, 0.4, 0.9);

		// Predict (JSON) to System.out
		mockMain(
				Predict.CMD_NAME,
				"-m", modelFileACP_reg,
				"--smiles",TEST_SMILES,
				"-p", predictfile_smiles.format(), predictfile_smiles.uri().toString(),
				"--confidences", TestUtils.toString(confs, ','),
				"-cg",
				(GENERATE_INCHI?"--output-inchi":""),
				"--echo"
		);

		String outputJSON = systemOutRule.getLog();
		JsonArray jsonArr = getJSONArrayFromLog(outputJSON);

		for (Object rec : jsonArr){
			CLIBaseTest.assertJSONPred((JsonObject)rec, false, true, GENERATE_INCHI, confs, null);
		}

		// Predict (TSV) to System.out
		systemOutRule.clearLog();

		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_reg,
				"--smiles",TEST_SMILES,
				"-p", predictfile_smiles.format(), predictfile_smiles.uri().toString(),
				"--confidences", TestUtils.toString(confs, ','),
				"--output-format", ChemOutputType.TSV.name(),
				"-cg",
		});

		String outputPLAIN = systemOutRule.getLog();

		// Get the prediction-output
		List<String> plainPredictionLines = getPredictionOutputFromFullLog(outputPLAIN);

		String[] headers = plainPredictionLines.get(0).split("\t");

		int numCols = headers.length;

		for(int i=0;i<plainPredictionLines.size();i++){
			Assert.assertEquals("All lines should have the same number of columns", numCols, plainPredictionLines.get(i).split("\t").length);
		}


		// Predict (SDF) to SDF-file
		File outFileSDF = TestUtils.createTempFile("output", ".sdf");

		systemOutRule.clearLog();

		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_reg,
				"--smiles",TEST_SMILES,
				"-p", predictfile_smiles.format(), predictfile_smiles.uri().toString(),
				"--confidences", TestUtils.toString(confs,'\t'),
				"--output-format", "sdf",
				"-cg",
				"-o",outFileSDF.getAbsolutePath(),
		});

		// Make sure we do not make predictions to the std-out!
		String outputSDF2File = systemOutRule.getLog();
		Assert.assertTrue("Should not include any mol-blocks", !outputSDF2File.contains("$$"));
		Assert.assertTrue("Should not include any 'signature'", !outputSDF2File.contains("Signature"));

		try(
				FileInputStream fis = new FileInputStream(outFileSDF);
				IteratingSDFReader mols = new IteratingSDFReader(fis, SilentChemObjectBuilder.getInstance());
				){


			IAtomContainer mol;
			int numMols=0, smilesLine=2; // first line is header, second is the first mol given to --smiles
			mols.next(); // SKIP FIRST MOLECULE (which is the one given to --smiles flag)
			while(mols.hasNext()){

				mol=mols.next();
				// now we check that the result is same in PLAIN and SDF
				String[] columnsInSMILESLine = plainPredictionLines.get(smilesLine).split("\t");
				System.err.println("Number of columns in SMILES-file: " + columnsInSMILESLine.length);
				for(int i=0; i<headers.length; i++){
					if (headers[i].startsWith("\"") && headers[i].endsWith("\"")) {
						headers[i] = headers[i].substring(1,headers[i].length()-1);
					}
					// Atom values are not formatted in the same way - "atom1"=dfa vs 1=dfa (to give proper JSON)
					if (headers[i].toLowerCase().contains("atom") && headers[i].contains("values"))
						continue;
					Assert.assertEquals("Not matching in header: " + convertToSDFPropertyKey(headers[i]),
							columnsInSMILESLine[i], 
							(String) mol.getProperty(convertToSDFPropertyKey(headers[i])));
				}
				smilesLine++;
				numMols++;
			}

			Assert.assertEquals(10, numMols);
		}
	}

	private String convertToSDFPropertyKey(String normal){
		return normal.replaceAll(" ", "_").replaceAll("\\.", "_").replaceAll("=", "_").replaceAll("-", "_");
	}

	@Test
	public void writeArgs() throws Exception {
		//		printOutput();
		mockMain(new String[] {
				GenerateSignatures.CMD_NAME
		});
	}

	@Test
	public void TestACPRegression_JSON() throws Exception {
		List<Double> confs = Arrays.asList(0.1, 0.4, 0.9);
		// Predict (JSON) to System.out
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_reg,
				"--smiles",TEST_SMILES,
				"-p", predictfile_smiles.format(), predictfile_smiles.uri().toString(),
				"--confidences", TestUtils.toString(confs,' '),
				(GENERATE_INCHI?"--output-inchi":""),
				"--echo"

		});
		String jsonOutput = systemOutRule.getLog();

		JsonArray jsonArr = getJSONArrayFromLog(jsonOutput);
		Assert.assertTrue(! jsonArr.isEmpty());

		for (Object json : jsonArr) {
			Assert.assertTrue(json instanceof JsonObject);
			JsonObject oneRec = (JsonObject) json;
			CLIBaseTest.assertJSONPred(oneRec, false, false, GENERATE_INCHI, confs, null);
		}


		// Then predict (JSON) TO FILE
		File outFileJSON = TestUtils.createTempFile("output", ".json");
		systemOutRule.clearLog();
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_reg,
				"--smiles",TEST_SMILES,
				"-p", predictfile_smiles.format(), predictfile_smiles.uri().toString(),
				"--confidences", TestUtils.toString(confs,' '),
				"-o", outFileJSON.getAbsolutePath(),
				(GENERATE_INCHI?"--output-inchi":""),
		});


		String jsonLinesFromFile = IOUtils.toString(new FileInputStream(outFileJSON),STANDARD_CHARSET);
		Object fromFile = Jsoner.deserialize(jsonLinesFromFile);
		Assert.assertTrue(fromFile instanceof JsonArray);

		Assert.assertEquals(jsonArr, fromFile);

		String json2FileLog = systemOutRule.getLog();
		Assert.assertTrue("Should be no json outputted to std-out",! json2FileLog.contains("{"));

		System.out.println(jsonLinesFromFile);
		
	}

	@Test
	public void TestACPRegression_CSV() throws Exception {
		/// ======================================================================================

		// Then predict (TSV) to Sys.out
		systemOutRule.clearLog();
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_reg,
				"-p", predictfile_smiles.format(), predictfile_smiles.uri().toString(),
				"--confidences", "0.1,0.4,0.9",
				"--output-format", ChemOutputType.TSV.name()
		});
		String plainOutput = systemOutRule.getLog();

		List<String> plainLines = getPredictionOutputFromFullLog(plainOutput);
		List<String> smilesPredictFileLines = null;
		try(InputStream stream = predictfile_smiles.url().openStream()){
			smilesPredictFileLines = IOUtils.readLines(stream, STANDARD_CHARSET);
		}
			

		for(int i=1; i<smilesPredictFileLines.size(); i++){
			Assert.assertTrue("Should preserve the input from SMILES-file and just add columns after",plainLines.get(i).contains(smilesPredictFileLines.get(i)));
			Assert.assertEquals(plainLines.get(i).split("\t").length , smilesPredictFileLines.get(i).split("\t").length + 1 + 3*4 ); // adds y-hat + 3 confidences, with 4 columns each (normal and capped intervals, lower/upper bounds)
		}



		// Then predict TSV/CSV - TO FILE
		File outFileSMILES = TestUtils.createTempFile("output", ".smiles");
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_reg,
				"-p", predictfile_smiles.format(), predictfile_smiles.uri().toString(),
				"--confidences", "0.1,0.4,0.9",
				"--output-format", ChemOutputType.TSV.name(),
				"-o", outFileSMILES.getAbsolutePath(),
		});
		List<String> outputLinesSMILES = IOUtils.readLines(new FileInputStream(outFileSMILES),STANDARD_CHARSET);

		// Check that everything is matching!
		for(int i=0; i<outputLinesSMILES.size();i++){
			Assert.assertEquals(plainLines.get(i).trim(), outputLinesSMILES.get(i).trim());
		}
	}

	@Test
	public void TestACPRegression_SDF() throws Exception {

		/// ======================================================================================

		////// SDF
		// Then predict (SDF) to Sys.out

		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_reg,
				"-p", predictfile_smiles.format(), predictfile_smiles.uri().toString(),
				"--confidences", "0.1,0.4,0.9",
				"--output-format", ChemOutputType.SDF_V3000.toString()
		});
		String sdfOutput = systemOutRule.getLog();

		// Get the SDF output
		List<String> sdfLines = getPredictionOutputFromFullLog(sdfOutput);
		Assert.assertTrue(!sdfLines.isEmpty());


		// Then predict SDF - TO FILE
		File outFileSDF = TestUtils.createTempFile("output", ".sdf");
		systemOutRule.clearLog();
		systemOutRule.enableLog();
		mockMain(
				Predict.CMD_NAME,
				"-m", modelFileACP_reg,
				"-p", predictfile_smiles.format(), predictfile_smiles.uri().toString(),
				"--confidences", "0.1,0.4,0.9",
				"--output-format", ChemOutputType.SDF_V3000.toString(),
				"-o", outFileSDF.getAbsolutePath()
		);

		String sdf2FileLog = systemOutRule.getLog();
		Assert.assertTrue("no SMILES field", ! sdf2FileLog.contains("SMILES"));
		Assert.assertTrue("no mol-blocks",! sdf2FileLog.contains("$$$"));

		List<String> outputLinesSDF = IOUtils.readLines(new FileInputStream(outFileSDF), STANDARD_CHARSET);

		// Check that everything is matching!
		ChemTestUtils.assertSameSDFOutput(sdfLines, outputLinesSDF);


		// Check that the properties are set
		IteratingSDFReader mols= new IteratingSDFReader(new FileInputStream(outFileSDF), SilentChemObjectBuilder.getInstance());
		IAtomContainer mol;
		int numMols = 0;
		String smilesProperty = "canonical_smiles";
		while(mols.hasNext()){
			mol = mols.next();
			numMols++;
			if (smilesProperty == null) {
				Map<Object,Object> props = mol.getProperties();

				for (Map.Entry<Object, Object> kv : props.entrySet()) {
					if (kv.getKey().toString().toLowerCase().contains("smiles")) {
						smilesProperty = kv.getKey().toString();
						break;
					}
				}
			}

			Assert.assertNotNull(mol.getProperty(smilesProperty));
			Assert.assertNotNull(mol.getProperty(predictfile_smiles.property()));
			for (double conf : Arrays.asList(0.1,0.4,0.9)) {
				Assert.assertNotNull(mol.getProperty(convertToSDFPropertyKey(OutputNamingSettings.getPredictionIntervalLowerBoundProperty(conf))));
				Assert.assertNotNull(mol.getProperty(convertToSDFPropertyKey(OutputNamingSettings.getPredictionIntervalUpperBoundProperty(conf))));
				Assert.assertNotNull(mol.getProperty(convertToSDFPropertyKey(OutputNamingSettings.getCappedPredictionIntervalLowerBoundProperty(conf))));
				Assert.assertNotNull(mol.getProperty(convertToSDFPropertyKey(OutputNamingSettings.getCappedPredictionIntervalUpperBoundProperty(conf))));
			}
		}

		try {
			mols.close();
		} catch (Exception e) {}

		Assert.assertEquals(10, numMols);
	}

	@Test
	public void testACPRegressionPredConfidenceGivenWidth() throws Exception {

		// Predict to JSON - no need, give the same output as for just giving confidences!

		// Predict (TSV) to System.out
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_reg,
				"--smiles",TEST_SMILES,
				"-p", predictfile_smiles.format(), predictfile_smiles.uri().toString(),
				"--prediction-widths", "1.5", "2.6",
				"--output-format", "tsv",
				"--silent",
		});

		String plainOutput = systemOutRule.getLog();

		String[] smilesLines = plainOutput.split("\n");
		String line;
		for(int i=0; i<smilesLines.length;i++){
			line = smilesLines[i];
			if(i==0){
				// header line
				Assert.assertTrue(line.contains(OutputNamingSettings.getConfGivenWidthProperty(1.5)));
				Assert.assertTrue(line.contains(OutputNamingSettings.getConfGivenWidthProperty(2.6)));
				Assert.assertTrue(line.contains(PredictionOutput.REGRESSION_Y_HAT_PREDICTION_PROPERTY));
			}
			Assert.assertTrue(line.split("\t").length>=3); // smiles column, confidence and predicted value
		}

		/// SDF
		File outFileSDF = TestUtils.createTempFile("output", ".sdf");
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_reg,
				"-p", predictfile_smiles.format(), predictfile_smiles.uri().toString(),
				"--output-format", "sdf",
				"--prediction-widths", "1.5", "2.5",
				"-o", outFileSDF.getAbsolutePath(),
		});

		try(
				InputStream sdfStream = new FileInputStream(outFileSDF);
				IteratingSDFReader mols = new IteratingSDFReader(sdfStream, SilentChemObjectBuilder.getInstance());
				){
			IAtomContainer mol;
			int numMols =0;
			while(mols.hasNext()){
				mol = mols.next();
				Assert.assertNotNull(mol.getProperty(convertToSDFPropertyKey(OutputNamingSettings.getConfGivenWidthProperty(1.5))));
				Assert.assertNotNull(mol.getProperty(convertToSDFPropertyKey(OutputNamingSettings.getConfGivenWidthProperty(2.5))));
				Assert.assertNotNull(mol.getProperty(convertToSDFPropertyKey(PredictionOutput.REGRESSION_Y_HAT_PREDICTION_PROPERTY)));
				numMols++;
			}
			Assert.assertEquals(10, numMols);
		}

	}

	@Test
	public void TestACPClassification_JSON() throws Exception {
		List<Double> confs = Arrays.asList(0.7);
		// JSON

		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_class,
				"--smiles",TEST_SMILES,
				"-cg",
				"-co",TestUtils.toString(confs, ','),
				(GENERATE_INCHI?"--output-inchi":""),
		});

		JsonArray jsonArr = getJSONArrayFromLog(systemOutRule.getLog());
		Assert.assertEquals(1, jsonArr.size());
		for (Object line: jsonArr){
			CLIBaseTest.assertJSONPred((JsonObject)line, true, true, GENERATE_INCHI, confs, null);
		}

		// JSON to FILE
		File outputFile = TestUtils.createTempFile("res", ".json");
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_class,
				"--smiles",TEST_SMILES,
				"-cg",
				"-co",TestUtils.toString(confs, ','),
				(GENERATE_INCHI?"--output-inchi":""),
				"--output",outputFile.getAbsolutePath(),
		});

		JsonArray jsonArr2 = (JsonArray) Jsoner.deserialize(IOUtils.toString(new FileInputStream(outputFile),STANDARD_CHARSET));

		Assert.assertEquals(jsonArr.size(), jsonArr2.size());
		Assert.assertEquals(jsonArr, jsonArr2);

	}

	@Test
	public void TestACPClassification_CSV() throws Exception {
		List<Double> confs = Arrays.asList(0.7, 0.8);

		// Then predict PLAIN/SMILES OUTPUT

		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_class,
				"--smiles",TEST_SMILES,
				"--output-format", ChemOutputType.TSV.name(),
				"-co", TestUtils.toString(confs, ' '),
				(GENERATE_INCHI?"--output-inchi":""),
				"--silent",
		});
		String plainOutput = systemOutRule.getLog();

		String[] lines = plainOutput.split("\n");
		String header = lines[0];
		int numHeaderFields = header.split("\t").length;
		Assert.assertEquals(" smiles+pred0.7, pred0.8, 2x p-values + (potentially 2 inchi-fields)",5+(GENERATE_INCHI?2:0), numHeaderFields);

		String pvalProperty = OutputNamingSettings.getPvalueForLabelProperty("");
		pvalProperty = pvalProperty.substring(0, pvalProperty.length()-1);
		Assert.assertTrue(header.contains("SMILES"));
		Assert.assertTrue(header.contains(pvalProperty));
		Assert.assertTrue(header.contains(OutputNamingSettings.getPredictedLabelsProperty(0.7)));
		Assert.assertTrue(header.contains(OutputNamingSettings.getPredictedLabelsProperty(0.8)));
		if(GENERATE_INCHI){
			Assert.assertTrue(header.contains(JSON.INCHI_KEY));
			Assert.assertTrue(header.contains(JSON.INCHIKEY_KEY));
		}

		Assert.assertEquals("Pred lines hould include as many lines as header", numHeaderFields, lines[1].split("\t").length); // last line is the prediction output
		String[] predLine = lines[1].split("\t");

		Assert.assertTrue("First col should be the smiles",predLine[0].equals(TEST_SMILES));
		Assert.assertTrue(lines[1].contains("mutagen") || lines[1].contains("nonmutagen")); // Should probably contain either of the labels (not known though)

		// SMILES to FILE

		File outputFile = TestUtils.createTempFile("res", ".smi");
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_class,
				"--smiles",TEST_SMILES,
				"--output-format", ChemOutputType.TSV.name(),
				"-co", TestUtils.toString(confs, ' '),
				(GENERATE_INCHI?"--output-inchi":""),
				"--silent",
				"-o", outputFile.getAbsolutePath(),
		});

		List<String> linesFromFile = IOUtils.readLines(new FileInputStream(outputFile),STANDARD_CHARSET);
		Assert.assertEquals(lines.length, linesFromFile.size());
		for(int i=0; i<lines.length;i++)
			Assert.assertEquals(lines[i].trim(), linesFromFile.get(i).trim());
	}
	
	@Test
	public void testManyConfsRoundedValsInOutputHeader()throws Exception{
	
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_class,
				"--smiles",TEST_SMILES,
				"--output-format", ChemOutputType.TSV.name(),
				"-co", ".01:0.99:0.01",
				(GENERATE_INCHI?"--output-inchi":""),
				"--silent",
		});
		String plainOutput = systemOutRule.getLog();
		String headerLine = plainOutput.split(System.lineSeparator(), 2)[0];
		
		for (String h : headerLine.split("\t")) {
			String[] splits = h.split("\\d+\\.\\d+");
			String noDigits = String.join("", splits);
			Assert.assertTrue(h.length() - noDigits.length() <= 4); // the numbers are 0.01,..0.99 (i.e. all 4 digits)
		}
	
	}

	@Test
	public void TestACPClassification_SDF() throws Exception {

		// Then predict SDF to SYS-OUT
		systemOutRule.clearLog();
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_class,
				"--smiles",TEST_SMILES,
				"--output-format", "sdf",
				(GENERATE_INCHI?"--output-inchi":""),
		});

		String sdf = systemOutRule.getLog();
		List<String> sdfLines = getPredictionOutputFromFullLog(sdf);
		systemOutRule.clearLog();


		// Then predict SDF to FILE
		File outFile = TestUtils.createTempFile("res", ".sdf");
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_class,
				"--smiles",TEST_SMILES,
				"--output-format", "sdf",
				(GENERATE_INCHI?"--output-inchi":""),
				"-o",outFile.getAbsolutePath(),
		});

		List<String> linesFromFile = IOUtils.readLines(new FileInputStream(outFile),STANDARD_CHARSET);

		ChemTestUtils.assertSameSDFOutput(sdfLines, linesFromFile);

		try(IteratingSDFReader mols = new IteratingSDFReader(new FileInputStream(outFile), SilentChemObjectBuilder.getInstance());){

			int numMols=0;
			IAtomContainer mol;
			while(mols.hasNext()){
				mol = mols.next();
				numMols++;

				//			Assert.assertEquals(TEST_SMILES,(String)mol.getProperty("SMILES"));
				Assert.assertNotNull(mol.getProperty(convertToSDFPropertyKey(OutputNamingSettings.getPvalueForLabelProperty("nonmutagen"))));
				Assert.assertNotNull(mol.getProperty(convertToSDFPropertyKey(OutputNamingSettings.getPvalueForLabelProperty("mutagen"))));
				if(GENERATE_INCHI){
					Assert.assertNotNull(mol.getProperty(convertToSDFPropertyKey(JSON.INCHI_KEY)));
					Assert.assertNotNull(mol.getProperty(convertToSDFPropertyKey(JSON.INCHIKEY_KEY)));
				}

			}
			Assert.assertEquals("Only a single molecule given to --smiles",1, numMols);
		}


	}

	@Test
	public void TestTCPClassification_JSON() throws Exception{
		List<Double> confs = Arrays.asList(0.6, 0.7, 0.8);
		CmpdData ames126 = TestResources.Cls.getAMES_126_gzip();
		
		File dataFile = TestUtils.createTempFile("data",".jar");
		mockMain(Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_CLASSIFICATION,
				"-mo", dataFile.getAbsolutePath(),
				"-td", ames126.format(), ames126.uri().toString(), 
				"-l", getLabelsArg(ames126.labels(),','),
				"-pr", ames126.property() 
				);

		// DEFAULT = JSON
		mockMain(new String[] {
				PredictOnline.CMD_NAME,
				"-ds", dataFile.getAbsolutePath(),

				"--smiles", TEST_SMILES,
				"-p", ames10.format(), ames10.uri().toString(),
				"-co", TestUtils.toString(confs, ' '),
				(GENERATE_INCHI? "--output-inchi":""),
		});

		String jsonPrediction = systemOutRule.getLog();
		JsonArray jsonPreds = getJSONArrayFromLog(jsonPrediction);
		for (Object line: jsonPreds){
			assertJSONPred((JsonObject)line, true, false, GENERATE_INCHI, confs, null);
		}


		// OUTPUT = plain
		systemOutRule.clearLog();
		mockMain(new String[] {
				PredictOnline.CMD_NAME,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"--smiles", TEST_SMILES,
				"--output-format", ChemOutputType.TSV.name(),
				"-p", ames10.format(), ames10.uri().toString(),
				"-co", TestUtils.toString(confs, ' ')
		});


		List<String> smilesLines = getPredictionOutputFromFullLog(systemOutRule.getLog());
		int numCols = -1;
		for(String line: smilesLines){
			if (numCols < 0) {
				numCols = line.split("\t").length;
			} else {
				Assert.assertEquals(numCols, line.split("\t").length);
			}
		}

		// Plain with file
		systemOutRule.clearLog();
		mockMain(new String[] {
				PredictOnline.CMD_NAME,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"--smiles", TEST_SMILES,
				"-p", ames10.format(), ames10.uri().toString(),
				"--output-format", ChemOutputType.TSV.name(),
				"-co", TestUtils.toString(confs, ',')
		});




		// Should be the same number of columns as when predicting to sys-out
		List<String> smilesLinesFromFile = getPredictionOutputFromFullLog(systemOutRule.getLog());
		for(String line: smilesLinesFromFile){

			Assert.assertEquals(numCols, line.split("\t").length);
		}

		// HERE WE TEST FOR SDF PREDICT FILE AND THAT ALL PROPERTIES ARE KEEPT IN CSV-OUTPUT

		try(InputStream stream = ames10.url().openStream();
			IteratingSDFReader mols = new IteratingSDFReader(stream, SilentChemObjectBuilder.getInstance());
			){

			
			
			IAtomContainer mol = null;
			int i = 0;
			String currSmilesLine;
			JsonObject json;
			while(mols.hasNext()){

				mol = mols.next();
				currSmilesLine = smilesLinesFromFile.get(i+2); // +1 because of header +1 because of --smiles molecule
				Assert.assertTrue(currSmilesLine.contains((String)mol.getProperty(ames10.property())));
				Assert.assertTrue(currSmilesLine.contains((String)mol.getProperty(CDKConstants.TITLE)));
				Assert.assertTrue(currSmilesLine.contains((String)mol.getProperty("Molecular Signature")));

				json = (JsonObject)jsonPreds.get(i+1); // +1 because of --smiles molecule
				JsonObject molProps = (JsonObject) json.get(JSON.MOLECULE_SECTION_KEY);
				Assert.assertTrue(molProps.containsKey(ames10.property()));
				Assert.assertTrue(molProps.containsKey(CDKConstants.TITLE));
				Assert.assertTrue(molProps.containsKey("Molecular Signature"));

				i++;
			}
		}

	}

	@Test
	public void TestTCPClassification_SDF() throws Exception{

		// SDF with file
		File sdfOut_V2000 = TestUtils.createTempFile("output", ".sdf");
		mockMain(new String[] {
				PredictOnline.CMD_NAME,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"--smiles", TEST_SMILES,
				"-p",predictfile_smiles.format(), predictfile_smiles.uri().toString(),
				// "-p", ames10.format(), ames10.uri().toString(),
				"--output-format", "sdf",
				"-o", sdfOut_V2000.getAbsolutePath(),
				"-co", "0.6,0.7,0.8"
		});
		Assert.assertTrue(! systemOutRule.getLog().contains("$$$"));
		// List<String> sdfLines = getPredictionOutputFromFullLog(systemOutRule.getLog());

		// SDF V3000 with file
		File sdfOut_V3000 = TestUtils.createTempFile("output", ".sdf");
		systemOutRule.clearLog();
		mockMain(new String[] {
				PredictOnline.CMD_NAME,
				"-ds", Classification.getAmes123().getAbsolutePath(),
				"--smiles", TEST_SMILES,
				"-p",predictfile_smiles.format(), predictfile_smiles.uri().toString(),
				// "-p", ames10.format(), ames10.uri().toString(),
				"--output-format", "sdf-v3000",
				"-o", sdfOut_V3000.getAbsolutePath(),
				"-co", "0.6 0.7 0.8"

		});
		Assert.assertTrue(! systemOutRule.getLog().contains("$$$"));
		Assert.assertTrue(systemErrRule.getLog().isEmpty());

		ChemTestUtils.assertSDFHasSameContentDifferentVersions(
			FileUtils.readFileToString(sdfOut_V3000, STANDARD_CHARSET), 
			FileUtils.readFileToString(sdfOut_V2000, STANDARD_CHARSET));
		
	}


	@SuppressWarnings("unchecked")
	@Test
	public void TestSignatureGeneration() throws Exception {

		// default = json
		mockMain(new String[] {
				GenerateSignatures.CMD_NAME,
				"--smiles", TEST_SMILES,
				(GENERATE_INCHI?"--output-inchi":""),
				"--silent",
		});

		String jsonOutput = systemOutRule.getLog();
		LoggerUtils.reloadLoggingConfig();
		// Make sure it can be parsed
		Map<String,Object> sigsRes = (JsonObject)((JsonArray) Jsoner.deserialize(jsonOutput)).get(0);
		Assert.assertTrue(sigsRes.containsKey(JSON.GENERATED_SIGNATURES_SECTION_KEY));
		Assert.assertTrue(sigsRes.containsKey(JSON.MOLECULE_SECTION_KEY));
		// Molecule section
		JsonObject molJSON = (JsonObject) sigsRes.get(JSON.MOLECULE_SECTION_KEY);
		Assert.assertTrue(molJSON.containsKey(JSON.SMILES_KEY));
		if (GENERATE_INCHI){
			Assert.assertTrue(molJSON.containsKey(StringUtils.toCamelCase(JSON.INCHI_KEY)));
			Assert.assertTrue(molJSON.containsKey(StringUtils.toCamelCase(JSON.INCHIKEY_KEY)));
		}
		// Signatures section
		Map<String,Double> signatures = (Map<String,Double>)sigsRes.get(JSON.GENERATED_SIGNATURES_SECTION_KEY);
		Assert.assertTrue(signatures.size() > 10);



		// plain text output
		betweenMethods();
		try{mockMain(new String[] {
				GenerateSignatures.CMD_NAME,
				"--smiles", TEST_SMILES,
				"--output-format", ChemOutputType.TSV.name(),
				"--silent",
				(GENERATE_INCHI?"--output-inchi":""),
		});
		} catch(Exception e) {
			e.printStackTrace(SYS_ERR);
			Assert.fail();
		}

		String plainOutput=systemOutRule.getLog();
		//		original.println(plainOutput);

		String[] lines = plainOutput.split("\n");
		String[] headers = lines[0].split("\t");
		int numExpectedCols = (GENERATE_INCHI?4:2); //expect smiles, (2x inchi if used) and generated signatures
		Assert.assertEquals(numExpectedCols, headers.length);
		// Find the part which is the mapping of generated signatures
		int signaturesIndex = -1;
		for (int i=0; i<headers.length; i++)
			if(headers[i].trim().equals(PredictionOutput.GENERATED_SIGNATURES_PROPERTY)){
				signaturesIndex=i; 
				break;
			}

		String mapAsString = lines[1].split("\t")[signaturesIndex].trim();
		// make sure it's a map
		Assert.assertTrue(mapAsString.startsWith("{"));
		Assert.assertTrue(mapAsString.endsWith("}"));
		Assert.assertTrue(mapAsString.contains("=") && mapAsString.contains(","));

		for(int i=0; i<lines.length; i++){
			//			original.println(lines[i]);
			Assert.assertEquals("each line is just SMILES and then Molecule Signatures", numExpectedCols, lines[i].split("\t").length );
		}
		if(GENERATE_INCHI){
			boolean foundInchi=false, foundInchiKey=false;
			for(String header: headers){
				if(header.equals(JSON.INCHI_KEY))
					foundInchi=true;
				else if(header.equals(JSON.INCHIKEY_KEY))
					foundInchiKey=true;
			}
			Assert.assertTrue("Inchi and InchiKey should be present", foundInchi && foundInchiKey);
		}
		// plain text output 2 (--smiles and -p file)

		betweenMethods();
		try {
			mockMain(new String[] {
					GenerateSignatures.CMD_NAME,
					"--smiles", TEST_SMILES,
					"--input", ames10.format(), ames10.uri().toString(),
					"--output-format", ChemOutputType.TSV.name(),
					"--output-inchi",
					"--silent"
			});
		} catch(Exception e) {
			e.printStackTrace(SYS_ERR);
			SYS_ERR.println(systemErrRule.getLog());
			Assert.fail();
		}

		String plainOutput2=systemOutRule.getLog();
		String[] plain2Lines = plainOutput2.split("\n");
		int numHeaders = plain2Lines[0].split("\t").length;
		boolean headerLine=true;
		for(String line: plain2Lines){
			if(!headerLine){
				// should contain a map with Signatures
				Assert.assertTrue(line.contains("{"));
				Assert.assertTrue(line.contains("}"));
				Assert.assertTrue(line.contains("="));
				Assert.assertTrue(line.contains(","));
			}
			Assert.assertEquals("All lines should have the same number of tabs",numHeaders, line.split("\t").length);
			headerLine=false;
		}


		// SDF
		betweenMethods();
		File outputFile = TestUtils.createTempFile("generatedSigns", ".sdf");
		mockMain(new String[] {
				GenerateSignatures.CMD_NAME,
				"--smiles", TEST_SMILES,
				"--input", ames10.format(), ames10.uri().toString(),
				"--output-format", "sdf",
				"-o", outputFile.getAbsolutePath(),
				(GENERATE_INCHI?"--output-inchi": ""),
		});

		IAtomContainer mol;
		try(ChemFileIterator mols = new SDFReader(new FileInputStream(outputFile));){
			Assert.assertTrue(mols.hasNext());

			while (mols.hasNext()){
				mol = mols.next();
				Assert.assertTrue(((String)mol.getProperty(convertToSDFPropertyKey(PredictionOutput.GENERATED_SIGNATURES_PROPERTY))).length() > 5);
				if(GENERATE_INCHI){
					Assert.assertTrue(mol.getProperty(convertToSDFPropertyKey(JSON.INCHI_KEY))!=null);
					Assert.assertTrue(mol.getProperty(convertToSDFPropertyKey(JSON.INCHIKEY_KEY))!=null);
				}	
			}
		}

		// SDF V3000
		betweenMethods();
		File outputFileV3000 = TestUtils.createTempFile("generatedSigns", ".sdf");
		mockMain(new String[] {
				GenerateSignatures.CMD_NAME,
				"--smiles", TEST_SMILES,
				"--input", ames10.format(), ames10.uri().toString(),
				"--output-format", "sdf-v3000",
				"-o", outputFileV3000.getAbsolutePath(),
				(GENERATE_INCHI?"--output-inchi": ""),
		});

		try(
				ChemFileIterator molsV3000 = new SDFReader(new FileInputStream(outputFileV3000));){
			Assert.assertTrue(molsV3000.hasNext());
			while(molsV3000.hasNext()){
				mol = molsV3000.next();
				Assert.assertTrue(((String) mol.getProperty(convertToSDFPropertyKey(PredictionOutput.GENERATED_SIGNATURES_PROPERTY))).length() > 5);
				if (GENERATE_INCHI){
					Assert.assertTrue(mol.getProperty(convertToSDFPropertyKey(JSON.INCHI_KEY))!=null);
					Assert.assertTrue(mol.getProperty(convertToSDFPropertyKey(JSON.INCHIKEY_KEY))!=null);
				}	
			}
		}
	}

	@Test
	public void TestSilentModeStillPrintToSTDOUT() throws Exception {
		runCheckSilent("json", 7, false); // 7 molecules!
		runCheckSilent("csv", 8, false); // 7 mols + header
		runCheckSilent("sdf", 300, false); // bunch of lines..
	}

	@Test
	public void TestCompressionOfResultsSTDOUT() throws Exception {
		runCompressionTest("json", false);
		runCompressionTest("csv", false);
		runCompressionTest("sdf", false);
	}

	@Test
	public void TestCompressionOfResultsFILE() throws Exception {
		runCompressionTestToFile("json", 7, false);
		runCompressionTestToFile("csv", 8, false);
		runCompressionTestToFile("sdf", 100, false);
	}

	public void runCheckSilent(String outputFormat, int minNumRows, boolean printOutput) throws Exception {
		// Predict to System.out
		systemOutRule.clearLog();
		// No silentMode
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_reg,
				"--smiles",TEST_SMILES,
				"-p", predictfile_smiles.format(), predictfile_smiles.uri().toString(),
				"--confidences", "0.1,0.4,0.9",
				"-of", outputFormat
		});
		String normalLog = systemOutRule.getLog();
		systemOutRule.clearLog();
		LoggerUtils.reloadLoggingConfig();

		// SILENT
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_reg,
				"--smiles",TEST_SMILES,
				"-p", predictfile_smiles.format(), predictfile_smiles.uri().toString(),
				"--confidences", "0.1,0.4,0.9",
				"--silent",
				"-of", outputFormat
		});
		LoggerUtils.reloadLoggingConfig();
		String silentLog = systemOutRule.getLog();
		systemOutRule.clearLog();

		if (printOutput){
			SYS_ERR.println("--- NON SILENT: ---");
			SYS_OUT.println(normalLog);
			SYS_ERR.println("--- SILENT: ---");
			SYS_OUT.println(silentLog);
		}

		// Verify that the prediction output is the same in both
		List<String> fromNormal = getPredictionOutputFromFullLog(normalLog);
		String[] fromSilent = silentLog.split("\n");
		if (outputFormat.equalsIgnoreCase("sdf")){
			ChemTestUtils.assertSameSDFOutput(fromNormal, Arrays.asList(fromSilent));
		} else {
			for(int i=0; i<fromNormal.size();i++){
				Assert.assertEquals(fromNormal.get(i), fromSilent[i]);
			}
		}
		Assert.assertTrue(fromSilent.length >= minNumRows);

	}

	public void runCompressionTest(String outputFormat, boolean printOutputs) throws Exception {
		// Predict to System.out

		// NORMAL (only print predictions)
		ByteArrayOutputStream bos_normal = new ByteArrayOutputStream();
		System.setOut(new PrintStream(bos_normal, true));
		systemOutRule.clearLog();
		LoggerUtils.reloadLoggingConfig();
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_reg,
				"--smiles",TEST_SMILES,
				"-p", predictfile_smiles.format(), predictfile_smiles.uri().toString(),
				"--confidences", "0.1,0.4,0.9",
				"-of", outputFormat,
				"--silent",
		});
		System.out.flush();
		String normalLog = bos_normal.toString();
		LoggerUtils.reloadLoggingConfig();
		systemOutRule.clearLog();


		ByteArrayOutputStream bos_gz = new ByteArrayOutputStream();
		System.setOut(new PrintStream(bos_gz, true));

		// GZIPPED
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_reg,
				"--smiles",TEST_SMILES,
				"-p", predictfile_smiles.format(), predictfile_smiles.uri().toString(),
				"--confidences", "0.1,0.4,0.9",
				"--compress",
				"--silent",
				"-of", outputFormat
		});

		LoggerUtils.reloadLoggingConfig();
		systemOutRule.clearLog();
		String unzippedOutput = IOUtils.toString(StreamUtils.unZIP(new ByteArrayInputStream(bos_gz.toByteArray())),STANDARD_CHARSET);

		System.setOut(SYS_OUT);

		if (printOutputs) {
			SYS_OUT.println("--- NORMAL: ---");
			SYS_OUT.println(normalLog);
			SYS_OUT.println("--- UNZIPPED: ---");
			SYS_OUT.println(unzippedOutput);
		}

		Assert.assertEquals(normalLog, unzippedOutput);

	}

	public void runCompressionTestToFile(String outputFormat, int minNumRows, boolean printOutputs) throws Exception {
		// Predict to System.out

		// Normal TO STDOUT
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_reg,
				"--smiles",TEST_SMILES,
				"-p", predictfile_smiles.format(), predictfile_smiles.uri().toString(),
				"--confidences", "0.1,0.4,0.9",
				"--silent",
				"-of", outputFormat
		});
		String normalLog = systemOutRule.getLog().trim();
		LoggerUtils.reloadLoggingConfig();


		File tmpFile = TestUtils.createTempFile("output", ".tmp"); 

		// GZIPPED TO FILE
		systemOutRule.clearLog();
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_reg,
				"--smiles",TEST_SMILES,
				"-p", predictfile_smiles.format(), predictfile_smiles.uri().toString(),
				"--confidences", "0.1,0.4,0.9",
				"--compress",
				"-of", outputFormat,
				"--output", tmpFile.getAbsolutePath(),
		});
		String fileLog = systemOutRule.getLog();
		systemOutRule.clearLog();

		if(printOutputs){
			SYS_OUT.println("--- SYS OUT ---");
			SYS_OUT.println(normalLog);
			SYS_OUT.println("--- File sys OUT ---");
			SYS_OUT.println(fileLog);
		}
		String unzippedFile =IOUtils.toString(StreamUtils.unZIP(new FileInputStream(tmpFile)),STANDARD_CHARSET);

		if(printOutputs){
			SYS_OUT.println("--- non-comp to std-out: ---");
			SYS_OUT.println(normalLog);
			SYS_OUT.println("--- unzipped from File: ---");
			SYS_OUT.println(unzippedFile);
		}

		Assert.assertTrue(normalLog.split("\n").length >= minNumRows);
		Assert.assertEquals(normalLog.trim(), unzippedFile.trim());

	}

	@Test
	public void TestCompressToSTDOUT_NoSilentFlag() throws Exception{
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("--quiet", "compress", "flag"));
		//		printOutput();
		mockMain(new String[] {
				Predict.CMD_NAME,
				"-m", modelFileACP_reg,
				"--smiles",TEST_SMILES,
				"-p", predictfile_smiles.format(), predictfile_smiles.uri().toString(),
				"--confidences", "0.1,0.4,0.9",
				"--compress",
				"-of", "csv"
		});
	}

	public static String unzip(final byte[] compressed) {
		if ((compressed == null) || (compressed.length == 0)) {
			throw new IllegalArgumentException("Cannot unzip null or empty bytes");
		}
		if (!isZipped(compressed)) {
			SYS_ERR.println("String was not gzipped!");
			Assert.fail("Called unzip on a non-zipped file");
			//			return new String(compressed);
		}

		try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressed)) {
			try (GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream)) {
				try (InputStreamReader inputStreamReader = new InputStreamReader(gzipInputStream, STANDARD_CHARSET)) {
					try (BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
						StringBuilder output = new StringBuilder();
						String line;
						while((line = bufferedReader.readLine()) != null){
							output.append(line);
							output.append('\n');
						}
						return output.toString();
					}
				}
			}
		} catch(IOException e) {
			throw new RuntimeException("Failed to unzip content", e);
		}
	}

	public static boolean isZipped(final byte[] compressed) {
		return (compressed[0] == (byte) (GZIPInputStream.GZIP_MAGIC)) && (compressed[1] == (byte) (GZIPInputStream.GZIP_MAGIC >> 8));
	}

	public static JsonObject flatten(JsonObject json){
		return flatten(json, null);
	}

	private static JsonObject flatten(JsonObject object, JsonObject flattened){
		if(flattened == null){
			flattened = new JsonObject();
		}
		Iterator<?> keys = object.keySet().iterator();
		while(keys.hasNext()){
			String key = (String)keys.next();
			if(object.get(key) instanceof JsonObject){
				flatten((JsonObject) object.get(key), flattened);
			} else {
				flattened.put(key, object.get(key));
			}
		}
		return flattened;
	}



	// New attempt
	static boolean GEN_INCHI;
	static {
		GEN_INCHI = (Math.random()>0.5 ? true : false);
	}

	
	@Test
	public void testSplittedJSONFormat() throws Exception {
		CSVCmpdData solu10 = TestResources.Reg.getSolubility_10();
		File plainFile = TestUtils.createTempFile("plain", ".json");
		mockMain(new String [] {
				Predict.CMD_NAME,
						"-m", PreTrainedModels.ACP_REG_LIBSVM.toString(),
						"-p", solu10.format(), solu10.uri().toString(),
						"--confidences", "0.1,0.4,0.9",
						"-of", "splitted-json",
						"--output", plainFile.getAbsolutePath()
		});
		String plainStr = FileUtils.readFileToString(plainFile, STANDARD_CHARSET).trim();
		
//		SYS_ERR.println(plainStr);
		String[] lines = plainStr.split("\n");
		for (String l : lines) {
			JsonObject json = (JsonObject) Jsoner.deserialize(l);
			Assert.assertTrue(!json.isEmpty());
			JsonObject molSection = (JsonObject) json.get(JSON.MOLECULE_SECTION_KEY);
			Assert.assertTrue(!molSection.isEmpty());
			JsonObject predSection = (JsonObject) json.get(JSON.PREDICTING_SECTION_KEY);
			Assert.assertTrue(!predSection.isEmpty());
		}
	}

	@Test
	/* Note: this might fail for SDF files as CDK generates some ID that seems to be time-dependent.
	 * So depending on the timing it many generate a different ID between runs, which leads to 'different' output files (i.e. only ID differs)
	 */
	public void testAllOutputFileTypesProduceIdenticalResults() throws Exception {
		CSVCmpdData solu10 = TestResources.Reg.getSolubility_10();
		for (ChemOutputType of : CLIParameters.ChemOutputType.values()) {
			System.err.println("running with output type : " + of);
			doTestAllOutputIdentical(PreTrainedModels.ACP_REG_LIBSVM.toString(), solu10.format(), solu10.uri().toString(), of);
			LoggerUtils.reloadLoggingConfig();
		}
	}

	private void doTestAllOutputIdentical(String modelPath, String predictFormat, String predictPath, ChemOutputType of) throws Exception {

		List<String> commonArgs = new ArrayList<>(Arrays.asList(Predict.CMD_NAME,
				"-m", modelPath,
				"-p", predictFormat, predictPath,
				"--confidences", "0.1,0.4,0.9"));
		if (GEN_INCHI)
			commonArgs.add("--output-inchi");
			


		// File
		File plainFile = TestUtils.createTempFile("plain", "."+of);
		mockMain(
				getParams(commonArgs,
						"-of", of.name(),
						"--output", plainFile.getAbsolutePath())
				);
		String plainStr = FileUtils.readFileToString(plainFile, STANDARD_CHARSET).trim();

		// Gzip file
		File gzipFile = TestUtils.createTempFile("gzip", "."+of);
		mockMain(
				getParams(commonArgs, 
						"-of", of.name(),
						"--compress",
						"--output", gzipFile.getAbsolutePath())
				);
		String unzippedStr = IOUtils.toString(StreamUtils.unZIP(new FileInputStream(gzipFile)), STANDARD_CHARSET).
				trim();


		PrintStream originalSO = System.out;

		// Stdout plain
		String stdOutStr = null;


		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				PrintStream baosPS = new PrintStream(baos);){

			System.setOut(baosPS);
			mockMain(
					getParams(commonArgs,
							"-of", of.name()
							)
					);

			stdOutStr = IOUtils.toString(new ByteArrayInputStream(baos.toByteArray()),STANDARD_CHARSET);
			stdOutStr = getPredictionOutputFromFullLogStr(stdOutStr).trim();
		}

		// Stdout gzip
		String unzippedOutStr = null;
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				PrintStream baosPS = new PrintStream(baos);){

			System.setOut(baosPS);
			mockMain(
					getParams(commonArgs, 
							"-of", of.name(),
							"--compress",
							"--silent"
							)
					);
			unzippedOutStr = IOUtils.toString(StreamUtils.unZIP(new ByteArrayInputStream(baos.toByteArray())),STANDARD_CHARSET).
					trim();
		}
		System.setOut(originalSO);

		// Assert all output are the same
		if (of == ChemOutputType.SDF_V2000 || of == ChemOutputType.SDF_V3000){
			ChemTestUtils.assertSameSDFOutput(stdOutStr, plainStr);
			ChemTestUtils.assertSameSDFOutput(plainStr, unzippedStr);
			ChemTestUtils.assertSameSDFOutput(plainStr, unzippedOutStr);
		} else {
			Assert.assertEquals(stdOutStr, plainStr);
			Assert.assertEquals(plainStr, unzippedStr);
			Assert.assertEquals(plainStr, unzippedOutStr);
		}
	}

	private static String[] getParams(List<String> common, String... args){
		List<String> res = new ArrayList<>(common);
		for (String a : args)
			res.add(a);
		return res.toArray(new String[] {});
	}

}

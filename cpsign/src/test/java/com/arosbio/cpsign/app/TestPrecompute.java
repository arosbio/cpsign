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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.chem.io.in.CSVFile;
import com.arosbio.chem.io.in.SDFile;
import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.descriptors.ChemDescriptor;
import com.arosbio.cheminf.descriptors.DescriptorFactory;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.cheminf.descriptors.UserSuppliedDescriptor;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.FeatureVector.Feature;
import com.arosbio.data.MissingValueFeature;
import com.arosbio.data.transform.Transformer;
import com.arosbio.data.transform.duplicates.KeepMedianLabel;
import com.arosbio.data.transform.duplicates.UseVoting;
import com.arosbio.data.transform.filter.LabelRangeFilter;
import com.arosbio.io.JarDataSource;
import com.arosbio.ml.io.ModelIO;
import com.arosbio.ml.io.impl.PropertyFileStructure;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.CLITest;
import com.arosbio.tests.utils.TestUtils;
import com.google.common.collect.Comparators;
import com.google.common.collect.Ordering;

@Category(CLITest.class)
public class TestPrecompute extends CLIBaseTest {

	// Classification data
	private static final CmpdData ames10 = TestResources.Cls.getAMES_10();
	private static final CmpdData ames126 = TestResources.Cls.getAMES_126();
	private static final CSVCmpdData classDuplicate = TestResources.Cls.getContradict_labels();
	private static final String LABELS_STRING = getLabelsArg(ames10.labels());

	// Regression
	private static final CSVCmpdData solu10 = TestResources.Reg.getSolubility_10();
	private static final CSVCmpdData solu500 = TestResources.Reg.getSolubility_500();
	private static final CSVCmpdData regDuplicate = TestResources.Reg.getContradict_labels_and_outlier();

	static File tempPropExclusive, tempCalibExclusive,emptyFile;

	@BeforeClass
	public static void setupExclusiveFiles() throws Exception {
		int numInPropTrainFile=17, numInCalibTrainFile=9;

		// Make some files for exclusive use
		tempPropExclusive = TestUtils.createTempFile("solubility.prop", ".smiles");
		tempCalibExclusive = TestUtils.createTempFile("solubility.calib", ".smiles");

		// Empty files that should give errors
		emptyFile = TestUtils.createTempFile("solubility.prop", ".smiles");
		CSVCmpdData solu_500 = TestResources.Reg.getSolubility_500();

		try(
				Reader reader = new InputStreamReader(solu_500.url().openStream());
				BufferedReader breader = new BufferedReader(reader);

				BufferedWriter bw_calib = new BufferedWriter(new FileWriter(tempCalibExclusive));
				BufferedWriter bw_prop = new BufferedWriter(new FileWriter(tempPropExclusive));
				){

			// Get the header
			String header = breader.readLine();

			// Skip the first 10 lines (that are in the train-file already)
			for(int i=0; i<10; i++){
				breader.readLine();
			}

			// next lines into proper exclusive
			bw_prop.write(header);
			bw_prop.newLine();
			for(int i=0; i<numInPropTrainFile; i++){
				bw_prop.write(breader.readLine());
				bw_prop.newLine();
			}

			// few lines in calib exclusive
			bw_calib.write(header);
			bw_calib.newLine();
			for(int i=0; i<numInCalibTrainFile; i++){
				bw_calib.write(breader.readLine());
				bw_calib.newLine();
			}
		}
	}

	@Test
	public void testUsage() {
		mockMain(Precompute.CMD_NAME);
//		printLogs();
	}

	@Test
	public void testImputeTransformer() throws Exception {
		File preFirst = TestUtils.createTempFile("datafile", ".csr.jar");
		//LoggerUtils.setDebugMode(SYS_ERR);

		try {
			mockMain(new String[] {
					Precompute.CMD_NAME,
					"-td", ames126.format(), ames126.uri().toString(),
					"-pr", ames126.property(),
					"--transformations", "drop-missing-data-feats", "single_feat_imp:median:colMinIndex=10:colMaxIndex=200","num-non-zer-select:4", "variance-based-select:2:50","standardize",
					"--labels", getLabelsArg(ames126.labels()),
					"-mo", preFirst.getAbsolutePath(),
					// "-mn", "dasf",
					"--time"
			}
					);
		} catch(Exception e){
			e.printStackTrace();
			Assert.fail();
		}


		// Doing the same thing but split up into precompute and then transform

		// First only compute descriptors
		File preNoTransform = TestUtils.createTempFile("precomp.needtransform", ".jar");
		mockMain(
			Precompute.CMD_NAME,
			"-td", ames126.format(), ames126.uri().toString(),
			"-pr", ames126.property(),
			
			"--labels", getLabelsArg(ames126.labels()),
			"-mo", preNoTransform.getAbsolutePath(),
			// "-mn", "dasf",
			"--time"
			);

		File preWITHTransform = TestUtils.createTempFile("precomp.transformed", ".jar");
		mockMain(
			Transform.CMD_NAME,
			"-ds", preNoTransform.getAbsolutePath(),
			"--transformations", "drop-missing-data-feats", "single_feat_imp:median:colMinIndex=10:colMaxIndex=200","num-non-zer-select:4", "variance-based-select:2:50","standardize",
			"-mo", preWITHTransform.getAbsolutePath(),
			"--time"
		);

		// Load both and compare the features - they should be identical!
		ChemDataset inOne = ModelSerializer.loadDataset(preFirst.toURI(), null);
		ChemDataset splitted = ModelSerializer.loadDataset(preWITHTransform.toURI(), null);

		// Check transformations
		List<Transformer> l1 = inOne.getTransformers(), l2 = splitted.getTransformers();
		Assert.assertEquals(l1.size(), l2.size());
		Assert.assertTrue(l1.size()>2);
		for (int i=0;i<l1.size();i++){
			Assert.assertEquals(l1.get(i).getClass(), l2.get(i).getClass());
		}

		// Check data
		Assert.assertTrue(DataUtils.equals(inOne.getDataset(), splitted.getDataset()));

		// Check that the model name is identical in both of them
		Map<String,Object> p1 = ModelIO.getCPSignProperties(preFirst.toURI());
		Map<String,Object> p2 = ModelIO.getCPSignProperties(preWITHTransform.toURI());
		
		// System.err.println(p1);
		// System.err.println(p2);
		Assert.assertEquals(CollectionUtils.getArbitratyDepth(p1, PropertyFileStructure.InfoSection.MODEL_NAME_KEY), 
			CollectionUtils.getArbitratyDepth(p2, PropertyFileStructure.InfoSection.MODEL_NAME_KEY));

				// printLogs();
	}

	@Test
	public void testTransformersFailing() throws Exception {

		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("RobustScaler"));
		//		exit.checkAssertionAfterwards(new PrintSysOutput());
		File model = TestUtils.createTempFile("datafile", ".csr.jar");
		mockMain(Precompute.CMD_NAME,
				"--model-type", PRECOMPUTE_CLASSIFICATION,
				"--labels",LABELS_STRING,
				"--train-data", ames126.format(), ames126.uri().toString(),
				"--property", ames126.property(),
				"--descriptors", "signatures:1:3","AminoAcidCountDescriptor","ALOGPDescriptor","HBondAcceptorCountDescriptor","HBondDonorCountDescriptor",
				"--model-out", model.getAbsolutePath(),
				"--transformations", "DropMissingDataFeatures", "RobustScaler:maxCol=15"

				);
	}

	@Test
	public void testTransformers() throws Exception {
		File preFirst = TestUtils.createTempFile("datafile", ".csr.jar");

		try {
			mockMain(new String[] {
					Precompute.CMD_NAME,
					"-td", SDFile.FORMAT_NAME , ames10.uri().toString(),
					"-pr", ames10.property(),
					"--descriptors", "usersupplied:IC50", "signatures:1", "all-cdk",
					"--transformations", "drop-missing-data-feats", "standardize",
					"--labels", LABELS_STRING,
					"-mo", preFirst.getAbsolutePath(),
					"-mn", "dasf",
					"--time"
			}
					);
		} catch(Exception e){
			e.printStackTrace();
			Assert.fail();
		}

		// Same thing but with two different calls to --transform

		File preSecond = TestUtils.createTempFile("datafile", ".csr.jar");

		try {
			mockMain(new String[] {
					Precompute.CMD_NAME,
					"-td", SDFile.FORMAT_NAME , ames10.uri().toString(),
					"-pr", ames10.property(),
					"--descriptors", "usersupplied:IC50", "signatures:1", "all-cdk",
					"--transformations", "drop-missing-data-feats", 
					"--transformations", "standardize",
					"--labels", LABELS_STRING,
					"-mo", preSecond.getAbsolutePath(),
					"-mn", "dasf",
					"--time"
			}
					);
		} catch(Exception e){
			e.printStackTrace();
			Assert.fail();
		}

		try (JarDataSource src1 = new JarDataSource(new JarFile(preFirst));
				JarDataSource src2 = new JarDataSource(new JarFile(preSecond));){
			ChemDataset data1 = ModelSerializer.loadDataset(src1, null);
			ChemDataset data2 = ModelSerializer.loadDataset(src2, null);

			Assert.assertEquals(data1.getNumAttributes(), data2.getNumAttributes());
			Assert.assertEquals(data1.getTransformers().toString(), data2.getTransformers().toString());
			Assert.assertEquals(data1.getNumRecords(), data2.getNumRecords());
		}

		//		printLogs();
	}



	@Test
	public void testAllCDKDescriptors() throws Exception {
		File preFirst = TestUtils.createTempFile("datafile", ".csr.jar");

		try {
			mockMain(new String[] {
					Precompute.CMD_NAME,
					"-td", SDFile.FORMAT_NAME , ames10.uri().toString(),
					"-pr", ames10.property(),
					"--descriptors", "usersupplied:IC50", "signatures:1", "all-cdk",
					"--labels", LABELS_STRING,
					"-mo", preFirst.getAbsolutePath(),
					"-mn", "dasf",
					"--time"
			}
					);
		} catch(Exception e){
			e.printStackTrace();
			Assert.fail();
		}

		ChemDataset precomp = ModelSerializer.loadDataset(preFirst.toURI(), null);
		List<ChemDescriptor> descriptorsList = precomp.getDescriptors();
		System.out.println(descriptorsList);
		Assert.assertTrue(descriptorsList.get(0) instanceof UserSuppliedDescriptor);
		Assert.assertTrue(((UserSuppliedDescriptor) descriptorsList.get(0)).getPropertyNames().contains("IC50"));
		Assert.assertTrue(descriptorsList.get(descriptorsList.size()-1) instanceof SignaturesDescriptor); // Last one should be the signatures descriptor!
		Assert.assertEquals(DescriptorFactory.getCDKDescriptorsNo3D().size()+2, descriptorsList.size());


		// Now do the 3D descriptors as well
		File preSecond = TestUtils.createTempFile("datafile", ".csr.jar");

		try {
			mockMain(new String[] {
					Precompute.CMD_NAME,
					"-td", SDFile.FORMAT_NAME , ames10.uri().toString(),
					"-pr", ames10.property(),
					"--descriptors", "usersupplied:IC50", "signatures:1", "all-cdk", "all-cdk-3d",
					"--labels", LABELS_STRING,
					"-mo", preSecond.getAbsolutePath(),
					"-mn", "dasf",
					"--time"
			}
					);
		} catch(Exception e){
			e.printStackTrace();
			Assert.fail();
		}

		ChemDataset precomp3D = ModelSerializer.loadDataset(preSecond.toURI(), null);
		List<ChemDescriptor> descriptorsList3D = precomp3D.getDescriptors();
		System.out.println(descriptorsList3D);
		Assert.assertTrue(descriptorsList3D.get(0) instanceof UserSuppliedDescriptor);
		Assert.assertTrue(((UserSuppliedDescriptor) descriptorsList3D.get(0)).getPropertyNames().contains("IC50"));
		Assert.assertTrue(descriptorsList3D.get(descriptorsList3D.size()-1) instanceof SignaturesDescriptor); // Last one should be the signatures descriptor!
		Assert.assertEquals(DescriptorFactory.getCDKDescriptorsNo3D().size() + DescriptorFactory.getCDKDescriptorsRequire3D().size()+2, 
				descriptorsList3D.size());

		//		printLogs();
	}

	@Test
	public void testUserSuppliedDescriptorsSDF() throws Exception {
		File preFirst = TestUtils.createTempFile("datafile", ".csr.jar");

		CmpdData herg = TestResources.Cls.getHERG();

		try {
			mockMain(new String[] {
					Precompute.CMD_NAME,
					"-td", herg.format(),herg.uri().toString(),
					"-pr", herg.property(),
					"--descriptors", "usersupplied:IC50", "signatures:1",
					"--labels", getLabelsArg(herg.labels()),
					"-mo", preFirst.getAbsolutePath(),
					"-mn", "dasf",
					"--time"
			}
					);
		} catch(Exception e){
			e.printStackTrace();
			Assert.fail();
		}

		ChemDataset precomp = ModelSerializer.loadDataset(preFirst.toURI(), null);
		List<ChemDescriptor> descriptorsList = precomp.getDescriptors();
		//		System.out.println(descriptorsList);
		Assert.assertTrue(descriptorsList.get(0) instanceof UserSuppliedDescriptor);
		Assert.assertTrue(((UserSuppliedDescriptor) descriptorsList.get(0)).getPropertyNames().contains("IC50"));
		Assert.assertTrue(descriptorsList.get(1) instanceof SignaturesDescriptor);
		//		printLogs();
	}

	@Test
	public void testUserSuppliedDescriptorsCSV() throws Exception {
		File preFirst = TestUtils.createTempFile("datafile", ".csr.jar");

		CSVCmpdData withDescriptors = TestResources.Cls.getAMES_126_chem_desc();
		mockMain(
				Precompute.CMD_NAME,
				"-td", withDescriptors.format(), "delim="+withDescriptors.delim(), withDescriptors.uri().toString(),
				"-pr", withDescriptors.property(),
				"--descriptors", "usersupplied:ALogp2", "usersupplied:nAromBlocks","usersupplied:ALogP,nH,nI,nAromRings",
				"--labels", getLabelsArg(withDescriptors.labels()),
				"-mo", preFirst.getAbsolutePath(),
				"-mn", "dasf",
				"--time"
				);
		

		ChemDataset precomp = ModelSerializer.loadDataset(preFirst.toURI(), null);
		List<ChemDescriptor> descriptorsList = precomp.getDescriptors();
		System.out.println(descriptorsList);
		Assert.assertTrue(descriptorsList.get(0) instanceof UserSuppliedDescriptor);
		assertListContainsIgnoreCase(((UserSuppliedDescriptor) descriptorsList.get(0)).getPropertyNames(),"ALogp2");
		// Assert.assertTrue(((UserSuppliedDescriptor) descriptorsList.get(0)).getPropertyNames().contains("ALogp2"));
		assertListContainsIgnoreCase(((UserSuppliedDescriptor) descriptorsList.get(1)).getPropertyNames(),"nAromBlocks");
		assertListContainsIgnoreCase(((UserSuppliedDescriptor) descriptorsList.get(2)).getPropertyNames(),"ALogP");
		assertListContainsIgnoreCase(((UserSuppliedDescriptor) descriptorsList.get(2)).getPropertyNames(),"nH");
		assertListContainsIgnoreCase(((UserSuppliedDescriptor) descriptorsList.get(2)).getPropertyNames(),"nI");
		assertListContainsIgnoreCase(((UserSuppliedDescriptor) descriptorsList.get(2)).getPropertyNames(),"nAromRings");
		//		printLogs();
	}

	private static void assertListContainsIgnoreCase(List<String> list, String item){

		for (String s : list){
			if (s.equalsIgnoreCase(item))
				return;
		}
		Assert.fail("Not containing: " + item);
	}

	@Test
	public void testUserSuppliedDescriptorsALL_EXCEPT() throws Exception {
		File preFirst = TestUtils.createTempFile("datafile", ".csr.jar");

		CSVCmpdData withDescriptors = TestResources.Cls.getAMES_126_chem_desc();
			mockMain(new String[] {
					Precompute.CMD_NAME,
					"-td", withDescriptors.format(), "delim="+withDescriptors.delim(), withDescriptors.uri().toString(),
					"-pr", withDescriptors.property(),
					"--descriptors", "usersupplied:sortingOrder=revers-alphabetical:properties=all,-cdk_Title,-Molecular Signature,-\"Ames test categorisation\",-bpol", 
					"--labels", getLabelsArg(withDescriptors.labels()),
					"-mo", preFirst.getAbsolutePath(),
					"-mn", "dasf",
					"--time"
			}
					);
		

		ChemDataset precomp = ModelSerializer.loadDataset(preFirst.toURI(), null);
		List<ChemDescriptor> descriptorsList = precomp.getDescriptors();
		System.out.println(descriptorsList);
		Assert.assertTrue(descriptorsList.get(0) instanceof UserSuppliedDescriptor);
		Assert.assertTrue(((UserSuppliedDescriptor) descriptorsList.get(0)).getPropertyNames().contains("ALOGP"));
		Assert.assertTrue(((UserSuppliedDescriptor) descriptorsList.get(0)).getPropertyNames().size()>20); // A bunch of them in there!

		boolean containsMissingVals = false, containsNaN = false;
		for (DataRecord r : precomp.getDataset()) {
			for (Feature f : r.getFeatures()) {
				if (f instanceof MissingValueFeature)
					containsMissingVals = true;
				if (Double.isNaN(f.getValue())) {
					containsNaN = true;
				}
			}
		}
		Assert.assertTrue(containsNaN);
		Assert.assertTrue(containsMissingVals);
		Assert.assertTrue(Comparators.isInOrder(descriptorsList.get(0).getFeatureNames(), Ordering.natural().reverse()));
		//		printLogs();
	}



	//	@Test
	public void generatePrecomputedWithTransformers() throws Exception {
		File outputFile = TestUtils.createTempFile("precomp.transformed", ".jar");
		CmpdData ames1337 = TestResources.Cls.getAMES_1337();
		mockMain(
				Precompute.CMD_NAME,
				"-td" , ames1337.format(), ames1337.uri().toString(),
				"-pr", ames1337.property(), 
				"-mt", PRECOMPUTE_CLASSIFICATION,
				"-l", LABELS_STRING,
				"--descriptors", "signatures:1:3",
				"-mo", outputFile.getAbsolutePath(),
				"-mn", "dasf",
				"--transformations", "keep_first", "zero_max_scale",
				"--time"
		);
		

	}

	@Test
	public void testPrecomputeRegression() throws Exception{ 

		File dataFile = TestUtils.createTempFile("datafile", ".csr.jar");

		// =====================================
		// PRECOMPUTE + TRANSFORM IN SINGLE STEP
		// =====================================
		try{
			mockMain(new String[] {
					Precompute.CMD_NAME,
					"-td" , CSVFile.FORMAT_NAME, solu500.uri().toString(),
					"-pr", solu10.property(), 
					"-mt", PRECOMPUTE_REGRESSION,
					"--descriptors", "signatures:1:3",
					"-mo", dataFile.getAbsolutePath(),
					"-mn", "dasf",
					"--transformations", "filter-label-range:0.1:5.1",
					"--transformations", "keep-min-lab",
					"--time"
			});
		} catch(Exception e){
			e.printStackTrace();
			Assert.fail();
		}

		// ========================================
		// PRECOMPUTE + TRANSFORM IN SEPARATE STEPS
		// ========================================

		File preNoTransform = TestUtils.createTempFile("datafile_no_transform", ".csr.jar");
		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-td" , CSVFile.FORMAT_NAME, solu500.uri().toString(),
				"-pr", solu10.property(), 
				"-mt", PRECOMPUTE_REGRESSION,
				"--descriptors", "signatures:1:3",
				"-mo", preNoTransform.getAbsolutePath(),
				"-mn", "dasf",
				"--time"
		});

		File precompWithTransform = TestUtils.createTempFile("transformed.precomp", ".jar");
		mockMain(new String[] {
			Transform.CMD_NAME,
			"-ds" , preNoTransform.getAbsolutePath(),
			"-mo", precompWithTransform.getAbsolutePath(),
			"--transformations", "filter-label-range:0.1:5.1",
			"--transformations", "keep-min-lab",
			"--time"
		});

		// Load both and compare the features - they should be identical!
		ChemDataset inOne = ModelSerializer.loadDataset(dataFile.toURI(), null);
		ChemDataset splitted = ModelSerializer.loadDataset(precompWithTransform.toURI(), null);

		// Check transformations
		List<Transformer> l1 = inOne.getTransformers(), l2 = splitted.getTransformers();
		Assert.assertEquals(l1.size(), l2.size());
		Assert.assertTrue(l1.isEmpty());
		for (int i=0;i<l1.size();i++){
			Assert.assertEquals(l1.get(i).getClass(), l2.get(i).getClass());
		}

		// Check data
		Assert.assertTrue(DataUtils.equals(inOne.getDataset(), splitted.getDataset()));

		// Check that the model name is identical in both of them
		Map<String,Object> p1 = ModelIO.getCPSignProperties(dataFile.toURI());
		Map<String,Object> p2 = ModelIO.getCPSignProperties(precompWithTransform.toURI());
		
		// System.err.println(p1);
		// System.err.println(p2);
		Assert.assertEquals(CollectionUtils.getArbitratyDepth(p1, PropertyFileStructure.InfoSection.MODEL_NAME_KEY), 
			CollectionUtils.getArbitratyDepth(p2, PropertyFileStructure.InfoSection.MODEL_NAME_KEY));


	}



	@Test
	public void testPrecomputeWithExclusiveTrainingSets() throws Exception {

		File outputModel = TestUtils.createTempFile("datafile", ".csr.jar");


		// =====================================
		// NORMAL TRAIN-FILE AND PROPER-TRAIN-EXCLUSIVE
		// =====================================

		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-td" , CSVFile.FORMAT_NAME, solu10.uri().toString(),
				"-pr", solu10.property(), 
				"-md", CSVFile.FORMAT_NAME, tempPropExclusive.getAbsolutePath(),
				"-mt", PRECOMPUTE_REGRESSION,
				"--descriptors", "signatures:1:2",
				"-mo", outputModel.getAbsolutePath(),
				"-mn", "dasf",
		});

		


		// =====================================
		// NORMAL TRAIN-FILE AND CALIB-TRAIN-EXCLUSIVE
		// =====================================

		FileUtils.forceDelete(outputModel);

		try{
			mockMain(new String[] {
					Precompute.CMD_NAME,
					"-td" , CSVFile.FORMAT_NAME, solu10.uri().toString(),
					"-pr", solu10.property(), 
					"-cd", CSVFile.FORMAT_NAME, tempCalibExclusive.getAbsolutePath(),
					"-mt", PRECOMPUTE_REGRESSION,
					"--descriptors", "signatures:1:2",
					"-mo", outputModel.getAbsolutePath(),
					"-mn", "dasf",
			});

		} catch(Exception e){
			e.printStackTrace();
			Assert.fail();
		}

		// =====================================
		// ALL 3 POSSIBLE TRAIN-FILES
		// =====================================

		FileUtils.forceDelete(outputModel);

		try{
			mockMain(new String[] {
					Precompute.CMD_NAME,
					"-td" , CSVFile.FORMAT_NAME,solu10.uri().toString(),
					"-pr", solu10.property(), 
					"-md", CSVFile.FORMAT_NAME, tempPropExclusive.getAbsolutePath(),
					"-cd", CSVFile.FORMAT_NAME, tempCalibExclusive.getAbsolutePath(),
					"-mt", PRECOMPUTE_REGRESSION,
					"--descriptors", "signatures:1:2",
					"-mo", outputModel.getAbsolutePath(),
					"-mn", "dasf",
			});

		} catch(Exception e){
			e.printStackTrace();
			Assert.fail();
		}

	}




	@Test
	public void testPrecomputeWithExclusiveTrainingSetsThatShouldFail_1() throws Exception {

		File outputModel = TestUtils.createTempFile("datafile", ".csr.jar");

		// =====================================
		// NORMAL TRAIN-FILE AND PROPER-TRAIN-EXCLUSIVE (which is empty!)
		// =====================================
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("No", "molecule", "parsed", "format"));
		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-td" , CSVFile.FORMAT_NAME, solu10.uri().toString(),
				"-pr", solu10.property(), 
				"-md", SDFile.FORMAT_NAME, emptyFile.getAbsolutePath(),
				"-mt", PRECOMPUTE_REGRESSION,
				"--descriptors", "signatures:1:2",
				"-mo", outputModel.getAbsolutePath(),
				"-mn", "dasf",
		});

	}

	@Test
	public void testPrecomputeWithExclusiveTrainingSetsThatShouldFail_2() throws Exception {

		File outputModel = TestUtils.createTempFile("datafile", ".csr.jar");
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("format", "No", "molecule"));

		// =====================================
		// NORMAL TRAIN-FILE AND CALIB-TRAIN-EXCLUSIVE (empty!)
		// =====================================
		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-td" , CSVFile.FORMAT_NAME,solu10.uri().toString(),
				"-pr", solu10.property(), 
				"-cd", SDFile.FORMAT_NAME, emptyFile.getAbsolutePath(),
				"-mt", PRECOMPUTE_REGRESSION,
				"--descriptors", "signatures:1:2",
				"-mo", outputModel.getAbsolutePath(),
				"-mn", "dasf",
		});

	}

	@Test
	public void testPrecomputeWithExclusiveTrainingSetsThatShouldFail_3() throws Exception {

		File outputModel = TestUtils.createTempFile("datafile", ".csr.jar");
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("Failed", "parsing", "data"));

		// =====================================
		// NORMAL TRAIN-FILE IS EMPTY
		// =====================================

		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-td" , CSVFile.FORMAT_NAME, emptyFile.getAbsolutePath(),
				"-md", CSVFile.FORMAT_NAME, tempPropExclusive.getAbsolutePath(),
				"-cd", CSVFile.FORMAT_NAME, tempCalibExclusive.getAbsolutePath(),
				"-pr", solu10.property(), 
				"-mt", PRECOMPUTE_REGRESSION,
				"--descriptors", "signatures:1:2",
				"-mo", outputModel.getAbsolutePath(),
				"-mn", "dasf",
		});
	}


	@Test
	public void testFilterOutlierMAX() throws Exception {
		File outputModel = TestUtils.createTempFile("datafile", ".csr.jar");

		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-td", regDuplicate.format(), regDuplicate.uri().toString(), 
				"-mt", PRECOMPUTE_REGRESSION,
				"--property", regDuplicate.property(),
				"-mo", outputModel.getAbsolutePath(),
				"-mn", "dasf",
				"--transformations", "filter_range:max=5",
		});
		//		printLogs();
		String[] loglines = systemOutRule.getLog().split("\n");

		boolean applyLineExists = false;
		for (String line: loglines) {
			String lowerCase = line.toLowerCase();
			if (lowerCase.startsWith( (" - applying " + new LabelRangeFilter().getName()).toLowerCase()))
				applyLineExists = true;
			if (lowerCase.matches("\\d+\\srecord.*?\\sremoved")) {
				Assert.assertEquals(1, Integer.parseInt(lowerCase.split("\\s",2)[0]));
			}
		}
		Assert.assertTrue(applyLineExists);
	}

	@Test
	public void testFilterNarrowRange() throws Exception {
		File outputModel = TestUtils.createTempFile("datafile", ".csr.jar");

		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-td", regDuplicate.format(), regDuplicate.uri().toString(),
				"-mt", PRECOMPUTE_REGRESSION,
				"--property", regDuplicate.property(),
				"-mo", outputModel.getAbsolutePath(),
				"-mn", "dasf",
				"--transformations", "filter-range:1:3",
		});
		//		printLogs();
		String[] loglines = systemOutRule.getLog().split("\n");

		boolean applyLineExists = false;
		for (String line: loglines) {
			String lowerCase = line.toLowerCase();
			if (lowerCase.startsWith(" - applying filter"))
				applyLineExists = true;
			if (lowerCase.matches("\\d+\\srecord.*?\\sremoved")) {
				Assert.assertEquals(5, Integer.parseInt(lowerCase.split("\\s",2)[0]));
			}
		}
		Assert.assertTrue(applyLineExists);
	}

	@Test
	public void testFilterOutlierMIN() throws Exception {
		File outputModel = TestUtils.createTempFile("datafile", ".csr.jar");

		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-td", regDuplicate.format(), regDuplicate.uri().toString(), //CSVFile.FORMAT_NAME, getURI("/resources/smiles_files/duplicates_synt_data.smi").getPath(),
				"-mt", PRECOMPUTE_REGRESSION,
				"--property", regDuplicate.property(),
				"-mo", outputModel.getAbsolutePath(),
				"-mn", "dasf",
				"--transformations", "filter_range:min=0",
		});
		//				printLogs();
		String[] loglines = systemOutRule.getLog().split("\n");

		boolean applyLineExists = false;
		for (String line: loglines) {
			String lowerCase = line.toLowerCase();
			if (lowerCase.startsWith(" - applying " + new LabelRangeFilter().getName().toLowerCase()))
				applyLineExists = true;
			if (lowerCase.matches("\\d+\\srecord.*?\\sremoved")) {
				Assert.assertEquals(3, Integer.parseInt(lowerCase.split("\\s",2)[0]));
			}
		}
		Assert.assertTrue(applyLineExists);
	}

	@Test
	public void testFilterOutlierFaultyArg() throws Exception {
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		File outputModel = TestUtils.createTempFile("datafile", ".csr.jar");

		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-td", regDuplicate.format(), regDuplicate.uri().toString(), //getURI("/resources/smiles_files/duplicates_synt_data.smi").getPath(),
				"-mt", PRECOMPUTE_REGRESSION,
				"--property", regDuplicate.property(),
				"-mo", outputModel.getAbsolutePath(),
				"-mn", "dasf",
				"--transformations", "range:1",
		});
	}

	@Test
	public void testDuplicatesReg() throws Exception {
		File outputModel = TestUtils.createTempFile("datafile", ".csr.jar");

		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-td", regDuplicate.format(), regDuplicate.uri().toString(),
				"-mt", PRECOMPUTE_REGRESSION,
				"--property", regDuplicate.property(),
				"-mo", outputModel.getAbsolutePath(),
				"-mn", "dasf",
				"--transformations", "keep-median-label",
		});
		//		printLogs();
		String[] loglines = systemOutRule.getLog().split("\n");

		boolean applyLineExists = false;
		for (String line: loglines) {
			String lowerCase = line.toLowerCase();
			if (lowerCase.startsWith(" - applying "+new KeepMedianLabel().getName().toLowerCase()))
				applyLineExists = true;
			if (lowerCase.matches("\\d+\\srecord.*?\\sremoved")) {
				Assert.assertEquals(3, Integer.parseInt(lowerCase.split("\\s",2)[0]));
			}
		}
		Assert.assertTrue(applyLineExists);
		//		printLogs();
	}

	@Test
	public void testDuplicatesClass() throws Exception {
		File outputModel = TestUtils.createTempFile("datafile", ".csr.jar");

		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-td", classDuplicate.format(), classDuplicate.uri().toString(),
				"-mt", PRECOMPUTE_CLASSIFICATION,
				"--property", classDuplicate.property(),
				"--labels", getLabelsArg(classDuplicate.labels(),' '),
				"-mo", outputModel.getAbsolutePath(),
				"-mn", "dasf",
				"--transformations", "voting",
		});
		//		printLogs();
		String[] loglines = systemOutRule.getLog().split("\n");

		boolean applyLineExists = false;
		for (String line: loglines) {
			String lowerCase = line.toLowerCase();
			if (lowerCase.startsWith(" - applying " + new UseVoting().getName().toLowerCase()))
				applyLineExists = true;
			if (lowerCase.matches("\\d+\\srecord.*?\\sremoved")) {
				Assert.assertEquals(4, Integer.parseInt(lowerCase.split("\\s",2)[0]));
			}
		}
		Assert.assertTrue(applyLineExists);
	}

	@Test
	public void testMultiColumn() throws Exception{
		CSVCmpdData csv = TestResources.Reg.getSolubility_10_multicol();
		// final String TRAIN_FILE_SMILES_MULTI_COLUMN = getFile("/resources/smiles_files/smiles_classification_multiColumn.smi").getAbsolutePath();
		// final String trainSMILES_MultiColumn_Property = "Activity";
		// final String labelsSMILES ="NEG,POS";
		//		File modelFile = new File("/Users/staffan/Desktop/test/models");
		File modelFile = TestUtils.createTempFile("data", ".jar");

		mockMain(
				Precompute.CMD_NAME,
				"-td", csv.format(), csv.uri().toString(),
				"-pr", csv.property(),
				"-mt", PRECOMPUTE_REGRESSION,
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "sdf"
				);
	}

	@Test
	public void testTrainDataWrongFormat() throws Exception {
		File modelFileSMILES = TestUtils.createTempFile("data", ".jar");
		final String labelsSMILES ="NEG,POS";
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("CSV", "parsing"));

		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-td" , CSVFile.FORMAT_NAME, ames10.uri().toString(),
				"-pr", ames10.property(),
				"-l", labelsSMILES,
				"-mt", PRECOMPUTE_CLASSIFICATION,
				"-mo", modelFileSMILES.getAbsolutePath(),
				"-mn", "model-name",
		});
	}

	@Test
	public void testCompressedInputFiles() throws Exception{

		File modelFile = TestUtils.createTempFile("acpmodel", ".svm.jar");
		//				File modelFile = new File("/Users/staffan/Desktop/acpmodel.jar");

		//		File modelFileSMILES = TestUtils.createTempFile("acpmodel_smiles", ".svm.jar");


		// Train with GZIPPED SDF-file
		systemOutRule.clearLog();
		systemErrRule.clearLog();
		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-td" , ames10.format(), TestResources.Cls.getAMES_10_gzip().uri().toString(),
				"-pr", ames10.property(),
				"-l", LABELS_STRING,
				"-mt", PRECOMPUTE_CLASSIFICATION,
				//				"--scorer", C_SVC.ALG_NAME,
				//				"-ss",strategy(RANDOM_SAMPLING, nrModels),
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "dsf",
		});

		Assert.assertTrue(systemErrRule.getLog()==null || systemErrRule.getLog().isEmpty());
	}

	@Test
	public void testUseFaultySignatureGenerator() throws Exception {
		String faultyGeneratorType = "blargh";
		CmpdData chang = TestResources.Reg.getChang();
		//Create temp files for model 
		File modelFile = TestUtils.createTempFile("acpmodel", ".jar"); 
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("signatureType", faultyGeneratorType, "invalid","arg", SignaturesDescriptor.DESCRIPTOR_NAME));
		//		exit.checkAssertionAfterwards(new PrintSysOutput(PRINT_USAGE));
		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_REGRESSION,
				"-td", chang.format(), chang.uri().toString(),
				"-mo", modelFile.getAbsolutePath(),
				"-mn", "model_name",
				"--descriptors", "signatures:signatureType=" +faultyGeneratorType,
				//				"-sg", "blargh",
		});
	}

	@Test
	public void testBBBDataset() throws Exception {
		File precompModelFile = TestUtils.createTempFile("pre", ".jar");
		CSVCmpdData bbb = TestResources.Cls.getBBB();
		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_CLASSIFICATION,
				"-td", bbb.format(), "delim="+bbb.delim(), bbb.uri().toString(),
				"-pr", bbb.property(),
				"--labels", getLabelsArg(bbb.labels(), ' '),//"penetrating non-penetrating",
				"-mo", precompModelFile.getAbsolutePath(),
				"-mn", "sdagas",
				//				"--percentiles", "0",
				//				"--progress-bar",
				"--time"
		});
	}

	@Test
	public void testCannotConvertTransformer() throws Exception {
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("robustscaler", "maxcol"));

		File precompModelFile = TestUtils.createTempFile("pre", ".jar");
		CSVCmpdData bbb = TestResources.Cls.getBBB();

		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_CLASSIFICATION,
				"-td", bbb.format(), "delim="+bbb.delim(), bbb.uri().toString(),
				"-pr", "Blood-Brain-Barrier Penetration",
				"--labels", "penetrating non-penetrating",
				"-mo", precompModelFile.getAbsolutePath(),
				"-mn", "sdagas",
				"--transform", "RobustScaler:maxCol=15",
				//				"--percentiles", "0",
				//				"--progress-bar",
				"--time"
		});
	}

	@Test
	public void testBadDescriptorInput() throws Exception {
		exit.expectSystemExitWithStatus(ExitStatus.USER_ERROR.code);
		exit.checkAssertionAfterwards(new AssertSysErrContainsString("signatures", "strange_param"));
		//			exit.checkAssertionAfterwards(new PrintSysOutput());
		CSVCmpdData bbb = TestResources.Cls.getBBB();

		File precompModelFile = TestUtils.createTempFile("pre", ".jar");
		mockMain(new String[] {
				Precompute.CMD_NAME,
				"-mt", PRECOMPUTE_CLASSIFICATION,
				"-td", bbb.format(), "delim="+bbb.delim(), bbb.uri().toString(),
				"-pr", bbb.property(),
				"--labels", getLabelsArg(bbb.labels()),
				"-d", "signatures:strange_param=2",
				"-mo", precompModelFile.getAbsolutePath(),
				"-mn", "sdagas",
				//				"--percentiles", "0",
				//				"--progress-bar",
				"--time"
		});
	}
	
	@Test
	public void testUserDefinedHeader() throws Exception {
		File precompModelFile = TestUtils.createTempFile("pre", ".jar");
//		LoggerUtils.setDebugMode(SYS_OUT);
		CSVCmpdData noHeader = TestResources.Reg.getSolubility_10_no_header_multicol();
		
		mockMain(Precompute.CMD_NAME,
				"--train-data", noHeader.format(), "header=SMILES,FLAG,COMMENT", noHeader.uri().toString(),
				// "--labels",getLabelsArg(noHeader.labels()),
				"-mt", PRECOMPUTE_REGRESSION,
				"--property", "FLAG",
				"-mo",precompModelFile.getAbsolutePath(),
				"-v");
//		printLogs();
	}
}

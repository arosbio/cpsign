/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cheminf.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.io.iterator.IteratingSDFReader;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import com.arosbio.chem.CDKConfigureAtomContainer;
import com.arosbio.chem.io.in.CSVChemFileReader;
import com.arosbio.chem.io.in.CSVFile;
import com.arosbio.chem.io.in.FailedRecord;
import com.arosbio.chem.io.in.JSONChemFileReader;
import com.arosbio.chem.io.in.JSONFile;
import com.arosbio.chem.io.in.SDFReader;
import com.arosbio.chem.io.in.SDFile;
import com.arosbio.chem.io.out.CSVWriter;
import com.arosbio.cheminf.data.ChemDataset.DescriptorCalcInfo;
import com.arosbio.cheminf.data.ChemDataset.NamedFeatureInfo;
import com.arosbio.cheminf.descriptors.ChemDescriptor;
import com.arosbio.cheminf.descriptors.DescriptorFactory;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.cheminf.descriptors.UserSuppliedDescriptor;
import com.arosbio.cheminf.descriptors.fp.FPDescriptor;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;
import com.arosbio.data.NamedLabels;
import com.arosbio.data.SparseFeature;
import com.arosbio.data.transform.feature_selection.DropColumnSelector;
import com.arosbio.data.transform.impute.SingleFeatureImputer;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.DataSink;
import com.arosbio.io.DataSource;
import com.arosbio.io.DataSource.DataEntry;
import com.arosbio.io.JarDataSink;
import com.arosbio.io.JarDataSource;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.MockFailingDescriptor;
import com.arosbio.testutils.TestChemDataLoader;
import com.arosbio.testutils.UnitTestBase;
import com.google.common.collect.Sets;

@Category(UnitTest.class)
public class TestChemDataset extends UnitTestBase {
	
	public CmpdData ames_mini = TestResources.Cls.getAMES_10();
	public NamedLabels ames_labels = new NamedLabels(ames_mini.labelsStr());

	public CSVCmpdData solu_10 = TestResources.Reg.getSolubility_10();

	@Test
	public void testFailingDescriptor() throws Exception {
		ChemDataset sp = new ChemDataset(new MockFailingDescriptor());
		sp.initializeDescriptors();
		DescriptorCalcInfo pi = sp.add(new SDFile(ames_mini.uri()).getIterator(),ames_mini.property(), ames_labels);
		
		Assert.assertEquals(10, pi.getNumSuccessfullyAdded()+pi.getFailedRecords().size());

	}

	@Test
	public void testSMILESwithMetalAtom() throws Exception {
		String smiles = "[Cu+2]12(N)(N)(N)(N)S(=O)(=O)O[Cu+2]34(N)(N)(N)(N)S(=O)(=O)O1O2O3O4";
		IAtomContainer mol = sp.parseSmiles(smiles);
		ChemDataset ds = new ChemDataset();
		ds.initializeDescriptors();
		ds.add(mol,1d);

		List<String> signatures = ds.getFeatureNames(true);

		boolean containsCopper = false;
		for(String s : signatures){
			if (s.contains("Cu")){
				containsCopper = true;
				break;
			}
		}
		Assert.assertTrue(containsCopper);
	}

	

	@Test
	public void testSaveToSinkPlain() throws Exception {
		ChemDataset sp = new ChemDataset(0,2);
		sp.initializeDescriptors();
		sp.add(new SDFile(ames_mini.uri()).getIterator(),ames_mini.property(),ames_labels);
		Assert.assertTrue(sp.getNumRecords()>5);

		File tmpJar = TestUtils.createTempFile("sigs", ".tmp");
		LoggerUtils.setDebugMode();
		try(DataSink sink = getJarDataSink(tmpJar)){
			sp.saveStateExceptRecords(sink, "hej", null);
			sp.saveToDataSink(sink, "hej2", null);
		}

		// Load
		ChemDataset loaded = new ChemDataset();
		DataSource jarSource = getJarDataSource(tmpJar);
		loaded.loadDescriptorsFromSource(jarSource, null);
		loaded.loadFromDataSource(jarSource, null);
		Assert.assertEquals(sp, loaded);

		// Load again with given basePath
		ChemDataset loaded2 = new ChemDataset();
		loaded2.loadDescriptorsFromSource(jarSource, "hej",null);
		loaded2.loadFromDataSource(jarSource, "hej2", null);
		Assert.assertEquals(sp, loaded2);
	}

	@Test
	public void testSaveToSinkEncrypted() throws Exception {
		EncryptionSpecification encSpec = getSpec("my password and salt");
		ChemDataset sp = new ChemDataset(0,2);
		sp.initializeDescriptors();
		sp.add(new SDFile(ames_mini.uri()).getIterator(),ames_mini.property(), ames_labels);
		Assert.assertTrue(sp.getNumRecords()>5);

		File tmpJar = TestUtils.createTempFile("sigs", ".tmp");
		LoggerUtils.setDebugMode();
		try(DataSink sink = getJarDataSink(tmpJar)){
			sp.saveStateExceptRecords(sink, "hej", encSpec);
			sp.saveToDataSink(sink, "hej2", encSpec);
		}

		// Load
		ChemDataset loaded = new ChemDataset();
		DataSource jarSource = getJarDataSource(tmpJar);
		loaded.loadDescriptorsFromSource(jarSource, encSpec);
		loaded.loadFromDataSource(jarSource, encSpec);
		Assert.assertEquals(sp, loaded);

		// Load again with given basePath
		ChemDataset loaded2 = new ChemDataset();
		loaded2.loadDescriptorsFromSource(jarSource, "hej",encSpec);
		loaded2.loadFromDataSource(jarSource, "hej2", encSpec);
		Assert.assertEquals(sp, loaded2);
	}

	@Test
	public void testAddFromSDF_REG() throws Exception {
		CmpdData chang = TestResources.Reg.getChang();
		genericTester(null, chang.uri(), chang.property(), false, DataType.SDF);
	}

	@Test
	public void testLoadMultipleFiles() throws Exception {
		ChemDataset sp = new ChemDataset();
		sp.initializeDescriptors();
		sp.add(new SDFile(ames_mini.uri()).getIterator(),ames_mini.property(), ames_labels);
		int fromFirstFile = sp.getDataset() .size();
		List<String> firstSignaturesCopy = getSignatures(sp); 

		CmpdData ames_json = TestResources.Cls.getAMES_10_json_bool();
		sp.add(new JSONFile(ames_json.uri()).getIterator(), ames_json.property(), new NamedLabels(ames_json.labelsStr()));

		Assert.assertEquals(fromFirstFile + 10, sp.getDataset() .size()); // 10 more molecules in new file
		List<String> newSigns = getSignatures(sp);
		Assert.assertTrue(firstSignaturesCopy.size() <= newSigns.size());
		for(int i=0; i<firstSignaturesCopy.size(); i++)
			Assert.assertEquals(firstSignaturesCopy.get(i), newSigns.get(i));

	}
	
	
	static List<String> getSignaturesList(SignaturesDescriptor sign){
		List<String> l = new ArrayList<>();
		for (String s : sign.getSignatures()) {
			l.add(s);
		}
		return l;
	}

	@Test
	public void testConvertToSparseFeatures() throws Exception {
		// Init the ChemDataset with signatures
		ChemDataset sp = new ChemDataset();
		sp.initializeDescriptors();
		sp.add(new SDFile(ames_mini.uri()).getIterator(),ames_mini.property(), ames_labels);
		int numSigs = getSignatures(sp).size();

		Iterator<IAtomContainer> mols = new CSVFile(solu_10.uri()).setDelimiter(solu_10.delim()).getIterator();
		Pair<List<FeatureVector>,DescriptorCalcInfo> sm = sp.convertToFeatureVector(mols);
//		List<List<SparseFeature>> sm = sp.convertToSparseFeatures(mols);
		//		System.out.println(sm);
		Assert.assertEquals(10, sm.getLeft().size());

		Assert.assertEquals("Number of signatures should not change",numSigs,getSignatures(sp).size());


		Iterator<IAtomContainer> mols2 = new CSVFile(solu_10.uri()).setDelimiter(solu_10.delim()).getIterator();
		List<IAtomContainer> molsList = new ArrayList<>();

		while(mols2.hasNext()){
			molsList.add(mols2.next());
		}
		Pair<List<FeatureVector>,DescriptorCalcInfo> sm2 = sp.convertToFeatureVector(molsList);
		
		Assert.assertEquals(sm.getLeft(), sm2.getLeft());
		Assert.assertEquals(sm.getRight().toString(), sm2.getRight().toString());

		Assert.assertEquals("Number of signatures should not change", numSigs, getSignatures(sp).size());
	}



	@Test
	public void testAddFromSDF_CLF() throws Exception{

		genericTester(ames_mini.labelsStr(), ames_mini.uri(), ames_mini.property(), true, DataType.SDF);
	}

	@Test
	public void testAddFromCSV_REG() throws Exception{
		CSVCmpdData solu500 = TestResources.Reg.getSolubility_500();
		genericTester(null, solu500.uri(), solu500.property(), false, DataType.CSV);
	}

	@Test
	public void testAddFromSDF_REG_ZIP() throws Exception  {
		ChemDataset sp = new ChemDataset();
		sp.initializeDescriptors();
		CmpdData sdfGZIP = TestResources.Reg.getChang_gzip();
		sp.add(new SDFile(sdfGZIP.uri()).getIterator(), sdfGZIP.property());
		Assert.assertEquals(34, sp.getNumRecords());
	}

	@Test
	public void testAddFromCSV_CLF() throws Exception {
		CSVCmpdData data = TestResources.Cls.getCox2();
		genericTester(data.labelsStr(), data.uri(), data.property(), true, DataType.CSV);
	}
	

	@Test
	public void testReadAndWriteSignatures() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, IllegalAccessException, InvalidKeyException{
		SignaturesDescriptor desc = new SignaturesDescriptor();
		desc.initialize();
		desc.calculateDescriptorsAndUpdate(getTestMol());
		
		List<String> sigs = getSignaturesList(desc);
		Assert.assertTrue(sigs.size()>4);
		

		// Write/Read as plain text
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		desc.writeSignatures(baos,false);
		ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
//		ChemDataset plain = new ChemDataset();
		SignaturesDescriptor plain = new SignaturesDescriptor();
		plain.readSignatures(bais);
		List<String> fromPlain = getSignaturesList(plain);

		// Write/Read as compressed
		ByteArrayOutputStream baosComp = new ByteArrayOutputStream();
		desc.writeSignatures(baosComp, true);
		ByteArrayInputStream baisComp = new ByteArrayInputStream(baosComp.toByteArray());
		SignaturesDescriptor comp = new SignaturesDescriptor();
		comp.readSignatures(baisComp);
		List<String> fromComp = getSignaturesList(comp);

		// Write/Read as encrypted
		EncryptionSpecification spec = getSpec("pwd");
		ByteArrayOutputStream baosEnc = new ByteArrayOutputStream();
		desc.writeSignaturesEncrypted(baosEnc, spec);
		ByteArrayInputStream baisEnc = new ByteArrayInputStream(baosEnc.toByteArray());
		SignaturesDescriptor enc = new SignaturesDescriptor();
		enc.readSignatures(baisEnc, spec);
		List<String> fromEnc = getSignaturesList(enc);

		// Verify that everything works
		Assert.assertEquals(sigs, fromPlain);
		Assert.assertEquals(sigs, fromComp);
		Assert.assertEquals(sigs, fromEnc);

		Assert.assertEquals(desc, plain);
		Assert.assertEquals(desc, comp);
		Assert.assertEquals(desc, enc);
	}

	@Test
	public void testReadAndWriteData() throws IllegalArgumentException, Exception{
		ChemDataset sprob = new ChemDataset(1,3);
		sprob.initializeDescriptors();
		sprob.add(new SDFile(ames_mini.uri()).getIterator(),ames_mini.property(), ames_labels);
		
		EncryptionSpecification spec = getSpec("password");
		List<DataRecord> data = sprob.getDataset() ;

		// Write/Read as plain text
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		sprob.getDataset().writeRecords(baos, false);
		ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
		ChemDataset plain = new ChemDataset();
		plain.getDataset().readRecords(bais, spec);
		List<DataRecord> fromPlainFeat = plain.getDataset() ;

		// Write/Read as compressed
		ByteArrayOutputStream baosComp = new ByteArrayOutputStream();
		sprob.getDataset().writeRecords(baosComp, true);
		ByteArrayInputStream baisComp = new ByteArrayInputStream(baosComp.toByteArray());
		ChemDataset comp = new ChemDataset();
		comp.getDataset().readRecords(baisComp,spec);
		List<DataRecord> fromCompFeat = comp.getDataset() ;

		// Write/Read as encrypted
		ByteArrayOutputStream baosEnc = new ByteArrayOutputStream();
		sprob.getDataset().writeRecords(baosEnc, spec);
		ByteArrayInputStream baisEnc = new ByteArrayInputStream(baosEnc.toByteArray());
		ChemDataset enc = new ChemDataset();
		enc.getDataset().readRecords(baisEnc, spec);
		List<DataRecord> fromEncFeat = enc.getDataset() ;

		// Verify that everything works (Data)
		Assert.assertEquals(true, datasetsEquals(data,fromPlainFeat));
		Assert.assertEquals(true, datasetsEquals(data,fromCompFeat));
		Assert.assertEquals(true, datasetsEquals(data,fromEncFeat));
	}

	public static boolean datasetsEquals(List<DataRecord> d1, List<DataRecord> d2){
		if(d1.size() != d2.size())
			return false;
		for(int i=0; i<d1.size(); i++){
			if(!d1.equals(d2))
				return false;
		}
		return true;
	}


	public void genericTester(List<String> labels, URI datafile, String property, boolean isClassification, DataType dtype) throws CDKException, IOException, CloneNotSupportedException, IllegalAccessException{

		ChemDataset spn = new ChemDataset(1,3);
		spn.initializeDescriptors();
		

			// New impl
			if(dtype == DataType.SDF && isClassification){
				spn.add(new SDFile(datafile).getIterator(), property, new NamedLabels(labels));
			} else if(dtype == DataType.SDF){
				spn.add(new SDFile(datafile).getIterator(), property);
			} else if(dtype == DataType.CSV && isClassification){
				spn.add(new CSVFile(datafile).setDelimiter('\t').getIterator(), property, new NamedLabels(labels));
			} else if(dtype == DataType.CSV){
				spn.add(new CSVFile(datafile).setDelimiter('\t').getIterator(), property);
			} else if (dtype == DataType.LIBSVM){
				try(InputStream dataStream = datafile.toURL().openStream()){
					spn.getDataset().readRecords(dataStream);
				}
				((SignaturesDescriptor)spn.getDescriptors().get(0)).readSignatures(this.getClass().getResourceAsStream(property));
			} else{
				throw new UnsupportedOperationException();
			}
		List<DataRecord> recsNew = spn.getDataset() ;
//		List<String> signNew = getSignatures(spn);

		List<ComparisonLP> fromNewImpl = new ArrayList<>();
		for(int i=0; i<recsNew.size(); i++){
			TreeMap<String, Double> features = new TreeMap<>();
//			FeatureVector dataset = recsNew.get(i).getFeatures();
//			for(int j=0; j<dataset.size(); j++){
//				features.put(signNew.get(dataset.get(j).getIndex()-1), dataset.get(j).getValue());
//			} // TODO
			fromNewImpl.add(new ComparisonLP((double) i, features));
		}

	}

	public enum DataType {
		SDF, CSV,JSON, LIBSVM
	}

	/**
	 * Help-class that facilitate the comparison between java and spark/scala-results.
	 * Convert to this common object and just check that they equals.
	 * @author staffan
	 *
	 */
	public static class ComparisonLP implements Comparable<ComparisonLP>{
		private TreeMap<String, Double> features;
		private double value;

		public ComparisonLP(double value, TreeMap<String,Double> features){
			this.features = features;
			this.value = value;
		}


		public boolean equals(ComparisonLP other){
			if(this.value != other.value){
				return false;
			}
			if(! this.features.equals(other.features)){
				return false;
			}
			return true;

		}

		public String toString(){
			String str = ""+value;
			for(String key: features.keySet()){
				str += " " + key + ":" + features.get(key);
			}

			return str;
		}

		@Override
		public int compareTo(ComparisonLP o) {
			return (int) (this.value - o.value);
		}

		public static ComparisonLP fromISparseFeature(SparseFeature[] arr, Double value, List<String> mapping){
			TreeMap<String,Double> feat = new TreeMap<>();
			for(int i=0; i<arr.length; i++){
				feat.put(mapping.get(arr[i].getIndex()-1), arr[i].getValue());
			}
			return new ComparisonLP(value, feat);
		}
	}

	public void testSpeedup() throws Exception, IOException, CloneNotSupportedException{
		String file = "/resources/cleanData_luciaOrig_0.0_2011-01-01.sdf";
		InputStream stream1 = this.getClass().getResourceAsStream(file);
		//		InputStream stream2 = this.getClass().getResourceAsStream(file);
		String property = "Activity";
		List<String> labels = Arrays.asList("NEG", "POS");
		File dataFile = new File(this.getClass().getResource(file).getFile());

		// Run the new sg
		Long start2 = System.currentTimeMillis();
		ChemDataset spn = new ChemDataset(0,5);
		spn.add(new SDFile(dataFile.toURI()).getIterator(), property, new NamedLabels(labels));
		Long timeNew = System.currentTimeMillis() - start2;


		// First run the old sg
		Long start = System.currentTimeMillis();
		Long timeOld = System.currentTimeMillis() - start;
		stream1.close();

		System.out.println("Time old: " + timeOld + "\nTime new: " + timeNew);
	}
	
	@Test
	public void computeDescriptorsBigDataFile() throws Exception {
		CmpdData ames = TestResources.Cls.getAMES_1337();
		ChemDataset sp = new ChemDataset();
		sp.initializeDescriptors();
		DescriptorCalcInfo info =  sp.add(new SDFReader(ames.url().openStream()), ames.property(), new NamedLabels(ames.labelsStr()));
		System.err.println(info);
		Assert.assertEquals(1337, sp.getNumRecords() + info.getFailedRecords().size());
	}

	@Test
	public void testJoin2Problems() throws IllegalArgumentException, Exception{

		ChemDataset sp1 = new ChemDataset();
		sp1.initializeDescriptors();
		sp1.add(new SDFile(ames_mini.uri()).getIterator(), ames_mini.property(), ames_labels);
//		sp1.fromChemFile(new File(MINI_FILE_PATH).toURI(), PROPERTY, AMES_LABELS);
		int sp1_size = sp1.getDataset() .size();

		ChemDataset sp1_clone = sp1.clone();

		ChemDataset sp2 = new ChemDataset(sp1.getDescriptors().get(0).clone());
		sp2.initializeDescriptors();
		sp2.add(new SDFile(ames_mini.uri()).getIterator(), ames_mini.property(), ames_labels);
//		sp2.fromChemFile(new File(SOLUBILITY_10_FILE_PATH).toURI(), null);
		int sp2_size = sp2.getDataset() .size();

		sp1.joinShallow(sp2);

		Assert.assertEquals(sp1_size + sp2_size, sp1.getDataset() .size());


		//		Assert.assertEquals(sp1_clone.getY(), sp1.getY().subList(0, sp1_size));
		//		Assert.assertEquals(sp2.getY(), sp1.getY().subList(sp1_size, sp1.getY().size()));	

		int i;
		for(i=0; i<sp1_size; i++){
			Assert.assertEquals(sp1_clone.getDataset() .get(i), sp1.getDataset() .get(i));
		}

		for(int j=0; j<sp2_size; j++){
			Assert.assertEquals(sp2.getDataset() .get(j), sp1.getDataset() .get(j+i));
		}
	}

//	@Test
//	public void testCheckMerge() throws Exception {
//		ChemDataset spAMES = new ChemDataset();
//		spAMES.add(new SDFile(getURIFromFullPath(AmesBinaryClass.MINI_FILE_PATH)).getIterator(), AmesBinaryClass.PROPERTY, AmesBinaryClass.AMES_LABELS_NL);
////		spAMES.fromChemFile(new File(MINI_FILE_PATH).toURI(),PROPERTY, AMES_LABELS);
//		ChemDataset spAMES_2 = spAMES.clone();
//
//		Assert.assertTrue(spAMES.checkMergableWithFailure(spAMES_2));
//
//		// Add a few signatures to the end should be fine!
//		List<String> newSignatures = new ArrayList<>(spAMES.getSignatures());
//		newSignatures.addAll(Arrays.asList("CCcRT", "dsfads"));
//		spAMES.setSignatures(newSignatures);
//		System.out.println(spAMES.getSignatures());
//		Assert.assertTrue(spAMES.checkMergable(spAMES_2));
//
//		//Add some to the other (so they're not matching) should fail!
//		List<String> newSignatures_2 = new ArrayList<>(spAMES_2.getSignatures());
//		newSignatures_2.addAll(Arrays.asList("Cadssda", "dsfads213"));
//		spAMES_2.setSignatures(newSignatures_2);
//		System.out.println(spAMES_2.getSignatures());
//
//		Assert.assertFalse(spAMES.checkMergable(spAMES_2));	
//
//		// different signatures heights should fail
//		ChemDataset sp12 = new ChemDataset(1,2);
//		sp12.setSignatures(Arrays.asList("CC"));
//		ChemDataset sp13 = new ChemDataset(1,3);
//		sp13.setSignatures(Arrays.asList("CC"));
//		Assert.assertFalse(sp12.checkMergable(sp13));
//		sp13.setStartHeight(0);
//		sp13.setEndHeight(2);
//		Assert.assertFalse(sp12.checkMergable(sp13));
//
//		//		sp13.setStartHeight(1);
//		//		sp13.setY(Arrays.asList(new Double(0.0), new Double(1.5)));
//		try{
//			sp12.checkMergableWithFailure(sp13);
//			Assert.fail();
//		} catch(Exception e) {}
//
//	}


	@Test
	public void testCompressSignatures() throws Exception {
		ChemDataset prob = new ChemDataset();
		prob.initializeDescriptors();
		prob.add(new SDFile(ames_mini.uri()).getIterator(), ames_mini.property(), ames_labels);
		SignaturesDescriptor d = (SignaturesDescriptor)prob.getDescriptors().get(0);
//		prob.fromChemFile(new File(MINI_FILE_PATH).toURI(), PROPERTY, AMES_LABELS);
		ByteArrayOutputStream baosGZ = new ByteArrayOutputStream();
		d.writeSignatures(baosGZ, true);

		ByteArrayOutputStream baosPLAIN = new ByteArrayOutputStream();
		d.writeSignatures(baosPLAIN, false);

		//		System.out.println(baosPLAIN);
		//		System.out.println(baosGZ);

		Assert.assertTrue(baosPLAIN.toString().length() > 2* baosGZ.toString().length());
	}

	
	@Test
	public void testGetStartIndexSignaturesDescr() throws Exception {
		ChemDataset cp = new ChemDataset();
		Assert.assertEquals(0, cp.getSignaturesDescriptorStartIndex());
		cp.setDescriptors(new UserSuppliedDescriptor("some-prop"));
		cp.initializeDescriptors();
		Assert.assertEquals(-1, cp.getSignaturesDescriptorStartIndex());
		
		cp = new ChemDataset();
		List<ChemDescriptor> descs = DescriptorFactory.getCDKDescriptorsNo3D().subList(1, 5);
//		for (ChemDescriptor d : DescriptorFactory.getInstance().getDescriptorsList())
//			SYS_OUT.println(d);
		descs.add(new SignaturesDescriptor());
//		SYS_ERR.println(descs);
		cp.setDescriptors(descs);
		cp.initializeDescriptors();
		cp.add(getTestMol(),1);
		
		// Manual check for index
		int expectedInd = 0;
		for (ChemDescriptor d : cp.getDescriptors()) {
			if (d instanceof SignaturesDescriptor)
				break;
			expectedInd += d.getLength();
//			SYS_ERR.println(d + ": " + d.getLength());
		}
		
		Assert.assertEquals(expectedInd, cp.getSignaturesDescriptorStartIndex()); // First it's at index 13
		
		cp.apply(new DropColumnSelector(Sets.newHashSet(1, 3, 4)));
		Assert.assertEquals(expectedInd-3, cp.getSignaturesDescriptorStartIndex()); // removed 3 indices should be 10 now
		
		cp.apply(new DropColumnSelector(Sets.newHashSet(0,2, 5, 30, 20))); // removed 2 ahead of the signatures and the remaining is after the start
		Assert.assertEquals(expectedInd-6, cp.getSignaturesDescriptorStartIndex());
		
//		SYS_ERR.println(cp.getSignaturesDescriptorStartIndex());
	}

	@Test
	public void testListFeatureNames() throws Exception {
		ChemDataset cp = new ChemDataset();
		
		cp.setDescriptors(DescriptorFactory.getCDKDescriptorsNo3D());
		cp.initializeDescriptors();
		cp.add(getTestMol(), 0);
		
		List<String> origNames = new ArrayList<>(cp.getFeatureNames(false));
		
		cp.apply(new DropColumnSelector(Sets.newHashSet(0, 3, 5, origNames.size()-1)));
		
		// 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 ..  (size - 1)     original indices 
		// 1 2 4 6 7 8 9 10 11 12 13 14 15 .. (size - 2)			indices left after first
		List<String> newNames = cp.getFeatureNames(false);
		// SYS_ERR.println(newNames);
		Assert.assertEquals(origNames.size() - 4, newNames.size());
		Assert.assertEquals(origNames.get(1), newNames.get(0));
		Assert.assertEquals(origNames.get(2), newNames.get(1));
		Assert.assertEquals(origNames.get(origNames.size()-2), newNames.get(newNames.size()-1));

		// Remove some more features with a second transformer
		cp.apply(new DropColumnSelector(6,9,10,11,newNames.size()-1));
		// 1 2 4 6 7 8 10 11 15 .. (size - 3)     					indices left after second

		newNames = cp.getFeatureNames(false);
		// SYS_ERR.println(newNames);
		Assert.assertEquals(origNames.size() - 4 - 5, newNames.size());
		Assert.assertEquals(origNames.get(1), newNames.get(0)); // not changed since above
		Assert.assertEquals(origNames.get(2), newNames.get(1)); // not changed since above
		Assert.assertEquals(origNames.get(4), newNames.get(2));
		Assert.assertEquals(origNames.get(6), newNames.get(3));
		Assert.assertEquals(origNames.get(7), newNames.get(4));
		Assert.assertEquals(origNames.get(8), newNames.get(5));
		Assert.assertEquals(origNames.get(10), newNames.get(6));
		Assert.assertEquals(origNames.get(11), newNames.get(7));
		Assert.assertEquals(origNames.get(15), newNames.get(8));
		Assert.assertEquals(origNames.get(origNames.size()-3), newNames.get(newNames.size()-1)); // removed the last one 2 times


		
	}

	@Test
	public void testSaveLoadChemProblem() throws Exception {
		int startHeight = 2;
		int endHeight = 4;
		ChemDataset cp = new ChemDataset(startHeight, endHeight);
		cp.initializeDescriptors();
		CSVCmpdData solu100 = TestResources.Reg.getSolubility_100();
		CSVFile csv= new CSVFile(solu100.uri()).setDelimiter(solu100.delim());

		try (CSVChemFileReader molsIter = csv.getIterator()){
			cp.add(molsIter, solu100.property());
		}

		File tmpJar = TestUtils.createTempFile("chem_problem", ".jar");
//		File tmpJar = new File("/Users/staffan/Desktop/chem.jar");

		try(
				OutputStream os = new FileOutputStream(tmpJar);
				JarOutputStream jar = new JarOutputStream(os);) {
			cp.saveToDataSink(new JarDataSink(jar), null, null);
		}
//		SYS_ERR.println("Finished writing ChemProblem to file: " + tmpJar);
		try(JarDataSource jsd = new JarDataSource(new JarFile(tmpJar))){
			Iterator<DataEntry> ents = jsd.entries();
			while (ents.hasNext()) {
				@SuppressWarnings("unused")
				DataEntry ent = ents.next();
//				SYS_ERR.println(ent.getName());
			}
		}
		
		// Load only the descriptors
		ChemDataset loaded = new ChemDataset();
		try (JarFile jar = new JarFile(tmpJar)){
			loaded.loadDescriptorsFromSource(new JarDataSource(jar), null);
		}
		
		Assert.assertEquals(cp.getDescriptors(), loaded.getDescriptors());

//		LoggerUtils.addStreamAppenderToRootLogger(SYS_ERR);
		// Load the entire ChemDataset
		try (JarFile jar = new JarFile(tmpJar)){
			loaded.loadFromDataSource(new JarDataSource(jar), null);
		}
		
		Assert.assertEquals(cp, loaded);
		
	}
	
	@Test
	public void testWithUserDefinedSDF() throws Exception {
		CmpdData herg = TestResources.Cls.getHERG();
		SDFile sdf = new SDFile(herg.uri());
		ChemDataset cp = new ChemDataset();
		cp.setDescriptors(new UserSuppliedDescriptor("IC50"));
		
		cp.initializeDescriptors();
		
		cp.add(sdf.getIterator(), "class");
		
		List<ChemDescriptor> descs = cp.getDescriptors();
		
		Assert.assertEquals(1,descs.get(0).getLength());
	}
	
	@Test
	public void testWithUserDefinedCSV_MANY() throws Exception {
		
		CSVCmpdData csv = TestResources.Cls.getAMES_126_chem_desc();
		ChemDataset ds = new ChemDataset(new UserSuppliedDescriptor("all","-smiles","-cdk_Title","-Molecular Signature","-Ames test categorisation"));
		ds.initializeDescriptors();
		CSVFile file = new CSVFile(csv.uri()).setDelimiter(csv.delim());
		DescriptorCalcInfo info = ds.add(file.getIterator(),csv.property(), new NamedLabels(csv.labelsStr()));

		Assert.assertEquals(126, ds.getNumRecords() + info.getFailedRecords().size());
		Assert.assertTrue("Most records should work",ds.getNumRecords()> 100);
		
		// SYS_ERR.println(ds.getFeatureNames(false));
		Assert.assertEquals(36, ds.getNumAttributes());
	}
	
	@Test
	public void testSalts() throws Exception {
		CSVFile csv= new CSVFile(solu_10.uri()).setDelimiter(solu_10.delim());
		
		try (CSVChemFileReader molsIter = csv.getIterator()){
			IAtomContainer mol = molsIter.next();
			CDKConfigureAtomContainer.configMolecule(mol);
			SignaturesDescriptor sd = new SignaturesDescriptor();
			sd.initialize();
//			System.err.println(sd.generateSignatures(mol));
		}
//		printLogs();
	}
	
	
	
	@Test
	public void testPhysChem() throws Exception {
		ChemDataset cp = new ChemDataset();
		
		
		List<ChemDescriptor> desc = DescriptorFactory.getInstance().getDescriptorsList();
		Set<ChemDescriptor> descSet = new HashSet<>(); 
		
		int nAttr = 0;
		for (ChemDescriptor d: desc) {
			d.initialize();
			// Skip the SignaturesDescriptor - it doesn't have a fixed size!
			if (d instanceof SignaturesDescriptor)
				continue;
			// Fingerprints are also sparse - skip
			if (d instanceof FPDescriptor)
				continue;
			nAttr += d.getLength();
			descSet.add(d);
		}
		
		cp.setDescriptors(descSet);
		Assert.assertTrue(descSet.size() > 10);
		
		Assert.assertEquals(descSet, new HashSet<>(cp.getDescriptors()));
//		LoggerUtils.setDebugMode(SYS_OUT);
//		SYS_ERR.println(getURIFromFullPath(RegressionSolubility.SOLUBILITY_10_FILE_PATH));
		CSVFile csv= new CSVFile(solu_10.uri()).setDelimiter(solu_10.delim());
//		LoggerUtils.addStreamAppenderToRootLogger(SYS_ERR);
		try (CSVChemFileReader molsIter = csv.getIterator()){
			cp.add(molsIter, solu_10.property());
		}
		
		assertFixedNumberOfAttributes(cp.getDataset(), nAttr);
		
		
		// Save and load!
		File tmpJar = TestUtils.createTempFile("chem_problem", ".jar");
		try(
				OutputStream os = new FileOutputStream(tmpJar);
				JarOutputStream jar = new JarOutputStream(os);) {
			cp.saveToDataSink(new JarDataSink(jar), null, null);
		}
		
		ChemDataset loaded = new ChemDataset();
		try (JarFile jar = new JarFile(tmpJar)){
			loaded.loadFromDataSource(new JarDataSource(jar), null);
		}
		
		Assert.assertEquals(cp.getProperties(), loaded.getProperties());
	}
	
	private static void assertFixedNumberOfAttributes(SubSet data, int numAttr) {
		for (DataRecord r : data) {
			FeatureVector f = r.getFeatures();
			Assert.assertEquals(0, f.getSmallestFeatureIndex());
			Assert.assertEquals(numAttr, f.getNumExplicitFeatures());
//			SparseFeature last = f.get(f.getLength())-1); // TODO - what should be here?
//			Assert.assertEquals(numAttr, last.getIndex());
		}
	}
	
	@Test
	public void testFailingDueToHAC() throws Exception{
		String badSMILES = "CC1C(C(CC(O1)OC2C(OC(CC2O)OC3CC(CC4=C(C5=C(C(=C34)O)C(=O)C6=CC=CC=C6C5=O)O)(C(=O)CO)O)C)N)O";
		ChemDataset cd = new ChemDataset();
		cd.initializeDescriptors();
		SmilesParser sp = new SmilesParser(SilentChemObjectBuilder.getInstance());
		cd.add(sp.parseSmiles(badSMILES), 0);
		
		Iterator<Pair<IAtomContainer,Double>> data = new ArrayList<Pair<IAtomContainer,Double>>(Arrays.asList(
				ImmutablePair.of(sp.parseSmiles(TEST_SMILES), 9d),
				ImmutablePair.of(sp.parseSmiles(TEST_SMILES_2), 9d),
				ImmutablePair.of(sp.parseSmiles(badSMILES), 9d)
				)).iterator();
		DescriptorCalcInfo pi = cd.add(data);
		Assert.assertTrue(pi.getFailedRecords().isEmpty());
//		SYS_ERR.println(pi);
	}
	
	@Test
	public void testUsingBadDescriptor() throws Exception {
		List<ChemDescriptor> desc = new ArrayList<>();
		desc.add(DescriptorFactory.getInstance().getDescriptor("AromaticAtomsCountDescriptor"));
		desc.add(new MockFailingDescriptor());
		desc.add(DescriptorFactory.getInstance().getDescriptor("AtomCountDescriptor"));
		
		ChemDataset ds = new ChemDataset(desc);
		ds.initializeDescriptors();

		CmpdData chang = TestResources.Reg.getChang();
		try (
				InputStream in = chang.url().openStream(); 
				IteratingSDFReader iterator = new IteratingSDFReader(in, SilentChemObjectBuilder.getInstance())){
			DescriptorCalcInfo info = ds.add(iterator, chang.property());
			System.err.println(info);
			if (info.getFailedRecords() != null)
				for (FailedRecord fr: info.getFailedRecords())
					System.out.println(fr);
			for (DataRecord r : ds.getDataset()) {
				System.err.println(r);
			}
		}
		// with a 20% chance of failure we should have some missing ones
		Assert.assertTrue(DataUtils.containsMissingFeatures(ds));
		
		// Verify that when computing new ones they should have missing values as well! (but still work!)
		try (
				InputStream in = TestResources.Reg.getSolubility_100().url().openStream();
				InputStreamReader reader = new InputStreamReader(in);
				CSVChemFileReader iterator = new CSVChemFileReader(CSVFormat.TDF.builder().setHeader().setSkipHeaderRecord(true).build(), reader);){
			
//			while (iterator.hasNext()) {
			Pair<List<FeatureVector>,DescriptorCalcInfo> descriptors = ds.convertToFeatureVector(iterator);
//			}
			Assert.assertTrue(DataUtils.containMissingFeatures(descriptors.getLeft()));
			for (FeatureVector v : descriptors.getLeft())
				System.err.println(v);
			
		}
		
		// ======================================================
		// APPLY TRANSFORMATIONS TO REMOVE MISSING FEATURES
		// ======================================================
		
		// So we add the SingleFeatureImputer
		ds.apply(new SingleFeatureImputer());
		
		for (DataRecord r : ds.getDataset()) {
			System.err.println(r);
		}
		// Now there should be no missing features!
		Assert.assertFalse(DataUtils.containsMissingFeatures(ds));
		
		// Coooool! now lets simply check what happens when converting some new molecules - the impute transformer should fix any missing features!
		System.err.println("\nNow trying convert new data set");
		
		try (
				InputStream in = TestResources.Reg.getSolubility_10().url().openStream();
				InputStreamReader reader = new InputStreamReader(in);
				CSVChemFileReader iterator = new CSVChemFileReader(CSVFormat.TDF.builder().setHeader().setSkipHeaderRecord(true).build(), reader);){ // withFirstRecordAsHeader()
			
//			while (iterator.hasNext()) {
			Pair<List<FeatureVector>,DescriptorCalcInfo> descriptors = ds.convertToFeatureVector(iterator);
			Assert.assertFalse(DataUtils.containMissingFeatures(descriptors.getLeft()));
//			}
			for (FeatureVector v : descriptors.getLeft())
				System.err.println(v);
			
			
		}
		
//		printLogs();
	}

	@Test
	public void testJsonOneLineInput() throws Exception {
		CmpdData json = TestResources.Reg.getChang_json_no_indent();
		ChemDataset dataset = new ChemDataset();
		dataset.initializeDescriptors();

		try (InputStream stream = json.url().openStream(); 
			JSONChemFileReader reader = new JSONChemFileReader(stream);) {
			DescriptorCalcInfo info = dataset.add(reader, json.property());
			Assert.assertEquals(json.numValidRecords(), info.getNumSuccessfullyAdded());
			Assert.assertEquals(json.numInvalidRecords(), info.getFailedRecords().size());
		}
	}

	@Test
	public void testFeaturesInfo() throws Exception {
		// Only signatures 
		ChemDataset data = TestChemDataLoader.loadDataset(TestResources.Cls.getAMES_126());
		List<NamedFeatureInfo> info = data.getFeaturesInfo(false);
		Assert.assertEquals(0, info.size());
		info = data.getFeaturesInfo(true);
		Assert.assertEquals(data.getNumAttributes(), info.size());
		List<String> fNames = data.getDescriptors().get(0).getFeatureNames();
		Assert.assertEquals(fNames.size(), info.size());
		for (NamedFeatureInfo f : info){
			Assert.assertEquals(fNames.get(f.index), f.featureName);
		}
		
		// With no signatures (CDK features)
		DescriptorFactory fac = DescriptorFactory.getInstance();
		ChemDescriptor[] descs = new ChemDescriptor[]{fac.getDescriptorFuzzyMatch("AminoAcidCountDescriptor"),
			fac.getDescriptorFuzzyMatch("WeightDescriptor"),
			fac.getDescriptorFuzzyMatch("SmallRingDescriptor"),
			fac.getDescriptorFuzzyMatch("ALOGPDescriptor"),
			fac.getDescriptorFuzzyMatch("BPolDescriptor")};
		data = TestChemDataLoader.loadDatasetWithInfo(TestResources.Cls.getAMES_10(), descs).getLeft();
		info = data.getFeaturesInfo(false);
		Assert.assertEquals(data.getNumAttributes(), info.size());
		fNames = data.getFeatureNames(false); 
		Assert.assertEquals(fNames.size(), info.size());
		for (NamedFeatureInfo f : info){
			Assert.assertEquals(fNames.get(f.index), f.featureName);
		}

		// With no signatures (user-defined ones form a CSV file)
		fNames = Arrays.asList("bpol","nRings3","nRings4","ALogp2","nAromBlocks","nA","nC","ALogP","nD","nE","nF","nG","nH","nI","nAromRings");
		data = TestChemDataLoader.loadDatasetWithInfo(TestResources.Cls.getAMES_10(), 
			new UserSuppliedDescriptor(new ArrayList<>(fNames))).getLeft();
		info = data.getFeaturesInfo(false);
		Assert.assertEquals(data.getNumAttributes(), info.size());
		Assert.assertEquals(fNames.size(), info.size());
		for (NamedFeatureInfo f : info){
			Assert.assertEquals(fNames.get(f.index).toUpperCase(), f.featureName);
		}

		// Combination of signatures and user-defined from CSV
		data = TestChemDataLoader.loadDatasetWithInfo(TestResources.Cls.getAMES_10(), 
			new UserSuppliedDescriptor(new ArrayList<>(fNames)),new SignaturesDescriptor()).getLeft();
		info = data.getFeaturesInfo(false);
		Assert.assertTrue(info.size() < data.getNumAttributes());

		info = data.getFeaturesInfo(true);
		Assert.assertEquals(data.getNumAttributes(), info.size());
		fNames = data.getFeatureNames(true); 
		Assert.assertEquals(fNames.size(), info.size());
		for (NamedFeatureInfo f : info){
			Assert.assertTrue(fNames.get(f.index).equalsIgnoreCase(f.featureName));
		}

	}
	

	// @Test
	public void generateCSV() throws Exception {
		DescriptorFactory fac = DescriptorFactory.getInstance();
		// List<ChemDescriptor> descs = DescriptorFactory.getCDKDescriptorsNo3D();
		// for(ChemDescriptor d : descs){
		// 	System.err.println(d);
		// }
		// printLogs();
		List<ChemDescriptor> descs = Arrays.asList(fac.getDescriptorFuzzyMatch("AminoAcidCountDescriptor"),
			fac.getDescriptorFuzzyMatch("WeightDescriptor"),
			fac.getDescriptorFuzzyMatch("SmallRingDescriptor"),
			fac.getDescriptorFuzzyMatch("ALOGPDescriptor"),
			fac.getDescriptorFuzzyMatch("BPolDescriptor"));

		List<IAtomContainer> mols = new ArrayList<>();
		CmpdData data = TestResources.Cls.getAMES_126();
		Iterator<IAtomContainer> iter = new SDFile(data.uri()).getIterator();
		IAtomContainer m = null;
		while(iter.hasNext()){
			m = iter.next();
			
			Map<Object,Object> calcFeats = new HashMap<>();
		
			for (ChemDescriptor d : descs){
				if (!d.isReady())
					d.initialize();
				List<String> fNames = d.getFeatureNames();
				List<SparseFeature> feats = d.calculateDescriptors(m);
				Assert.assertEquals(fNames.size(), feats.size());

				for (int i=0; i<fNames.size(); i++){
					calcFeats.put(fNames.get(i), feats.get(i).getValue());
				}
				
			}
			m.addProperties(calcFeats);

			mols.add(m);
		}
		SYS_ERR.println("NUm mols: " + mols.size());
		// LoggerUtils.setDebugMode(SYS_ERR);
		try(
			OutputStream os = new FileOutputStream("/Users/star/Desktop/chem.csv");
			CSVWriter writer = new CSVWriter(os)){
			for (IAtomContainer mol : mols){
				writer.writeRecord(mol);
				// SYS_ERR.println(mol.getProperties());
			}

				
		}
		
		// for (IAtomContainer mol : mols){
		// 	System.err.println(mol.getProperties());
		// }
		printLogs();

	}
	
}

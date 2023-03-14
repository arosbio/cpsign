/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cheminf.descriptors;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.apache.commons.csv.CSVFormat;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.CDKConfigureAtomContainer;
import com.arosbio.chem.io.in.CSVChemFileReader;
import com.arosbio.cheminf.descriptors.UserSuppliedDescriptor.SortingOrder;
import com.arosbio.cheminf.descriptors.fp.ECFP4;
import com.arosbio.cheminf.descriptors.fp.ECFP6;
import com.arosbio.cheminf.descriptors.fp.MACCS;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.data.MissingValueFeature;
import com.arosbio.data.SparseFeature;
import com.arosbio.io.DataSink;
import com.arosbio.io.JarDataSink;
import com.arosbio.io.JarDataSource;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.MockFailingDescriptor;
import com.arosbio.testutils.UnitTestBase;
import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableMap;

import ch.qos.logback.classic.Logger;

@Category(UnitTest.class)
public class TestDescriptors extends UnitTestBase {

	boolean print = false;

	@Test
	public void testUserSuppliedDescriptor()
			throws IllegalStateException, CDKException, IOException, InvalidKeyException {
		UserSuppliedDescriptor desc = new UserSuppliedDescriptor("feat1");
		IAtomContainer cont = getTestMol();
		try {
			desc.calculateDescriptors(cont);
			Assert.fail();
		} catch (IllegalStateException e) {
		}

		desc.initialize();

		try {
			List<SparseFeature> fsfs = desc.calculateDescriptors(cont);
			Assert.assertTrue(fsfs.size() == 1);
			Assert.assertTrue(fsfs.get(0) instanceof MissingValueFeature);
			// Assert.fail();
		} catch (Exception e) {
		}

		cont.setProperty("feat1", "this_is_invalid");
		List<SparseFeature> missingValFeats = desc.calculateDescriptors(cont);
		Assert.assertTrue(missingValFeats.get(0) instanceof MissingValueFeature);

		cont.setProperty("feat1", "2.0");
		List<SparseFeature> feats = desc.calculateDescriptors(cont);
		Assert.assertEquals(1, feats.size());
		Assert.assertEquals(0, feats.get(0).getIndex());
		Assert.assertEquals(2.0, feats.get(0).getValue(), 0.000001);

		File saveFile = TestUtils.createTempFile("toStore", ".something");
		try (
				JarOutputStream jar = new JarOutputStream(new FileOutputStream(saveFile));
				DataSink sink = new JarDataSink(jar)) {
			desc.saveDescriptorToSink(sink, null, null);
		}

		UserSuppliedDescriptor loaded = new UserSuppliedDescriptor();
		try (
				JarFile jar = new JarFile(saveFile);
				JarDataSource src = new JarDataSource(jar);) {
			loaded.loadDescriptorFromSource(src, null, null);

		}

		// SYS_ERR.println(loaded.getProperties());
		List<SparseFeature> feats2 = loaded.calculateDescriptorsAndUpdate(cont);
		Assert.assertEquals(feats, feats2);
	}

	@Test
	public void testUserDefOrder() throws Exception {
		CSVCmpdData toy_set = TestResources.Reg.getToy_many_cols();
		CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(toy_set.delim()).setHeader().build();
		// LoggerUtils.setDebugMode(SYS_ERR);
		UserSuppliedDescriptor desc = new UserSuppliedDescriptor("all", "-#SMILES", "-SAMPLE_ID", "-RESPONSE VALUE");
		desc.initialize();

		try (CSVChemFileReader smilesReader = new CSVChemFileReader(format,
				new InputStreamReader(toy_set.url().openStream()));) {
			while (smilesReader.hasNext()) {
				desc.calculateDescriptors(smilesReader.next());
			}
		}

		Assert.assertEquals("Note not in true alphabetical order",
				Arrays.asList("A", "B", "C", "D", "E", "F", "G", "I", "H"), desc.getPropertyNames());

		// ALPHABETICAL
		desc = new UserSuppliedDescriptor("all", "-#SMILES", "-SAMPLE_ID", "-RESPONSE VALUE");
		desc.setSortingOrder(SortingOrder.ALPHABETICAL);
		desc.initialize();

		

		try (CSVChemFileReader smilesReader = new CSVChemFileReader(format,
				new InputStreamReader(toy_set.url().openStream()));) {
			while (smilesReader.hasNext()) {
				desc.calculateDescriptors(smilesReader.next());
			}
		}

		Assert.assertEquals("Alphabetical order", Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H", "I"),
				desc.getPropertyNames());

		// REVERSED ALPHABETICAL
		desc = new UserSuppliedDescriptor("all", "-#SMILES", "-SAMPLE_ID", "-RESPONSE VALUE");
		desc.setSortingOrder(SortingOrder.REVERSE_ALPHABETICAL);
		desc.initialize();

		try (CSVChemFileReader smilesReader = new CSVChemFileReader(format,
				new InputStreamReader(toy_set.url().openStream()));) {
			while (smilesReader.hasNext()) {
				desc.calculateDescriptors(smilesReader.next());
			}
		}
		List<String> reversed = Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H", "I");
		Collections.reverse(reversed);

		Assert.assertEquals("Rev - Alphabetical order", reversed, desc.getPropertyNames());

	}

	@Test
	public void testWHIMDescriptor() throws Exception {
		ChemDescriptor desc = DescriptorFactory.getInstance().getDescriptor("WHIMDescriptor");
		desc.initialize();
		if (print) {
			SYS_OUT.println(desc);
		}
		IAtomContainer test = getTestMol();
		// SYS_OUT.println(desc.getConfigParameters());
		System.out.println(desc.calculateDescriptors(CDKConfigureAtomContainer.calculate3DCoordinates(test, false)));
		// printLogs();
	}

	// @Test
	public void testTaeAminoAcidDescriptor() throws Exception {
		// Removed this one as it depends on the molecule being a BioPolymer
		ChemDescriptor desc = DescriptorFactory.getInstance().getDescriptor("TaeAminoAcidDescriptor");
		if (print) {
			SYS_OUT.println(desc);
		}
		LoggerUtils.addStreamAppenderToLogger((Logger) LoggerFactory.getLogger(CDKPhysChemWrapper.class), SYS_OUT,
				"%m%n");
		IAtomContainer test = getTestMol();
		SYS_OUT.println(desc.getConfigParameters());
		SYS_OUT.println(desc.calculateDescriptors(test));
	}

	@Test
	public void testAllDescriptors() throws IllegalStateException, CDKException {
		List<ChemDescriptor> desc = DescriptorFactory.getInstance().getDescriptorsList();
		for (ChemDescriptor d : desc) {
			if (!(d instanceof SignaturesDescriptor) && ! (d instanceof MockFailingDescriptor)) {
				doCheckDescriptor(d);
			}
		}
	}

	public void doCheckDescriptor(ChemDescriptor desc) throws IllegalStateException, CDKException {
		IAtomContainer test = getTestMol();
		CDKConfigureAtomContainer.configMolecule(test);
		desc.initialize();
		int maxNumFeats = desc.getLength();
		if (desc.requires3DCoordinates()) {
			test = CDKConfigureAtomContainer.calculate3DCoordinates(test, false);
		}
		List<SparseFeature> l = desc.calculateDescriptors(test);
		Assert.assertNotNull(l);
		Assert.assertTrue(maxNumFeats >= l.size());

		// Ordering must be in order
		Comparators.isInOrder(l, new Comparator<SparseFeature>() {

			@Override
			public int compare(SparseFeature o1, SparseFeature o2) {
				return o1.getIndex() - o2.getIndex();
			}
		});

		if (print) {
			SYS_OUT.println(desc.getName() + " : " + l);
		}
	}

	@Test
	public void testConfigureSignatureDescriptorTunableStuff() {
		int start = 0, end = 2;

		SignaturesDescriptor desc = new SignaturesDescriptor(start, end);

		// List<ConfigParameter> params = desc.getConfigParameters();
		// for (ConfigParameter p : params) {
		// SYS_ERR.println(p);
		// }
		Map<String, Object> newParams = new HashMap<>();
		desc.setConfigParameters(newParams);

		// This result depends on if InChI can be generated or not on the current
		// machine
		newParams.put("signatureType", "stereo");
		if (INCHI_AVAILABLE_ON_SYSTEM) {
			desc.setConfigParameters(newParams);
			Assert.assertEquals(SignaturesDescriptor.SignatureType.STEREO, desc.getSignatureType());
		} else {
			try {
				// Try to set it - it should fail!
				desc.setConfigParameters(newParams);
				Assert.fail("When inchi is not available on system - setting stereo should fail");
			} catch (IllegalArgumentException e) {
				// We should end up with a failure
			}
		}

		newParams.clear();
		newParams.put("vectorType", "Binary");
		desc.setConfigParameters(newParams);
		Assert.assertEquals(SignaturesDescriptor.VectorType.BINARY, desc.getVectorType());

		newParams.clear();
		newParams.put("startHeight", 2);
		desc.setConfigParameters(newParams);
		Assert.assertEquals(2, desc.getStartHeight());

		newParams.clear();
		newParams.put("endHeight", 6);
		desc.setConfigParameters(newParams);
		Assert.assertEquals(6, desc.getEndHeight());

		try {
			newParams.clear();
			newParams.put("startHeight", 11);
			desc.setConfigParameters(newParams);
			Assert.fail();
		} catch (IllegalArgumentException e) {
		}

		try {
			newParams.clear();
			newParams.put("some_other_parameter", 11);
			desc.setConfigParameters(newParams);
		} catch (IllegalArgumentException e) {
			Assert.fail("Un-recognized params should simply fall through");
		}
	}

	// @Test
	public void testListDescriptors() {
		List<ChemDescriptor> descs = DescriptorFactory.getInstance().getDescriptorsList();
		for (ChemDescriptor d : descs) {
			SYS_ERR.println(d);
		}
	}

	// @Test
	// public void testCDKFP() {
	// CircularFingerprinter cp = new CircularFingerprinter();
	// SYS_ERR.println("size: " + cp.getSize());
	// SYS_ERR.println(cp.getVersionDescription());
	// }

	@Test
	public void testCDKCircularFPWrapper() {
		int len = 100;
		ECFP6 fpBIT = new ECFP6();
		fpBIT.setLength(len);

		fpBIT.initialize();
		List<SparseFeature> descBIT = fpBIT.calculateDescriptors(getTestMol());

		ECFP6 fpCOUNT = fpBIT.clone();
		Assert.assertEquals(len, fpCOUNT.getLength());
		fpCOUNT.useCountVersion(true);
		fpCOUNT.initialize();
		List<SparseFeature> descCOUNT = fpCOUNT.calculateDescriptors(getTestMol());

		// SYS_OUT.println("FP_bit: "+descBIT);
		// SYS_OUT.println("FP_cnt: "+descCOUNT);

		Assert.assertEquals(descBIT.size(), descCOUNT.size());
		Assert.assertTrue(descBIT.size() <= len);

		for (int i = 0; i < descBIT.size(); i++) {
			Assert.assertEquals(descBIT.get(i).getIndex(), descCOUNT.get(i).getIndex());
			Assert.assertEquals(1, descBIT.get(i).getValue(), 0.00001);
			Assert.assertTrue(descCOUNT.get(i).getValue() >= 1);
		}

		// Check config stuff
		ECFP4 ecfp = new ECFP4();
		ecfp.setConfigParameters(ImmutableMap.of("length", 650));
		Assert.assertEquals(650, ecfp.getLength());

		ecfp.setConfigParameters(ImmutableMap.of("useCount", true));
		Assert.assertTrue(ecfp.usesCountVersion());

		ecfp.initialize();
		try {
			ecfp.setConfigParameters(ImmutableMap.of("length", 6050));
			Assert.fail("should not be possible to set configs after initialization");
		} catch (IllegalStateException e) {
		}

	}

	@Test
	public void getMACSS() {
		// MACCSFingerprinter mcs = new MACCSFingerprinter();
		// SYS_ERR.println(mcs.getSize());
		MACCS maccs = new MACCS();
		maccs.initialize();
		IAtomContainer mol = getTestMol();
		List<SparseFeature> desc1 = maccs.calculateDescriptors(mol);
		Assert.assertTrue(desc1.size() > 2);
		Assert.assertTrue(desc1.get(0).getIndex() >= 0);
		Assert.assertTrue("MACCS should contain 166 features [0,165] for indices",
				desc1.get(desc1.size() - 1).getIndex() <= 165);
		// SYS_ERR.println(mol.getProperties());
		// CPSignMolProperties.stripBadProperties(mol);
		// SYS_ERR.println(mol.getProperties());
		// for(Map.Entry<Object, Object> pp : mol.getProperties().entrySet()) {
		// SYS_ERR.println(pp.getKey()+ " type: " + pp.getKey().getClass() + "::: value
		// " + pp.getValue() + " type: " + pp.getValue());
		// }

		// Compute again with new instance, they should be identical fps
		MACCS maccs2 = new MACCS();
		maccs2.initialize();
		List<SparseFeature> desc2 = maccs2.calculateDescriptors(getTestMol());
		Assert.assertEquals(desc1.size(), desc2.size());
		Assert.assertTrue(desc1.size() <= maccs2.getLength());

		for (int i = 0; i < desc1.size(); i++) {
			Assert.assertEquals(desc1.get(i).getIndex(), desc2.get(i).getIndex());
			Assert.assertEquals(1, desc1.get(i).getValue(), 0.00001);
			Assert.assertTrue(desc2.get(i).getValue() >= 1);
		}

	}

	@Test
	public void getFingerprintDescr() {
		List<ChemDescriptor> fps = DescriptorFactory.getFingerprintDescriptors();
		Assert.assertEquals(5, fps.size());
		// for (ChemDescriptor d : DescriptorFactory.getFingerprintDescriptors()) {
		// SYS_OUT.println(d);
		// }
	}

}

/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cheminf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.chem.io.in.CSVFile;
import com.arosbio.chem.io.in.ChemFileParserUtils;
import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor.SignatureType;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor.VectorType;
import com.arosbio.cheminf.descriptors.StereoAtomSignature;
import com.arosbio.commons.Stopwatch;
import com.arosbio.data.NamedLabels;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.UnitTestBase;

@Category(UnitTest.class)
public class TestStereoAtomSignature extends UnitTestBase {

	// https://pubchem.ncbi.nlm.nih.gov/compound/16219709#section=Top
	final public static String smiles = "C[C@H]1/C=C/C=C/CC/C=C/C=C/C=C/C=C/[C@@H](C[C@H]2[C@@H]([C@H](C[C@](O2)(C[C@H]([C@@H](CC[C@H](C[C@H](C[C@H](CC(=O)O[C@H]([C@H]([C@@H]1O)C)C)O)O)O)O)O)O)O)C(=O)O)O[C@H]3[C@H]([C@H]([C@@H]([C@H](O3)C)O)N)O";
	final public static String smiles2 = "F/C=C\\F";
	final public static String smiles3 = "F/C=C/F";
	final public static String smiles4 = "N[C@](Br)(O)C";
	final public static String smiles4_nonChiral = "N[C](Br)(O)C";
	final public static String smiles5 = "O=C4[C@@H]5Oc1c2c(ccc1OC)C[C@H]3N(CC[C@]25[C@H]3CC4)C";
	final public static String smiles6_aromatic="OCc1ccc(cc1)Cl";
	// final public static SmilesParser sp = new SmilesParser(SilentChemObjectBuilder.getInstance());
	final public static boolean printSigs=true;

	//	String trainSMILES = this.getClass().getResource("/resources/smiles_files/smiles_classification.smi").getFile();

	@Test
	public void testGenerateFromFile() throws Exception {
		CmpdData data = TestResources.Reg.getSolubility_10();

		SignaturesDescriptor desc = new SignaturesDescriptor();
		desc.setSignaturesType(SignatureType.STEREO);
		ChemDataset sp = new ChemDataset(desc);
		sp.initializeDescriptors();
		sp.add(new CSVFile(data.uri()).getIterator(), data.property());
//		sp.fromChemFile(getURI("/resources/smiles_files/smiles_classification.smi"), null, Arrays.asList("POS", "NEG"));
		
		Set<String> sigs = new HashSet<>(toSet(desc.getSignatures()));
		if (printSigs){
			int i=0;
			for(String sig: sigs){
				i++;
				System.out.println(sig);
				if(i>20)
					break;
			}
		}

		SignaturesDescriptor desc2 = new SignaturesDescriptor();
		desc2.setSignaturesType(SignatureType.STEREO);
		ChemDataset sp2 = new ChemDataset(desc2);
		sp2.initializeDescriptors();
		sp2.add(new CSVFile(data.uri()).getIterator(), data.property());
		// sp2.add(new CSVFile(getURI("/resources/smiles_files/smiles_classification.smi")).getIterator(), "solubility(fake!)", new NamedLabels(Arrays.asList("POS", "NEG")));
		Set<String> sigs2 = toSet(desc2.getSignatures());
		
		Set<String> difference1_2 = new HashSet<String>(sigs);
		difference1_2.removeAll(sigs2);

		Set<String> difference2_1 = new HashSet<>(sigs2);
		difference2_1.removeAll(sigs);

		System.out.println("Diff 1-2.size: " + difference1_2.size());
		System.out.println("Diff 2-1.size: " + difference2_1.size());
		for(String sig: difference1_2)
			System.out.println("*"+sig);
		System.out.println("-----------");
		for(String sig: difference2_1)
			System.out.println("+"+sig);

		Assert.assertTrue(difference1_2.isEmpty());
		Assert.assertTrue(difference2_1.isEmpty());
	}
	
	static <T> Set<T> toSet(Iterable<T> data){
		Set<T> s = new HashSet<>();
		for (T d : data) {
			s.add(d);
		}
		return s;
	}
	

	@Test
	public void testStereoSignatures() throws Exception {
		IAtomContainer mol = ChemFileParserUtils.parseSMILES(smiles);
		for(int i=0; i<5; i++)
			printFingerprintSignatures(mol,i);
	}

	@Test
	public void testStereoSignaturesAromatic() throws Exception {
		IAtomContainer mol = ChemFileParserUtils.parseSMILES(smiles6_aromatic);
		for(int i=0; i<5; i++)
			printFingerprintSignatures(mol,i);
	}

	@Test
	public void testDifferentTransCisChiralityShouldGiveDifferentSignatures() throws Exception {
		// trans-difluoroethane
		IAtomContainer trans = ChemFileParserUtils.parseSMILES(smiles3);
		Set<String> transSigs = new HashSet<>(getSigns(trans, 2));
		// cis-difluoroethane
		IAtomContainer cis = ChemFileParserUtils.parseSMILES(smiles2);
		Set<String> cisSigs = new HashSet<>(getSigns(cis, 2));

		Assert.assertEquals(transSigs, new HashSet<String>(transSigs));
		Assert.assertFalse(transSigs.equals(cisSigs));

		if (printSigs){
			System.out.println(transSigs);
			System.out.println(cisSigs);
		}
	}

	@Test
	public void testChiralCarbonShouldBeShown() throws Exception {
		IAtomContainer chiralMol = ChemFileParserUtils.parseSMILES(smiles4);
		int numAtSigns = 0;
		List<String> signs= getSigns(chiralMol, 2);
		for(String sign: signs){
			numAtSigns+=(sign.contains("@")? 1 : 0);
		}
		Assert.assertTrue(numAtSigns > 0);
		if(printSigs)
			System.out.println(signs);

		IAtomContainer nonChiral = ChemFileParserUtils.parseSMILES(smiles4_nonChiral);
		int numAtSignsNonChiral = 0;
		List<String> signsNonChiral= getSigns(nonChiral, 2);
		for(String sign: signsNonChiral){
			numAtSignsNonChiral+=(sign.contains("@")? 1 : 0);
		}
		Assert.assertEquals(0, numAtSignsNonChiral);
		if(printSigs)
			System.out.println(signsNonChiral);
	}

	public List<String> getSigns(IAtomContainer mol, int height) throws Exception {

		List<String> sigs = new ArrayList<>();
		for(IAtom atom: mol.atoms()){
			sigs.add(new StereoAtomSignature(atom, height, mol).toCanonicalString());
		}
		return sigs;
	}

	public void printFingerprintSignatures(IAtomContainer mol, int height) throws Exception{
		//		IAtomContainer mol = sp.parseSmiles(smilesMol);
		//		CircularFingerprinter fp = new CircularFingerprinter(CircularFingerprinter.CLASS_ECFP6,6);
		//		fp.calculate(mol);
		//		for(int i=0;i<fp.getFPCount(); i++)
		//		LoggerUtials.setDebugMode();
		int numSigns=0;
		for(IAtom atom: mol.atoms()){
			String sig = new StereoAtomSignature(atom, height, mol).toCanonicalString();
			if(printSigs)
				System.out.println(sig);
			numSigns++;
		}
		Assert.assertEquals(mol.getAtomCount(), numSigns);
		//		System.out.println(numSigns);
	}

	public static void main(String[] args) throws Exception {
		TestStereoAtomSignature.testSpeedNormalVsStereo();
	}


	public static void testSpeedNormalVsStereo() throws Exception {
		ChemDataset spNormal = new ChemDataset();
		
		ChemDataset spStereo = new ChemDataset(new SignaturesDescriptor(1, 3, SignatureType.STEREO, VectorType.COUNT));
		((SignaturesDescriptor)spStereo.getDescriptors().get(0)).setSignaturesType(SignatureType.STEREO);

		CSVCmpdData cas = TestResources.Cls.getCAS_N6512();
		Stopwatch watch = new Stopwatch();
		
		watch.start();
		spNormal.add(new CSVFile(cas.uri()).getIterator(), cas.property(), new NamedLabels(cas.labelsStr()));
//		spNormal.fromChemFile(getURI(testFile), "class", Arrays.asList("0", "1"));
		watch.stop();
		System.out.println(watch.elapsedTimeMillis());

		watch.start();
		spStereo.add(new CSVFile(cas.uri()).getIterator(), cas.property(), new NamedLabels(cas.labelsStr()));
//		spStereo.fromChemFile(getURI(testFile), "class", Arrays.asList("0", "1"));
		watch.stop();
		System.out.println(watch.elapsedTimeMillis());

	}
}

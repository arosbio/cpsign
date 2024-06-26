/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem.io.in;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.chem.io.in.ChemIOUtils.ChemIOFormat;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.UnitTest;

@Category(UnitTest.class)
public class TestChemIOUtils {
	
	private CSVCmpdData csv = TestResources.Cls.getAMES_126_chem_desc();
	private CmpdData sdf = TestResources.Cls.getAMES_10();
	private CmpdData json = TestResources.Cls.getAMES_10_json();
	
	@Test
	public void testSDF() throws IOException {
		
		try(BufferedInputStream sdfStream = new BufferedInputStream(sdf.url().openStream())){
			Assert.assertFalse(ChemIOUtils.isCSV(sdfStream));
			Assert.assertTrue(ChemIOUtils.isSDF(sdfStream));
			Assert.assertFalse(ChemIOUtils.isJSON(sdfStream));
		}
		
		Assert.assertEquals(ChemIOFormat.SDF, ChemIOUtils.deduceFormat(sdf.uri()));
		
		try(BufferedInputStream v3000 = new BufferedInputStream(new ByteArrayInputStream(SDF_V3000_TXT.getBytes(StandardCharsets.UTF_8)))){
			Assert.assertFalse(ChemIOUtils.isCSV(v3000));
			Assert.assertTrue(ChemIOUtils.isSDF(v3000));
			Assert.assertFalse(ChemIOUtils.isJSON(v3000));
		}
		
	}
	
	@Test
	public void testCSV() throws IOException {
		
		try(BufferedInputStream csvStream = new BufferedInputStream(csv.url().openStream())){
			Assert.assertTrue(ChemIOUtils.isCSV(csvStream));
			Assert.assertFalse(ChemIOUtils.isSDF(csvStream));
			Assert.assertFalse(ChemIOUtils.isJSON(csvStream));
		}
		
		Assert.assertEquals(ChemIOFormat.CSV, ChemIOUtils.deduceFormat(csv.uri()));
		
	}
	
	@Test
	public void testJSON() throws IOException {
		
		try(BufferedInputStream jsonStream = new BufferedInputStream(json.url().openStream())){
			Assert.assertFalse(ChemIOUtils.isCSV(jsonStream));
			Assert.assertFalse(ChemIOUtils.isSDF(jsonStream));
			Assert.assertTrue(ChemIOUtils.isJSON(jsonStream));
		}
		
		Assert.assertEquals(ChemIOFormat.JSON, ChemIOUtils.deduceFormat(json.uri()));
		
	}

	@Test
	public void testStrangeCSVFile()throws Exception {
		CSVCmpdData regDuplicate = TestResources.Reg.getContradict_labels_and_outlier();
		Assert.assertEquals('\t',ChemIOUtils.deduceDelimiter(regDuplicate.uri()));
	}
	
	
	private final static String SDF_V3000_TXT = "c1\n  SciTegic10121708212D\n\n  0  0  0  0  0  0            999 V3000\nM  V30 BEGIN CTAB\nM  V30 COUNTS 36 39 1 0 1\nM  V30 BEGIN ATOM\nM  V30 1 C 2.10593 -0.25313 0 0\nM  V30 2 C 1.35703 -0.59923 0 0\nM  V30 3 C 0.66323 -0.13613 0 0\nM  V30 4 N -0.14767 -0.61473 0 0\nM  V30 5 C -0.84317 -0.15163 0 0\nM  V30 6 C -1.58687 -0.61473 0 0\nM  V30 7 C -2.28417 -0.15163 0 0\nM  V30 8 Cl -3.00727 -0.56993 0 0\nM  V30 9 C -2.28417 0.65237 0 0\nM  V30 10 C -2.99867 1.06557 0 0\nM  V30 11 O -2.99867 1.89027 0 0\nM  V30 12 N -3.71307 0.65237 0 0\nM  V30 13 C -4.46717 0.98817 0 0\nM  V30 14 C -5.01807 0.37527 0 0 CFG=1\nM  V30 15 O -5.84277 0.37527 0 0\nM  V30 16 C -4.60487 -0.33923 0 0 CFG=2\nM  V30 17 F -5.01807 -1.05373 0 0\nM  V30 18 C -3.79917 -0.16713 0 0\nM  V30 19 C -1.58687 1.05697 0 0\nM  V30 20 Cl -1.58687 1.92127 0 0\nM  V30 21 C -0.84317 0.65237 0 0\nM  V30 22 O 0.66323 0.66967 0 0\nM  V30 23 C 1.47413 -1.40663 0 0\nM  V30 24 N 2.27643 -1.57713 0 0\nM  V30 25 N 2.68273 -0.82993 0 0\nM  V30 26 C 3.48843 -0.77143 0 0\nM  V30 27 C 3.83453 -0.02243 0 0\nM  V30 28 C 4.64193 0.09287 0 0\nM  V30 29 C 5.16013 -0.59923 0 0\nM  V30 30 F 5.98483 -0.59923 0 0\nM  V30 31 C 4.81413 -1.34813 0 0\nM  V30 32 C 4.00663 -1.46523 0 0\nM  V30 33 F 2.50883 1.25837 0 0\nM  V30 34 F 3.13033 0.32527 0 0\nM  V30 35 F 1.52063 0.75397 0 0\nM  V30 36 C 2.31773 0.54047 0 0\nM  V30 END ATOM\nM  V30 BEGIN BOND\nM  V30 1 1 36 1\nM  V30 2 1 36 33\nM  V30 3 1 36 34\nM  V30 4 1 36 35\nM  V30 5 2 1 2\nM  V30 6 1 3 2\nM  V30 7 1 3 4\nM  V30 8 1 5 4\nM  V30 9 2 6 5\nM  V30 10 1 7 6\nM  V30 11 1 7 8\nM  V30 12 2 9 7\nM  V30 13 1 9 10\nM  V30 14 2 10 11\nM  V30 15 1 10 12\nM  V30 16 1 13 12\nM  V30 17 1 14 13\nM  V30 18 1 14 15 CFG=3\nM  V30 19 1 16 14\nM  V30 20 1 16 17 CFG=1\nM  V30 21 1 18 16\nM  V30 22 1 18 12\nM  V30 23 1 19 9\nM  V30 24 1 19 20\nM  V30 25 2 21 19\nM  V30 26 1 5 21\nM  V30 27 2 3 22\nM  V30 28 1 2 23\nM  V30 29 2 23 24\nM  V30 30 1 24 25\nM  V30 31 1 25 26\nM  V30 32 1 27 26\nM  V30 33 2 28 27\nM  V30 34 1 29 28\nM  V30 35 1 29 30\nM  V30 36 2 31 29\nM  V30 37 1 32 31\nM  V30 38 2 26 32\nM  V30 39 1 25 1\nM  V30 END BOND\nM  V30 END CTAB\nM  END\n$$$$\n";
	
	
	
//	private static Pattern SDF_V2000 = Pattern.compile("\\v[0-9\s]{10,}V2000\\v");
//    private static Pattern SDF_V3000 = Pattern.compile("\\v[0-9\s]{10,}V3000\\v");
//    private static String regex = "[0-9\s]{10,}V3000"; //"\b[0-9\s]*\bV3000\b";
//    
//    
//    @Test
//    public void testPattern() {
//    	Matcher m = SDF_V2000.matcher(SDF_V3000_TXT);
//    	System.err.println(m.find());
//    	
//    	//System.err.println(Pattern.matches(regex, SDF_V3000_TXT));
//    	Matcher m2 = SDF_V3000.matcher(SDF_V3000_TXT);
//    	System.err.println(m2.find());
//    	//System.err.println(m2.find());
//    	String sdf_txt = "  0  0  0  0  0  0            999 V3000"; //"V3000"; //
//    	System.err.println(Pattern.matches(regex, sdf_txt));
//    }
	
}
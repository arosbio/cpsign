/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem.io.out;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

import org.apache.commons.csv.CSVFormat;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.io.iterator.IteratingSDFReader;
import org.openscience.cdk.silent.SilentChemObjectBuilder;

import com.arosbio.chem.io.in.CSVChemFileReader;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.UnitTestBase;

@Category(UnitTest.class)
public class TestCSVWriter extends UnitTestBase {

	@Test
	public void testWriterWithExplicitHeader() throws IOException {
		try (
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				CSVWriter writer = new CSVWriter(baos, Arrays.asList("SMILES", "Prediction", "Comments"),
						CSVFormat.TDF.withRecordSeparator("\n"));) {
			IAtomContainer mol = getTestMol();
			writer.writeRecord(mol);
			writer.flush();
			String header = baos.toString().split("\n")[0];
			System.out.println(header);
			Assert.assertEquals("SMILES\tPrediction\tComments", header);
		}

	}

	@Test
	public void testFromSDF() throws FileNotFoundException, IOException {
		CmpdData ames = TestResources.Cls.getAMES_10_gzip();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (
				IteratingSDFReader iter = new IteratingSDFReader(
						ames.url().openStream(),
						// new FileInputStream(new File(AmesBinaryClass.MINI_FILE_PATH + GZIP_SUFFIX)),
						SilentChemObjectBuilder.getInstance());
				CSVWriter writer = new CSVWriter(baos);) {

			IAtomContainer mol;
			while (iter.hasNext()) {
				mol = iter.next();
				writer.writeRecord(mol);
			}
			writer.flush();
			String output = baos.toString();
			System.out.println(output);
		}
	}

	@Test
	public void testAddResults() throws Exception {
		CmpdData ames = TestResources.Cls.getAMES_10_gzip();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (
				InputStream is = ames.url().openStream(); 
				IteratingSDFReader iter = new IteratingSDFReader(is, SilentChemObjectBuilder.getInstance());
				CSVWriter writer = new CSVWriter(baos);) {

			IAtomContainer mol;
			while (iter.hasNext()) {
				mol = iter.next();
				double[] pvals = { Math.random(), Math.random() };
				mol.setProperty("P-values", "[" + pvals[0] + ", " + pvals[1] + "]");
				mol.setProperty("Predicted value", Math.random() * 100);
				writer.writeRecord(mol);
			}
			writer.flush();
			String output = baos.toString();
			System.out.println(output);
		}
	}

	@Test
	public void testWriterWithActualMols() throws IOException {
		writeMols(TestResources.Reg.getSolubility_10_no_header_multicol().url().openStream(), 
				CSVFormat.DEFAULT.withDelimiter('\t').withHeader("smiles", "sol", "comment"));
		System.out.println("\n\n");
		writeMols(TestResources.Reg.getSolubility_10_multicol().url().openStream(), 
				CSVFormat.DEFAULT.withDelimiter('\t').withHeader());
		System.out.println("\n\n");
		writeMols(TestResources.Reg.getErronious().url().openStream(), 
				CSVFormat.DEFAULT.withDelimiter('\t').withHeader());

	}

	public void writeMols(InputStream stream, CSVFormat format) throws IOException {
		try (
				CSVChemFileReader reader = new CSVChemFileReader(
						format, new InputStreamReader(stream));
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				CSVWriter writer = new CSVWriter(baos, reader.getHeaders());) {

			System.err.println(reader.getHeaders());

			while (reader.hasNext())
				writer.writeRecord(reader.next());

			writer.flush();
			String output = baos.toString();
			System.out.println(output);
		}
	}

}

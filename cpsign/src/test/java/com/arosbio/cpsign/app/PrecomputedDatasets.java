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
import java.io.IOException;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.chem.io.in.MolAndActivityConverter;
import com.arosbio.chem.io.in.SDFReader;
import com.arosbio.chem.io.in.SDFile;
import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.descriptors.ChemDescriptor;
import com.arosbio.cheminf.descriptors.DescriptorFactory;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.data.NamedLabels;
import com.arosbio.ml.io.ModelInfo;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.MockFailingDescriptor;

public class PrecomputedDatasets {

	public static class Regression {

		private static File precompSol10, precompSol100, precompChang, precompChangCustomDescAndTransform;
		public static int customSignaturesEndH = 2;

		public static File getSolu10() throws IOException {
			if (precompSol10 != null)
				return precompSol10;
			precompSol10 = TestUtils.createTempFile("sol10_ds", ".jar");
			// SOL 10
			CSVCmpdData sol10 = TestResources.Reg.getSolubility_10();
			CLIBaseTest.mockMain(Precompute.CMD_NAME,
					"--model-type", CLIBaseTest.PRECOMPUTE_REGRESSION,
					"--train-data", sol10.format(), sol10.uri().toString(),
					"--property", sol10.property(),
					"--model-out", precompSol10.getAbsolutePath(),
					"--silent");
			return precompSol10;
		}

		public static File getSolu100() throws IOException {
			if (precompSol100 != null)
				return precompSol100;
			precompSol100 = TestUtils.createTempFile("sol100_ds", ".jar");
			// SOL 100
			CSVCmpdData sol100 = TestResources.Reg.getSolubility_100();
			CLIBaseTest.mockMain(Precompute.CMD_NAME,
					"--model-type", CLIBaseTest.PRECOMPUTE_REGRESSION,
					"--train-data", sol100.format(), sol100.uri().toString(),
					"--property", sol100.property(),
					"--model-out", precompSol100.getAbsolutePath(),
					"--silent");
			return precompSol100;
		}

		public static File getChang() throws IOException {
			if (precompChang != null)
				return precompChang;
			precompChang = TestUtils.createTempFile("chang_ds", ".jar");
			// Chang std
			CmpdData chang = TestResources.Reg.getChang();
			CLIBaseTest.mockMain(Precompute.CMD_NAME,
					"--model-type", CLIBaseTest.PRECOMPUTE_REGRESSION,
					"--train-data", chang.format(), chang.uri().toString(),
					"--property", chang.property(),
					"--model-out", precompChang.getAbsolutePath(),
					"--silent");
			return precompChang;
		}

		public static File getChangCustomDescrAndTransformers() throws IOException {
			if (precompChangCustomDescAndTransform != null)
				return precompChangCustomDescAndTransform;
			precompChangCustomDescAndTransform = TestUtils.createTempFile("chang_desc_trans", ".jar");
			// Chang custom desc + transforms
			CmpdData chang = TestResources.Reg.getChang();
			CLIBaseTest.mockMain(Precompute.CMD_NAME,
					"--model-type", CLIBaseTest.PRECOMPUTE_REGRESSION,
					"--train-data", chang.format(), chang.uri().toString(),
					"--property", chang.property(),
					"--descriptors", "signatures:1:"+customSignaturesEndH, "AminoAcidCountDescriptor","ALOGPDescriptor","HBondAcceptorCountDescriptor","HBondDonorCountDescriptor",
					"--transformations", "DropMissingDataFeatures", "Standardizer:colMaxIndex=10",
					"--model-out", precompChangCustomDescAndTransform.getAbsolutePath(),
					"--silent");
			return precompChangCustomDescAndTransform;
		}
	}

	public static class Classification {

		private static File precompClassAmes, precompClassAmesWithTransformAndCDK, precomp3class, tooSmall, missingData;
		
		public static int customStartH =0, customEndH = 2;

		public static File getAmes123() throws IOException {
			if (precompClassAmes != null)
				return precompClassAmes;

			precompClassAmes = TestUtils.createTempFile("ames-standard", ".jar");
			CmpdData ames = TestResources.Cls.getAMES_126();
			CLIBaseTest.mockMain(Precompute.CMD_NAME,
					"--model-type", CLIBaseTest.PRECOMPUTE_CLASSIFICATION,
					"--labels", CLIBaseTest.getLabelsArg(ames.labels()),
					"--train-data", ames.format(), ames.uri().toString(),
					"--property", ames.property(),
					"--model-out", precompClassAmes.getAbsolutePath(),
					"--silent");

			return precompClassAmes;
		}

		public static File getAmesCDKDescAndTransformations() throws IOException {
			if (precompClassAmesWithTransformAndCDK != null)
				return precompClassAmesWithTransformAndCDK;

			precompClassAmesWithTransformAndCDK = TestUtils.createTempFile("ames-with-transforms", ".jar");
			// AMES small with transformations + different descr.
			CmpdData ames = TestResources.Cls.getAMES_126();
			CLIBaseTest.mockMain(Precompute.CMD_NAME,
					"--model-type", CLIBaseTest.PRECOMPUTE_CLASSIFICATION,
					"--labels", CLIBaseTest.getLabelsArg(ames.labels()),
					"--train-data", ames.format(), ames.uri().toString(),
					"--property", ames.property(),
					"--descriptors", "signatures:"+customStartH+':'+customEndH,"AminoAcidCountDescriptor","ALOGPDescriptor","HBondAcceptorCountDescriptor","HBondDonorCountDescriptor",
					"--transformations", "DropMissingDataFeatures", "RobustScaler:colMaxIndex=15",
					"--model-out", precompClassAmesWithTransformAndCDK.getAbsolutePath(),
					"--silent");

			return precompClassAmesWithTransformAndCDK;

		}

		public static File get3ClassLTKB() throws IOException {
			if (precomp3class != null)
				return precomp3class;
			precomp3class = TestUtils.createTempFile("LTKB", ".jar");

			// LTKB 3 class problem
			CSVCmpdData ltkb = TestResources.MultiCls.getLTKB();
			CLIBaseTest.mockMain(Precompute.CMD_NAME,
					"--model-type", CLIBaseTest.PRECOMPUTE_CLASSIFICATION,
					"--labels", CLIBaseTest.getLabelsArg(ltkb.labels(),'\t'), 
					"--train-data", ltkb.format(),"delim="+ltkb.delim(), ltkb.uri().toString(), 
					"--property", ltkb.property(),
					"--model-out", precomp3class.getAbsolutePath(),
					"--silent");
			return precomp3class;
		}

		public static File getTooSmallDS() throws Exception {
			if (tooSmall != null)
				return tooSmall;
			
			tooSmall = TestUtils.createTempFile("smallDS", ".jar");

			ChemDataset ds = new ChemDataset();
			
			
			ds.initializeDescriptors();
			CmpdData file = TestResources.Cls.getAMES_126();
			NamedLabels labels = new NamedLabels(file.labelsStr());
			try (SDFReader is = new SDFile(file.uri()).getIterator();
				MolAndActivityConverter conv = MolAndActivityConverter.classificationConverter(is, file.property(),labels)){
					
					for (int i=0; i<5; i++){
						Pair<IAtomContainer,Double> inst = conv.next();
						ds.add(inst.getLeft(),inst.getRight());
					}
			}
			ds.setTextualLabels(labels);

			ModelSerializer.saveDataset(ds, new ModelInfo("too small data set"), tooSmall, null);

			return tooSmall;
		}
		
		public static File getMissingDataDS() throws Exception {
			if (missingData != null)
				return missingData;
			
			missingData = TestUtils.createTempFile("missing-data-DS", ".jar");
			List<ChemDescriptor> descs = DescriptorFactory.getCDKDescriptorsNo3D();
			// Take some random descriptors, including the failing one
			ChemDataset ds = new ChemDataset(new MockFailingDescriptor(),descs.get(0),descs.get(3),descs.get(5),descs.get(10));
			ds.initializeDescriptors();
			CmpdData file = TestResources.Cls.getAMES_126();
			try (SDFReader is = new SDFile(file.uri()).getIterator();
				MolAndActivityConverter conv = MolAndActivityConverter.classificationConverter(is, file.property(), new NamedLabels(file.labelsStr()))){
					ds.add(conv);
			}

			ModelSerializer.saveDataset(ds, new ModelInfo("missing data"), missingData, null);
			
			return missingData;
		}


	}

}

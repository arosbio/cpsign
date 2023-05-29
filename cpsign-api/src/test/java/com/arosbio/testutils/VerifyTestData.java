package com.arosbio.testutils;

import java.io.IOException;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.cheminf.data.ChemDataset;
import com.arosbio.cheminf.data.ChemDataset.DescriptorCalcInfo;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.TestResources.CmpdData;
import com.arosbio.tests.suites.NonSuiteTest;


@Category(NonSuiteTest.class)
public class VerifyTestData {

    @Test
    public void printInfo() throws IOException {
        // Benchmark resources in cpsign-api
        // doPrint(BenchmarkResources.getADAM5());
        // doPrint(BenchmarkResources.getCyclin());
        // doPrint(BenchmarkResources.getD_Amino());
        // doPrint(BenchmarkResources.getSerine());

        // // Classification data sets
        // doPrint(TestResources.Cls.getAMES_126_gzip());
        // doPrint(TestResources.Cls.getAMES_126());
        // doPrint(TestResources.Cls.getAMES_126_chem_desc());
        // doPrint(TestResources.Cls.getAMES_1337());
        // doPrint(TestResources.Cls.getAMES_2497());
        // doPrint(TestResources.Cls.getAMES_4337());
        // doPrint(TestResources.Cls.getBBB());
        // doPrint(TestResources.Cls.getCAS_N6512());
        // doPrint(TestResources.Cls.getCox2());
        // doPrint(TestResources.Cls.getCPD());
        // doPrint(TestResources.Cls.getDHFR());
        // doPrint(TestResources.Cls.getEPAFHM());
        // doPrint(TestResources.Cls.getFDA());
        // doPrint(TestResources.Cls.getScreen_U251());
        // doPrint(TestResources.Cls.getSingleMOL());
        // doPrint(TestResources.Cls.getErroneous());
        // doPrint(TestResources.Cls.getHERG());
        // doPrint(TestResources.Cls.getContradict_labels());

        // Multiclass data sets
        // doPrint(TestResources.MultiCls.getLTKB());


        // Regression 
        // doPrint(TestResources.Reg.getChang());
        // doPrint(TestResources.Reg.getChang_gzip());
        // doPrint(TestResources.Reg.getChang_json_no_indent());
        doPrint(TestResources.Reg.getChang_json());
        doPrint(TestResources.Cls.getAmes10WithInvalidRecords());
        // doPrint(TestResources.Reg.getGluc());
        // doPrint(TestResources.Reg.getErroneous());
        // doPrint(TestResources.Reg.getSolubility_10_multicol());
        // doPrint(TestResources.Reg.getSolubility_10_no_header_multicol());
        // doPrint(TestResources.Reg.getSolubility_10_no_header());
        // doPrint(TestResources.Reg.getSolubility_10());
        // doPrint(TestResources.Reg.getSolubility_10_gzip());
        // doPrint(TestResources.Reg.getSolubility_10_excel());
        // doPrint(TestResources.Reg.getSolubility_100());
        // doPrint(TestResources.Reg.getSolubility_500());
        // doPrint(TestResources.Reg.getSolubility_1k());
        // doPrint(TestResources.Reg.getSolubility_4k());
        // doPrint(TestResources.Reg.getSolubility_5k());
        // doPrint(TestResources.Reg.getSolubility_10k());
        // doPrint(TestResources.Reg.getSolubility());
        // doPrint(TestResources.Reg.getLogS_1210());
        // doPrint(TestResources.Reg.getHERG());
        // doPrint(TestResources.Reg.getToy_many_cols());
        // doPrint(TestResources.Reg.getContradict_labels_and_outlier());

    }

    public static void doPrint(CmpdData res) throws IOException {
        Pair<ChemDataset,DescriptorCalcInfo> result = TestChemDataLoader.loadDatasetWithInfo(res);
        System.err.println(res.uri() + ": valid="+result.getLeft().getNumRecords() + " , invalid=" + result.getRight().getFailedRecords().size());
    }


    
}

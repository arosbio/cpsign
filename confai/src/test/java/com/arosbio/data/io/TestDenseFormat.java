/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.transform.format.MakeDenseTransformer;
import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.TestUtils.StringOutputStream;
import com.arosbio.testutils.TestDataLoader;

@Category(UnitTest.class)
public class TestDenseFormat {
    
    @Test
    public void testSparseOriginal() throws IOException{
        SubSet sparseOriginalData = TestDataLoader.getInstance().getDataset(true, true).getDataset();
        SubSet denseOriginalData = new MakeDenseTransformer().fitAndTransform(sparseOriginalData);
        
        // Test save / load the sparse data
        DenseFormat fmt = new DenseFormat(true);
        StringOutputStream os = new StringOutputStream();
        fmt.write(os, sparseOriginalData);

        SubSet read = null;
        try(InputStream is = IOUtils.toInputStream(os.toString(StandardCharsets.UTF_8), StandardCharsets.UTF_8)){
            read = fmt.read(is);
        }
        Assert.assertTrue(DataUtils.equals(sparseOriginalData, read));
        

        // Test save / load dense data
        StringOutputStream os2 = new StringOutputStream();
        fmt.write(os2, denseOriginalData);

        SubSet read2 = null;
        try(InputStream is = IOUtils.toInputStream(os2.toString(StandardCharsets.UTF_8), StandardCharsets.UTF_8)){
            read2 = fmt.read(is);
        }
        Assert.assertTrue(DataUtils.equals(denseOriginalData, read2));
        Assert.assertTrue(DataUtils.equals(read, read2)); // check that both loaded data sets equals as well


        // Test save it to LIBSVM format to check the difference in size
        StringOutputStream os3 = new StringOutputStream();
        LIBSVMFormat fmt_sparse = new LIBSVMFormat();
        fmt_sparse.write(os3, sparseOriginalData);

        SubSet read3 = null;
        try(InputStream is = IOUtils.toInputStream(os3.toString(StandardCharsets.UTF_8), StandardCharsets.UTF_8)){
            read3 = fmt_sparse.read(is);
        }
        Assert.assertTrue(DataUtils.equals(sparseOriginalData, read3));
    }

    @Test
    public void testDenseOriginal() throws IOException{
        SubSet denseOriginalData = TestDataLoader.loadSubset(TestResources.SVMLIGHTFiles.REGRESSION_HOUSING_25);
        
        // Test save / load dense data
        DenseFormat fmt = new DenseFormat(true);
        StringOutputStream os = new StringOutputStream();
        fmt.write(os, denseOriginalData);

        SubSet read = null;
        try(InputStream is = IOUtils.toInputStream(os.toString(StandardCharsets.UTF_8), StandardCharsets.UTF_8)){
            read = fmt.read(is);
        }
        Assert.assertTrue(DataUtils.equals(denseOriginalData, read));
        

        // Test save it to LIBSVM format to check the difference in size
        StringOutputStream os2 = new StringOutputStream();
        LIBSVMFormat fmt_sparse = new LIBSVMFormat();
        fmt_sparse.write(os2, denseOriginalData);

        SubSet read2 = null;
        try(InputStream is = IOUtils.toInputStream(os2.toString(StandardCharsets.UTF_8), StandardCharsets.UTF_8)){
            read2 = fmt_sparse.read(is);
        }
        Assert.assertTrue(DataUtils.equals(denseOriginalData, read2));
        Assert.assertTrue(DataUtils.equals(read, read2)); // check that both loaded data sets equals as well

    }
}

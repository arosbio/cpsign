package com.arosbio.cpsign.app.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.cheminf.descriptors.UserSuppliedDescriptor;
import com.arosbio.cheminf.descriptors.UserSuppliedDescriptor.SortingOrder;
import com.arosbio.commons.logging.LoggerUtils;
import com.arosbio.cpsign.app.params.converters.ConfigUtils;
import com.arosbio.data.transform.feature_selection.VarianceBasedSelector;
import com.arosbio.tests.suites.UnitTest;

@Category(UnitTest.class)
public class TestConfigUtils {


    @Test
    public void testConfigEnum() throws Exception {
        UserSuppliedDescriptor descriptor = new UserSuppliedDescriptor();
        Assert.assertTrue(descriptor.getSortingOrder() != SortingOrder.REVERSE_ALPHABETICAL); 
        Assert.assertEquals(descriptor.getSortingOrder(), SortingOrder.UNMODIFIED); // Unmodified as default 
        List<String> confList = new ArrayList<>();
        confList.add("sortOrder='revers-alphabetical'");
        ConfigUtils.setConfigs(descriptor, confList, "Usersupplied:sortOrder='revers-alphabetical'");
        Assert.assertEquals(descriptor.getSortingOrder(), SortingOrder.REVERSE_ALPHABETICAL);

        VarianceBasedSelector vbs = new VarianceBasedSelector();
        List<String> confs = Arrays.asList("criterion=keepN","n=50");
        LoggerUtils.setDebugMode(System.err);
        ConfigUtils.setConfigs(vbs, confs, "VarianceBasedSelection:criterion=keepN:n=50");

    }
    
}
